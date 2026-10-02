---
phase: 17-waitlist-landing-page-api
plan: 03
subsystem: api
tags: [waitlist, bucket4j, rate-limit, cors, spring-security, jwt, enumeration-safety]

# Dependency graph
requires:
  - phase: 17-waitlist-landing-page-api
    provides: "17-01 public POST /api/waitlist (WAITLIST_JOIN_MESSAGE), SecurityConfig permitAll, app.waitlist.allowed-origins key; 17-02 GET /api/waitlist/confirm handler"
provides:
  - per-IP Bucket4j throttle on POST /api/waitlist (exact method+path match, registered URL pattern /api/waitlist)
  - config-driven CORS (corsConfigurationSource bean) mapped on /api/waitlist only, POST + Content-Type, no credentials, blank = inert
  - JwtAuthenticationFilter skip for /api/waitlist (join + confirm)
  - six-state byte-identical join response proof (WAIT-01 / D-04)
affects: [17-04, 17-05, 17-06]

# Actuals (#2632) — chars/4 over the realized diff
actuals:
  tokens: 4366
  tasks: 3
  commits: 0
plan_head_before: 6ae4f42581082686024784d210267d5ca2c05edc
plan_head_after: 6ae4f42581082686024784d210267d5ca2c05edc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Exact method + path match inside the shared RateLimitFilter for a single public POST route (no prefix entry, so sibling GET routes and OPTIONS preflights are never throttled)"
    - "CorsConfigurationSource bean passed explicitly to http.cors { it.configurationSource(...) }, registered per exact path, blank config registers nothing"
    - "Filter-level JWT skip tests call the filter bean directly with an explicit servletPath (MockMvc leaves servletPath empty)"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistCorsIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
    - src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt

key-decisions:
  - "Boot's auto-configured MockMvc applies FilterRegistrationBean URL patterns (RESEARCH A2 confirmed), so WaitlistRateLimitIntegrationTest uses the injected MockMvc and needs no hand-built builder"
  - "Per-IP join throttle is an exact POST + /api/waitlist match, not an AUTH_PATHS prefix, so confirm links and CORS preflights are never limited"
  - "CORS maps only /api/waitlist with explicit origins, POST, Content-Type, allowCredentials=false, maxAge 3600; blank app.waitlist.allowed-origins registers no mapping"

patterns-established:
  - "Public-route wiring for a browser-called endpoint: permitAll + JWT shouldNotFilter skip + RateLimitFilter registration + narrow CORS mapping"

requirements-completed: [WAIT-03, WAIT-01]

