---
status: complete
phase: 05-real-time-chat
source: 05-01-SUMMARY.md, 05-02-SUMMARY.md
started: 2026-06-15T21:49:00Z
updated: 2026-06-15T22:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running server. Start the application from scratch. Server boots without errors, all Flyway migrations (V1–V12) complete, and a basic API call returns a live response.
result: pass

### 2. Send a Chat Message via WebSocket
expected: Connect to /ws via STOMP with a valid JWT. Send a message to a matched user via /app/chat.send with matchId and content. The message is received in real time on /topic/chat/{conversationId} and a notification arrives on /user/{userId}/queue/notifications.
result: pass

### 3. Lazy Conversation Creation
expected: When sending the first message with a matchId that has no existing conversation, a new Conversation and ConversationParticipant records are created automatically. Subsequent messages to the same match reuse the existing conversation.
result: pass

### 4. Non-Match Message Rejection
expected: Attempting to send a message with a matchId where the sender is not a participant in the match returns an error and no conversation is created.
result: pass

### 5. Message History (Cursor Pagination)
expected: GET /api/conversations/{id}/messages returns messages ordered by createdAt DESC. Passing a cursor parameter returns the next page of older messages. Response includes hasMore flag.
result: pass

### 6. Conversation List
expected: GET /api/conversations returns the authenticated user's conversations sorted by lastMessageAt DESC. Each entry includes the other user's info, their cats, the last message preview (truncated to 100 chars), and the unread count.
result: pass

### 7. Mark Conversation as Read
expected: POST /api/conversations/{id}/read updates lastReadAt for the calling user. Subsequent GET /api/conversations shows unread count reset to 0 for that conversation.
result: pass

### 8. Offline Message Delivery
expected: User disconnects from WebSocket. Messages sent to them while offline are marked undelivered. When the user reconnects (SessionConnectedEvent), undelivered messages are pushed to /user/{userId}/queue/notifications and marked as delivered.
result: pass

### 9. WebSocket Auth Enforcement
expected: Connecting to /ws without a JWT or with an invalid JWT fails at the STOMP CONNECT frame. Subscribing to a conversation the user is not a participant of is rejected.
result: pass

## Summary

total: 9
passed: 9
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
