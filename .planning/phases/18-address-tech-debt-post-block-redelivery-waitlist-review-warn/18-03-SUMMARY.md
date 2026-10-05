---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 03
subsystem: api
tags: [rate-limit, x-forwarded-for, trusted-proxy, cidr, logback, security]

requires:
  - phase: 17-waitlist-landing-page-api
    provides: TrustedProxyMatcher (exact/CIDR trust) and the rightmost-non-trusted-hop key rule in RateLimitFilter
provides:
  - TrustedProxyMatcher.canonicalize(hop) strips brackets and a port, then re-renders the address from its parsed bytes
  - RateLimitFilter.resolveClientIp walks canonical hops right to left and falls back to remoteAddr on an unparseable hop
  - One-shot WARN (AtomicBoolean) when an untrusted peer sends X-Forwarded-For
  - Edge tests for non-octet-aligned CIDR prefixes (IPv4 /12, IPv6 /41)
  - Shared rate-limit test context also trusts the 198.51.100.0/24 test range
affects: [18-07, 18-09, 18-11]

actuals:
  tokens: 6764
  tasks: 3
  commits: 0

tech-stack:
  added: []
  patterns:
    - "Canonicalize every forwarded hop before the trust check and before it can become a bucket key; unparseable text fails safe to the peer"
    - "One-shot misconfiguration WARN guarded by AtomicBoolean.compareAndSet; never logs header values"
    - "Logback ListAppender attached to a class logger in pure-JUnit tests to assert log events"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/common/RateLimitFilterWarnTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt
    - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
    - src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt

key-decisions:
  - "Phase 18-03 (D-15, WR-02): each X-Forwarded-For hop is canonicalized (brackets and :port stripped, re-rendered from parsed bytes) before both the trust check and key selection; an unparseable hop makes the key the peer's remoteAddr"
  - "Phase 18-03: a bare IPv6 address followed by a port without brackets is treated as an address, never as address plus port"
  - "Phase 18-03 (IN-02): the shape check is private (hasIpLiteralShape); the only public entry points are matches (strict, rejects brackets) and canonicalize"
  - "Phase 18-03 (D-17, IN-05): the first untrusted peer that sends X-Forwarded-For logs one WARN naming the peer, rate-limit.trusted-proxies and RATE_LIMIT_TRUSTED_PROXIES; the guard is per filter instance"
  - "Phase 18-03: the three shared rate-limit test classes trust 198.51.100.0/24 in a byte-identical @TestPropertySource array, so tests that need their own trusted peer never share 127.0.0.1's bucket"

patterns-established:
  - "Rate-limit tests needing a dedicated trusted peer use 198.51.100.x (TEST-NET-2), which the shared test context trusts"

requirements-completed: [WAIT-03]

