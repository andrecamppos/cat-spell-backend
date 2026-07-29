---
phase: 09-notification-triggers-smart-delivery
plan: 03
subsystem: api
tags: [spring-events, async, transactional-event-listener, push, testcontainers, awaitility]

requires:
  - phase: 09-notification-triggers-smart-delivery
    provides: PushNotificationService (notifyMatch / notifyMessage) from Plan 02
  - phase: 05-real-time-chat
    provides: ChatService.sendMessage (recipient, senderName, preview) + STOMP fan-out
  - phase: 04-discovery-matching
    provides: MatchService.createMatch idempotent new-match path
provides:
  - MatchCreatedEvent / MessageSentEvent domain events (IDs + strings only)
  - PushNotificationListener (@Async @TransactionalEventListener AFTER_COMMIT)
  - Event publication wired into MatchService.createMatch and ChatService.sendMessage
affects: []

tech-stack:
  added: []
  patterns:
    - "@Async @TransactionalEventListener(AFTER_COMMIT) to run side effects off the request thread, post-commit"
    - "Chat/match decoupled from push via ApplicationEventPublisher (events carry primitives only)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/push/event/PushEvents.kt
    - src/main/kotlin/com/catspell/api/push/event/PushNotificationListener.kt
    - src/test/kotlin/com/catspell/api/push/PushTriggerIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/match/service/MatchService.kt
    - src/main/kotlin/com/catspell/api/chat/service/ChatService.kt

key-decisions:
  - "Match event published only on the new-save branch (not existing-match return or duplicate-key fallback) to avoid duplicate notifications (T-9-08)"
  - "Listener swallows+logs push exceptions so a failure never propagates to or rolls back the domain write"
  - "Integration test mocks PushNotificationService via @TestConfiguration @Primary MockK bean (no springmockk dependency in repo)"

patterns-established:
  - "Events published inside the existing @Transactional method so AFTER_COMMIT binds to that transaction"
  - "Awaitility (transitive via spring-boot-starter-test) polls for async side-effect assertions"

requirements-completed: [PUSH-10]

coverage:
  - id: D1
    description: "A persisted chat message dispatches notifyMessage asynchronously AFTER the transaction commits (PUSH-10, D-07)"
    requirement: "PUSH-10"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/PushTriggerIntegrationTest.kt#message send dispatches notifyMessage asynchronously after the transaction commits"
        status: pass
    human_judgment: false
  - id: D2
    description: "A slow/failing push neither blocks nor rolls back message persistence; sendMessage returns and the row is committed (PUSH-10, T-9-02)"
    requirement: "PUSH-10"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/PushTriggerIntegrationTest.kt#a failing push neither blocks nor rolls back message persistence"
        status: pass
    human_judgment: false
  - id: D3
    description: "MatchService/ChatService publish events from within their @Transactional write paths without behavior regression on the request path"
    verification:
      - kind: integration
        ref: "./gradlew test --tests SwipeMatchIntegrationTest --tests ChatIntegrationTest --tests MatchIntegrationTest"
        status: pass
    human_judgment: false
  - id: D4
    description: "createMatch publishes only on a genuinely new match, not on the existing-match return or duplicate-key fallback (T-9-08)"
    verification:
      - kind: other
        ref: "source review: MatchService.createMatch publishes inside the new-save branch only"
        status: pass
    human_judgment: true
    rationale: "No dedicated duplicate-suppression test was added this plan; correctness is established by code placement + review, so a human should confirm no duplicate-match notification in UAT."

duration: 21 min
completed: 2026-07-29
status: complete
---

# Phase 9 Plan 03: Async Notification Triggers Summary

**`@Async @TransactionalEventListener(AFTER_COMMIT)` push pipeline: `MatchService`/`ChatService` publish ID-only domain events that `PushNotificationListener` consumes off-thread after commit and delegates to `PushNotificationService`, so a slow/failing FCM call never blocks or rolls back persistence (PUSH-10).**

## Performance

- **Duration:** 21 min
- **Started:** 2026-07-29T15:09:00Z
- **Completed:** 2026-07-29T15:30:06Z
- **Tasks:** 3
- **Files modified:** 5 (3 created, 2 modified)

## Accomplishments
- `MatchCreatedEvent` / `MessageSentEvent` carrying only IDs + precomputed strings (no JPA entities → no lazy-load in the async listener).
- `PushNotificationListener` with two `@Async @TransactionalEventListener(AFTER_COMMIT)` handlers that delegate to Plan 02's service and swallow/log push failures.
- Event publication wired into `MatchService.createMatch` (new-match branch only) and `ChatService.sendMessage` (reusing recipient/senderName/100-char preview), keeping chat/match coupled only to `ApplicationEventPublisher`.
- Integration test proving async, commit-gated dispatch and non-blocking behavior on push failure (PUSH-10).

## Task Commits

1. **Task 1: Domain events + async listener** - `11a3409` (feat)
2. **Task 2: Publish from transactional write paths** - `85cdec0` (feat)
3. **Task 3: Trigger integration test** - `cca37cc` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/push/event/PushEvents.kt` - MatchCreatedEvent, MessageSentEvent
- `src/main/kotlin/com/catspell/api/push/event/PushNotificationListener.kt` - Async AFTER_COMMIT handlers
- `src/main/kotlin/com/catspell/api/match/service/MatchService.kt` - Publish MatchCreatedEvent on new match
- `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt` - Publish MessageSentEvent after fan-out
- `src/test/kotlin/com/catspell/api/push/PushTriggerIntegrationTest.kt` - Async/commit-gated/non-blocking tests

## Decisions Made
- Mocked `PushNotificationService` via a `@TestConfiguration @Primary` MockK bean because the repo has no `springmockk`/`@MockkBean`; avoids bean-override conflict by using a distinct bean name.
- Awaitility resolved transitively via `spring-boot-starter-test`; no `build.gradle.kts` change required.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- **Pre-existing full-suite flakiness (out of scope):** `./gradlew test` (213 tests) shows 4 `DiscoveryIntegrationTest` feed-content failures ("X should appear in feed"). These are cross-test DB-state pollution in the geospatial feed suite, NOT caused by this phase: `DiscoveryIntegrationTest` passes in isolation and also passes when run alongside the new `PushTriggerIntegrationTest`. This phase's changes are additive event publishing and touch no discovery/feed logic. All Phase 9 tests plus the match/chat/swipe regression tests are green. Recommend a separate test-isolation fix (e.g. per-test DB cleanup) tracked outside this milestone.
- IDE Kotlin analyzer emitted false-positive stdlib metadata-version errors (analyzer 2.1.0 vs project stdlib 2.4.0); disproven by the passing gradle compile + test runs.

## User Setup Required
None - no external service configuration required. (Real FCM delivery still requires the Firebase credentials from Phase 8's USER-SETUP when `push.enabled=true`.)

## Next Phase Readiness
- Phase 9 complete: match/message triggers → smart delivery decision → async, commit-gated FCM dispatch is wired end-to-end.
- Suggested follow-up: dedicated duplicate-match-suppression test (D4) during UAT, and a project-wide integration-test isolation fix for the discovery feed flakiness.

---
*Phase: 09-notification-triggers-smart-delivery*
*Completed: 2026-07-29*
