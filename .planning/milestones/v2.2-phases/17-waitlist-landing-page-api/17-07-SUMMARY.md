---
phase: 17-waitlist-landing-page-api
plan: 07
subsystem: api
tags: [rate-limiting, bucket4j, x-forwarded-for, trusted-proxy, security, spring-boot]

# Dependency graph
requires:
  - phase: 17-waitlist-landing-page-api (17-03)
    provides: RateLimitFilter registered on /api/auth/* and the exact POST /api/waitlist match
provides:
  - Trust-aware per-IP bucket key - X-Forwarded-For honored only when remoteAddr is a configured trusted proxy (T-17-30)
  - rate-limit.trusted-proxies config key (default 127.0.0.1,::1; env RATE_LIMIT_TRUSTED_PROXIES)
  - Integration proof on POST /api/auth/login and POST /api/waitlist that a forged X-Forwarded-For from an untrusted peer never gets a fresh bucket
affects: [17-VERIFICATION SC3, deployment / reverse-proxy configuration]

# Actuals (#2632) - chars/4 over the realized diff of this plan's edits
actuals:
  tokens: 1750
  tasks: 2
  commits: 0
plan_head_before: 6ae4f42581082686024784d210267d5ca2c05edc
plan_head_after: 6ae4f42581082686024784d210267d5ca2c05edc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Trusted-proxy gate before reading forwarding headers: key on remoteAddr unless the peer is in an exact-match allow-set"
    - "Rate-limit tests in a shared cached context pin a per-test remoteAddr and assert requests 1-2 are not 429 before asserting request 3 is"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt

key-decisions:
  - "RateLimitFilter trusts X-Forwarded-For only when request.remoteAddr is in rate-limit.trusted-proxies (exact match, default 127.0.0.1,::1); an untrusted peer is always keyed on its own socket address (T-17-30, replaces accepted T-17-17)"
  - "No CIDR or hop-count parsing: operators whose proxy connects from another address set RATE_LIMIT_TRUSTED_PROXIES"

patterns-established:
  - "Per-test TEST-NET-3 peer addresses (203.0.113.51/.52/.53) with 203.0.113.50 kept as the documented untrusted address, so tests sharing one bucket map never inherit a drained bucket"

requirements-completed: [WAIT-03]

coverage:
  - id: D1
    description: "RateLimitFilter ignores X-Forwarded-For from an untrusted remoteAddr; three forged headers from one untrusted peer hit 429 at capacity 2 on POST /api/auth/login"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt#a forged X-Forwarded-For from an untrusted remoteAddr never gets a fresh bucket"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt#an untrusted remoteAddr without X-Forwarded-For is throttled at capacity"
        status: pass
    human_judgment: false
  - id: D2
    description: "The same forged-header throttle proof on the SC3-named endpoint POST /api/waitlist"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt#a forged X-Forwarded-For from an untrusted remoteAddr is still throttled on the waitlist join"
        status: pass
    human_judgment: false
  - id: D3
    description: "Trusted-proxy (127.0.0.1 default) forwarded IPs still honored; no regression in suites that vary X-Forwarded-For"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.catspell.api.waitlist.* --tests com.catspell.api.common.* --tests com.catspell.api.invite.* --tests com.catspell.api.auth.* --tests com.catspell.api.push.* (236 tests, 0 failures, 1 pre-existing skip FcmSmokeTest)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Production trusted-proxy address matches the real reverse proxy's peer address"
    verification: []
    human_judgment: true
    rationale: "Deployment-specific: the default 127.0.0.1,::1 is only correct when the proxy connects from loopback; an operator must confirm or set RATE_LIMIT_TRUSTED_PROXIES for the actual ingress"

# Metrics
duration: 15min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 07: Trusted-proxy X-Forwarded-For gate for the per-IP rate limit Summary

**RateLimitFilter now keys per-IP buckets on remoteAddr and honors X-Forwarded-For only from configured trusted proxies (default 127.0.0.1,::1), so a direct caller can no longer reset its throttle on /api/auth/* or POST /api/waitlist by rotating the header.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-10-02T11:42:02Z
- **Completed:** 2026-10-02T11:57:23Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- `resolveClientIp` returns `remoteAddr` immediately when the peer is not in `trustedProxies`; it reads the first X-Forwarded-For value only for a trusted peer (T-17-30). This closes the failed SC3 per-IP half in 17-VERIFICATION.md.
- `RateLimitFilterConfig` reads `rate-limit.trusted-proxies` (default `127.0.0.1,::1`), splits and trims it into a set, and passes it to the registered filter. The no-arg `RateLimitFilter()` call site still compiles.
- Integration proof on `POST /api/auth/login` (new `RateLimitTrustedProxyIntegrationTest`, 2 tests) and on `POST /api/waitlist` (1 new test in `WaitlistRateLimitIntegrationTest`).
- Regression sweep across the waitlist, common, invite, auth and push packages: 236 tests, 0 failures. The 1 skip is the pre-existing credential-gated `FcmSmokeTest`.

## Staged files (no commits; stage-only rule)

| Task | Paths staged |
|------|--------------|
| 1: Trust X-Forwarded-For only from a trusted proxy (tracer, TDD) | `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`, `src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt` |
| 2: Prove on POST /api/waitlist + regression sweep | `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt` |
| Plan metadata | `.planning/phases/17-waitlist-landing-page-api/17-07-SUMMARY.md`, `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md` |

HEAD unchanged at `6ae4f42581082686024784d210267d5ca2c05edc`.

## TDD Evidence (Task 1)

- **RED** (in place of a RED commit hash): `./gradlew test --tests "com.catspell.api.common.RateLimitTrustedProxyIntegrationTest"` exited 1 before the fix. The failing test was `RateLimitTrustedProxyIntegrationTest#a forged X-Forwarded-For from an untrusted remoteAddr never gets a fresh bucket()`, at the request-3 assertion: `AssertionFailedError: three distinct forged X-Forwarded-For values from one untrusted peer must share that peer's bucket ==> expected: <429> but was: <401>`. The pre-fix filter keyed each request on its forged header, so request 3 was not throttled. The no-header baseline test passed, as expected. `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` (target_test_failed).
- **GREEN:** After the `RateLimitFilter.kt` change, `./gradlew compileKotlin -q && ./gradlew test --tests "...RateLimitTrustedProxyIntegrationTest"` passed with 2/2 tests and BUILD SUCCESSFUL. The tracer gate re-ran the verify with `--rerun` and it passed again before Task 2 started.
- **REFACTOR:** None needed.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`: `trustedProxies` constructor parameter, trust-gated `resolveClientIp`, `rate-limit.trusted-proxies` wiring in `RateLimitFilterConfig`.
- `src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt`: forged-XFF and no-XFF proofs against the registered filter on `/api/auth/login`.
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt`: `joinFromUntrustedPeer` helper, the new forged-XFF waitlist test, and a clarifying comment above the existing "different IP" test. Existing assertions are untouched; the diff is additions only.

## Decisions Made
- Trusted proxies are an exact-match set with no CIDR or hop counting, as the plan scoped. Spring relaxed binding means `RATE_LIMIT_TRUSTED_PROXIES` overrides the key.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Shared-context bucket collision in the planned test design (orchestrator-flagged pitfall)**
- **Found during:** Task 1 (test authoring), applied again in Task 2
- **Issue:** I checked the code. `RateLimitFilter` keys buckets on client IP only, not on path. The bucket map lives in the filter bean for the whole cached Spring context and refills once per minute. `RateLimitTrustedProxyIntegrationTest` and `WaitlistRateLimitIntegrationTest` have identical annotations (`@SpringBootTest @AutoConfigureMockMvc @TestPropertySource(rate-limit.capacity=2)` on `BaseIntegrationTest`), so they share one context and one bucket map. The plan said to pin every new test to `203.0.113.50`. With that design, whichever test ran second would start on a drained bucket. Its "third request is 429" assertion would then pass for the wrong reason, and there would be no real RED.
- **Fix:** I kept `203.0.113.50` in both files as the documented `UNTRUSTED_PEER` constant, so the artifact checks pass. Each test uses its own TEST-NET-3 address that no other test uses: `.51` for the forged-XFF login test, `.52` for the no-XFF login test, `.53` for the forged-XFF waitlist test. Each test also uses its own forged-XFF range (`10.201.0.x` and `10.203.0.x`). Every test asserts requests 1 and 2 are NOT 429 before it asserts request 3 IS 429.
- **Files modified:** `RateLimitTrustedProxyIntegrationTest.kt`, `WaitlistRateLimitIntegrationTest.kt`
- **Verification:** RED failed on the request-3 assertion (401, not 429) before the fix. GREEN passed after the fix. The sweep passed.
- **Staged in:** Task 1 and Task 2 staged files

---

**Total deviations:** 1 auto-fixed (Rule 1, test-correctness)
**Impact on plan:** The change was needed so the tests actually prove the fix. No scope change, and production behavior is exactly as planned.

## Issues Encountered
None.

## Threat Flags
None. The change narrows an existing trust boundary (T-17-30 in the plan's threat model) and adds no new surface.

## User Setup Required
None for dev or tests. For production, the default trusted set (`127.0.0.1,::1`) is correct only when the reverse proxy connects over loopback. Otherwise set `RATE_LIMIT_TRUSTED_PROXIES` to the proxy's peer address (see coverage D4).

## Next Phase Readiness
- SC3's per-IP half is closed. 17-08 (test-tier gaps) is the remaining gap-closure plan in this phase.
- The orchestrator still owns the full-suite `./gradlew test` run after this wave.

## Self-Check: PASSED

- All 3 key files exist and are fully staged; nothing is left unstaged in the working tree.
- HEAD is still `6ae4f42`, and no commits were made, as the stage-only rule requires.
- WAIT-03 was not marked complete. `requirements.ready-ids` blocked it because 17-08 also declares WAIT-03 and has no SUMMARY yet.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*
