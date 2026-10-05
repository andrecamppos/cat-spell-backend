---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 07
subsystem: api
tags: [rate-limit, bucket4j, caffeine, path-normalization, admin, waitlist, security]

requires:
  - phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
    provides: "18-02 RateLimitBuckets (bounded Caffeine per-key store); 18-03 canonical-hop resolveClientIp, one-shot WARN, shared rate-limit test property array"
provides:
  - "RateLimitFilter with three bounded per-IP bucket families: AUTH (rate-limit.capacity), WAITLIST_JOIN (rate-limit.waitlist-capacity, default 10/min), ADMIN (rate-limit.admin-capacity, default 5/min, every request, before the token check)"
  - "Registered URL patterns exactly /api/auth/*, /api/waitlist, /api/admin/*"
  - "RequestPaths.normalized(request): servletPath + pathInfo, `;` content removed, dot and empty segments resolved; the one path source for the limiter (and for the JWT skip list in 18-10)"
  - "Config keys rate-limit.waitlist-capacity:10, rate-limit.admin-capacity:5, rate-limit.max-tracked-keys:100000 bound in RateLimitFilterConfig"
affects: [18-09 CORS headers on 429, 18-10 JWT skip list reuses RequestPaths, 18-11 application.yml declarations and operator docs for the new keys]

actuals:
  tokens: 4300
  tasks: 2
  commits: 0
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40

tech-stack:
  added: []
  patterns:
    - "Throttle decisions key on RequestPaths.normalized(request), never the raw requestURI or getPathWithinApplication"
    - "Each throttled route family has its own RateLimitBuckets store and its own capacity key; a private enum picks the family from method + normalized path"
    - "A path spelling that MockMvc's filter mapping cannot route (dot segments vs an exact pattern) is proven by driving the registered filter instance directly"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/common/security/RequestPaths.kt
    - src/test/kotlin/com/catspell/api/common/RequestPathsTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
    - src/test/resources/application.yml
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt

key-decisions:
  - "Phase 18-07 (D-14, WR-07a): POST /api/waitlist has its own per-IP bucket family (rate-limit.waitlist-capacity, default 10/min), separate from the /api/auth family (rate-limit.capacity)"
  - "Phase 18-07 (D-13, WR-08): /api/admin and /api/admin/** have a strict per-IP family (rate-limit.admin-capacity, default 5/min) that counts every request, any method, at HIGHEST_PRECEDENCE ahead of the X-Admin-Token check (Open Question 1 default: successes count too; operators honor Retry-After or raise RATE_LIMIT_ADMIN_CAPACITY)"
  - "Phase 18-07 (WR-10): all three families are RateLimitBuckets stores bounded by rate-limit.max-tracked-keys (default 100000); RateLimitFilter has no unbounded map"
  - "Phase 18-07 (D-16, IN-01): the limiter matches on RequestPaths.normalized (servletPath + pathInfo, semicolons stripped, dot/empty segments resolved), so it no longer relies on StrictHttpFirewall for /./, // or ;param spellings"

patterns-established:
  - "New per-IP throttle families: add a BucketFamily entry, a RateLimitBuckets store, an @Value capacity key with a default, and a registered URL pattern"
  - "Path-based security checks use RequestPaths.normalized"

requirements-completed: [WAIT-01, WAIT-03, WAIT-04]

