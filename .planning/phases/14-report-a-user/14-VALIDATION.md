---
phase: 14
slug: report-a-user
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-09-25
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL/PostGIS) + MockMvc + MockK + Awaitility |
| **Config file** | `build.gradle.kts` (test task; Testcontainers self-provisions containers) |
| **Quick run command** | `./gradlew test --tests "*Report*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60s warm (~30–90s cold Testcontainers startup) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*Report*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 14-01 T1–T3 | 14-01 | 1 | MOD-06/07/08 | T-14-01..06 | V21 CHECK backstops (self, category), no dedupe, reporter-safe DTO | integration | `./gradlew test --tests "*Block*"` | ✅ | ✅ green |
| 14-02 T1 | 14-02 | 2 | MOD-06, MOD-08, MOD-07 | T-14-10..16 | self→404→429 guard order, atomic alsoBlock, in-tx event publish (no inline email) | source+compile | `./gradlew compileKotlin` | ✅ | ✅ green |
| 14-02 T2 | 14-02 | 2 | MOD-06, MOD-08 | T-14-10..15 | persist/no-dedupe(2 rows), self, 404, 429 (+independent buckets), alsoBlock true/false | integration | `./gradlew test --tests "*ReportServiceIntegrationTest*"` | ✅ | ✅ green |
| 14-03 T1 | 14-03 | 3 | MOD-07 | T-14-20..24 | @Async AFTER_COMMIT listener, swallow+log, operator-only recipient, no entity deref | source+compile | `./gradlew compileKotlin` | ✅ | ✅ green |
| 14-03 T2 | 14-03 | 3 | MOD-07 | T-14-20,21 | operator emailed once after commit; report survives email-send failure | integration | `./gradlew test --tests "*ReportNotificationIntegrationTest*"` | ✅ | ✅ green |
| 14-04 T1 | 14-04 | 3 | MOD-06, MOD-08 | T-14-30..35 | reporter from JWT principal (not body); 201 { reportId } only (D-11) | source+compile | `./gradlew compileKotlin` | ✅ | ✅ green |
| 14-04 T2 | 14-04 | 3 | MOD-06, MOD-08 | T-14-30..35 | 201 + 400(blank/1001/lowercase/self)/404/429/401 + 1000 boundary + alsoBlock block | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

### Requirement Coverage Rollup

| Requirement | Covering Tests | Status |
|-------------|----------------|--------|
| **MOD-06** (report with category + required details) | ReportServiceIntegrationTest (persist, no-dedupe), ReportEndpointIntegrationTest (201, blank/1000/1001 boundary, lowercase category, self, 404) | ✅ COVERED |
| **MOD-07** (persist + out-of-band operator notify; survives email failure) | ReportNotificationIntegrationTest (emailed once after commit; report survives email-send failure) | ✅ COVERED |
| **MOD-08** (optional "also block" flag) | ReportServiceIntegrationTest (alsoBlock true/false), ReportEndpointIntegrationTest (alsoBlock true → 201 + blocked) | ✅ COVERED |

---

## Wave 0 Requirements

- [x] `ReportEndpointIntegrationTest.kt` — endpoint + validation + status codes (MOD-06, MOD-08)
- [x] `ReportServiceIntegrationTest.kt` — persistence, self-report, 404, rate-limit, alsoBlock delegation (MOD-06, MOD-08)
- [x] `ReportNotificationIntegrationTest.kt` — AFTER_COMMIT async operator email + email-failure survives the report (MOD-07)

*Delivered per-plan via TDD (not a separate Wave 0) — all three reuse the existing Block Testcontainers harness; no new framework install required.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| _(none — all behaviors have automated verification via Testcontainers + mocked EmailSender)_ | — | — | — |

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-09-28 — full `./gradlew test --tests "*Report*"` BUILD SUCCESSFUL.

---

## Validation Audit 2026-09-28

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Audit reconstructed the placeholder Per-Task Map from the 4 PLAN/SUMMARY files and cross-referenced every requirement (MOD-06/07/08) to an existing green integration test. All three requirements are COVERED; `./gradlew test --tests "*Report*"` was re-run and returned BUILD SUCCESSFUL (18 Report test methods across 3 suites). No new tests were required.
