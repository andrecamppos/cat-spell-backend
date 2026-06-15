---
phase: 5
slug: real-time-chat
status: verified
threats_open: 0
asvs_level: 1
created: 2025-06-15
---

# Phase 5 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| WebSocket CONNECT | Client → STOMP endpoint `/ws` | JWT token (Authorization header) |
| STOMP SUBSCRIBE | Client → topic `/topic/chat/{id}` | Conversation ID (user must be participant) |
| STOMP SEND | Client → `/app/chat.send` | Message content (max 1000 chars), matchId/conversationId |
| REST API | Client → `/api/conversations/**` | JWT bearer token, conversation IDs |
| Server → Client push | Server → `/user/{id}/queue/notifications` | Chat notifications (userId-scoped by Spring) |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-05-01 | Spoofing | WebSocketAuthInterceptor | mitigate | JWT validated on STOMP CONNECT; missing/invalid token throws MessagingException | closed |
| T-05-02 | Tampering | WebSocketAuthInterceptor | mitigate | SUBSCRIBE to /topic/chat/{id} checks participant membership via ConversationParticipantRepository | closed |
| T-05-03 | Elevation of Privilege | ChatService.sendMessage | mitigate | Match existence + sender membership validated before conversation creation | closed |
| T-05-04 | Denial of Service | SendMessageRequest | mitigate | @Size(max = 1000) on content field; @NotBlank prevents empty | closed |
| T-05-05 | Tampering | MessageRepository | mitigate | Spring Data JPA parameterized queries; cursor typed as Instant (no string injection) | closed |
| T-05-06 | Information Disclosure | ChatService.getConversations | mitigate | findConversationsByUserId scoped to authenticated user's participant rows | closed |
| T-05-07 | Elevation of Privilege | ChatService.markRead | mitigate | findByConversationIdAndUserId returns null → ResourceNotFoundException (404) | closed |
| T-05-08 | Tampering | ChatService.getConversations | mitigate | Unread count server-computed from DB timestamps (lastReadAt vs createdAt), not client-supplied | closed |
| T-05-09 | Information Disclosure | ChatService.deliverUnreadMessages | mitigate | Query filtered by userId + Spring convertAndSendToUser resolves user principal; userId from authenticated CONNECT | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|

No accepted risks.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2025-06-15 | 9 | 9 | 0 | gsd-secure-phase |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2025-06-15
