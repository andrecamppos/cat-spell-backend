---
phase: 12-account-credentials
plan: 05
subsystem: testing
tags: [kotlin, junit, mockmvc, testcontainers, mockk, integration]

requires:
  - phase: 12-account-credentials
    provides: change-password/change-email/confirm-email-change endpoints + services (12-01..12-04)
provides:
  - AccountCredentialsIntegrationTest proving ACCT-01..05 end-to-end
affects: [account-credentials]

tech-stack:
  added: []
  patterns:
    - "Testcontainers + MockMvc + mocked EmailSender token capture, Bearer-auth on authenticated endpoints"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/auth/AccountCredentialsIntegrationTest.kt
  modified:
    - src/test/resources/application.yml

key-decisions:
  - "12-05 is a verification-only plan over behavior already built in 12-01..12-04, so the TDD RED→GREEN cycle collapses: the tests are GREEN on first run (no separate implementation step)."

patterns-established:
  - "Confirm token captured from the email whose `to` equals the pending new address, proving the confirm email targets the NEW address."

requirements-completed: [ACCT-01, ACCT-02, ACCT-03, ACCT-04, ACCT-05]

coverage:
  - id: D1
    description: "change-password: wrong current password -> 403 INVALID_CURRENT_PASSWORD (unchanged); success -> no tokens + new password logs in + pre-change refresh revoked; short password -> 400."
    requirement: "ACCT-01"
    verification:
      - kind: integration
        ref: "AccountCredentialsIntegrationTest#ACCT-01 wrong password / ACCT-02 successful / ACCT-01 short password"
        status: pass
    human_judgment: false
  - id: D2
    description: "change-email to a taken address -> 409 with no confirm email and no pending row."
    requirement: "ACCT-05"
    verification:
      - kind: integration
        ref: "AccountCredentialsIntegrationTest#ACCT-05 taken address"
        status: pass
    human_judgment: false
  - id: D3
    description: "change-email emails the NEW address, account email unchanged until confirm; confirm swaps email, stamps verified, revokes pre-confirm sessions; reused/unknown/expired tokens rejected."
    requirement: "ACCT-04"
    verification:
      - kind: integration
        ref: "AccountCredentialsIntegrationTest#ACCT-03 ACCT-04 request+confirm / ACCT-04 reused-unknown-expired"
        status: pass
    human_judgment: false

duration: 9min
completed: 2026-08-19
status: complete
---

# Phase 12 Plan 05: Account-Credentials Integration Tests Summary

**AccountCredentialsIntegrationTest drives the real HTTP endpoints against Testcontainers Postgres + a mocked EmailSender to prove all of ACCT-01..05: 403 INVALID_CURRENT_PASSWORD, revoke-all + no-token on password change, 409 on taken email, confirm-only email swap + verified stamp + revoke, and reused/expired/unknown token rejection.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-08-19T15:39:57Z
- **Completed:** 2026-08-19T15:49:11Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- New `AccountCredentialsIntegrationTest` (5 test methods) covering every ACCT-01..05 behavior end-to-end via MockMvc + Bearer auth, capturing confirm tokens from the mocked `EmailSender`.
- Change-password cases: wrong password → 403 `INVALID_CURRENT_PASSWORD` (unchanged), success → empty body + new-password login + old refresh token rejected at `/api/auth/refresh`, short password → 400.
- Change-email cases: 409 on a taken address (no email sent), 202 + confirm email to the NEW address with the account email unchanged until confirm, confirm swaps the email + stamps `email_verified_at` + revokes sessions, and reused/unknown/expired confirm tokens are rejected with no further swap.
- Full `./gradlew test` suite stays green — no regressions to prior auth/email flows.

## Task Commits

1. **Deviation fix: confirm-email-change-url in test yml** - `a2fde08` (fix)
2. **Task 1: change-password cases** - `87730f4` (test)
3. **Task 2: change-email request/confirm cases** - `2c618f9` (test)

## Files Created/Modified
- `src/test/kotlin/com/catspell/api/auth/AccountCredentialsIntegrationTest.kt` - The ACCT-01..05 integration test.
- `src/test/resources/application.yml` - Added the required `app.confirm-email-change-url` test key (see deviation).

## Decisions Made
- Since the feature was fully built in 12-01..12-04, the TDD cycle for this verification-only plan collapses to GREEN-on-first-run; the tests fail-fast rule is satisfied because there is no un-built behavior to RED against.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added `app.confirm-email-change-url` to the test application.yml**
- **Found during:** Task 1 (first `@SpringBootTest` boot after 12-02's renderer existed)
- **Issue:** `src/test/resources/application.yml` shadows the main one and defined an `app:` block without `confirm-email-change-url`; `EmailChangeEmailRenderer`'s `@Value("${app.confirm-email-change-url}")` has no inline default, so the Spring context (for ALL `@SpringBootTest`) would fail to start.
- **Fix:** Added `confirm-email-change-url: catspell://confirm-email-change` under the test yml `app:` block.
- **Files modified:** src/test/resources/application.yml
- **Verification:** `./gradlew test` (full suite) boots and passes.
- **Committed in:** `a2fde08`

---

**Total deviations:** 1 auto-fixed (1 blocking).
**Impact on plan:** Necessary for any Spring Boot test to start; no scope creep — a required config key that 12-02 added to the main yml but not the shadowing test yml.

## Issues Encountered
None beyond the deviation above. The Hikari "connection has been closed" / DDL-shutdown warnings at suite teardown are Testcontainers container-stop noise, not test failures (BUILD SUCCESSFUL, exit 0).

## Next Phase Readiness
- Phase 12 behavior is fully proven end-to-end; ready for phase verification and completion.

---
*Phase: 12-account-credentials*
*Completed: 2026-08-19*

## Self-Check: PASSED
