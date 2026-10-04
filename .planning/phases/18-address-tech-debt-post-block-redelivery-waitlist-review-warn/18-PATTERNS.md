# Phase 18: Address tech debt: post-block redelivery + waitlist review warnings - Pattern Map

**Mapped:** 2026-10-03
**Files analyzed:** 22 (4 new, 18 modified)
**Analogs found:** 21 / 22

All paths are relative to `src/main/kotlin/com/catspell/api/` unless they say otherwise. Every analog was checked as git-tracked.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `common/ratelimit/RateLimitBuckets.kt` (NEW) | utility | in-memory cache | `waitlist/service/WaitlistService.kt:45-55` (bucket factory) | role-match |
| `common/security/RequestPaths.kt` (NEW) | utility | transform | `RateLimitFilter.kt:44-47` (path derivation) | partial |
| `common/security/AdminTokenFilter.kt` + config (NEW) | middleware | request-response | `common/security/RateLimitFilter.kt` (Filter + FilterRegistrationBean) | exact |
| `inviteSendExecutor` bean (NEW, config) | config | async | none in repo (`applicationTaskExecutor` is Boot's default) | none |
| `common/security/RateLimitFilter.kt` | middleware | request-response | itself | self |
| `common/security/TrustedProxyMatcher.kt` | utility | transform | itself | self |
| `common/security/JwtAuthenticationFilter.kt` | middleware | request-response | itself (skip list :16-29) | self |
| `common/security/AdminTokenGuard.kt` | utility/guard | validation | itself | self |
| `common/config/SecurityConfig.kt` | config | — | itself (CORS :39-43, :76) | self |
| `chat/model/ConversationRepository.kt` | repository | CRUD/query | `ConversationRepository.kt:11` + `moderation/model/BlockRepository.kt:10-14` | exact |
| `chat/service/ChatService.kt` | service | event-driven (push) | itself `:262-292` | self |
| `waitlist/model/WaitlistEntryRepository.kt` | repository | CRUD | itself (`rotatePendingToken` :36-46, claim queries :59-63) | self |
| `waitlist/service/WaitlistService.kt` | service | CRUD + email | itself | self |
| `waitlist/controller/WaitlistController.kt` | controller | request-response | itself (`:24-27`, `:50-52`) | self |
| `waitlist/controller/WaitlistAdminController.kt`, `invite/controller/InviteAdminController.kt` | controller | request-response | itself (KDoc only) | self |
| `email/service/WaitlistConfirmEmailRenderer.kt`, `WaitlistInviteEmailRenderer.kt` | utility | transform | itself | self |
| `invite/service/InviteService.kt` | service | CRUD | itself `:40` | self |
| `common/exception/Exceptions.kt` | model | — | itself `:49` | self |
| `auth/service/{EmailVerification,EmailChange,PasswordReset}Service.kt`, `moderation/service/ReportService.kt` | service | — | `WaitlistService.kt:45-55` | exact |
| `waitlist/service/WaitlistEmailNormalizer.kt`, `waitlist/model/WaitlistEntry.kt` | KDoc only | — | — | — |
| `build.gradle.kts`, `src/main/resources/application.yml`, `docs/CONFIGURATION.md` | config/docs | — | existing `bucket4j-core` line / `${ENV:default}` keys | exact |
| Tests (see Shared Patterns: Testing) | test | — | `src/test/kotlin/com/catspell/api/common/RateLimit*IntegrationTest.kt`, `TrustedProxyMatcherTest.kt` | exact |

## Pattern Assignments

### `common/ratelimit/RateLimitBuckets.kt` (utility, NEW)

**Analog:** `waitlist/service/WaitlistService.kt` lines 45-55. Every service repeats this exact pattern; replace each copy with the helper.
```kotlin
private val emailBuckets = ConcurrentHashMap<String, Bucket>()

private fun emailBucket(normalizedEmail: String): Bucket = emailBuckets.computeIfAbsent(normalizedEmail) {
    val bandwidth = Bandwidth.builder()
        .capacity(perEmailCapacity)
        .refillIntervally(perEmailCapacity, Duration.ofHours(perEmailRefillHours))
        .build()
    Bucket.builder().addLimit(bandwidth).build()
}
```
How to change it: the helper takes `(capacity: Long, refill: Duration, maximumSize: Long)` and wraps `Caffeine.newBuilder().maximumSize(max).expireAfterAccess(refill).build<String, Bucket>()`. It exposes `fun bucketFor(key: String): Bucket = cache.get(key) { newBucket() }`. Use access-based expiry, never write-based (RESEARCH Pattern 2). For tests, add an optional `ticker`/`executor` constructor parameter.
Call sites: `WaitlistService.kt:47-55`, `EmailVerificationService.kt:35-43`, `EmailChangeService.kt:42-50`, `PasswordResetService.kt:34-42`, `ReportService.kt:32-40`, `RateLimitFilter.kt:26,57,96-102`.

---

### `common/security/AdminTokenFilter.kt` (middleware, NEW)

**Analog:** `common/security/RateLimitFilter.kt`

**Imports** (lines 1-19): `jakarta.servlet.Filter`, `FilterChain`, `ServletRequest/Response`, `HttpServletRequest/Response`, `FilterRegistrationBean`, `@Value`, `@Bean`, `@Configuration`, `Ordered`.

**Plain class, not `@Component`** (line 21), with registration in a sibling `@Configuration` (lines 105-122):
```kotlin
@Configuration
class RateLimitFilterConfig(
    @Value("\${rate-limit.capacity:10}") private val capacity: Long,
    ...
) {
    @Bean
    fun rateLimitFilterRegistration(): FilterRegistrationBean<RateLimitFilter> {
        val registration = FilterRegistrationBean(RateLimitFilter(capacity, trustedProxies))
        registration.addUrlPatterns("/api/auth/*", "/api/waitlist")
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE)
        return registration
    }
}
```
For the admin filter, use `addUrlPatterns("/api/admin/*")` and `setOrder(Ordered.HIGHEST_PRECEDENCE + 10)`, and inject `AdminTokenGuard`.

**Guard reuse** (`AdminTokenGuard.kt:23-31`): call `adminTokenGuard.require(request.getHeader("X-Admin-Token"))` and catch `AdminAuthException`.

**401 body** (`ProblemDetailAuthenticationEntryPoint.kt:10-17`, `internal`, same package):
```kotlin
internal fun writeUnauthorized(response: HttpServletResponse, detail: String) {
    if (response.isCommitted) return
    response.status = HttpServletResponse.SC_UNAUTHORIZED
    response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    response.writer.write("""{"title":"Unauthorized","status":401,"detail":"$detail"}""")
}
```
Call it with `detail = "Not authorized"`, which matches `AdminAuthException` (Exceptions.kt:43).

---

### `common/security/AdminTokenGuard.kt` (D-13 minimum length)

Add an `init { require(adminToken.isBlank() || adminToken.length >= 32) { "app.invite.admin-token must be at least 32 characters" } }` to the existing `@Value("\${app.invite.admin-token:}")` constructor (lines 17-20). This follows the same fail-at-construction idea as `RateLimitFilter.kt:28-29` (`TrustedProxyMatcher` built once so a bad entry fails startup). Every test using `test-admin-secret` (17 characters) must switch to a shared constant of 32 or more characters.

---

### `common/security/RateLimitFilter.kt` (self-modification)

- **Path** (lines 44-47): replace `UrlPathHelper.defaultInstance.getPathWithinApplication(httpRequest)` with `RequestPaths.normalized(httpRequest)`.
- **Families** (lines 50-55): `isWaitlistJoin` already exists. Add an `/api/admin` prefix check and select one of three `RateLimitBuckets` (auth / waitlist-join / admin).
- **429 writer** (lines 64-73): add `Access-Control-Allow-Origin`, `Vary: Origin` and `Access-Control-Expose-Headers: Retry-After` when the `Origin` is in the waitlist allowed origins, taken from the same source as `SecurityConfig.kt:39-43`.
- **resolveClientIp** (lines 85-95): map each hop through `trustedProxyMatcher.canonicalize(hop)`. If a hop returns null, return `remoteAddr`. Inside the untrusted branch, add an `AtomicBoolean` one-shot `log.warn` when the request has an `X-Forwarded-For` header.
- **Config** (lines 105-122): add `@Value("\${rate-limit.waitlist-capacity:...}")` and `@Value("\${rate-limit.admin-capacity:5}")`, and add the `"/api/admin/*"` URL pattern.

### `common/security/JwtAuthenticationFilter.kt`

Skip list at lines 16-29 (`val path = request.servletPath`, `path.startsWith("/api/waitlist") || ...`). Read the path from `RequestPaths.normalized(request)` instead, use exact matches for `/api/waitlist` and `/api/waitlist/confirm`, and add a `/api/admin/` prefix. This is required: MockMvc leaves `servletPath` as `""`, so the skip list cannot be tested otherwise (see WaitlistEnumerationSafetyIntegrationTest.kt:61-62).

### `common/security/RequestPaths.kt` (NEW)

An `object` or top-level `internal fun normalized(request: HttpServletRequest): String = request.servletPath + (request.pathInfo ?: "")`. The current approach, the `UrlPathHelper` call at `RateLimitFilter.kt:47`, is what this replaces. Keep the comment style from lines 44-46 that explains the bypasses.

---

### `chat/model/ConversationRepository.kt` (W1 query)

**Analog JPQL** (line 11):
```kotlin
@Query("SELECT c FROM Conversation c JOIN ConversationParticipant cp ON cp.conversation = c WHERE cp.user.id = :userId AND c.match.endedAt IS NULL ORDER BY c.lastMessageAt DESC NULLS LAST")
fun findConversationsByUserId(@Param("userId") userId: UUID): List<Conversation>
```
**Bidirectional block predicate** (`moderation/model/BlockRepository.kt:10-14`): `(b.blocker.id = :a AND b.blocked.id = :bId) OR (b.blocker.id = :bId AND b.blocked.id = :a)`.
New query: `findHiddenConversationIdsForUser(userId): List<UUID>`. It selects `c.id` where `c.match.endedAt IS NOT NULL OR EXISTS (SELECT 1 FROM Block b WHERE ` the predicate over `c.match.user1.id` / `c.match.user2.id` `)`.

### `chat/service/ChatService.kt` `deliverUnreadMessages` (lines 262-292)

Keep the existing `@Transactional`, `findByUserId`, and `findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc` flow. Partition the results by `msg.conversation.id in hiddenIds`. For hidden messages, set `delivered = true` and `save` without calling `convertAndSendToUser`. Return the pushed count. Leave `MatchService.endMatch` untouched (D-05).

---

### `waitlist/model/WaitlistEntryRepository.kt`

- `rotatePendingToken` (lines 36-46): currently `UPDATE WaitlistEntry e SET e.email = :email, e.confirmTokenHash = :hash, e.confirmTokenExpiresAt = :expiresAt, e.updatedAt = :now ...`. Remove `e.email = :email` and its parameter. Add `AND (e.confirmTokenHash IS NULL OR e.updatedAt <= :resendCutoff)`.
- Copy the KDoc style of the claim queries (lines 59-63, "evaluated under the row lock") for the cooldown comment.
- Add `findStoredEmail(normalizedEmail): String?`. Delete `findByNormalizedEmail` (lines 99-100), which has no callers.

### `waitlist/service/WaitlistService.kt`

- Join (lines 66-87): the bucket check comes first (unchanged). Compute `resendCutoff = now.minus(cooldown)`. When `rotated == 1`, publish `WaitlistConfirmationRequestedEvent(storedEmail, rawToken)`.
- Config: `@Value("\${app.waitlist.per-email-refill-hours:1}")` at line 40 changes its default to 24. Add `app.waitlist.resend-cooldown-minutes:15` and `app.waitlist.invite-send-timeout-ms:10000`.
- Convert (lines 141-161): render the email outside the try. Submit `emailSender.send` to `inviteSendExecutor` and call `future.get(timeout)`. Handle Timeout, Execution and Interrupted exceptions, plus a non-`SUCCESS` `EmailSendStatus` (EmailSender.kt:10-16). In each case, `log.warn` without the address and throw `WaitlistInviteDeliveryException(cause = ex)`.

### `common/exception/Exceptions.kt` line 49

`class WaitlistInviteDeliveryException(message: String = "Invite email could not be delivered", cause: Throwable? = null) : RuntimeException(message, cause)`. `GlobalExceptionHandler.kt:102-109` stays unchanged.

### `invite/service/InviteService.kt` line 40

Change `inviteRepository.save(Invite(...))` to `inviteRepository.saveAndFlush(Invite(...))`.

### Email renderers

- `WaitlistConfirmEmailRenderer.kt`: remove the sentence at :26 and :38, and replace "7 days" at :24/:35 with the TTL from `@Value("\${app.waitlist.confirm-token-ttl-hours:168}")`. Change `"$confirmUrl?token=$rawToken"` at :16 to `UriComponentsBuilder.fromUriString(confirmUrl).queryParam("token", rawToken).build().toUriString()`.
- `WaitlistInviteEmailRenderer.kt`: make the same change at :16 for `code`.

### `waitlist/controller/WaitlistController.kt`

The constructor `@Value` params at :24-27 become `private val successUri: URI = URI.create(confirmSuccessUrl)`, built once. Replace the per-request `URI.create(target)` at :50-52.

### `common/security/TrustedProxyMatcher.kt`

Add `fun canonicalize(hop: String): String?`, which strips `[...]` and `:port`, then calls `parseLiteral` and re-renders the result. Rename `isIpLiteral` to a private `hasIpLiteralShape`. Use the sketch from the current `17-REVIEW.md` WR-02 as the starting point.

## Shared Patterns

### Config binding
**Source:** `RateLimitFilter.kt:105-110`, `WaitlistService.kt:40`. Use `@Value("\${key:default}")` constructor params only, never `@ConfigurationProperties`. Mirror each key in `application.yml` as `${ENV_VAR:default}` and add a row in `docs/CONFIGURATION.md`.

### Fail at startup
**Source:** `RateLimitFilter.kt:28-29`. Validate or parse in the constructor or `init`, so a bad config value fails bean creation. Apply this to AdminTokenGuard (minimum length), WaitlistController URIs, and RateLimitBuckets parameters.

### Problem+JSON written by filters
**Source:** `RateLimitFilter.kt:64-73` (429) and `ProblemDetailAuthenticationEntryPoint.kt:10-17` (401). Write the raw JSON string with `MediaType.APPLICATION_PROBLEM_JSON_VALUE` and `{title,status,detail}`.

### Filter registration and ordering
Filters are plain classes registered with `FilterRegistrationBean`, not `@Component`. The order is RateLimitFilter (`HIGHEST_PRECEDENCE`), then AdminTokenFilter (`+10`), then Spring Security (-100).

### No PII in logs
Log the exception's `javaClass.simpleName` or a fixed message. Never log an email address, `errorDetail`, or the admin token.

### Testing
Extend these tests:
- `src/test/kotlin/com/catspell/api/common/RateLimitIntegrationTest.kt`, `RateLimitBypassIntegrationTest.kt` (add `/api/auth/./login`), `RateLimitTrustedProxyIntegrationTest.kt` (malformed hop now expects the peer's bucket; add a rotating-port test).
- `TrustedProxyMatcherTest.kt` (add the `172.16.0.0/12` and `2001:db8:ab00::/41` edge tests).
- The Phase 13 block-enforcement integration tests (W1 reconnect test).
- Every waitlist and admin test that uses `test-admin-secret`, and every waitlist test whose outcome the cooldown changes (see RESEARCH Validation Architecture).

Integration tests use Testcontainers on Podman. mockk is the test-double library.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `inviteSendExecutor` `ThreadPoolTaskExecutor` bean | config | async | The repo defines no custom executor. Use RESEARCH Pattern 4 step 6 (core 1, max 2, queue 10, graceful shutdown), and do not reuse `applicationTaskExecutor`. |

## Metadata

**Analog search scope:** `src/main/kotlin/com/catspell/api/{common,chat,moderation,waitlist,invite,email,auth}`
**Files scanned:** 9 read directly, plus file:line facts verified in RESEARCH.md
**Pattern extraction date:** 2026-10-03
