---
phase: 10-password-recovery
plan: 03
subsystem: auth
tags: [spring-boot, kotlin, bucket4j, secure-random, sha-256, jpa, spring-security, password-reset]

# Dependency graph
requires:
  - phase: 10-password-recovery (10-01)
    provides: EmailSender seam + PasswordResetEmailRenderer
  - phase: 10-password-recovery (10-02)
    provides: PasswordResetToken entity + PasswordResetTokenRepository + Flyway V15
provides:
  - "PasswordResetService.requestReset — enumeration-safe forgot flow with per-email Bucket4j guard, 32-byte SecureRandom token, SHA-256 hashed storage, 30-min TTL, email via EmailSender seam"
  - "AuthService.resetPassword — transactional single-use token consume, BCrypt password update, revoke-all sessions, no auto-login"
  - "Both /api/auth/forgot-password and /api/auth/reset-password whitelisted across SecurityConfig, JwtAuthenticationFilter, and RateLimitFilter.AUTH_PATHS"
affects: [10-04-password-recovery-controller, email-verification, credential-changes]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-email Bucket4j guard (ConcurrentHashMap<String, Bucket>) in the service layer, keyed by normalized email"
    - "High-entropy reset token: 32-byte SecureRandom → Base64url raw (emailed) + deterministic SHA-256 hex (stored/indexed)"
    - "Enumeration-safe single-return forgot flow (no existence signal, no 429 for known/unknown/rate-limited email)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt
  modified:
    - src/main/kotlin/com/catspell/api/auth/service/AuthService.kt
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
    - src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt
    - src/test/resources/application.yml

key-decisions:
  - "SHA-256 (deterministic, indexable) for reset-token hashing — NOT BCrypt (whose random salt breaks hash lookup); BCrypt reserved for the new password only (RESEARCH divergence from CONTEXT hint)."
  - "Per-email bucket defaults via @Value with in-code defaults (capacity 3 / refill 3 per hour; TTL 30 min per D-06) — avoids touching production application.yml which is out of plan scope."
  - "Prior unused tokens are invalidated (used_at set) before issuing a fresh token, so only the newest reset link is usable."
  - "resetPassword uses expiresAt.isBefore(now) exactly as the plan action specified for the TTL boundary."

patterns-established:
  - "Service-layer per-recipient rate limiting via ConcurrentHashMap<String, Bucket> mirroring RateLimitFilter.createBucket."
  - "Reset-token lifecycle: SecureRandom raw (email-only) + SHA-256 hex (stored), single-use via used_at, TTL via expires_at."

requirements-completed: [RECOV-02, RECOV-04, RECOV-05, RECOV-06, RECOV-07]

coverage:
  - id: D1
    description: "PasswordResetService.requestReset: enumeration-safe forgot flow, per-email Bucket4j guard, 32-byte SecureRandom token, SHA-256 hashed storage, 30-min TTL, email via EmailSender."
    requirement: "RECOV-02"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin (source-asserted: single normal exit; SecureRandom+Base64url; MessageDigest SHA-256; expiresAt now+30m; bucket-exhaustion skips send)"
        status: pass
    human_judgment: true
    rationale: "Behavioral proof (identical 202 for known/unknown/rate-limited email) is deferred to Plan 04's integration test per the plan; source assertions + compile are the only automated checks available here."
  - id: D2
    description: "No account-enumeration signal from forgot flow (no distinct return/status/429 for known vs unknown vs rate-limited email)."
    requirement: "RECOV-04"
    verification:
      - kind: other
        ref: "source-assert PasswordResetService.requestReset returns Unit on every branch; no exception/429 path"
        status: pass
    human_judgment: true
    rationale: "Enumeration-safety is a security property proven behaviorally in Plan 04 (identical 202); requires human/UAT confirmation."
  - id: D3
    description: "AuthService.resetPassword: @Transactional single-use consume, rejects absent/used/expired with InvalidTokenException (401), BCrypt password update, revoke-all sessions, no auto-login."
    requirement: "RECOV-05"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin (source-asserted: @Transactional; findByTokenHash; used/expired→InvalidTokenException; usedAt set; passwordEncoder.encode; returns Unit)"
        status: pass
    human_judgment: true
    rationale: "Transactional replay/expiry behavior proven by Plan 04 integration tests; not unit-covered in this plan."
  - id: D4
    description: "On successful reset, all active refresh tokens are revoked via revokeAllUserTokens (no-op safe with zero tokens)."
    requirement: "RECOV-06"
    verification:
      - kind: other
        ref: "source-assert resetPassword calls revokeAllUserTokens(user)"
        status: pass
    human_judgment: true
    rationale: "Revoke-all outcome (including empty-set no-op) verified behaviorally in Plan 04."
  - id: D5
    description: "forgot-password is per-IP rate-limited (added to RateLimitFilter.AUTH_PATHS) and both endpoints are public across SecurityConfig + JwtAuthenticationFilter; per-email guard adds recipient-level capping."
    requirement: "RECOV-07"
    verification:
      - kind: other
        ref: "source-assert AUTH_PATHS contains /api/auth/forgot-password; permitAll + shouldNotFilter include both paths"
        status: pass
    human_judgment: true
    rationale: "Reachability (202 not 401) and per-IP 429 proven by Plan 04 integration tests."

# Metrics
duration: 20min
completed: 2026-08-08
status: complete
---

# Phase 10 Plan 03: Password-Recovery Business Logic Summary

