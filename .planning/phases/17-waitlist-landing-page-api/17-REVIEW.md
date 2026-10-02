---
phase: 17-waitlist-landing-page-api
reviewed: 2026-10-02T12:26:31Z
depth: standard
files_reviewed: 35
files_reviewed_list:
  - .gitignore
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
findings:
  critical: 2
  warning: 9
  info: 7
  total: 18
status: issues_found
---

# Phase 17: Code Review Report

**Reviewed:** 2026-10-02T12:26:31Z
**Depth:** standard
**Files Reviewed:** 35
**Status:** issues_found

## Summary

This is a re-review after gap-closure plans 17-07 and 17-08. 17-07 changed `RateLimitFilter` to trust `X-Forwarded-For` only when `remoteAddr` is in `rate-limit.trusted-proxies`, and added tests. 17-08 was test-only: an exact column-set test and a 20-thread per-email cap test. Each finding from the prior review was checked again against the current code.

- **CR-01 is narrowed but still open.** A direct, untrusted caller can no longer forge its bucket key. But when the request comes through a trusted proxy, the filter still uses the **leftmost** `X-Forwarded-For` entry. That entry is whatever the client sent whenever the proxy appends to the header, which is what nginx `$proxy_add_x_forwarded_for` and AWS ALB do. So the bypass still works in the proxied deployment the trust list exists for.
- **New CR-02.** The filter decides whether to throttle by comparing the raw, undecoded `requestURI`. Servlet mapping, Spring Security and Spring MVC all match on the decoded path. So `POST /api/%77aitlist` (and `/api/auth/%6Cogin`) reaches the handler with no per-IP limit at all.
- **New WR-09.** Trusted-proxy matching is an exact string compare. The shipped `::1` default never matches the form Tomcat reports for IPv6 loopback. A mismatch silently puts every client into one shared bucket.
- **Still true, no code change:** WR-01 to WR-08 and IN-01 to IN-07. WR-07 is reworded: part (b) can now be handled by configuration.

The 17-08 tests are sound. The column-set test uses a literal set, and the concurrency test checks the real capacity of 3, the count of distinct hashes, and that the stored hash is one of them.

## Critical Issues

### CR-01: Behind a trusted proxy, the per-IP key is still the client-controlled leftmost `X-Forwarded-For` entry (residual of the prior CR-01)

**File:** `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:73-83` (bug at line 80); `RateLimitFilter.kt:97-99`
**Issue:** 17-07 fixed the direct-connection case: an untrusted `remoteAddr` is always the key. When the peer *is* trusted, though, `resolveClientIp` returns `forwardedFor.split(",").first()`. Most reverse proxies append the real client address to whatever `X-Forwarded-For` the client already sent. Examples are nginx's standard `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for` and AWS ALB/ELB. So a client that sends `X-Forwarded-For: <random>` arrives as `<random>, <real-ip>`. The filter keys on `<random>` and gives the client a fresh bucket on every request.

That brings back the original attack in exactly the deployment shape that `rate-limit.trusted-proxies` is for: unlimited confirmation emails through `POST /api/waitlist`, unlimited `/api/auth/login` attempts, and unbounded growth of the `buckets` map (WR-01). The attacker can make each key up to the header size limit (about 8 KB), which makes the memory growth worse. The new tests only cover an *untrusted* peer that forges the header. `WaitlistRateLimitIntegrationTest.kt:111-121` and the other tests that use the default `127.0.0.1` peer send a single-entry header, so they never exercise a chain where the client prepends an entry.
**Fix:** Take the **rightmost entry that is not a trusted proxy**, which is what Tomcat's `RemoteIpValve` does:
```kotlin
private fun resolveClientIp(request: HttpServletRequest): String {
    val remoteAddr = request.remoteAddr
    if (!isTrusted(remoteAddr)) return remoteAddr
    val hops = request.getHeader("X-Forwarded-For")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    // Walk right-to-left: the first hop that is not one of our proxies is the real client.
    return hops.asReversed().firstOrNull { !isTrusted(it) } ?: remoteAddr
}
```
Another option is to remove the hand-rolled parsing. Set `server.forward-headers-strategy: native` plus `server.tomcat.remoteip.internal-proxies`, and key on `request.remoteAddr` only. Add a test where the trusted peer sends `X-Forwarded-For: <forged>, <real>` with a different `<forged>` value each time, and assert that the requests share one bucket.

### CR-02: The rate-limit filter matches on the raw `requestURI`, so percent-encoding the path skips the limit entirely

