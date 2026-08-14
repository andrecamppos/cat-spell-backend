---
phase: 11-email-verification
plan: 03
subsystem: auth
tags: [kotlin, spring, bucket4j, email-verification, security, enumeration-safe, single-use-token]

# Dependency graph
requires:
  - phase: 11-email-verification
    provides: "Plan 01 EmailVerificationToken/repository + User.emailVerifiedAt; Plan 02 EmailVerificationEmailRenderer + app.verify-email-url"
  - phase: 10-password-recovery
    provides: "PasswordResetService (enumeration-safe template), AuthService.resetPassword token-claim pattern, three-place security whitelist pattern"
provides:
  - EmailVerificationService.issueAndSend(user) + enumeration-safe resend(email) with per-email Bucket4j guard
  - AuthService.verifyEmail(rawToken) transactional atomic single-use claim stamping email_verified_at, no session
  - AuthService.login EMAIL_NOT_VERIFIED hard-gate after password check
  - EmailNotVerifiedException + 403 ProblemDetail handler with code EMAIL_NOT_VERIFIED
  - verify-email + resend-verification whitelisted in SecurityConfig, JwtAuthenticationFilter, RateLimitFilter.AUTH_PATHS
affects: [11-04 HTTP surface, 11-05 test migration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Enumeration-safe resend: per-email Bucket4j guard + always-return-normally for unknown/verified/rate-limited"
    - "Atomic single-use token claim via markUsed conditional UPDATE; login gate ordered after passwordEncoder.matches"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt
  modified:
    - src/main/kotlin/com/catspell/api/auth/service/AuthService.kt
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
    - src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt

key-decisions:
  - "verifyEmail mints NO session and does not revoke refresh tokens (D-02) — user logs in fresh after verifying"
  - "Login gate placed after passwordEncoder.matches so unknown email/wrong password stays a generic 401 (D-03)"
  - "Verification token TTL = 24h via ChronoUnit.HOURS (D-08); resend per-email capacity 3 / refill 1h (D-05)"

patterns-established:
  - "EMAIL_NOT_VERIFIED machine-readable code on a 403 ProblemDetail for app-side resend routing"

requirements-completed: [VERIFY-01, VERIFY-02, VERIFY-03, VERIFY-04]

coverage:
  - id: D1
    description: "EmailVerificationService issues hashed 24h single-use tokens (issueAndSend) and exposes enumeration-safe per-email-rate-limited resend (VERIFY-01, VERIFY-04)"
    requirement: "VERIFY-04"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin --rerun-tasks"
        status: pass
    human_judgment: true
    rationale: "Compilation proves the flow shape; enumeration-safety, single-send, and per-email bucket boundary behavior are asserted by the Plan 04/05 integration tests, which do not exist yet."
  - id: D2
    description: "AuthService.verifyEmail atomically claims the token (markUsed), rejects absent/expired, stamps email_verified_at, and mints no session (VERIFY-02)"
    requirement: "VERIFY-02"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin --rerun-tasks"
        status: pass
    human_judgment: true
    rationale: "Single-use-under-concurrency and no-session behavior require a running DB; exercised by Plan 04/05 integration tests still to be written."
  - id: D3
    description: "login hard-gates unverified accounts with 403 EMAIL_NOT_VERIFIED after the password check (VERIFY-03)"
    requirement: "VERIFY-03"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin --rerun-tasks"
        status: pass
    human_judgment: true
    rationale: "The 401-vs-403 ordering distinction is behavioral and asserted by Plan 04/05 integration tests, not by compilation alone."
  - id: D4
    description: "verify-email + resend-verification whitelisted in the three security places (SecurityConfig, JwtAuthenticationFilter, RateLimitFilter.AUTH_PATHS) (D-05)"
    requirement: "VERIFY-01"
    verification:
      - kind: other
        ref: "grep of the three security files"
        status: pass
    human_judgment: true
    rationale: "Endpoint reachability without a JWT and per-IP rate-limiting are proven only once the Plan 04 controller exists and integration tests hit the routes."

# Metrics
duration: 12min
completed: 2026-08-12
status: complete
---

# Phase 11 Plan 03: Verification service + login hard-gate Summary

**Enumeration-safe `EmailVerificationService` (hashed 24h single-use tokens + per-email Bucket4j resend), `AuthService.verifyEmail` atomic single-use claim with no session, and a post-password `EMAIL_NOT_VERIFIED` 403 login gate.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-08-12T21:24:41Z
- **Completed:** 2026-08-12T21:36:47Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- `EmailVerificationService`: `issueAndSend(user)` invalidates prior unused tokens, mints a 32-byte SecureRandom Base64url token, stores its SHA-256 hash with a 24h expiry (D-08), and sends exactly one email; `resend(email)` is enumeration-safe (per-email Bucket4j guard + three silent-return branches: rate-limited, unknown, already-verified) and never signals account state (VERIFY-04, D-04/D-05).
- `AuthService.verifyEmail(rawToken)` is `@Transactional`: hashes via the existing helper, rejects absent/expired tokens with `InvalidTokenException`, atomically claims via `markUsed` (rejects on zero rows), stamps `emailVerifiedAt`, and mints NO session (VERIFY-02, D-02).
- `AuthService.login` now throws `EmailNotVerifiedException` when `emailVerifiedAt == null`, after `passwordEncoder.matches`, so unknown-email/wrong-password stays a generic 401 (VERIFY-03, D-03).
- `EmailNotVerifiedException` maps to a 403 ProblemDetail with `code = EMAIL_NOT_VERIFIED`; both new endpoints whitelisted in SecurityConfig + JwtAuthenticationFilter, resend added to RateLimitFilter.AUTH_PATHS.

## Task Commits

1. **Task 1: EmailVerificationService (issue + enumeration-safe resend)** - `3d1e2f0` (feat)
2. **Task 2: verifyEmail + login gate + EmailNotVerifiedException/handler** - `e70f356` (feat)
3. **Task 3: whitelist verify-email + resend-verification (3 places)** - `1040e91` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt` - Enumeration-safe issue/resend flow
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` - verifyEmail + login gate + repo injection
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` - EmailNotVerifiedException
- `src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt` - 403 EMAIL_NOT_VERIFIED handler
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` - permitAll additions
- `src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt` - shouldNotFilter additions
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt` - AUTH_PATHS resend entry

## Decisions Made
- No session on verifyEmail (D-02); login gate ordered after password check (D-03); 24h TTL (D-08).
- `register` intentionally NOT modified here — its no-token contract flip is colocated with the controller in Plan 04 so each plan compiles cleanly.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Kotlin incremental compilation repeatedly reported `UP-TO-DATE` / a transient cache `EOFException` after edits; confirmed each task with `./gradlew compileKotlin --rerun-tasks`, all `BUILD SUCCESSFUL`.

## User Setup Required
None - no external service configuration required. Optional env overrides: `VERIFY_EMAIL_URL` and the `app.verify-token.ttl-hours` / `app.resend-verification.*` @Value defaults.

## Next Phase Readiness
- Plan 04 can now expose `POST /api/auth/verify-email` + `POST /api/auth/resend-verification`, wire the DTOs, and flip `register` to the no-token 201 contract calling `issueAndSend`.

## Self-Check: PASSED

---
*Phase: 11-email-verification*
*Completed: 2026-08-12*
