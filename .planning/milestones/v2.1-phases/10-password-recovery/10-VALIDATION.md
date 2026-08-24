---
phase: 10
slug: password-recovery
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-07
validated: 2026-08-08
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (JUnit Platform) + Spring Boot Test + MockK 1.13.11; Testcontainers (postgres 1.20.6) for integration |
| **Config file** | `build.gradle.kts` (`tasks.withType<Test> { useJUnitPlatform() }`) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.email.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~90 seconds full suite (Testcontainers boot dominates); email unit tests < 10s |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.email.*"` (unit, fast) or the plan-relevant test class.
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 10-01-01 | 01 | 1 | EMAIL-01, EMAIL-02 | T-10-01 / T-10-03 | No-op sender is the default bean; masks recipient, never logs token, no network I/O | unit | `./gradlew test --tests "com.catspell.api.email.*"` | ✅ | ✅ green |
| 10-01-02 | 01 | 1 | RECOV-02 | T-10-02 | Reset deep link built from env `app.reset-password-url`, never from HTTP request (host-header safe) | unit / integration | `./gradlew test --tests "com.catspell.api.auth.PasswordResetIntegrationTest"` | ✅ | ✅ green |
| 10-01-03 | 01 | 1 | EMAIL-01, EMAIL-02 | T-10-01 / T-10-03 | Contract SUCCESS incl. concurrency; default-bean selection proven | unit | `./gradlew test --tests "com.catspell.api.email.*"` | ✅ | ✅ green |
| 10-02-01 | 02 | 2 | RECOV-05 | T-10-04 / T-10-06 / T-10-07 | Stores only SHA-256 token_hash (no raw column); parameterized lookup; entity↔V15 schema parity | integration | `./gradlew test --tests "com.catspell.api.auth.PasswordResetIntegrationTest"` | ✅ | ✅ green |
| 10-02-02 | 02 | 2 | RECOV-05 | T-10-05 | V15 UNIQUE token_hash index + nullable used_at single-use marker | integration | `./gradlew test --tests "com.catspell.api.auth.PasswordResetIntegrationTest"` | ✅ | ✅ green |
| 10-03-01 | 03 | 3 | RECOV-02, RECOV-04, RECOV-07 | T-10-08 / T-10-09 / T-10-13 | Enumeration-safe forgot flow; 256-bit SecureRandom token, 30-min TTL; per-email + per-IP rate limit | integration | `./gradlew test --tests "com.catspell.api.auth.PasswordResetIntegrationTest" --tests "com.catspell.api.common.RateLimitIntegrationTest"` | ✅ | ✅ green |
| 10-03-02 | 03 | 3 | RECOV-05, RECOV-06 | T-10-10 / T-10-11 / T-10-12 | Transactional single-use consume; reused/expired → 401; revoke-all sessions; user from token FK | integration | `./gradlew test --tests "com.catspell.api.auth.PasswordResetIntegrationTest"` | ✅ | ✅ green |
| 10-03-03 | 03 | 3 | RECOV-07 | T-10-15 | Both endpoints public across all 3 security tiers; forgot-password in AUTH_PATHS | integration | `./gradlew test --tests "com.catspell.api.common.RateLimitIntegrationTest"` | ✅ | ✅ green |
| 10-04-01 | 04 | 4 | RECOV-01, RECOV-03 | T-10-17 / T-10-20 | Public 202 generic forgot; 200 Void reset (no auto-login/tokens); @Email + @Size(min=8) validation | integration | `./gradlew test --tests "com.catspell.api.auth.PasswordResetIntegrationTest"` | ✅ | ✅ green |
| 10-04-02 | 04 | 4 | RECOV-01, RECOV-03, RECOV-04, RECOV-05, RECOV-06 | T-10-16 | End-to-end enumeration-safety (byte-identical response), single-use, hashed storage, session revocation | integration | `./gradlew test --tests "com.catspell.api.auth.PasswordResetIntegrationTest"` | ✅ | ✅ green |
| 10-04-03 | 04 | 4 | RECOV-07 | T-10-18 | (capacity+1)th forgot-password per IP → 429 (proves AUTH_PATHS membership) | integration | `./gradlew test --tests "com.catspell.api.common.RateLimitIntegrationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.* JUnit 5 + Spring Boot Test + MockK + Testcontainers were already configured in `build.gradle.kts`; no new test framework install was required. All phase test files were authored within their respective plans.

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

The three human-judgment items recorded in `10-03-SUMMARY.md` (enumeration-safety, transactional replay/expiry, revoke-all) are proven behaviorally by the Plan 04 integration tests (`PasswordResetIntegrationTest` RECOV-03/04/05/06 and `RateLimitIntegrationTest`), so no residual manual-only gaps remain.

Note: T-10-14 (timing side-channel on forgot-password) is an **accepted risk** (AR-10-01), not a validation gap — the ASVS L1 identical-response requirement is automated (RECOV-04); uniform-timing hardening is deferred defense-in-depth.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — existing infra sufficient)
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-08-08

---

## Validation Audit 2026-08-08

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

All 9 phase requirements (EMAIL-01, EMAIL-02, RECOV-01..07) map to automated tests across 4 test files (`EmailSenderContractTest`, `EmailSenderSelectionTest`, `PasswordResetIntegrationTest`, `RateLimitIntegrationTest`). `./gradlew test` for these classes returned BUILD SUCCESSFUL — all green. No auditor spawn required; no test files generated.
