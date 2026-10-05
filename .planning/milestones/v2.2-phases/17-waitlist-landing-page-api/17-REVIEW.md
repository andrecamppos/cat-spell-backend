---
phase: 17-waitlist-landing-page-api
reviewed: 2026-10-02T20:45:19Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
  - src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt
  - src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt
  - src/main/resources/application.yml
  - docs/CONFIGURATION.md
findings:
  critical: 0
  warning: 2
  info: 5
  total: 7
status: issues_found
---

# Phase 17: Code Review Report

**Reviewed:** 2026-10-02T20:45:19Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

This is an incremental review after gap-closure plan 17-09. The scope is the six files changed by 17-09. Most of the change is staged (`git diff --cached`). The test half of `RateLimitBypassIntegrationTest.kt` was committed in c6cf055.

17-09 makes three changes:
- `RateLimitFilter` now matches on the decoded path.
- `RateLimitFilter` now picks the rightmost X-Forwarded-For hop that is not a trusted proxy, reading across every header line.
- The new JDK-only `TrustedProxyMatcher` compares trusted proxies by address bytes and supports CIDR ranges.

**CR-01, CR-02 and WR-09 are resolved.** The evidence is in the next section. No new Critical issues were found.

Checks made beyond reading the code:
- I ran `TrustedProxyMatcherTest`: 6 tests, 0 failures.
- I confirmed JDK 17.0.20 behavior with a scratch program: `::ffff:127.0.0.1` and `::ffff:7f00:1` parse to a 4-byte `Inet4Address`, and malformed IPv6-shaped text such as `:` or `1:2:3:4:5:6:7:8:9` fails fast with `UnknownHostException`, with no DNS lookup.
- `UrlPathHelper` is not deprecated in spring-web 7.0.7.
- I did not run `RateLimitBypassIntegrationTest` (Testcontainers) in this pass.

There are two Warnings:
- **WR-01:** the bit-mask branch for CIDR prefixes that are not a multiple of 8 has no test.
- **WR-02:** the chosen hop is used as the bucket key exactly as written, so the documented `ip:port` shape fails open. Each connection gets its own key.

The Info items are residual hardening and documentation gaps.

## Status of Prior Findings CR-01, CR-02, WR-09

### CR-01 (key was the client-controlled leftmost X-Forwarded-For hop): RESOLVED

`RateLimitFilter.kt:84-94`. An untrusted peer is still keyed on `remoteAddr` (line 86-88). For a trusted peer, the filter now flattens every header line in arrival order and walks from the right:
```kotlin
val hops = request.getHeaders("X-Forwarded-For")?.toList().orEmpty()
    .flatMap { line -> line.split(",") }
    .map { it.trim() }
    .filter { it.isNotEmpty() }
return hops.asReversed().firstOrNull { !trustedProxyMatcher.matches(it) } ?: remoteAddr
```
With an appending proxy, `<forged>, <real>` now keys on `<real>`. These tests cover it, and the CR-01 `@Disabled` annotation has been removed:
- `RateLimitBypassIntegrationTest.kt:140-151`: a rotating leftmost hop.
- `:169-180`: a trusted inner hop is skipped.
- `:182-193`: a second header line.

### CR-02 (raw `requestURI` let percent-encoded paths skip the limit): RESOLVED

`RateLimitFilter.kt:47` now uses `UrlPathHelper.defaultInstance.getPathWithinApplication(httpRequest)`. That path is percent-decoded, has `;` content removed and has `//` collapsed. The same `path` feeds both the waitlist exact match (line 50) and the `AUTH_PATHS` prefix check (line 51). These tests cover it, and both `@Disabled` annotations have been removed:
- `RateLimitBypassIntegrationTest.kt:100-118`: `/api/%77aitlist` and `/api/auth/%6Cogin`.
- `:120-138`: `/%61pi/waitlist` and `/api/waitlis%74` must give exactly `[202, 202, 429]` with the canonical path.

One residual hardening item remains: dot segments are not normalized by the filter. See IN-01.

### WR-09 (exact-string trust match, dead `::1` default, no CIDR, undocumented key): RESOLVED

- **Compared by value.** `TrustedProxyMatcher.kt:47-51` parses the candidate to address bytes, and `Range.contains` (lines 26-36) compares those bytes, only within one address family. `RateLimitFilter.kt:29` builds the matcher once, so a bad entry fails startup.
- **`::1` default.** `::1` now matches `0:0:0:0:0:0:0:1`. Coverage: `TrustedProxyMatcherTest.kt:19-26` and `RateLimitBypassIntegrationTest.kt:153-167`, which uses an uncompressed IPv6 loopback peer.
- **CIDR support.** Added at `TrustedProxyMatcher.kt:53-67`.
- **Config key declared.** `application.yml:38-39` now has `rate-limit.trusted-proxies`.
- **Documented.** `docs/CONFIGURATION.md:83-111` documents the key, including the server-to-server landing case (line 106). That also covers prior WR-07(b).

Two residuals remain. A misconfiguration is still silent at runtime (IN-05), and one branch of the matcher is untested (WR-01).

### Prior findings not re-verified in this pass

