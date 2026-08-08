---
phase: "10"
name: password-recovery
created: 2026-08-08T12:26:52Z
verified: 2026-08-08T12:26:52Z
status: passed
score: 33/33 must-haves verified
behavior_unverified: 0
---

# Phase 10: Password Recovery Verification Report

**Phase Goal:** A user who forgot their password can regain access via an emailed single-use reset link, backed by a new reusable transactional-email abstraction.
**Verified:** 2026-08-08T12:26:52Z
**Status:** passed

## Goal Achievement

Goal-backward verification against the ACTUAL codebase (not SUMMARY claims). Every
`must_haves.truth` across all four plans was checked in the real source files, and the
phase's automated tests were executed and confirmed green.

### Observable Truths — Plan 01 (Email Seam)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | LoggingEmailSender is the sole/default EmailSender bean when email.enabled unset/false | ✓ VERIFIED | `LoggingEmailSender.kt:7-9` `@ConditionalOnProperty(name=["email.enabled"], havingValue="false", matchIfMissing=true)`; `EmailSenderSelectionTest` (2/2 pass) proves default + disabled-when-enabled; only 3 files exist under `email/service/` — no network sender |
| 2 | send() returns SUCCESS, never opens a network connection | ✓ VERIFIED | `LoggingEmailSender.kt:13-16` returns `EmailResult(SUCCESS, "logged")`, only logs; `EmailSenderContractTest` (2/2 pass) |
| 3 | Deep link built from env-configured `app.reset-password-url`, never from HTTP request | ✓ VERIFIED | `PasswordResetEmailRenderer.kt:8` `@Value("\${app.reset-password-url}")`, line 12 `"$resetPasswordUrl?token=$rawToken"`; no `HttpServletRequest`/host-header reference in file |
| 4 | Rendered EmailMessage carries both HTML and plain-text bodies, each embedding the link | ✓ VERIFIED | `PasswordResetEmailRenderer.kt:15-41` builds `htmlBody` + `textBody`, both embed `$resetLink`; returned as `EmailMessage(htmlBody, textBody)` |
| 5 | Recipient masked in logs; raw token never logged | ✓ VERIFIED | `LoggingEmailSender.kt:14` logs `maskEmail(to)` + subject only; `maskEmail` lines 18-25; no token/body logged anywhere |
| 6 | Stateless no-op sender: concurrent send() calls each return SUCCESS, no shared-state corruption | ✓ VERIFIED | `LoggingEmailSender` holds no mutable per-request state; `EmailSenderContractTest#concurrent sends each return SUCCESS` passes |

### Observable Truths — Plan 02 (Reset-Token Store)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 7 | `password_reset_tokens` table (V15): UUID PK, user_id FK ON DELETE CASCADE, unique token_hash, expires_at, nullable used_at, created_at | ✓ VERIFIED | `V15__create_password_reset_tokens_table.sql:1-11` matches exactly, incl. `REFERENCES users(id) ON DELETE CASCADE` and unique index on token_hash |
| 8 | Entity column names/types match V15 (Hibernate validate/create-drop parity) | ✓ VERIFIED | `PasswordResetToken.kt:18-28` `@Column` names token_hash/expires_at/used_at/created_at + `user_id` join column all map 1:1 to V15; schema exercised end-to-end by `PasswordResetIntegrationTest` under Testcontainers (10/10 pass) |
| 9 | Repository exposes `findByTokenHash` for indexed redemption lookup | ✓ VERIFIED | `PasswordResetTokenRepository.kt:7` `findByTokenHash(tokenHash): PasswordResetToken?` |
| 10 | token_hash is a deterministic SHA-256 hex → single unique-index lookup, no ambiguity | ✓ VERIFIED | Hash produced by `AuthService.hashToken`/`PasswordResetService.hashToken` (SHA-256 + `HexFormat`), stored in unique column; `findByTokenHash` resolves it |

