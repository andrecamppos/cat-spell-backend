---
phase: 7
slug: mixed-discovery-feed
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-22
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
| 07-01-01 | 01 | 1 | DISC-08 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ⬜ pending |
| 07-01-02 | 01 | 1 | DISC-09 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ⬜ pending |
| 07-02-01 | 02 | 1 | DISC-10 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ⬜ pending |
| 07-02-02 | 02 | 1 | DISC-11 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ⬜ pending |
| 07-03-01 | 03 | 2 | DISC-12 | — | N/A | integration | `./gradlew test --tests "com.catspell.api.discovery.*"` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. Testcontainers-based integration tests with PostgreSQL + PostGIS + MinIO are fully operational from Phase 6. No new test framework or fixture setup needed.

---

## Manual-Only Verifications

All phase behaviors have automated verification.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
