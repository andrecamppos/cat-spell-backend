---
phase: 08-push-delivery-foundation
plan: 02
subsystem: infra
tags: [push, fcm, firebase-admin, health, spring-conditional, actuator]

requires:
  - phase: 08-push-delivery-foundation
    provides: com.catspell.api.push module layout (plan 01)
provides:
  - PushProvider abstraction (PushPayload, PushSendStatus, PushResult) — Firebase-neutral
  - LoggingPushProvider (no-op, active when push.enabled=false/missing)
  - FcmPushProvider (firebase-admin, active when push.enabled=true; UNREGISTERED mapped distinctly)
  - FirebaseConfig fail-fast credential wiring (firebaseApp/firebaseMessaging beans)
  - FirebaseHealthIndicator (actuator component "firebase", config status without live send)
  - push config block + FIREBASE_CREDENTIALS_BASE64 env slot
affects: [push-delivery, push-triggers, phase-09]

tech-stack:
  added:
    - "com.google.firebase:firebase-admin:9.9.0"
  patterns:
    - "@ConditionalOnProperty(push.enabled) provider selection (true=FCM, false/missing=no-op)"
    - "Firebase types confined to FcmPushProvider/FirebaseConfig; call sites depend only on PushProvider"
    - "base64 env-var credentials (no secrets in repo); fail-fast at startup on missing/invalid creds"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/push/service/PushProvider.kt
    - src/main/kotlin/com/catspell/api/push/service/LoggingPushProvider.kt
    - src/main/kotlin/com/catspell/api/push/service/FcmPushProvider.kt
    - src/main/kotlin/com/catspell/api/push/config/FirebaseConfig.kt
    - src/main/kotlin/com/catspell/api/common/health/FirebaseHealthIndicator.kt
    - src/test/kotlin/com/catspell/api/push/PushProviderSelectionTest.kt
    - src/test/kotlin/com/catspell/api/push/FirebaseHealthIntegrationTest.kt
  modified:
    - build.gradle.kts
    - src/main/resources/application.yml
    - .env.example

key-decisions:
  - "D-06: service-account JSON supplied as base64 env var FIREBASE_CREDENTIALS_BASE64; no secrets in repo"
  - "D-07: push.enabled gates behavior — true=fail-fast + FCM, false/missing=no-op logging provider"
  - "D-08: Firebase health indicator reports config status only (no live FCM send)"
  - "D-09: firebase-admin behind PushProvider; abstraction has no Firebase types"
  - "@ConditionalOnProperty resolves to org.springframework.boot.autoconfigure.condition in Boot 4.0.6 (verified against the jar)"

patterns-established:
  - "Conditional provider bean selection via push.enabled"
  - "ObjectProvider<FirebaseApp> in the health indicator so it works whether or not FirebaseConfig is active"

requirements-completed: [PUSH-09, PUSH-11]

coverage:
  - id: D1
    description: "PushProvider abstraction is provider-neutral (no Firebase types leak into interface/payload)"
    requirement: "PUSH-09"
    verification:
      - kind: other
        ref: "grep -q com.google.firebase src/main/kotlin/com/catspell/api/push/service/PushProvider.kt (returns no match)"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushProviderSelectionTest.kt#logging provider selected when push disabled or missing"
        status: pass
    human_judgment: false
  - id: D2
    description: "push.enabled selects LoggingPushProvider (false/missing) vs FcmPushProvider (true); FcmPushProvider maps UNREGISTERED to a distinct status"
    requirement: "PUSH-09"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushProviderSelectionTest.kt#logging provider selected when push disabled or missing"
        status: pass
    human_judgment: false
  - id: D3
    description: "Fail-fast at startup when push.enabled=true and credentials are missing/blank"
    requirement: "PUSH-11"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushProviderSelectionTest.kt#context fails fast when push enabled and credentials blank"
        status: pass
    human_judgment: false
  - id: D4
    description: "FirebaseHealthIndicator reports config status (UP/push=disabled) without a live send and does not fail the aggregate when disabled"
    requirement: "PUSH-11"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/FirebaseHealthIntegrationTest.kt#firebase health reports disabled and stays UP when authorized"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/FirebaseHealthIntegrationTest.kt#anonymous health is UP even with push disabled"
        status: pass
    human_judgment: false
  - id: D5
    description: "Real FCM send path (FirebaseMessaging.send) against a live Firebase project"
    verification: []
    human_judgment: true
    rationale: "FcmPushProvider only activates with real credentials (push.enabled=true); exercised by the disabled-by-default smoke test in plan 03, not by CI."