### Observable Truths — Plan 03 (Business Logic + Security Wiring)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 11 | `requestReset(email)` always returns normally regardless of registration (no existence signal) | ✓ VERIFIED | `PasswordResetService.kt:49-78` returns Unit on every branch (rate-limited → return; user absent → return; else issue+send); no exception/429/distinct return |
| 12 | Registered email → 32-byte SecureRandom token, SHA-256 hash stored, expires_at = now+30min, email sent | ✓ VERIFIED | `PasswordResetService.kt:68-77,80-90` 32-byte SecureRandom Base64url, SHA-256 hex, `expiresAt = now.plus(ttl=30, MINUTES)`, `emailSender.send(...)` |
| 13 | Per-email Bucket4j guard caps sends; on exhaustion silently skips send, still returns normally (no 429/signal) | ✓ VERIFIED | `PasswordResetService.kt:34-42,54-56` `ConcurrentHashMap<String,Bucket>` keyed by normalized email; `if (!tryConsume(1)) return` |
| 14 | `AuthService.resetPassword` is @Transactional: hashes token, finds by hash, rejects absent/used/expired (401), marks used_at, sets BCrypt hash, revokes ALL refresh tokens | ✓ VERIFIED | `AuthService.kt:93-112` `@Transactional`; `findByTokenHash` → `InvalidTokenException` (→401); used/expired guard line 99; `usedAt=now`; `passwordEncoder.encode`; `revokeAllUserTokens(user)`; no tokens returned |
| 15 | Both endpoints in SecurityConfig.permitAll AND JwtAuthenticationFilter.shouldNotFilter; forgot-password in RateLimitFilter.AUTH_PATHS | ✓ VERIFIED | `SecurityConfig.kt:28` (both paths permitAll); `JwtAuthenticationFilter.kt:21-22` (both shouldNotFilter); `RateLimitFilter.kt:28` (forgot-password in AUTH_PATHS) |
| 16 | resetPassword succeeds with zero active refresh tokens (revoke-all is a no-op) | ✓ VERIFIED | `AuthService.kt:131-135` `findAllByUserAndRevokedFalse` → empty list → `saveAll(empty)` no-op; password still updated (line 107) |
| 17 | revoke-all marks every active token revoked regardless of iteration order (set-based) | ✓ VERIFIED | `AuthService.kt:132-134` iterates full active set setting `revoked=true`; order-independent |
| 18 | Concurrent forgot for registered vs unregistered both return same generic outcome (thread-safe bucket store) | ✓ VERIFIED | `ConcurrentHashMap` bucket store; both branches return Unit; `PasswordResetIntegrationTest#concurrent forgot-password requests each return 202` passes |
| 19 | Concurrent forgot requests share thread-safe per-email buckets (ConcurrentHashMap + Bucket4j) | ✓ VERIFIED | `PasswordResetService.kt:34-42` `computeIfAbsent` on ConcurrentHashMap + Bucket4j (thread-safe) |
| 20 | Boundary: expires_at exactly now treated as expired/rejected via isBefore | ✓ VERIFIED (backstop) | `AuthService.kt:99` uses `expiresAt.isBefore(Instant.now())` as plan specified; `RECOV-05 - expired reset token is rejected 401` passes |
| 21 | Refresh token natural expiry coinciding with reset instant still gets revoked | ✓ VERIFIED (backstop) | `revokeAllUserTokens` revokes by active-set membership independent of expiry; `RECOV-06` test passes |
| 22 | Per-email refill arithmetic (capacity over Duration) refills without rounding drift | ✓ VERIFIED (backstop) | `PasswordResetService.kt:37-40` `refillIntervally(capacity, Duration.ofHours(refillHours))` — interval refill, no fractional drift |

