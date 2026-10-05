---
phase: "17"
slug: "waitlist-landing-page-api"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
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

Filled from the 17-01..17-09 PLAN.md files (17-07/17-08/17-09 are gap-closure plans added after the first draft). Each test file is created by the task that first lists it (no separate Wave 0 plan; every task's `<automated>` command runs a test it creates or a regression that already exists).

| Task | Req ID | Behavior | Test Type | Automated Command | File Exists | Status |
|------|--------|----------|-----------|-------------------|-------------|--------|
| 17-01-T1 | WAIT-01, WAIT-02 | Unauthenticated POST → constant 202; one PENDING row with 64-hex hash; V24 validates against the entity | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest" --tests "com.catspell.api.invite.InviteMigrationTest"` | ✅ | ✅ green |
| 17-01-T2 | WAIT-03 | Normalization rules; +suffix/case share one row + bucket; capacity boundary silent 202 | unit + integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistEmailNormalizerTest" --tests "com.catspell.api.waitlist.WaitlistPerEmailLimitIntegrationTest"` | ✅ | ✅ green |
| 17-02-T1 | WAIT-02 | One confirm email after commit with hash-matching token; new token on PENDING re-join; none for CONFIRMED/INVITED | integration (async, Awaitility) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest"` | ✅ | ✅ green |
| 17-02-T2 | WAIT-02 | 302 success/error; reuse/unknown/blank/missing/rotated/expired → error; strict expiry; concurrent single winner | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest"` | ✅ | ✅ green |
| 17-03-T1 | WAIT-03 | Registered filter URL patterns; 202/202/429 problem+json; Retry-After ≥ 1; confirm never throttled | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest" --tests "com.catspell.api.common.RateLimitIntegrationTest"` | ✅ | ✅ green |
| 17-03-T2 | WAIT-03 | CORS preflight/actual for the allowed origin only; blank config emits none | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistCors*"` | ✅ | ✅ green |
| 17-03-T3 | WAIT-01 | Stale Bearer skipped on /api/waitlist (with /api/profile 401 control); six-state identical responses | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistEnumerationSafetyIntegrationTest" --tests "com.catspell.api.auth.AuthIntegrationTest"` | ✅ | ✅ green |
| 17-04-T1 | WAIT-04 | Invite admin behavior unchanged after `AdminTokenGuard` extraction | integration (regression) | `./gradlew test --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest*"` | ✅ | ✅ green |
| 17-04-T2 | WAIT-04 | Confirmed-only list ordered by confirmed_at; status/limit validation; 401 before params; deny-by-default | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistAdmin*" --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest*"` | ✅ | ✅ green |
| 17-05-T1 | WAIT-04, WAIT-02 | Convert CONFIRMED → code + organic invite + INVITED + email; PENDING/INVITED 409; unknown 404; rollback on ERROR/throw; 4-thread single winner | integration (service level) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"` | ✅ | ✅ green |
| 17-05-T2 | WAIT-04 | HTTP 201/409/404/502/401 ProblemDetails; guard before lookup; deny-by-default | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConvert*" --tests "com.catspell.api.waitlist.WaitlistAdmin*"` | ✅ | ✅ green |
| 17-06-T1 | WAIT-01, WAIT-02 | V24 on a private DB: nullability, three named constraints, duplicate-key + CHECK violations, PENDING default, multiple NULL hashes | integration (Flyway, private DB) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistMigrationTest" --tests "com.catspell.api.invite.InviteMigrationTest"` | ✅ | ✅ green |
| 17-06-T2 | WAIT-01, WAIT-02 | 400 validation with zero rows; PENDING/CONFIRMED/INVITED re-join; 8-thread single row; 168h TTL bracket | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest"` | ✅ | ✅ green |
| 17-07-T1 | WAIT-03 | Untrusted remoteAddr: forged X-Forwarded-For never gets a fresh bucket; trusted peer still honors XFF | integration | `./gradlew test --tests "com.catspell.api.common.RateLimitTrustedProxyIntegrationTest"` | ✅ | ✅ green |
| 17-07-T2 | WAIT-03 | Same forgery proof on POST /api/waitlist + regression sweep (waitlist/common/invite/auth/push) | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest"` | ✅ | ✅ green |
| 17-08-T1 | WAIT-03 (D-05) | waitlist_entries has exactly the ten email-only columns | integration (Flyway, private DB) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistMigrationTest"` | ✅ | ✅ green |
| 17-08-T2 | WAIT-03 | 20 concurrent joins for one new email mint exactly per-email-capacity (3) tokens; one row; stored hash among sent | integration (concurrency) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistPerEmailConcurrencyIntegrationTest"` | ✅ | ✅ green |
| SC3 / CR-02 | WAIT-03 | Percent-encoded `/api/%77aitlist` and `/api/auth/%6Cogin` throttled like canonical paths | integration | `./gradlew test --tests "com.catspell.api.common.RateLimitBypassIntegrationTest"` | ✅ | ✅ green (fixed by 17-09) |
| SC3 / CR-01 | WAIT-03 | Trusted peer with rotating leftmost XFF hop shares the real client's bucket | integration | `./gradlew test --tests "com.catspell.api.common.RateLimitBypassIntegrationTest"` | ✅ | ✅ green (fixed by 17-09) |
| 17-09-T1 | WAIT-03 | Decoded-path match: encoded join/login spellings share the canonical per-IP bucket; trusted peer keys on the rightmost untrusted XFF hop | integration | `./gradlew test --tests "com.catspell.api.common.RateLimitBypassIntegrationTest" --tests "com.catspell.api.common.RateLimitIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest"` | ✅ | ✅ green |
| 17-09-T2 | WAIT-03 | `TrustedProxyMatcher`: loopback forms, CIDR edges, family guard, non-literal rejection, fail-fast config; inner-proxy chain, second XFF line, malformed hop | unit + integration | `./gradlew test --tests "com.catspell.api.common.TrustedProxyMatcherTest" --tests "com.catspell.api.common.RateLimitBypassIntegrationTest" --tests "com.catspell.api.common.RateLimitTrustedProxyIntegrationTest"` | ✅ | ✅ green |
| 17-09-T3 | WAIT-03 | `rate-limit.trusted-proxies` declared and documented; 17-07 regression slice green | config + docs + integration (regression) | YAML check (`ok`), docs grep (`docs-ok`), `./gradlew test --tests "com.catspell.api.waitlist.*" --tests "com.catspell.api.common.*" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.auth.*" --tests "com.catspell.api.push.*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistEmailNormalizerTest.kt` — WAIT-03 normalization
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt` — V24 schema (own DB `waitlist_migration_test`) (17-06)
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt` — WAIT-01 (created 17-01, extended 17-06)
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt` — WAIT-01
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt` — WAIT-02
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt` — WAIT-03 per-email (17-01)
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt` — WAIT-03 per-IP, registered filter (17-03)
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistCorsIntegrationTest.kt` — WAIT-03 CORS (17-03)
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt` — WAIT-04 list (17-04)
- [x] `src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt` — WAIT-04 convert (17-05)
- [x] `src/test/resources/application.yml` — add `app.waitlist:` block

Framework install: none — existing infrastructure covers the stack.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Confirm link survives mail-scanner prefetch UX (error-page copy) | WAIT-02 | Depends on real mail providers / landing-page copy in a separate repo | Send a confirm email to a Gmail/Outlook inbox, observe whether the link is pre-consumed, and check the error page wording |
| Production proxy X-Forwarded-For behavior and connect address | WAIT-03 | Depends on the real deployment's reverse proxy; CR-01 itself is now automated (17-09) | Confirm the prod proxy appends or overwrites XFF, and set `RATE_LIMIT_TRUSTED_PROXIES` to its connect address/CIDR (see `docs/CONFIGURATION.md`) |
| Browser CORS from deployed landing origin vs foreign origin | WAIT-03 | MockMvc proves headers, not a real browser + deployment | With `WAITLIST_ALLOWED_ORIGINS` set, `fetch` POST /api/waitlist from the landing origin (readable 202) and from a foreign origin (blocked) |
| Confirm/invite email rendering and copy | WAIT-02, WAIT-04 | Copy quality not asserted (see WR-03, IN-01) | Open both emails in a real client; follow links; check copy |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (all 13 tasks across 17-01..17-06 carry an `<automated>` command with `<fails_when>`)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every task samples)
- [x] Wave 0 covers all MISSING references (no `MISSING` markers in any plan; every not-yet-existing test file a command targets is listed above with its creating plan)
- [x] No watch-mode flags (plain `./gradlew test --tests ...`; no `--continuous`)
- [x] Feedback latency < 180s (waitlist slice result timestamps span ~20 s of test time; Gradle startup + container reuse keeps the slice well under 180 s)
- [x] `nyquist_compliant: true` — restored by the second 2026-10-02 audit: 17-09 fixed CR-01/CR-02 and the three bypass tests run enabled and green

