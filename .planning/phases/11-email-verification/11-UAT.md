---
status: partial
phase: 11-email-verification
source: [11-01-SUMMARY.md, 11-02-SUMMARY.md, 11-03-SUMMARY.md, 11-04-SUMMARY.md, 11-05-SUMMARY.md]
started: 2026-08-13T14:49:32Z
updated: 2026-08-13T16:40:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: On a fresh DB (`podman compose down -v && podman compose up -d && ./gradlew bootRun`), the app boots cleanly, Flyway runs V16 + V17 without error, and a primary request (health check or POST /api/auth/register) returns live data.
result: pass

### 2. Register sends a real verification email with a working deep link
expected: POST /api/auth/register with a fresh email returns 201 and NO tokens/session. Exactly one verification email is sent to that address, containing a single-use deep link keyed on `app.verify-email-url` (e.g. `catspell://verify-email?token=...`). Opening/using the link verifies the account. (Tests stub the EmailSender, so real delivery + link formatting need a human check.)
result: blocked
blocked_by: third-party
reason: "No real email transport exists in this milestone — the only EmailSender is the no-op LoggingEmailSender (email.enabled=false default), which does not log the token/link and masks the recipient. The raw token is never persisted or logged by design. Phase 11 built the email seam + renderer only; external email providers are OPT-OUT in 11-COVERAGE.md. Renderer→EmailMessage deep-link rendering is proven by auto-passed integration test 11-04-D1."

### 3. End-to-end verify-then-login happy path in a live environment
expected: A newly registered (unverified) user CANNOT log in — login returns 403 with code EMAIL_NOT_VERIFIED after the password check (wrong password / unknown user still returns 401). After hitting POST /api/auth/verify-email with the emailed token (200, single-use — reusing or an expired/blank/unknown token is rejected), the same user can log in successfully and receives tokens.
result: pass
note: "Login gate verified live: unverified+correct-password → 403 EMAIL_NOT_VERIFIED; wrong password → 401; unknown user → 401 (gate correctly ordered after password check, no enumeration). Token-claim + post-verify login portion covered by auto-passed integration tests 11-04-D2/D3 (not manually testable locally — no raw token, same cause as Test 2)."

### 4. Existing (grandfathered) accounts are not locked out
expected: Accounts that existed before this phase (email_verified_at backfilled to created_at by V17) can still log in normally without needing to re-verify. Resend-verification (POST /api/auth/resend-verification) always returns an identical generic 202 regardless of whether the email is unknown, unverified, or already verified (enumeration-safe), and is per-IP rate limited (429 after the cap).
result: pass
note: "Resend enumeration-safety verified live: unverified/unknown/non-existent emails all returned identical 202. Per-IP rate limit verified live: 7x202 then 5x429. Grandfathered-login (Part A) had no local pre-existing accounts to exercise (cold-start DB) — covered by auto-passed integration test 11-05-D3."

### 5. EmailVerificationToken entity + repository (SHA-256 hash, single-use markUsed)
expected: Tokens are stored as SHA-256 hashes only, looked up via findByTokenHash, and claimed atomically via markUsed (single-use).
result: pass
source: automated
coverage_id: 11-04-D2

### 6. V16/V17 migrations + grandfather backfill
expected: V16 creates email_verification_tokens (unique token_hash); V17 adds nullable email_verified_at and backfills existing rows idempotently without overwriting.
result: pass
source: automated
coverage_id: 11-05-D3

### 7. Verification email renderer (deep link into EmailMessage)
expected: EmailVerificationEmailRenderer renders the single-use token deep link into an EmailMessage keyed on app.verify-email-url.
result: pass
source: automated
coverage_id: 11-04-D1

### 8. register: unverified user + one email + 201 no tokens (VERIFY-01)
expected: register creates an unverified user, sends exactly one verification email, returns 201 with no tokens.
result: pass
source: automated
coverage_id: 11-04-D1

### 9. verify-email: valid claim, reject reused/expired/blank/unknown (VERIFY-02)
expected: verify-email claims a valid token (200, no tokens), rejects reused/expired/blank/unknown tokens, and sets email_verified_at.
result: pass
source: automated
coverage_id: 11-04-D2

### 10. login hard-gate 403 EMAIL_NOT_VERIFIED (VERIFY-03)
expected: login hard-gates unverified accounts with 403 EMAIL_NOT_VERIFIED after the password check; unknown/wrong-password stays 401. Whole integration suite green under the new contract (249 tests).
result: pass
source: automated
coverage_id: 11-04-D3

### 11. resend-verification enumeration-safe + per-IP rate limit (VERIFY-04)
expected: resend-verification returns an identical 202 body for unknown/verified/unverified emails, invalidates prior tokens, and returns 429 after the per-IP cap.
result: pass
source: automated
coverage_id: 11-04-D4

## Summary

total: 11
passed: 10
issues: 0
pending: 0
skipped: 0
blocked: 1

## Gaps

[none yet]
