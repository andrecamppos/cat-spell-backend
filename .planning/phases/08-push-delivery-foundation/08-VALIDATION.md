---
phase: 8
slug: push-delivery-foundation
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-17
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test + Testcontainers 1.20.6 + MockK 1.13.11 |
| **Config file** | `build.gradle.kts` (`useJUnitPlatform()`); harness `src/test/kotlin/com/catspell/api/BaseIntegrationTest.kt` |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.push.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~90–150 seconds (Testcontainers PostGIS + MinIO cold start) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.push.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 150 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 1 | PUSH-01 | — | N/A | migration | `./gradlew flywayValidate` (via context start) | ❌ W0 | ⬜ pending |
| 08-01-02 | 01 | 1 | PUSH-01/02 | — | Tokens scoped to owner | integration | `./gradlew test --tests "*DeviceTokenIntegrationTest"` | ❌ W0 | ⬜ pending |
| 08-01-03 | 01 | 1 | PUSH-01/02 | T-08-01 | Only caller's tokens mutated | integration | `./gradlew test --tests "*DeviceTokenIntegrationTest"` | ❌ W0 | ⬜ pending |
| 08-01-04 | 01 | 1 | PUSH-01/02 | T-08-01 | Endpoints require auth | integration | `./gradlew test --tests "*DeviceTokenIntegrationTest"` | ❌ W0 | ⬜ pending |
| 08-01-05 | 01 | 1 | PUSH-01/02 | — | N/A | integration | `./gradlew test --tests "*DeviceTokenIntegrationTest"` | ❌ W0 | ⬜ pending |
| 08-02-01 | 02 | 1 | PUSH-09 | — | N/A | build | `./gradlew dependencies --configuration runtimeClasspath` | ❌ W0 | ⬜ pending |
| 08-02-02 | 02 | 1 | PUSH-09 | — | No provider leak | source | `./gradlew compileKotlin` + import assertion | ❌ W0 | ⬜ pending |
| 08-02-03 | 02 | 1 | PUSH-09 | — | N/A | integration | `./gradlew test --tests "*PushProviderSelectionTest"` | ❌ W0 | ⬜ pending |
| 08-02-04 | 02 | 1 | PUSH-11 | T-08-02 | Fail-fast on bad creds | integration | `./gradlew test --tests "*FirebaseHealth*"` | ❌ W0 | ⬜ pending |
| 08-02-05 | 02 | 1 | PUSH-11 | — | Health authorized-only | integration | `./gradlew test --tests "*FirebaseHealth*"` | ❌ W0 | ⬜ pending |
| 08-03-01 | 03 | 2 | PUSH-03 | — | Only UNREGISTERED prunes | integration | `./gradlew test --tests "*TokenPruningTest"` | ❌ W0 | ⬜ pending |
| 08-03-02 | 03 | 2 | PUSH-12 | — | Payload shape asserted | integration | `./gradlew test --tests "*PushProviderContractTest"` | ❌ W0 | ⬜ pending |
| 08-03-03 | 03 | 2 | PUSH-12 | — | Dry-run auth (manual) | smoke | `./gradlew test --tests "*FcmSmokeTest" -Dpush.smoke=true` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements — `BaseIntegrationTest` (Testcontainers
  PostGIS + MinIO), Spring Boot Test, MockK, and JUnit Platform are already wired in
  `build.gradle.kts`. No framework install needed.
- New test files created during execution (not pre-stubbed): `DeviceTokenIntegrationTest`,
  `PushProviderSelectionTest`, `FirebaseHealthIntegrationTest`, `TokenPruningTest`,
  `PushProviderContractTest`, `FcmSmokeTest` (disabled by default).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real FCM `validate_only` dry-run auth + payload | PUSH-12 | No real Firebase service-account secret in CI (D-11) | Set `FIREBASE_CREDENTIALS_BASE64` + `PUSH_ENABLED=true`, run `./gradlew test --tests "*FcmSmokeTest" -Dpush.smoke=true` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 150s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
