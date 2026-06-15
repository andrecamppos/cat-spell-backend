---
phase: 05-real-time-chat
plan: 02
subsystem: api
tags: [chat, conversation-list, unread-count, mark-read, offline-delivery, websocket]

requires:
  - phase: 05-real-time-chat
    plan: 01
    provides: Conversation, ConversationParticipant, Message entities, ChatService, WebSocket config
provides:
  - GET /api/conversations — conversation list with other user, cats, last message, unread count
  - POST /api/conversations/{id}/read — mark conversation as read
  - Offline message delivery on WebSocket reconnect via SessionConnectedEvent
  - ConversationResponse, LastMessagePreview, ConversationListResponse DTOs
affects: []

tech-stack:
  added: []
  patterns: [SessionConnectedEvent listener for offline delivery, @Async + @EnableAsync, server-computed unread counts from lastReadAt]

key-files:
  created:
    - src/main/kotlin/com/catspell/api/chat/service/WebSocketSessionListener.kt
    - src/test/kotlin/com/catspell/api/chat/ConversationListIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/chat/model/ChatDtos.kt
    - src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt
    - src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt
    - src/main/kotlin/com/catspell/api/chat/service/ChatService.kt
    - src/main/kotlin/com/catspell/api/chat/controller/ConversationController.kt
    - src/main/kotlin/com/catspell/api/CatSpellApplication.kt

key-decisions:
  - "SessionConnectedEvent + @Async for offline delivery — cleaner than interceptor, ensures STOMP session established"
  - "200ms delay before delivery — allows STOMP session to fully initialize"
  - "Server-computed unread count from lastReadAt vs message timestamps — tamper-proof"
  - "Conversation list sorted by lastMessageAt DESC NULLS LAST via JPQL"
  - "Reuse MatchUserSummary and MatchCatSummary DTOs for conversation list — consistent API"
  - "Last message preview truncated to 100 chars"

patterns-established:
  - "Spring @EventListener + @Async for WebSocket lifecycle hooks"
  - "@EnableAsync on main application class"
  - "Server-side unread count: null lastReadAt = count all from others, non-null = count after timestamp"

requirements-completed: [CHAT-03]

duration: ~15min
completed: 2026-06-15
---

# Plan 05-02 Summary: Conversation List + Unread Counts + Offline Delivery

**Conversation listing with rich metadata, mark-read, and offline message delivery on reconnect**

## Performance

- **Tasks:** 5/5 complete
- **Files created:** 2
- **Files modified:** 6

## Accomplishments
- ConversationResponse, LastMessagePreview, ConversationListResponse DTOs
- ConversationRepository.findConversationsByUserId JPQL query (lastMessageAt DESC NULLS LAST)
- MessageRepository.countByConversationIdAndSenderIdNot for unread when lastReadAt is null
- ChatService.getConversations: other user + cats + last message preview + unread count
- ChatService.markRead: updates lastReadAt for calling user
- ChatService.deliverUnreadMessages: queries undelivered, pushes via /user/queue/notifications, marks delivered
- WebSocketSessionListener: @EventListener(SessionConnectedEvent) triggers async offline delivery
- @EnableAsync on CatSpellApplication
- GET /api/conversations and POST /api/conversations/{id}/read endpoints
- 10 integration tests: empty list, conversation after message, other user + cat info, last message preview, unread count, mark read resets count, auth on mark read, auth on list, sort order, offline delivery

## Task Commits

1. `2745436` — Add conversation list DTOs and repository queries
2. `1db78ee` — Implement getConversations and markRead in ChatService
3. `37ac6dc` — Add REST endpoints for conversation list and mark read
4. `74e7db8` — Implement offline message delivery on reconnect
5. `adddc5c` — Integration tests

## Deviations from Plan

- None — all tasks executed as planned

## Next Phase Readiness
- Phase 5 complete — all CHAT requirements (CHAT-01, CHAT-02, CHAT-03) fulfilled
- Ready for subsequent phases

---
*Phase: 05-real-time-chat*
*Completed: 2026-06-15*
