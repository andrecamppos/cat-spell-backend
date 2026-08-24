---
phase: 11
slug: email-verification
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-13
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Reconstructed retroactively from phase artifacts (State B: SUMMARYs present, no prior VALIDATION.md).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (Postgres/PostGIS) + MockK |
| **Config file** | `build.gradle.kts`, `src/test/resources/application.yml` |
| **Quick run command** | `./gradlew test --tests 'com.catspell.api.auth.EmailVerificationIntegrationTest'` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~2–5 min full suite (Testcontainers Postgres boot; requires podman socket per AGENTS.md) |

---

## Sampling Rate

- **After every task commit:** Run the relevant `--tests` class filter
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~300 seconds (Testcontainers-backed integration suite)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | VERIFY-02 | T-11-01 / T-11-02 | Store only SHA-256 hash; single-use claim via atomic `markUsed` (`usedAt IS NULL` guard) | integration | `./gradlew test --tests '*EmailVerificationIntegrationTest'` | ✅ | ✅ green |
| 11-01-02 | 01 | 1 | VERIFY-05 | T-11-03 / T-11-04 | V16 unique `token_hash`; V17 nullable `email_verified_at` + grandfather backfill (no lockout, no drift) | integration | `./gradlew test --tests '*GrandfatherMigrationTest'` | ✅ | ✅ green |
| 11-02-01 | 02 | 1 | VERIFY-01 | T-11-06 | Raw token only ever inside the email body (deep link), never persisted/logged | integration | `./gradlew test --tests '*EmailVerificationIntegrationTest'` | ✅ | ✅ green |
| 11-03-01 | 03 | 2 | VERIFY-04 | — | Enumeration-safe resend: per-email Bucket4j + always-normal-return for unknown/verified/rate-limited | integration | `./gradlew test --tests '*EmailVerificationIntegrationTest'` | ✅ | ✅ green |
| 11-03-02 | 03 | 2 | VERIFY-02, VERIFY-03 | T-11-02 | verifyEmail atomic single-use, no session; login gate 403 EMAIL_NOT_VERIFIED after password check | integration | `./gradlew test --tests '*EmailVerificationIntegrationTest'` | ✅ | ✅ green |
| 11-03-03 | 03 | 2 | VERIFY-01 | — | verify-email + resend-verification whitelisted in 3 security places (public reachability) | integration | `./gradlew test --tests '*EmailVerificationIntegrationTest'` | ✅ | ✅ green |
| 11-04-01 | 04 | 3 | VERIFY-01 | — | register creates unverified user, sends exactly one email, returns 201 with no tokens | integration | `./gradlew test --tests '*EmailVerificationIntegrationTest'` | ✅ | ✅ green |
| 11-04-02 | 04 | 3 | VERIFY-02, VERIFY-03, VERIFY-04 | T-11-02 | verify/resend endpoints + 403 gate contract proven end-to-end (7 cases) | integration | `./gradlew test --tests '*EmailVerificationIntegrationTest'` | ✅ | ✅ green |
| 11-05-01 | 05 | 4 | VERIFY-03 | — | Whole integration suite migrated to no-token register + login hard-gate (gate stays honest) | integration | `./gradlew test` | ✅ | ✅ green |
| 11-05-02 | 05 | 4 | VERIFY-04 | — | resend-verification per-IP rate-limited: (capacity+1)th request from one IP → 429 | integration | `./gradlew test --tests '*RateLimitIntegrationTest'` | ✅ | ✅ green |
| 11-05-03 | 05 | 4 | VERIFY-05 | T-11-03 | V17 backfill unlocks legacy NULL-verified account; idempotent, non-overwriting | integration | `./gradlew test --tests '*GrandfatherMigrationTest'` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No Wave 0 test scaffolding was needed — Phase 11 reused the established Testcontainers + MockK integration-test harness (`BaseIntegrationTest`), adding a shared `markEmailVerified(email)` helper and two new test classes (`EmailVerificationIntegrationTest`, `GrandfatherMigrationTest`).

---

## Manual-Only Verifications

All phase behaviors have automated verification.

Every observable truth (VERIFY-01..05) is exercised by a passing Testcontainers-backed integration test. The concrete transactional-email provider remains deferred (no-op/logging `EmailSender` seam), so there is no live-send behavior to verify manually this phase.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — existing infra sufficient)
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-08-13

---

## Validation Audit 2026-08-13

| Metric | Count |
|--------|-------|
| Requirements audited | 5 (VERIFY-01..05) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

**Result:** Nyquist-compliant. Reconstructed from artifacts (11-01..05 SUMMARY.md + 11-VERIFICATION.md). Every requirement maps to a behavior-targeting integration test that is present on disk and reported green (full suite: 249 passed, 1 pre-existing skip). No test generation required.
