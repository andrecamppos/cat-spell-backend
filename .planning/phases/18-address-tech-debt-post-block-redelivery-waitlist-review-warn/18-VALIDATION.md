---
phase: "18"
slug: "address-tech-debt-post-block-redelivery-waitlist-review-warn"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-03"
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (spring-boot-starter-test), mockk 1.13.11, Spring MockMvc, Testcontainers 1.20.6 on Podman, Awaitility |
| **Config file** | `src/test/resources/application.yml` (`ddl-auto: create-drop`, Flyway disabled); `BaseIntegrationTest` truncates tables `@BeforeEach` |
| **Quick run command** | `./gradlew test --tests 'com.catspell.api.common.TrustedProxyMatcherTest'` |
| **Full suite command** | `./gradlew test` (run in the background; ~15 min / ~430 tests) |
| **Estimated runtime** | quick ~60 s; targeted area filters 2–5 min; full suite ~950 s |

---

## Sampling Rate

- **After every task (staged, not committed):** run the targeted `--tests` filter for the touched area (see the map below)
- **After every plan wave:** `./gradlew test --tests 'com.catspell.api.waitlist.*' --tests 'com.catspell.api.common.*' --tests 'com.catspell.api.invite.*' --tests 'com.catspell.api.moderation.*' --tests 'com.catspell.api.chat.*'`
- **Before `/gsd-verify-work`:** full `./gradlew test` must be green (run in the background)
- **Max feedback latency:** ~300 seconds for a targeted filter

---

## Per-Task Verification Map

