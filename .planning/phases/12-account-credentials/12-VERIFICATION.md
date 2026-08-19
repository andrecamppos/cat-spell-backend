---
phase: 12-account-credentials
verified: 2026-08-19T15:55:00Z
status: passed
score: 5/5 must-haves verified
behavior_unverified: 0
---

# Phase 12: Account Credentials Verification Report

**Phase Goal:** Self-service credential changes while logged in — change password (revoke other sessions) and change email (verify the new address before it takes effect, reject if already in use).
**Verified:** 2026-08-19T15:55:00Z
**Status:** passed

## Goal Achievement

### Observable Truths

| # | Truth (ACCT req) | Status | Evidence |
|---|------------------|--------|----------|
| 1 | ACCT-01: a logged-in user changes password with current + new password; wrong current password → 403 INVALID_CURRENT_PASSWORD before any state change | ✓ VERIFIED | `AuthService.changePassword` (line 152) verifies `passwordEncoder.matches` and throws `InvalidCurrentPasswordException` before mutating; handler maps to 403 `code=INVALID_CURRENT_PASSWORD` (GlobalExceptionHandler line 79). `AccountCredentialsIntegrationTest#ACCT-01 wrong password` + `#ACCT-01 short password 400` green. |
| 2 | ACCT-02: a successful password change revokes ALL active sessions and mints no tokens | ✓ VERIFIED | `changePassword` re-encodes hash then calls `revokeAllUserTokens(user)` (line 168), returns Unit (no `createRefreshToken`/`AuthResponse`). `#ACCT-02 successful change` asserts empty body, new-password login, and pre-change refresh token rejected at `/api/auth/refresh`. Green. |
| 3 | ACCT-03: a logged-in user initiates an email change with current password + new email; a confirm email is sent to the NEW address and the account email is unchanged until confirmation | ✓ VERIFIED | `EmailChangeService.requestChange` verifies password, mints a hashed single-use token, and sends `emailChangeEmailRenderer.render(newEmail, rawToken)`; never assigns `user.email`. `#ACCT-03 ACCT-04` asserts 202, confirm email `to == newEmail`, and old email still resolves pre-confirm. Green. |
| 4 | ACCT-04: the new address must be confirmed via single-use token before it becomes active; reused/expired/unknown tokens rejected; confirm revokes sessions | ✓ VERIFIED | `AuthService.confirmEmailChange` (line 172) rejects unknown/expired, atomic `markUsed` (line 183; 0 rows → InvalidTokenException), then swaps `user.email = request.newEmail` (line 190), stamps `emailVerifiedAt`, and `revokeAllUserTokens(user)` (line 195). `#ACCT-03 ACCT-04` + `#ACCT-04 reused-unknown-expired` green. |
| 5 | ACCT-05: an email change is rejected if the new address already belongs to another account | ✓ VERIFIED | `requestChange` throws `DuplicateEmailException` (409) on `existsByEmail(newEmail)` before minting/sending. `#ACCT-05 taken address` asserts 409, no confirm email sent, and no pending row created. Green. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `auth/model/EmailChangeRequest.kt` + `…Repository.kt` | Pending-change store w/ atomic markUsed | ✓ EXISTS + SUBSTANTIVE | Entity maps `email_change_requests` (+ `new_email`); repo has `findByTokenHash`, `findAllByUserAndUsedAtIsNull`, atomic `markUsed`. |
| `db/migration/V18…sql` | email_change_requests table | ✓ EXISTS + SUBSTANTIVE | FK ON DELETE CASCADE, unique `token_hash`, both indexes; applies on boot. |
| `email/service/EmailChangeEmailRenderer.kt` + `app.confirm-email-change-url` | Confirm-email renderer to new address | ✓ EXISTS + SUBSTANTIVE | Recipient-agnostic; `?token=` deep-link; config in application.yml (+ test yml). |
| `common/exception` (InvalidCurrentPasswordException + handler) | Distinct 403 code | ✓ EXISTS + SUBSTANTIVE | 403 `INVALID_CURRENT_PASSWORD` ProblemDetail. |
| `auth/service/EmailChangeService.kt` + `AuthService` changePassword/confirmEmailChange | Business logic | ✓ EXISTS + SUBSTANTIVE | verify-password/revoke-all/no-token/409-on-taken/swap-on-confirm. |
| `auth/controller/AuthController.kt` + `AuthDtos.kt` | 3 endpoints + 3 DTOs | ✓ EXISTS + SUBSTANTIVE | change-password (200), change-email (202), confirm-email-change (200). |
| Security whitelist (SecurityConfig, JwtAuthenticationFilter, RateLimitFilter) | Only confirm-email-change public | ✓ EXISTS + SUBSTANTIVE | confirm-email-change permitAll + shouldNotFilter; change-email in AUTH_PATHS; change endpoints stay authenticated. |
| `auth/AccountCredentialsIntegrationTest.kt` | End-to-end proof ACCT-01..05 | ✓ EXISTS + SUBSTANTIVE | 6 tests, all green. |

