---
phase: "17"
slug: "waitlist-landing-page-api"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-01"
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + MockMvc; MockK 1.13.11; Awaitility (transitive); Testcontainers (Podman) via `BaseIntegrationTest` |
| **Config file** | `build.gradle.kts`; `src/test/resources/application.yml` (Flyway off, `create-drop`, `rate-limit.capacity: 10000`) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.waitlist.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~120 seconds (waitlist slice); full suite longer |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew compileKotlin -q` + `./gradlew test --tests "com.catspell.api.waitlist.*"`
- **After every plan wave:** Run `./gradlew test --tests "com.catspell.api.waitlist.*" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.common.*"`, then `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

To be filled per task once PLAN.md files exist. The requirement → test mapping below is from 17-RESEARCH.md § Validation Architecture.

| Req ID | Behavior | Test Type | Automated Command | File Exists | Status |
|--------|----------|-----------|-------------------|-------------|--------|
| WAIT-01 | New / duplicate joins (PENDING, CONFIRMED, INVITED) return identical status + body; unauthenticated; stray invalid Bearer ignored | integration | `./gradlew test --tests "*WaitlistEnumerationSafetyIntegrationTest"` | ❌ W0 | ⬜ pending |
| WAIT-01 | Concurrent joins same new email → all 202, exactly 1 row; invalid email → 400 | integration | `./gradlew test --tests "*WaitlistJoinIntegrationTest"` | ❌ W0 | ⬜ pending |
| WAIT-02 | Only SHA-256 hash stored; confirm success/reuse/expired/unknown/blank/rotated; re-join rotation; concurrent confirm single winner | integration | `./gradlew test --tests "*WaitlistConfirmIntegrationTest"` | ❌ W0 | ⬜ pending |
| WAIT-03 | Email normalization rules | unit | `./gradlew test --tests "*WaitlistEmailNormalizerTest"` | ❌ W0 | ⬜ pending |
| WAIT-03 | Per-email + per-IP (registered filter) limits; confirm GET not throttled | integration | `./gradlew test --tests "*WaitlistRateLimitIntegrationTest"` | ❌ W0 | ⬜ pending |
| WAIT-03 | CORS allowed-origin preflight behavior | integration | `./gradlew test --tests "*WaitlistCorsIntegrationTest"` | ❌ W0 | ⬜ pending |
| WAIT-04 | Admin auth, list filter, convert 201/409/404, rollback on email failure | integration | `./gradlew test --tests "*WaitlistAdminIntegrationTest"` | ❌ W0 | ⬜ pending |
| (schema) | V24 applies under Flyway + `ddl-auto=validate` | integration | `./gradlew test --tests "*WaitlistMigrationTest"` | ❌ W0 | ⬜ pending |
| (regression) | Invite admin behavior after `AdminTokenGuard` extraction | integration | `./gradlew test --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest"` | ✅ | ⬜ pending |
| (regression) | Auth rate limits unchanged | integration | `./gradlew test --tests "com.catspell.api.common.RateLimitIntegrationTest"` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistEmailNormalizerTest.kt` — WAIT-03 normalization
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt` — V24 schema (own DB `waitlist_migration_test`)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt` — WAIT-01
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt` — WAIT-01
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt` — WAIT-02
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt` — WAIT-03
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistCorsIntegrationTest.kt` — WAIT-03 CORS
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt` — WAIT-04
- [ ] `src/test/resources/application.yml` — add `app.waitlist:` block

Framework install: none — existing infrastructure covers the stack.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Confirm link survives mail-scanner prefetch UX (error-page copy) | WAIT-02 | Depends on real mail providers / landing-page copy in a separate repo | Send a confirm email to a Gmail/Outlook inbox, observe whether the link is pre-consumed, and check the error page wording |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
