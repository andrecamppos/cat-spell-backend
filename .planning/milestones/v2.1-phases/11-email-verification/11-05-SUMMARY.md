---
phase: 11-email-verification
plan: 05
subsystem: testing
tags: [kotlin, junit, mockmvc, testcontainers, bucket4j, flyway, email-verification]

# Dependency graph
requires:
  - phase: 11-email-verification
    provides: "Plan 04 register no-token 201 + login gate + verify/resend endpoints; Plan 01 V17 backfill + email_verified_at; Plan 03 RateLimitFilter.AUTH_PATHS resend entry"
provides:
  - BaseIntegrationTest.markEmailVerified(email) shared helper
  - Whole integration suite migrated to the no-token register + login hard-gate contract (green)
  - RateLimitIntegrationTest resend-verification per-IP 429 case (VERIFY-04)
  - GrandfatherMigrationTest proving the V17 backfill unlocks legacy NULL-verified accounts idempotently (VERIFY-05)
affects: [12-account-credentials]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Tests promote users past the login gate via markEmailVerified (real column write), never a gate-off flag"
    - "register-then-authenticate helpers acquire tokens from the LOGIN response, not register"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/auth/GrandfatherMigrationTest.kt
  modified:
    - src/test/kotlin/com/catspell/api/BaseIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/auth/AuthIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/auth/RefreshTokenIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/auth/PasswordResetIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/common/RateLimitIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/common/HealthEndpointIntegrationTest.kt
    - "15 feature-package integration tests (discovery/push/profile/chat/match/cat)"

key-decisions:
  - "markEmailVerified writes email_verified_at directly (jdbcTemplate) so the real login gate stays exercised (VERIFY-03)"
  - "Feature-test register helpers now register -> markEmailVerified -> login and read tokens from login (D-01)"

patterns-established:
  - "Async STOMP chat tests poll the persisted message count WHILE STILL CONNECTED before disconnecting, avoiding outbound-flush drops"

requirements-completed: [VERIFY-03, VERIFY-04, VERIFY-05]

coverage:
  - id: D1
    description: "Whole integration suite passes under the no-token register + login hard-gate contract (VERIFY-03)"
    requirement: "VERIFY-03"
    verification:
      - kind: integration
        ref: "./gradlew test (249 tests, 0 failures, 1 skipped)"
        status: pass
    human_judgment: false
  - id: D2
    description: "resend-verification is per-IP rate-limited: (capacity+1)th request from one IP returns 429 (VERIFY-04)"
    requirement: "VERIFY-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitIntegrationTest.kt#should rate limit resend-verification endpoint"
        status: pass
    human_judgment: false
  - id: D3
    description: "V17 grandfather backfill sets email_verified_at = created_at for a legacy NULL row, which can then log in; idempotent and non-overwriting (VERIFY-05)"
    requirement: "VERIFY-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/GrandfatherMigrationTest.kt#VERIFY-05 - backfill grandfathers a NULL-verified legacy user who can then log in"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/GrandfatherMigrationTest.kt#VERIFY-05 - backfill is idempotent and does not overwrite an already-verified row"
        status: pass
    human_judgment: false

# Metrics
duration: 47min
completed: 2026-08-12
status: complete
---

# Phase 11 Plan 05: Integration-suite migration Summary

**Migrated the entire integration suite to the no-token register + login hard-gate contract via a shared `markEmailVerified` helper, and added the resend-verification per-IP rate-limit and V17 grandfather-backfill proofs — `./gradlew test` green (249 passed, 1 pre-existing skip).**

## Performance

- **Duration:** 47 min
- **Started:** 2026-08-12T21:52:28Z
- **Completed:** 2026-08-12T22:40:02Z
- **Tasks:** 3
- **Files modified:** 22

