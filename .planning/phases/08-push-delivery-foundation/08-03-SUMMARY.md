---
phase: 08-push-delivery-foundation
plan: 03
subsystem: api
tags: [push, fcm, token-pruning, mockk, testing, smoke-test]

requires:
  - phase: 08-push-delivery-foundation
    provides: "PushProvider abstraction (plan 02), DeviceTokenService.deactivateToken (plan 01)"
provides:
  - PushSendService send seam with UNREGISTERED-only token pruning
  - mocked-provider contract tests (payload shape + pruning branches)
  - disabled-by-default validate_only FCM smoke test
affects: [phase-09]

tech-stack:
  added: []
  patterns:
    - "Pruning orchestration lives in PushSendService, not in PushProvider implementations"
    - "@Bean @Primary mockk() PushProvider via nested @TestConfiguration for Spring-context provider mocking"
    - "@Tag(smoke) + @Disabled for opt-in real-credential tests excluded from default CI"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/push/service/PushSendService.kt
    - src/test/kotlin/com/catspell/api/push/TokenPruningTest.kt
    - src/test/kotlin/com/catspell/api/push/PushProviderContractTest.kt
    - src/test/kotlin/com/catspell/api/push/FcmSmokeTest.kt
  modified: []

key-decisions:
  - "D-04: UNREGISTERED soft-deactivates the token (active=false + deactivatedAt); no hard-delete"
  - "D-10: mocked-provider contract tests run in normal CI (no real Firebase)"
  - "D-11: validate_only dry-run is a separate @Tag(smoke)+@Disabled test, opt-in with real creds"
  - "Only UNREGISTERED prunes; SUCCESS and ERROR (transient) leave the token active"

patterns-established:
  - "Thin send seam (PushSendService) that Phase 9 triggers will reuse — no send-decision logic"

requirements-completed: [PUSH-03, PUSH-12]

coverage:
  - id: D1
    description: "PushSendService prunes the token only on UNREGISTERED; SUCCESS/ERROR leave it active"
    requirement: "PUSH-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/TokenPruningTest.kt#UNREGISTERED result prunes the token"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/TokenPruningTest.kt#ERROR result does not prune the token"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/TokenPruningTest.kt#SUCCESS result does not prune the token"
        status: pass
    human_judgment: false
  - id: D2
    description: "Payload shape (title/body/data) passed to the provider is asserted against a captured PushPayload"
    requirement: "PUSH-12"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/push/PushProviderContractTest.kt#send passes the expected payload shape to the provider"
        status: pass
    human_judgment: false
  - id: D3
    description: "validate_only dry-run smoke test is disabled by default and excluded from the standard test task"
    requirement: "PUSH-12"
    verification:
      - kind: other
        ref: "gradlew test --tests com.catspell.api.push.FcmSmokeTest -> skipped (BUILD SUCCESSFUL, @Disabled + @Tag(smoke))"
        status: pass
    human_judgment: false
  - id: D4
    description: "Real FCM validate_only round-trip against a live project (opt-in smoke test)"
    verification: []
    human_judgment: true
    rationale: "Requires real FIREBASE_CREDENTIALS_BASE64 and network to Firebase; disabled-by-default, exercised manually only."

duration: ~25 min
completed: 2026-07-17
status: complete
---

# Phase 8 Plan 3: Dead-Token Pruning & Provider-Mocked Verification Summary

**`PushSendService` send seam that soft-deactivates FCM `UNREGISTERED` tokens (only), with mocked-provider contract tests for payload shape + pruning branches and a disabled-by-default validate_only smoke test.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-07-17
- **Tasks:** 3
- **Files modified:** 4 created

## Accomplishments
- `PushSendService.send()` calls `PushProvider` and prunes via `DeviceTokenService.deactivateToken` ONLY on `UNREGISTERED` (D-04/PUSH-03)
- `TokenPruningTest` proves UNREGISTERED deactivates while ERROR/SUCCESS leave the token active (mitigates T-08-06 mass-deactivation)
- `PushProviderContractTest` captures the `PushPayload` and asserts title/body/data (PUSH-12/D-10)
- `FcmSmokeTest` is `@Tag("smoke")` + `@Disabled`, reads creds from env, dry-runs against real Firebase, and never runs in default CI (D-11)
- Pruning lives in `PushSendService`, keeping the providers Firebase-confined and reusable by Phase 9

## Task Commits

1. **Task 1: PushSendService with UNREGISTERED pruning** - `3ae1636` (feat)
2. **Task 2: mocked-provider contract tests** - `fdfd275` (test)
3. **Task 3: disabled-by-default smoke test** - `a05f4f8` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/push/service/PushSendService.kt` - send seam + pruning
- `src/test/kotlin/com/catspell/api/push/TokenPruningTest.kt` - pruning branch tests
- `src/test/kotlin/com/catspell/api/push/PushProviderContractTest.kt` - payload-shape contract
- `src/test/kotlin/com/catspell/api/push/FcmSmokeTest.kt` - disabled-by-default smoke test

## Decisions Made
None beyond the plan (D-04/D-10/D-11 followed as written).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- **Real compile error (fixed):** `PushProviderContractTest` initially imported `io.mockk.capture`, which does not exist as a top-level symbol — `capture(slot)` is a `MockKMatcherScope` receiver method available inside `every { }` (like `any()`). Removed the import; tests compile and pass.
- **Pre-existing flaky suite failures (NOT Phase 8):** The full `./gradlew test` run shows `DiscoveryIntegrationTest` failures caused by shared-DB test pollution (one static Postgres container, no per-test cleanup, default feed page size). Proven pre-existing: DiscoveryIntegrationTest passes in isolation, and the full suite still fails (3 failures) with the entire `push` package excluded. Phase 8 tests create users without profiles/locations, so they cannot appear in the discovery feed. Documented in `deferred-items.md`; out of Phase 8 scope. The full push suite passes and `FcmSmokeTest` is skipped.

## User Setup Required
None - no external service configuration required for CI/local.

## Next Phase Readiness
- `PushSendService.send(token, payload)` is the seam Phase 9 triggers (match/message notifications) will call
- Dead-token pruning verified; Phase 9 can rely on UNREGISTERED tokens being deactivated
- Phase 8 complete — ready for Phase 9 (Notification Triggers & Smart Delivery)

---
*Phase: 08-push-delivery-foundation*
*Completed: 2026-07-17*