Seeded from RESEARCH.md `## Validation Architecture`. Task IDs are `<plan>-T<task>`. "Created by task" means the task writes the test file itself, test-first, so no separate Wave 0 plan is needed.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 18-01-T1 | 18-01 | 1 | MOD-02/MOD-03 (W1) | T-18-01 | A blocked pair gets no reconnect push; `delivered = true`; visible conversations are still pushed | integration (broker capture) | `./gradlew test --tests "com.catspell.api.moderation.BlockEnforcementIntegrationTest"` | ✅ file / tests added by task | ⬜ pending |
| 18-01-T2 | 18-01 | 1 | MOD-02/MOD-03 (W1) | T-18-01 | Unmatch, rematch, recipient-block and block-row-only cases stay suppressed | integration | `./gradlew test --tests "com.catspell.api.moderation.BlockEnforcementIntegrationTest" --tests "com.catspell.api.chat.*"` | ✅ | ⬜ pending |
| 18-02-T1 | 18-02 | 1 | WAIT-03, MOD-06 (WR-10) | T-18-04, T-18-06, T-18-SC | Bounded, access-expiring bucket store; per-reporter 429 intact | unit + integration | `./gradlew test --tests "com.catspell.api.common.RateLimitBucketsTest" --tests "com.catspell.api.moderation.ReportServiceIntegrationTest"` | created by task | ⬜ pending |
| 18-02-T2 | 18-02 | 1 | WAIT-03 (WR-10) | T-18-04 | Auth per-email stores bounded; suites unchanged | integration | `./gradlew test --tests "com.catspell.api.auth.PasswordResetIntegrationTest" --tests "com.catspell.api.auth.EmailVerificationIntegrationTest" --tests "com.catspell.api.auth.AccountCredentialsIntegrationTest" --tests "com.catspell.api.common.RateLimitBucketsTest"` | ✅ | ⬜ pending |
| 18-03-T1 | 18-03 | 1 | WAIT-03 (current WR-02) | T-18-07, T-18-08 | `ip:port` / `[v6]:port` hops share one bucket; unparseable hop → peer bucket | integration | `./gradlew test --tests "com.catspell.api.common.RateLimitBypassIntegrationTest" --tests "com.catspell.api.common.RateLimitTrustedProxyIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest" --tests "com.catspell.api.common.RateLimitIntegrationTest" --tests "com.catspell.api.common.TrustedProxyMatcherTest"` | ✅ (update) | ⬜ pending |
| 18-03-T2 | 18-03 | 1 | WAIT-03 (current WR-01, IN-02) | T-18-09 | Non-octet CIDR edges; canonicalization table; shape check private | unit | `./gradlew test --tests "com.catspell.api.common.TrustedProxyMatcherTest"` | ✅ (extend) | ⬜ pending |
| 18-03-T3 | 18-03 | 1 | WAIT-03 (current IN-05) | T-18-10 | One-shot WARN for untrusted X-Forwarded-For | unit (ListAppender) | `./gradlew test --tests "com.catspell.api.common.RateLimitFilterWarnTest" --tests "com.catspell.api.common.RateLimitIntegrationTest"` | created by task | ⬜ pending |
| 18-04-T1 | 18-04 | 1 | WAIT-02 (WR-03 copy, IN-08, IN-12) | T-18-11, T-18-12 | Truthful copy, TTL rendered, UriComponentsBuilder link | unit + integration | `./gradlew test --tests "com.catspell.api.email.WaitlistConfirmEmailRendererTest" --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest"` | created by task | ⬜ pending |
| 18-04-T2 | 18-04 | 1 | WAIT-02, WAIT-04 (IN-07, IN-12) | T-18-12, T-18-13 | Invite link safe; malformed redirect URL fails at construction | unit + integration | `./gradlew test --tests "com.catspell.api.email.*" --tests "com.catspell.api.waitlist.WaitlistControllerUrlTest" --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"` | created by task | ⬜ pending |
| 18-05-T1 | 18-05 | 1 | INV-02, WAIT-04 (WR-08 length) | T-18-14, T-18-15 | Short non-blank admin token fails startup; message has no token | unit (ApplicationContextRunner) + integration | `./gradlew test --tests "com.catspell.api.common.AdminTokenGuardStartupTest" --tests "com.catspell.api.waitlist.WaitlistAdminIntegrationTest"` | created by task | ⬜ pending |
| 18-05-T2 | 18-05 | 1 | INV-02, WAIT-04 | T-18-14 | All admin-token contexts start on TEST_ADMIN_TOKEN | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.waitlist.WaitlistAdminIntegrationTest"` | ✅ (update) | ⬜ pending |
| 18-06-T1 | 18-06 | 2 | WAIT-01/02/03/04 (WR-03, WR-04, WR-11, IN-11) | T-18-17, T-18-18 | Cooldown no-op re-join; first-stored email pinned | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest"` | ✅ (update) | ⬜ pending |
| 18-06-T2 | 18-06 | 2 | WAIT-03 (WR-11) | T-18-19 | Exactly one email under a 20-thread race; bucket still caps after the cooldown | integration | `./gradlew test --tests "com.catspell.api.waitlist.*"` | ✅ (update) | ⬜ pending |
| 18-07-T1 | 18-07 | 2 | WAIT-01/03/04 (WR-07a, WR-08 throttle, WR-10) | T-18-21, T-18-22 | Separate join / auth / admin buckets; admin throttled before the token check | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest" --tests "com.catspell.api.common.RateLimit*" --tests "com.catspell.api.waitlist.WaitlistAdminIntegrationTest"` | ✅ (extend) | ⬜ pending |
| 18-07-T2 | 18-07 | 2 | WAIT-03 (current IN-01) | T-18-23 | Dot-segment / `//` / `;` spellings throttled by the filter itself | unit + integration | `./gradlew test --tests "com.catspell.api.common.RequestPathsTest" --tests "com.catspell.api.common.RateLimit*" --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistCorsIntegrationTest"` | created by task | ⬜ pending |
| 18-08-T1 | 18-08 | 3 | WAIT-04 (WR-05, WR-06) | T-18-26, T-18-27, T-18-30 | Hung send → 502 + rollback within the timeout; retry succeeds | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"` | ✅ (extend) | ⬜ pending |
| 18-08-T2 | 18-08 | 3 | WAIT-04 (WR-05, WR-06) | T-18-25, T-18-28 | Flush before send; cause chained; renderer bug passes through; no address in logs | unit (mockk) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistServiceConvertTest" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"` | created by task | ⬜ pending |
| 18-09-T1 | 18-09 | 3 | WAIT-01/03 (WR-07c) | T-18-31, T-18-32 | Join 429 readable by allowed origins only | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistCors429IntegrationTest" --tests "com.catspell.api.waitlist.WaitlistCorsIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest"` | created by task | ⬜ pending |
| 18-09-T2 | 18-09 | 3 | WAIT-01 (WR-07c) | T-18-32, T-18-33 | Policy join-only, explicit-origin, credential-free; auth 429 CORS-free | unit + integration | `./gradlew test --tests "com.catspell.api.common.WaitlistCorsPolicyTest" --tests "com.catspell.api.waitlist.WaitlistCors429IntegrationTest" --tests "com.catspell.api.waitlist.WaitlistCorsIntegrationTest"` | created by task | ⬜ pending |
| 18-10-T1 | 18-10 | 4 | INV-02, WAIT-04 (WR-08 central, AUD-01, IN-09) | T-18-34, T-18-35, T-18-36 | Stale Bearer + correct token → 200; unmapped admin path → 401; `limit=abc` → 401 | integration | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistAdminIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistEnumerationSafetyIntegrationTest"` | ✅ (extend) | ⬜ pending |
| 18-10-T2 | 18-10 | 4 | WAIT-01 (IN-06) | T-18-37, T-18-38 | Exact JWT skip list in the Tomcat shape; auth/invite/waitlist suites green | integration | `./gradlew test --tests "com.catspell.api.waitlist.*" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.auth.*"` | ✅ (extend) | ⬜ pending |
| 18-11-T1 | 18-11 | 5 | all (current IN-04, D-13/14/15 docs) | T-18-39 | Every key declared and documented | config/docs gate | `ruby -ryaml …` + docs env-row loop (see 18-11-PLAN) | ✅ | ⬜ pending |
| 18-11-T2 | 18-11 | 5 | all (D-01, D-02) | T-18-40 | 26 disposition rows, 0 open; full suite green | bookkeeping + full suite | disposition grep gate + `./gradlew test` (background) | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Each Wave 0 file is created test-first by the task that needs it (no separate Wave 0 plan):

- [ ] `src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt` — shared bucket helper semantics (fake `Ticker`) — 18-02-T1
- [ ] `src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt` — `ApplicationContextRunner` length check — 18-05-T1
- [ ] `src/test/kotlin/com/catspell/api/common/RateLimitFilterWarnTest.kt` — 18-03-T3; `src/test/kotlin/com/catspell/api/common/RequestPathsTest.kt` — 18-07-T2
- [ ] `src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt` — 18-04-T1 (plus `WaitlistInviteEmailRendererTest.kt`, `waitlist/WaitlistControllerUrlTest.kt` — 18-04-T2)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt` and `src/test/kotlin/com/catspell/api/invite/InviteServiceTest.kt` — 18-08-T2
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt` — 18-09-T1 (plus `common/WaitlistCorsPolicyTest.kt` — 18-09-T2)
- [ ] Shared `TEST_ADMIN_TOKEN` (≥ 32 chars) test constant in `src/test/kotlin/com/catspell/api/TestAdminToken.kt` — 18-05-T1

No framework install is needed.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Production reverse-proxy shape (`RATE_LIMIT_TRUSTED_PROXIES`, `WAITLIST_ALLOWED_ORIGINS`) | WAIT-03 | Deploy-time configuration (deferred) | Checked at deploy; out of scope for this phase |

*All in-scope phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