**File:** `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:40-47`
**Issue:** `path = httpRequest.requestURI` is the raw, undecoded request URI (Servlet spec, Tomcat `getRequestURI`). Every other layer matches on the **decoded** path:
- The servlet filter mapping `"/api/waitlist"` / `"/api/auth/*"`, so the filter is invoked.
- Spring Security's `PathPatternRequestMatcher` permitAll rules. `StrictHttpFirewall` in 7.1.0 blocks only `%25`, `%2e`, `%2f`, `%3b`, `%5c`, `%00`, `%0a` and `%0d`.
- Spring MVC's `PathPattern` handler lookup, which compares decoded segment values.

So `POST /api/%77aitlist` gets past the filter: `isWaitlistJoin` is false, and no `AUTH_PATHS` prefix matches, so the filter calls `chain.doFilter` with no throttling. The request then passes `permitAll` and reaches `WaitlistController.join`. The same trick removes the brute-force limit on `/api/auth/%6Cogin`, `/api/auth/%72egister` and the other auth paths.

The exact-equality waitlist check was added in this phase. The raw-URI prefix check for auth paths already existed, but it has the same root cause. Either way, the D-07 per-IP control can be bypassed without any header tricks. I worked this out from the Tomcat, Spring Security 7.1.0 firewall and Spring MVC path-matching code. It was not run against a live server, so a negative test is still needed.
**Fix:** Match on the same decoded, normalized path the rest of the stack uses:
```kotlin
// servletPath + pathInfo is decoded and normalized by the container (JwtAuthenticationFilter already uses servletPath)
val path = httpRequest.servletPath + (httpRequest.pathInfo ?: "")
```
You could also use `UrlPathHelper.defaultInstance.getPathWithinApplication(httpRequest)`. Add an integration test showing that `POST /api/%77aitlist` and `POST /api/auth/%6Cogin` are throttled at capacity.

## Warnings

### WR-01: Bucket maps keyed by attacker-chosen values grow without bound (memory exhaustion)

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt:47-55`; `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:25, 50`
**Issue:** Still true, with no change. `emailBuckets` keeps one `Bucket` for every distinct normalized email for the life of the process, and nothing evicts entries. `RateLimitFilter.buckets` has the same problem. Its keys are attacker-chosen through CR-01, with up to about 8 KB per key, and an IPv6 attacker can rotate through 2^64 addresses even without CR-01. The limits are also kept per instance, so "3 per email" becomes 3 × instance count, and a restart resets them.
**Fix:** Use a bounded, expiring cache, for example Caffeine `maximumSize(100_000).expireAfterAccess(Duration.ofHours(perEmailRefillHours))`, for both maps. For a multi-instance deployment, use a shared bucket4j `ProxyManager` (JDBC or Redis). Alternatively, enforce the per-email limit from `updated_at` (see WR-02).

### WR-02: The per-email limit still lets anyone send about 72 confirmation emails per day to a victim

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt:49-55, 70-86`
**Issue:** Still true. `refillIntervally(perEmailCapacity, 1h)` with defaults of 3 and 1 h restores the full capacity every hour. Every PENDING re-join rotates the token and publishes a new confirmation email, so any address that never confirms receives 3 emails/hour, or 72/day, for as long as the attacker keeps going. The new concurrency test proves only the burst cap, not the daily total.
**Fix:** Add a resend cooldown to the rotation itself, so a re-join inside the window is a silent no-op:
```kotlin
WHERE e.normalizedEmail = :normalizedEmail AND e.status = :pending
  AND (e.confirmTokenHash IS NULL OR e.updatedAt < :resendCutoff)
```
Pass `now.minus(resendCooldown)`, for example 15 minutes, and widen the bucket window, for example 3 per 24 h.

### WR-03: A double-submit or re-join kills the earlier link, and the email copy tells the user a dead link means "you're confirmed"

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt:73-86`; `src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt:26, 38`
**Issue:** Still true. Every PENDING join overwrites `confirm_token_hash`, so only the newest email works. The concurrency test confirms that up to 3 distinct tokens are mailed but only one is stored. The async sends can arrive in any order. The email says "If the page says this link was already used, your spot is still confirmed", but rotated-away, expired, unknown and reused tokens all redirect to the same error page. A user who clicks a superseded link is told they are confirmed while the entry stays PENDING, and they never receive an invite.
**Fix:** The WR-02 cooldown fixes the double-submit case. Also remove the "still confirmed" sentence, or replace it with "use the most recent email".

### WR-04: The normalizer can merge distinct mailboxes, and a PENDING re-join overwrites the delivery address, so a waitlist spot can be hijacked

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt:3-8, 12-19`; `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt:45-47`; `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt:74-81, 147`
**Issue:** Still true. The KDoc says "distinct mailboxes are never merged". But lowercasing the local part and stripping `+suffix` does merge mailboxes on providers where `+` is a literal character or local parts are case-sensitive. `rotatePendingToken` then sets `e.email = :email` to the latest variant. Someone who owns `bob+x@corp.example` can re-join while Bob is still PENDING. That redirects the entry's address and its only valid link to that person, and `convertToInvite` later mails Bob's invite to the same address (`recipient = entry.email`, line 147).
**Fix:** Keep the address from the first insert by dropping `e.email = :email` from the UPDATE, or send the confirmation only to the stored address. At minimum, correct the KDoc.

