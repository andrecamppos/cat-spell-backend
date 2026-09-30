---
phase: 16
slug: invite-only-access-referral
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-09-30
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
| 16-01-01 | 01 | 1 | INV-04, INV-05 | T-16-01 / T-16-03 | V23 stores only `code_hash` (raw code never has a column); single-use `consumed_at`; nullable `referrer_user_id`; no expiry/revoke columns; deny-by-default empty `admin-token` | integration | `test -f .../V23__create_invites_and_referrals.sql && grep ... && grep -A2 "invite:" application.yml \| grep admin-token` | ✅ | ⬜ pending |
| 16-01-02 | 01 | 1 | INV-04 | T-16-02 | `InviteRepository.markConsumed` is the atomic single-use claim (conditional UPDATE `WHERE consumed_at IS NULL`); entities validate against V23 | integration | `./gradlew compileKotlin -q` | ✅ | ⬜ pending |
| 16-01-03 | 01 | 1 | INV-04, INV-05 | T-16-01 / T-16-03 | V23 schema shape + `uq_referrals_invitee` UNIQUE + `chk_referrals_no_self` CHECK proven under real Flyway | integration | `./gradlew test --tests "com.catspell.api.invite.InviteMigrationTest"` | ❌ W0 | ⬜ pending |
| 16-02-01 | 02 | 2 | INV-04, INV-02 | T-16-06 | One `InviteRequiredException` → single 403 body + `code=INVITE_REQUIRED` for invalid/consumed/missing; `AdminAuthException` → generic 401, no code hint | integration | `./gradlew compileKotlin -q && grep -q "INVITE_REQUIRED" .../GlobalExceptionHandler.kt` | ✅ | ⬜ pending |
| 16-02-02 | 02 | 2 | INV-02, INV-04, INV-05 | T-16-05 / T-16-07 / T-16-08 / T-16-09 | SecureRandom+Base64url code; SHA-256 hash-at-rest; atomic `markConsumed`; referral row only for a real non-self referrer; validate treats null/blank/not-found/consumed identically | integration | `./gradlew compileKotlin -q` | ✅ | ⬜ pending |
| 16-02-03 | 02 | 2 | INV-04, INV-05 | T-16-07 / T-16-09 / T-16-08 | Code stored hashed (raw absent from DB); concurrent double-consume → exactly one winner; referral row iff referrer present | integration | `./gradlew test --tests "com.catspell.api.invite.InviteSingleUseIntegrationTest" --tests "com.catspell.api.invite.ReferralAttributionIntegrationTest"` | ❌ W0 | ⬜ pending |
| 16-03-01 | 03 | 3 | INV-02 | T-16-11 / T-16-12 / T-16-13 | `permitAll` route guarded by constant-time `MessageDigest.isEqual`; deny-by-default when `admin-token` blank; generic 401 | integration | `./gradlew compileKotlin -q && grep -q "/api/admin/invites" .../SecurityConfig.kt && grep -q "MessageDigest.isEqual" .../InviteAdminController.kt` | ✅ | ⬜ pending |
| 16-03-02 | 03 | 3 | INV-02 | T-16-11 / T-16-13 / T-16-14 | 201 + raw code once for a valid token; 401 for wrong/missing; deny-by-default when unset; 400 for unknown `referrerUserId` | integration | `./gradlew test --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest*"` | ❌ W0 | ⬜ pending |
| 16-04-01 | 04 | 3 | INV-01, INV-03 | T-16-16 | `RegisterRequest.inviteCode: String? = null` with NO `@field:NotBlank` (optional at DTO; requiredness enforced only when gated) | integration | `./gradlew compileKotlin -q && grep -q "inviteCode" .../AuthDtos.kt` | ✅ | ⬜ pending |
| 16-04-02 | 04 | 3 | INV-01, INV-03, INV-04 | T-16-16 / T-16-18 / T-16-19 | `@Transactional register` composes age gate → (if enabled) validate → save → consume; invite path never runs when gate off; failed consume rolls back the user insert | integration | `./gradlew compileKotlin -q && grep -q "inviteService.validate" .../AuthService.kt && grep -q "app.invite.enabled" .../AuthService.kt` | ✅ | ⬜ pending |
| 16-04-03 | 04 | 3 | INV-01, INV-03, INV-04 | T-16-16 / T-16-17 / T-16-18 | Gate OFF → 201 for any/absent code, nothing consumed; gate ON → valid 201 / missing 403; invalid==consumed==missing byte-identical 403; no orphan account on failed consume | integration | `./gradlew test --tests "com.catspell.api.invite.InviteGateIntegrationTest" --tests "com.catspell.api.invite.InvitePublicModeIntegrationTest" --tests "com.catspell.api.invite.InviteEnumerationSafetyIntegrationTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*File Exists: ✅ = created by a compile/grep-verified task · ❌ W0 = integration test file must be authored (Wave-0 dependency, see below)*

---

## Wave 0 Requirements

The following invite integration test files do not yet exist and are authored by the `tdd="true"` tasks that own them (each is the third task of its plan). They are the Wave-0 test dependencies for this phase:

- [ ] `src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt` — INV-04/INV-05 V23 schema shape + constraints [16-01 Task 3]. **Must set `@TestPropertySource(properties = ["spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"])`** because the test profile (`src/test/resources/application.yml`) disables Flyway and uses `ddl-auto=create-drop` — the migration test needs real Flyway to exercise V23.
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteSingleUseIntegrationTest.kt` — INV-04 hashed-at-rest + atomic single-use (concurrent double-consume) [16-02 Task 3]
- [ ] `src/test/kotlin/com/catspell/api/invite/ReferralAttributionIntegrationTest.kt` — INV-05 referral row iff referrer present [16-02 Task 3]
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt` — INV-02 issuance + shared-secret + deny-by-default + referrer validation (needs `app.invite.admin-token` test value) [16-03 Task 2]
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt` — INV-01/INV-03 gate ON (needs `app.invite.enabled=true`) [16-04 Task 3]
- [ ] `src/test/kotlin/com/catspell/api/invite/InvitePublicModeIntegrationTest.kt` — INV-01 gate OFF (needs `app.invite.enabled=false`) [16-04 Task 3]
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteEnumerationSafetyIntegrationTest.kt` — INV-04 invalid==consumed==missing byte-identical 403 [16-04 Task 3]

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

**Approval:** pending
