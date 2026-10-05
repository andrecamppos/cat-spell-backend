---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 09
subsystem: api
tags: [cors, rate-limit, waitlist, spring-security, security]

requires:
  - phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
    provides: "18-07 RateLimitFilter with the BucketFamily enum (AUTH, WAITLIST_JOIN, ADMIN), rate-limit.waitlist-capacity, RateLimitFilterConfig"
provides:
  - "WaitlistCorsPolicy: one @Component that turns app.waitlist.allowed-origins into the join's CorsConfiguration (null when blank). Spring Security and the rate limiter both read it"
  - "A WAITLIST_JOIN 429 always carries Vary: Origin. For an allowed origin it also carries Access-Control-Allow-Origin (the request's own spelling) and Access-Control-Expose-Headers: Retry-After, X-RateLimit-Remaining, X-RateLimit-Reset"
  - "RateLimitFilter constructor parameter corsConfiguration: CorsConfiguration? = null (last, defaulted)"
affects: [18-11 operator docs for the landing-page CORS and trusted-proxy setup]

actuals:
  tokens: 3600
  tasks: 2
  commits: 0
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40

tech-stack:
  added: []
  patterns:
    - "A response written before Spring Security's CorsFilter mirrors the shared CorsConfiguration through checkOrigin. It never uses a CORS processor, which would turn a disallowed origin into a 403"
    - "Configuration that two filter chains must agree on lives in one bean that both read, not in two @Value bindings"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/common/config/WaitlistCorsPolicy.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt
    - src/test/kotlin/com/catspell/api/common/WaitlistCorsPolicyTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt

key-decisions:
  - "Phase 18-09 (D-14, WR-07c): the waitlist join's CORS policy lives in one WaitlistCorsPolicy bean. SecurityConfig registers its configuration for /api/waitlist, and RateLimitFilterConfig passes the same object into RateLimitFilter. Only WaitlistCorsPolicy reads app.waitlist.allowed-origins"
  - "Phase 18-09 (D-14): only the WAITLIST_JOIN family's 429 gets CORS headers. It always adds Vary: Origin. The grant (ACAO echoing the request's spelling plus Expose-Headers) is added only when CorsConfiguration.checkOrigin accepts the Origin. There are no credentials, no wildcard and no CORS processor. AUTH and ADMIN 429s never carry a grant"

patterns-established:
  - "Shared CORS policy: read WaitlistCorsPolicy.configuration and never re-parse app.waitlist.allowed-origins"

requirements-completed: [WAIT-01, WAIT-03]

coverage:
  - id: D1
    description: "A throttled cross-origin join from an allowed landing origin gets a readable 429: [202, 202, 429], ACAO equal to the origin, Expose-Headers listing Retry-After, X-RateLimit-Remaining and X-RateLimit-Reset, Vary: Origin, Retry-After present, no credentials header"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt#a throttled join from the landing origin gets a 429 the page can read"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt#the 429 grant echoes the request's own spelling of an allowed origin"
        status: pass
    human_judgment: false
  - id: D2
    description: "No CORS grant leaks onto a 429: a foreign origin gets none but still varies on Origin, no Origin gets none, and a throttled /api/auth/login from the allowed origin gets none"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt#a throttled join from a foreign origin gets no grant but still varies on Origin"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt#a throttled join with no Origin header gets no grant"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt#a throttled login from the landing origin never gets a CORS grant"
        status: pass
    human_judgment: false
  - id: D3
    description: "One shared policy: WaitlistCorsPolicy is null for blank lists. Otherwise it is explicit-origin (trimmed), POST-only, Content-Type only, credential-free and maxAge 3600, and it uses no wildcard or origin patterns"
    requirement: WAIT-01
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/common/WaitlistCorsPolicyTest.kt (3 methods, 4 cases)"
        status: pass
    human_judgment: false
  - id: D4
    description: "No regression: WaitlistCorsIntegrationTest and WaitlistCorsDisabledIntegrationTest pass unchanged, and the 429 body and Retry-After are unchanged"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "./gradlew test --rerun --tests \"com.catspell.api.common.*\" --tests \"com.catspell.api.waitlist.*\" (28 classes, 178 tests, 0 failures)"
        status: pass
    human_judgment: false

duration: 18min
completed: 2026-10-04
status: complete
---

# Phase 18 Plan 09: CORS-readable 429 on the landing-page join from one shared policy Summary

**A throttled `POST /api/waitlist` from an origin in `app.waitlist.allowed-origins` now gets a 429 that the landing page can read cross-origin. The response carries `Access-Control-Allow-Origin`, exposes `Retry-After` and the `X-RateLimit-*` headers, and adds `Vary: Origin`. The decision comes from a new `WaitlistCorsPolicy` bean, which Spring Security's CORS registration also reads.**

## Performance

- **Duration:** 18 min
- **Started:** 2026-10-04T15:08:42Z
- **Completed:** 2026-10-04T15:26:49Z
- **Tasks:** 2 (Task 1 was a tracer)
- **Files modified:** 5 (3 created, 2 modified)

