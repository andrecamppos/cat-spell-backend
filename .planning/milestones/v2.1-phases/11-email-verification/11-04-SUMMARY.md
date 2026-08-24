---
phase: 11-email-verification
plan: 04
subsystem: api
tags: [kotlin, spring, rest, mockmvc, testcontainers, mockk, email-verification]

# Dependency graph
requires:
  - phase: 11-email-verification
    provides: "Plan 03 EmailVerificationService (issueAndSend/resend), AuthService.verifyEmail + login gate, three-place whitelist"
provides:
  - VerifyEmailRequest + ResendVerificationRequest DTOs
  - AuthService.register no-token contract (creates unverified user, sends first verification email)
  - POST /api/auth/register (201 GenericMessageResponse), POST /api/auth/verify-email (200), POST /api/auth/resend-verification (202)
  - EmailVerificationIntegrationTest proving VERIFY-01..04 end to end with a stubbed EmailSender
affects: [11-05 legacy test migration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Register returns 201 GenericMessageResponse with no session (verify-then-login contract)"
    - "MockK @Primary EmailSender bean capturing EmailMessage to parse the raw token in integration tests"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt
    - src/main/kotlin/com/catspell/api/auth/service/AuthService.kt
    - src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt
    - src/test/resources/application.yml

key-decisions:
  - "register now returns Unit at the service layer and a 201 GenericMessageResponse at the controller — no tokens (D-01)"
  - "AuthService depends on EmailVerificationService (acyclic — the service does not depend back on AuthService)"

patterns-established:
  - "verify-email returns 200 Void, resend-verification returns 202 with a fixed generic body regardless of email state"

requirements-completed: [VERIFY-01, VERIFY-02, VERIFY-03, VERIFY-04]

coverage:
  - id: D1
    description: "register creates an unverified user, sends exactly one verification email, returns 201 with no tokens (VERIFY-01)"
    requirement: "VERIFY-01"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt#VERIFY-01 - register creates unverified user, sends one email, returns 201 with no tokens"
        status: pass
    human_judgment: false
  - id: D2
    description: "verify-email claims a valid token (200, no tokens), rejects reused/expired/blank/unknown tokens, and sets email_verified_at (VERIFY-02)"
    requirement: "VERIFY-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt#VERIFY-02 - verify-email with valid token returns 200 no tokens and allows login"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt#VERIFY-02 - reused token is 401 and force-expired token is 401"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt#VERIFY-02 - blank token is rejected and unknown token is 401"
        status: pass
    human_judgment: false
  - id: D3
    description: "login hard-gates unverified accounts 403 EMAIL_NOT_VERIFIED after the password check; unknown/wrong-password stays 401 (VERIFY-03)"
    requirement: "VERIFY-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt#VERIFY-03 - login before verification is 403 EMAIL_NOT_VERIFIED, unknown or wrong password is 401"
        status: pass
    human_judgment: false
  - id: D4
    description: "resend-verification returns an identical 202 body for unknown/verified/unverified emails and invalidates prior tokens (VERIFY-04)"
    requirement: "VERIFY-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt#VERIFY-04 - resend returns identical 202 for unverified, unknown, and already-verified emails"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt#VERIFY-04 - resend issues a fresh token and invalidates the prior one"
        status: pass
    human_judgment: false

# Metrics
duration: 11min
completed: 2026-08-12
status: complete
---

# Phase 11 Plan 04: HTTP surface + register contract flip Summary

**Public verification API — register flips to a no-token 201, `POST /verify-email` (200) and `POST /resend-verification` (202) added — proven end to end by a 7-case `EmailVerificationIntegrationTest` with a stubbed EmailSender.**

## Performance

- **Duration:** 11 min
- **Started:** 2026-08-12T21:39:58Z
- **Completed:** 2026-08-12T21:51:13Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Added `VerifyEmailRequest` / `ResendVerificationRequest` DTOs; `AuthService.register` now saves an unverified user and calls `emailVerificationService.issueAndSend(savedUser)`, returning no session (D-01, VERIFY-01).
- `AuthController`: `register` returns 201 `GenericMessageResponse`; `verify-email` returns 200 `Void`; `resend-verification` always returns 202 with a fixed generic body (D-01/D-02/D-04). All three carry `@SecurityRequirements`.
- `EmailVerificationIntegrationTest` (7 tests, all green): register→verify→login happy path, the 403 `EMAIL_NOT_VERIFIED` gate vs generic 401s, single-use/expired/blank/unknown token rejection, resend enumeration-safety (identical 202 bodies), and prior-token invalidation on resend — with a MockK `@Primary` EmailSender (no network, EMAIL-02) and the stored `token_hash` asserted distinct from the raw emailed token.

## Task Commits

1. **Task 1: DTOs + register no-token flip** - `31a81aa` (feat)
2. **Task 2: verify-email + resend-verification endpoints, register 201** - `a9c3bf5` (feat)
3. **Task 3: EmailVerificationIntegrationTest** - `15abec6` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt` - VerifyEmailRequest + ResendVerificationRequest
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` - register no-token flip + EmailVerificationService dependency
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` - register 201 + two new endpoints
- `src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt` - end-to-end VERIFY-01..04 proof
- `src/test/resources/application.yml` - added `app.verify-email-url` (test context boot fix — see Deviations)

## Decisions Made
- register returns Unit (service) / 201 GenericMessageResponse (controller) with no session (D-01).
- Verify token parsed from the captured email body — never from a server URL or log (D-07).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added `app.verify-email-url` to the test application.yml**
- **Found during:** Task 3 (EmailVerificationIntegrationTest)
- **Issue:** `src/test/resources/application.yml` has its own `app:` block (only `reset-password-url`); it does NOT inherit the main `application.yml`. `EmailVerificationEmailRenderer`'s `@Value("\${app.verify-email-url}")` has no annotation-level default, so the Spring context failed to boot with `PlaceholderResolutionException: Could not resolve placeholder 'app.verify-email-url'` and all 7 tests errored.
- **Fix:** Added `verify-email-url: catspell://verify-email` under the test config `app:` block (mirrors how `reset-password-url` is present in both main and test yml).
- **Files modified:** src/test/resources/application.yml
- **Verification:** All 7 tests green after the fix.
- **Committed in:** `15abec6` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for the integration test to boot; consistent with the existing test-config convention. No scope creep.

## Issues Encountered
- A Kotlin backtick test name containing `;` failed compilation (`Name contains illegal characters: ;`); replaced with `,`.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The verification API is public, validated, and enumeration-safe. The register contract change now breaks the legacy register/login-dependent integration tests — Plan 05 migrates the whole suite to the new contract and adds the grandfather-migration + resend rate-limit coverage.

## Self-Check: PASSED

---
*Phase: 11-email-verification*
*Completed: 2026-08-12*
