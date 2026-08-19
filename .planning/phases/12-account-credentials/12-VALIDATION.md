---
phase: 12
slug: account-credentials
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-19
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Reconstructed retroactively from phase artifacts (State B) by /gsd-validate-phase.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot Test) + Testcontainers (Postgres 1.20.6) + MockK 1.13.11 + MockMvc |
| **Config file** | `build.gradle.kts` (`useJUnitPlatform()`, `TESTCONTAINERS_RYUK_DISABLED=true`) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.auth.AccountCredentialsIntegrationTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~33 s (single class); full suite longer (255 tests) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~35 seconds (single class)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 12-01 | 01 | 1 | ACCT-04 | — | Pending email change persisted (user_id, new_email, token_hash, expires_at, used_at); atomic single-use `markUsed` claim (concurrent double-confirm applies at most once) | integration | `./gradlew test --tests "com.catspell.api.auth.AccountCredentialsIntegrationTest"` | ✅ | ✅ green |
| 12-02 | 02 | 1 | ACCT-01, ACCT-03 | — | Wrong current password → distinct 403 `INVALID_CURRENT_PASSWORD` (not 401); confirm email renders to the caller-supplied NEW address only, never `user.email` | integration | `./gradlew test --tests "com.catspell.api.auth.AccountCredentialsIntegrationTest"` | ✅ | ✅ green |
| 12-03 | 03 | 2 | ACCT-01, ACCT-02, ACCT-03, ACCT-04, ACCT-05 | — | Verify-password-before-mutate; revoke-all-sessions + mint-no-tokens on any credential change; 409 on taken address before minting; email swap only inside confirm after atomic claim | integration | `./gradlew test --tests "com.catspell.api.auth.AccountCredentialsIntegrationTest"` | ✅ | ✅ green |
| 12-04 | 04 | 3 | ACCT-01, ACCT-03, ACCT-04 | — | Only confirm-email-change is public (permitAll + shouldNotFilter); change-password/change-email stay JWT-authenticated; change-email added to per-IP rate-limit AUTH_PATHS; no endpoint returns tokens | integration | `./gradlew test --tests "com.catspell.api.auth.AccountCredentialsIntegrationTest"` | ✅ | ✅ green |
| 12-05 | 05 | 4 | ACCT-01, ACCT-02, ACCT-03, ACCT-04, ACCT-05 | — | End-to-end proof: 403 wrong-password (unchanged) + 400 short password; no-token + revoke-all on password change; 409 on taken email (no send, no row); confirm-only swap + verified stamp + revoke; reused/unknown/expired confirm tokens rejected | integration | `./gradlew test --tests "com.catspell.api.auth.AccountCredentialsIntegrationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. `AccountCredentialsIntegrationTest` (`src/test/kotlin/com/catspell/api/auth/AccountCredentialsIntegrationTest.kt`) exercises every ACCT-01..05 behavior end-to-end against Testcontainers Postgres with a mocked `EmailSender` for token capture. No new framework install required.

---

## Manual-Only Verifications

All phase behaviors have automated verification.

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — full coverage)
- [x] No watch-mode flags
- [x] Feedback latency < 35s (single class)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-08-19

---

## Validation Audit 2026-08-19

| Metric | Count |
|--------|-------|
| Requirements audited | 5 (ACCT-01..05) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Reconstructed from SUMMARY/VERIFICATION artifacts (State B). All five requirements map to green integration tests in `AccountCredentialsIntegrationTest` (6/6 passing; `./gradlew test --tests …AccountCredentialsIntegrationTest` → BUILD SUCCESSFUL, 33s). No missing or partial coverage.
