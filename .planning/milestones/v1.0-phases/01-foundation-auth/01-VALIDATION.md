---
phase: 1
slug: foundation-auth
status: audited
nyquist_compliant: true
wave_0_complete: true
created: 2025-06-09
audited: 2026-06-18
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + H2 (PostgreSQL compat mode) |
| **Config file** | `build.gradle.kts` (test dependencies) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.auth.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~33 seconds |
| **Test count (auth)** | 31 (15 + 8 + 8) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.auth.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 33 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | AUTH-01 | T-01-01 | BCrypt password hashing; vague error messages prevent user enumeration | integration | `./gradlew test --tests "*AuthIntegrationTest*register*"` | ✅ | ✅ green |
| 01-01-02 | 01 | 1 | AUTH-02 | T-01-02 | JWT rejected without valid signature (HS512) | integration | `./gradlew test --tests "*AuthIntegrationTest*login*"` | ✅ | ✅ green |
| 01-01-03 | 01 | 1 | AUTH-02 | T-01-06 | Protected endpoint rejects request without JWT | integration | `./gradlew test --tests "*AuthIntegrationTest*protected*"` | ✅ | ✅ green |
| 01-02-01 | 02 | 2 | AUTH-03 | T-01-08 | Revoked refresh token rejected; rotation creates new token; theft detection | integration | `./gradlew test --tests "*RefreshTokenIntegrationTest*"` | ✅ | ✅ green |
| 01-02-02 | 02 | 2 | AUTH-03 | T-01-09 | Expired tokens rejected; multi-device sessions independent | integration | `./gradlew test --tests "*RefreshTokenIntegrationTest*"` | ✅ | ✅ green |
| 01-03-01 | 03 | 2 | AUTH-01, AUTH-02 | T-01-12 | Vague auth errors prevent user enumeration; RFC 7807 format | integration | `./gradlew test --tests "*ErrorHandlingIntegrationTest*"` | ✅ | ✅ green |
| 01-03-02 | 03 | 2 | AUTH-01 | T-01-04 | Field-level validation errors with violations array (D-14) | integration | `./gradlew test --tests "*ErrorHandlingIntegrationTest*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Test File Inventory

| File | Tests | Covers |
|------|-------|--------|
| `src/test/kotlin/com/catspell/api/auth/AuthIntegrationTest.kt` | 15 | AUTH-01 (register), AUTH-02 (login, protected endpoints), AUTH-03 (refresh basics) |
| `src/test/kotlin/com/catspell/api/auth/RefreshTokenIntegrationTest.kt` | 8 | AUTH-03 (rotation, theft detection, expiry, multi-device) |
| `src/test/kotlin/com/catspell/api/auth/ErrorHandlingIntegrationTest.kt` | 8 | AUTH-01/AUTH-02 (RFC 7807 format, validation errors, vague auth errors) |

---

## Wave 0 Requirements

- [x] `src/test/kotlin/com/catspell/api/auth/AuthIntegrationTest.kt` — 15 integration tests for AUTH-01, AUTH-02, AUTH-03
- [x] `src/test/kotlin/com/catspell/api/auth/RefreshTokenIntegrationTest.kt` — 8 integration tests for AUTH-03
- [x] `src/test/kotlin/com/catspell/api/auth/ErrorHandlingIntegrationTest.kt` — 8 integration tests for RFC 7807
- [x] Spring Boot Test dependency in `build.gradle.kts`
- [x] H2 (PostgreSQL compat) for test runtime (deviation from Testcontainers — see 01-01-SUMMARY.md)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Podman PostgreSQL starts and app connects | AUTH-01 | Infrastructure startup | Run `podman compose up -d`, then `./gradlew bootRun`, verify app starts without errors |
| Generic 500 error response (no stack trace) | T-01-13 | Requires triggering unexpected exception | Trigger unhandled exception; verify response is generic "An unexpected error occurred" with no stack trace |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 33s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-06-18

---

## Validation Audit 2026-06-18

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Total tests (auth) | 31 |
| Manual-only | 2 |

**Auditor notes:** All AUTH-01, AUTH-02, AUTH-03 requirements have comprehensive automated integration test coverage across 3 test files. VALIDATION.md was in draft state (pre-execution) — updated with actual results. Deviation: H2 in PostgreSQL compat mode used instead of Testcontainers (documented in 01-01-SUMMARY.md). T-01-13 (generic 500 error) moved to manual-only since triggering requires mock injection.