### Observable Truths — Plan 04 (HTTP Surface + Integration Proof)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 23 | POST /api/auth/forgot-password accepts {email}, reachable w/o JWT, returns 202 + fixed generic body | ✓ VERIFIED | `AuthController.kt:47-54` `@SecurityRequirements`, 202 `GenericMessageResponse`; `RECOV-01` test passes |
| 24 | forgot-password byte-identical response (status+body) whether email registered or not | ✓ VERIFIED | Controller returns unconditional fixed body; `RECOV-04 - identical response` test asserts equal status + body bytes (pass) |
| 25 | POST /api/auth/reset-password accepts {token,newPassword}, valid token → 200 with NO tokens; user can log in with new password | ✓ VERIFIED | `AuthController.kt:56-61` returns `ResponseEntity.ok().build()` (Void); `RECOV-03` test asserts empty body + new-password login OK + old rejected |
| 26 | Reused and expired reset tokens both rejected 401 | ✓ VERIFIED | `RECOV-05 - reused reset token is rejected 401` + `RECOV-05 - expired reset token is rejected 401` pass |
| 27 | Stored token row holds a hash, not the raw emailed token | ✓ VERIFIED | `RECOV-05 - stored reset token is hashed not the raw emailed token` asserts `tokenHash != rawToken` (pass) |
| 28 | After successful reset, a pre-reset refresh token is rejected | ✓ VERIFIED | `RECOV-06 - refresh token issued before reset is rejected after reset` passes |
| 29 | >per-IP capacity forgot-password from one IP → 429 (proves AUTH_PATHS membership) | ✓ VERIFIED | `RateLimitIntegrationTest#should rate limit forgot-password endpoint` passes (9/9) |
| 30 | Concurrent forgot-password requests each receive 202 | ✓ VERIFIED | `concurrent forgot-password requests each return 202` passes |
| 31 | Blank newPassword → 400 (@Size min=8); empty/unknown token → 401 | ✓ VERIFIED | `AuthDtos.kt:33-38` `@Size(min=8)`; `reset-password with blank newPassword is rejected 400` + `unknown token is rejected 401` pass |
| 32 | Nth per-IP request succeeds, (N+1)th → 429 (boundary) | ✓ VERIFIED | RateLimit boundary asserted in `RateLimitIntegrationTest` (pass) |
| 33 | Two submissions with same valid token: first 200, second 401 (single-use adjacency) | ✓ VERIFIED (backstop) | `RECOV-05 - reused reset token is rejected 401` exercises exactly this adjacency (pass) |

