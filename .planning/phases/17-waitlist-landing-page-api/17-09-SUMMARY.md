---
phase: 17-waitlist-landing-page-api
plan: 09
subsystem: api
tags: [rate-limiting, bucket4j, x-forwarded-for, trusted-proxy, cidr, urlpathhelper, spring-security, mockmvc]

# Dependency graph
requires:
  - phase: 17-waitlist-landing-page-api (17-03)
    provides: exact POST /api/waitlist throttle match and the /api/waitlist URL registration on RateLimitFilter
  - phase: 17-waitlist-landing-page-api (17-07)
    provides: rate-limit.trusted-proxies @Value binding and the untrusted-peer remoteAddr key (T-17-30)
provides:
  - Decoded-path throttle match, so percent-encoded join/login spellings share the canonical per-IP bucket (T-17-32, CR-02)
  - Rightmost-untrusted X-Forwarded-For hop as the trusted-peer bucket key, read across every header line (T-17-33, CR-01)
  - TrustedProxyMatcher with family-safe exact/CIDR matching over strict IP literals and fail-fast config (T-17-34, T-17-35, WR-09)
  - Declared rate-limit.trusted-proxies key and operator documentation for client-IP resolution
affects: [17-VERIFICATION re-verify, 17-VALIDATION SC3 rows, RateLimitFilter, reverse-proxy deployment config]

# Actuals (#2632): chars/4 over the realized diff (21,555 changed-line chars)
actuals:
  tokens: 5400
  tasks: 3
  commits: 0
plan_head_before: c6cf055f3dd91a5dfaae6c041fc11fa7ffa0d076
plan_head_after: c6cf055f3dd91a5dfaae6c041fc11fa7ffa0d076

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Request-path security checks use UrlPathHelper.defaultInstance.getPathWithinApplication, the same decoded view Spring MVC, Spring Security and MockMvc's filter decorator match on"
    - "Client IP behind proxies = rightmost X-Forwarded-For hop that is not a trusted proxy (Tomcat RemoteIpValve semantics), across all header lines"
    - "Trusted-address membership by parsed bytes + CIDR prefix within one family; only strict IP literals ever reach InetAddress"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt
    - src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
    - src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt
    - src/main/resources/application.yml
    - docs/CONFIGURATION.md

key-decisions:
  - "RateLimitFilter matches on UrlPathHelper.defaultInstance.getPathWithinApplication, not the raw requestURI; the waitlist join stays an exact POST + /api/waitlist match (no prefix rule)"
  - "A trusted peer's bucket key is the rightmost X-Forwarded-For hop that is not a trusted proxy, read across every header line; all-trusted or no hops fall back to remoteAddr"
  - "Trusted proxies are matched by TrustedProxyMatcher (JDK-only, exact or CIDR, same-family only, strict literals only); an invalid rate-limit.trusted-proxies entry throws at construction and fails startup"
  - "TrustedProxyMatcher.parseLiteral also catches IllegalArgumentException alongside UnknownHostException, because JDK 17 wraps ambiguous-literal errors; matches() never throws on header content"

patterns-established:
  - "Bypass-proof rate-limit tests: send post(URI(rawUri)) so the filter sees the undecoded URI, pin a never-reused 203.0.113.x peer, and assert [not 429, not 429, 429]"

requirements-completed: [WAIT-03]