## Accomplishments
- **WR-07(c) / D-14:** RateLimitFilter runs at `HIGHEST_PRECEDENCE`, ahead of Spring Security's CorsFilter, so it writes the join's 429 itself. Before this plan, that 429 had no CORS headers, so the browser saw an opaque failure and could not read Retry-After. Now the 429 branch for the `WAITLIST_JOIN` family works like this:
  - it always adds `Vary: Origin`;
  - when `corsConfiguration?.checkOrigin(Origin)` returns a value, it echoes that value in `Access-Control-Allow-Origin` and sets `Access-Control-Expose-Headers: Retry-After, X-RateLimit-Remaining, X-RateLimit-Reset`.
  It uses no CORS processor class, because one would answer a disallowed origin with a 403 and hide the 429.
- **One source of truth (T-18-33):** `WaitlistCorsPolicy` (a `@Component` in `com.catspell.api.common.config`) parses `app.waitlist.allowed-origins` once. SecurityConfig no longer binds that key: `corsConfigurationSource()` registers `waitlistCorsPolicy.configuration` for `/api/waitlist` when it is non-null. RateLimitFilterConfig takes the same bean and passes `waitlistCorsPolicy.configuration` to the filter. A blank list gives `null`, and then neither side emits any CORS header.
- **Scope (T-18-32):** a foreign origin, a missing Origin and the AUTH and ADMIN families never get a grant. The 429 body, Retry-After and the X-RateLimit headers are unchanged. `RateLimitFilter()` and the existing positional constructions still compile, because the new parameter is last and defaults to null.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
|------|--------------|
| 1 (tracer) | src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt (at RED), then src/main/kotlin/com/catspell/api/common/config/WaitlistCorsPolicy.kt, src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt, src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt (at GREEN) |
| 2 | src/test/kotlin/com/catspell/api/common/WaitlistCorsPolicyTest.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt |
| Plan metadata | .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-09-SUMMARY.md, .planning/STATE.md, .planning/ROADMAP.md, .planning/REQUIREMENTS.md (whichever changed) |

RateLimitFilter.kt was edited on top of the staged 18-03 and 18-07 content, then re-staged. Those plans' changes are intact: hop canonicalization, the fail-safe resolveClientIp, the one-shot WARN, the three bucket families, `RequestPaths.normalized` and the RateLimitFilterConfig keys.

## TDD Evidence

**Task 1 (tracer). RED came before any production change.** I wrote WaitlistCors429IntegrationTest (4 cases) with the production code untouched.
- Command: `./gradlew test -Pkotlin.compiler.execution.strategy=in-process --tests "com.catspell.api.waitlist.WaitlistCors429IntegrationTest"`. Exit 1: 4 tests, 3 failed on assertions.
  - `a throttled join from the landing origin gets a 429 the page can read`: "expected: <https://landing.example> but was: <null>" (no ACAO on the 429)
  - `the 429 grant echoes the request's own spelling of an allowed origin`: "expected: <https://LANDING.example> but was: <null>"
  - `a throttled join from a foreign origin gets no grant but still varies on Origin`: "the 429 must vary on Origin ==> expected: <true> but was: <false>"
  - `a throttled join with no Origin header gets no grant` passed. This was expected: before the fix, the 429 carried no CORS headers at all.
- `gsd-tools check tdd-red-evidence` on the copied Surefire XML (target WaitlistCors429IntegrationTest) returned `RED_EVIDENCE_OK` / `target_test_failed`.
- The RED test file was staged before any production edit.
- GREEN: the Task 1 `<verify>` command gave WaitlistCors429 4/4, WaitlistCors 4/4 and WaitlistRateLimit 7/7. That is 15 tests, 0 failures.
- Tracer gate: the run is interactive (`auto_advance` false), `human_verify_mode` is end-of-phase and the verify is automated only. I re-ran the verify and it reported `:test` UP-TO-DATE, so the passing run on unchanged inputs still applied. Tracer verified end-to-end; expansion continued with no checkpoint.

**Task 2. Pinning tests for behavior Task 1 had already built, so RED was proven by mutation.**
- I added WaitlistCorsPolicyTest and the `/api/auth/login` case. Then I backed up the two production files in the session scratchpad and applied two temporary mutations:
  - RateLimitFilter: called `addJoinCorsHeaders` for every family, with no WAITLIST_JOIN guard;
  - WaitlistCorsPolicy: set `allowCredentials = true`.
- Command: `./gradlew test -Pkotlin.compiler.execution.strategy=in-process --tests "com.catspell.api.common.WaitlistCorsPolicyTest" --tests "com.catspell.api.waitlist.WaitlistCors429IntegrationTest"`. Exit 1: 9 tests, 2 failed on assertions.
  - `a throttled login from the landing origin never gets a CORS grant`: "a login 429 must get no grant ==> expected: <null> but was: <https://landing.example>"
  - `configured origins are trimmed and the join is POST-only with no credentials`: "expected: <false> but was: <true>"