coverage:
  - id: D1
    description: "Waitlist join and login draw from separate per-IP buckets: two logins then two joins are not 429, the third join and the third login are 429"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt#the waitlist join and login draw from separate per-IP buckets"
        status: pass
    human_judgment: false
  - id: D2
    description: "Operator routes are throttled per IP before the token check: [401, 401, 429] from one peer with a wrong token, a different peer still gets 401"
    requirement: WAIT-04
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt#operator routes are throttled per IP before the token check"
        status: pass
    human_judgment: false
  - id: D3
    description: "The registered filter patterns are exactly /api/auth/*, /api/waitlist and /api/admin/*"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt#the registered rate-limit filter covers the auth paths, the waitlist join and the operator routes"
        status: pass
    human_judgment: false
  - id: D4
    description: "RequestPaths.normalized resolves dot segments, empty segments and path parameters and keeps a trailing slash"
    requirement: WAIT-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/common/RequestPathsTest.kt (7 cases)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Dot-segment, double-slash and path-parameter spellings draw from the canonical bucket in the filter itself (/api/auth/./login, /api/./waitlist, /api//waitlist, /api/waitlist;x=1)"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a dot-segment login path is throttled by the filter like the canonical path"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a dot-segment join path is throttled by the filter itself like the canonical path"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#every spelling of the join path that reaches the join handler shares the canonical per-IP bucket"
        status: pass
    human_judgment: false
  - id: D6
    description: "No regression: RateLimitFilter() still throttles /api/auth/*, confirm links and CORS preflights are never throttled, admin, invite and waitlist suites pass"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "./gradlew test --tests \"com.catspell.api.common.*\" --tests \"com.catspell.api.waitlist.*\" (25 classes, 163 tests, 0 failures)"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests \"com.catspell.api.invite.*\" (8 classes, 26 tests, 0 failures)"
        status: pass
    human_judgment: false

duration: 30min
completed: 2026-10-04
status: complete
---

# Phase 18 Plan 07: Separate, bounded per-IP throttle families on the normalized path Summary

**RateLimitFilter now keeps three bounded Caffeine-backed per-IP bucket families: auth, the waitlist join (10/min) and the operator routes (5/min, before the token check). It selects the family on a new container-normalized path (`RequestPaths.normalized`), so `/./`, `//` and `;param` spellings are throttled by the filter itself.**

## Performance

- **Duration:** about 30 min
- **Started:** 2026-10-04T13:57:04Z
- **Completed:** 2026-10-04T14:27:30Z
- **Tasks:** 2 (Task 1 was a tracer)
- **Files modified:** 7 (2 created, 5 modified)

## Accomplishments
- **WR-07(a) / D-14:** the landing-page join has its own `WAITLIST_JOIN` family (`rate-limit.waitlist-capacity`, default 10/min). It can no longer spend a shared IP's login budget, and logins can't spend the join budget.
- **WR-08 / D-13:** `/api/admin` and `/api/admin/**` have an `ADMIN` family (`rate-limit.admin-capacity`, default 5/min) that counts every request. Because the filter runs at `HIGHEST_PRECEDENCE`, guesses at the operator secret are throttled before `AdminTokenGuard` sees them. `/api/admin/*` is registered alongside `/api/auth/*` and `/api/waitlist`.
- **WR-10:** all three families are `RateLimitBuckets` instances (maximumSize `rate-limit.max-tracked-keys`, default 100000, and expireAfterAccess one minute). No `ConcurrentHashMap` remains in RateLimitFilter.
- **IN-01 / D-16:** a new `internal object RequestPaths` with `fun normalized(request)` is the one path source. RateLimitFilter no longer calls `getPathWithinApplication`. 18-10 reuses it for the JWT skip list.
- 18-03's canonical, fail-safe `resolveClientIp` and the one-shot WARN are unchanged. `RateLimitFilter()` still compiles and throttles `/api/auth/*`, because every new parameter has a default.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
|------|--------------|
| 1 (tracer) | src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt, src/test/resources/application.yml, src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt, src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt, src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt |
| 2 | src/main/kotlin/com/catspell/api/common/security/RequestPaths.kt, src/test/kotlin/com/catspell/api/common/RequestPathsTest.kt, src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt, src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt |
| Plan metadata | .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-07-SUMMARY.md, .planning/STATE.md, .planning/ROADMAP.md, .planning/REQUIREMENTS.md (whichever changed) |

## TDD Evidence

**Task 1 (tracer). RED came before any production change.** I first set the shared property array in all three classes, then added the renamed registration test and the two behaviour tests.
- Command: `./gradlew test --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest"`
- Two discarded attempts were caused by the VS Code build writing into `build/`. One was a `compileTestKotlin` "Unable to delete directory". The other loaded no context because of a `FileNotFoundException` on the test class resource. Both are INVALID_RED environment failures.
- Valid RED: 7 tests, 3 failed, all on assertions. The 4 existing tests passed.
  - `the waitlist join and login draw from separate per-IP buckets`: "two spent logins must not use up the join budget ... expected: <[202, 202]> but was: <[429, 429]>". This is the shared bucket.
  - `operator routes are throttled per IP before the token check`: "expected: <[401, 401, 429]> but was: <[401, 401, 401]>"
  - `the registered rate-limit filter covers the auth paths, the waitlist join and the operator routes`: "operator routes must be registered to be throttled: [/api/auth/*, /api/waitlist]"
- `gsd-tools check tdd-red-evidence` (Surefire XML, target WaitlistRateLimitIntegrationTest) returned `RED_EVIDENCE_OK` / `target_test_failed`.
- GREEN: the Task 1 `<verify>` command gave 50 tests, 0 failures. That covers WaitlistRateLimit 7, RateLimitBypass 11, RateLimitTrustedProxy 2, RateLimit 10, RateLimitFilterWarn 3, RateLimitBuckets 6 and WaitlistAdmin 11.
- Tracer gate: the run is interactive, `human_verify_mode` is end-of-phase and the verify is automated only. I re-ran the verify. It reported `:test` UP-TO-DATE after a comment-only edit, so the bytecode was unchanged and the passing run still applied. Tracer verified end-to-end; expansion continued with no checkpoint.

**Task 2. RED came before the filter change.** I added `NORMALIZED_SPELLINGS`, the dot-segment login test and RequestPathsTest. `RequestPaths` was a compiling stub that only concatenated `servletPath + pathInfo` and was not wired into the filter, so the unit test went RED on assertions rather than on a compile error.
- Command: `./gradlew test --rerun --tests "com.catspell.api.common.RequestPathsTest" --tests "com.catspell.api.common.RateLimitBypassIntegrationTest"`. The first attempt hit the IDE `build/` race (a missing `.class` during MD5 hashing) and was discarded.
- Valid RED: 19 tests, 6 failed.
  - `a dot-segment login path is throttled by the filter like the canonical path`: "got [401, 400, 400] ==> expected: <429> but was: <400>". The third status is the firewall's 400, exactly as the plan predicted.
  - The `/api/./waitlist` spelling: "got [202, 400, 400] ==> expected: <429>"
  - RequestPathsTest failed on 4 of 7 cases, for example `pathInfo=/api/auth/./login ==> expected: </api/auth/login> but was: </api/auth/./login>`. The 3 cases with nothing to normalize passed.
- `check tdd-red-evidence` returned `RED_EVIDENCE_OK` for both RateLimitBypassIntegrationTest and RequestPathsTest.
- Mutation RED for the replacement dot-segment join test (see Deviations): I put the old path source (`UrlPathHelper.getPathWithinApplication`) back from a scratch copy and ran `./gradlew test --rerun --tests "com.catspell.api.common.RateLimitBypassIntegrationTest"`. Result: 13 tests, 2 failed. The join test got "[202, 200, 200] ==> expected: <429> but was: <200>" and the login test got "[401, 400, 400]". The file was then restored, and `cmp` confirmed it was byte-identical.
- GREEN: the Task 2 `<verify>` command gave 52 tests, 0 failures. That covers RequestPaths 7, RateLimitBypass 13, RateLimitTrustedProxy 2, RateLimit 10, RateLimitFilterWarn 3, RateLimitBuckets 6, WaitlistRateLimit 7 and WaitlistCors 4.

No REFACTOR step was needed.

## Verification
- Plan `<verification>`: `./gradlew test --tests "com.catspell.api.common.*" --tests "com.catspell.api.waitlist.*"` passed. It covered 25 classes and 163 tests with 0 failures and 0 errors.
- Extra regression check: the admin throttle also covers `/api/admin/invites`, so I ran `./gradlew test --tests "com.catspell.api.invite.*"`. It passed with 8 classes, 26 tests and 0 failures.
- Acceptance greps, all passing:
  - `ConcurrentHashMap` count 0
  - `RateLimitBuckets(` count 3
  - `"/api/admin/*"` count 1
  - `admin-capacity: 10000` and `waitlist-capacity: 10000` each count 1
  - identical `@TestPropertySource` lines: `sort -u | wc -l` is 1
  - `getPathWithinApplication` count 0 and `RequestPaths.normalized` count 1
  - RateLimitBypassIntegrationTest contains `NORMALIZED_SPELLINGS` and `/api/auth/./login`
  - RequestPathsTest has 7 cases, all passing

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/common/security/RequestPaths.kt`: container-normalized request path (new)
- `src/test/kotlin/com/catspell/api/common/RequestPathsTest.kt`: 7 parameterized normalization cases (new)
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`:
  - three `RateLimitBuckets` families and the `BucketFamily` enum
  - the family is chosen on `RequestPaths.normalized`
  - three new `@Value` keys
  - the `/api/admin/*` registration
  - KDoc naming the families
- `src/test/resources/application.yml`: `waitlist-capacity: 10000` and `admin-capacity: 10000`
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt`: the renamed registration test (asserting exactly 3 patterns), the separate-buckets test and the operator-throttle test
- `src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt`: `NORMALIZED_SPELLINGS`, the dot-segment login and join tests, and a direct-filter helper
- `src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt`: shared property array

## Decisions Made
- **Open Question 1 (recorded assumption):** the admin bucket counts every request, including successes and any method, as D-13 is written. Batch-converting many waitlist entries can therefore return 429. The assumption is that operators honor `Retry-After`. The capacity is configurable through `rate-limit.admin-capacity` / `RATE_LIMIT_ADMIN_CAPACITY`, which 18-11 declares and documents. Flag this at review if only failed attempts should count.
- The shared property array stays on one line, so the plan's `sort -u` identical-array check compares the real arrays and not just an opening `@TestPropertySource(` line.
- The family order is: exact POST `/api/waitlist`, then the `/api/admin` prefix, then the AUTH_PATHS prefixes. The three are disjoint, so the order only documents intent.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Test assumption] `/api/./waitlist` cannot reach the filter through MockMvc**
- **Found during:** Task 2 GREEN
- **Issue:** The plan put `/api/./waitlist` in `NORMALIZED_SPELLINGS` and expected a 429 through MockMvc. After the fix, the login spelling passed, but `/api/./waitlist` still gave `[202, 400, 400]`. The cause is in spring-test 7.0.7: `MockMvcFilterDecorator` line 157 matches FilterRegistrationBean URL patterns against `UrlPathHelper.getPathWithinApplication`, which does not resolve dot segments. So the exact `/api/waitlist` pattern never maps `/api/./waitlist` to the filter. `/api/auth/./login` does reach it, because `/api/auth/*` is a prefix pattern. Tomcat maps filters on the normalized URI, so production is correct.
- **Fix:**
  - `NORMALIZED_SPELLINGS` keeps `/api//waitlist` and `/api/waitlist;x=1`, which MockMvc does route to the filter.
  - `/api/./waitlist` returns to `OTHER_SPELLINGS` (peer .76), where it must never produce a third 202.
  - A new test, `a dot-segment join path is throttled by the filter itself like the canonical path` (peer 203.0.113.84, which no other test uses), sends one canonical MockMvc join. It then drives the registered filter instance directly twice with a MockMvc-shaped un-normalized request: POST, empty servletPath, `pathInfo=/api/./waitlist`. The third status must be 429. The mutation check above proves this test fails without the fix.
- **Files modified:** src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt
- **Verification:** the Task 2 verify and the plan verification both pass.

**2. [Rule 1 - Acceptance grep] comment matched the admin-pattern grep**
- **Found during:** Task 1
- **Issue:** My registration comment quoted `"/api/admin/*"`, so the grep printed 2 instead of 1.
- **Fix:** I reworded the comment. The change was comment-only and the bytecode was unchanged.

---

**Total deviations:** 2 auto-fixed (both Rule 1, test or comment level). **Impact:** the production code is as planned. The `/api/./waitlist` proof moved from MockMvc to a direct call on the registered filter, which is a stronger "filter itself" proof. No scope creep.

## Issues Encountered
- **VS Code build interference:** the VS Code Gradle/Java extension kept writing into the shared `build/` directory. That caused three spurious failures: "Unable to delete directory", a `FileNotFoundException` on a test class resource, and "Failed to create MD5 hash". It also caused one run that executed no tests. Re-running, using `--rerun` where needed, fixed all of them. No code was changed for these.
- The Testcontainers qemu readiness flake did not occur in this plan.

## Known Stubs

None. The Task 2 RED stub (`RequestPaths` doing concatenation only) was replaced by the GREEN implementation before staging.

## User Setup Required

None. The new keys have defaults. 18-11 declares `rate-limit.waitlist-capacity`, `rate-limit.admin-capacity` and `rate-limit.max-tracked-keys` in application.yml and documents them (`RATE_LIMIT_ADMIN_CAPACITY`).

## Next Phase Readiness
- 18-09 can add CORS headers to the 429 branch. The response body and headers are unchanged.
- 18-10 can reuse `RequestPaths.normalized` for the JWT skip list. It is `internal` in `com.catspell.api.common.security`.
- 18-11 must declare the three keys and document the admin-throttle trade-off (Open Question 1).

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-04*

## Self-Check: PASSED

- (a) All 7 source/test paths, this SUMMARY, .planning/STATE.md and .planning/ROADMAP.md appear in `git diff --cached --name-only`. REQUIREMENTS.md was not changed: `requirements.ready-ids` reported 0/3 ready, because sibling plans 18-08 to 18-11 still declare WAIT-01/03/04.
- (b) `git rev-parse HEAD` is `605ddb4d49266e805436c16b18bfc78746c46e40`. No commits were made.
- (c) `git diff --name-only` (unstaged changes) is empty.
- STATE.md archived milestone status lines (v1.0 "All phases complete", and v1.1 / v2.0 / v2.1 "✅ Milestone complete (shipped ...)") are unchanged.