coverage:
  - id: D1
    description: "POST /api/%77aitlist and POST /api/auth/%6Cogin from one untrusted peer get 429 on request 3 at capacity 2 (CR-02 tests re-enabled, assertions unchanged)"
    requirement: "WAIT-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a percent-encoded waitlist join path is throttled like the canonical path"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a percent-encoded login path is throttled like the canonical path"
        status: pass
    human_judgment: false
  - id: D2
    description: "Spelling sweep: the three percent-encoded spellings give exactly [202, 202, 429] after one canonical join; ;x=1, trailing slash, // and /./ never yield a third 202 from one peer"
    requirement: "WAIT-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#every spelling of the join path that reaches the join handler shares the canonical per-IP bucket"
        status: pass
    human_judgment: false
  - id: D3
    description: "Behind a trusted peer, the bucket key is the rightmost untrusted hop: rotating forged leftmost hops, a trusted inner hop, and a proxy-added second header line all share the real client's bucket; a malformed hop never 5xx"
    requirement: "WAIT-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#rotating a client-controlled leftmost X-Forwarded-For hop behind a trusted proxy shares the real client bucket"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a client hop behind a trusted inner proxy hop is the bucket key"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a proxy-added second X-Forwarded-For header line is honored"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a malformed rightmost hop from a trusted peer never errors and shares one bucket"
        status: pass
    human_judgment: false
  - id: D4
    description: "TrustedProxyMatcher: ::1 trusts 0:0:0:0:0:0:0:1 and ::ffff:127.0.0.1; CIDR edges exact; no cross-family match or throw; non-literals never parsed or trusted; invalid entries fail construction; blanks skipped. The registered filter trusts the uncompressed IPv6 loopback peer"
    requirement: "WAIT-03"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt (6 tests)"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#the uncompressed IPv6 loopback peer is trusted by the default loopback entry"
        status: pass
    human_judgment: false
  - id: D5
    description: "No regression: untrusted-peer keying, single-value XFF from 127.0.0.1, confirm links and non-auth paths never throttled; 17-07 regression slice passes"
    requirement: "WAIT-03"
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.catspell.api.{waitlist,common,invite,auth,push}.* (46 classes, 252 tests, 0 failures, 0 errors, 1 skip = FcmSmokeTest)"
        status: pass
    human_judgment: false
  - id: D6
    description: "rate-limit.trusted-proxies declared in application.yml and documented in docs/CONFIGURATION.md (CIDR, rightmost rule, append/overwrite, landing-page case, unsupported shapes)"
    requirement: "WAIT-03"
    verification:
      - kind: other
        ref: "ruby YAML check prints ok; docs grep chain prints docs-ok"
        status: pass
    human_judgment: true
    rationale: "Whether the operator documentation is clear and complete for the real production proxy shape (17-VERIFICATION human_verification item 3) needs a human read"

# Metrics
duration: 105min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 09: Close the WAIT-03 per-IP rate-limit bypasses Summary

**RateLimitFilter now throttles on the decoded application path and keys trusted-proxy traffic on the rightmost untrusted X-Forwarded-For hop. A new JDK-only TrustedProxyMatcher matches trusted peers by address value or CIDR within one address family and fails startup on bad config.**

## Performance

- **Duration:** 105 min wall clock. That includes about 56 minutes when the host was asleep and the first regression-slice run stalled; active work was about 50 minutes.
- **Started:** 2026-10-02T18:30:22Z
- **Completed:** 2026-10-02T20:16:00Z
- **Tasks:** 3
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments
- CR-02 closed. `POST /api/%77aitlist`, `/%61pi/waitlist`, `/api/waitlis%74` and `POST /api/auth/%6Cogin` now draw from the canonical per-IP bucket. The waitlist join stays an exact `POST` + `/api/waitlist` match, so confirm links and preflights are still never throttled.
- CR-01 closed. Behind a trusted proxy, a client can no longer get a fresh bucket by prepending hops. The key is the rightmost hop that is not a trusted proxy, read across every `X-Forwarded-For` line. Both appending and overwriting proxies work.
- WR-09 closed. The default `::1` entry now trusts Tomcat's `0:0:0:0:0:0:0:1`. CIDR is supported. Header text never reaches DNS. An unparseable trusted-proxies entry fails startup.
- `rate-limit.trusted-proxies` is declared in `application.yml` and documented, with the supported and unsupported deployment shapes.
- All three tests that `/gsd-validate-phase` staged in `RateLimitBypassIntegrationTest` are enabled and pass with their assertions unchanged. The blob-preservation check prints nothing.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `c6cf055`.

