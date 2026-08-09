---
status: complete
phase: 10-password-recovery
source: [10-01-SUMMARY.md, 10-02-SUMMARY.md, 10-03-SUMMARY.md, 10-04-SUMMARY.md]
started: 2026-08-09T07:31:34Z
updated: 2026-08-09T11:52:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: |
  Kill any running server, clear ephemeral state (temp DB / Testcontainers), and start the app from
  scratch. Server boots with no errors, Flyway applies migration V15 (creates password_reset_tokens
  with a unique token_hash index), and a primary request (health check or hitting an auth endpoint)
  returns live — no startup/schema-drift failures.
result: pass

### 2. Forgot-Password Is Enumeration-Safe
expected: |
  POST /api/auth/forgot-password with a REGISTERED email and again with an UNREGISTERED email.
  Both return an identical 202 with the same generic body ("If an account exists for that email,
  a password reset link has been sent."). No difference in status, body, or timing reveals whether
  the account exists. (RECOV-02, RECOV-04)
result: pass

### 3. Reset-Password Full Flow + Session Revocation
expected: |
  Trigger forgot-password for a registered user, grab the raw reset token from the app logs
  (LoggingEmailSender prints the rendered link since email.enabled is unset). POST /api/auth/reset-password
  with that token + a new password returns 200 with an empty body (no auto-login, no tokens). You can then
  log in with the NEW password; the OLD password is rejected; and any refresh token issued before the reset
  is now rejected (all sessions revoked). Reusing the same reset token or an expired one returns 401. (RECOV-03, RECOV-05, RECOV-06)
result: pass
source: automated
note: |
  Not manually reproducible by design — the raw reset token is never logged (LoggingEmailSender masks
  recipient, logs no token) and only a SHA-256 hash is persisted. Behavior is proven by passing
  integration tests: PasswordResetIntegrationTest RECOV-03 (valid token -> 200, new pw logs in),
  RECOV-05 (reused/expired -> 401, stored value is a hash), RECOV-06 (pre-reset refresh token revoked).
  User confirmed acceptance of automated coverage in UAT session.

### 4. Forgot-Password Per-IP Rate Limiting
expected: |
  Hammer POST /api/auth/forgot-password repeatedly from a single IP. After the configured capacity is
  exhausted, further requests return 429 (Too Many Requests), proving the endpoint is in AUTH_PATHS. (RECOV-07)
result: pass
note: "Observed 10x 202 then 429 on requests 11-12 from one IP (capacity 10/window)."

### 5. EmailSender No-Op Default (no network in dev/CI)
expected: LoggingEmailSender is the default (sole) EmailSender bean when email.enabled is unset — no network sender in dev/CI.
result: pass
source: automated
coverage_id: 10-01-D1

### 6. EmailSender Contract
expected: EmailSender.send returns EmailResult SUCCESS without throwing or network I/O, including concurrent calls.
result: pass
source: automated
coverage_id: 10-01-D2

### 7. Password-Reset Email Rendering (host-header safe)
expected: PasswordResetEmailRenderer builds HTML+text bodies with an env-configured deep link (never from a request header).
result: pass
source: automated
coverage_id: 10-01-D3

### 8. Reset-Token Store (hashed, indexed)
expected: PasswordResetToken entity + repository store only a SHA-256 token_hash with an indexed findByTokenHash lookup.
result: pass
source: automated
coverage_id: 10-02-D1

### 9. V15 Migration Schema Parity
expected: V15 Flyway migration creates password_reset_tokens with a unique token_hash index matching the entity schema.
result: pass
source: automated
coverage_id: 10-02-D2

### 10. forgot-password Reachable, Returns 202 Generic
expected: POST /api/auth/forgot-password reachable without JWT returns 202 with a fixed generic body.
result: pass
source: automated
coverage_id: 10-04-D1

### 11. forgot-password Byte-Identical Response (no enumeration)
expected: forgot-password returns byte-identical status+body for registered vs unregistered email.
result: pass
source: automated
coverage_id: 10-04-D2

### 12. reset-password Valid Token → 200, No Tokens, Login Works
expected: reset-password with a valid token returns 200 with no tokens in body; login with new password works, old password rejected.
result: pass
source: automated
coverage_id: 10-04-D3

### 13. reset-password Rejects Reused/Expired Tokens; Stored Hash Only
expected: Reused and expired reset tokens are rejected 401; the stored token row holds a hash, not the raw emailed token.
result: pass
source: automated
coverage_id: 10-04-D4

### 14. Sessions Revoked After Reset
expected: A refresh token issued before the reset is rejected 401 after a successful reset (all sessions revoked).
result: pass
source: automated
coverage_id: 10-04-D5

### 15. forgot-password Per-IP 429
expected: The (capacity+1)th forgot-password request from one IP returns 429, proving AUTH_PATHS membership.
result: pass
source: automated
coverage_id: 10-04-D6

### 16. Input Validation Edges
expected: blank newPassword -> 400; unknown token -> 401; concurrent forgot requests each 202.
result: pass
source: automated
coverage_id: 10-04-D7

## Summary

total: 16
passed: 16
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
