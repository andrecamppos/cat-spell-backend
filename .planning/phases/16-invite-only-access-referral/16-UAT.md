---
status: complete
phase: 16-invite-only-access-referral
source: [16-01-SUMMARY.md, 16-02-SUMMARY.md, 16-03-SUMMARY.md, 16-04-SUMMARY.md]
started: 2026-10-01T13:20:29Z
updated: 2026-10-01T13:28:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running server; reset ephemeral state (podman compose down -v then up -d); start the app from scratch (./gradlew bootRun). Server boots without errors, Flyway applies all migrations including V23 (invites + referrals) against an empty schema, and a primary request succeeds (health check or public-mode register → 201).
result: pass
notes: "Fresh DB (podman compose down -v + up -d). ./gradlew bootRun: all 23 Flyway migrations applied incl v23 'create invites and referrals'; app started on 8080 with no errors. /actuator/health → 200 UP; POST /api/auth/register (public mode, no inviteCode) → 201; invites + referrals tables confirmed present."

### 2. V23 schema and constraints (INV-04)
expected: V23 creates invites + referrals with the expected columns, UNIQUE(code_hash), UNIQUE(invitee_id), and chk_referrals_no_self CHECK; entities validate under Flyway + ddl-auto=validate
result: pass
source: automated
coverage_id: 16-01-D1

### 3. Atomic single-use claim + referral storage constraints (INV-05)
expected: InviteRepository.markConsumed is the atomic single-use claim (conditional UPDATE WHERE consumed_at IS NULL); referral attribution constraints enforced at the storage tier
result: pass
source: automated
coverage_id: 16-01-D2

### 4. Invite codes hashed at rest (INV-04)
expected: Invite codes are high-entropy, hashed-at-rest (code_hash == SHA-256(raw)), and the raw code is never persisted
result: pass
source: automated
coverage_id: 16-02-D1

### 5. All validate() failures return generic 403 (INV-04)
expected: All validate() failure modes (null/blank/unknown/consumed) throw the single generic InviteRequiredException → 403 INVITE_REQUIRED
result: pass
source: automated
coverage_id: 16-02-D2

### 6. Single-use is atomic under concurrency (INV-04)
expected: Concurrent consumption of one code yields exactly one winner, one consumed invite
result: pass
source: automated
coverage_id: 16-02-D3

### 7. Referral attribution only for a real distinct referrer (INV-05)
expected: Referral attribution written only for a real distinct referrer; bootstrap and self-referral write none
result: pass
source: automated
coverage_id: 16-02-D4

### 8. Admin issuance with correct token (INV-02)
expected: POST /api/admin/invites with a correct X-Admin-Token returns 201 with the raw code once and stores it hashed
result: pass
source: automated
coverage_id: 16-03-D1

### 9. Missing/wrong admin token → generic 401 (INV-02)
expected: Missing/wrong token returns generic 401 and mints nothing; constant-time compare
result: pass
source: automated
coverage_id: 16-03-D2

### 10. Deny-by-default on blank admin token (INV-02)
expected: A blank app.invite.admin-token rejects every request on the permitAll route
result: pass
source: automated
coverage_id: 16-03-D3

### 11. Referrer validation on issuance (INV-02)
expected: Unknown referrerUserId → 400; real referrer and bootstrap (no referrer) → 201
result: pass
source: automated
coverage_id: 16-03-D4

### 12. Public mode bypasses the gate (INV-01)
expected: Public mode (gate off) returns 201 for missing/empty/garbage inviteCode and consumes nothing
result: pass
source: automated
coverage_id: 16-04-D1

### 13. Gated register requires a valid unconsumed code (INV-03)
expected: Gated register: valid code → 201 + consumed; missing → 403 INVITE_REQUIRED, no users row
result: pass
source: automated
coverage_id: 16-04-D2

### 14. Enumeration-safe gated failures, no orphan account (INV-04)
expected: Invalid, consumed, and missing gated codes return a byte-identical 403/title/INVITE_REQUIRED; a rejected register leaves no orphan account
result: pass
source: automated
coverage_id: 16-04-D3

## Summary

total: 14
passed: 14
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
