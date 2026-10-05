---
phase: 17-waitlist-landing-page-api
verified: 2026-10-02T21:20:00Z
status: passed
score: 59/59 must-haves verified (roadmap SCs 4/4; plan truths 55/55 = 48 carried + 7 from 17-09)
covered_files:
  - .planning/phases/17-waitlist-landing-page-api/17-01-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-01-SUMMARY.md
  - .planning/phases/17-waitlist-landing-page-api/17-02-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-02-SUMMARY.md
  - .planning/phases/17-waitlist-landing-page-api/17-03-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-03-SUMMARY.md
  - .planning/phases/17-waitlist-landing-page-api/17-04-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-04-SUMMARY.md
  - .planning/phases/17-waitlist-landing-page-api/17-05-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-05-SUMMARY.md
  - .planning/phases/17-waitlist-landing-page-api/17-06-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-06-SUMMARY.md
  - .planning/phases/17-waitlist-landing-page-api/17-07-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-07-SUMMARY.md
  - .planning/phases/17-waitlist-landing-page-api/17-08-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-08-SUMMARY.md
  - .planning/phases/17-waitlist-landing-page-api/17-09-PLAN.md
  - .planning/phases/17-waitlist-landing-page-api/17-09-SUMMARY.md
  - docs/CONFIGURATION.md
  - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
  - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
  - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt
  - src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt
  - src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt
  - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
  - src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt
  - src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt
  - src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt
  - src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt
  - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt
  - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt
  - src/main/kotlin/com/catspell/api/waitlist/event/WaitlistEmailListener.kt
  - src/main/kotlin/com/catspell/api/waitlist/event/WaitlistEvents.kt
  - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistDtos.kt
  - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntry.kt
  - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt
  - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistStatus.kt
  - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt
  - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V24__create_waitlist_entries.sql
  - src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistCorsIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistEmailNormalizerTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt
  - src/test/resources/application.yml

covered_digest: "v2:sha256:2aa9006bbe2b97d8045a68bf8a02801696c591ca2cd00f3b36d762d2174abe3d"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 51/52
  gaps_closed:
    - "ROADMAP SC3: The endpoint is protected by per-IP + per-email rate limiting with email normalization — CR-02 (raw requestURI) fixed by matching on UrlPathHelper.getPathWithinApplication; CR-01 (leftmost XFF hop) fixed by keying on the rightmost untrusted hop across all header lines; WR-09 fixed by TrustedProxyMatcher"
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "Review-disposition bookkeeping lost two prior warnings. The 17-09 re-review reused the IDs WR-01 and WR-02, so the original WR-01 (unbounded per-IP / per-email bucket maps, no eviction) and WR-02 (per-email 3/h, about 72 mails/day per victim) no longer appear in 17-REVIEW-DISPOSITION.md."
    category: other
    reason: "Both still hold in code: RateLimitFilter.buckets and WaitlistService emailBuckets have no eviction, and refillIntervally(perEmailCapacity, 1h) is unchanged. Re-add them under new IDs so they get an explicit disposition. This does not block SC3: neither is in the SC3 wording, and 17-09 removed the client-controlled key sources that made the first one exploitable."
    evidence_status: "code read; no deterministic failing test (none expected)"
coincidental_reliance_items:
  - truth: "17-09 truth 2: no tested non-canonical spelling (/api/waitlist;x=1, /api/waitlist/, /api//waitlist, /api/./waitlist) yields a third 202 from one peer"
    reason: undeclared-precondition
    harden: "For ;x=1 and //, the filter's own canonicalisation already shares the bucket. For /./ and /%2e/, the filter does not throttle (probe P7: filter passes them). They are stopped only because Spring Security's default StrictHttpFirewall rejects non-normalized paths with a 400; nothing in SecurityConfig pins that firewall. Either normalise dot segments in RateLimitFilter (17-REVIEW IN-01) or add a test that asserts the firewall bean is StrictHttpFirewall with defaults."
