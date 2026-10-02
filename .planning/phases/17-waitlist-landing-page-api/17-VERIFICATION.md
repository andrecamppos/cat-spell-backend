---
phase: 17-waitlist-landing-page-api
verified: 2026-10-02T16:02:34Z
status: gaps_found
score: 51/52 must-haves verified (roadmap SCs 3/4; plan truths 48/48 after dedupe)
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
  - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
  - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
  - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt
  - src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt
  - src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt
  - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
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
  - src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt
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
covered_digest: "v2:sha256:34af511aca8d653a5275ae1f1d240a7907d2ec58b91d21b603623672d451b6c9"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 46/48
  gaps_closed:
    - "Prohibition (17-01, test-tier): MUST NOT persist anything about a submitter beyond email + normalized key (D-05) — now enforced by WaitlistMigrationTest 'waitlist_entries has exactly the ten expected columns and no others' (passes)"
    - "17-01 backstop truth: concurrent joins for one normalized email mint at most per-email-capacity confirm tokens — now proven by WaitlistPerEmailConcurrencyIntegrationTest (20 threads, exactly 3 distinct tokens, 1 row, stored hash among sent; passes)"
  gaps_remaining:
    - "ROADMAP SC3: The endpoint is protected by per-IP + per-email rate limiting with email normalization (per-IP half partially closed: the direct untrusted-peer forgery is fixed; two bypasses remain)"
  regressions: []