### WR-05: The invite email is sent before the invite row is flushed, and a row lock is held across external I/O

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt:141-161` (lines 151-153)
**Issue:** Still true. `InviteService.create` calls `inviteRepository.save(...)`. `Invite.id` uses `@GeneratedValue(UUID)`, so the INSERT is deferred until flush. Nothing triggers a flush after `create`, so the INSERT runs at commit, after `emailSender.send` has already delivered the code. If the flush or commit fails, the user holds a code that was never stored. The `markInvited` row lock and a pooled connection are also held for the whole synchronous provider call.
**Fix:** Call `entityManager.flush()` (or `saveAndFlush` in `InviteService.create`) before the send. Put a strict timeout on the sender, or move the send to an outbox.

### WR-06: Invite delivery failures are swallowed with no cause and no log

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt:152-159`
**Issue:** Still true. `catch (ex: Exception) { throw WaitlistInviteDeliveryException() }` drops the cause. The `ERROR` branch ignores `result.errorDetail`. Neither the service nor `GlobalExceptionHandler.handleWaitlistInviteDelivery` logs anything, so repeated 502s leave no trail on the server. The broad catch also reports bugs in the renderer as "could not be delivered".
**Fix:**
```kotlin
} catch (ex: Exception) {
    log.warn("Waitlist invite email send failed: {}", ex.javaClass.simpleName)
    throw WaitlistInviteDeliveryException(cause = ex)
}
if (result.status != EmailSendStatus.SUCCESS) {
    log.warn("Waitlist invite email was not accepted by the email provider")
    throw WaitlistInviteDeliveryException()
}
```
Add a `cause` parameter to the exception, and keep the address out of the log text.

### WR-07: The per-IP bucket is shared with `/api/auth/*`, and 429s are unreadable cross-origin

**File:** `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:49-50, 58-67, 108`; `src/main/resources/application.yml:57`
**Issue:** Still true, reworded.
- **(a) Shared bucket.** `buckets[clientIp]` is shared by the join and by register, login and refresh. Landing-page traffic from a NAT or office IP uses up that IP's login budget, and the reverse.
- **(b) Server-to-server mode.** This mode ("blank = no CORS (server-to-server only)") now works only if the operator adds the landing server's address to `rate-limit.trusted-proxies` and that server forwards the visitor IP. Nothing documents this. Without it, the whole waitlist is capped at `rate-limit.capacity` (default 10/min) globally. With it, CR-01 applies to whatever header the landing server relays.
- **(c) 429s without CORS headers.** The filter runs at `HIGHEST_PRECEDENCE`, ahead of Spring Security's `CorsFilter`, so a 429 has no `Access-Control-Allow-Origin`. The browser landing page sees an opaque failure and cannot read `Retry-After`.

**Fix:** Use a separate bucket map and capacity for the join. Document the trusted-proxy requirement for server-to-server mode. Add the CORS headers for allowed origins on the 429 path, or order the limiter after CORS processing.

### WR-08: The admin boundary depends on each handler calling the guard and has no brute-force throttling, yet it now exposes the full waitlist PII

**File:** `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt:43`; `src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt:23-31`; `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:107`
**Issue:** Still true.
- `/api/admin/waitlist/**` is `permitAll`, so any future handler under that prefix that forgets `adminTokenGuard.require(...)` is public.
- `/api/admin/*` is not in the rate-limit URL patterns.
- The guard enforces no minimum token length, so a weak `INVITE_ADMIN_TOKEN` can be brute-forced without limit. That one secret unlocks every subscriber's email (`GET /api/admin/waitlist`) and invite minting.

**Fix:** Enforce the token centrally on `/api/admin/**`, either with a `OncePerRequestFilter`/`HandlerInterceptor`, or with a Spring Security filter that grants `ROLE_OPERATOR` plus `hasRole("OPERATOR")`. Put the admin routes behind a strict per-IP limiter, and fail startup when a non-blank token is shorter than about 32 characters.

