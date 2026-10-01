---
phase: "17"
slug: "waitlist-landing-page-api"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: true
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

Filled from the 17-01..17-06 PLAN.md files. Each test file is created by the task that first lists it (no separate Wave 0 plan; every task's `<automated>` command runs a test it creates or a regression that already exists).

| Task | Req ID | Behavior | Test Type | Automated Command | File Exists | Status |
|------|--------|----------|-----------|-------------------|-------------|--------|
| 17-01-T1 | WAIT-01, WAIT-02 | Unauthenticated POST → constant 202; one PENDING row with 64-hex hash; V24 validates against the entity | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest" --tests "com.catspell.api.invite.InviteMigrationTest"` | created by task | ⬜ pending |
| 17-01-T2 | WAIT-03 | Normalization rules; +suffix/case share one row + bucket; capacity boundary silent 202 | unit + integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistEmailNormalizerTest" --tests "com.catspell.api.waitlist.WaitlistPerEmailLimitIntegrationTest"` | created by task | ⬜ pending |
| 17-02-T1 | WAIT-02 | One confirm email after commit with hash-matching token; new token on PENDING re-join; none for CONFIRMED/INVITED | integration (async, Awaitility) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest"` | created by task | ⬜ pending |
| 17-02-T2 | WAIT-02 | 302 success/error; reuse/unknown/blank/missing/rotated/expired → error; strict expiry; concurrent single winner | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest"` | created by 17-02-T1 | ⬜ pending |
| 17-03-T1 | WAIT-03 | Registered filter URL patterns; 202/202/429 problem+json; Retry-After ≥ 1; confirm never throttled | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest" --tests "com.catspell.api.common.RateLimitIntegrationTest"` | created by task | ⬜ pending |
| 17-03-T2 | WAIT-03 | CORS preflight/actual for the allowed origin only; blank config emits none | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistCors*"` | created by task | ⬜ pending |
| 17-03-T3 | WAIT-01 | Stale Bearer skipped on /api/waitlist (with /api/profile 401 control); six-state identical responses | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistEnumerationSafetyIntegrationTest" --tests "com.catspell.api.auth.AuthIntegrationTest"` | created by task | ⬜ pending |
| 17-04-T1 | WAIT-04 | Invite admin behavior unchanged after `AdminTokenGuard` extraction | integration (regression) | `./gradlew test --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest*"` | ✅ | ⬜ pending |
| 17-04-T2 | WAIT-04 | Confirmed-only list ordered by confirmed_at; status/limit validation; 401 before params; deny-by-default | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistAdmin*" --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest*"` | created by task | ⬜ pending |
| 17-05-T1 | WAIT-04, WAIT-02 | Convert CONFIRMED → code + organic invite + INVITED + email; PENDING/INVITED 409; unknown 404; rollback on ERROR/throw; 4-thread single winner | integration (service level) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"` | created by task | ⬜ pending |
| 17-05-T2 | WAIT-04 | HTTP 201/409/404/502/401 ProblemDetails; guard before lookup; deny-by-default | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConvert*" --tests "com.catspell.api.waitlist.WaitlistAdmin*"` | created by 17-05-T1 | ⬜ pending |
| 17-06-T1 | WAIT-01, WAIT-02 | V24 on a private DB: nullability, three named constraints, duplicate-key + CHECK violations, PENDING default, multiple NULL hashes | integration (Flyway, private DB) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistMigrationTest" --tests "com.catspell.api.invite.InviteMigrationTest"` | created by task | ⬜ pending |
| 17-06-T2 | WAIT-01, WAIT-02 | 400 validation with zero rows; PENDING/CONFIRMED/INVITED re-join; 8-thread single row; 168h TTL bracket | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest"` | created by 17-01-T1 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistEmailNormalizerTest.kt` — WAIT-03 normalization
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt` — V24 schema (own DB `waitlist_migration_test`) (17-06)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt` — WAIT-01 (created 17-01, extended 17-06)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt` — WAIT-01
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt` — WAIT-02
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt` — WAIT-03 per-email (17-01)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt` — WAIT-03 per-IP, registered filter (17-03)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistCorsIntegrationTest.kt` — WAIT-03 CORS (17-03)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt` — WAIT-04 list (17-04)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt` — WAIT-04 convert (17-05)
- [ ] `src/test/resources/application.yml` — add `app.waitlist:` block

Framework install: none — existing infrastructure covers the stack.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Confirm link survives mail-scanner prefetch UX (error-page copy) | WAIT-02 | Depends on real mail providers / landing-page copy in a separate repo | Send a confirm email to a Gmail/Outlook inbox, observe whether the link is pre-consumed, and check the error page wording |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (all 13 tasks across 17-01..17-06 carry an `<automated>` command with `<fails_when>`)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every task samples)
- [x] Wave 0 covers all MISSING references (no `MISSING` markers in any plan; every not-yet-existing test file a command targets is listed above with its creating plan)
- [x] No watch-mode flags (plain `./gradlew test --tests ...`; no `--continuous`)
- [ ] Feedback latency < 180s (estimated ~120 s for the waitlist slice; not measured until the Wave 0 test files exist)
- [x] `nyquist_compliant: true` set in frontmatter (`wave_0_complete` stays `false` until execution creates the Wave 0 files)

**Approval:** pending