| Task | Paths staged |
|------|--------------|
| 1: Decoded-path throttle match (CR-02) | `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`, `src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt` |
| 2: Rightmost untrusted hop + TrustedProxyMatcher (CR-01, WR-09) | `src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt`, `src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt`, `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`, `src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt` |
| 3: Declare and document rate-limit.trusted-proxies | `src/main/resources/application.yml`, `docs/CONFIGURATION.md` |
| Plan metadata | this SUMMARY, `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md` |

## TDD Evidence

Stage-only run, so RED and GREEN are recorded as test runs instead of commits.

**Task 1 (tracer, CR-02)**
- **RED:** `./gradlew test --tests "com.catspell.api.common.RateLimitBypassIntegrationTest"` exited 1 with 4 tests, 3 failed and 1 skipped (CR-01, still disabled). Each test failed at its intended assertion; no 404 guard fired:
  - `a percent-encoded waitlist join path is throttled like the canonical path`: `POST /api/%77aitlist must share the per-IP bucket of POST /api/waitlist ==> expected: <429> but was: <202>`
  - `a percent-encoded login path is throttled like the canonical path`: `POST /api/auth/%6Cogin must share the per-IP bucket of POST /api/auth/login ==> expected: <429> but was: <401>`
  - `every spelling of the join path ...`: `/api/%77aitlist must draw from the canonical POST /api/waitlist bucket, got [202, 202, 202]`
  - `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` (target_test_failed).
- **GREEN:** the same class, plus RateLimitIntegrationTest and WaitlistRateLimitIntegrationTest, exited 0. Bypass reported `tests="4" skipped="1" failures="0" errors="0"`, RateLimitIntegrationTest 10/0/0, and WaitlistRateLimitIntegrationTest 5/0/0.
- **Tracer gate:** interactive run, `end-of-phase` mode, automated-only verify. Verify passed end-to-end, so expansion continued with no checkpoint.

**Task 2 (CR-01, WR-09)**
- **Integration RED:** `./gradlew test --tests "com.catspell.api.common.RateLimitBypassIntegrationTest"` exited 1 with 8 tests and 4 failed. `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK`.
  - CR-01: `the bucket must key on the rightmost non-trusted hop (203.0.113.62), not the forged leftmost one ==> expected: <429> but was: <401>`
  - IPv6 loopback: `a different forwarded client must get its own bucket, proving the IPv6 loopback peer is trusted ==> expected: not equal but was: <429>`
  - Inner hop: `... 203.0.113.65 used as the key, got [401, 401, 401] ==> expected: <429> but was: <401>`
  - Second line: `... 203.0.113.66 is the key, got [401, 401, 401] ==> expected: <429> but was: <401>`
  - The malformed-hop guard passed before the fix, as the plan allowed.
- **Unit RED:** `./gradlew test --tests "com.catspell.api.common.TrustedProxyMatcherTest"` failed at `:compileTestKotlin` with `Unresolved reference 'TrustedProxyMatcher'`, as the plan prescribes.
- **GREEN:** the five-class slice exited 0:
  - Bypass 8/0/0/0 and TrustedProxyMatcherTest 6/0/0/0 (tests/skipped/failures/errors).
  - RateLimitTrustedProxyIntegrationTest 2/0/0, RateLimitIntegrationTest 10/0/0 and WaitlistRateLimitIntegrationTest 5/0/0 (tests/failures/errors).
  - The non-literal unit test ran in 1 ms, so `999.1.1.1` never reached DNS.
- **REFACTOR:** none needed.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt` (new):
  - `class TrustedProxyMatcher(entries: Collection<String>)` with `fun matches(address: String?): Boolean` and `companion object { fun isIpLiteral(value: String): Boolean }`.
  - Entries are parsed once into (bytes, prefix bits). Matching compares whole bytes and then the masked partial byte, only between addresses of the same length.
  - Bad entries throw `IllegalArgumentException("Invalid rate-limit.trusted-proxies entry '<entry>': ...")`.
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`:
  - `path` comes from `UrlPathHelper.defaultInstance.getPathWithinApplication(httpRequest)`.
  - A `private val trustedProxyMatcher = TrustedProxyMatcher(trustedProxies)` is built at construction.
  - `resolveClientIp` walks the hops from `getHeaders("X-Forwarded-For")` right to left.
  - The comments are rewritten. The constructor signature, registration and both `@Value` keys/defaults are unchanged.
