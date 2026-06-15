# Phase 5: Real-Time Chat — Research

**Researched:** 2026-06-15
**Confidence:** HIGH
**Phase requirements:** CHAT-01, CHAT-02, CHAT-03

## 1. Spring WebSocket + STOMP Configuration

### Dependency
Add `spring-boot-starter-websocket` to `build.gradle.kts`:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-websocket")
```

### WebSocketConfig
Spring Boot 4.0.x uses `@EnableWebSocketMessageBroker` with `WebSocketMessageBrokerConfigurer`:

```kotlin
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic", "/queue")
        config.setApplicationDestinationPrefixes("/app")
        config.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
    }
}
```

**Key decisions:**
- `/topic/chat/{conversationId}` — per-conversation message broadcast
- `/user/queue/notifications` — user-specific cross-conversation notifications
- `/app/chat.send` — application destination for sending messages
- Simple broker is sufficient for <10k connections (STACK.md recommendation)
- No SockJS fallback (D-12: native WebSocket only, mobile clients)

### SecurityConfig Changes
WebSocket endpoint `/ws` needs permit rule. The HTTP-level JWT filter should NOT apply to WebSocket upgrade requests — auth happens at STOMP CONNECT level:

```kotlin
it.requestMatchers("/ws/**").permitAll()
```

## 2. WebSocket Authentication via JWT

### ChannelInterceptor Pattern (D-09)
Reuse existing `JwtService.validateToken()` and `JwtService.extractUserId()`:

```kotlin
@Component
class WebSocketAuthInterceptor(
    private val jwtService: JwtService
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        if (accessor?.command == StompCommand.CONNECT) {
            val authHeader = accessor.getFirstNativeHeader("Authorization")
                ?: throw MessagingException("Missing Authorization header")
            val token = authHeader.removePrefix("Bearer ").trim()
            val userId = jwtService.extractUserId(token)
            accessor.user = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
        }
        return message
    }
}
```

Register in WebSocketConfig:
```kotlin
override fun configureClientInboundChannel(registration: ChannelRegistration) {
    registration.interceptors(webSocketAuthInterceptor)
}
```

**Session behavior (D-10):** Once authenticated via CONNECT, session stays alive until disconnect. Token expiry only checked on reconnect.

### SUBSCRIBE Validation (D-11)
Validate conversation membership on SUBSCRIBE, not per-message. Add check in interceptor for SUBSCRIBE command to `/topic/chat/{conversationId}`:
- Extract `conversationId` from destination path
- Query `ConversationParticipant` to verify user is a member
- Reject with `MessagingException` if not a participant

## 3. Database Schema

### Next Migration: V10

Three tables needed. All follow existing patterns (UUID PKs, timestamps):

**V10__create_conversations_table.sql:**
```sql
CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id        UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_conversations_match ON conversations(match_id);
```

**V11__create_conversation_participants_table.sql:**
```sql
CREATE TABLE conversation_participants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_at    TIMESTAMPTZ,
    muted           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX idx_cp_conv_user ON conversation_participants(conversation_id, user_id);
