---
phase: 12-account-credentials
plan: 02
subsystem: api
tags: [email, kotlin, spring, problemdetail, rfc7807, config]

requires:
  - phase: 11-email-verification
    provides: EmailVerificationEmailRenderer + EmailNotVerified handler analogs
provides:
  - EmailChangeEmailRenderer (recipient-agnostic confirm-email renderer)
  - app.confirm-email-change-url config key
  - InvalidCurrentPasswordException + 403 handler with code INVALID_CURRENT_PASSWORD
affects: [12-03, 12-04, 12-05]

tech-stack:
  added: []
  patterns:
    - "Distinct 403 RFC-7807 ProblemDetail with a machine-readable code property"
    - "@Value env config with default (no @ConfigurationProperties)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/email/service/EmailChangeEmailRenderer.kt
  modified:
    - src/main/resources/application.yml
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt

key-decisions:
  - "Renderer targets the caller-supplied recipient only (never user.email) so the confirm email goes to the pending new address (D-05)."
  - "Reused the existing DuplicateEmailException 409 handler for the taken-email case (D-06) — no new handler."

patterns-established:
  - "Credential-flow wrong-password surfaces a distinct 403 INVALID_CURRENT_PASSWORD, not a 401, to avoid confusing token-refresh logic (D-02)."

requirements-completed: [ACCT-01, ACCT-03]

coverage:
  - id: D1
    description: "EmailChangeEmailRenderer renders a confirm deep-link (app.confirm-email-change-url?token=<raw>) to the caller-supplied new address."
    requirement: "ACCT-03"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin; grep confirms render(recipientEmail, rawToken) + no user.email. Behavioral proof deferred to 12-05."
        status: pass
    human_judgment: false
  - id: D2
    description: "InvalidCurrentPasswordException maps to a 403 ProblemDetail carrying code=INVALID_CURRENT_PASSWORD."
    requirement: "ACCT-01"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin; grep confirms FORBIDDEN + setProperty code INVALID_CURRENT_PASSWORD. HTTP assertion in 12-05."
        status: pass
    human_judgment: false

duration: 3min
completed: 2026-08-19
status: complete
---

# Phase 12 Plan 02: Change-Email Renderer & Wrong-Password 403 Summary

**Recipient-agnostic EmailChangeEmailRenderer (app.confirm-email-change-url deep-link) plus a distinct 403 INVALID_CURRENT_PASSWORD ProblemDetail for wrong-current-password on the credential endpoints.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-08-19T14:05:31Z
- **Completed:** 2026-08-19T14:09:19Z
- **Tasks:** 2
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- `EmailChangeEmailRenderer.render(recipientEmail, rawToken)` builds a `?token=` deep-link from `app.confirm-email-change-url` and targets the passed recipient only (never `user.email`).
- Added `app.confirm-email-change-url` to `application.yml` with an env-overridable default (`CONFIRM_EMAIL_CHANGE_URL:catspell://confirm-email-change`).
- Added `InvalidCurrentPasswordException` and a `GlobalExceptionHandler` method mapping it to HTTP 403 with `code = INVALID_CURRENT_PASSWORD`, mirroring the EMAIL_NOT_VERIFIED handler.

## Task Commits

1. **Task 1: EmailChangeEmailRenderer + config key** - `5ae2b84` (feat)
2. **Task 2: InvalidCurrentPasswordException + 403 handler** - `0938ab8` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/email/service/EmailChangeEmailRenderer.kt` - Confirm-email renderer to the new address.
- `src/main/resources/application.yml` - New `app.confirm-email-change-url` key.
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` - New `InvalidCurrentPasswordException`.
- `src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt` - New 403 handler with machine-readable code.

## Decisions Made
- Followed plan/D-05/D-06 exactly; no new handler for the taken-email case (existing 409 reused).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## Next Phase Readiness
- 12-03 can now render/send confirm emails to the new address and throw `InvalidCurrentPasswordException` for wrong-password paths.

---
*Phase: 12-account-credentials*
*Completed: 2026-08-19*

## Self-Check: PASSED