- `src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt`:
  - All three original tests are enabled and the `Disabled` import is removed.
  - New `postLoginWithForwardedLines` helper; new peer constants .63-.67 and .70-.76.
  - New tests: the spelling sweep, plus four tests for the IPv6 loopback peer, the inner hop, the second header line and the malformed hop.
  - KDoc updated for the 6x/7x addresses.
- `src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt` (new): 6 plain JUnit 5 tests covering loopback forms, CIDR edges, the family guard, non-literals, invalid entries and blank entries.
- `src/main/resources/application.yml`: new top-level `rate-limit.trusted-proxies: "${RATE_LIMIT_TRUSTED_PROXIES:127.0.0.1,::1}"` between `email:` and `app:`.
- `docs/CONFIGURATION.md`: the `### Rate Limiting` section is rewritten (scope, decoded paths, client-IP resolution, the env var, deployment, the landing page, unsupported shapes). The Business Limits row is now `Rate limit (auth + waitlist join)`.

## Decisions Made
- Path source is `UrlPathHelper.defaultInstance.getPathWithinApplication`, as the plan chose. It is the resolver MockMvc's filter decorator uses for URL patterns, so the filter's own match and its invocation cannot disagree in tests.
- `TrustedProxyMatcher.parseLiteral` catches `IllegalArgumentException` as well as `UnknownHostException`. JDK 17's `validateNumericFormatV4` can raise IAE for ambiguous dotted forms, and `matches()` must never throw on header content.
- A malformed rightmost hop (`rl-malformed-hop`) is not a trusted proxy, so under the rightmost-untrusted rule it becomes the key. The three requests share that one bucket, which is what the test asserts. The behavior is fail-closed: no fresh bucket per request and never a 5xx.

## Deviations from Plan

None in the code: the plan was executed as written.

The only process deviation is environmental. The first Task 3 regression-slice run (20-minute background bound) stalled when the host slept. Hikari logged `clock leap detected (housekeeper delta=56m5s)`, Postgres connections were refused after resume, and no XML was written. The run was stopped at its time limit. Its orphaned Gradle test worker (PID 88391) ignored SIGTERM and was force-stopped. The slice was then rerun unchanged with a 60-minute bound and passed in 12m 56s.

## Issues Encountered
- The host-sleep stall described above. Nothing in the code was involved.

## Threat Model Coverage
- T-17-32 (decoded path), T-17-33 (rightmost untrusted hop), T-17-34 (value/CIDR family-safe matching) and T-17-35 (no DNS, no throw) are mitigated and proven by the tests listed in `coverage`.
- T-17-36 (pass-through / ip:port proxies) is transferred to the operator and documented as unsupported in docs/CONFIGURATION.md.
- T-17-37 (WR-01 unbounded bucket map) stays accepted and open in 17-REVIEW-DISPOSITION.md. This plan removes the two client-controlled key sources.
- No new endpoints, auth paths or schema changes were introduced.

## User Setup Required
None. When the app is deployed behind a reverse proxy that does not connect from loopback, set `RATE_LIMIT_TRUSTED_PROXIES` to the proxy's address or CIDR (see docs/CONFIGURATION.md). Which value is right for production is still 17-VERIFICATION human_verification item 3.

## Next Phase Readiness
- Phase 17 plan set is complete (9/9 summaries).
- These follow-ups belong to the orchestrator:
  - the unfiltered `./gradlew test` (about 14 minutes)
  - `/gsd-validate-phase 17`, so the two escalated SC3 rows flip to green
  - phase re-verification

## Self-Check: PASSED

- All 6 files_modified paths and this SUMMARY exist on disk and are staged.
- Stage-only run, so there are no commit hashes to check. `git rev-parse HEAD` is still `c6cf055f3dd91a5dfaae6c041fc11fa7ffa0d076`.
- Every task's acceptance criteria and the plan-level `<verification>` for Tasks 1-3 were re-run and pass.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*