human_verification:
  - test: "From the deployed landing origin with WAITLIST_ALLOWED_ORIGINS set, run a browser fetch POST to /api/waitlist; repeat from a foreign origin"
    expected: "202 readable by the landing page; the foreign origin is blocked by CORS"
    why_human: "MockMvc proves the headers, not a real browser plus deployment (17-03-SUMMARY)"
  - test: "Open the confirmation and invite emails in a real mail client and follow the links"
    expected: "Links work; copy is accurate (see WR-03 'still confirmed' sentence and IN-01 hardcoded '7 days' from the first review)"
    why_human: "Copy quality and rendering are not asserted by any test"
  - test: "Confirm the production reverse proxy's connect address, whether it appends or overwrites X-Forwarded-For, and that it writes plain IP hops (no ip:port, no [v6]:port)"
    expected: "RATE_LIMIT_TRUSTED_PROXIES is set to the proxy's address or CIDR. Hops are bare IP literals, because a proxy that writes ip:port gives every connection its own bucket (17-REVIEW WR-02, probe P5)."
    why_human: "Deployment fact outside the repo. docs/CONFIGURATION.md documents ip:port as unsupported, but this shape fails open rather than closed."
  - test: "Decide on the flagged test-tier prohibition (17-09): 'MUST NOT throttle ... CORS preflight requests'"
    expected: "Accept the code guard plus probe evidence, or add an integration test: exhaust one IP's join bucket, then send OPTIONS /api/waitlist and expect no 429"
    why_human: "unverified-prohibition — human review recommended. The confirm-link half is test-enforced (WaitlistRateLimitIntegrationTest). The preflight half is enforced by the code guard `httpRequest.method == \"POST\"` and was observed in a scratch probe (P1: 3 OPTIONS after exhaustion all passed). No wired test covers it."
---

# Phase 17: Waitlist / Landing-Page API Verification Report

**Phase Goal:** Capture demand via a public, enumeration-safe, double-opt-in waitlist that the operator can convert into invites — the backing API for the separate-repo landing page.
**Verified:** 2026-10-02T21:20:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap-closure plan 17-09

## Repository state

- HEAD is `c6cf055`. The 17-09 changes are staged, and the working tree equals the index.
- Main-source changes since the prior verification: `RateLimitFilter.kt` (modified), `TrustedProxyMatcher.kt` (new) and `src/main/resources/application.yml` (one key added). No other `src/main` file changed.
- `build.gradle.kts` and `src/test/resources/application.yml` are unchanged against HEAD.

**Test evidence (run by me on this tree):**

- The orchestrator reported a full-suite result (430 tests, 0 failures), but it could not be confirmed from disk. A later targeted `TrustedProxyMatcherTest` run at 20:45Z had already overwritten `build/test-results/test/`, leaving only one XML file. No source file is newer than that run.
- I therefore ran the 17-07/17-09 regression slice myself, in two parts:
  - `com.catspell.api.common.*` + `com.catspell.api.waitlist.*`: BUILD SUCCESSFUL in 6m 47s. 20 classes, 125 tests, 0 failures, 0 errors, 0 skipped.
  - `com.catspell.api.auth.*` + `invite.*` + `push.*`: BUILD SUCCESSFUL in 7m 17s. 26 classes, 127 tests, 0 failures, 0 errors, 1 skipped (`FcmSmokeTest`, pre-existing and credential-gated).
  - Total: 46 classes and 252 tests, matching the 17-09-SUMMARY regression-slice figure.
- The first part's XML is copied to the session scratchpad. `build/test-results/test/` now holds the second part.

## Goal Achievement

### Roadmap Success Criteria