**Approval:** approved — 2026-10-02 (after 17-09 gap closure)

---

## Validation Audit 2026-10-02
| Metric | Count |
|--------|-------|
| Gaps found | 2 (+4 unmapped 17-07/17-08 tasks, already covered) |
| Resolved | 0 |
| Escalated | 2 (CR-01, CR-02 — impl bugs in `RateLimitFilter`; 3 red-confirmed tests added as `@Disabled` in `src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt`) |

Evidence: last full suite 416 tests / 0 failures / 1 skip (`FcmSmokeTest`); `./gradlew test` reports `:test UP-TO-DATE` against the current tree. New bypass class: 3 skipped, 0 failures; rate-limit quick slice green.

## Validation Audit 2026-10-02 (after 17-09)
| Metric | Count |
|--------|-------|
| Gaps found | 2 carried over (CR-01, CR-02) + 3 unmapped 17-09 tasks |
| Resolved | 2 (both SC3 rows green; 17-09 tasks mapped, already covered) |
| Escalated | 0 |

Evidence: full `./gradlew test` on the 17-09 staged tree: 430 tests, 0 failures, 0 errors, 1 skip (`FcmSmokeTest`), BUILD SUCCESSFUL in 15m 44s. `RateLimitBypassIntegrationTest` 8 tests / 0 skipped; `TrustedProxyMatcherTest` 6 / 0 skipped. Task 3 YAML check `ok`, docs check `docs-ok`.