### WR-09: Trusted-proxy matching is exact-string, the `::1` default never matches, and a mismatch silently collapses all clients into one bucket

**File:** `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:22, 75, 97-99, 104`; `src/main/resources/application.yml` (key not declared)
**Issue:** `remoteAddr !in trustedProxies` compares strings. Tomcat fills `remoteAddr` from `InetAddress.getHostAddress()`, which gives IPv6 loopback in the uncompressed form `0:0:0:0:0:0:0:1`, never `::1`. So the documented `::1` default (in the code, the config default and the test KDoc) is dead. The same happens to any IPv6 proxy address an operator writes in compressed form. There is also no CIDR support, so a proxy on a container bridge network with a changing address (podman/k8s) cannot be listed reliably.

Every one of these mismatches fails the same way. The proxy is treated as an untrusted client, so every user behind it shares a single bucket of `rate-limit.capacity` requests/min for register, login, refresh and the waitlist join. Ordinary traffic then causes a site-wide self-DoS. `rate-limit.trusted-proxies` also does not appear in `application.yml`, and `docs/CONFIGURATION.md:86-90` still says the limiter applies only to `/api/auth/*`, so operators have no prompt to configure it.
**Fix:** Parse both sides to `InetAddress` and compare those, and support CIDR ranges, for example with Spring Security's `IpAddressMatcher`:
```kotlin
private val trustedMatchers = trustedProxies.map { IpAddressMatcher(it) }   // accepts "10.0.0.0/8", "::1", "127.0.0.1"
private fun isTrusted(addr: String) = trustedMatchers.any { it.matches(addr) }
```
Declare `rate-limit.trusted-proxies: ${RATE_LIMIT_TRUSTED_PROXIES:127.0.0.1,::1}` in `application.yml` and document it, including the server-to-server landing case.

## Info

### IN-01: The email copy hardcodes "expires in 7 days" while the TTL is configurable

**File:** `src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt:24, 35`
**Issue:** Still true. `WAITLIST_CONFIRM_TTL_HOURS` can be changed, but the copy always promises 7 days.
**Fix:** Inject `app.waitlist.confirm-token-ttl-hours` and render the value.

### IN-02: The class KDoc says 401 always comes before parameter validation, but type-conversion errors return 400 first

**File:** `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt:18-23, 35`
**Issue:** Still true. An unauthenticated `GET /api/admin/waitlist?limit=abc` fails argument conversion with a 400 before `require(token)` runs, which contradicts the class KDoc. The KDoc on `convert` (line 44) now admits this for a malformed UUID, but the class-level claim is unchanged.
**Fix:** Correct the class KDoc, or take `limit` as `String` and parse it after the guard. Central enforcement (WR-08) also fixes this.

### IN-03: The operator list cannot page past the first 500 rows

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt:115-119`
**Issue:** Still true. `PageRequest.of(0, limit)` always returns page 0.
**Fix:** Add an `offset`/`page` parameter, or keyset pagination on `(confirmed_at, created_at, id)`.

### IN-04: Unused repository method

**File:** `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt:99-100`
**Issue:** Still true. Nothing in `src/` calls `findByNormalizedEmail`.
**Fix:** Remove it.

### IN-05: Link building assumes the configured URL has no query string

**File:** `src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt:16`; `src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt:16`
**Issue:** Still true. `"$confirmUrl?token=$rawToken"` produces `...?a=b?token=...` when the configured URL already has a query string.
**Fix:** `UriComponentsBuilder.fromUriString(confirmUrl).queryParam("token", rawToken).build().toUriString()`.

### IN-06: The JWT skip list matches the broad prefix `/api/waitlist`

**File:** `src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt:27`
**Issue:** Still true. `startsWith("/api/waitlist")` also matches `/api/waitlistX` and any future authenticated route under `/api/waitlist/`. Valid Bearer tokens on those routes would be ignored, and every request would get a 401.
**Fix:** `path == "/api/waitlist" || path == "/api/waitlist/confirm"`.

### IN-07: A misconfigured redirect URL fails only at runtime, after the token was already claimed

**File:** `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt:50-52`
**Issue:** Still true. `URI.create(target)` runs after `confirm(token)` has committed. A malformed success URL turns a real confirmation into a JSON 400 (`handleIllegalArgument`), and the link is spent anyway.
**Fix:** Parse both URLs once in the constructor so a bad value fails at startup.

---

_Reviewed: 2026-10-02T12:26:31Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
