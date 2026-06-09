---
phase: 1
slug: foundation-auth
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2025-06-09
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `build.gradle.kts` (test dependencies) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.auth.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds (with Testcontainers PostgreSQL startup) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.auth.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | AUTH-01 | — | N/A | integration | `./gradlew test --tests "*AuthIntegrationTest*register*"` | ❌ W0 | ⬜ pending |
| 01-01-02 | 01 | 1 | AUTH-02 | — | JWT rejected without valid signature | integration | `./gradlew test --tests "*AuthIntegrationTest*login*"` | ❌ W0 | ⬜ pending |
| 01-01-03 | 01 | 1 | AUTH-03 | — | Revoked refresh token rejected; rotation creates new token | integration | `./gradlew test --tests "*AuthIntegrationTest*refresh*"` | ❌ W0 | ⬜ pending |
| 01-01-04 | 01 | 1 | AUTH-02 | — | Protected endpoint rejects request without JWT | integration | `./gradlew test --tests "*AuthIntegrationTest*protected*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/catspell/api/auth/AuthIntegrationTest.kt` — integration test stubs for AUTH-01, AUTH-02, AUTH-03
- [ ] Testcontainers PostgreSQL dependency in `build.gradle.kts`
- [ ] Spring Boot Test dependency in `build.gradle.kts`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Docker Compose PostgreSQL starts and app connects | AUTH-01 | Infrastructure startup | Run `docker compose up -d`, then `./gradlew bootRun`, verify app starts without errors |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
