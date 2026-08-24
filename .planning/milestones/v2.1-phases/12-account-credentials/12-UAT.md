---
status: complete
phase: 12-account-credentials
source: [12-01-SUMMARY.md, 12-02-SUMMARY.md, 12-03-SUMMARY.md, 12-04-SUMMARY.md, 12-05-SUMMARY.md]
started: 2026-08-24T09:34:16Z
updated: 2026-08-24T09:40:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running server/service. Clear ephemeral state (temp DBs, caches, lock files). Start the application from scratch. Server boots without errors, Flyway V18 (email_change_requests table) migration applies cleanly, and a primary query (health check, homepage load, or basic API call) returns live data.
result: pass

### 2. EmailChangeRequest persistence (email_change_requests table)
expected: EmailChangeRequest entity + email_change_requests table persist a pending email change (user_id, new_email, token_hash, expires_at, used_at, created_at).
result: pass
source: automated
coverage_id: 12-01-D1

### 3. Atomic single-use markUsed claim
expected: Atomic single-use markUsed claim (UPDATE ... WHERE usedAt IS NULL) so concurrent double-confirm applies at most once.
result: pass
source: automated
coverage_id: 12-01-D2

### 4. Change-email confirm renderer targets new address
expected: EmailChangeEmailRenderer renders a confirm deep-link (app.confirm-email-change-url?token=<raw>) to the caller-supplied new address.
result: pass
source: automated
coverage_id: 12-02-D1

### 5. Wrong current password maps to 403 INVALID_CURRENT_PASSWORD
expected: InvalidCurrentPasswordException maps to a 403 ProblemDetail carrying code=INVALID_CURRENT_PASSWORD.
result: pass
source: automated
coverage_id: 12-02-D2

### 6. changePassword: verify-then-mutate + revoke-all + no-tokens
expected: changePassword — wrong current password -> 403 INVALID_CURRENT_PASSWORD (no state change); correct -> re-hash + revoke all sessions, no tokens.
result: pass
source: automated
coverage_id: 12-03-D1

### 7. confirmEmailChange: atomic claim + email swap + revoke
expected: confirmEmailChange — atomic markUsed claim (unknown/expired/reused -> InvalidTokenException); on success swaps users.email, stamps emailVerifiedAt, revokes all sessions.
result: pass
source: automated
coverage_id: 12-03-D2

### 8. EmailChangeService.requestChange invariants
expected: EmailChangeService.requestChange — verifies password, rejects taken address (409) before minting, per-email 429 guard, mints hashed single-use token, emails confirm link to the NEW address; never changes users.email.
result: pass
source: automated
coverage_id: 12-03-D3

### 9. Change-credential request DTO validation
expected: Three request DTOs with correct @field: validation (Size(min=8) newPassword, Email newEmail, plain token).
result: pass
source: automated
coverage_id: 12-04-D1

### 10. Endpoint auth posture
expected: change-password + change-email endpoints authenticated (resolve caller via extractUserId, no token body); confirm-email-change public; none returns tokens.
result: pass
source: automated
coverage_id: 12-04-D2

### 11. Security whitelist wiring
expected: confirm-email-change whitelisted in SecurityConfig + JwtAuthenticationFilter; change-email in RateLimitFilter.AUTH_PATHS; change endpoints NOT whitelisted.
result: pass
source: automated
coverage_id: 12-04-D3

### 12. change-password end-to-end (integration)
expected: change-password — wrong current password -> 403 INVALID_CURRENT_PASSWORD (unchanged); success -> no tokens + new password logs in + pre-change refresh revoked; short password -> 400.
result: pass
source: automated
coverage_id: 12-05-D1

### 13. change-email taken address end-to-end (integration)
expected: change-email to a taken address -> 409 with no confirm email and no pending row.
result: pass
source: automated
coverage_id: 12-05-D2

### 14. change-email request/confirm end-to-end (integration)
expected: change-email emails the NEW address, account email unchanged until confirm; confirm swaps email, stamps verified, revokes pre-confirm sessions; reused/unknown/expired tokens rejected.
result: pass
source: automated
coverage_id: 12-05-D3

## Summary

total: 14
passed: 14
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
