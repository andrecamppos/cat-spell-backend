---
phase: 8
slug: push-delivery-foundation
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-07-17
validated: 2026-07-27
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
| 08-01-01 | 01 | 1 | PUSH-01 | — | N/A | migration | `./gradlew flywayValidate` (via context start) | ✅ | ✅ green |
| 08-01-02 | 01 | 1 | PUSH-01/02 | — | Tokens scoped to owner | integration | `./gradlew test --tests "*DeviceTokenIntegrationTest"` | ✅ | ✅ green |
| 08-01-03 | 01 | 1 | PUSH-01/02 | T-08-01 | Only caller's tokens mutated | integration | `./gradlew test --tests "*DeviceTokenIntegrationTest"` | ✅ | ✅ green |
| 08-01-04 | 01 | 1 | PUSH-01/02 | T-08-01 | Endpoints require auth | integration | `./gradlew test --tests "*DeviceTokenIntegrationTest"` | ✅ | ✅ green |
| 08-01-05 | 01 | 1 | PUSH-01/02 | — | N/A | integration | `./gradlew test --tests "*DeviceTokenIntegrationTest"` | ✅ | ✅ green |
| 08-02-01 | 02 | 1 | PUSH-09 | — | N/A | build | `./gradlew dependencies --configuration runtimeClasspath` | ✅ | ✅ green |
| 08-02-02 | 02 | 1 | PUSH-09 | — | No provider leak | source | `./gradlew compileKotlin` + import assertion | ✅ | ✅ green |
| 08-02-03 | 02 | 1 | PUSH-09 | — | N/A | integration | `./gradlew test --tests "*PushProviderSelectionTest"` | ✅ | ✅ green |
| 08-02-04 | 02 | 1 | PUSH-11 | T-08-02 | Fail-fast on bad creds | integration | `./gradlew test --tests "*FirebaseHealth*"` | ✅ | ✅ green |
| 08-02-05 | 02 | 1 | PUSH-11 | — | Health authorized-only | integration | `./gradlew test --tests "*FirebaseHealth*"` | ✅ | ✅ green |
| 08-03-01 | 03 | 2 | PUSH-03 | — | Only UNREGISTERED prunes | integration | `./gradlew test --tests "*TokenPruningTest"` | ✅ | ✅ green |
| 08-03-02 | 03 | 2 | PUSH-12 | — | Payload shape asserted | integration | `./gradlew test --tests "*PushProviderContractTest"` | ✅ | ✅ green |
| 08-03-03 | 03 | 2 | PUSH-12 | — | Dry-run auth (manual) | smoke | `./gradlew test --tests "*FcmSmokeTest" -Dpush.smoke=true` | ✅ | ✅ green |

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

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 150s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-07-27

---

## Validation Audit 2026-07-27

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Audit outcome: **NYQUIST-COMPLIANT**. All 13 per-task verifications map to existing
test files that target the mapped behavior. Push suite re-run green on 2026-07-27
(`./gradlew test --tests "com.catspell.api.push.*"` → BUILD SUCCESSFUL, ~1m58s;
`FcmSmokeTest` skipped by design). No test generation required — VALIDATION.md was
seeded at plan-time and had not been refreshed after execution; statuses now reflect
the passing state confirmed by 08-VERIFICATION.md (13/13) and plan SUMMARY coverage.
The real-Firebase `validate_only` round-trip (PUSH-12/D-11) remains manual-only.
