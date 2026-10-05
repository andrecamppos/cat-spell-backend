---
phase: 16
slug: invite-only-access-referral
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-09-30
validated: 2026-10-01
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test + MockMvc + Testcontainers, via `com.catspell.api.BaseIntegrationTest` (MockK 1.13.11 for any unit mocks) |
| **Config file** | `build.gradle.kts` test task (`useJUnitPlatform()`, `TESTCONTAINERS_RYUK_DISABLED=true`); `src/test/resources/application.yml` (test profile: `spring.flyway.enabled=false`, `ddl-auto=create-drop`) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.invite.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | Quick (invite subset): ~120-180s — Testcontainers boots a `postgis/postgis:16-3.4-alpine` Postgres/PostGIS container (+ MinIO) once per JVM fork before the ~7 invite integration classes run. Full suite: ~5-8 min (container boot dominates; individual tests are fast). |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.invite.*"` (plus the affected `auth` register tests for 16-04)
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~180 seconds (quick invite subset, including one container boot)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 16-01-01 | 01 | 1 | INV-04, INV-05 | T-16-01 / T-16-03 | V23 stores only `code_hash` (raw code never has a column); single-use `consumed_at`; nullable `referrer_user_id`; no expiry/revoke columns; deny-by-default empty `admin-token` | integration | `test -f .../V23__create_invites_and_referrals.sql && grep ... && grep -A2 "invite:" application.yml \| grep admin-token` | ✅ | ✅ green |
| 16-01-02 | 01 | 1 | INV-04 | T-16-02 | `InviteRepository.markConsumed` is the atomic single-use claim (conditional UPDATE `WHERE consumed_at IS NULL`); entities validate against V23 | integration | `./gradlew compileKotlin -q` | ✅ | ✅ green |
| 16-01-03 | 01 | 1 | INV-04, INV-05 | T-16-01 / T-16-03 | V23 schema shape + `uq_referrals_invitee` UNIQUE + `chk_referrals_no_self` CHECK proven under real Flyway | integration | `./gradlew test --tests "com.catspell.api.invite.InviteMigrationTest"` | ✅ | ✅ green |
| 16-02-01 | 02 | 2 | INV-04, INV-02 | T-16-06 | One `InviteRequiredException` → single 403 body + `code=INVITE_REQUIRED` for invalid/consumed/missing; `AdminAuthException` → generic 401, no code hint | integration | `./gradlew compileKotlin -q && grep -q "INVITE_REQUIRED" .../GlobalExceptionHandler.kt` | ✅ | ✅ green |
| 16-02-02 | 02 | 2 | INV-02, INV-04, INV-05 | T-16-05 / T-16-07 / T-16-08 / T-16-09 | SecureRandom+Base64url code; SHA-256 hash-at-rest; atomic `markConsumed`; referral row only for a real non-self referrer; validate treats null/blank/not-found/consumed identically | integration | `./gradlew compileKotlin -q` | ✅ | ✅ green |
| 16-02-03 | 02 | 2 | INV-04, INV-05 | T-16-07 / T-16-09 / T-16-08 | Code stored hashed (raw absent from DB); concurrent double-consume → exactly one winner; referral row iff referrer present | integration | `./gradlew test --tests "com.catspell.api.invite.InviteSingleUseIntegrationTest" --tests "com.catspell.api.invite.ReferralAttributionIntegrationTest"` | ✅ | ✅ green |
| 16-03-01 | 03 | 3 | INV-02 | T-16-11 / T-16-12 / T-16-13 | `permitAll` route guarded by constant-time `MessageDigest.isEqual`; deny-by-default when `admin-token` blank; generic 401 | integration | `./gradlew compileKotlin -q && grep -q "/api/admin/invites" .../SecurityConfig.kt && grep -q "MessageDigest.isEqual" .../InviteAdminController.kt` | ✅ | ✅ green |
| 16-03-02 | 03 | 3 | INV-02 | T-16-11 / T-16-13 / T-16-14 | 201 + raw code once for a valid token; 401 for wrong/missing; deny-by-default when unset; 400 for unknown `referrerUserId` | integration | `./gradlew test --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest*"` | ✅ | ✅ green |
| 16-04-01 | 04 | 3 | INV-01, INV-03 | T-16-16 | `RegisterRequest.inviteCode: String? = null` with NO `@field:NotBlank` (optional at DTO; requiredness enforced only when gated) | integration | `./gradlew compileKotlin -q && grep -q "inviteCode" .../AuthDtos.kt` | ✅ | ✅ green |
| 16-04-02 | 04 | 3 | INV-01, INV-03, INV-04 | T-16-16 / T-16-18 / T-16-19 | `@Transactional register` composes age gate → (if enabled) validate → save → consume; invite path never runs when gate off; failed consume rolls back the user insert | integration | `./gradlew compileKotlin -q && grep -q "inviteService.validate" .../AuthService.kt && grep -q "app.invite.enabled" .../AuthService.kt` | ✅ | ✅ green |
| 16-04-03 | 04 | 3 | INV-01, INV-03, INV-04 | T-16-16 / T-16-17 / T-16-18 | Gate OFF → 201 for any/absent code, nothing consumed; gate ON → valid 201 / missing 403; invalid==consumed==missing byte-identical 403; no orphan account on failed consume | integration | `./gradlew test --tests "com.catspell.api.invite.InviteGateIntegrationTest" --tests "com.catspell.api.invite.InvitePublicModeIntegrationTest" --tests "com.catspell.api.invite.InviteEnumerationSafetyIntegrationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*File Exists: ✅ = created by a compile/grep-verified task · ❌ W0 = integration test file must be authored (Wave-0 dependency, see below)*

---

## Wave 0 Requirements

All Wave-0 invite integration test files below now exist (authored by the `tdd="true"` task that owns each) and run green in both the quick invite subset and the full suite:

- [x] `src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt` — INV-04/INV-05 V23 schema shape + constraints [16-01 Task 3]. Boots with `@TestPropertySource(["spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"])`. **Runs against a dedicated, freshly (re)created database on the shared Testcontainers Postgres** (not `catspell`): all other contexts use `ddl-auto=create-drop` on the shared `public` schema, so pointing Flyway at that same DB was order-dependent (fails with "non-empty schema but no schema history table") and a Flyway clean would wipe the shared schema out from under the other cached contexts. Isolating onto a private DB lets Flyway migrate from empty (V3 installs PostGIS there) with zero cross-context interference. *(Fixed during validation — see audit below.)*
- [x] `src/test/kotlin/com/catspell/api/invite/InviteSingleUseIntegrationTest.kt` — INV-04 hashed-at-rest + atomic single-use (concurrent double-consume) [16-02 Task 3]
- [x] `src/test/kotlin/com/catspell/api/invite/ReferralAttributionIntegrationTest.kt` — INV-05 referral row iff referrer present [16-02 Task 3]
- [x] `src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt` — INV-02 issuance + shared-secret + deny-by-default + referrer validation (needs `app.invite.admin-token` test value) [16-03 Task 2]
- [x] `src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt` — INV-01/INV-03 gate ON (needs `app.invite.enabled=true`) [16-04 Task 3]
- [x] `src/test/kotlin/com/catspell/api/invite/InvitePublicModeIntegrationTest.kt` — INV-01 gate OFF (needs `app.invite.enabled=false`) [16-04 Task 3]
- [x] `src/test/kotlin/com/catspell/api/invite/InviteEnumerationSafetyIntegrationTest.kt` — INV-04 invalid==consumed==missing byte-identical 403 [16-04 Task 3]

Framework install: **none** — JUnit 5 + Spring Boot Test + MockMvc + Testcontainers are already configured in `build.gradle.kts`/`BaseIntegrationTest`.

Test config: per-test `app.invite.enabled` (both states) and `app.invite.admin-token` are set via `@TestPropertySource`; the phase adds `app.invite.*` defaults to `src/test/resources/application.yml` in 16-01.

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-10-01

---

## Validation Audit 2026-10-01

| Metric | Count |
|--------|-------|
| Gaps found | 1 |
| Resolved | 1 |
| Escalated | 0 |

**Finding (16-01-03 / 16-01-01 — PARTIAL → COVERED):** `InviteMigrationTest` was order-dependently red. The documented quick sampling command `./gradlew test --tests "com.catspell.api.invite.*"` failed 5/5 in that class with `FlywayException: Found non-empty schema(s) "public" but no schema history table`. Root cause: it was the only context flipping `spring.flyway.enabled=true` while sharing the one Testcontainers `catspell` DB with the `ddl-auto=create-drop` contexts; whichever booted first decided whether Flyway saw an empty schema. The full-suite verification (327 green) had merely gotten a lucky ordering.

**Resolution:** Isolated `InviteMigrationTest` onto a dedicated database (`invite_migration_test`), DROP/CREATE-d fresh on the same container via `@DynamicPropertySource`, so Flyway migrates from empty (V3 installs PostGIS) with no clean and no cross-context interference. An earlier attempt that ran Flyway `clean` on the shared DB was rejected because it broke 30 tests in other contexts (match/moderation/profile). No implementation files were modified — the fix is test-only.

**Post-fix results:** quick invite subset `26 tests, 0 failed`; full suite `./gradlew test → 327 passed, 0 failed, 1 skipped (pre-existing)`. `InviteMigrationTest` 5/5 green regardless of execution order.
