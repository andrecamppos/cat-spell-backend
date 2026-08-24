---
phase: 12-account-credentials
plan: 04
subsystem: api
tags: [kotlin, spring, rest, security, jwt, validation]

requires:
  - phase: 12-account-credentials
    provides: AuthService.changePassword/confirmEmailChange + EmailChangeService.requestChange (12-03)
provides:
  - POST /api/auth/change-password (authenticated)
  - POST /api/auth/change-email (authenticated)
  - POST /api/auth/confirm-email-change (public)
  - ChangePasswordRequest / ChangeEmailRequest / ConfirmEmailChangeRequest DTOs
affects: [12-05]

tech-stack:
  added: []
  patterns:
    - "Three-place public-endpoint whitelist (SecurityConfig + JwtAuthenticationFilter + RateLimitFilter)"
    - "extractUserId() principal resolution for authenticated endpoints"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt
    - src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
    - src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt

key-decisions:
  - "Only confirm-email-change is public (D-07); the two change endpoints keep JWT auth and are absent from permitAll + shouldNotFilter."

patterns-established:
  - "change-email added to RateLimitFilter.AUTH_PATHS for per-IP throttling on top of the per-target-email guard in the service."

requirements-completed: [ACCT-01, ACCT-02, ACCT-03, ACCT-04, ACCT-05]

coverage:
  - id: D1
    description: "Three request DTOs with correct @field: validation (Size(min=8) newPassword, Email newEmail, plain token)."
    requirement: "ACCT-01"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin. Validation behavior (400s) asserted in 12-05."
        status: pass
    human_judgment: false
  - id: D2
    description: "change-password + change-email endpoints authenticated (resolve caller via extractUserId, no token body); confirm-email-change public; none returns tokens."
    requirement: "ACCT-03"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin; grep confirms @SecurityRequirements posture. HTTP behavior in 12-05."
        status: pass
    human_judgment: false
  - id: D3
    description: "confirm-email-change whitelisted in SecurityConfig + JwtAuthenticationFilter; change-email in RateLimitFilter.AUTH_PATHS; change endpoints NOT whitelisted."
    requirement: "ACCT-04"
    verification:
      - kind: unit
        ref: "grep -R change-password src/main/kotlin/com/catspell/api/common => no match; confirm-email-change present in both auth-bypass files."
        status: pass
    human_judgment: false

duration: 13min
completed: 2026-08-19
status: complete
---

# Phase 12 Plan 04: Credential-Change Endpoints & Security Wiring Summary

**Three DTOs + three endpoints (change-password/change-email authenticated, confirm-email-change public) over the 12-03 services, with confirm-email-change whitelisted in exactly the three security places and change-email added to per-IP throttling.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-08-19T15:13:17Z
- **Completed:** 2026-08-19T15:27:08Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Added `ChangePasswordRequest`, `ChangeEmailRequest`, `ConfirmEmailChangeRequest` DTOs with the project's `@field:` validation idiom (reusing `GenericMessageResponse` for the ack).
- Added the three `AuthController` handlers + a private `extractUserId()`; the two change endpoints stay authenticated (no `@SecurityRequirements`) and delegate to the 12-03 services, and confirm-email-change is public — none returns a token body.
- Wired the three-place whitelist so only `confirm-email-change` is public (SecurityConfig permitAll + JwtAuthenticationFilter.shouldNotFilter), and added `change-email` to `RateLimitFilter.AUTH_PATHS` for per-IP throttling.

## Task Commits

1. **Task 1: change-credential request DTOs** - `f3b50a3` (feat)
2. **Task 2: three endpoints + extractUserId** - `b2efb89` (feat)
3. **Task 3: security whitelist edits** - `9b7518b` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt` - Three new request DTOs.
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` - Three endpoints + extractUserId + EmailChangeService injection.
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` - permitAll adds confirm-email-change.
- `src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt` - shouldNotFilter adds confirm-email-change.
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt` - AUTH_PATHS adds change-email.

## Decisions Made
- Followed plan/D-07 exactly: exactly one public route.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## Next Phase Readiness
- API surface complete; 12-05 can drive the endpoints end-to-end with Testcontainers.

---
*Phase: 12-account-credentials*
*Completed: 2026-08-19*

## Self-Check: PASSED