**Artifacts:** 8/8 verified

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| AuthController.changePassword | AuthService.changePassword | extractUserId() principal | ✓ WIRED (controller line 103) |
| AuthController.changeEmail | EmailChangeService.requestChange | extractUserId() principal | ✓ WIRED (controller line 111) |
| AuthController.confirmEmailChange | AuthService.confirmEmailChange | req.token | ✓ WIRED (controller line 118) |
| AuthService | EmailChangeRequestRepository | constructor inject + findByTokenHash/markUsed | ✓ WIRED (line 27, 174, 183) |
| GlobalExceptionHandler | 403 ProblemDetail | code=INVALID_CURRENT_PASSWORD | ✓ WIRED (line 79) |
| SecurityConfig + JwtAuthenticationFilter | confirm-email-change | permitAll + shouldNotFilter | ✓ WIRED (SecurityConfig line 28) |
| RateLimitFilter.AUTH_PATHS | change-email | per-IP 429 | ✓ WIRED (line 30) |

**Wiring:** 7/7 connections verified

## Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| ACCT-01 | ✓ SATISFIED | AccountCredentialsIntegrationTest#ACCT-01 (wrong password 403, short password 400) |
| ACCT-02 | ✓ SATISFIED | AccountCredentialsIntegrationTest#ACCT-02 (no tokens + revoke-all) |
| ACCT-03 | ✓ SATISFIED | AccountCredentialsIntegrationTest#ACCT-03 ACCT-04 (email to new address, unchanged until confirm) |
| ACCT-04 | ✓ SATISFIED | AccountCredentialsIntegrationTest#ACCT-03 ACCT-04 + #ACCT-04 reused/unknown/expired |
| ACCT-05 | ✓ SATISFIED | AccountCredentialsIntegrationTest#ACCT-05 (409 on taken address, no email) |

**Coverage:** 5/5 requirements satisfied

## Anti-Patterns Found

**Anti-patterns:** 0 found (0 blockers, 0 warnings). Raw tokens are never persisted (SHA-256 hash only); no session is minted on any credential change (fresh-login invariant); the account email swaps only inside `confirmEmailChange` after an atomic single-use claim; the confirm email targets the new address only (never the current account email).

## Human Verification Required

None — every observable truth is exercised by a passing automated integration test (Testcontainers Postgres). Full suite: **255 tests, 0 failures, 1 skipped** (`./gradlew test`, pre-existing skip). AccountCredentialsIntegrationTest: **6/6 green.**

## Gaps Summary

**No gaps found.** Phase goal achieved. Ready to proceed.

## Verification Metadata

**Verification approach:** Goal-backward (derived from phase goal + PLAN must_haves)
**Must-haves source:** 12-01..05 PLAN.md frontmatter
**Automated checks:** full suite green (255 passed, 1 pre-existing skip)
**Human checks required:** 0
**Verifier:** inline (Agent primitive unavailable in this runtime; sequential-inline fallback)

---
*Verified: 2026-08-19*