| # | Success criterion | Status | Evidence |
|---|---|---|---|
| 1 | Public unauthenticated join; identical enumeration-safe response for new vs duplicate | ✓ VERIFIED | Regression check. `WaitlistController` / `WaitlistService` are unchanged since the prior pass. EnumerationSafety 4/4 and Join 11/11 pass (my run). The new path canonicalisation only adds 429s on spellings that previously skipped the limit; the join response is unchanged. |
| 2 | Double opt-in via hashed single-use time-limited token; only confirmed count | ✓ VERIFIED | Regression check. The code is unchanged. Confirm 11/11, Convert 16/16 and Migration 8/8 pass (my run). |
| 3 | Per-IP + per-email rate limiting with email normalization | ✓ VERIFIED (was ✗ partial) | Both per-IP bypasses are closed, proven by tests and by direct probes of the compiled filter (see Behavioral Spot-Checks). **CR-02:** `RateLimitFilter.kt:47` now derives `path` from `UrlPathHelper.defaultInstance.getPathWithinApplication`; both the exact `POST`+`/api/waitlist` check (l.50) and the `AUTH_PATHS` prefix check use it. **CR-01:** `resolveClientIp` (l.84-94) reads `getHeaders("X-Forwarded-For")` across all lines and returns the rightmost hop that is not a trusted proxy, falling back to remoteAddr. Bypass 8/8 (0 skipped), TrustedProxy 2/2, RateLimit 10/10 and WaitlistRateLimit 5/5 pass. The per-email half is unchanged: PerEmailLimit 3/3, PerEmailConcurrency 1/1, Normalizer 8/8. |
| 4 | Operator converts a confirmed entry into an invite and emails the code/link | ✓ VERIFIED | Regression check. The code is unchanged. Convert 16/16, ConvertDenyByDefault 2/2, Admin 11/11, AdminDenyByDefault 2/2 and the invite package (26 tests) pass. |

### Plan Must-Have Truths

| Plan | Truths | Status | Notes |
|---|---|---|---|
| 17-01 … 17-08 | 48 (deduped as in the prior report) | 48 ✓ | Regression only: the code under them is unchanged except `RateLimitFilter`. Their suites pass in my run. 17-07's "trusted peer's forwarded IP honored as before" still holds for single-value XFF (WaitlistRateLimit "a different IP is still allowed" passes). 17-09 explicitly supersedes it for multi-hop chains. |
| 17-09 #1 | CR-02: decoded path; `%77aitlist` / `%6Cogin` get 429 on request 3; original assertions unchanged | ✓ VERIFIED | Both tests are enabled and pass. The blob-preservation check against `6a8882ab…` prints nothing. Probe P2 independently shows 200, 200, 429 for mixed encoded spellings on one peer. |
| 17-09 #2 | Spelling sweep: encoded spellings give exactly [202, 202, 429]; others give ≤ 2×202 | ✓ VERIFIED (coincidental-reliance) | The sweep test passes. For `/./` and `/%2e/`, the result relies on the default `StrictHttpFirewall` (probe P7). See coincidental_reliance_items. |
| 17-09 #3 | CR-01: rightmost untrusted hop across all lines; rotating leftmost, inner trusted hop, second header line | ✓ VERIFIED | The CR-01, inner-hop and second-line tests pass. Probes P3 / P3b / P3c show rotating forged hops, and injected `127.0.0.1, ::1` hops, all share the real client's bucket. |
| 17-09 #4 | WR-09: family-safe exact/CIDR over strict literals | ✓ VERIFIED | TrustedProxyMatcherTest passes 6/6, covering loopback forms, `/16` edges, the family guard, non-literals and `0.0.0.0/0`. The IPv6-loopback integration test passes. Probe P6: a `999.1.1.1` hop took 0 ms, so no DNS lookup. |
| 17-09 #5 | Malformed hop never 5xx; invalid config throws IAE | ✓ VERIFIED | The malformed-hop test passes. The unit test rejects `proxy.internal`, `/33`, `/abc`, `/8/8` and `::1/129`. Probe P8: `RateLimitFilter(2, {"proxy.internal"})` throws at construction, because the matcher is an eager `private val`. |
| 17-09 #6 | No regression; 17-07 slice green, only FcmSmokeTest skipped | ✓ VERIFIED | My two runs: 252 tests, 0 failures, 0 errors, 1 skip (FcmSmokeTest). |
| 17-09 #7 | Key declared in application.yml; docs cover CIDR, the rightmost rule, append/overwrite, the landing case, unsupported shapes | ✓ VERIFIED | `application.yml:38-39` has the quoted `${RATE_LIMIT_TRUSTED_PROXIES:127.0.0.1,::1}`. A Spring `StandardEnvironment` probe resolves it to `127.0.0.1,::1`. All required topics are present in the rewritten `### Rate Limiting` section of `docs/CONFIGURATION.md`. |