## Accomplishments
- Added `BaseIntegrationTest.markEmailVerified(email)` (jdbcTemplate `UPDATE users SET email_verified_at = NOW()`) and made `jdbcTemplate` protected for subclass access.
- Migrated all register-then-authenticate helpers across ~19 test files to `register -> markEmailVerified -> login`, reading tokens from the LOGIN response (register now returns no tokens). Rewrote `AuthIntegrationTest` to assert the new contracts directly: register 201/no-tokens, login-unverified 403 `EMAIL_NOT_VERIFIED`, login-after-verify 200/tokens.
- Added `RateLimitIntegrationTest.should rate limit resend-verification endpoint` proving the per-IP 429 (AUTH_PATHS membership, VERIFY-04).
- Added `GrandfatherMigrationTest` proving the V17 backfill stamps a legacy NULL-verified row with `created_at`, lets it log in, and is idempotent/non-overwriting (VERIFY-05).
- Full suite green: 249 tests, 0 failures, 1 skipped.

## Task Commits

1. **Task 1: markEmailVerified helper + auth-package migration** - `a414810` (test)
2. **Task 2: feature-package register-helper migration (15 files)** - `34fe3a3` (test)
3. **Task 3: resend-verification rate-limit + GrandfatherMigrationTest** - `590e5da` (test)

## Files Created/Modified
- `src/test/kotlin/com/catspell/api/BaseIntegrationTest.kt` - markEmailVerified helper + protected jdbcTemplate
- `src/test/kotlin/com/catspell/api/auth/AuthIntegrationTest.kt` - rewritten for the new contract + 403 gate assertion
- `src/test/kotlin/com/catspell/api/auth/RefreshTokenIntegrationTest.kt` - register-verify-login helper; register-no-tokens assertion
- `src/test/kotlin/com/catspell/api/auth/PasswordResetIntegrationTest.kt` - register helper verifies before login
- `src/test/kotlin/com/catspell/api/common/RateLimitIntegrationTest.kt` - resend-verification 429 case
- `src/test/kotlin/com/catspell/api/auth/GrandfatherMigrationTest.kt` - VERIFY-05 backfill proof (new)
- 15 feature-package integration tests - uniform register-verify-login helper migration (incl. chat STOMP robustness fix)

## Decisions Made
- Promote users via a real `email_verified_at` write, never a gate-off flag (keeps the gate honest, VERIFY-03).
- Feature helpers read tokens from login, preserving each helper's original signature and business assertions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Made the async STOMP chat tests deterministic**
- **Found during:** Task 2 (full `./gradlew test`)
- **Issue:** `ChatIntegrationTest`'s `cursor pagination` (35 msgs) and `newest first` (5 msgs) tests used fire-and-forget STOMP sends with fixed `Thread.sleep` settles. In this (podman) environment they intermittently lost one message — `disconnect()` raced the async outbound-channel flush, and a rapid 50 ms burst dropped a frame — yielding 34/4 instead of 35/5. This is a pre-existing test fragility, unrelated to the Phase 11 auth-contract change (token acquisition cannot drop a chat message).
- **Fix:** Poll the persisted message count WHILE STILL CONNECTED until all messages land, then disconnect; add a post-connect warm-up; and slow the 35-message burst from 50 ms to 100 ms between sends. Assertions (30+5 pagination, 5 newest-first) are unchanged.
- **Files modified:** src/test/kotlin/com/catspell/api/chat/ChatIntegrationTest.kt
- **Verification:** ChatIntegrationTest green in isolation and in the full suite.
- **Committed in:** `34fe3a3` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Robustness-only test change; no production code or assertion intent altered. No scope creep.

## Issues Encountered
- A transient Testcontainers `ContainerLaunchException` (podman VM hiccup) failed one ChatIntegrationTest run; a re-run passed. Environment flake, not a code issue.
- Testcontainers requires a Docker-compatible socket; runs used `DOCKER_HOST` pointed at the podman machine socket with `TESTCONTAINERS_RYUK_DISABLED=true` (documented in AGENTS.md as a podman project).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 11 (Email Verification) is complete: VERIFY-01..05 implemented and proven, the whole suite is green under the new auth contract. Ready for Phase 12 (Account Credentials).

## Self-Check: PASSED

---
*Phase: 11-email-verification*
*Completed: 2026-08-12*
