---
phase: 02
slug: user-profiles-photos
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2025-06-11
---

# Phase 02 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `src/test/resources/application.yml` |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.profile.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.profile.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | — | — | Testcontainers replaces H2 without breaking Phase 1 | integration | `./gradlew test` | ✅ | ✅ green |
| 02-01-02 | 01 | 1 | PROF-01, PROF-02, PROF-05 | T-02-01, T-02-02 | Profile CRUD with validation, JWT auth enforced | integration | `./gradlew build -x test` | ✅ | ✅ green |
| 02-01-03 | 01 | 1 | PROF-01, PROF-02, PROF-05 | T-02-01, T-02-02, T-02-06 | All profile behaviors verified via 15 integration tests | integration | `./gradlew test --tests "com.catspell.api.profile.ProfileIntegrationTest"` | ✅ | ✅ green |
| 02-02-01 | 02 | 2 | — | — | S3 infra compiles, MinIO configured | compilation | `./gradlew build -x test` | ✅ | ✅ green |
| 02-02-02 | 02 | 2 | PROF-03, PROF-04 | T-02-07, T-02-08, T-02-12, T-02-14 | Photo upload/delete/reorder, thumbnail, completeness | integration | `./gradlew build -x test` | ✅ | ✅ green |
| 02-02-03 | 02 | 2 | PROF-03, PROF-04 | T-02-07, T-02-08, T-02-12, T-02-14 | All photo + completeness behaviors via 15 integration tests | integration | `./gradlew test --tests "com.catspell.api.profile.PhotoIntegrationTest" --tests "com.catspell.api.profile.CompletenessIntegrationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Requirement Coverage

| Requirement | Description | Tests | Status |
|-------------|-------------|-------|--------|
| **PROF-01** | Create profile with display name, bio, preferences | `create profile successfully`, `create profile duplicate returns conflict`, `create profile underage returns bad request`, `create profile invalid age range returns bad request`, `create profile missing required fields returns bad request`, `create profile bio too long returns bad request`, `create profile invalid gender returns bad request` | ✅ COVERED |
| **PROF-02** | Edit own profile | `update profile successfully`, `update profile partial only changes provided fields`, `get profile successfully`, `get profile not found` | ✅ COVERED |
| **PROF-03** | Upload photos to S3 | `request upload url successfully`, `request upload url invalid type returns bad request`, `request upload url exceeds limit returns bad request`, `confirm upload successfully`, `confirm upload photo not found`, `reorder photos successfully`, `reorder photos mismatch returns bad request`, `list photos successfully`, `photo endpoints require authentication` | ✅ COVERED |
| **PROF-04** | Delete own photos | `delete photo successfully`, `delete photo not owned returns not found` | ✅ COVERED |
| **PROF-05** | Set/update GPS location | `update location successfully`, `update location without profile returns not found`, `update location invalid coordinates returns bad request` | ✅ COVERED |
| **D-13** | Profile completeness | `incomplete without profile`, `incomplete without photo`, `incomplete without location`, `complete profile` | ✅ COVERED |

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements.

---

## Manual-Only Verifications

All phase behaviors have automated verification.

---

## Validation Audit 2025-06-11

| Metric | Count |
|--------|-------|
| Gaps found | 2 |
| Resolved | 2 |
| Escalated | 0 |

### Gaps Resolved

1. **Invalid gender validation** (MISSING → COVERED): Added `create profile invalid gender returns bad request` test + `validateGender`/`validateGenderPreference` in `ProfileService`
2. **Partial update isolation** (MISSING → COVERED): Added `update profile partial only changes provided fields` test verifying only provided fields change

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2025-06-11
