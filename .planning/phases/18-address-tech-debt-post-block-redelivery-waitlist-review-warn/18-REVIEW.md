---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
reviewed: 2026-10-04T00:00:00Z
depth: standard
files_reviewed: 56
files_reviewed_list:
  - build.gradle.kts
  - docs/CONFIGURATION.md
  - src/main/kotlin/com/catspell/api/auth/service/EmailChangeService.kt
  - src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt
  - src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt
  - src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt
  - src/main/kotlin/com/catspell/api/chat/service/ChatService.kt
  - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
  - src/main/kotlin/com/catspell/api/common/config/WaitlistCorsPolicy.kt
  - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
  - src/main/kotlin/com/catspell/api/common/ratelimit/RateLimitBuckets.kt
  - src/main/kotlin/com/catspell/api/common/security/AdminTokenFilter.kt
  - src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt
  - src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt
  - src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt
  - src/main/kotlin/com/catspell/api/common/security/RequestPaths.kt
  - src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt
  - src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt
  - src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt
  - src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt
  - src/main/kotlin/com/catspell/api/invite/service/InviteService.kt
  - src/main/kotlin/com/catspell/api/moderation/service/ReportService.kt
  - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt
  - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt
  - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntry.kt
  - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt
  - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt
  - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt
  - src/main/resources/application.yml
  - src/test/kotlin/com/catspell/api/TestAdminToken.kt
  - src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt
  - src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt
  - src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/common/RateLimitFilterWarnTest.kt
  - src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/common/RequestPathsTest.kt
  - src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt
  - src/test/kotlin/com/catspell/api/common/WaitlistCorsPolicyTest.kt
  - src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt
  - src/test/kotlin/com/catspell/api/email/WaitlistInviteEmailRendererTest.kt
  - src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/invite/InviteServiceTest.kt
  - src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistControllerUrlTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt
  - src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt
  - src/test/resources/application.yml
findings:
  critical: 0
  warning: 1
  info: 6
  total: 7
status: issues_found
---

# Phase 18: Code Review Report

**Reviewed:** 2026-10-04T00:00:00Z
**Depth:** standard
**Files Reviewed:** 56
**Status:** issues_found

## Summary

I reviewed the staged phase-18 delta (`git diff --cached 605ddb4`) in the context of each whole file, checked it against the locked decisions D-01..D-18 in `18-CONTEXT.md`, and followed the call sites into `MatchService`, `GlobalExceptionHandler`, `LoggingEmailSender` and `WaitlistEmailListener`.

Most of the security-relevant changes hold up when traced:

- **Admin boundary.** The central `AdminTokenFilter` is registered on `/api/admin/*`, which also matches bare `/api/admin`. It runs after `RateLimitFilter` and before Spring Security. The JWT skip list, the Spring Security `permitAll`, and the throttle all cover the same path set, so a stale Bearer header can't 401 an operator. Where the path checks disagree, they fail closed.
- **Admin token.** The startup minimum-length check never echoes the token. `MessageDigest.isEqual` takes time that depends only on the length of the provided token.
- **XFF canonicalization.** Only shape-checked text ever reaches `InetAddress`, so no DNS lookup is possible. A hop that isn't a literal falls back to the peer's bucket. A hop the client controls can never become the key, because the proxy-appended rightmost hop is read first.
- **Bucket store.** The Caffeine-backed bucket store's expire-after-access window really is lossless with interval refill.
- **Waitlist join.** The cooldown UPDATE is race-safe under the row lock. The pinned address is read with a scalar query after the bulk UPDATE.
- **Convert.** The invite row is flushed before the send. Every failure branch rolls back and logs no address or code, and the chained cause is never logged by `GlobalExceptionHandler`.

I found no blocker. One warning: the W1 guarantee in D-04, that a suppressed message "can't resurface after a rematch", only holds if the recipient happened to reconnect while the conversation was hidden. Since nothing ever sets `delivered = true` at send time, this gap affects almost every message.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Undelivered pre-block or pre-unmatch messages still resurface if the rematch comes before the recipient's next reconnect

**File:** `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt:262-309` (with `src/main/kotlin/com/catspell/api/match/service/MatchService.kt:37-46`, `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt:74-110`)

**Issue:** Hidden messages are suppressed (and marked `delivered = true`) only inside `deliverUnreadMessages`. That means only when the recipient reconnects *while* the conversation is hidden. Take this sequence: A and B chat, A blocks B (or unmatches), then they unblock and rematch (`createMatch` reactivates the same match row by clearing `endedAt`), and only after that does B reconnect. On that reconnect, `findHiddenConversationIdsForUser` no longer returns the conversation. Every pre-block message to B that is still `delivered = false` is then pushed as a `/queue/notifications` preview.

This is the normal case, not an edge case. `sendMessage` pushes live but never sets `delivered = true`; the only writes to the flag are in `deliverUnreadMessages` (lines 286 and 304). So every message B received live before the block is still undelivered and comes back as a stale preview after the rematch.

D-04's stated purpose, and the KDoc at lines 264-267 ("so they can't resurface if the pair later rematches"), is therefore only met conditionally. The new tests (`message suppressed after ... does not resurface after ... rematch`) always put a reconnect between the block and the rematch, so they don't cover this path.