The other prior findings were not part of this scope. Where they touch the files reviewed here, they still hold:
- **Prior WR-01 (unbounded `buckets` map).** Still true at `RateLimitFilter.kt:26, 57`. The risk is smaller because keys behind an appending proxy are no longer client-chosen. WR-02 below and IPv6 address rotation still produce unbounded keys.
- **Prior WR-07(a) and (c).** Still true. The waitlist join and auth endpoints share one map (line 57). The filter runs at `HIGHEST_PRECEDENCE` (line 120), so a 429 carries no CORS headers.
- **Prior WR-08.** Still true. `/api/admin/*` is not in the URL patterns at line 119.
- **Everything else.** Prior WR-02 to WR-06 and IN-01 to IN-07 are in files outside this scope and were not re-checked. The earlier report is preserved in git at aa09317.

## Warnings

### WR-01: The CIDR mask branch for prefixes that are not a multiple of 8 has no test

**File:** `src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt:32-35`; `src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt:28-39, 41-49`
**Issue:** Every CIDR in the tests has a prefix that is a multiple of 8: `/16`, `/0`, and `/40` for `2001:db8::/40`. So `remainingBits` is always 0, and lines 34-35 never run:
```kotlin
val mask = (0xFF shl (8 - remainingBits)) and 0xFF
return (candidate[wholeBytes].toInt() and mask) == (network[wholeBytes].toInt() and mask)
```
The code is correct today. Traced for `172.16.0.0/12`: 172.31.x.x matches and 172.32.0.0 does not, and sign extension is handled by `and mask`. But this is the trust boundary, and common container ranges need this branch (`172.16.0.0/12`, `100.64.0.0/10`, IPv6 `/56`). If someone breaks it, for example by dropping `and 0xFF` or swapping the shift direction, the matcher could trust too much (a forgeable key) or too little (a shared bucket for everyone), and no test would fail.
**Fix:** Add edge tests for a prefix that is not a multiple of 8, in each address family:
```kotlin
@Test
fun `non-octet-aligned CIDR prefixes trust exactly their edges`() {
    val v4 = TrustedProxyMatcher(listOf("172.16.0.0/12"))
    assertTrue(v4.matches("172.16.0.0")); assertTrue(v4.matches("172.31.255.255"))
    assertFalse(v4.matches("172.15.255.255")); assertFalse(v4.matches("172.32.0.0"))
    val v6 = TrustedProxyMatcher(listOf("2001:db8:ab00::/41"))
    assertTrue(v6.matches("2001:db8:ab7f:ffff::1")); assertFalse(v6.matches("2001:db8:ab80::"))
}
```

### WR-02: The chosen hop is used as the bucket key exactly as written, so `ip:port` and other non-literal hops fail open

**File:** `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:93`; `docs/CONFIGURATION.md:108-111`; `src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt:195-204`
**Issue:** `firstOrNull { !trustedProxyMatcher.matches(it) }` returns the hop text exactly as written, and that text becomes the `buckets` key. `matches` returns false for anything that does not parse, so any non-literal hop counts as "not a trusted proxy" and is used as the key.
- **Ports.** Some front ends write the peer as `ip:port` or `[v6]:port`. Azure App Service / Application Gateway do this, and the operator cannot change it. Each new TCP connection has a new source port, so each one gets a fresh bucket. The per-IP limit on login and on the waitlist join is then bypassed completely, and the map gets a new entry per connection (prior WR-01).
- **Failure direction.** The docs (lines 110-111) list `ip:port` as "unsupported" and say to strip ports at the proxy. But every other misconfiguration in this design fails safe: an unknown proxy collapses all clients into one bucket. This one fails open.
- **Trusted hops with ports.** A trusted inner proxy written as `10.0.0.5:443` is not recognized as trusted. It is chosen as the key, so all clients behind it share that hop's bucket.
- **Spelling.** The same address in two textual spellings gives two buckets.

`a malformed rightmost hop ... shares one bucket` (test lines 195-204) confirms this: the bucket is keyed on the literal string `rl-malformed-hop`.
**Fix:**
1. Normalize each hop before both the trust check and key selection: strip `[...]` and `:port`, then canonicalize from the parsed bytes.
2. Fall back to `remoteAddr` (fail safe) when the chosen hop is still not a literal.

```kotlin
// TrustedProxyMatcher companion
fun canonicalize(hop: String): String? {
    val host = when {
        hop.startsWith("[") && "]" in hop -> hop.substring(1, hop.indexOf(']'))
        hop.count { it == ':' } == 1 -> hop.substringBefore(':')   // a.b.c.d:port
        else -> hop
    }
    if (!isIpLiteral(host)) return null
    return parseLiteral(host)?.let { InetAddress.getByAddress(it).hostAddress }
}

// RateLimitFilter.resolveClientIp
val hops = /* as today */ .map { TrustedProxyMatcher.canonicalize(it) }
val client = hops.asReversed().firstOrNull { it == null || !trustedProxyMatcher.matches(it) }
return client ?: remoteAddr   // a null (unparseable) hop also falls back to the peer
```
Then update the malformed-hop test so it expects the peer's bucket. Give that test its own peer address so it does not share the default `127.0.0.1` bucket. Add an `ip:port` test that rotates the port and expects a 429 on the third request. Update `CONFIGURATION.md:111` to match.

