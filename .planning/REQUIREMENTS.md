# Requirements: Cat Spell Backend — v2.0

**Defined:** 2026-07-17
**Milestone:** v2.0 Push Notifications
**Core Value:** Reach users with timely match and message alerts when they're away
from the app, complementing (not replacing) real-time WebSocket chat.

## v2.0 Requirements

Requirements for push notifications. Maps to Phases 8-9.

### Device Tokens

- [ ] **PUSH-01**: User can register a device push token via an authenticated endpoint; upserted by `(user_id, device_id)`
- [ ] **PUSH-02**: User can unregister a device token (on logout); multiple active devices per user are supported
- [ ] **PUSH-03**: Backend deactivates tokens FCM reports as `UNREGISTERED` so dead tokens stop receiving sends

### Match Notifications

- [ ] **PUSH-04**: Both users receive a push notification when a mutual match is detected, with a deep-link payload to the match

### Message Notifications

- [ ] **PUSH-05**: Recipient receives a push notification for a new chat message, with sender + conversation deep-link payload
- [ ] **PUSH-06**: Message pushes use a per-conversation collapse key so an offline device shows only the latest, not a stack

### Send Logic

- [ ] **PUSH-07**: Push is sent only when the recipient is offline OR online-but-not-viewing that conversation ("offline + inactive")
- [ ] **PUSH-08**: Backend tracks STOMP presence and active-conversation per session to drive the send decision; state clears on disconnect

### Delivery Infrastructure

- [ ] **PUSH-09**: Notifications deliver via FCM HTTP v1 behind a `PushProvider` abstraction that allows adding direct APNs later without call-site changes
- [ ] **PUSH-10**: FCM sends run asynchronously (off Spring domain events), never blocking the message-persistence or request path
- [ ] **PUSH-11**: A health indicator reports Firebase/FCM configuration status; startup fails fast if credentials are missing

### Verification

- [ ] **PUSH-12**: Send-decision, payload shape, and token pruning are covered by integration tests using a mocked provider; an FCM `validate_only` dry-run smoke test confirms real auth/payload

## Future Requirements (deferred)

- Per-type notification toggles (match on/off, message on/off)
- Quiet hours / do-not-disturb windows
- Direct APNs integration for iOS delivery reliability (seed: `direct-apns-hardening`)
- Rich media / images in push payloads
- Shared-store (Redis) presence for horizontal scaling

## Out of Scope

- **Marketing / campaign / topic broadcast pushes** — v2.0 is transactional only
- **Push delivery analytics dashboard** — basic logging only for v2.0
- **In-app notification center / history** — OS notification tray is sufficient
- **Web push** — mobile app only

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PUSH-01 | Phase 8 | Pending |
| PUSH-02 | Phase 8 | Pending |
| PUSH-03 | Phase 8 | Pending |
| PUSH-04 | Phase 9 | Pending |
| PUSH-05 | Phase 9 | Pending |
| PUSH-06 | Phase 9 | Pending |
| PUSH-07 | Phase 9 | Pending |
| PUSH-08 | Phase 9 | Pending |
| PUSH-09 | Phase 8 | Pending |
| PUSH-10 | Phase 9 | Pending |
| PUSH-11 | Phase 8 | Pending |
| PUSH-12 | Phase 8 | Pending |

**Coverage:**

- v2.0 requirements: 12 total
- Mapped to phases: 12 (Phase 8: 6, Phase 9: 6)
- Unmapped: 0 ✓

---
*Requirements defined: 2026-07-17*