**Fix:** Close the gap on the rematch path. D-05 only freezes the block/unmatch *teardown*, not reactivation. In the reactivation branch of `MatchService.createMatch`, mark that conversation's undelivered messages as delivered with one set-based UPDATE before publishing `MatchCreatedEvent`:

```kotlin
// MessageRepository
@Modifying
@Query("UPDATE Message m SET m.delivered = true WHERE m.conversation.match.id = :matchId AND m.delivered = false")
fun markAllDeliveredForMatch(@Param("matchId") matchId: UUID): Int

// MatchService.createMatch, inside `if (existing.endedAt != null) { ... }`
messageRepository.markAllDeliveredForMatch(existing.id!!)
```

(If the JPQL path through `conversation.match` won't compile as a bulk UPDATE, use `m.conversation.id IN (SELECT c.id FROM Conversation c WHERE c.match.id = :matchId)`.) Then add a test with no reconnect between the unmatch and the rematch, and correct the KDoc.

## Info

### IN-01: Per-IP limits (including the D-13 admin throttle) key on the full IPv6 address

**File:** `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:152-177`, `src/main/kotlin/com/catspell/api/common/ratelimit/RateLimitBuckets.kt:16-20`

**Issue:** An attacker who holds an IPv6 /64 gets about 2^64 independent buckets per family. The Caffeine size bound limits memory (WR-10). But heavy key churn evicts and resets the buckets of low-frequency legitimate keys (the residual T-18-05 already names this). For the admin family this is mitigated by the ≥32-character token, so it is not a practical brute force.

**Fix:** Out of this phase's scope. Consider keying IPv6 clients on their /64 prefix in a later phase, and record it alongside the deferred distributed-bucket item.

### IN-02: The "never a wildcard" CORS claim isn't enforced, and the 429 grant echoes `*`

**File:** `src/main/kotlin/com/catspell/api/common/config/WaitlistCorsPolicy.kt:22-31`, `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:118-128`

**Issue:** If `WAITLIST_ALLOWED_ORIGINS=*`, `CorsConfiguration.checkOrigin` returns `"*"`. Both Spring's `CorsFilter` and the limiter's 429 then emit `Access-Control-Allow-Origin: *`, even though the KDoc and `docs/CONFIGURATION.md` say wildcards are never allowed. This requires operator misconfiguration, and the wildcard behavior itself predates the phase.

**Fix:** In `buildConfiguration`, reject `*` (or any origin containing `*`) with `require(...)` so a bad value fails startup.

### IN-03: The confirmation email copy doesn't mention the daily cap

**File:** `src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt:66-70`

**Issue:** The email tells the user to "join the waitlist again; we send at most one new link every 15 minutes". After 3 joins in the refill window (every join counts, including no-ops inside the cooldown), further joins silently send nothing for up to 24 h. A user who follows the copy can end up with no link and no explanation.

**Fix:** Mention the cap ("at most N new links a day"), rendered from `app.waitlist.per-email-capacity` and `per-email-refill-hours` in the same way the TTL is rendered.

### IN-04: An interrupt-ignoring provider can permanently exhaust the 2-thread invite send pool

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt:69-80, 223-246`

**Issue:** After a timeout, `cancel(true)` interrupts the worker. A sender blocked in I/O with no timeout of its own keeps the thread anyway. Two such sends occupy both workers for good. Every later convert then waits in the queue until its own timeout and fails with 502, and that lasts until a restart, not just for one request. The timeout also counts time spent waiting in the queue. The docs cover late delivery, but not this pool exhaustion.

**Fix:** Add a sentence to the "Known residual" paragraph in `docs/CONFIGURATION.md` and to the KDoc. Optionally, log at WARN when a timeout leaves `activeCount == maximumPoolSize`.

### IN-05: The first inserted address wins, so a variant can squat a slot

**File:** `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt:3-11`, `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt:36-46`

**Issue:** D-08 stops a *later* variant from redirecting mail. But on providers where `+` is literal or local parts are case-sensitive, whoever inserts first owns the normalized key. An attacker who registers `alice+x@host` first will receive Alice's later confirmation links and her invite. The KDoc wording ("can never redirect") reads as absolute. The impact is low because these providers are rare and the invite is a bearer code anyway (W3).

**Fix:** Change the KDoc to "a later variant can never redirect…; the first-inserted address owns the entry", and leave the behavior as is.

### IN-06: The confirm and invite URLs are still parsed only when an email is rendered

**File:** `src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt:25-28`, `src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt:17-20`

**Issue:** IN-07 moved the redirect URLs to startup parsing, but `confirm-url` and `invite-url` are still parsed by `UriComponentsBuilder.fromUriString` on every render. A malformed value surfaces only at send time. For the join, that happens inside the async AFTER_COMMIT listener, after the token has already been rotated, so the user gets no email. For the convert, it surfaces as a 500.

**Fix:** Parse each base URL once in its constructor (`UriComponentsBuilder.fromUriString(url).build()`, which throws during bean creation), and clone it per render.

---

_Reviewed: 2026-10-04T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
