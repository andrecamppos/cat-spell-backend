---
phase: 6
slug: api-polish-integration-tests
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-16
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `build.gradle.kts` (test configuration) |
| **Quick run command** | `./gradlew test --tests "*IntegrationTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | D-01,D-02,D-03,D-04 | integration | `./gradlew test --tests "*OpenApiIntegrationTest"` | ❌ W0 | ⬜ pending |
| 06-01-02 | 01 | 1 | D-05,D-06,D-07,D-08 | integration | `./gradlew test --tests "*RateLimitIntegrationTest"` | ❌ W0 | ⬜ pending |
| 06-01-03 | 01 | 1 | D-09,D-10,D-11 | integration | `./gradlew test --tests "*HealthEndpointIntegrationTest"` | ❌ W0 | ⬜ pending |
| 06-02-01 | 02 | 2 | D-12 | integration | `./gradlew test` | ✅ existing | ⬜ pending |
| 06-02-02 | 02 | 2 | D-13 | integration | `./gradlew test --tests "*RateLimit* *Health* *OpenApi*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing test infrastructure (BaseIntegrationTest + Testcontainers) covers all phase requirements
- No new test framework setup needed

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