CREATE INDEX idx_cp_user ON conversation_participants(user_id);
```

**V12__create_messages_table.sql:**
```sql
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_messages_conv_created ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_undelivered ON messages(conversation_id, delivered) WHERE NOT delivered;
```

**Design notes:**
- `conversations.match_id` has UNIQUE constraint — one conversation per match
- `conversations.last_message_at` denormalized for efficient conversation list sorting
- `messages.delivered` tracks per-recipient delivery status (D-06)
- Partial index on undelivered messages for efficient reconnect queries
- Content max length (1000 chars) enforced at application level via Jakarta validation (D-16)

## 4. JPA Entities

Follow existing patterns from Match/CatProfile entities:
- JPA entities as classes (not data classes) with `equals`/`hashCode` overrides
- `kotlin-jpa` plugin handles no-arg constructors
- `FetchType.LAZY` for all associations
- UUID primary keys with `GenerationType.UUID`

### Conversation Entity
```kotlin
@Entity
@Table(name = "conversations")
class Conversation(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    var match: Match,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_message_at")
    var lastMessageAt: Instant? = null
)
```

### ConversationParticipant Entity
```kotlin
@Entity
@Table(name = "conversation_participants")
class ConversationParticipant(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    var conversation: Conversation,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "last_read_at")
    var lastReadAt: Instant? = null,

    @Column(nullable = false)
    var muted: Boolean = false
)
```

### Message Entity
```kotlin
@Entity
@Table(name = "messages")
class Message(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    var conversation: Conversation,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    var sender: User,

    @Column(nullable = false, length = 1000)
    var content: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var delivered: Boolean = false
)
```

## 5. Service Layer: ChatService

### Core Flow — Send Message (D-03, D-05)
1. Validate sender has an active match with recipient (via `MatchRepository.findByUserPair()`)
2. Find or create conversation:
   - Check if conversation exists for this match
   - If not: create `Conversation` + 2 `ConversationParticipant` rows atomically
3. Persist `Message` with `delivered = false`
4. Update `conversation.lastMessageAt`
5. Broadcast message to `/topic/chat/{conversationId}` via `SimpMessagingTemplate`
6. Send notification to other user via `/user/{userId}/queue/notifications` (D-07)
7. Return message response

### Lazy Conversation Creation (D-03)
All in one `@Transactional` method. The conversation is created on first message, not on match creation. This avoids empty conversations.

### Message History (D-14, D-15)
- REST endpoint: `GET /api/conversations/{id}/messages?cursor={timestamp}&size=30`
- Cursor-based pagination, newest first
- Cursor = `created_at` of last message in previous page
- Default page size: 30 (D-15)
- Consistent with Phase 4's cursor pattern in DiscoveryService

### Conversation List (D-04, D-08)
- REST endpoint: `GET /api/conversations`
- Returns: other user's display name + first photo, cat names + first photos, last message preview, unread count
- Sorted by `last_message_at DESC`
- Unread count: `SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND sender_id != ? AND created_at > participant.last_read_at`
- Reuse existing query patterns from `MatchService.getMatches()` for user/cat data enrichment

### Mark Read
- REST endpoint: `POST /api/conversations/{id}/read`
- Updates `conversation_participants.last_read_at = NOW()` for the calling user

### Reconnect Flow (D-05, D-06)
1. User reconnects via WebSocket CONNECT
2. After successful auth, query: `SELECT * FROM messages WHERE conversation_id IN (user's conversations) AND delivered = false AND sender_id != userId ORDER BY created_at`
3. Push each undelivered message via `/user/{userId}/queue/notifications`
4. Mark as `delivered = true`

## 6. Controller Layer

### ChatController (WebSocket)
```kotlin
@Controller
class ChatController(private val chatService: ChatService) {
    @MessageMapping("/chat.send")
    fun sendMessage(
        @Payload request: SendMessageRequest,
        principal: Principal
    ) {
        val userId = UUID.fromString(principal.name)
        chatService.sendMessage(userId, request)
    }
}
```

### ConversationController (REST)
```kotlin
@RestController
@RequestMapping("/api/conversations")
class ConversationController(private val chatService: ChatService) {

    @GetMapping
    fun getConversations(): ResponseEntity<ConversationListResponse>

    @GetMapping("/{id}/messages")
    fun getMessages(
        @PathVariable id: UUID,
        @RequestParam(required = false) cursor: Instant?,
        @RequestParam(defaultValue = "30") size: Int
    ): ResponseEntity<MessagePageResponse>

    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: UUID): ResponseEntity<Void>
}
```

## 7. DTOs

```kotlin
// WebSocket
data class SendMessageRequest(
    val conversationId: UUID?,  // null for first message (lazy creation)
    val matchId: UUID?,         // used when conversationId is null
    val content: String
)

data class ChatMessageResponse(
    val messageId: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val senderName: String,
    val content: String,
    val createdAt: Instant
)

data class ChatNotification(
    val conversationId: UUID,
    val messageId: UUID,
    val senderName: String,
    val preview: String  // truncated content
)

// REST
data class ConversationResponse(
    val conversationId: UUID,
    val matchId: UUID,
    val otherUser: MatchUserSummary,  // reuse from Phase 4
    val otherUserCats: List<MatchCatSummary>,  // reuse from Phase 4
    val lastMessage: LastMessagePreview?,
    val unreadCount: Int
)

data class LastMessagePreview(
    val content: String,
    val sentAt: Instant,
    val sentByMe: Boolean
)

data class ConversationListResponse(
    val conversations: List<ConversationResponse>
)

data class MessagePageResponse(
    val messages: List<ChatMessageResponse>,
    val nextCursor: Instant?,
    val hasMore: Boolean
)
```

## 8. Testing Strategy

### WebSocket Integration Tests
Spring Boot provides `WebSocketStompClient` for integration testing:

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatIntegrationTest : BaseIntegrationTest() {
    @LocalServerPort
    var port: Int = 0

    // Use WebSocketStompClient for WebSocket tests
    // Use MockMvc for REST endpoint tests
}
```

**Test dependency needed:**
```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-websocket")
```

**Key test scenarios:**
1. Send message via WebSocket → recipient receives it on subscribed topic
2. Send message to non-match → rejected
3. First message creates conversation lazily
4. Message history returns paginated results
5. Conversation list returns unread counts
6. WebSocket authentication with valid/invalid JWT
7. SUBSCRIBE to non-participant conversation → rejected
8. Reconnect delivers undelivered messages
9. Mark read updates unread count

### Test Pattern
Follow existing `MatchIntegrationTest` pattern:
- Extend `BaseIntegrationTest` (Testcontainers PostgreSQL + MinIO)
- Use `setupCompleteUser()` and `createMutualMatch()` helpers
- `@SpringBootTest` with `RANDOM_PORT` (needed for WebSocket, not just MockMvc)
- `WebSocketStompClient` connects to `ws://localhost:{port}/ws`

## 9. Package Structure

Follow domain-first vertical slice pattern (`com.catspell.api.chat.*`):

```
src/main/kotlin/com/catspell/api/chat/
├── controller/
│   ├── ChatController.kt         (WebSocket @Controller)
│   └── ConversationController.kt (REST @RestController)
├── model/
│   ├── Conversation.kt
│   ├── ConversationParticipant.kt
│   ├── Message.kt
│   ├── ConversationRepository.kt
│   ├── ConversationParticipantRepository.kt
│   ├── MessageRepository.kt
│   └── ChatDtos.kt
├── service/
│   └── ChatService.kt
└── config/
    └── WebSocketConfig.kt

src/main/kotlin/com/catspell/api/common/security/
└── WebSocketAuthInterceptor.kt   (alongside JwtService/JwtAuthenticationFilter)
```

## 10. Pitfalls & Risks

### N+1 Query Risk
Conversation list enrichment (user profiles, photos, cats) mirrors `MatchService.getMatches()` which has N+1 potential. Use batch queries or `@EntityGraph` for production. Acceptable for v1 MVP given small match counts.

### Transaction Boundary with WebSocket
`SimpMessagingTemplate.convertAndSend()` should be called AFTER the transaction commits, not inside it. If the transaction rolls back, the WebSocket message is already sent. Options:
- Use `@TransactionalEventListener(phase = AFTER_COMMIT)` for the broadcast
- Or accept the minor inconsistency for v1 (message appears briefly, then disappears on retry)

**Recommendation:** Use `@TransactionalEventListener(phase = AFTER_COMMIT)` — it's clean and Spring-native.

### Thread Safety
`SimpMessagingTemplate` is thread-safe and can be injected directly. No additional synchronization needed.

### STOMP CONNECT Race Condition
If a client sends SUBSCRIBE before CONNECT completes, the interceptor won't have set the user principal. Spring handles this — messages are queued until CONNECT completes.

### Message Ordering
Server-assigned `created_at` (D-13) is sufficient for 1:1 chat with a single server. If scaling to multiple servers, consider sequence numbers. Not needed for v1 MVP.

## Validation Architecture

### Correctness Dimensions
1. **Schema correctness** — Flyway migrations create tables with correct FKs, indexes
2. **Auth correctness** — JWT validated on CONNECT, membership on SUBSCRIBE
3. **Data flow** — Message persisted → broadcast → delivered flag updated
4. **Pagination** — Cursor-based, consistent with Phase 4 pattern
5. **Lazy creation** — First message atomically creates conversation + participants

### Risk Areas
- WebSocket auth bypass (missing interceptor registration)
- Conversation created without match validation
- Unread count calculation off by one
- Message delivered flag not updated after push

---

## RESEARCH COMPLETE

*Research for Phase 5: Real-Time Chat*
*Researched: 2026-06-15*
