---
phase: 05-real-time-chat
plan: 01
subsystem: api
tags: [chat, websocket, stomp, jwt, messaging, cursor-pagination]

requires:
  - phase: 04-discovery-matching
    provides: Match entity, MatchRepository, MatchService
provides:
  - Conversation, ConversationParticipant, Message JPA entities + repositories
  - WebSocket STOMP endpoint at /ws with JWT auth interceptor
  - ChatService with lazy conversation creation, message sending, cursor-paginated history
  - ChatController (WebSocket @MessageMapping) + ConversationController (REST)
  - V10 + V11 + V12 Flyway migrations for conversations, participants, messages
affects: [05-02-conversation-list-unread-offline]

tech-stack:
  added: [spring-boot-starter-websocket]
  patterns: [STOMP over WebSocket, JWT CONNECT auth, lazy conversation creation on first message, cursor pagination by createdAt]

key-files:
  created:
    - src/main/resources/db/migration/V10__create_conversations_table.sql
    - src/main/resources/db/migration/V11__create_conversation_participants_table.sql
    - src/main/resources/db/migration/V12__create_messages_table.sql
    - src/main/kotlin/com/catspell/api/chat/model/Conversation.kt
    - src/main/kotlin/com/catspell/api/chat/model/ConversationParticipant.kt
    - src/main/kotlin/com/catspell/api/chat/model/Message.kt
    - src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt
    - src/main/kotlin/com/catspell/api/chat/model/ConversationParticipantRepository.kt
    - src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt
    - src/main/kotlin/com/catspell/api/chat/model/ChatDtos.kt
    - src/main/kotlin/com/catspell/api/chat/config/WebSocketConfig.kt
    - src/main/kotlin/com/catspell/api/common/security/WebSocketAuthInterceptor.kt
    - src/main/kotlin/com/catspell/api/chat/service/ChatService.kt
    - src/main/kotlin/com/catspell/api/chat/controller/ChatController.kt
    - src/main/kotlin/com/catspell/api/chat/controller/ConversationController.kt
    - src/test/kotlin/com/catspell/api/chat/ChatIntegrationTest.kt
  modified:
    - build.gradle.kts
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt

key-decisions:
  - "STOMP over WebSocket — standard Spring messaging with simple broker for pub/sub"
  - "JWT validated on STOMP CONNECT frame via ChannelInterceptor — reuses existing JwtService"
  - "Subscription authorization — interceptor validates user is participant of conversation on SUBSCRIBE"
  - "Lazy conversation creation — first message with matchId creates conversation + participants"
  - "Cursor-based pagination for message history — ordered by createdAt DESC"
  - "Message broadcast to /topic/chat/{conversationId} + notification to /user/{userId}/queue/notifications"
  - "SecurityConfig permits /ws/** — WebSocket auth handled at STOMP layer, not HTTP"

patterns-established:
  - "WebSocket STOMP with JWT auth via ChannelInterceptor"
  - "Lazy entity creation on first use (conversation from match)"
  - "Dual delivery: topic broadcast for conversation + user queue for cross-conversation notifications"

requirements-completed: [CHAT-01, CHAT-02]

duration: ~30min
completed: 2026-06-15
---

# Plan 05-01 Summary: Core WebSocket Chat + Message History

**Complete real-time chat vertical slice: DB migrations → entities → WebSocket config → JWT auth → service → controllers → 9 integration tests**

## Performance

- **Tasks:** 7/7 complete
- **Files created:** 16
- **Files modified:** 2

## Accomplishments
- V10 migration: conversations table with unique index on match_id
- V11 migration: conversation_participants table with composite unique (conversation_id, user_id)
- V12 migration: messages table with partial index on undelivered messages
- Conversation, ConversationParticipant, Message JPA entities with Spring Data repositories
- ChatDtos: SendMessageRequest (with validation), ChatMessageResponse, ChatNotification, MessagePageResponse
- WebSocketConfig: STOMP endpoint /ws, simple broker /topic + /queue, app prefix /app
- WebSocketAuthInterceptor: JWT validation on CONNECT, participant check on SUBSCRIBE
- ChatService: sendMessage with lazy conversation creation, getMessages with cursor pagination
- ChatController: @MessageMapping for /chat.send
- ConversationController: GET /api/conversations/{id}/messages
- 9 integration tests: send/receive, lazy creation, non-match rejection, JWT rejection, message history, cursor pagination, max length, non-participant subscription, auth required

## Task Commits

1. `1942789` — Add WebSocket dependency and chat Flyway migrations
2. `4732bc2` — Create JPA entities and repositories
3. `5ee1843` — Create chat DTOs
4. `4c0a9a1` — Configure WebSocket STOMP with JWT authentication
5. `dfe6201` — Implement ChatService
6. `d68cb40` — Implement controllers
7. `0d9fcc0` — Integration tests

## Deviations from Plan

- None — all tasks executed as planned

## Next Phase Readiness
- Conversation + Message entities ready for conversation list, unread counts, mark-read in Plan 05-02
- MessageRepository already includes queries for undelivered messages and unread counts

---
*Phase: 05-real-time-chat*
*Completed: 2026-06-15*