**Score:** 59/59 verified (4 SCs + 55 plan truths; 0 behavior-unverified; 1 coincidental-reliance advisory)

### Prohibitions (must-NOT)

| Plan | Prohibition | Tier | Status | Evidence |
|---|---|---|---|---|
| 17-01 | No IP / UA / referrer / profile data persisted (D-05) | test | ✓ enforced | Migration exact column-set test passes (8/8) |
| 17-01 | No merging beyond trim + lowercase + `+suffix` strip (D-03) | test | ✓ enforced | Normalizer 8/8, PerEmailLimit 3/3 |
| 17-02 | Join can't repeatedly mail a third party | test | ✓ enforced | PerEmailLimit, Confirm, PerEmailConcurrency pass. The 72/day ceiling is an advisory. |
| 17-04 | Admin list never exposes emails without a valid token | test | ✓ enforced | Admin + DenyByDefault suites |
| 17-05 | No invite/email for non-CONFIRMED; no fabricated referral | test | ✓ enforced | Convert suite |
| 17-09 | Never throttle confirm links or CORS preflights; keep the exact join match | test | ⚠️ partly flagged | The confirm half is test-enforced ("confirm links are never throttled…" passes). The exact match is kept (`path == "/api/waitlist"`, l.50). The **preflight half has no wired test**: it is enforced by the `method == "POST"` guard and observed by probe P1 only. Flagged per fail-closed rule; see human_verification item 4. |
| 17-09 | Never silently drop an unparseable trusted-proxies entry | test | ✓ enforced | Unit test `an invalid trusted-proxies entry fails construction`; probe P8 at filter level. Blanks are skipped by design and tested. |
| 17-09 | No new dependency / rate-limit store | test | ✓ enforced | `git diff HEAD -- build.gradle.kts` is empty. The only imports are `java.net.InetAddress` and spring-web `UrlPathHelper`. |

### Advisory (New Scope, Unevidenced)

| # | Finding | Category | Why Advisory |
|---|---|---|---|
| 1 | Prior WR-01 (unbounded bucket maps) and prior WR-02 (72 mails/day per victim) dropped from 17-REVIEW-DISPOSITION.md by ID reuse | other | Bookkeeping, not a code change. Both still hold in code, and neither is in an SC's wording. |

### Required Artifacts