**Enumeration-safe forgot-password flow (per-email Bucket4j guard + SHA-256 single-use 30-min token via the EmailSender seam) plus a transactional reset that revokes all sessions, with both endpoints wired through all three security tiers.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 3 completed
- **Files modified:** 6 (1 created, 5 modified)

## Accomplishments
- Created `PasswordResetService` implementing the enumeration-safe `requestReset(email)` — always returns normally, per-email Bucket4j guard silently skips send on exhaustion, issues a 32-byte SecureRandom / Base64url raw token stored as SHA-256 hex with a 30-minute TTL, and sends via the Plan 01 `EmailSender`.
- Added `@Transactional AuthService.resetPassword(rawToken, newPassword)` — hashes the token, looks up by hash, rejects absent/used/expired tokens with `InvalidTokenException` (401), marks `used_at`, updates the BCrypt password hash, and calls the existing `revokeAllUserTokens(user)` with no auto-login.
- Whitelisted `/api/auth/forgot-password` and `/api/auth/reset-password` in `SecurityConfig.permitAll` and `JwtAuthenticationFilter.shouldNotFilter`, and added `/api/auth/forgot-password` to `RateLimitFilter.AUTH_PATHS` for per-IP limiting (RESEARCH Pitfall #1 correction).

## Task Commits

Each task was committed atomically:

1. **Task 1: Create PasswordResetService** - `67f691a` (feat)
2. **Task 2: Add transactional AuthService.resetPassword** - `0543f48` (feat)
3. **Task 3: Whitelist both endpoints across three security tiers** - `560e0df` (feat)

**Deviation fix:** `d1bd5f2` (fix: test config placeholder — see Deviations)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt` - New enumeration-safe forgot flow, per-email guard, token issuance + email send.
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` - Added `PasswordResetTokenRepository` dependency + `@Transactional resetPassword` + private `hashToken` helper.
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt` - Added `/api/auth/forgot-password` to `AUTH_PATHS`.
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` - Added both new paths to `permitAll`.
- `src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt` - Added both new paths to `shouldNotFilter`.
- `src/test/resources/application.yml` - Added `app.reset-password-url` and `email.enabled` (deviation, see below).

## Decisions Made
- **SHA-256, not BCrypt, for the reset token** — deterministic hash is indexable/lookup-able (BCrypt's per-hash salt would break `findByTokenHash`); BCrypt stays reserved for the new password. (Follows RESEARCH recommendation / D-06 discretion.)
- **Config via `@Value` with in-code defaults** (per-email capacity 3 / refill 3 per hour; TTL 30 min) rather than editing production `application.yml`, keeping the change inside plan scope.
- **Invalidate prior unused tokens** before issuing a new one so only the freshest link works.
- **TTL boundary** uses `expiresAt.isBefore(Instant.now())` exactly as the plan's action text specified.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Fix broken build / missing critical config] Test Spring context could not resolve `app.reset-password-url`**
- **Found during:** Verification after Task 3 (ran the full `./gradlew test` suite).
- **Issue:** Plan 01's `PasswordResetEmailRenderer` (`@Component`) declares `@Value("${app.reset-password-url}")` with no default. The **test** `src/test/resources/application.yml` never defined that property, so every full-context `@SpringBootTest` failed with `PlaceholderResolutionException` during bean creation (198 of 199 failures). This is a pre-existing latent gap surfaced (not caused) by wiring `PasswordResetService`, which also depends on the renderer.
- **Fix:** Added `app.reset-password-url: catspell://reset-password` and `email.enabled: false` to the test `application.yml` (mirrors production `application.yml`). This dropped the failure count from 199 to 1.
- **Files modified:** `src/test/resources/application.yml`
- **Verification:** `./gradlew clean test` — 226 tests, 198 previously-broken contexts now load; only the unrelated flaky chat test remained.
- **Committed in:** `d1bd5f2`

---

**Total deviations:** 1 auto-fixed (Rule 2 - missing test config to load the Spring context).
**Impact on plan:** Necessary to unblock every integration test; no scope creep into other plans' source. No plan source behavior changed.

## Issues Encountered
- **Pre-existing flaky test (out of scope):** `com.catspell.api.chat.ChatIntegrationTest > message history supports cursor pagination` fails non-deterministically (observed `$.messages.length()` = 1 then 4, expected 5 across separate runs). It is a WebSocket message-delivery timing/ordering flake in the chat module, entirely unrelated to password recovery, and was NOT modified by this plan. Left untouched per scope constraints. All other 225 tests pass; the plan's required verify (`./gradlew compileKotlin`) is green for all three tasks.

## User Setup Required
None - no external service configuration required (concrete email provider deferred per D-03; `email.enabled=false` uses the no-op LoggingEmailSender).

## Next Phase Readiness
- Plan 04 can now build the `AuthController` endpoints on top of `PasswordResetService.requestReset` (202) and `AuthService.resetPassword` (200, no tokens); both routes are already public and forgot-password is per-IP + per-email rate-limited.
- Behavioral proofs (identical 202 for known/unknown/rate-limited email; 401 on used/expired token; per-IP 429; session revocation) are the explicit remit of Plan 04's integration tests.

## Self-Check: PASSED

- Created file exists: `src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt` (verified on disk).
- Recorded commits present in `git log`: `67f691a`, `0543f48`, `560e0df`, `d1bd5f2`.
- Required verify `./gradlew compileKotlin` green for all three tasks.

---
*Phase: 10-password-recovery*
*Completed: 2026-08-08*