coverage:
  - id: D1
    description: "Forwarded hops in ip:port and [v6]:port form share the real client's bucket, and a trusted inner proxy written with a port is skipped"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a forwarded IPv4 hop with a rotating port shares the client bucket"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a forwarded bracketed IPv6 hop with a rotating port shares the client bucket"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a trusted inner proxy hop written with a port is skipped"
        status: pass
    human_judgment: false
  - id: D2
    description: "An unparseable hop falls back to the trusted peer's bucket (never 5xx; the key is the peer, not the hop text)"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt#a malformed rightmost hop from a trusted peer never errors and falls back to the peer bucket"
        status: pass
    human_judgment: false
  - id: D3
    description: "canonicalize table, null cases, strict matches on brackets, and non-octet CIDR edges (172.16.0.0/12, 2001:db8:ab00::/41)"
    requirement: WAIT-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt (10 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "One-shot WARN on the first untrusted X-Forwarded-For; none for trusted peers or requests without the header"
    requirement: WAIT-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitFilterWarnTest.kt (3 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "No regression in the per-IP limiter"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "./gradlew test --tests \"com.catspell.api.common.*\" --tests \"com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest\" (59 tests)"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-03
status: complete
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40
---

# Phase 18 Plan 03: Canonical X-Forwarded-For Hops, CIDR Edge Tests and Misconfiguration WARN Summary

**Forwarded hops are canonicalized before the trust check and key selection. `ip:port` and `[v6]:port` share the client's bucket, unparseable hops fail safe to the peer, and the first untrusted forwarded request logs one WARN.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-10-03T21:19:36Z
- **Completed:** 2026-10-03T21:44:30Z
- **Tasks:** 3
- **Files modified:** 7 (1 created, 6 modified)

## Accomplishments
- `TrustedProxyMatcher.canonicalize(hop)` accepts only these shapes: `[v6]`, `[v6]:port`, `a.b.c.d:port` (port of 1-5 digits), or a bare literal. It returns the JDK canonical text (`::1` becomes `0:0:0:0:0:0:0:1`, `::ffff:10.0.0.1` becomes `10.0.0.1`) or null. Only text that passes the shape check reaches `InetAddress`, so no DNS lookups happen.
- `RateLimitFilter.resolveClientIp` walks the canonical hops right to left with an explicit loop. A null hop returns remoteAddr (fail safe), a trusted hop is skipped, and the first other hop is the key. Rotating-port attacks and trusted hops written with a port are closed (WR-02, T-18-07, T-18-08).
- `isIpLiteral` is replaced by `private fun hasIpLiteralShape`, documented as a shape pre-filter only (IN-02).
- Non-octet CIDR edges are pinned for IPv4 `/12` and IPv6 `/41` (WR-01, T-18-09). A mutation check proved the test catches a broken mask.
- One-shot WARN for an untrusted peer that sends X-Forwarded-For. It names the peer, `rate-limit.trusted-proxies` and `RATE_LIMIT_TRUSTED_PROXIES`, and never logs the header value (IN-05, D-17, T-18-10).

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
|------|--------------|
| 1 (tracer) | src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt, src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt, src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt, src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt |
| 2 | src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt, src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt |
| 3 | src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt, src/test/kotlin/com/catspell/api/common/RateLimitFilterWarnTest.kt |
| Plan metadata | .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-03-SUMMARY.md, .planning/STATE.md, .planning/ROADMAP.md, .planning/REQUIREMENTS.md (whichever changed) |

## TDD Evidence

**Task 1 (tracer). RED came before any production change.** First I changed the three `@TestPropertySource` arrays, added the three port/bracket tests, and rewrote the malformed-hop test. Then:
- Command: `./gradlew test --tests "com.catspell.api.common.RateLimitBypassIntegrationTest"`
- The first attempt hit the known Testcontainers readiness flake: all 11 tests failed with ExceptionInInitializerError / ContainerLaunchException. That is an INVALID_RED environment failure, so it was discarded and re-run.
- Valid RED: 11 tests, 4 failed, and all 4 were the target tests failing on behavioural assertions:
  - `a forwarded IPv4 hop with a rotating port shares the client bucket`: "the port must be stripped so every connection keys on 203.0.113.68, got [401, 401, 401] ==> expected: <429> but was: <401>" (each connection got a fresh bucket)
  - `a forwarded bracketed IPv6 hop with a rotating port shares the client bucket`: "got [401, 401, 401] ==> expected: <429>"
  - `a trusted inner proxy hop written with a port is skipped`: "got [401, 401, 401] ==> expected: <429>"
  - `a malformed rightmost hop ... falls back to the peer bucket`: "the same malformed hop from another trusted peer must get that peer's bucket ... expected: not equal but was: <429>" (the key was the hop text)
- GREEN: `./gradlew test --tests RateLimitBypassIntegrationTest --tests RateLimitTrustedProxyIntegrationTest --tests WaitlistRateLimitIntegrationTest --tests RateLimitIntegrationTest --tests TrustedProxyMatcherTest` gave 34 tests, 0 failures (11 + 2 + 5 + 10 + 6). The first GREEN attempt hit the same container flake and was re-run.
- Tracer gate: interactive, `human_verify_mode` end-of-phase, `<verify>` automated only. The verify passed, so execution went straight on to expansion with no checkpoint.

**Task 2.** The behaviour under test already existed: `canonicalize` came in with Task 1, and the CIDR mask code was already correct (the review finding was a coverage gap). So the new tests passed on first run: `./gradlew test --tests "com.catspell.api.common.TrustedProxyMatcherTest"` gave 10 tests, 0 failures. As the substitute RED proof, I ran a mutation check. I changed the mask to `(0xFF shr (8 - remainingBits))` and the same command failed: 10 tests, 1 failed, `non-octet-aligned CIDR prefixes trust exactly their edges` with "the last address of 172.16.0.0/12 must be trusted ==> expected: <true> but was: <false>". I restored the file byte-for-byte from a scratch copy, and the re-run passed 10/10.

**Task 3.** I wrote RateLimitFilterWarnTest first. RED: `./gradlew test --tests "com.catspell.api.common.RateLimitFilterWarnTest"` gave 3 tests, 1 failed: `the first untrusted peer that sends X-Forwarded-For logs exactly one WARN` with "got [] ==> expected: <1> but was: <0>". The two zero-WARN tests passed, as expected before the change. GREEN: `./gradlew test --tests RateLimitFilterWarnTest --tests RateLimitIntegrationTest` gave 3 + 10 tests, 0 failures. The first GREEN attempt hit the container flake in RateLimitIntegrationTest and was re-run.

No REFACTOR step was needed.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt`: adds `canonicalize`, makes the shape check private as `hasIpLiteralShape`, and updates the KDoc
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`: fail-safe canonical hop walk, the D-15 comment, a logger, and the AtomicBoolean-guarded WARN
- `src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt`: shared-array property, three new tests, the rewritten malformed-hop test, and new constants (203.0.113.68/.69, 2001:db8::68, 198.51.100.9/.10/.11)
- `src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt`, `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt`: byte-identical `@TestPropertySource` array
- `src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt`: canonicalize table, null cases, strict-brackets check, and non-octet CIDR edges
- `src/test/kotlin/com/catspell/api/common/RateLimitFilterWarnTest.kt`: Logback ListAppender proof of the one-shot WARN

## Decisions Made
- The resolver uses an explicit loop instead of `firstOrNull { it == null || ... }`, so "found an unparseable hop" and "found no candidate" stay distinct, as RESEARCH recommends.
- The WARN check lives in the untrusted-peer branch and evaluates `getHeader("X-Forwarded-For") != null` before `compareAndSet`. A request without the header therefore never consumes the one-shot.

## Deviations from Plan

None that affect behaviour. One wording fix: in my new KDoc for RateLimitBypassIntegrationTest I first wrote "byte-identical `@TestPropertySource` arrays". The acceptance grep (`grep -h "@TestPropertySource" ... | sort -u | wc -l`) matched that comment line and printed 2. I reworded it to "byte-identical test property arrays", and the criterion then printed 1. This was a comment-only change.

## Issues Encountered
- The amd64 PostGIS image under qemu repeatedly exceeded the Testcontainers readiness wait on a cold JVM. That caused one discarded RED attempt, two GREEN re-runs and two failed plan-level runs, each one all-ExceptionInInitializerError. The Podman machine was running and had no stray containers. Every re-run passed. No Testcontainers or image configuration was changed.

## Verification
- Plan-level: `./gradlew test --tests "com.catspell.api.common.*" --tests "com.catspell.api.waitlist.WaitlistRateLimitIntegrationTest"` passed with 59 tests and 0 failures: HealthEndpoint 6, OpenApi 6, RateLimitBuckets 6, RateLimitBypass 11, RateLimitFilterWarn 3, RateLimit 10, RateLimitTrustedProxy 2, TrustedProxyMatcher 10, WaitlistRateLimit 5.
- `grep -rn "isIpLiteral" src/` is empty.
- All acceptance criteria pass: `fun canonicalize` count 1; `TrustedProxyMatcher.canonicalize` in RateLimitFilter count 1; identical-arrays check 1; the four addresses present; `private fun hasIpLiteralShape` count 1; both CIDRs present in TrustedProxyMatcherTest; `AtomicBoolean` count 2; `RATE_LIMIT_TRUSTED_PROXIES` count 2; 3 `@Test` methods, with every request built by one helper that sets `servletPath`.

## User Setup Required

None. No external service configuration is required. Operator docs for the new behaviour are in 18-11.

## Next Phase Readiness
- 18-07 (path normalization, bucket families, Caffeine store) can build on the canonical `resolveClientIp`. RateLimitFilterWarnTest already sets `servletPath`.
- 18-11 should update `docs/CONFIGURATION.md`: `ip:port` hops are now supported, an unparseable hop falls back to the peer, and the one-shot WARN exists.

## Self-Check: PASSED
- (a) All 7 source/test paths appear in `git diff --cached --name-only`. The SUMMARY and the plan metadata are staged after this file is written.
- (b) `git rev-parse HEAD` is `605ddb4d49266e805436c16b18bfc78746c46e40`.
- (c) `git diff --name-only` (unstaged changes) is empty.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-03*
