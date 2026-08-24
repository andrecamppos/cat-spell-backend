---
phase: 12-account-credentials
plan: 03
subsystem: auth
tags: [kotlin, spring, bucket4j, tokens, sessions, passwords]

requires:
  - phase: 12-account-credentials
    provides: EmailChangeRequestRepository (12-01), EmailChangeEmailRenderer + InvalidCurrentPasswordException (12-02)
provides:
  - AuthService.changePassword (ACCT-01/02)
  - AuthService.confirmEmailChange (ACCT-04)
  - EmailChangeService.requestChange (ACCT-03/05)
affects: [12-04, 12-05]

tech-stack:
  added: []
  patterns:
    - "Verify-current-password-before-mutate on credential changes (403 INVALID_CURRENT_PASSWORD)"
    - "Revoke-all-sessions + mint-no-tokens on any credential change (fresh-login invariant)"
    - "Per-target-email Bucket4j anti-inbox-bombing guard"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/auth/service/EmailChangeService.kt
  modified:
    - src/main/kotlin/com/catspell/api/auth/service/AuthService.kt

key-decisions:
  - "Bucket exhaustion on change-email surfaces a real 429 via ResponseStatusException(TOO_MANY_REQUESTS) — no new exception class needed since ResponseEntityExceptionHandler maps it (keeps 12-03 to its two declared files)."
  - "User-by-id not found uses the established userRepository.findById(id).orElseThrow { ResourceNotFoundException } idiom."

patterns-established:
  - "Every credential-change path (changePassword, confirmEmailChange) reuses AuthService.revokeAllUserTokens and returns Unit — no AuthResponse, no createRefreshToken."

requirements-completed: [ACCT-01, ACCT-02, ACCT-03, ACCT-04, ACCT-05]

coverage:
  - id: D1
    description: "changePassword: wrong current password -> 403 INVALID_CURRENT_PASSWORD (no state change); correct -> re-hash + revoke all sessions, no tokens."
    requirement: "ACCT-01"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin; grep confirms revokeAllUserTokens + no createRefreshToken. Behavioral proof in 12-05."
        status: pass
    human_judgment: false
  - id: D2
    description: "confirmEmailChange: atomic markUsed claim (unknown/expired/reused -> InvalidTokenException); on success swaps users.email, stamps emailVerifiedAt, revokes all sessions."
    requirement: "ACCT-04"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin. Behavioral proof (single-use/expiry/swap) in 12-05."
        status: pass
    human_judgment: false
  - id: D3
    description: "EmailChangeService.requestChange: verifies password, rejects taken address (409) before minting, per-email 429 guard, mints hashed single-use token, emails confirm link to the NEW address; never changes users.email."
    requirement: "ACCT-03"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin; grep confirms no user.email assignment + render(newEmail). Behavioral proof in 12-05."
        status: pass
    human_judgment: false

duration: 29min
completed: 2026-08-19
status: complete
---

# Phase 12 Plan 03: Credential-Change Business Logic Summary

**AuthService.changePassword + confirmEmailChange and a new EmailChangeService.requestChange, carrying every ACCT-01..05 invariant: verify-password-403, revoke-all-sessions, no-token-minting, 409-on-taken, and email-swap-only-on-confirm.**

## Performance

- **Duration:** 29 min
- **Started:** 2026-08-19T14:21:44Z
- **Completed:** 2026-08-19T14:50:48Z
- **Tasks:** 3
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- `AuthService.changePassword(userId, currentPassword, newPassword)` re-verifies the current password (throws `InvalidCurrentPasswordException` before any mutation), re-hashes on success, and revokes all sessions — returning Unit with no tokens (D-01/02/03).
- `AuthService.confirmEmailChange(rawToken)` atomically claims the token via `markUsed` (unknown/expired/reused → `InvalidTokenException`), then swaps `users.email`, stamps `emailVerifiedAt`, and revokes all sessions (D-08).
- New `EmailChangeService.requestChange(userId, currentPassword, newEmail)` verifies the password, rejects a taken address with 409 before minting, applies a per-target-email Bucket4j guard, mints a SHA-256-hashed single-use token, and emails the confirm link to the NEW address — never touching `users.email`.

## Task Commits

1. **Tasks 1 & 2: AuthService.changePassword + confirmEmailChange** - `319601d` (feat)
2. **Task 3: EmailChangeService request path** - `24e9109` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` - Added the two credential-change methods + EmailChangeRequestRepository injection.
- `src/main/kotlin/com/catspell/api/auth/service/EmailChangeService.kt` - New change-email request service.

## Decisions Made
- On per-email bucket exhaustion, throw `ResponseStatusException(TOO_MANY_REQUESTS)` (mapped to 429 by the framework) instead of adding a new exception class, keeping the plan to its two declared files.

## Deviations from Plan

None - plan executed exactly as written. (Tasks 1 and 2 share the AuthService.kt file/constructor/import changes, so they were committed together as one atomic AuthService commit.)

## Issues Encountered
None.

## Next Phase Readiness
- 12-04 can now wire the three endpoints to `authService.changePassword`, `emailChangeService.requestChange`, and `authService.confirmEmailChange`.

## Prohibitions Honored
- Neither `changePassword` nor `confirmEmailChange` mints/returns any token (grep: `createRefreshToken`/`AuthResponse` absent from both). `EmailChangeService` never assigns `user.email`.

---
*Phase: 12-account-credentials*
*Completed: 2026-08-19*

## Self-Check: PASSED
