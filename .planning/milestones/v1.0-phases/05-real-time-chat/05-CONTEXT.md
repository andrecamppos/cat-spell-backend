# Phase 5: Real-Time Chat - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver WebSocket-based real-time messaging between matched users. Text-only for v1. Messages are persisted to PostgreSQL, users can retrieve paginated message history via REST, and view a list of their conversations (one per match). Conversations are created lazily on first message. Offline users receive queued messages on reconnect via database delivery tracking.

</domain>

<decisions>
## Implementation Decisions

### Conversation Model
- **D-01:** Separate `conversations` table with FK to match. Not reusing `matches` directly — allows conversation-specific metadata (last_message_at, etc.)
- **D-02:** Separate `conversation_participants` table — one row per user per conversation. Tracks `last_read_at` and `muted` status per participant. Ready for v2 read receipts
- **D-03:** Conversations created lazily on first message — avoids empty conversations cluttering the list. ChatService creates conversation + 2 participant rows atomically when first message is sent
- **D-04:** Conversation list returns: other user's display name + first photo, last message preview (truncated text + timestamp), unread count, plus their cats (names + first photo each). Consistent with Phase 4 match list richness

### Offline & Missed Messages
- **D-05:** Queue + deliver on reconnect — server pushes undelivered messages via WebSocket when user reconnects. No in-memory queuing; uses database delivery tracking
- **D-06:** Messages table has a `delivered` boolean per recipient. On WebSocket reconnect, query undelivered messages and push them. Mark as delivered after successful push. Survives server restarts
- **D-07:** WebSocket pushes lightweight notification for new messages across ALL conversations (not just the active one). Client can update badges/unread counts in real time
- **D-08:** REST conversation list endpoint includes server-computed unread count per conversation (derived from `last_read_at` vs message timestamps). Client has accurate state on app open

### WebSocket Authentication
- **D-09:** JWT sent in STOMP CONNECT frame's `Authorization` header. Server validates via Spring `ChannelInterceptor` on the inbound channel. Reuses existing `JwtService` for token parsing
- **D-10:** WebSocket session outlives token — once authenticated via CONNECT, the session stays alive until explicitly closed. Token expiry only checked on reconnect. Simplest approach for v1
- **D-11:** Match/conversation membership validated on SUBSCRIBE only (not per-message). When user subscribes to a conversation topic, server verifies they are a participant. Messages to subscribed topics are trusted
- **D-12:** No SockJS fallback — native WebSocket only. Mobile apps all support WebSocket natively. Simpler configuration

### Message Ordering & History
- **D-13:** Messages ordered by server-assigned `created_at` timestamp (Instant). No sequence numbers — sufficient for 1:1 chat where server is the single ordering authority
- **D-14:** Cursor-based pagination, newest first. Default returns most recent messages. Client scrolls up to load older pages. Cursor = last message timestamp. Consistent with Phase 4 cursor pattern
- **D-15:** 30 messages per page — fills a mobile screen with scroll room, light payload
- **D-16:** Maximum message length: 1000 characters. Keeps messages concise, consistent with dating app conventions

### Claude's Discretion
No areas deferred to Claude's discretion — all decisions made by user.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md` — Core value (cat-first discovery), constraints (Kotlin + Spring Boot, PostgreSQL), out-of-scope items (no media in chat, no icebreakers)
- `.planning/REQUIREMENTS.md` — CHAT-01 through CHAT-03 requirement definitions and traceability
- `.planning/ROADMAP.md` §Phase 5 — Success criteria for this phase

### Prior Phase Context
- `.planning/phases/01-foundation-auth/01-CONTEXT.md` — Package structure decisions (D-01–D-04), JWT token lifecycle, error format (RFC 7807). Phase 5 MUST follow the same domain-first vertical slice pattern (`com.catspell.api.chat.*`). Reuse `JwtService` for WebSocket auth
- `.planning/phases/02-user-profiles-photos/02-CONTEXT.md` — Profile photo patterns, profile completeness. User display name + first photo needed for conversation list
- `.planning/phases/03-cat-profiles/03-CONTEXT.md` — Cat data model, cat photo patterns. Cat names + first photo needed for conversation list
- `.planning/phases/04-discovery-matching/04-CONTEXT.md` — Match entity (user1/user2 pair), MatchRepository queries, swipe model. Chat unlocked by match existence

### Stack Research
- `.planning/research/STACK.md` — Recommended versions, dependencies, Kotlin entity gotchas
- `.planning/research/SUMMARY.md` — Architecture approach, critical pitfalls (Kotlin entity gotchas, N+1 queries)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Match` entity (`com.catspell.api.match.model.Match`) — user1/user2 pair with UUID PK. Conversation FK target
- `MatchRepository` (`com.catspell.api.match.model.MatchRepository`) — `findByUser1IdOrUser2Id()` for listing user's matches, `findByUserPair()` for verifying match exists before allowing chat
- `JwtService` (`com.catspell.api.common.security.JwtService`) — Token parsing/validation, reuse in WebSocket `ChannelInterceptor`
- `JwtAuthenticationFilter` — Reference implementation for extracting user identity from JWT
- `UserProfile` entity — display name + photos for conversation list response
- `CatProfile` + `CatPhoto` entities — cat names + first photo for conversation list response
- `GlobalExceptionHandler` — RFC 7807 error handling for REST chat endpoints

### Established Patterns
- Domain-first vertical slices: `com.catspell.api.{domain}.controller/service/model/`
- JPA entities as classes (not data classes) with `equals`/`hashCode` overrides (kotlin-jpa plugin)
- DTOs as Kotlin data classes with Jakarta validation annotations
- Flyway versioned migrations: next available is V10+
- `extractUserId()` pattern in controllers via `SecurityContextHolder`
- Cursor-based pagination pattern from Phase 4 discovery feed

### Integration Points
- `SecurityConfig.securityFilterChain` — add WebSocket endpoint permit rules
- New `WebSocketConfig` — configure STOMP endpoints, message broker, application destination prefixes
- New `WebSocketAuthInterceptor` — ChannelInterceptor for STOMP CONNECT JWT validation
- Flyway migrations — `conversations`, `conversation_participants`, `messages` tables
- `build.gradle.kts` — add `spring-boot-starter-websocket` dependency

</code_context>

<specifics>
## Specific Ideas

- Conversation created atomically on first message: ChatService checks match exists → creates conversation + 2 participant rows → persists message → broadcasts via WebSocket, all in one transaction
- Messages table: id (UUID), conversation_id (FK), sender_id (FK to users), content (text, max 1000), created_at (Instant), delivered (boolean default false)
- Conversation participants table: id, conversation_id (FK), user_id (FK), last_read_at (nullable Instant), muted (boolean default false)
- STOMP topic per conversation: `/topic/chat/{conversationId}` — user subscribes after opening a conversation
- User-specific queue for cross-conversation notifications: `/user/queue/notifications` — lightweight payloads with conversation_id, sender name, message preview
- On reconnect flow: authenticate → query undelivered messages → push via user queue → mark delivered

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 5-Real-Time Chat*
*Context gathered: 2026-06-15*