coverage:
  - id: D1
    description: "The registered RateLimitFilter covers POST /api/waitlist per IP: urlPatterns contain /api/auth/* and /api/waitlist; at capacity 2 joins 1-2 are 202 and join 3 is 429 problem+json 'Too Many Requests' with an integer Retry-After >= 1; another IP is still 202"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt (4 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "GET /api/waitlist/confirm is never throttled and carries no X-RateLimit-Remaining even after the IP's join bucket is exhausted; existing auth throttling unchanged"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt#confirm links are never throttled even after the IP join bucket is exhausted"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitIntegrationTest.kt (10 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Config-driven CORS: the configured origin gets Access-Control-Allow-Origin on preflight (200, POST allowed) and on the actual 202 join; a foreign origin, /api/auth/login, and the blank-config context get none; Access-Control-Allow-Credentials is never sent"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistCorsIntegrationTest.kt (WaitlistCorsIntegrationTest 4 tests + WaitlistCorsDisabledIntegrationTest 1 test)"
        status: pass
    human_judgment: false
  - id: D4
    description: "JwtAuthenticationFilter skips /api/waitlist and /api/waitlist/confirm with a garbage Bearer header (chain reached, no 401) while /api/profile with the same header still gets 401"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt (3 filter-level tests)"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/auth/AuthIntegrationTest.kt (15 tests, incl. protected endpoint with invalid token)"
        status: pass
    human_judgment: false
  - id: D5
    description: "New, PENDING-duplicate, CONFIRMED-duplicate, INVITED-duplicate, +suffix-variant-of-confirmed and per-email-exhausted joins return byte-identical status, Content-Type and body; message equals WAITLIST_JOIN_MESSAGE"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt#new pending confirmed invited suffix-variant and throttled joins are byte-identical"
        status: pass
    human_judgment: false
  - id: D6
    description: "Browser on the real landing-page origin can complete the join end-to-end in production deployment"
    requirement: WAIT-03
    verification: []
    human_judgment: true
    rationale: "MockMvc proves the CORS headers; a real browser fetch from the deployed landing page (and the operator setting WAITLIST_ALLOWED_ORIGINS / X-Forwarded-For at the proxy) can only be checked against a deployment"

# Metrics
duration: 21min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 03: Waitlist Anti-Abuse and Integration Surface Summary

**The registered Bucket4j `RateLimitFilter` now throttles `POST /api/waitlist` per IP through an exact method+path match and the `/api/waitlist` URL pattern. A blank-by-default `CorsConfigurationSource` lets only the configured landing-page origin POST the join without credentials. The JWT filter ignores stale Bearer headers on the waitlist routes. A six-state test proves the join response is byte-identical across every membership state.**

Changes staged, not committed (user's no-auto-commit rule).

## Performance

- **Duration:** 21 min (12 of them the full-suite run)
- **Started:** 2026-10-02T09:18:25Z
- **Completed:** 2026-10-02T09:39:55Z
- **Tasks:** 3
- **Files modified:** 6 (3 created, 3 modified)

## Accomplishments
- `RateLimitFilter.doFilter` adds an `isWaitlistJoin` exact match (`POST` + `/api/waitlist`). `RateLimitFilterConfig` registers `addUrlPatterns("/api/auth/*", "/api/waitlist")`. Capacity, refill, ordering and the 429 body/headers are unchanged.
- `SecurityConfig` gains `@Value("\${app.waitlist.allowed-origins:}")`, a `corsConfigurationSource()` bean that maps `/api/waitlist` only (explicit origins, `POST`, `Content-Type`, `allowCredentials = false`, `maxAge = 3600`), and `.cors { it.configurationSource(corsConfigurationSource()) }`.
- `JwtAuthenticationFilter.shouldNotFilter` skips `path.startsWith("/api/waitlist")`. Admin waitlist paths are not added.
- Three new test classes (plus `WaitlistCorsDisabledIntegrationTest` in the CORS file) cover every must-have truth.

## Task Outcomes (staged, not committed)

| Task | Files staged | Verification |
|------|--------------|--------------|
| 1. Per-IP throttle covers POST /api/waitlist (tdd=true) | RateLimitFilter.kt, WaitlistRateLimitIntegrationTest.kt | RED: all 4 tests failed on intended assertions (urlPatterns `[/api/auth/*]`, 3rd join 202 not 429). GREEN: `./gradlew test --tests WaitlistRateLimitIntegrationTest --tests RateLimitIntegrationTest`: PASS (4 + 10). Acceptance greps PASS. |
| 2. Config-driven CORS on POST /api/waitlist only (tdd=true) | SecurityConfig.kt, WaitlistCorsIntegrationTest.kt | RED: the 2 positive cases failed (preflight 401, no ACAO on POST). The 3 negative cases passed, as expected before any CORS existed. GREEN: `./gradlew test --tests "WaitlistCors*"`: PASS (4 + 1). All 5 acceptance greps PASS. |
| 3. JWT skip + six-state enumeration proof (tdd=true) | JwtAuthenticationFilter.kt, WaitlistEnumerationSafetyIntegrationTest.kt | RED: the join and confirm skip cases failed (chain.request null). The /api/profile control and the six-state test passed (17-01 already built that behavior; this test is a proof). GREEN: `./gradlew test --tests WaitlistEnumerationSafetyIntegrationTest --tests AuthIntegrationTest`: PASS (4 + 15). Acceptance grep PASS. |

Plan-level verification:
- `./gradlew compileKotlin -q`: exit 0.
- `./gradlew test --tests "com.catspell.api.waitlist.*" --tests "com.catspell.api.common.*" --tests "com.catspell.api.auth.AuthIntegrationTest"`: 73 tests, 0 failures.
- Full `./gradlew test` (with 17-01 and 17-02 in the tree): 363 tests, 0 failures, 0 errors, 1 skipped (the existing `FcmSmokeTest`). `/ws` and the other routes are unaffected.

## TDD Notes
- `workflow.tdd_mode=false`, so the RED/GREEN commit gate does not apply. Each task was staged after GREEN instead of committed.
- REFACTOR: none needed.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`: exact join match and the `/api/waitlist` URL registration.
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt`: CORS source bean and `http.cors` wiring.
- `src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt`: `/api/waitlist` skip.
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt`: registration, 202/202/429 boundary, Retry-After, confirm-not-throttled, other-IP.
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistCorsIntegrationTest.kt`: configured-origin and blank-config CORS classes.
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt`: filter-level JWT skip tests and the six-state byte-equality test.

## Decisions Made
- RESEARCH A2 holds: the injected auto-configured MockMvc applied the registered URL patterns, so the fallback `MockMvcBuilders...addFilter(...)` builder was not needed.
- No OPTIONS permitAll was added. The CORS filter answers allowed preflights before authorization. Disallowed or unmapped preflights fall through to the existing rules and get no CORS headers.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] KDoc text `/ws/**` opened a nested Kotlin comment**
- **Found during:** Task 2 (first GREEN compile)
- **Issue:** Kotlin supports nested block comments. The `/*` inside `` `/ws/**` `` in the new KDoc opened one, so compilation failed with "Unclosed comment".
- **Fix:** Reworded the KDoc to "the `/ws` STOMP endpoint".
- **Files modified:** src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
- **Verification:** It compiles, and the CORS tests pass.
- **Staged in:** Task 2 staging

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Comment wording only. No behavior change and no scope creep.

## Issues Encountered
None.

## Threat Model Coverage
- T-17-13: mitigated. The registration is asserted on the bean, and the boundary is proven.
- T-17-14: mitigated. Explicit origins, POST only, no credentials, `/api/waitlist` only. The blank default is inert.
- T-17-15: mitigated. The six-state byte-equality test passes.
- T-17-16: mitigated. The filter-level skip is proven, with the /api/profile negative control.
- T-17-17 (X-Forwarded-For rotation): accepted as planned. It is recorded for SECURITY.md.

## User Setup Required
None for local or test runs. For production, the operator sets `WAITLIST_ALLOWED_ORIGINS` to the landing-page origin(s). The proxy must overwrite `X-Forwarded-For` so the per-IP bucket keys on real client IPs.

## Next Phase Readiness
- Ready for 17-04 (admin listing), 17-05 (convert to invite) and 17-06 (contract/migration tests).
- Changes are staged but not committed. The user needs to review and commit.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*

## Self-Check: PASSED
- All 6 plan files and this SUMMARY exist on disk. They show in `git diff --cached --name-only`, which replaces the commit check under the no-auto-commit override. HEAD is still 6ae4f42, so no commits were made.