duration: ~20 min
completed: 2026-07-17
status: complete
---

# Phase 8 Plan 2: PushProvider Abstraction, FCM Delivery, Firebase Config & Health Summary

**Provider-neutral `PushProvider` abstraction with `push.enabled`-gated selection between a no-op `LoggingPushProvider` and a firebase-admin `FcmPushProvider`, fail-fast credential wiring, and an actuator Firebase health indicator.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-17
- **Tasks:** 5
- **Files modified:** 7 created, 3 modified

## Accomplishments
- `PushProvider`/`PushPayload`/`PushResult`/`PushSendStatus` — no Firebase types leak into the abstraction (grep-clean)
- `LoggingPushProvider` (no-op, masked token log) active by default; `FcmPushProvider` active only when `push.enabled=true`
- `FcmPushProvider` maps `MessagingErrorCode.UNREGISTERED` to `PushResult(UNREGISTERED)` (consumed by plan 03); other failures → `ERROR`
- `FirebaseConfig` fails fast on missing/invalid base64 credentials; guards against double-init
- `FirebaseHealthIndicator` (actuator component `firebase`) reports config status without a live send and keeps the aggregate UP when push is disabled
- firebase-admin 9.9.0 added; `push` config block + `FIREBASE_CREDENTIALS_BASE64` env slot

## Task Commits

1. **Task 1: firebase-admin dependency + push config** - `5c7106d` (chore)
2. **Task 2: PushProvider interface + payload types** - `ac9e251` (feat)
3. **Task 3: LoggingPushProvider + FcmPushProvider** - `3a77d07` (feat)
4. **Task 4: FirebaseConfig fail-fast + FirebaseHealthIndicator** - `5ebd332` (feat)
5. **Task 5: selection/fail-fast/health tests** - `5193d51` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/push/service/PushProvider.kt` - abstraction + result types
- `src/main/kotlin/com/catspell/api/push/service/LoggingPushProvider.kt` - no-op provider
- `src/main/kotlin/com/catspell/api/push/service/FcmPushProvider.kt` - FCM provider
- `src/main/kotlin/com/catspell/api/push/config/FirebaseConfig.kt` - beans + fail-fast
- `src/main/kotlin/com/catspell/api/common/health/FirebaseHealthIndicator.kt` - actuator component
- `src/test/kotlin/com/catspell/api/push/PushProviderSelectionTest.kt` - selection + fail-fast
- `src/test/kotlin/com/catspell/api/push/FirebaseHealthIntegrationTest.kt` - health status
- `build.gradle.kts`, `application.yml`, `.env.example` - dependency + config wiring

## Decisions Made
- Verified `@ConditionalOnProperty` package for Spring Boot 4.0.6 by inspecting the on-classpath jar (`org.springframework.boot.autoconfigure.condition`) rather than assuming — it is unchanged from Boot 3.x.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- IDE Kotlin analyzer (v2.1.0) flags spurious "Unresolved reference" errors against the Kotlin 2.4.0 stdlib. Confirmed non-issues: `./gradlew compileKotlin` and `./gradlew test` both succeed.

## User Setup Required
None for local/CI (push.enabled=false default). Production push will require setting `PUSH_ENABLED=true` and a base64 `FIREBASE_CREDENTIALS_BASE64` — surfaced in `.env.example`. (No USER-SETUP.md: no `user_setup` block in the plan.)

## Next Phase Readiness
- `PushProvider` seam ready for plan 03's `PushSendService` + pruning
- `FcmPushProvider.send(token, payload, dryRun)` overload ready for plan 03's validate_only smoke test
- Ready for plan 08-03 (dead-token pruning & provider-mocked verification)

---
*Phase: 08-push-delivery-foundation*
*Completed: 2026-07-17*
