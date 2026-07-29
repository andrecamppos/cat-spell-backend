---
phase: 09
slug: notification-triggers-smart-delivery
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-07-29
---

# Phase 09 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Mobile client → STOMP inbound channel | Client SUBSCRIBE frames determine presence + active-conversation state used by the send decision | Authenticated STOMP principal, subscription destinations |
| Domain event → push fan-out | Recipient/matched-user IDs decide who receives a push containing message preview / match info | User IDs, message preview, match info |
| Backend → FCM | Payload (incl. lock-screen preview) crosses to Google FCM and the device | Message preview, deep-link IDs |
| Request thread → async listener | FCM I/O is moved off the request/persistence thread; a failure here must not affect the write | Domain event primitives |
| Transaction commit → side effect | Push must fire only after a successful commit, never on rollback | Domain event primitives |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-9-01 | Information Disclosure | PushNotificationService fan-out targeting | high | mitigate | `notifyMessage` targets only the recipient id; `notifyMatch` targets only the passed matched ids; no broadcast/topic sends. Verified in `PushNotificationService.kt` (unit tests assert sender receives zero sends). | closed |
| T-9-02 | Denial of Service | Synchronous/failing FCM call on the message path | high | mitigate | `@Async @TransactionalEventListener(AFTER_COMMIT)` isolates FCM I/O post-commit on a separate thread; handler try/catches and logs. Verified in `PushNotificationListener.kt`; integration Test B asserts non-blocking. | closed |
| T-9-03 | Spoofing/Tampering | StompPresenceListener subscription tracking | medium | accept | Presence keyed off the authenticated STOMP principal (`accessor.user`, WebSocketAuthInterceptor, Phase 5), not a client-supplied id. Conversation-topic authz owned by the chat/STOMP boundary (Phase 5); a forged subscription only affects that client's own suppression. | closed |
| T-9-04 | Denial of Service | PresenceRegistry unbounded growth | low | mitigate | State bounded by live sessions and cleared on `SessionDisconnectEvent`; no persistence, no per-message growth. Verified `removeSession` clears `sessionsByUser` + `destinationsBySession` in `PresenceRegistry.kt`. | closed |
| T-9-05 | Information Disclosure | Message preview on lock screen (D-01) | medium | accept | Accepted product decision (CONTEXT.md D-01, dating-app norm). Revisit if per-type toggles / content-hiding ship (deferred). | closed |
| T-9-06 | Tampering | PushPayload.data deep-link keys | low | mitigate | Keys are server-derived from persisted IDs (conversationId/messageId/senderId/matchId); no client input flows into the payload. Verified in `PushNotificationService.kt`. | closed |
| T-9-07 | Repudiation/Consistency | Push fired for a match/message that later rolled back | medium | mitigate | `AFTER_COMMIT` phase guarantees the listener runs only when the transaction commits; pre-commit events discarded on rollback. Verified in `PushNotificationListener.kt`. | closed |
| T-9-08 | Information Disclosure | Duplicate match notification on idempotent createMatch | low | mitigate | Event published only on the new-save branch, not on existing-match returns or the duplicate-key fallback. Verified in `MatchService.createMatch`. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-9-01 | T-9-03 | STOMP identity is established by the Phase 5 `WebSocketAuthInterceptor`; presence is keyed off the authenticated principal. Topic authorization is a Phase 5 concern, out of Phase 9 scope; a forged subscription only affects the offending client's own push suppression. | Phase plan author (09-01-PLAN) | 2026-07-29 |
| R-9-02 | T-9-05 | Showing message preview on the lock screen is an accepted product decision (CONTEXT.md D-01, standard dating-app behavior). To be revisited if per-type notification toggles or content-hiding are shipped. | Phase plan author (09-02-PLAN) | 2026-07-29 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-07-29 | 8 | 8 | 0 | gsd-secure-phase (L1 grep-depth, State B from artifacts) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-07-29