- `check tdd-red-evidence` returned `RED_EVIDENCE_OK` for both WaitlistCorsPolicyTest and WaitlistCors429IntegrationTest.
- Restore: I copied both production files back from the backups. `git diff --name-only` then listed neither file, so they are byte-identical to the staged GREEN versions.
- GREEN: the Task 2 `<verify>` command (with `--rerun`) gave WaitlistCorsPolicy 4/4, WaitlistCors429 5/5 and WaitlistCors 4/4. That is 13 tests, 0 failures. The two mutation-sensitive assertions passed, so the run used the restored classes.

No REFACTOR step was needed.

## Verification
- Plan `<verification>` and a wider regression check together: `./gradlew test --rerun --tests "com.catspell.api.common.*" --tests "com.catspell.api.waitlist.*"`. Result: 28 classes, 178 tests, 0 failures, 0 errors, in 7m 7s. This covers WaitlistCorsPolicyTest, `WaitlistCors*` (including WaitlistCorsDisabledIntegrationTest) and WaitlistRateLimitIntegrationTest.
- Acceptance greps, all passing:
  - `checkOrigin` in RateLimitFilter.kt: 1
  - `CorsProcessor` in RateLimitFilter.kt: 0
  - `app.waitlist.allowed-origins` in SecurityConfig.kt: 0
  - `ACCESS_CONTROL_EXPOSE_HEADERS` in RateLimitFilter.kt: 1
  - `waitlistCorsPolicy.configuration` appears in both SecurityConfig.kt and RateLimitFilter.kt (key links)
- WaitlistCors429IntegrationTest has 5 `@Test` methods, including the `/api/auth/login` case that asserts no ACAO. WaitlistCorsPolicyTest has 3 methods (4 cases). All pass.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/common/config/WaitlistCorsPolicy.kt`: the shared join CORS policy (new)
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt`: depends on WaitlistCorsPolicy instead of the raw `@Value`; the unused `Value` and `CorsConfiguration` imports were removed
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`:
  - the `corsConfiguration` parameter and the `addJoinCorsHeaders` helper, called only for WAITLIST_JOIN 429s
  - the file-private `EXPOSED_RATE_LIMIT_HEADERS` constant
  - RateLimitFilterConfig takes WaitlistCorsPolicy
  - KDoc and comments for D-14
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt`: 5 registered-filter cases on peers 203.0.113.84 to .88 (new)
- `src/test/kotlin/com/catspell/api/common/WaitlistCorsPolicyTest.kt`: plain JUnit policy tests (new)

## Decisions Made
- The 429 grant echoes `checkOrigin`'s return value, which is the request's own spelling (for example `https://LANDING.example`). That matches what Spring's CORS processing does on the normal path.
- Expose-Headers is one comma-separated header line held in a file-private constant. The Vary header is added with `addHeader`, so it never overwrites a Vary value set earlier.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking/environment] Run with no tests caused by the IDE build race**
- **Found during:** Task 2 GREEN
- **Issue:** The first Task 2 verify finished with BUILD SUCCESSFUL in 22s but wrote no result files. A `--rerun` retry reported `:test NO-SOURCE`. The VS Code Java extension had cleared and was rebuilding `build/classes`, as the environment notes describe.
- **Fix:** I confirmed that `build/classes/kotlin/test` had been repopulated (151 classes) and re-ran with `--rerun`. That run executed 13 tests with 0 failures. No code was changed.
- **Files modified:** none

---

**Total deviations:** 1 (an environment re-run; no code impact). **Impact:** the production and test code are exactly as planned. Task 2's RED is mutation-based, because its tests pin behavior that Task 1 built. This is recorded in TDD Evidence above.

## Issues Encountered
- The VS Code build interference described above. The Testcontainers qemu readiness flake did not occur.

## Known Stubs

None.

## User Setup Required

None. The existing `app.waitlist.allowed-origins` key is reused unchanged. 18-11 documents the landing-page setup.

## Next Phase Readiness
- 18-10 and 18-11 can proceed. RateLimitFilter's constructor gained one trailing, defaulted parameter. The 429 body and headers are otherwise unchanged.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-04*

## Self-Check: PASSED

- (a) All 5 source/test paths, this SUMMARY, .planning/STATE.md and .planning/ROADMAP.md appear in `git diff --cached --name-only`. REQUIREMENTS.md was not changed: `requirements.ready-ids` reported 0/2 ready, because sibling plans 18-10 and 18-11 still declare WAIT-01/WAIT-03.
- (b) `git rev-parse HEAD` is `605ddb4d49266e805436c16b18bfc78746c46e40` on branch phase/18-address-tech-debt-post-block-redelivery-waitlist-review-warn. No commits were made.
- (c) `git diff --name-only` (unstaged changes) is empty.
- STATE.md archived milestone status lines (v1.0 "All phases complete", and v1.1 / v2.0 / v2.1 "✅ Milestone complete (shipped ...)") are unchanged; the diff against the pre-run copy touches only the current-position, metrics, session and decision lines.
