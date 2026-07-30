---
phase: 09-notification-triggers-smart-delivery
plan: 02
subsystem: api
tags: [push, fcm, apns, collapse-key, presence, mockk, spring]

requires:
  - phase: 09-notification-triggers-smart-delivery
    provides: PresenceRegistry (isOnline / isViewingConversation) from Plan 01
  - phase: 08-push-delivery-foundation
    provides: PushSendService, PushProvider/FcmPushProvider, DeviceTokenRepository
provides:
  - PushPayload.collapseKey (provider-neutral) mapped to FCM AndroidConfig.collapse_key + APNs apns-collapse-id
  - PushNotificationService — match presence-suppression fan-out + message offline+inactive send decision
affects: [09-03-push-triggers]

tech-stack:
  added: []
  patterns:
    - "Send-decision business logic isolated in PushNotificationService (chat/match stay decoupled from push)"
    - "Per-conversation FCM collapse via optional PushPayload field, kept provider-neutral"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/push/service/PushNotificationService.kt
    - src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/push/service/PushProvider.kt
    - src/main/kotlin/com/catspell/api/push/service/FcmPushProvider.kt

key-decisions:
  - "collapseKey added as optional trailing PushPayload field (default null) — additive, non-breaking"
  - "Match copy: title 'It's a match!', body 'You have a new match on Cat Spell' (Claude discretion per CONTEXT.md)"
  - "Token pruning left to PushSendService; PushNotificationService only fans out"

patterns-established:
  - "notifyMatch filters online users (D-02); notifyMessage uses !online || !viewing (D-04)"
  - "Deep-link data keys: match -> matchId; message -> conversationId/messageId/senderId"

requirements-completed: [PUSH-04, PUSH-05, PUSH-06, PUSH-07]

coverage:
  - id: D1
    description: "Mutual match pushes offline matched users and suppresses online ones; payload carries matchId (D-02, PUSH-04)"
    requirement: "PUSH-04"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt#notifyMatch pushes only offline users and carries matchId"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt#notifyMatch with both offline pushes both users"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt#notifyMatch with both online sends nothing"
        status: pass
    human_judgment: false
  - id: D2
    description: "New message pushes the recipient only with sender name title + preview body and deep-link data (D-01, PUSH-05)"
    requirement: "PUSH-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt#notifyMessage payload carries preview title body deep-link data and collapseKey"
        status: pass
    human_judgment: false
  - id: D3
    description: "Message payload sets collapseKey = conversationId; FcmPushProvider maps it to FCM/APNs collapse (D-05/D-06, PUSH-06)"
    requirement: "PUSH-06"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt#notifyMessage payload carries preview title body deep-link data and collapseKey"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushProviderContractTest.kt#send passes the expected payload shape to the provider"
        status: pass
    human_judgment: false
  - id: D4
    description: "Message send decision: send when offline OR online-but-not-viewing; suppress when online AND viewing (D-04, PUSH-07)"
    requirement: "PUSH-07"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt#notifyMessage sends when recipient is offline"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt#notifyMessage sends when recipient is online but not viewing the conversation"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt#notifyMessage suppresses when recipient is online and viewing the conversation"
        status: pass
    human_judgment: false

duration: 15 min
completed: 2026-07-29
status: complete
---

# Phase 9 Plan 02: Smart-Delivery Decision & Payload Summary

**`PushNotificationService` holding match presence-suppression fan-out and the message "offline + inactive" send decision, plus a provider-neutral `collapseKey` mapped to FCM `AndroidConfig.collapse_key` and APNs `apns-collapse-id`.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-07-29T13:40:00Z
- **Completed:** 2026-07-29T13:55:35Z
- **Tasks:** 2
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments
- Added optional `collapseKey` to `PushPayload` (non-breaking) and mapped it to FCM/APNs collapse only when present.
- Implemented `PushNotificationService.notifyMatch` (D-02 presence suppression, matchId deep-link) and `notifyMessage` (D-04 offline+inactive decision, D-01 preview payload, D-05/D-06 per-conversation collapse).
- Full MockK unit coverage of the send-decision matrix, fan-out, zero-token safety, and payload shape (PUSH-04/05/06/07).

## Task Commits

1. **Task 1: Add collapseKey + FCM/APNs mapping** — `1c38ebd` (feat)
2. **Task 2: Implement PushNotificationService (TDD)** — `b85a67d` (test) → `f1fc90c` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/push/service/PushProvider.kt` - Added `collapseKey` field to `PushPayload`
- `src/main/kotlin/com/catspell/api/push/service/FcmPushProvider.kt` - Map collapseKey to AndroidConfig/ApnsConfig
- `src/main/kotlin/com/catspell/api/push/service/PushNotificationService.kt` - Send decision + fan-out
- `src/test/kotlin/com/catspell/api/push/PushNotificationServiceTest.kt` - Send-decision matrix + payload tests

## Decisions Made
- `collapseKey` is a trailing nullable field so all existing call sites and `PushProviderContractTest` compile unchanged.
- Match push copy wording chosen at Claude's discretion per CONTEXT.md.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- IDE Kotlin analyzer emitted false-positive stdlib "unresolved reference" / metadata-version errors (analyzer 2.1.0 vs project stdlib 2.4.0). Confirmed non-issues: `./gradlew compileKotlin compileTestKotlin`, `PushProviderContractTest`, and `PushNotificationServiceTest` all pass.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `PushNotificationService.notifyMatch` / `notifyMessage` are the seam Plan 03's `@Async @TransactionalEventListener` will call after commit.

---
*Phase: 09-notification-triggers-smart-delivery*
*Completed: 2026-07-29*
