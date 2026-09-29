---
phase: 15
slug: age-verification
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-09-28
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (`@SpringBootTest` + `@AutoConfigureMockMvc`) + Testcontainers (Postgres/PostGIS + MinIO) |
| **Config file** | `build.gradle.kts` (test deps); `src/test/kotlin/com/catspell/api/BaseIntegrationTest.kt` (container + per-test DB truncate) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.auth.*" --tests "com.catspell.api.age.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60–180 seconds (Testcontainers boot dominates; reused across the suite) |

---

## Sampling Rate

- **After every task commit:** Run the quick command for the touched module (`auth`, `age`, `profile`, or `discovery`).
- **After every plan wave:** Run `./gradlew test`.
- **Before `/gsd-verify-work`:** Full suite must be green.
- **Max feedback latency:** ~180 seconds (first run; faster on warm containers).

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| Wave 0 | 01 | 0 | AGE-01/02/03 | — | Failing tests exist for every new behavior before impl | integration | `./gradlew test --tests "com.catspell.api.auth.AgeGateIntegrationTest"` | ✅ | ✅ green |
| AgeVerifier seam | 01 | 1 | AGE-02 | T-15-01 | `requireAdult` throws for <18, passes for ≥18 (server-side) | unit/integration | `./gradlew test --tests "com.catspell.api.age.LocalAgeVerifierTest"` | ✅ | ✅ green |
| Register DOB + gate | 01 | 1 | AGE-01, AGE-02 | T-15-01 / T-15-05 | Under-18 → 422 `UNDER_MINIMUM_AGE`, no `users` row; 18+ persists DOB | integration | `./gradlew test --tests "com.catspell.api.auth.AgeGateIntegrationTest"` | ✅ | ✅ green |
| Under-18 error contract | 01 | 1 | AGE-02 | T-15-05 | 422 RFC 7807 body carries `code` and no account detail | integration | `./gradlew test --tests "com.catspell.api.auth.AgeGateIntegrationTest"` | ✅ | ✅ green |
| V22 migration | 01 | 1 | AGE-03 | T-15-04 | Backfill sets DOB from completed profiles; NULL-DOB grandfathered, still usable | integration | `./gradlew test --tests "com.catspell.api.auth.DobMigrationTest"` | ✅ | ✅ green |
| DOB immutability | 01 | 2 | AGE-01 | T-15-03 | Profile update cannot set DOB (field absent) | integration | `./gradlew test --tests "com.catspell.api.profile.ProfileIntegrationTest"` | ✅ | ✅ green |
| Discovery reads users.DOB | 01 | 2 | (D-05) | T-15-04 | Age-range filter unchanged after DOB sourced from `users` | integration | `./gradlew test --tests "com.catspell.api.discovery.DiscoveryIntegrationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/kotlin/com/catspell/api/age/LocalAgeVerifierTest.kt` — boundary math (exactly 18 passes, 17y364d fails, future DOB rejected).
- [x] `src/test/kotlin/com/catspell/api/auth/AgeGateIntegrationTest.kt` — AGE-01/AGE-02 (register accepts + persists DOB; under-18 → 422 + `code` + no row; 18+ succeeds).
- [x] `src/test/kotlin/com/catspell/api/auth/DobMigrationTest.kt` — V22 backfill + grandfather (mirrors `GrandfatherMigrationTest`).
- [x] Update register helpers in the ~12 existing DOB-dependent test files to supply `dateOfBirth` at register; drop DOB from profile-creation bodies. *(31 test files updated per 15-02-SUMMARY.)*

*Existing Testcontainers/Gradle infrastructure covers execution; only the above test files/fixtures are new or need updating.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mobile app routes to a tailored under-18 screen on `code=UNDER_MINIMUM_AGE` | AGE-02 | Client-side routing lives in the separate mobile repo | Register with an under-18 DOB; confirm the app shows the age-rejection screen (not a generic error). Backend contract is auto-verified. |

*All server-side phase behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-09-29 (retroactive audit — all 7 tasks green per 15-VERIFICATION.md `./gradlew test` BUILD SUCCESSFUL)

---

## Validation Audit 2026-09-29

Retroactive Nyquist audit (State A) of the executed phase. All test files referenced in the Per-Task Map exist on disk and substantively target their behaviors; green status sourced from the same-day `15-VERIFICATION.md` (full `./gradlew test` BUILD SUCCESSFUL, 0 failures). No auditor subagent needed — no gaps.

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
