---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 10
subsystem: security
tags: [admin-token, servlet-filter, filter-registration-bean, jwt, spring-security, mockmvc]

requires:
  - phase: 18-05
    provides: AdminTokenGuard minimum-length startup check and the shared TEST_ADMIN_TOKEN
  - phase: 18-07
    provides: RequestPaths.normalized and the admin throttle at HIGHEST_PRECEDENCE on /api/admin/*
  - phase: 18-09
    provides: SecurityConfig wired to WaitlistCorsPolicy
provides:
  - "AdminTokenFilter: a central deny-by-default X-Admin-Token filter on /api/admin/*, order HIGHEST_PRECEDENCE + 10 (after RateLimitFilter, before Spring Security at -100), registered only through AdminTokenFilterConfig's FilterRegistrationBean"
  - "JwtAuthenticationFilter skip list reads RequestPaths.normalized: exact /api/waitlist and /api/waitlist/confirm, plus /api/admin and /api/admin/ prefixed paths"
  - "SecurityConfig permits /api/admin/** as a whole (the filter is the boundary)"
  - "MockMvc and direct-filter proofs of D-11, D-12 (AUD-01), IN-06 and IN-09"
affects: [18-11]

actuals:
  tokens: 3300
  tasks: 2
  commits: 0
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40

tech-stack:
  added: []
  patterns:
    - "Path-scoped servlet filter: a plain OncePerRequestFilter plus a sibling @Configuration exposing FilterRegistrationBean (never a @Component, so Boot does not also map it to every path)"
    - "Path-based security decisions (rate limiter, JWT skip list) use RequestPaths.normalized; the JWT skip list uses exact matches for public routes"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/common/security/AdminTokenFilter.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
    - src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt
    - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt
    - src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt

key-decisions:
  - "Phase 18-10 (D-11, WR-08): the operator boundary is one servlet filter, AdminTokenFilter, on /api/admin/* at HIGHEST_PRECEDENCE + 10. It reuses AdminTokenGuard.require and answers 401 Not authorized before Spring Security and MVC, so unmapped admin paths are 401 (not 404) and malformed parameters are 401 (not 400) without a token. Per-handler require() calls stay as defense in depth"
  - "Phase 18-10 (D-12, AUD-01): the JWT filter skips /api/admin and /api/admin/*, so a stale Bearer header no longer 401s an operator who sends the right X-Admin-Token (RED observed: 401 Invalid or expired token)"
  - "Phase 18-10 (IN-06): the JWT filter's waitlist skip is exact (/api/waitlist, /api/waitlist/confirm) and reads RequestPaths.normalized, so the skip list now also applies under MockMvc"

patterns-established:
  - "Operator routes: AdminTokenFilter is the boundary; SecurityConfig permits /api/admin/** and new admin handlers still call AdminTokenGuard.require as a second layer"

requirements-completed: [INV-02, WAIT-04, WAIT-01]

coverage:
  - id: D1
    description: "An operator with the correct X-Admin-Token and a stale Authorization: Bearer header gets 200 from GET /api/admin/waitlist (D-12 / AUD-01)"
    requirement: WAIT-04
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#correct token with a stale Bearer header still lists entries"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every /api/admin path is token-gated before Spring Security: unmapped path without a token is 401 Not authorized, with the token it is 404; blank configured token gives 401 on any admin path; the registration is /api/admin/* at HIGHEST_PRECEDENCE + 10 (D-11 / WR-08)"
    requirement: INV-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#an unmapped admin path without a token is 401 not 404"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#an unmapped admin path with the correct token reaches MVC and is 404"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#the admin token filter is registered on every admin path after the rate limiter and before Spring Security"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#any admin path is 401 when admin-token is blank whatever header is sent (WaitlistAdminDenyByDefaultIntegrationTest)"
        status: pass
    human_judgment: false
  - id: D3
    description: "GET /api/admin/waitlist?limit=abc without a token is 401, not 400 (IN-09)"
    requirement: WAIT-04
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#a malformed limit without a token is 401 not 400"
        status: pass
    human_judgment: false
  - id: D4
    description: "Production-shape JWT skip list: /api/admin/waitlist and /api/admin/invites skipped, /api/waitlistX still validated (401), join/confirm/profile unchanged (IN-06, D-12)"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt (7 tests)"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.catspell.api.waitlist.* --tests com.catspell.api.invite.* --tests com.catspell.api.auth.* (35 classes, 210 tests, 0 failures)"
        status: pass
    human_judgment: false

duration: 19min
completed: 2026-10-04
status: complete
---

# Phase 18 Plan 10: Central Admin Token Filter and Exact JWT Skip List Summary

**A deny-by-default `AdminTokenFilter` now guards every `/api/admin` path between the rate limiter and Spring Security, and the JWT filter skips operator routes and matches the waitlist routes exactly on the normalized path. A stale Bearer header no longer locks the operator out (before the fix: 401 `Invalid or expired token`). Unmapped or malformed admin requests without a token get the generic 401.**

## Performance

- **Duration:** 19 min
- **Started:** 2026-10-04T15:42:34Z
- **Completed:** 2026-10-04T16:01:41Z
- **Tasks:** 2
- **Files modified:** 8 (1 created, 7 modified)

## Accomplishments

- **WR-08 / D-11:** `AdminTokenFilter` (a plain `OncePerRequestFilter`) calls `AdminTokenGuard.require(request.getHeader("X-Admin-Token"))` and maps `AdminAuthException` to `writeUnauthorized(response, "Not authorized")`. `AdminTokenFilterConfig` registers it through `FilterRegistrationBean` on `/api/admin/*` at `Ordered.HIGHEST_PRECEDENCE + 10`. That puts it after RateLimitFilter, so guesses are throttled, and before Spring Security (-100) and MVC. It is not a `@Component`.
- **D-12 / AUD-01:** `JwtAuthenticationFilter.shouldNotFilter` skips `/api/admin` and `/api/admin/` prefixed paths, so the correct token plus `Authorization: Bearer garbage` now lists the waitlist (200).
- **IN-06:** the waitlist skip is now exact (`/api/waitlist`, `/api/waitlist/confirm`), and the path comes from `RequestPaths.normalized(request)` instead of `request.servletPath`. `/api/waitlistX` with a stale Bearer is still 401. The skip list now applies under MockMvc too.
- **IN-09:** `GET /api/admin/waitlist?limit=abc` without a token is 401, not 400. `GET /api/admin/does-not-exist` is 401 `Not authorized` without a token and 404 with it.
- **SecurityConfig:** the two narrow admin matchers became one `requestMatchers("/api/admin/**").permitAll()`, with a comment naming AdminTokenFilter as the boundary.
- **Docs:** the KDocs of WaitlistAdminController, InviteAdminController and AdminTokenGuard now describe the central filter as the boundary and the handler `require(...)` calls as defense in depth. Both handler checks in WaitlistAdminController remain. AdminTokenGuard code is unchanged.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
| ---- | ------------ |
| 1 (tracer), at RED | src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt |
| 1 (tracer), at GREEN | src/main/kotlin/com/catspell/api/common/security/AdminTokenFilter.kt, src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt, src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt |
| 2 | src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt, src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt, src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt, src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt |
| Plan metadata | .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-10-SUMMARY.md, .planning/STATE.md, .planning/ROADMAP.md, .planning/REQUIREMENTS.md (whichever changed) |

SecurityConfig.kt (18-09), AdminTokenGuard.kt (18-05) and WaitlistAdminIntegrationTest.kt (18-05) were edited on top of their staged content and re-staged. The WaitlistCorsPolicy injection, the `MIN_ADMIN_TOKEN_LENGTH` startup check and `TEST_ADMIN_TOKEN` are intact.

## TDD Evidence

**Task 1 (tracer). RED came first: D-12 was reproduced through MockMvc before any production change.**
- I added 5 behavior tests that do not reference the new class: the stale-Bearer list, the unmapped path with and without a token, `limit=abc` without a token, and the blank-token `/api/admin/anything` case in WaitlistAdminDenyByDefaultIntegrationTest.
- Command: `./gradlew test -Pkotlin.compiler.execution.strategy=in-process --rerun --tests "com.catspell.api.waitlist.WaitlistAdminIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistAdminDenyByDefaultIntegrationTest"`. Exit 1: 18 tests, 5 failed, all on assertions:
  - `correct token with a stale Bearer header still lists entries`: "Status expected:<200> but was:<401>". The response body was `{"title":"Unauthorized","status":401,"detail":"Invalid or expired token"}`. **This is the D-12 RED status, the evidence for the AUD-01 disposition row.**
  - `an unmapped admin path without a token is 401 not 404`: "JSON path "$.detail" expected:<Not authorized> but was:<Authentication required>"
  - `an unmapped admin path with the correct token reaches MVC and is 404`: "Status expected:<404> but was:<401>"
  - `a malformed limit without a token is 401 not 400`: "Status expected:<401> but was:<400>"
  - `any admin path is 401 when admin-token is blank whatever header is sent`: "JSON path "$.detail" expected:<Not authorized> but was:<Authentication required>"
- `check tdd-red-evidence` returned `RED_EVIDENCE_OK` (target: the stale-Bearer test).
- GREEN: I created AdminTokenFilter and changed the JWT skip list and SecurityConfig, then added the registration test (`urlPatterns == [/api/admin/*]`, `order == HIGHEST_PRECEDENCE + 10`). It was written after the class existed so the RED run was not a compile error. The Task 1 `<verify>` command, plus the deny-by-default class, gave WaitlistAdmin 16/16, WaitlistAdminDenyByDefault 3/3 and WaitlistEnumerationSafety 4/4. 23 tests, 0 failures.
- Tracer feedback gate: interactive run (auto_advance false), `human_verify_mode` end-of-phase, and the verify is automated only. The re-run passed, so the tracer was verified end-to-end and I expanded with no checkpoint.

**Task 2. Mutation RED.** Task 1 had already produced the behavior these tests pin, so I followed the 18-07/18-09 approach.
- I added the three direct-filter tests (`/api/admin/waitlist`, `/api/admin/invites`, `/api/waitlistX`). I backed up JwtAuthenticationFilter.kt to the session scratchpad and wrote the HEAD (pre-plan) version over the working file only. Then I ran `./gradlew test --rerun --tests "com.catspell.api.waitlist.WaitlistEnumerationSafetyIntegrationTest"`.
- Exit 1: 7 tests, 3 failed on assertions:
  - "the operator list must reach the rest of the filter chain ==> expected: not <null>"
  - "invite issuance must reach the rest of the filter chain ==> expected: not <null>"
  - "a look-alike path with an invalid token must stop at the JWT filter ==> expected: <null> but was: <MockHttpServletRequest@...>"
- I restored the file from the backup. `cmp` confirmed it was byte-identical, and `git diff` showed no unstaged change. `check tdd-red-evidence` returned `RED_EVIDENCE_OK`.
- GREEN: the Task 2 `<verify>` command, `./gradlew test --rerun --tests "com.catspell.api.waitlist.*" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.auth.*"`, gave BUILD SUCCESSFUL. 35 classes (auth 9, waitlist 17, invite 9), 210 tests, 0 failures, 0 errors, in 11m 53s.

**REFACTOR:** none needed.

## Files Created/Modified

- `src/main/kotlin/com/catspell/api/common/security/AdminTokenFilter.kt`: AdminTokenFilter plus AdminTokenFilterConfig (new)
- `src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt`: the skip list uses the normalized path, with exact waitlist matches and operator routes
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt`: `/api/admin/**` permitAll, with a boundary comment
- `src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt`: class KDoc only
- `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt`: class KDoc and the `convert` KDoc
- `src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt`: class KDoc
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt`: 5 new tests in the main class, 1 in the deny-by-default class
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt`: 3 direct-filter tests; the `runJwtFilter` and class KDocs were updated

## Decisions Made

- I put the blank-token test ("nested blank-token class" in the plan) in the existing top-level `WaitlistAdminDenyByDefaultIntegrationTest` in the same file. That class already carries `@TestPropertySource(properties = ["app.invite.admin-token="])`. The Task 1 verify filter names only `WaitlistAdminIntegrationTest`, so I also ran the deny-by-default class explicitly. The Task 2 `waitlist.*` run covers it too.
- The filter's header name is a private constant (`X-Admin-Token`). It matches the handlers' `@RequestHeader` value.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] KDoc URL patterns opened a nested Kotlin comment**
- **Found during:** Task 1 (GREEN compile)
- **Issue:** Kotlin nests block comments, so the `/*` inside the backticked URL patterns in the new KDoc opened a nested comment, and the compile failed with "Unclosed comment".
- **Fix:** I reworded the two KDoc sentences so they don't spell `/*`. The code still uses `addUrlPatterns("/api/admin/*")`.
- **Files modified:** src/main/kotlin/com/catspell/api/common/security/AdminTokenFilter.kt
- **Verification:** compile passed; Task 1 verify was green.
- **Staged in:** Task 1 GREEN staging

**2. [Rule 1 - Doc accuracy] WaitlistAdminController.convert KDoc**
- **Found during:** Task 2
- **Issue:** The KDoc said a malformed UUID fails with 400. That now holds only with the correct token; without a token the filter answers 401 first.
- **Fix:** I made the sentence conditional. No code changed.
- **Files modified:** src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt
- **Staged in:** Task 2 staging

**3. [Rule 1 - Doc accuracy] runJwtFilter KDoc in WaitlistEnumerationSafetyIntegrationTest**
- **Found during:** Task 2
- **Issue:** The KDoc said "the skip list cannot be exercised through MockMvc". That is no longer true, because the skip list reads RequestPaths.normalized.
- **Fix:** It now says these tests pin the production (Tomcat) shape.
- **Staged in:** Task 2 staging

---

**Total deviations:** 3 auto-fixed (1 compile bug, 2 documentation-accuracy fixes)
**Impact on plan:** none on scope. All three are confined to files the plan lists.

## Issues Encountered

- In the Task 2 verify run, `compileKotlin` was UP-TO-DATE after the KDoc-only edits, which means another process (likely the IDE) had already compiled the current sources into `build/`. The edits were comments only, so this does not affect the result.

## Threat Flags

None. The new surface is the filter the plan's threat model covers (T-18-34 to T-18-38). The `/api/admin/**` permitAll is safe because AdminTokenFilter runs first on the same prefix, and both handler `require()` calls remain (T-18-38).

## User Setup Required

None. No external service configuration required.

## Next Phase Readiness

- 18-11 can record AUD-01 as closed. The RED status was 401 `Invalid or expired token`, and GREEN is 200. 18-11 can also document AdminTokenFilter as the operator boundary in the operator docs.

## Self-Check: PASSED

- AdminTokenFilter.kt exists, and all 8 plan paths are in `git diff --cached --name-only`.
- `git rev-parse HEAD` is `605ddb4d49266e805436c16b18bfc78746c46e40`.
- `git diff --name-only` (unstaged) lists none of this plan's files.
- Acceptance greps: `@Component` 0, `HIGHEST_PRECEDENCE + 10` 1, `startsWith("/api/waitlist")` 0, `request.servletPath` 0, `"/api/admin/**"` 1; the "WHOLE/ENTIRE access boundary" grep gives 0 in each of the three files; `adminTokenGuard.require` in WaitlistAdminController gives 2; `/api/waitlistX` and `/api/admin/waitlist` are present in WaitlistEnumerationSafetyIntegrationTest.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-04*