## Info

### IN-01: Dot-segment spellings are kept out only by Spring Security's StrictHttpFirewall, not by the filter

**File:** `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:44-47`; `src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt:62-68, 130-137`
**Issue:** `UrlPathHelper.getPathWithinApplication` decodes the path, removes `;` content and collapses `//`. It does not resolve `/./` or `/../`. So `POST /api/./waitlist` and `/api/%2e/waitlist` reach the filter as a path that is not `/api/waitlist`, and they are not throttled. Tomcat still maps them to the filter and the handler.

Today they are stopped later, because `StrictHttpFirewall` rejects paths that are not normalized, and encoded periods, with a 400. The test at lines 130-137 accepts that 400 as a pass. If the firewall is ever loosened (`setAllowUrlEncodedPeriod(true)`, a custom `HttpFirewall`), or the filter is reused on a chain without it, the CR-02 bypass returns for these spellings.
**Fix:** Key on the path the container has already normalized: `httpRequest.servletPath + (httpRequest.pathInfo ?: "")`. Tomcat decodes it, resolves dot segments, merges `//` and drops path parameters. Alternatively, apply `StringUtils.cleanPath` / `UriComponentsBuilder.fromPath(path).build().normalize()` to the `UrlPathHelper` result. Add `/api/auth/./login` to the spellings test.

### IN-02: `isIpLiteral` is public and named as a literal check, but it is only a shape pre-filter

**File:** `src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt:79-89`
**Issue:** The KDoc says it "Reports whether [value] is a strict IP literal". In fact the IPv6 branch accepts any short text made of hex digits, `:` and `.` that contains a `:`. So `isIpLiteral("1.2.3.4:5678")` and `isIpLiteral("1:2:3:4:5:6:7:8:9")` both return true. `matches` is still safe, because `parseLiteral` rejects these. But a future caller that trusts the name, for example to accept a hop without parsing it, would accept non-addresses.
**Fix:** Rename it to `hasIpLiteralShape` (or make it `private`) and reword the KDoc. Or make the public function return `parseLiteral(value) != null` after the shape check.

### IN-03: Entries in IPv4-mapped CIDR form fail startup with a misleading message, and zone-scoped peers are never trusted

**File:** `src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt:53-67, 87-89`
**Issue:** The JDK collapses `::ffff:a.b.c.d` (and `::ffff:0:0`) to a 4-byte `Inet4Address`, which I verified on 17.0.20. A dual-stack operator who writes `::ffff:10.0.0.0/104` therefore gets `maxBits = 32`, then `104 > 32`, then the error "expected an IP address or CIDR range". The entry is a valid IPv6 CIDR, so the message does not explain the failure.

Separately, Tomcat reports link-local IPv6 peers with a zone (`fe80:0:0:0:...%eth0`). `isIpLiteral` rejects `%`, so such a proxy can never be trusted. That fails safe: all clients share one bucket. It is not documented.
**Fix:** When the network address parsed to 4 bytes from text containing `:`, subtract 96 from the prefix, or reject the entry with a specific message. Strip a `%zone` suffix from the peer address before matching, or document that link-local proxies are unsupported.

### IN-04: The configuration docs omit the new variable from the places operators check first

**File:** `docs/CONFIGURATION.md:14-24, 85-89, 178`
**Issue:**
- The Environment Variables table (lines 14-24) does not list `RATE_LIMIT_TRUSTED_PROXIES` or `RATE_LIMIT_CAPACITY`.
- The Production row (line 178) lists only the JWT, DB and S3 overrides. Yet any deployment behind a proxy on another host or container must set the trusted proxies, or every client shares one bucket.
- The Rate Limiting snippet (lines 85-89) is presented as `application.yml` content. It shows `capacity: 10`, which is not in `application.yml`; 10 is the `@Value` default at `RateLimitFilter.kt:107`. The snippet also shows the value unquoted, while the real file quotes it (line 39).

**Fix:** Add both variables to the table. Add "`RATE_LIMIT_TRUSTED_PROXIES` when behind a reverse proxy" to the Production row. Make the snippet match `application.yml`, or label `capacity` as a code default.

### IN-05: A trusted-proxy misconfiguration is still silent at runtime (residual of WR-09)

**File:** `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:84-88`
**Issue:** When the peer is not trusted, any X-Forwarded-For it sends is ignored without a trace. If an operator forgets to set `RATE_LIMIT_TRUSTED_PROXIES` behind a proxy, every user shares one bucket of `rate-limit.capacity` requests per minute for login, register and the waitlist join. The first sign is user-facing 429s. The new docs explain the failure but give no signal at runtime.
**Fix:** Log once at WARN, guarded by an `AtomicBoolean`, the first time an untrusted peer sends `X-Forwarded-For`. Name the peer address and the config key. A one-shot log cannot be used for log flooding.

---

_Reviewed: 2026-10-02T20:45:19Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
