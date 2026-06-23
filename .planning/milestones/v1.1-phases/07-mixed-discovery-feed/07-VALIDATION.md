---
phase: 7
slug: mixed-discovery-feed
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-22
updated: 2026-06-23
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + PostGIS + MinIO) |
| **Config file** | `build.gradle.kts` (test dependencies already configured) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.discovery.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.discovery.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | DISC-08 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ✅ green |
| 07-01-02 | 01 | 1 | DISC-09 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ✅ green |
| 07-01-03 | 01 | 1 | DISC-10 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ✅ green |
| 07-01-04 | 01 | 1 | DISC-11 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ✅ green |
| 07-01-05 | 01 | 1 | — | — | N/A | grep | `grep -n "swiped on this\|swipe on your" src/main/kotlin/com/catspell/api/common/exception/*.kt` | ✅ | ✅ green |
| 07-02-01 | 02 | 2 | DISC-10 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ✅ green |
| 07-02-02 | 02 | 2 | DISC-11 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ✅ green |
| 07-02-03 | 02 | 2 | DISC-12 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ✅ green |
| 07-02-04 | 02 | 2 | DISC-08–12 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. Testcontainers-based integration tests with PostgreSQL + PostGIS + MinIO are fully operational from Phase 6. No new test framework or fixture setup needed.

---

## Manual-Only Verifications

All phase behaviors have automated verification.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 45s (measured: ~42s)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** ✅ approved

---

## Validation Audit 2026-06-23

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

All 9 tasks across 2 plans have automated verification. 29 integration tests in `DiscoveryIntegrationTest.kt` cover all 5 requirements (DISC-08 through DISC-12). Full suite: 180/180 green.