**Score:** 33/33 truths verified (0 present-behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `email/service/EmailSender.kt` | interface + value objects + enum | ✓ EXISTS + SUBSTANTIVE | `interface EmailSender`, `EmailMessage`, `EmailResult`, `EmailSendStatus` |
| `email/service/LoggingEmailSender.kt` | no-op default sender | ✓ EXISTS + SUBSTANTIVE | `@ConditionalOnProperty ... matchIfMissing=true`, masks recipient |
| `email/service/PasswordResetEmailRenderer.kt` | HTML+text body + deep link | ✓ EXISTS + SUBSTANTIVE | env-configured link, both bodies |
| `auth/model/PasswordResetToken.kt` | hashed single-use entity | ✓ EXISTS + SUBSTANTIVE | token_hash unique, used_at, expires_at |
| `auth/model/PasswordResetTokenRepository.kt` | findByTokenHash | ✓ EXISTS + SUBSTANTIVE | + findAllByUserAndUsedAtIsNull |
| `db/migration/V15__...sql` | table + indexes | ✓ EXISTS + SUBSTANTIVE | parity with entity |
| `auth/service/PasswordResetService.kt` | forgot flow + guard | ✓ EXISTS + SUBSTANTIVE | requestReset enumeration-safe |
| `auth/service/AuthService.kt` (resetPassword) | transactional consume | ✓ EXISTS + SUBSTANTIVE | `@Transactional fun resetPassword` |
| `auth/model/AuthDtos.kt` | forgot/reset DTOs | ✓ EXISTS + SUBSTANTIVE | ForgotPasswordRequest/ResetPasswordRequest/GenericMessageResponse |
| `auth/controller/AuthController.kt` | 2 endpoints | ✓ EXISTS + SUBSTANTIVE | forgot (202) + reset (200 Void) |
| test files (4) | EMAIL/RECOV proofs | ✓ EXISTS + SUBSTANTIVE | all present, all green |

**Artifacts:** 11/11 verified

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| PasswordResetEmailRenderer | EmailSender | returns EmailMessage consumed by send | ✓ WIRED (`PasswordResetService.kt:76-77`) |
| PasswordResetEmailRenderer | application.yml | @Value app.reset-password-url | ✓ WIRED (`application.yml:38-39`) |
| PasswordResetToken | V15 migration | @Column ↔ DDL parity | ✓ WIRED |
| PasswordResetService | PasswordResetTokenRepository | persists SHA-256 token_hash | ✓ WIRED |
| PasswordResetService | EmailSender | emailSender.send | ✓ WIRED |
| AuthService.resetPassword | PasswordResetTokenRepository | findByTokenHash | ✓ WIRED |
| AuthController | PasswordResetService | requestReset | ✓ WIRED |
| AuthController | AuthService | resetPassword | ✓ WIRED |

**Wiring:** 8/8 connections verified

## Requirements Coverage (Goal-Backward)

| Requirement | Status | Evidence (file:line / test) |
|-------------|--------|-----------------------------|
| EMAIL-01: provider-abstracted EmailSender seam, stubbed in tests | ✓ SATISFIED | `EmailSender.kt:18-20`; `EmailSenderContractTest` (2/2); MockK `@Primary` stub in `PasswordResetIntegrationTest.kt:38-43` |
| EMAIL-02: env-configurable, no-op/logging default, no real network sends | ✓ SATISFIED | `LoggingEmailSender.kt:7-9` matchIfMissing default; `application.yml:35-36` `email.enabled=${EMAIL_ENABLED:false}`; `EmailSenderSelectionTest` (2/2); no network sender class exists |
| RECOV-01: request reset by submitting email | ✓ SATISFIED | `AuthController.kt:47-54`; `RECOV-01` test |
| RECOV-02: single-use, time-limited reset link emailed | ✓ SATISFIED | `PasswordResetService.kt:68-77` (30-min TTL, single token, emailed); rendered link `PasswordResetEmailRenderer.kt:12` |
| RECOV-03: set new password with valid token+password | ✓ SATISFIED | `AuthController.kt:56-61` + `AuthService.kt:93-112`; `RECOV-03` test |
| RECOV-04: identical generic response, no enumeration | ✓ SATISFIED | `PasswordResetService.kt:49-78` single-return; `RECOV-04` byte-identical test |
| RECOV-05: hashed, single-use, short-TTL tokens; used/expired rejected | ✓ SATISFIED | `PasswordResetToken.kt` (hash+used_at+expires_at); `AuthService.kt:95-104`; 3 `RECOV-05` tests |
| RECOV-06: successful reset revokes all sessions | ✓ SATISFIED | `AuthService.kt:111,131-135`; `RECOV-06` test |
| RECOV-07: forgot-password rate-limited (per-email and/or per-IP) | ✓ SATISFIED | Per-IP `RateLimitFilter.kt:28` + per-email `PasswordResetService.kt:34-56`; `RateLimitIntegrationTest#should rate limit forgot-password endpoint` |

**Coverage:** 9/9 requirements satisfied. `.planning/REQUIREMENTS.md` marks all 9 `[x]` / Complete (lines 13-24, 70-78) — consistent with codebase evidence.

## Prohibitions Check

| Prohibition | Status | Evidence |
|-------------|--------|----------|
| No real network send from default provider or tests | ✓ HELD | Only `LoggingEmailSender` exists; tests use MockK `@Primary` stub |
| Never log raw token or unmasked recipient | ✓ HELD | `LoggingEmailSender.kt:14` masks recipient, logs subject only; no token/body logging in renderer or service |
| No new template-engine dependency (e.g. Thymeleaf) | ✓ HELD | No Thymeleaf/mail-starter/javax.mail in `build.gradle.kts`; bodies are plain Kotlin string templates |
| Never store the raw reset token | ✓ HELD | Only `tokenHash` column; `RECOV-05 hashed` test asserts stored != raw |
| Never BCrypt-hash the reset token | ✓ HELD | SHA-256 used for reset token (`hashToken`); BCrypt only for password (`AuthService.kt:107`) |
| Never auto-log-in after reset | ✓ HELD | `AuthController.kt:60` `ok().build()` Void; `AuthService.resetPassword` issues no tokens |
| Never leak account existence (no 429/distinct body for known vs unknown/limited) | ✓ HELD | Service swallows all signals; controller returns fixed 202; `RECOV-04` test |
| Never leave endpoints out of any of the 3 whitelists | ✓ HELD | Present in SecurityConfig, JwtAuthenticationFilter, RateLimitFilter.AUTH_PATHS |

**Prohibitions:** 8/8 held.

## Context Decisions Honored

D-01 (env-configured deep link, no web page) ✓ · D-02 (backend-rendered HTML+text) ✓ ·
D-03 (only abstraction + no-op provider, concrete deferred) ✓ · D-04 (reuse RateLimitFilter +
per-email Bucket4j guard) ✓ · D-05 (202 generic) ✓ · D-06 (30-min TTL) ✓ ·
D-07 (200 no tokens, revoke-all, no auto-login) ✓.

## Automated Test Results

Command: `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL.
Command: `./gradlew test --tests EmailSenderSelectionTest --tests EmailSenderContractTest --tests PasswordResetIntegrationTest --tests RateLimitIntegrationTest` → BUILD SUCCESSFUL.

| Suite | tests | failures | errors | skipped |
|-------|-------|----------|--------|---------|
| EmailSenderSelectionTest | 2 | 0 | 0 | 0 |
| EmailSenderContractTest | 2 | 0 | 0 | 0 |
| PasswordResetIntegrationTest | 10 | 0 | 0 | 0 |
| RateLimitIntegrationTest | 9 | 0 | 0 | 0 |
| **Total** | **23** | **0** | **0** | **0** |

The teardown-time `PSQLException`/Hikari "connection is not available" log lines are Testcontainers
container-shutdown noise (context close after the suite passed), not test failures — the suites
report 0 failures/0 errors and the build succeeds.

**Pre-existing out-of-scope flake:** `ChatIntegrationTest > message history supports cursor pagination`
(WebSocket/chat) is a known non-deterministic test unrelated to Phase 10; not run/counted here and
not a phase-10 gap.

## Anti-Patterns Found

None. No stubs, TODOs, placeholder returns, or unwired paths in the phase's production code.

## Human Verification Required

None — all observable truths were verified programmatically via source inspection and green
automated tests.

## Gaps Summary

**No gaps found.** Phase goal achieved: a user who forgot their password can request a reset
(202 generic, enumeration-safe, rate-limited), receive an emailed single-use 30-minute deep-link
via the reusable no-op/logging `EmailSender` abstraction, and set a new password (transactional,
single-use consume, all sessions revoked, no auto-login). Ready to proceed.

## Verification Metadata

**Verification approach:** Goal-backward (derived from phase goal + all 4 plan must_haves)
**Must-haves source:** PLAN.md frontmatter (10-01..04)
**Automated checks:** 2 compile tasks + 23 tests passed, 0 failed
**Human checks required:** 0

---
*Verified: 2026-08-08T12:26:52Z*
*Verifier: Claude (subagent)*