| Artifact | Status | Details |
|---|---|---|
| `RateLimitFilter.kt` | ✓ VERIFIED | Contains `getPathWithinApplication` and `TrustedProxyMatcher(trustedProxies)`. Registration and both `@Value` keys/defaults are unchanged. Wired via `RateLimitFilterConfig` at `HIGHEST_PRECEDENCE`. |
| `TrustedProxyMatcher.kt` | ✓ VERIFIED | 100 lines, JDK-only. Byte/prefix `Range.contains` checks size equality before comparing. `parseLiteral` catches UHE and IAE. Used by the filter for both the peer and every hop. |
| `TrustedProxyMatcherTest.kt` | ✓ VERIFIED | Contains `10.88.0.0/16`; 6/6 pass |
| `RateLimitBypassIntegrationTest.kt` | ✓ VERIFIED | Contains `0:0:0:0:0:0:0:1`; there are no `@Disabled` annotations and no Disabled import. 8/8 pass, 0 skipped. |
| `src/main/resources/application.yml` | ✓ VERIFIED | Contains `RATE_LIMIT_TRUSTED_PROXIES`; placeholder resolves correctly |
| `docs/CONFIGURATION.md` | ✓ VERIFIED | Contains `RATE_LIMIT_TRUSTED_PROXIES`, `rightmost`, `CIDR`, `0:0:0:0:0:0:0:1`, `POST /api/waitlist` and `ip:port`. The Business Limits row has been renamed. |
| All other phase artifacts | ✓ VERIFIED | Unchanged since the prior pass; suites green |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| RateLimitFilter | TrustedProxyMatcher | `private val trustedProxyMatcher = TrustedProxyMatcher(trustedProxies)` (l.29); used at l.86 (peer) and l.93 (hops) | ✓ WIRED |
| RateLimitFilter.doFilter | UrlPathHelper | `UrlPathHelper.defaultInstance.getPathWithinApplication(httpRequest)` (l.47) feeds l.50 and l.51 | ✓ WIRED |
| application.yml | RateLimitFilterConfig | `rate-limit.trusted-proxies` → `@Value("${rate-limit.trusted-proxies:127.0.0.1,::1}")` → split/trim → `RateLimitFilter(capacity, trustedProxies)` | ✓ WIRED (resolution probed) |
| RateLimitFilterConfig | `/api/waitlist`, `/api/auth/*` | `addUrlPatterns`; container matches the decoded path, so encoded spellings still invoke the filter (proven through MockMvc's decorator, which uses the same resolver) | ✓ WIRED |
| Waitlist / invite / admin chains | — | unchanged from the prior report | ✓ WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|---|---|---|---|---|
| Per-IP bucket key | `clientIp` | `remoteAddr` for untrusted peers; the rightmost non-trusted XFF hop for trusted peers | Not client-chosen in supported deployments | ✓ FLOWING (was ⚠️) |
| Throttle decision path | `path` | decoded application path | Matches what the firewall, Security and MVC see | ✓ FLOWING |
| Admin list, convert `code`, confirmation link | — | unchanged | Yes | ✓ FLOWING |
| Outbound email | `EmailMessage` | `EmailSender` (only `LoggingEmailSender` exists) | Built, not network-sent | ℹ️ Pre-existing project seam |

### Behavioral Spot-Checks

I wrote a scratch Java probe in the session scratchpad. It ran the **compiled** `RateLimitFilter` / `TrustedProxyMatcher` from `build/classes` with `MockHttpServletRequest`, at capacity 2 with the default trust set. There was no server and no repo change. "200" means the filter passed the request down the chain.

| # | Behavior | Input | Result | Status |
|---|---|---|---|---|
| P1 | Join exhaustion, then preflight and confirm | 3× POST `/api/waitlist`, then 3× OPTIONS `/api/waitlist`, then GET `/api/waitlist/confirm` (same peer) | 200, 200, 429 / 200, 200, 200 / 200 | ✓ PASS |
| P2 | CR-02 encoded spellings share one bucket | `/api/%77aitlist`, `/%61pi/waitlist`, `/api/waitlis%74` on one peer; `/api/auth/%6Cogin`, `/api/auth/%6cogin`, `/api/%61uth/login` on another | 200, 200, 429 for both | ✓ PASS (was ✗) |
| P3 | CR-01 appending trusted proxy | 127.0.0.1, XFF `10.66.0.{i}, 198.51.100.7` ×4 | 200, 200, 429, 429 | ✓ PASS (was ✗ 200×5) |
| P3b | Same via Tomcat-form IPv6 loopback | `0:0:0:0:0:0:0:1`, rotating leftmost | 200, 200, 429 | ✓ PASS |
| P3c | Client injects trusted-looking hops | XFF `10.68.0.{i}, 127.0.0.1, ::1, 198.51.100.9` | 200, 200, 429 | ✓ PASS |
| P4 | 17-07 regression: untrusted forger | 203.0.113.200, rotating XFF | 200, 200, 429 | ✓ PASS |
| P5 | WR-02: trusted proxy writing `ip:port` | XFF `198.51.100.20:4000{i}` ×4 | 200 ×4 (never throttled) | ⚠️ Fails open, but only for a documented-unsupported proxy shape. A client cannot inject it: P5b, with a client-written `1.1.1.1:i` left of the real hop, gives 200, 200, 429. Routed to human item 3. |
| P6 | No DNS on IPv4-shaped junk | hop `999.1.1.1` | 0 ms | ✓ PASS |
| P7 | Non-canonical spellings vs firewall | `/api/./waitlist`, `/api/%2e/waitlist`, `/api/waitlist;x=1`, `/api//waitlist` through `StrictHttpFirewall` | all REJECTED; `/api/%77aitlist` accepted (and throttled per P2) | ✓ PASS. Dot segments pass the filter un-throttled but never reach a handler. SecurityConfig has no custom firewall (grep). |
| P8 | Fail-fast config at filter level | `RateLimitFilter(2, {"proxy.internal"})` | throws `IllegalArgumentException: Invalid rate-limit.trusted-proxies entry 'proxy.internal'…` | ✓ PASS |
| P9 | Non-octet CIDR branch (17-REVIEW WR-01, untested in repo) | `172.16.0.0/12` edges; `2001:db8:ab00::/41` | true, true, false, false / true, false | ✓ PASS (correct today; still no in-repo test) |
| — | Placeholder resolution | `StandardEnvironment` with the yml value | `[127.0.0.1,::1]` | ✓ PASS |
| — | Slice suites | two targeted gradle runs (above) | 252 tests, 0 fail, 1 skip | ✓ PASS |
| — | Assertion preservation vs blob `6a8882ab` | plan's grep loop | no output | ✓ PASS |
| — | Debt markers in the 6 changed files | grep TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER | none | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED. No `scripts/*/tests/probe-*.sh` exist, and no plan declares a probe script. The plans' "probe WAIT-0x" labels refer to integration tests, which ran above.

### Requirements Coverage

| Requirement | Source plans | Description | Status | Evidence |
|---|---|---|---|---|
| WAIT-01 | 17-01, 17-03, 17-06 | Public join, enumeration-safe (email + optional info) | ✓ SATISFIED | SC1. "Optional info" was excluded by D-05 (scope note). |
| WAIT-02 | 17-01, 17-02, 17-05, 17-06 | Double opt-in, hashed single-use time-limited token | ✓ SATISFIED | SC2 |
| WAIT-03 | 17-01, 17-03, 17-07, 17-08, 17-09 | Per-IP + per-email rate limiting with normalization (optional disposable-domain filter) | ✓ SATISFIED | SC3. Disposable-domain filtering is optional and deferred per CONTEXT. |
| WAIT-04 | 17-04, 17-05 | Operator converts a confirmed entry into an emailed invite | ✓ SATISFIED | SC4 |

No orphaned requirements: REQUIREMENTS.md maps exactly WAIT-01..04 to Phase 17, and every ID is claimed by a plan.

The staged REQUIREMENTS.md still shows WAIT-01, WAIT-02 and WAIT-04 as unchecked with traceability "Gaps Found". ROADMAP.md shows phase 17 "In Progress". These are bookkeeping updates for the orchestrator.

### Anti-Patterns / Review Findings Weighed

I checked each finding against the code and the probes. 17-REVIEW.md was not taken as fact.

| File | Finding | Severity | My conclusion |
|---|---|---|---|
| RateLimitFilter.kt | CR-01, CR-02, WR-09 (prior) | — | Resolved. Confirmed by tests and probes P2, P3, P3b, P3c and P8. |
| TrustedProxyMatcher.kt:32-35 | WR-01: non-octet CIDR mask branch untested | ⚠️ Warning | The code is correct (probe P9), but a trust-boundary branch has no regression test |
| RateLimitFilter.kt:93 | WR-02: chosen hop used verbatim; `ip:port` hops fail open | ⚠️ Warning | Confirmed by P5. It needs the trusted proxy itself to write ports; a client cannot inject it (P5b). It is documented as unsupported, so it is a deployment fact (human item 3), not an SC3 failure in supported configs. |
| RateLimitFilter.kt:47 | IN-01: dot segments not normalized by the filter | ℹ️ Info | Confirmed by P7. Today it is held by the default firewall; recorded as coincidental reliance. |
| TrustedProxyMatcher.kt | IN-02 (`isIpLiteral` name), IN-03 (mapped CIDR message; zone peers) | ℹ️ Info | Confirmed by reading; no safety impact (both fail closed) |
| docs/CONFIGURATION.md | IN-04: env-var table and Production row omit the new variable | ℹ️ Info | Confirmed. The Rate Limiting section itself is complete. |
| RateLimitFilter.kt | IN-05: silent misconfiguration (shared bucket) | ℹ️ Info | Confirmed; fails closed |
| RateLimitFilter / WaitlistService | prior WR-01 unbounded maps, prior WR-02 72/day | ⚠️ Warning | Still true; dropped from the disposition record (advisory 1) |
| various | prior WR-03..WR-08, IN-01..IN-07 (first review) | ⚠️ / ℹ️ | Unchanged files; still open in the disposition |

No unreferenced TBD/FIXME/XXX markers in any staged file.

### Human Verification Required

1. **CORS from the real landing page.** From the deployed origin, `fetch` POST to `/api/waitlist`, then repeat from a foreign origin. Expected: a readable 202 from the landing origin; the foreign origin is blocked. Why human: needs a real browser and the real deployment.
2. **Email copy and rendering.** Open the confirmation and invite emails in a real client and follow the links. Expected: links work and the copy is accurate (prior WR-03, IN-01). Why human: no test asserts this.
3. **Production proxy shape.** Confirm the proxy's connect address and whether it appends or overwrites X-Forwarded-For. Also confirm that it writes bare IP hops with no port. Expected: `RATE_LIMIT_TRUSTED_PROXIES` is set to that address or CIDR. Why human: deployment fact. After 17-09 it no longer decides whether a client can forge its key. It does decide whether the `ip:port` fail-open shape (P5) applies.
4. **Flagged prohibition: preflights never throttled.** Accept the `method == "POST"` guard plus probe P1, or add an integration test (exhaust one IP, then OPTIONS `/api/waitlist` expects no 429). Why human: a test-tier prohibition with no wired test is never auto-passed.

### Gaps Summary

The single remaining gap from the prior verification (SC3 per-IP half) is closed. Both bypasses were reproduced against the compiled filter before and are refuted after:

- **CR-02 (encoded path):** the throttle decision now uses the decoded application path, so encoded spellings of the join and login paths share the canonical bucket.
- **CR-01 (appending proxy):** a trusted peer's key is now the rightmost hop that is not a trusted proxy, so rotating forged leftmost hops gains nothing.
- **WR-09:** fixed alongside, with value-based, family-safe CIDR matching and fail-fast config.

The three previously red tests now run enabled, with their assertions unchanged. The 252-test regression slice is green on this tree.

All 4 roadmap success criteria and all 55 plan truths are verified. Status is `human_needed`, not `passed`, for two reasons. Three deployment and UX checks carry over. One 17-09 test-tier prohibition (preflights never throttled) has code and probe evidence but no wired test. The open warnings (WR-01 test gap, WR-02 `ip:port` shape, and the two prior warnings dropped from the disposition record) do not block the phase goal.

---

_Verified: 2026-10-02T21:20:00Z_
_Verifier: Claude (gsd-verifier)_
