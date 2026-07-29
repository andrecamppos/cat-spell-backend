---
phase: 09-notification-triggers-smart-delivery
plan: 01
subsystem: infra
tags: [stomp, websocket, presence, push, spring-events, concurrenthashmap]

requires:
  - phase: 05-real-time-chat
    provides: STOMP session lifecycle + WebSocketAuthInterceptor authenticated principal
  - phase: 08-push-delivery-foundation
    provides: push module + PushSendService FCM seam
provides:
  - PresenceRegistry — in-memory per-session presence + active-conversation store
  - StompPresenceListener — STOMP lifecycle events → registry mutations
  - isOnline / isViewingConversation queries for the Plan 02 send decision
affects: [09-02-push-notification-service, 09-03-push-triggers]

tech-stack:
  added: []
  patterns:
    - "Single-instance in-memory presence via ConcurrentHashMap (D-06)"
    - "Active-conversation inferred from existing /topic/chat/{id} STOMP subscription (D-03) — no new client contract"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/push/presence/PresenceRegistry.kt
    - src/main/kotlin/com/catspell/api/push/presence/StompPresenceListener.kt
    - src/test/kotlin/com/catspell/api/push/PresenceRegistryTest.kt
  modified: []

key-decisions:
  - "Presence keyed off authenticated STOMP principal (accessor.user), not client-supplied id"
  - "Chat topic prefix constant '/topic/chat/' mirrors ChatService.sendMessage exactly"
  - "Listeners are synchronous (not @Async) so registry state is ordered relative to the connection lifecycle"

patterns-established:
  - "push.presence package keeps chat/match decoupled from push"
  - "SessionUnsubscribeEvent carries only subscriptionId → per-session subscriptionId→destination map"

requirements-completed: [PUSH-08]

coverage:
  - id: D1
    description: "isOnline reflects >=1 live STOMP session; false only after the last session disconnects"
    requirement: "PUSH-08"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PresenceRegistryTest.kt#user is offline with no sessions and online after adding a session"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PresenceRegistryTest.kt#user stays online until the last of multiple sessions disconnects"
        status: pass
    human_judgment: false
  - id: D2
    description: "isViewingConversation reflects /topic/chat/{id} subscribe/unsubscribe and ignores non-chat destinations"
    requirement: "PUSH-08"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PresenceRegistryTest.kt#isViewingConversation reflects chat topic subscription lifecycle"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PresenceRegistryTest.kt#isViewingConversation ignores non-chat destinations and other conversations"
        status: pass
    human_judgment: false
  - id: D3
    description: "removeSession clears presence + all subscriptions for that session only, leaving other users untouched (PUSH-08)"
    requirement: "PUSH-08"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PresenceRegistryTest.kt#removeSession clears presence and all subscriptions for that session only"
        status: pass
    human_judgment: false
  - id: D4
    description: "StompPresenceListener routes SessionConnected/Subscribe/Unsubscribe/Disconnect events to the matching registry mutations"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin compileTestKotlin"
        status: pass
    human_judgment: true
    rationale: "Real end-to-end STOMP event routing is exercised by Plan 03's integration test; this plan only proves it compiles and wires the correct calls."

duration: 12 min
completed: 2026-07-29
status: complete
---

# Phase 9 Plan 01: Presence & Active-Conversation Registry Summary

**In-memory single-instance `PresenceRegistry` (ConcurrentHashMap-backed) plus a `StompPresenceListener` that tracks live STOMP sessions and `/topic/chat/{id}` subscriptions to drive the Phase 9 "offline + inactive" send decision.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-29T13:22:00Z
- **Completed:** 2026-07-29T13:34:13Z
- **Tasks:** 2
- **Files modified:** 3 (all created)

## Accomplishments
- `PresenceRegistry` with per-session presence and active-conversation state; `isOnline` and `isViewingConversation` queries for Plan 02.
- `StompPresenceListener` mapping the four STOMP lifecycle events onto registry mutations, inferring active-conversation from the existing chat subscription (D-03) with no new client contract.
- Full JUnit 5 unit coverage of presence transitions, multi-session semantics, subscription lifecycle, per-session isolation, and disconnect cleanup (PUSH-08).

## Task Commits

1. **Task 1: Implement PresenceRegistry (TDD)** — `7290ebd` (test) → `d51ce5e` (feat)
2. **Task 2: Wire STOMP lifecycle events** — `090b7e7` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/push/presence/PresenceRegistry.kt` - In-memory presence + active-conversation store
- `src/main/kotlin/com/catspell/api/push/presence/StompPresenceListener.kt` - STOMP events → registry mutations
- `src/test/kotlin/com/catspell/api/push/PresenceRegistryTest.kt` - Unit tests for all state transitions

## Decisions Made
- Chat topic prefix `/topic/chat/` defined as a constant in both classes to stay byte-identical to `ChatService.sendMessage`'s published destination.
- Listeners are intentionally synchronous (not `@Async`) so registry state stays ordered against the connection lifecycle.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- IDE Kotlin analyzer reported false-positive "unresolved reference" / stdlib metadata-version errors (analyzer 2.1.0 vs project stdlib 2.4.0). Confirmed non-issues: `./gradlew compileKotlin compileTestKotlin` and the unit tests both pass.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Registry exposes `isOnline` / `isViewingConversation` — ready for Plan 02's `PushNotificationService` send decision (D-02, D-04).
- No changes to existing chat/match code; decoupling preserved.

---
*Phase: 09-notification-triggers-smart-delivery*
*Completed: 2026-07-29*