gaps:
  - truth: "ROADMAP SC3: The endpoint is protected by per-IP + per-email rate limiting with email normalization"
    status: partial
    reason: >-
      Per-email half holds and is now proven under concurrency. 17-07 fixed one per-IP bypass: an untrusted
      direct peer that forges X-Forwarded-For is now keyed on its own remoteAddr (proven by tests and by a direct
      probe). Two per-IP bypasses remain in RateLimitFilter, both shown by a direct probe of the compiled filter.
      (1) No proxy needed (CR-02): the filter matches on the raw, undecoded requestURI. So `POST /api/%77aitlist`
      skips the per-IP limit, and so do `/api/auth/%6Cogin` etc. Every later layer decodes and accepts it:
      StrictHttpFirewall accepts it, Spring Security permitAll for POST /api/waitlist matches it, and the MVC
      PathPattern /api/waitlist matches it. So the request reaches WaitlistController.join with no per-IP limit, in
      every deployment shape. (2) Behind a trusted proxy that appends to X-Forwarded-For (CR-01, carried forward
      from the prior gap's stated reason): resolveClientIp keys on the leftmost entry, which the client controls.
      The default trust set includes 127.0.0.1, so a same-host nginx with the standard `$proxy_add_x_forwarded_for`
      is exploitable with the default config. Probe: 5/5 requests from one real client with a rotating forged
      leftmost hop are allowed at capacity 2.
    artifacts:
      - path: "src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt"
        issue: "line 40: `val path = httpRequest.requestURI` (raw, undecoded) drives both the exact waitlist match (line 43) and the AUTH_PATHS prefix match (line 44)"
      - path: "src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt"
        issue: "line 80: `forwardedFor.split(\",\").first().trim()` takes the client-controlled leftmost hop when the peer is trusted"
    missing:
      - "Match on the decoded, normalized path the rest of the stack uses (servletPath + pathInfo, or UrlPathHelper.getPathWithinApplication) and add an integration test showing POST /api/%77aitlist and POST /api/auth/%6Cogin are throttled at capacity"
      - "Resolve the client from the rightmost X-Forwarded-For hop that is not a trusted proxy (RemoteIpValve semantics), or switch to server.forward-headers-strategy=native + server.tomcat.remoteip.internal-proxies and key on remoteAddr only; add a test where the trusted peer sends 'X-Forwarded-For: <forged-i>, <real>' with a rotating <forged-i> and assert the requests share one bucket"
      - "Recommended alongside (WR-09): compare addresses semantically (e.g. IpAddressMatcher, which also allows CIDR) so the '::1' default matches Tomcat's '0:0:0:0:0:0:0:1', and declare/document rate-limit.trusted-proxies in application.yml and docs/CONFIGURATION.md"
human_verification:
  - test: "From the deployed landing origin with WAITLIST_ALLOWED_ORIGINS set, run a browser fetch POST to /api/waitlist; repeat from a foreign origin"
    expected: "202 readable by the landing page; the foreign origin is blocked by CORS"
    why_human: "MockMvc proves the headers, not a real browser plus deployment (17-03-SUMMARY)"
  - test: "Open the confirmation and invite emails in a real mail client and follow the links"
    expected: "Links work; copy is accurate (see WR-03 'still confirmed' sentence and IN-01 hardcoded '7 days')"
    why_human: "Copy quality and rendering are not asserted by any test"
  - test: "Confirm how the production reverse proxy sets X-Forwarded-For (append vs overwrite) and from which address it connects"
    expected: "Known value for rate-limit.trusted-proxies; informs the CR-01 fix (or an override if the proxy provably overwrites)"
    why_human: "Deployment fact outside the repo"
---

# Phase 17: Waitlist / Landing-Page API Verification Report

**Phase Goal:** Capture demand via a public, enumeration-safe, double-opt-in waitlist that the operator can convert into invites — the backing API for the separate-repo landing page.
**Verified:** 2026-10-02T16:02:34Z
**Status:** gaps_found
**Re-verification:** Yes — after gap closure plans 17-07 and 17-08

Repository state: all phase-17 work is staged, not committed (HEAD 6ae4f42), as expected. Implementation was checked in the working tree and index. Test evidence comes from `build/test-results/test/`: the unfiltered suite reported 67 suites, 416 tests, 0 failures, 0 errors, and 1 skip (`FcmSmokeTest`). `find src -newer <oldest result XML>` returns nothing, so the results match the current tree. The only main-source change since the prior verification is `RateLimitFilter.kt` (17-07). The other changes since then are four test files (17-07/17-08).

## Goal Achievement

### Roadmap Success Criteria

| # | Success criterion | Status | Evidence |
|---|---|---|---|
| 1 | Public unauthenticated join; identical enumeration-safe response for new vs duplicate | ✓ VERIFIED | Regression check: `WaitlistController.join` always returns 202 + `WAITLIST_JOIN_MESSAGE`. `join` never throws for dupes or throttling (ON CONFLICT DO NOTHING, conditional rotate, silent bucket exit). Mail goes through `@Async` AFTER_COMMIT. permitAll + JWT skip are present. `WaitlistEnumerationSafetyIntegrationTest` 4/4 passes. |
| 2 | Double opt-in via hashed single-use time-limited token; only confirmed count | ✓ VERIFIED | Regression check: 32-byte SecureRandom token, SHA-256 hex stored only. `claimConfirm` is a single conditional UPDATE (`status=PENDING AND expiresAt > now`). `markInvited` requires CONFIRMED. Confirm 11/11 and Convert 16/16 pass. |
| 3 | Per-IP + per-email rate limiting with email normalization | ✗ FAILED (partial) | Per-email ✓: normalizer + shared bucket. PerEmailLimit 3/3 passes, and the new 20-thread concurrency proof passes. Per-IP: the 17-07 fix holds for a direct untrusted forger. But **two bypasses remain**, both reproduced against the compiled filter (see Behavioral Spot-Checks): percent-encoded path (CR-02, no proxy needed) and leftmost-XFF behind an appending trusted proxy (CR-01). |
| 4 | Operator converts a confirmed entry into an invite and emails the code/link | ✓ VERIFIED | Regression check: guard comes first, then `convertToInvite`: CONFIRMED→INVITED claim, `inviteService.create(null)`, synchronous send, 502 + rollback on failure. Convert 16/16, Admin 11/11 and DenyByDefault 2/2 pass. |

### Plan Must-Have Truths

| Plan | Truths | Status | Notes |
|---|---|---|---|
| 17-01 | 7 | 7 ✓ | The backstop concurrency truth is now ✓ VERIFIED by a passing wired test (17-08). Its address is unique to that class, so the bucket starts fresh (no coincidental reliance). |
| 17-02 | 9 | 9 ✓ | Unchanged code; Confirm suite 11/11 |
| 17-03 | 8 (1 deduped into SC1) | 8 ✓ | These truths hold as written: registration patterns, 429 + Retry-After, confirm never throttled, auth unchanged, CORS, JWT skip. The SC3 problem is outside their wording. |
| 17-04 | 7 | 7 ✓ | Unchanged; Admin + DenyByDefault suites green |
| 17-05 | 8 | 8 ✓ | Unchanged; Convert suites green |
| 17-06 | 5 | 5 ✓ | Unchanged; Migration + Join suites green |
| 17-07 | 3 | 3 ✓ | (1) The trust gate exists at `RateLimitFilter.kt:73-83` and is wired via `RateLimitFilterConfig` `trustedProxies`. (2) A forged XFF from an untrusted peer gets 429 at capacity on `/api/auth/login` and `/api/waitlist` (2/2 + 1 new test pass; probe A confirms). (3) A trusted 127.0.0.1 peer still honors XFF, and all pre-existing suites are green. **Caveat:** truth 1's clause "closing the 'failed' SC3 gap" does not hold. The mechanism it describes is real, but SC3 stays open for the reasons below. That failure is counted once, under SC3. |
| 17-08 | 2 (1 deduped into the 17-01 backstop) | 1 ✓ | The exact column-set test uses a literal 10-column set via `information_schema.columns` and `assertEquals` (passes, Migration 8/8) |

**Score:** 51/52 verified (4 SCs + 48 deduped plan truths; 1 FAILED: SC3; 0 behavior-unverified)

### Prohibitions (must-NOT)

| Plan | Prohibition | Tier | Status | Evidence |
|---|---|---|---|---|
| 17-01 | No IP / UA / referrer / profile data persisted (D-05) | test | ✓ enforced (was flagged) | `WaitlistMigrationTest` exact column-set test passes; V24 and the entity show the same 10 columns |
| 17-01 | No merging beyond trim + lowercase + `+suffix` strip (D-03) | test | ✓ enforced | Normalizer "preserves dots"; PerEmailLimit "dotted addresses stay two distinct entries" |
| 17-02 | Join can't be used to repeatedly mail a third party | test | ✓ enforced | The per-email bucket is service-level, so the CR-02 path bypass does not affect it. Covered by the PerEmailLimit "over-limit join is silent" test, the Confirm "no email on CONFIRMED/INVITED re-join" test, and the new concurrency cap test. WR-02 (3/h ≈ 72/day) remains a warning. |
| 17-04 | Admin list never exposes emails on a blank/missing/wrong token | test | ✓ enforced | Admin 401 cases + DenyByDefault suite |
| 17-05 | No invite/email for a non-CONFIRMED entry | test | ✓ enforced | Convert suite |
| 17-05 | No fabricated referral attribution | test | ✓ enforced | Convert suite `referrer_user_id IS NULL`, zero referrals |

### Advisory (New Scope, Unevidenced)

None. The two findings that block both have deterministic evidence (probe output below), and both are in a file 17-07 changed. CR-01 is also a carried-forward gap: the prior gap's reason said appending proxies keep the client-supplied first value. The other review findings are treated as warnings, not blockers.

### Required Artifacts

| Artifact | Status | Details |
|---|---|---|
| `RateLimitFilter.kt` | ⚠️ WIRED, still bypassable | `trustedProxies` exists and is wired (17-07 artifact check passes). Raw-URI match (l.40) and leftmost-hop selection (l.80) are the remaining defects. |
| `RateLimitTrustedProxyIntegrationTest.kt` | ✓ VERIFIED | Contains `203.0.113.50`; 2 tests pass; pins `remoteAddr` via RequestPostProcessor |
| `WaitlistRateLimitIntegrationTest.kt` | ✓ VERIFIED | New forged-XFF test passes (5/5). The test only covers single-entry XFF, so it never exercises a `<forged>, <real>` chain. |
| `WaitlistMigrationTest.kt` | ✓ VERIFIED | Contains `information_schema.columns` exact-set assertion |
| `WaitlistPerEmailConcurrencyIntegrationTest.kt` | ✓ VERIFIED | Contains `newFixedThreadPool(20)`. No capacity override. Uses `await().during(500ms)`, so a late 4th send would fail the test. |
| All other phase artifacts (V24, entity, repository, normalizer, service, controllers, listener, renderers, guard, SecurityConfig, exceptions) | ✓ VERIFIED | Unchanged since the prior pass; regression sanity re-read, suites green |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| RateLimitFilterConfig | RateLimitFilter | `rate-limit.trusted-proxies` → `Set<String>` → `RateLimitFilter(capacity, trustedProxies)` | ✓ WIRED |
| RateLimitFilterConfig | `/api/waitlist` | `addUrlPatterns("/api/auth/*", "/api/waitlist")` | ✓ WIRED (path match inside the filter is the defect) |
| WaitlistPerEmailConcurrencyIntegrationTest | WaitlistService `emailBuckets` | 20 direct `join` calls on one normalized address | ✓ WIRED |
| WaitlistController → WaitlistService → Repository → event → listener → renderer → EmailSender | — | as in the prior report | ✓ WIRED (unchanged) |
| Admin controllers → AdminTokenGuard; convert → InviteService | — | guard first; `create(null)` in REQUIRED tx | ✓ WIRED (unchanged) |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|---|---|---|---|---|
| Admin list response | `WaitlistEntryResponse` list | JPA derived query on `waitlist_entries` | Yes | ✓ FLOWING |
| Convert response `code` | raw invite code | `InviteService.create` (hash persisted) | Yes | ✓ FLOWING |
| Confirmation link | raw token | `generateRawToken()` → event → renderer; hash persisted | Yes | ✓ FLOWING |
| Per-IP bucket key | `clientIp` | `remoteAddr`, or leftmost XFF when the peer is trusted | Partly attacker-controlled | ⚠️ see SC3 |
| Outbound email | `EmailMessage` | `EmailSender` (only `LoggingEmailSender` exists project-wide) | Built, not network-sent | ℹ️ Pre-existing project seam |

### Behavioral Spot-Checks

The probes ran the **compiled** `RateLimitFilter` and the Spring path-matching components directly. They used the project's test runtime classpath, from a scratch program outside the repo (no server, no repo changes). Capacity 2, default trust set `{127.0.0.1, ::1}`.

| Behavior | Input | Result | Status |
|---|---|---|---|
| A. 17-07 fix: untrusted peer forging XFF | 3× POST `/api/waitlist`, remoteAddr 203.0.113.90, XFF 10.9.0.{1,2,3} | 200, 200, **429** | ✓ PASS |
| B. CR-01: trusted loopback proxy that appends | 5× POST `/api/waitlist`, remoteAddr 127.0.0.1, XFF `10.66.0.{i}, 198.51.100.7` (same real client) | 200 ×5, never throttled | ✗ FAIL |
| B'. Control: trusted proxy that overwrites | 3×, XFF `198.51.100.8` | 200, 200, 429 | ✓ (shows B is caused by the leftmost-hop choice) |
| C. CR-02: encoded path at the filter | 5× POST `/api/%77aitlist` and 5× POST `/api/auth/%6Cogin`, one untrusted peer each | 200 ×10, never throttled. Control: `/api/waitlist` from a fresh peer gives 200, 200, 429 | ✗ FAIL |
| C'. CR-02: does the encoded path reach the handler? | `/api/%77aitlist` through `StrictHttpFirewall`, `PathPatternRequestMatcher(POST, /api/waitlist)`, MVC `PathPattern /api/waitlist` | firewall=accepted, securityPermitAll=true, mvc=true, decodedPath=`/api/waitlist`. `/api/auth/%6Cogin` matches `/api/auth/login`. | ✗ confirms bypass reaches `WaitlistController.join` (Tomcat decoding unreserved `%77` is standard container behavior; not run against a live Tomcat) |
| D. WR-09: Tomcat-form IPv6 loopback | remoteAddr `0:0:0:0:0:0:0:1` + rotating XFF | 200, 200, 429 (treated as untrusted, XFF ignored) | ℹ️ Confirms the `::1` default never matches this form. Fails closed (shared bucket), not a bypass. |
| Waitlist + rate-limit suites on the current tree | read JUnit XML | RateLimit 10/10, TrustedProxy 2/2, WaitlistRateLimit 5/5, Migration 8/8, PerEmailConcurrency 1/1, PerEmailLimit 3/3, Confirm 11/11, Convert 16/16, Admin 11/11, Join 11/11, Enumeration 4/4, CORS 4/4 + 1/1, Normalizer 8/8 | ✓ PASS |
| Debt markers in staged sources | grep TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER | none | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED. No `scripts/*/tests/probe-*.sh` exist, and no plan declares a probe script. The "probe WAIT-0x" labels in the plans refer to integration tests.

### Requirements Coverage

| Requirement | Source plans | Description | Status | Evidence |
|---|---|---|---|---|
| WAIT-01 | 17-01, 17-03, 17-06 | Public join, enumeration-safe response (email + optional info) | ✓ SATISFIED | SC1. "Optional info" was deliberately excluded by D-05; this is a scope note. |
| WAIT-02 | 17-01, 17-02, 17-05, 17-06 | Double opt-in, hashed single-use time-limited token | ✓ SATISFIED | SC2 |
| WAIT-03 | 17-01, 17-03, 17-07, 17-08 | Per-IP + per-email rate limiting with normalization (optional disposable-domain filter) | ✗ BLOCKED (partial) | Per-email ✓; per-IP bypassable (SC3 gap). Disposable-domain filtering is optional and deferred per CONTEXT. REQUIREMENTS.md marks WAIT-03 `[x] Complete`, which is still premature. |
| WAIT-04 | 17-04, 17-05 | Operator converts a confirmed entry into an emailed invite | ✓ SATISFIED | SC4 |

No orphaned requirements. REQUIREMENTS.md maps exactly WAIT-01..04 to Phase 17, and every ID is claimed by at least one plan.

### Anti-Patterns / Review Findings Weighed

I checked each finding against the code myself. 17-REVIEW.md was not taken as fact.

| File | Finding | Severity | My conclusion |
|---|---|---|---|
| RateLimitFilter.kt:40-44 | CR-02 raw `requestURI` match | 🛑 Blocker | Confirmed by probes C/C'. Bypasses per-IP in any deployment. |
| RateLimitFilter.kt:80 | CR-01 leftmost XFF behind a trusted appending proxy | 🛑 Blocker | Confirmed by probe B. Exploitable with the default trust set behind a same-host appending nginx. |
| RateLimitFilter.kt:22,75,99 | WR-09 exact-string trust match; `::1` dead; no CIDR; key undocumented | ⚠️ Warning | Confirmed by probe D. Failure mode is a shared bucket (self-DoS), not a bypass. |
| WaitlistService / RateLimitFilter | WR-01 unbounded bucket maps | ⚠️ Warning | Code confirms no eviction. Made worse by CR-01/CR-02. |
| WaitlistService | WR-02 3/h ≈ 72/day per victim | ⚠️ Warning | Confirmed: `refillIntervally(perEmailCapacity, 1h)` |
| WaitlistConfirmEmailRenderer | WR-03 "still confirmed" copy vs. rotated links | ⚠️ Warning | Unchanged; UX risk for SC2's "only confirmed count" |
| WaitlistService / Repository | WR-04 PENDING re-join overwrites `email` | ⚠️ Warning | Confirmed `e.email = :email` in `rotatePendingToken` |
| WaitlistService.convertToInvite | WR-05 send before invite flush; WR-06 swallowed cause | ⚠️ Warning | Confirmed by reading lines 151-159 |
| RateLimitFilter / SecurityConfig | WR-07 shared auth/join bucket; 429s lack CORS headers | ⚠️ Warning | Filter runs at HIGHEST_PRECEDENCE, before CORS |
| SecurityConfig / AdminTokenGuard | WR-08 per-handler guard, no admin throttle, no min token length | ⚠️ Warning | Confirmed |
| various | IN-01..IN-07 | ℹ️ Info | Unchanged |

No unreferenced TBD/FIXME/XXX markers in any staged file.

### Human Verification Required

These do not change the status (gaps_found takes precedence). They are listed so they are not lost.

1. **CORS from the real landing page.** From the deployed origin, `fetch` POST to `/api/waitlist`. Expected: 202 readable; a foreign origin is blocked. Why human: needs a real browser and the real deployment.
2. **Email copy and rendering.** Open the confirmation and invite emails in a real mail client. Expected: links work and the copy is accurate (WR-03, IN-01). Why human: not asserted by any test.
3. **Production proxy XFF behavior.** Confirm append vs overwrite and the proxy's connecting address. Why human: deployment fact. It sets `RATE_LIMIT_TRUSTED_PROXIES` and the CR-01 fix shape.

### Gaps Summary

The gap-closure round closed two of the three prior items cleanly. The D-05 email-only prohibition is now test-enforced. The per-email mint cap is now proven under 20-way concurrency against the real capacity of 3. 17-07 also fixed what it targeted: a direct, untrusted caller can no longer forge its per-IP key.

**SC3 is still not met, for two reasons in the same file, `RateLimitFilter.kt`.** Both are reproduced against the compiled code.

1. **Encoded-path bypass (CR-02, any deployment).** The filter decides whether to throttle using the raw `requestURI`. Every other layer decodes the path. So `POST /api/%77aitlist` passes through without a per-IP limit, then passes the firewall and permitAll and reaches the join handler. The same trick removes the brute-force limit on `/api/auth/*`. This one cannot be fixed by deployment config. The fix is to match on `servletPath + pathInfo` (or `UrlPathHelper`), plus a negative test.
2. **Leftmost-hop bypass behind an appending proxy (CR-01, carried forward).** When the peer is trusted, the key is the first `X-Forwarded-For` entry, which the client wrote. Loopback is trusted by default, so a same-host nginx using the standard `$proxy_add_x_forwarded_for` is exploitable with the default config. The fix is to take the rightmost hop that is not a trusted proxy (or use Tomcat `RemoteIpValve` via `server.forward-headers-strategy=native`), plus a `<forged>, <real>` test.

The per-email bucket is still in force on both bypass paths, so a single victim address stays capped. What fails is the per-IP half of SC3: one source can still spray unlimited distinct addresses and grow both bucket maps (WR-01). WR-09 (IPv6 loopback form, no CIDR, undocumented key) should be fixed with the same change.

CR-02 is a code defect, not a deployment assumption, so an override is not appropriate for SC3 as a whole. Phase 17 is the last phase in the roadmap, so nothing is deferred.

---

_Verified: 2026-10-02T16:02:34Z_
_Verifier: Claude (gsd-verifier)_
