# Phase 18: Address tech debt: post-block redelivery + waitlist review warnings - Research

**Researched:** 2026-10-03
**Domain:** Spring Boot 4 / Kotlin hardening: STOMP redelivery filtering, Bucket4j + Caffeine eviction, servlet-filter ordering (rate limit, admin guard, JWT, CORS), X-Forwarded-For canonicalization, transactional email I/O
**Confidence:** HIGH for code facts and library behavior checked in the cached sources; MEDIUM for a few Hibernate/Kotlin compile details marked [ASSUMED]

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Scope
- **D-01:** Close **all open warning-level findings** listed in the Phase Boundary, plus the two dropped warnings. Re-add the dropped ones under **new IDs** (suggested: WR-10 unbounded bucket maps, WR-11 per-email daily volume) so the earlier IDs aren't reused again.
- **D-02:** Info-level items: take only the cheap ones listed in the Phase Boundary, plus the stale-Bearer verification. Pagination, IPv4-mapped CIDR and zone-scoped peers get disposition `deferred`, with the reason in the Source cell.
- **D-03:** The bucket-map eviction fix covers **every** service using the unbounded `ConcurrentHashMap<String, Bucket>` pattern: `WaitlistService`, `EmailVerificationService`, `EmailChangeService`, `PasswordResetService`, and `RateLimitFilter`. Use one shared helper, not four copies.

#### W1: Redelivery on reconnect
- **D-04:** In `deliverUnreadMessages`, an undelivered message in a hidden conversation (`match.endedAt != null`, or `isBlockedEitherWay` for the pair) is **skipped and marked `delivered = true`**. No preview is pushed, and it can't resurface if the pair later rematches (the same match row is reactivated, see `MatchService.kt:41-46`). The message row itself is kept as server-side evidence (Phase 13 D-04/D-07). — **Reversibility:** costly — afterwards, a suppressed message can't be told apart from one that was pushed; the `delivered` flag is the only record.
- **D-05:** The fix lives **only on the reconnect path**. Block/unmatch teardown (`MatchService.endMatch`) is unchanged. Resolve hidden conversations in a set-based way (one query or join), not one block lookup per conversation.
- **D-06:** No additional block re-check at push time for a send-vs-block race. The send path already rejects a blocked or ended pair before saving.

#### Waitlist re-join and hijack (WR-03, WR-04, dropped WR-02 → WR-11)
- **D-07:** **Resend cooldown + daily cap.** A PENDING re-join inside a configurable cooldown (default 15 min) returns the same enumeration-safe `202` and does nothing: no token rotation, no email. The per-email bucket widens to 3 per 24 h. Both values are configurable `app.waitlist.*` keys (`@Value`, no `@ConfigurationProperties`). This refines Phase 17 D-04: outside the cooldown, a PENDING re-join still rotates the token and sends a fresh email.
- **D-08:** **Pin the first-stored address.** A re-join never changes `waitlist_entries.email`. Drop `e.email = :email` from the rotate UPDATE. The fresh confirm link and the later invite both go to the address stored at first insert. D-03 normalization from Phase 17 (trim + lowercase + strip `+suffix`) stays as is. Correct the `WaitlistEmailNormalizer` KDoc, which claims distinct mailboxes are never merged.
- **D-09:** **Confirm email copy:** remove the false "your spot is still confirmed" sentence. Say that only the most recent email's link works, and that joining again sends a fresh one (subject to the cooldown). Render the configured TTL instead of a hardcoded "7 days" (first-review IN-01). Keep **one** error URL; the landing-page redirect contract doesn't change.

#### Waitlist convert I/O (WR-05, WR-06)
- **D-10:** **Flush, send, roll back.** Flush the invite row (`saveAndFlush` or an explicit flush) before the email is sent. The send stays synchronous inside the transaction, so a provider failure rolls back INVITED and the invite row and returns the existing 502, which the operator can retry. Give the send a strict timeout. Log the failure at WARN with the exception type and no email address. Add a `cause` parameter to `WaitlistInviteDeliveryException` and chain it. Also handle the `EmailSendStatus` non-success branch, logging without the address.

#### Admin boundary (WR-08, stale-Bearer item)
- **D-11:** **Enforce the admin token centrally** on `/api/admin/**` with a `OncePerRequestFilter` or `HandlerInterceptor` that reuses the existing constant-time, deny-by-default `AdminTokenGuard` check, so any future handler is protected automatically. Remove the per-handler `require(...)` calls or keep them as defense in depth (Claude's discretion). This also makes the 401 come before parameter conversion (first-review IN-02).
- **D-12:** Add `/api/admin` to the JWT filter's skip list, so a stale or invalid Bearer header can't 401 the operator before the admin check runs. First reproduce the failure with a test.
- **D-13:** **Throttle + minimum length.** Put a strict per-IP bucket on `/api/admin/**` (e.g. 5/min, configurable). **Fail startup** when `app.invite.admin-token` is non-blank and shorter than 32 characters. Blank still means deny all. — **Reversibility:** costly — any deploy with a short `INVITE_ADMIN_TOKEN` won't start until the token is rotated; document this in `docs/CONFIGURATION.md`.

#### Rate limiter (WR-07, current WR-02, current IN-01, IN-05)
- **D-14:** **Separate join bucket + CORS on 429.** `POST /api/waitlist` gets its own bucket map and capacity key, apart from `/api/auth/*`. The 429 response includes `Access-Control-Allow-Origin` (and the headers needed to expose `Retry-After`) for origins in `WAITLIST_ALLOWED_ORIGINS`, so the landing page can read it. Document that server-to-server mode requires the landing server in `RATE_LIMIT_TRUSTED_PROXIES`, forwarding the visitor IP.
- **D-15:** Current WR-02: use the review's suggested fix. Canonicalize each X-Forwarded-For hop (strip `[...]` and `:port`, re-render from the parsed bytes) before both the trust check and key selection. A hop that still isn't a literal **falls back to `remoteAddr`** (fail safe). Update the malformed-hop test to expect the peer's bucket, add a rotating-port test, and update `docs/CONFIGURATION.md`.
- **D-16:** Current IN-01: match throttled paths on the container-normalized path (`servletPath + pathInfo`, or an equivalent normalization), not only `UrlPathHelper`. Add `/api/auth/./login` to the spellings test.
- **D-17:** Current IN-05: log a WARN **once** (guarded by an `AtomicBoolean`) the first time an untrusted peer sends X-Forwarded-For, naming the peer address and the config key.

#### Bucket eviction (dropped WR-01 → WR-10)
- **D-18:** **Add Caffeine** (`com.github.ben-manes.caffeine:caffeine`, version managed by the Spring Boot BOM, no explicit version) as the backing store for every bucket map in D-03: `maximumSize` plus `expireAfterAccess` equal to that bucket's refill window. The user approved this new dependency for this phase. — **Reversibility:** reversible — it sits behind one shared helper.

### Claude's Discretion
- Exact config key names and defaults: cooldown, daily cap, admin bucket capacity, join bucket capacity, Caffeine max size, send timeout. Follow the existing `app.*` / `rate-limit.*` + `@Value` conventions, and declare each key in `application.yml` and `docs/CONFIGURATION.md`.
- Filter vs interceptor for D-11, and its ordering relative to `RateLimitFilter` and the JWT filter.
- How the send timeout is applied for D-10 (sender-level or call-level).
- Cheap first-review one-liners that also got dropped from the disposition file: IN-04 (remove the unused `findByNormalizedEmail`) and IN-05 (build links with `UriComponentsBuilder`). Include them if trivial; either way, give them a disposition under new IDs.
- How the remaining "cheap" items are implemented: exact-match JWT skip list (`/api/waitlist`, `/api/waitlist/confirm`), redirect URLs parsed in the constructor so a bad value fails at startup, `isIpLiteral` renamed or made private, and the docs env-var table and Production row.
- Plan and wave grouping. A natural split: W1 chat fix / waitlist service + email / rate limiter + trusted proxy / admin boundary / shared eviction helper + disposition bookkeeping.
- Disposition bookkeeping format: new IDs for the dropped findings, `fixed` with the plan reference, and `deferred` with a reason for out-of-scope items.

### Deferred Ideas (OUT OF SCOPE)
- **W2:** an endpoint that lets legacy accounts without a DOB set one. It's a new capability and needs its own phase.
- **W3:** binding a waitlist-conversion invite to the waitlist email. By design (bearer code, INV-04).
- **Production reverse-proxy shape check:** set `RATE_LIMIT_TRUSTED_PROXIES` / `WAITLIST_ALLOWED_ORIGINS` at deploy time (deferred UAT test 3).
- **Invite email in a real client:** UAT follow-up at deploy.
- **First-review IN-03:** operator waitlist list pagination past 500 rows.
- **Current-review IN-03:** a clearer startup error for IPv4-mapped CIDR entries, and support for zone-scoped (link-local) peers.
- **Shared/distributed buckets** (bucket4j `ProxyManager` over JDBC/Redis) for multi-instance deployments. Caffeine bounds memory per instance only.
</user_constraints>

<phase_requirements>
## Phase Requirements

ROADMAP.md maps no requirement IDs to Phase 18 (`Requirements: TBD`). The phase adds no capability. Each fix hardens a requirement that is already shipped. No new REQ-IDs are invented here.

| Fix (finding) | Existing requirement it strengthens | Research support |
|---|---|---|
| W1 reconnect redelivery skips hidden conversations | MOD-02 (block enforced on every surface), MOD-03 (blocked conversation locked) | Pattern 1, Code Example 1 |
| WR-10 bounded bucket maps (Caffeine) | WAIT-03 (per-IP + per-email limiting); also the v2.1 per-email limits on verify/reset/change-email | Pattern 2, Code Example 2 |
| WR-11 + WR-03 cooldown, 3 per 24 h, honest copy | WAIT-02 (double opt-in), WAIT-03 | Pattern 3, Code Example 3 |
| WR-04 pin the first address | WAIT-01, WAIT-04 (the invite goes to the right person) | Pattern 3 |
| WR-05 / WR-06 flush, timeout, log, chain cause | WAIT-04 | Pattern 4, Code Example 4 |
| WR-08 + IN-02 (first) + stale Bearer: central admin guard, throttle, minimum token length | INV-02, WAIT-04 (operator-only boundary) | Pattern 5, Code Example 5 |
| WR-07 separate join bucket, CORS on 429 | WAIT-01, WAIT-03 | Pattern 6, Code Example 6 |
| Current WR-02 hop canonicalization, WR-01 CIDR tests, IN-01 path normalization, IN-02 rename, IN-05 one-shot WARN | WAIT-03 | Patterns 6-7, Code Examples 6-7 |
| IN-06 exact JWT skip, IN-07 URLs parsed at startup, IN-01 (first) TTL in copy, IN-04/IN-05 (first) | WAIT-01, WAIT-02 | Pattern 3, Pattern 5 |
</phase_requirements>

## Summary

All the work is in existing code. The only new dependency is Caffeine. The Spring Boot 4.0.6 BOM manages it at **3.2.3**, so `implementation("com.github.ben-manes.caffeine:caffeine")` needs no version [VERIFIED: spring-boot-dependencies-4.0.6.pom line 41: `<caffeine.version>3.2.3</caffeine.version>`]. No Flyway migration is needed. `waitlist_entries.updated_at` already exists and is written on insert and on every rotation, so it can carry the resend cooldown [VERIFIED: V24__create_waitlist_entries.sql:17 `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),`; WaitlistEntryRepository.kt:45-46 `UPDATE WaitlistEntry e SET e.email = :email, e.confirmTokenHash = :hash, e.confirmTokenExpiresAt = :expiresAt, e.updatedAt = :now`].

Three facts drive the plan more than any library choice:
1. **MockMvc never sets `servletPath`.** The builder leaves it `""` and puts the decoded URI in `pathInfo` [VERIFIED: spring-test-7.0.7-sources AbstractMockHttpServletRequestBuilder.java:108 `private String servletPath = "";` and :976-986]. `JwtAuthenticationFilter.shouldNotFilter` reads only `request.servletPath`, so its skip list is dead in every MockMvc test. A MockMvc test of the stale-Bearer fix (D-12) cannot pass unless the filter reads `servletPath + pathInfo`. The repo already documents the problem at WaitlistEnumerationSafetyIntegrationTest.kt:61-62: "MockMvc leaves it empty, so the skip list cannot be exercised through MockMvc". One shared `RequestPaths.normalized(request)` helper, used by `RateLimitFilter` and `JwtAuthenticationFilter`, fixes D-12 and D-16 and makes both testable.
2. **Five test classes use the 17-character admin token `test-admin-secret`.** D-13's startup check would stop all of their contexts, and the deny-by-default classes would keep working. Every occurrence must move to one shared ≥32-character constant.
3. **The cooldown changes the result of several existing waitlist tests.** Re-join rotation, the "overwrites email" assertion and the 20-thread "exactly 3 emails" assertion all change. Inside the cooldown they now see exactly one email. The full list of affected tests is under Validation Architecture.

**Primary recommendation:** build a shared `RateLimitBuckets` helper (Caffeine-backed) and a `RequestPaths` helper first, because later waves depend on them. Then make the waitlist, convert, admin, rate-limiter and W1 changes in parallel waves. Close with docs and the disposition update.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|---|---|---|---|
| Hidden-conversation suppression on reconnect (W1) | API / Backend (`ChatService`) | Database (one set-based JPQL query) | The reconnect path is server-side. The DB resolves the hidden set in one query. |
| Per-key bucket storage and eviction | API / Backend (in-process Caffeine) | — | Buckets are per instance (distributed buckets are deferred). |
| Resend cooldown, address pinning | Database (conditional UPDATE under the row lock) | API (`WaitlistService`) | Race-free only when the DB evaluates the cutoff atomically. |
| Invite flush, send timeout, rollback | API (`WaitlistService` transaction) | Database (flush) | The transaction boundary owns the rollback. |
| Admin token enforcement, admin throttle | API: servlet filter tier (ahead of Spring Security) | — | It must run before JWT, MVC argument conversion and handlers. |
| CORS headers on 429 | API: servlet filter tier (`RateLimitFilter`) | Spring Security CORS config (single source) | The 429 is written before Spring Security's `CorsFilter` runs. |
| Client-IP resolution and path normalization | API: servlet filter tier | — | Runs at `HIGHEST_PRECEDENCE`, before the firewall. |
| Operator docs | Docs (`docs/CONFIGURATION.md`) | — | D-13 and D-14 require operator-facing text. |

## Project Constraints (from AGENTS.md and the user's global rules)

There is no `./CLAUDE.md` and no project skills directory. These directives apply:
- **Containers are Podman, not Docker.** Testcontainers finds the Podman socket through `build.gradle.kts` (`podman machine inspect ...`). Never assume Docker.
- **Config binding is `@Value` only.** No `@ConfigurationProperties` and no config processor. New keys go into `application.yml` as `${ENV:default}` and into `docs/CONFIGURATION.md`. The IDE "Unknown property" warning is cosmetic.
- **Test command:** `./gradlew test`. Integration tests start their own containers.
- **Never auto-commit. Stage only** (user rule; overrides GSD commit steps). Executors use `git add`, never `git commit`.
- **Never add a dependency without confirmation.** Caffeine is pre-approved by D-18. Nothing else may be added.
- **Read the repository's real APIs before calling them.** Do not invent signatures (user thumb-rule). This research quotes every in-repo signature it relies on.
- **Execute-phase runs sequentially, with no worktrees** (user memory note).
- Kotlin style follows the existing repository. The user's Java guideline about an `f` field prefix does not apply to this Kotlin codebase, which does not use it.

## Standard Stack

### Core (already present)
| Library | Version | Purpose | Why Standard |
|---|---|---|---|
| Spring Boot | 4.0.6 | Web, security, JPA, WebSocket | Project stack [VERIFIED: build.gradle.kts `id("org.springframework.boot") version "4.0.6"`] |
| Spring Security | 7.1.0 (overrides the BOM's 7.0.5) | Filter chain, CORS, firewall | [VERIFIED: build.gradle.kts `extra["spring-security.version"] = "7.1.0"`; BOM line 199 `<spring-security.version>7.0.5</spring-security.version>`] |
| Tomcat embed | 11.0.21 | Servlet container; it normalizes the request path | [VERIFIED: BOM line 209 `<tomcat.version>11.0.21</tomcat.version>`] |
| Hibernate ORM | 7.2.12.Final | JPA | [VERIFIED: BOM line 69] |
| Bucket4j core | 8.10.1 | Token buckets | [VERIFIED: build.gradle.kts `implementation("com.bucket4j:bucket4j-core:8.10.1")`] |
| mockk | 1.13.11 | Test doubles (the project's choice; not Mockito) | [VERIFIED: build.gradle.kts] |

### New
| Library | Version | Purpose | When to Use |
|---|---|---|---|
| `com.github.ben-manes.caffeine:caffeine` | 3.2.3 (BOM-managed; Central `<release>` is 3.3.0) | Bounded, expiring `Cache<String, Bucket>` behind one helper | Every per-key bucket map (D-03, D-18) |

**Installation** (`build.gradle.kts` dependencies block):
```kotlin
implementation("com.github.ben-manes.caffeine:caffeine")   // version from the Spring Boot 4.0.6 BOM (3.2.3)
```
Transitive compile dependencies: `org.jspecify:jspecify:1.0.0` and `com.google.errorprone:error_prone_annotations:2.43.0` [VERIFIED: caffeine-3.2.3.pom on repo1.maven.org]. Do not pin 3.3.0. The BOM pin keeps Spring Boot's own Caffeine integrations consistent.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|---|---|---|
| Caffeine `Cache<String, Bucket>` | `bucket4j-caffeine` `ProxyManager` | That is a second new artifact and an extra abstraction for no gain in one process. Rejected (D-18 approves Caffeine only). |
| Hand-rolled LRU + TTL map | — | Concurrency, atomic compute and eviction correctness are hard to get right. Don't hand-roll. |

**Version verification:** Maven Central lists `3.2.3` (HTTP 200 on `caffeine-3.2.3.pom`); the latest release is `3.3.0`, last updated 2026-09-21 [VERIFIED: repo1.maven.org maven-metadata.xml].

## Package Legitimacy Audit

The `gsd-tools package-legitimacy check` seam supports only `npm|pypi|crates`. For `--ecosystem maven` it returned `Error: Usage: gsd-tools package-legitimacy check --ecosystem <npm|pypi|crates>`. So there is no seam verdict. Manual evidence is below.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---|---|---|---|---|---|---|
| `com.github.ben-manes.caffeine:caffeine` 3.2.3 | Maven Central | Long-established (in Spring Boot BOMs for years; BOM-pinned in 4.0.6) | Not available from Central | github.com/ben-manes/caffeine (SCM in the POM) | No seam verdict (Maven unsupported); manually confirmed via the Spring Boot BOM (authoritative) + Central POM | Approved by the user in D-18; no checkpoint needed beyond D-18 |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none
Maven has no postinstall-script equivalent, so the npm postinstall check does not apply.

## Architecture Patterns

### System Architecture Diagram (HTTP request path after Phase 18)

```
HTTP request
   │
   ▼
Tomcat (CoyoteAdapter: strip ;params → percent-decode → normalize //, /./, /../ ; ../ above root → 400)
   │  servletPath + pathInfo = normalized path
   ▼
[1] RateLimitFilter  (FilterRegistrationBean, order HIGHEST_PRECEDENCE; url patterns /api/auth/*, /api/waitlist, /api/admin/*)
   │  path = RequestPaths.normalized(req)
   │  key  = canonical client IP (untrusted peer → remoteAddr [+ one-shot WARN if it sent XFF];
   │                              trusted peer → rightmost canonicalized non-trusted hop; unparseable → remoteAddr)
   │  bucket family: AUTH (rate-limit.capacity) | WAITLIST_JOIN (rate-limit.waitlist-capacity) | ADMIN (rate-limit.admin-capacity)
   │  ├─ exhausted → 429 problem+json + Retry-After (+ ACAO/Vary/ACEH when the waitlist Origin is allowed)  ──► response
   │  └─ ok → continue
   ▼
[2] AdminTokenFilter (FilterRegistrationBean, order HIGHEST_PRECEDENCE + 10; url pattern /api/admin/*)
   │  AdminTokenGuard.require(X-Admin-Token)  ── fail → 401 problem+json "Not authorized" ──► response
   ▼
[3] springSecurityFilterChain (order -100): StrictHttpFirewall → CorsFilter → JwtAuthenticationFilter
   │  (skip list now exact paths, incl. /api/admin/**, read from RequestPaths.normalized) → authorization (permitAll / authenticated)
   ▼
DispatcherServlet → controller (admin handlers may keep require() as defense in depth)
```

WebSocket reconnect path (W1):
```
STOMP CONNECT ──► SessionConnectedEvent ──► WebSocketSessionListener (@Async, sleep 200 ms)
   ──► ChatService.deliverUnreadMessages(userId) [@Transactional]
         ├─ undelivered = messages(delivered=false, sender≠user, user's conversations)
         ├─ hidden      = ONE query: user's conversation ids whose match.endedAt≠null OR a block exists either way
         ├─ hidden msgs  → delivered = true, NO push
         └─ visible msgs → convertAndSendToUser(user, "/queue/notifications", ChatNotification) ; delivered = true
```

### Recommended Project Structure (new and changed files only)
```
src/main/kotlin/com/catspell/api/
├── common/ratelimit/RateLimitBuckets.kt        # NEW shared Caffeine-backed per-key bucket helper (D-03/D-18)
├── common/security/RequestPaths.kt             # NEW normalized-path helper (D-16, D-12 testability)
├── common/security/AdminTokenFilter.kt         # NEW central /api/admin/* guard + its FilterRegistrationBean config (D-11)
├── common/security/RateLimitFilter.kt          # 3 bucket families, canonical hops, CORS on 429, WARN once
├── common/security/TrustedProxyMatcher.kt      # canonicalize(), isIpLiteral → private hasIpLiteralShape
├── common/security/JwtAuthenticationFilter.kt  # exact skip list incl. /api/admin, RequestPaths
├── common/security/AdminTokenGuard.kt          # init-time min-length check (D-13)
├── common/config/SecurityConfig.kt             # CORS config shared with RateLimitFilter (single source)
├── chat/model/ConversationRepository.kt        # findHiddenConversationIdsForUser (W1)
├── chat/service/ChatService.kt                 # deliverUnreadMessages partition (W1)
├── waitlist/model/WaitlistEntryRepository.kt   # cooldown + no email overwrite; findStoredEmail; drop findByNormalizedEmail
├── waitlist/service/WaitlistService.kt         # cooldown, stored address, flush/timeout/log in convert, RateLimitBuckets
├── waitlist/controller/WaitlistController.kt    # URIs parsed in the constructor (IN-07)
├── email/service/WaitlistConfirmEmailRenderer.kt # TTL rendered, honest copy, UriComponentsBuilder
├── email/service/WaitlistInviteEmailRenderer.kt  # UriComponentsBuilder
├── invite/service/InviteService.kt             # saveAndFlush
└── auth/service/{EmailVerification,EmailChange,PasswordReset}Service.kt, moderation/service/ReportService.kt  # RateLimitBuckets
```

### Pattern 1: W1, set-based hidden-conversation resolution on reconnect

**Current code** [VERIFIED: ChatService.kt:262-292]:
```kotlin
@Transactional
fun deliverUnreadMessages(userId: UUID): Int {
    val participations = conversationParticipantRepository.findByUserId(userId)
    val conversationIds = participations.mapNotNull { it.conversation.id }
    if (conversationIds.isEmpty()) return 0
    val undelivered = messageRepository.findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc(
        conversationIds, userId
    )
    for (msg in undelivered) { ... messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", ChatNotification(...)); msg.delivered = true; messageRepository.save(msg) }
    return undelivered.size
}
```
**Entity facts the query needs** [VERIFIED]:
- `Conversation.match` is `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "match_id", nullable = false, unique = true) var match: Match` (Conversation.kt:15-17).
- `Match` has `user1`, `user2` and `@Column(name = "ended_at") var endedAt: Instant? = null` (Match.kt:15-27).
- `Block` has `blocker` and `blocked` (Block.kt:15-21).
- The existing JPQL style is `JOIN ConversationParticipant cp ON cp.conversation = c WHERE cp.user.id = :userId AND c.match.endedAt IS NULL` (ConversationRepository.kt:11).
- The bidirectional predicate is `WHERE (b.blocker.id = :a AND b.blocked.id = :bId) OR (b.blocker.id = :bId AND b.blocked.id = :a)` (BlockRepository.kt:10-14).

**What:** add one repository query that returns the ids of the user's hidden conversations. Partition the undelivered list against that set. Mark the hidden messages `delivered = true` without pushing. This is set-based: one extra query, no per-conversation block lookup (D-05).

**Facts that make it safe:**
- A block always ends the match (`blockRepository.save(...)` then `matchService.endMatch(blockerId, blockedId, "BLOCK")`, BlockService.kt:34-40). So in practice "blocked" implies `endedAt != null`. Keep both predicates anyway, as D-04 requires (defense in depth).
- A rematch reuses the same match row: `if (existing.endedAt != null) { existing.endedAt = null; existing.endedReason = null; matchRepository.save(existing) ... }` (MatchService.kt:41-46). The conversation is unique per match, so it is also reused. That is why the hidden messages must be marked `delivered = true` on the first suppressed reconnect.
- **Return value.** The listener ignores it: `chatService.deliverUnreadMessages(uuid)` (WebSocketSessionListener.kt:24). Recommendation: return the **pushed** count. A service-level test can then assert `0` for a hidden conversation with no STOMP client.

**Pre-existing behavior, out of scope.** `sendMessage` pushes the live notification but never sets `delivered = true` (ChatService.kt:75-109). So every message is re-pushed on the recipient's next reconnect, including ones already seen live. Do not change this in Phase 18. Note it in the summary only.

### Pattern 2: one shared Caffeine-backed bucket helper (D-03, D-18)

**Every unbounded map in the repo** [VERIFIED]:

| File:line | Map | Capacity key | Refill |
|---|---|---|---|
| `waitlist/service/WaitlistService.kt:47-55` | `emailBuckets` | `app.waitlist.per-email-capacity:3` | `refillIntervally(perEmailCapacity, Duration.ofHours(perEmailRefillHours))`, `app.waitlist.per-email-refill-hours:1` → D-07 changes the default to 24 |
| `auth/service/EmailVerificationService.kt:35-43` | `emailBuckets` | `app.resend-verification.per-email-capacity:3` | `app.resend-verification.per-email-refill-hours:1` (hours) |
| `auth/service/EmailChangeService.kt:42-50` | `emailBuckets` | `app.change-email.per-email-capacity:3` | `app.change-email.per-email-refill-hours:1` (hours) |
| `auth/service/PasswordResetService.kt:34-42` | `emailBuckets` | `app.forgot-password.per-email-capacity:3` | `app.forgot-password.per-email-refill-hours:1` (hours) |
| `common/security/RateLimitFilter.kt:26, 57, 96-102` | `buckets` (per IP) | `rate-limit.capacity:10` | `refillIntervally(capacity, Duration.ofMinutes(1))` |
| `moderation/service/ReportService.kt:32-40` | `reporterBuckets` (per user UUID) | `app.report.per-reporter-capacity:5` | `app.report.per-reporter-refill-hours:1` |

`ReportService` uses the same pattern but is **not** named in D-03. Its keys are authenticated user ids, so they are bounded by the number of users and carry low risk. **Recommendation:** migrate it too. It is a one-line change with the shared helper, and it matches D-03's "every service using the pattern" intent. Record it in the SUMMARY. `PresenceRegistry` maps are session maps, not buckets, and are out of scope.

**Why `expireAfterAccess` equal to the refill window is lossless.** With `refillIntervally`, a bucket untouched for one full window has refilled completely at the next interval boundary. Evicting it and later recreating a fresh, full bucket changes nothing [ASSUMED: derived from Bucket4j's "intervally" refill semantics; matches the first review's WR-01 fix sketch]. Use **access**-based expiry, not write-based. With `expireAfterWrite`, an attacker hammering one key would get the bucket evicted mid-window and reset, which fails open.

**Caffeine API facts** [CITED: Cache.java v3.2.3 Javadoc, github.com/ben-manes/caffeine]:
- `V get(K key, Function<? super K, ? extends V> mappingFunction)`. "The entire method invocation is performed atomically, so the function is applied at most once per key." This replaces `computeIfAbsent` one-for-one.
- `Cache<K, V extends @Nullable Object>` is `@NullMarked` (jspecify). With `V = Bucket` (non-null), Kotlin sees `get(...)` as returning `Bucket`.
- `expireAfterAccess`: "Expire entries after the specified duration has passed since the entry was last accessed by a read or a write" [CITED: Caffeine wiki, Eviction].
- Deterministic tests: use `Caffeine.ticker(Ticker)` for time, `Caffeine.executor(Runnable::run)` for same-thread maintenance, and `Cache.cleanUp()` [CITED: Caffeine wiki, Testing].
- Size eviction uses W-TinyLFU. Frequently hit keys, such as an attacker's throttled key, are favored over one-hit flood keys. Under extreme key churn, a throttled key can still be evicted and reset. This is documented residual risk; per-instance limits are already accepted (deferred: distributed buckets).

### Pattern 3: waitlist cooldown, pinned address, honest copy (D-07, D-08, D-09)

**Current join** [VERIFIED: WaitlistService.kt:66-87]:
```kotlin
val trimmed = email.trim()
val normalized = WaitlistEmailNormalizer.normalize(email)
if (!emailBucket(normalized).tryConsume(1)) return
val now = Instant.now()
waitlistEntryRepository.insertIfAbsent(trimmed, normalized, now)
val rawToken = generateRawToken()
val rotated = waitlistEntryRepository.rotatePendingToken(normalized, trimmed, hashToken(rawToken), now.plus(confirmTokenTtlHours, ChronoUnit.HOURS), now, WaitlistStatus.PENDING)
if (rotated == 1) { eventPublisher.publishEvent(WaitlistConfirmationRequestedEvent(trimmed, rawToken)) }
```
**Changes:**
1. **Rotate UPDATE.** Drop `e.email = :email` and its `email` parameter (D-08). Add `AND (e.confirmTokenHash IS NULL OR e.updatedAt <= :resendCutoff)`, with `resendCutoff = now.minus(cooldown)`.
   - A fresh insert has `confirm_token_hash = NULL`, because `insertIfAbsent` writes only `id, email, normalized_email, status, created_at, updated_at` (WaitlistEntryRepository.kt:25-27). So the first join always rotates.
   - Use `<=` so that a cooldown of 0 behaves as "always rotate". That makes a zero cooldown usable in a test property if it is ever needed.
2. **Under concurrency** the cooldown makes the result exactly one email. Postgres READ COMMITTED re-checks the `WHERE` clause on the updated row version after a blocked UPDATE waits on the row lock. Once the first rotation commits, `confirm_token_hash IS NOT NULL` and `updated_at ≈ now`, so every waiting UPDATE matches 0 rows [ASSUMED: standard PostgreSQL EvalPlanQual re-check; the existing claim queries already rely on it (WaitlistEntryRepository.kt:59-63, "evaluated under the row lock")].
3. **Send to the stored address.** After `rotated == 1`, read it back with `@Query("SELECT e.email FROM WaitlistEntry e WHERE e.normalizedEmail = :normalizedEmail") fun findStoredEmail(...): String?`. Publish `WaitlistConfirmationRequestedEvent(storedEmail, rawToken)`.
   - `insertIfAbsent` still writes `trimmed` only on the first insert.
   - Then delete the unused `findByNormalizedEmail` (WaitlistEntryRepository.kt:99-100; grep finds no caller in `src/`; first-review IN-04).
4. **Bucket order is unchanged.** `tryConsume` still runs first. A no-op re-join inside the cooldown still uses a per-email token. This tightens the worst case further (an attacker burning the 3/24 h budget leaves the victim at most 3 emails per day). The cost: a user who double-submits and then asks again hours later may be silent until the window refills. Accept this. It is simplest and matches D-07's "does nothing" externally. **[Planner: confirm, or refund with `bucket.addTokens(1)` when `rotated == 0` because of the cooldown. Refunding cannot tell "terminal state" apart from "cooldown" without a second query, so keep it simple.]** RESOLVED: kept, no refund (Open Question 2, applied in 18-06).
5. **New keys.** `app.waitlist.resend-cooldown-minutes: ${WAITLIST_RESEND_COOLDOWN_MINUTES:15}`. `per-email-refill-hours` default goes **1 → 24** in `application.yml` (`${WAITLIST_PER_EMAIL_REFILL_HOURS:24}`) and in the `@Value` default (`app.waitlist.per-email-refill-hours:24`).
6. **Comments and KDoc.**
   - Fix `WaitlistEntry.kt:14`: "Delivery address: trimmed, case preserved, overwritten on a PENDING re-join."
   - Fix the `rotatePendingToken` KDoc (WaitlistEntryRepository.kt:36-41).
   - Fix the `WaitlistEmailNormalizer` KDoc. Its "distinct mailboxes are never merged" claim is false; state that `+`-literal and case-sensitive providers can be merged, and that the stored address is pinned.
   - **Do NOT edit the comment in `V24__create_waitlist_entries.sql`.** It says "last submitted while PENDING", but changing an applied migration changes its Flyway checksum and breaks `validate` on existing databases.
7. **Copy (D-09).** Inject `@Value("\${app.waitlist.confirm-token-ttl-hours:168}")` into `WaitlistConfirmEmailRenderer`. Render it as `N days` when `hours % 24 == 0`, otherwise `N hours`. The sentence to remove appears twice, quoted verbatim [VERIFIED: WaitlistConfirmEmailRenderer.kt:26 and :38]: `Some email security tools open links automatically. If the page says this link was already used, your spot is still confirmed.` The hardcoded TTL appears at :24 (`expires in 7 days.`) and :35. Suggested replacement copy: "Only the link in the most recent email from us works. If you need a new one, join the waitlist again; we send a fresh link at most once every few minutes." Exact wording is the planner's choice. Keep the single error URL.
8. **Links (first-review IN-05).** Build links with `UriComponentsBuilder.fromUriString(url).queryParam("token", raw).build().toUriString()`, in both renderers. The current form is `"$confirmUrl?token=$rawToken"` at :16; the invite renderer has `"$inviteUrl?code=$rawCode"` (WaitlistInviteEmailRenderer.kt:16). Probe results, spring-web 7.0.7 [VERIFIED: jshell run this session]:
   - `catspell://register` → `catspell://register?token=AbC-_9`
   - `http://localhost:8080/api/waitlist/confirm` → `...confirm?token=AbC-_9`
   - `https://x.example/c?a=b` → `https://x.example/c?a=b&token=AbC-_9`

   The existing test assertion `"$INVITE_URL?code=$code"` with `INVITE_URL = "catspell://register"` therefore still holds.
9. **Redirect URLs (IN-07).** `WaitlistController` takes `confirmSuccessUrl` and `confirmErrorUrl` and calls `URI.create(target)` per request (WaitlistController.kt:24-27, :50-52). Make them plain constructor params and parse them into `private val successUri: URI = URI.create(...)` (and an error URI) once, at construction. A bad value then fails at startup.

### Pattern 4: convert, flush first, bounded send, chained cause (D-10)

**Current code** [VERIFIED: WaitlistService.kt:141-161; InviteService.kt:40 `inviteRepository.save(Invite(codeHash = hashToken(rawCode), referrerUserId = referrerUserId))`; Exceptions.kt:49 `class WaitlistInviteDeliveryException(message: String = "Invite email could not be delivered") : RuntimeException(message)`].

**The email sender reality.** The only `EmailSender` in `src/main` is `LoggingEmailSender`, `@ConditionalOnProperty(name = ["email.enabled"], havingValue = "false", matchIfMissing = true)` [VERIFIED: LoggingEmailSender.kt:9]. `.env.example` says "EMAIL_ENABLED=true activates a real email provider (deferred; not yet built)". There is **no** `spring-boot-starter-mail` and no SMTP or HTTP provider. So a sender-level timeout (for example the `mail.smtp.*timeout` properties) has nothing to configure today.

**Recommendation: a call-level timeout in `WaitlistService.convertToInvite`.**
1. `InviteService.create` uses `inviteRepository.saveAndFlush(...)`. That also benefits `InviteAdminController`, and a flush failure surfaces before any email.
2. **Render outside the try**: `val message = waitlistInviteEmailRenderer.render(recipient, rawCode)`. A renderer bug then becomes a 500 with rollback, not a misleading "could not be delivered" (review WR-06 point).
3. Submit `emailSender.send(message)` to a dedicated small executor. Call `future.get(timeout)`.
   - On `TimeoutException`: `future.cancel(true)`, then `log.warn("Waitlist invite email send timed out after {} ms", ...)`, then throw `WaitlistInviteDeliveryException(cause = ex)`.
   - On `ExecutionException`: unwrap `ex.cause`, log `ex.cause.javaClass.simpleName`, throw with that cause.
   - On `InterruptedException`: restore the interrupt flag, then throw.
4. On a non-SUCCESS `EmailResult` (`enum class EmailSendStatus { SUCCESS, ERROR }`, `errorDetail: String?`; EmailSender.kt:10-16): `log.warn("Waitlist invite email was not accepted by the email provider")`. Do not log `errorDetail`, which may embed the address. Throw.
5. Exception: `class WaitlistInviteDeliveryException(message: String = "Invite email could not be delivered", cause: Throwable? = null) : RuntimeException(message, cause)`. `GlobalExceptionHandler.handleWaitlistInviteDelivery` (:102-109) keeps returning 502 and needs no change.
6. **Executor.** Use a `ThreadPoolTaskExecutor` bean, for example `inviteSendExecutor` with core 1, max 2, queue 10, and graceful shutdown. Spring manages its lifecycle. Do **not** reuse `applicationTaskExecutor`, which carries the `@Async` push and email listeners. Queueing behind them would cause spurious timeouts.
7. **Key.** `app.waitlist.invite-send-timeout-ms: ${WAITLIST_INVITE_SEND_TIMEOUT_MS:10000}`.
8. **Residual to document.** A send that times out may still complete later on the worker thread. The user would then receive a code that was rolled back. Redeeming it fails with the generic invite-required 403, and the operator's retry sends a valid code. Note this in CONFIGURATION.md or in the KDoc.
9. **Future-proofing (KDoc only).** Any real `EmailSender` added later must also bound its own I/O. For an SMTP starter, Spring Boot's `spring.mail.properties.mail.smtp.connectiontimeout`, `timeout` and `writetimeout` [ASSUMED: standard JavaMail properties; not verified this session because no mail starter is on the classpath].

### Pattern 5: central admin boundary (D-11, D-12, D-13)

**Filter, not interceptor (recommended).** A servlet `Filter` registered with `FilterRegistrationBean` on `/api/admin/*`:
- runs **before** Spring Security (order -100), so neither the JWT filter nor MVC argument conversion can answer first [VERIFIED: spring-boot-security-4.0.6-sources SecurityFilterProperties.java:49 `public static final int DEFAULT_FILTER_ORDER = OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 100;`]
- covers unmapped future paths (`GET /api/admin/anything` gets 401, not 404)
- is matched by Tomcat on the normalized path
- follows the same registration idiom as `RateLimitFilter` (RateLimitFilter.kt:114-122)

A `HandlerInterceptor` would run after the security chain, so a stale Bearer would still 401 first, and it only protects mapped handlers.

**Ordering:** RateLimitFilter (`Ordered.HIGHEST_PRECEDENCE`, unchanged) → AdminTokenFilter (`Ordered.HIGHEST_PRECEDENCE + 10`) → Spring Security (-100).
- The throttle runs **before** the token check, so wrong-token guesses are throttled.
- Make the filter a plain class, **not** `@Component`. Spring Boot auto-registers every `Filter` bean for `/*`, and the JWT filter is an example: it is a `@Component OncePerRequestFilter`, so it is registered twice and the second pass is skipped by OncePerRequestFilter's marker.

**401 body.** Reuse the existing `internal fun writeUnauthorized(response: HttpServletResponse, detail: String)` [VERIFIED: ProblemDetailAuthenticationEntryPoint.kt:10-17] with `detail = "Not authorized"`. That matches `AdminAuthException(message: String = "Not authorized")` (Exceptions.kt:43) and `handleAdminAuth` → `ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Not authorized")`, title `"Unauthorized"` (GlobalExceptionHandler.kt:85-91). Existing tests assert only `jsonPath("$.title").value("Unauthorized")` (WaitlistAdminIntegrationTest), so the filter-written body (no `type`/`instance`) passes. Inside the filter, call `adminTokenGuard.require(header)` and catch `AdminAuthException`, so the guard stays the single constant-time check [VERIFIED: AdminTokenGuard.kt:23-31 uses `MessageDigest.isEqual`].

**Per-handler `require(...)`:** recommend **keeping** the calls as defense in depth. They cost little and still protect the handlers if the registration's URL pattern is ever broken. Rewrite the class KDocs in `WaitlistAdminController` (:18-23) and `InviteAdminController` (:16-20), which say the guard "is the WHOLE / ENTIRE access-control boundary", to describe the central filter (first-review IN-02).

**D-12 skip list.** The current list is `path.startsWith("/api/waitlist") || ...`, with `val path = request.servletPath` [VERIFIED: JwtAuthenticationFilter.kt:16-29].
- Change the source to `RequestPaths.normalized(request)`.
- Make the waitlist entries exact: `path == "/api/waitlist" || path == "/api/waitlist/confirm"` (IN-06).
- Add `path == "/api/admin" || path.startsWith("/api/admin/")`.

Admin routes must still pass authorization: SecurityConfig.kt:41-43 already has `.permitAll()` for `/api/admin/invites`, `/api/admin/waitlist` and `/api/admin/waitlist/**`. Recommend broadening to `requestMatchers("/api/admin/**").permitAll()`. Otherwise a future `/api/admin/x` route that passes the admin filter reaches `anyRequest().authenticated()` and gets a JWT 401. With the central filter in front, permitAll on the whole prefix is safe.

**Reproduce first (D-12).** With MockMvc, `servletPath = ""`, so **today** any Bearer on any path is validated. `GET /api/admin/waitlist` with `X-Admin-Token: <correct>` and `Authorization: Bearer garbage` returns **401 "Invalid or expired token"** from `JwtAuthenticationFilter.writeUnauthorized` [VERIFIED: code reading of JwtAuthenticationFilter.kt:36-57 + AbstractMockHttpServletRequestBuilder.java:976-986]. Write that test, watch it fail, then fix. Also add a direct filter-bean test with `servletPath = "/api/admin/waitlist"`, mirroring `runJwtFilter` (WaitlistEnumerationSafetyIntegrationTest.kt:64-71), to prove the production-shape path.

**D-13 minimum length.** Add a Kotlin `init {}` block in `AdminTokenGuard`. It throws `IllegalStateException("app.invite.admin-token must be blank (operator endpoints disabled) or at least 32 characters")` when `adminToken.isNotBlank() && adminToken.length < 32`. Never include the token in the message. Bean creation fails, so the context fails. Test it with `ApplicationContextRunner().withUserConfiguration(AdminTokenGuard::class.java).withPropertyValues("app.invite.admin-token=short").run { assertThat(it).hasFailed() }`, the same runner pattern as `EmailSenderSelectionTest`. That needs no Testcontainers and takes milliseconds.

**D-13 throttle.** Add a third bucket family in `RateLimitFilter` for `path == "/api/admin" || path.startsWith("/api/admin/")`, with key `rate-limit.admin-capacity: ${RATE_LIMIT_ADMIN_CAPACITY:5}` per minute per client IP. Add `"/api/admin/*"` to `registration.addUrlPatterns(...)` (currently `"/api/auth/*", "/api/waitlist"`, RateLimitFilter.kt:119). `WaitlistRateLimitIntegrationTest` asserts the registered patterns, so extend that assertion.

### Pattern 6: rate limiter, separate join bucket, CORS on 429, canonical hops (D-14, D-15, D-17)

**Separate join bucket.** Today `buckets.computeIfAbsent(clientIp)` serves both auth and the waitlist join (RateLimitFilter.kt:57). Use three `RateLimitBuckets` instances: AUTH (`rate-limit.capacity`), WAITLIST_JOIN (`rate-limit.waitlist-capacity: ${RATE_LIMIT_WAITLIST_CAPACITY:10}`) and ADMIN. Each has a one-minute window, and that window is its `expireAfterAccess`.
- Also declare `rate-limit.capacity: ${RATE_LIMIT_CAPACITY:10}` in `application.yml`. Today the key exists only as the `@Value("\${rate-limit.capacity:10}")` default (RateLimitFilter.kt:107); current-review IN-04 points this out.
- Keep a no-arg-compatible constructor, because `RateLimitIntegrationTest` does `private val rateLimitFilter = RateLimitFilter()` (RateLimitIntegrationTest.kt:25). Give every new parameter a default.

**CORS on 429.** The CORS policy is built in `SecurityConfig.corsConfigurationSource()`: `allowedOrigins = origins; allowedMethods = listOf("POST"); allowedHeaders = listOf("Content-Type"); allowCredentials = false; maxAge = 3600L`, registered for `"/api/waitlist"` only when origins is non-empty (SecurityConfig.kt:64-79).
- Extract the construction into one small component, for example `WaitlistCorsPolicy(@Value("\${app.waitlist.allowed-origins:}") raw)`, that exposes `configuration: CorsConfiguration?`. Both `SecurityConfig` and `RateLimitFilterConfig` use it, so there is one source of truth.
- In the 429 branch, for the waitlist join only:
  - always `addHeader("Vary", "Origin")`
  - `val allowed = policy.configuration?.checkOrigin(request.getHeader(HttpHeaders.ORIGIN))`
  - when `allowed != null`, set `Access-Control-Allow-Origin: <allowed>` and `Access-Control-Expose-Headers: Retry-After, X-RateLimit-Remaining, X-RateLimit-Reset`
- `CorsConfiguration.checkOrigin` is case-insensitive. It returns the request's origin, or `null` for a foreign or absent origin [VERIFIED: jshell, spring-web 7.0.7: exact → `https://landing.example`, upper-case → `https://LANDING.example`, foreign → `null`, null → `null`].
- `Retry-After` is not a CORS-safelisted response header, so it must be in `Access-Control-Expose-Headers` [CITED: WHATWG Fetch, CORS-safelisted response-header names].
- Also add `exposedHeaders = listOf("Retry-After", "X-RateLimit-Remaining", "X-RateLimit-Reset")` to the shared `CorsConfiguration`, so normal 202s expose the counters too. This is optional but consistent.
- **Do not** use `DefaultCorsProcessor.processRequest` here. It rejects a disallowed origin by writing a 403, which would hide the 429.

**Canonical hops (D-15).** The current logic is `hops.asReversed().firstOrNull { !trustedProxyMatcher.matches(it) } ?: remoteAddr` (RateLimitFilter.kt:89-93). `isIpLiteral` is public with a strict IPv4 regex and an IPv6 shape regex of up to 45 characters; `parseLiteral` is private and calls `InetAddress.getByName(literal).address` (TrustedProxyMatcher.kt:74-98).
- Add `fun canonicalize(hop: String): String?` to the companion. Accept only these shapes:
  - `[v6]` or `[v6]:port`. After `]`: end of string, or `:` followed by 1-5 digits.
  - `a.b.c.d:port`. Exactly one `:`; the port is 1-5 digits.
  - A bare literal.
- Return `InetAddress.getByAddress(bytes).hostAddress`, or `null`. Probe results, JDK 21 here (same `Inet*Address` code as 17) [VERIFIED: jshell run this session]:

  | Input | Output |
  |---|---|
  | `::1` | `0:0:0:0:0:0:0:1` |
  | `0:0:0:0:0:0:0:1` | `0:0:0:0:0:0:0:1` |
  | `::ffff:10.0.0.1` | `10.0.0.1` |
  | `2001:db8::1` | `2001:db8:0:0:0:0:0:1` |
  | `10.0.0.5` | `10.0.0.5` |

  That is the same uncompressed form Tomcat uses for `remoteAddr`, so keys from hops and from peers line up.
- A bare IPv6 address with a port and no brackets (`2001:db8::1:80`) is ambiguous. It is treated as an address. Document this.
- Walk the canonicalized hops right to left with an explicit loop:
  - `null` (unparseable) → return `remoteAddr` (fail safe)
  - trusted → continue
  - otherwise → return the hop
  - all hops trusted → `remoteAddr`

  The review's `firstOrNull { it == null || ... } ?: remoteAddr` happens to be correct, but it merges "found null" with "none found". Write the explicit loop for readability.
- Only text that passes the shape filter ever reaches `InetAddress`, so DNS is never touched.
- **IN-02.** Rename `isIpLiteral` → `private fun hasIpLiteralShape`. `TrustedProxyMatcherTest.kt:57` calls `TrustedProxyMatcher.isIpLiteral(it)`; change that assertion to `assertNull(TrustedProxyMatcher.canonicalize(it))`. Keep `matches("[::1]") == false`, because `matches` stays strict and only `canonicalize` strips brackets.

**One-shot WARN (D-17).** Add `private val warnedUntrustedForwardedFor = AtomicBoolean(false)`. In `resolveClientIp`, when the peer is untrusted and `request.getHeader("X-Forwarded-For") != null` and `compareAndSet(false, true)`:
```kotlin
log.warn("Ignoring X-Forwarded-For from untrusted peer {}; if this is your reverse proxy, add it to rate-limit.trusted-proxies (RATE_LIMIT_TRUSTED_PROXIES)", remoteAddr)
```
It fires only on throttled paths, because the filter is mapped only there. That is acceptable.

### Pattern 7: path normalization (D-16)

Tomcat 11.0.21 `CoyoteAdapter.postParseRequest` runs `parsePathParameters` (strips `;`), then percent-decodes, then `normalize(...)`. Normalize does `Replace "//" with "/"`, resolves `/./` and `/../`, and returns false (400) when `/../` would leave the context [VERIFIED: tomcat-embed-core-11.0.21-sources CoyoteAdapter.java:627-639 and :1110-1205]. So in production `servletPath + pathInfo` is already normalized.

MockMvc is different. Its `pathInfo` is the **decoded but not dot-normalized** request URI. So a MockMvc test of `/api/auth/./login` only proves the fix if the helper also normalizes. Tested with spring-core/web 7.0.7 [VERIFIED: jshell]:
- `StringUtils.cleanPath("/api//waitlist")` → `/api//waitlist`. It does **not** merge slashes.
- `UriComponentsBuilder.fromPath(p).build().normalize().path`: `/api/auth/./login` → `/api/auth/login`, and `/api//waitlist` → `/api/waitlist`.

Recommended helper:
```kotlin
internal object RequestPaths {
    /** Container-normalized path (servletPath + pathInfo), with dot segments and empty segments resolved again so MockMvc requests (which skip Tomcat normalization) match production. */
    fun normalized(request: HttpServletRequest): String {
        val containerPath = request.servletPath + (request.pathInfo ?: "")
        val withoutParams = UrlPathHelper.defaultInstance.removeSemicolonContent(containerPath)
        return UriComponentsBuilder.fromPath(withoutParams).build().normalize().path ?: withoutParams
    }
}
```
`removeSemicolonContent` is a public `UrlPathHelper` method [ASSUMED: public in Spring 6/7; verify at compile time]. It only matters in MockMvc, because Tomcat already strips path parameters. `StrictHttpFirewall` still rejects `/./`, `%2e` and `;` with a 400 later in the chain. After this change the limiter no longer depends on the firewall for those spellings.

### Anti-Patterns to Avoid
- **One block lookup per conversation** in `deliverUnreadMessages`. D-05 forbids it. Use one query.
- **`expireAfterWrite` for buckets.** It resets an active attacker's bucket mid-window, which fails open.
- **`@Component` on the new filters.** It double-registers them for `/*`. Register through `FilterRegistrationBean` only.
- **Logging the email, the token, the admin token or `EmailResult.errorDetail`.** The project rule is D-08: fixed text plus the exception type.
- **Editing applied migration V24.** It changes the Flyway checksum.
- **A `HandlerInterceptor` for the admin guard.** It runs after the JWT filter and leaves unmapped `/api/admin/*` paths as 404s.
- **`DefaultCorsProcessor` in the 429 branch.** It writes a 403 for disallowed origins.
- **Changing the three rate-limit test classes' `@TestPropertySource` differently.** They share one cached context by having identical annotations (RateLimitBypassIntegrationTest.kt:20-23 KDoc). Any change must be identical across all three.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Bounded, expiring, concurrent per-key map | LRU `LinkedHashMap` + timestamps + locks | Caffeine `Cache.get(key, fn)` + `maximumSize` + `expireAfterAccess` | Atomic compute per key, amortized eviction, W-TinyLFU admission |
| Token bucket | Counters + timestamps | Bucket4j `Bandwidth.builder().capacity(n).refillIntervally(n, window)` (already used) | Thread-safe, lock-free |
| Origin check | String compare against a list | `CorsConfiguration.checkOrigin(origin)` on the same config SecurityConfig registers | Same case-insensitive semantics as the real CORS filter, one source of truth |
| Path normalization | Regex replace of `/./` | Tomcat's `servletPath + pathInfo` plus `UriComponents.normalize()` | Container-exact in production, reproducible in MockMvc |
| IP parsing | Regex for IPv6 | Shape pre-filter, then `InetAddress.getByName` (literal only) / `getByAddress` | The JDK parser is exact. The shape filter stops DNS. |
| Constant-time token compare | `==` | `MessageDigest.isEqual` (already in `AdminTokenGuard`) | Timing-safe |
| Startup config validation | A runtime check per request | Kotlin `init {}` throwing in the `@Component` | Fails the context, so the deploy never serves |

**Key insight:** each item in this phase is a failure-direction fix (fail-open → fail-safe). The library primitives already have the right failure direction. Hand-rolled versions are where the earlier bypasses came from.

## Common Pitfalls

### Pitfall 1: The MockMvc `servletPath` is empty
**What goes wrong:** the JWT skip list and any `servletPath`-only path logic behave differently in MockMvc than in Tomcat. A D-12 MockMvc test fails even after `/api/admin` is added to the skip list.
**Why it happens:** `private String servletPath = "";`, and `pathInfo` is set to the decoded URI (AbstractMockHttpServletRequestBuilder.java:108, :976-986).
**How to avoid:** read paths only through `RequestPaths.normalized(request)` (servletPath + pathInfo). Keep one direct-filter test that sets `servletPath` explicitly.
**Warning signs:** a stale-Bearer test that passes only through the direct-bean call.

### Pitfall 2: The short admin token in the test suite
**What goes wrong:** after D-13, every context with `app.invite.admin-token=test-admin-secret` fails to start.
**Where:**
- `WaitlistAdminIntegrationTest.kt:21` (`private const val ADMIN_SECRET = "test-admin-secret"`)
- `WaitlistConvertIntegrationTest.kt:47` (`CONVERT_ADMIN_SECRET`)
- `InviteAdminEndpointIntegrationTest.kt:27` plus header literals at :44, :87, :101
- `InviteGateIntegrationTest.kt:27`

**How to avoid:** add one shared `const val TEST_ADMIN_TOKEN = "test-admin-secret-0123456789abcdef"` (34 characters) in a test-utility file and use it everywhere. The property strings stay byte-identical across classes, so context caching is preserved. The blank-token deny-by-default classes (`app.invite.admin-token=`) are unaffected.

### Pitfall 3: Test throughput limits
**What goes wrong:** with a 5/min admin bucket, `WaitlistAdminIntegrationTest`'s about 11 requests from MockMvc's default `127.0.0.1` get 429s. The new join bucket defaults to 10/min, which breaks every waitlist test class that joins more than 10 times from one IP.
**Why:** `src/test/resources/application.yml` only raises the existing key: `rate-limit:\n  capacity: 10000` (:71-72).
**How to avoid:** add `waitlist-capacity: 10000` and `admin-capacity: 10000` under `rate-limit:` in the test `application.yml`. The three throttle-test classes then set `rate-limit.waitlist-capacity=2` (and `rate-limit.admin-capacity=2` if the admin throttle test lives there) identically.

### Pitfall 4: Existing waitlist tests encode the old re-join behavior
See the "Existing tests that must change" table under Validation Architecture. To restore "outside the cooldown", backdate `updated_at` with `jdbcTemplate.update("UPDATE waitlist_entries SET updated_at = updated_at - INTERVAL '16 minutes' WHERE normalized_email = ?", n)`. That exercises the real 15-minute default, instead of setting the cooldown to 0 in the test config.

### Pitfall 5: Asserting "no push" over STOMP can pass by accident
**What goes wrong:** a STOMP client that subscribes after the 200 ms async delivery sees nothing either way.
**How to avoid:** use one of these:
- **(a)** A positive control in the same reconnect: an undelivered message in a visible conversation with a third user. Wait for it, then assert the hidden one never arrived.
- **(b) Preferred, deterministic, no RANDOM_PORT.** Capture at the broker. `SimpMessagingTemplate.convertAndSendToUser` sends to `this.destinationPrefix + user + destination` (SimpMessagingTemplate.java:229), on the `brokerChannel` bean (AbstractMessageBrokerConfiguration.java:249) [VERIFIED: spring-messaging-7.0.7-sources]. Add a `ChannelInterceptor` to `@Autowired @Qualifier("brokerChannel") AbstractSubscribableChannel`, capture `SimpMessageHeaderAccessor.getDestination(...)` equal to `/user/<uuid>/queue/notifications`, and remove it in `@AfterEach`. Call `chatService.deliverUnreadMessages(recipientId)` directly.

### Pitfall 6: The rematch edge case is not covered by D-04
**What goes wrong:** a message is sent while matched, the recipient stays offline through unmatch and rematch, then reconnects. The match is active again, so the old message is pushed.
**Why it is acceptable:** after the rematch the same conversation and its full history are visible again (`getMessages` has no time filter), so nothing hidden leaks. D-05 keeps teardown unchanged, which rules out marking messages delivered at block time.
**How to handle:** record it as a known limitation. Do not fix it.

### Pitfall 7: Caffeine maintenance is asynchronous
**What goes wrong:** an eviction-count test is flaky.
**How to avoid:** the helper takes an optional `Ticker` and `Executor` (`Runnable::run`). Tests call `cleanUp()` before asserting `estimatedSize()`.

### Pitfall 8: The send runs on another thread
**What goes wrong:** inside the mock sender, a test queries `invites` with `jdbcTemplate` to "prove the flush", and sees 0 rows. The worker thread uses a different connection, and Postgres has no dirty reads.
**How to avoid:** prove the ordering with a mockk unit test of `WaitlistService` (`verifyOrder { inviteService.create(null); emailSender.send(any()) }`), and of `InviteService` (`verify { inviteRepository.saveAndFlush(any()) }`). Prove the rollback-on-timeout end to end in `WaitlistConvertIntegrationTest`.

### Pitfall 9: The JWT filter is registered twice
`JwtAuthenticationFilter` is a `@Component OncePerRequestFilter` added to the security chain. Spring Boot also auto-registers it as a servlet filter. The second pass is skipped by OncePerRequestFilter. Changing `shouldNotFilter` affects both passes identically, so this is harmless. Do not "fix" it in this phase.

### Pitfall 10: The disposition file's ID collisions
**What goes wrong:** the current file's `IN-01..IN-05` rows are the **current** review's. The first review's IN-01 (TTL), IN-02 (admin KDoc), IN-03 (pagination), IN-04 (unused method) and IN-05 (`UriComponentsBuilder`) were silently dropped, along with WR-01 and WR-02.
**How to avoid:** give every dropped first-review item a new ID. See the bookkeeping map below.

## Code Examples

### 1. W1: hidden-conversation query and delivery partition
```kotlin
// ConversationRepository — JPQL in the style of findConversationsByUserId (ConversationRepository.kt:11)
@Query(
    """
    SELECT c.id FROM Conversation c JOIN ConversationParticipant cp ON cp.conversation = c
    WHERE cp.user.id = :userId
      AND (c.match.endedAt IS NOT NULL OR EXISTS (
            SELECT b.id FROM Block b
            WHERE (b.blocker.id = c.match.user1.id AND b.blocked.id = c.match.user2.id)
               OR (b.blocker.id = c.match.user2.id AND b.blocked.id = c.match.user1.id)))
    """
)
fun findHiddenConversationIdsForUser(@Param("userId") userId: UUID): List<UUID>

// ChatService.deliverUnreadMessages — after loading `undelivered` exactly as today
val hidden = conversationRepository.findHiddenConversationIdsForUser(userId).toSet()
val (suppressed, visible) = undelivered.partition { it.conversation.id in hidden }
suppressed.forEach { it.delivered = true }          // D-04: no push; can't resurface after a rematch
messageRepository.saveAll(suppressed)
for (msg in visible) { /* existing push + delivered = true + save */ }
return visible.size                                  // pushed count
```
If Hibernate 7 rejects the implicit `c.match.user1.id` joins inside the subquery, add `JOIN c.match m` and use `m.*`. As a fallback, use the equivalent native SQL over `conversation_participants(conversation_id, user_id)`, `conversations(match_id)`, `matches(user1_id, user2_id, ended_at)` and `blocks(blocker_id, blocked_id)` [VERIFIED column names: V9, V10, V11, V19, V20 migrations] [ASSUMED: the JPQL compiles as written].

### 2. The shared bucket helper
```kotlin
package com.catspell.api.common.ratelimit

/** Per-key Bucket4j buckets in a bounded Caffeine cache; an idle key expires after one refill window, which is lossless because an untouched bucket is full by then. */
class RateLimitBuckets(
    private val capacity: Long,
    private val window: Duration,
    maxKeys: Long,
    ticker: Ticker = Ticker.systemTicker(),
    executor: Executor? = null
) {
    private val cache: Cache<String, Bucket> = Caffeine.newBuilder()
        .maximumSize(maxKeys)
        .expireAfterAccess(window)
        .ticker(ticker)
        .apply { if (executor != null) executor(executor) }
        .build()

    fun bucketFor(key: String): Bucket = cache.get(key) {
        Bucket.builder().addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, window).build()).build()
    }

    internal fun estimatedSize(): Long = cache.estimatedSize()
    internal fun cleanUp() = cache.cleanUp()
}
```
Key: `rate-limit.max-tracked-keys: ${RATE_LIMIT_MAX_TRACKED_KEYS:100000}`, shared by all instances. The default is the planner's choice. Bucket4j bucket memory per key is small, so 100k keys per map comes to a few tens of MB at worst [ASSUMED].

### 3. Cooldown rotate UPDATE (email pinned)
```kotlin
@Modifying
@Query(
    """
    UPDATE WaitlistEntry e SET e.confirmTokenHash = :hash, e.confirmTokenExpiresAt = :expiresAt, e.updatedAt = :now
    WHERE e.normalizedEmail = :normalizedEmail AND e.status = :pending
      AND (e.confirmTokenHash IS NULL OR e.updatedAt <= :resendCutoff)
    """
)
fun rotatePendingToken(
    @Param("normalizedEmail") normalizedEmail: String,
    @Param("hash") hash: String,
    @Param("expiresAt") expiresAt: Instant,
    @Param("now") now: Instant,
    @Param("resendCutoff") resendCutoff: Instant,
    @Param("pending") pending: WaitlistStatus
): Int
```

### 4. Convert with a bounded send
```kotlin
val message = waitlistInviteEmailRenderer.render(recipient, rawCode)          // renderer bugs → 500, not 502
val future = CompletableFuture.supplyAsync({ emailSender.send(message) }, inviteSendExecutor)
val result = try {
    future.get(inviteSendTimeoutMs, TimeUnit.MILLISECONDS)
} catch (ex: TimeoutException) {
    future.cancel(true)
    log.warn("Waitlist invite email send timed out after {} ms", inviteSendTimeoutMs)
    throw WaitlistInviteDeliveryException(cause = ex)
} catch (ex: ExecutionException) {
    val cause = ex.cause ?: ex
    log.warn("Waitlist invite email send failed: {}", cause.javaClass.simpleName)
    throw WaitlistInviteDeliveryException(cause = cause)
} catch (ex: InterruptedException) {
    Thread.currentThread().interrupt()
    throw WaitlistInviteDeliveryException(cause = ex)
}
if (result.status != EmailSendStatus.SUCCESS) {
    log.warn("Waitlist invite email was not accepted by the email provider")
    throw WaitlistInviteDeliveryException()
}
```

### 5. Central admin filter
```kotlin
class AdminTokenFilter(private val adminTokenGuard: AdminTokenGuard) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try {
            adminTokenGuard.require(request.getHeader("X-Admin-Token"))
        } catch (_: AdminAuthException) {
            writeUnauthorized(response, "Not authorized")      // same detail/title as handleAdminAuth
            return
        }
        chain.doFilter(request, response)
    }
}

@Configuration
class AdminTokenFilterConfig(private val adminTokenGuard: AdminTokenGuard) {
    @Bean
    fun adminTokenFilterRegistration(): FilterRegistrationBean<AdminTokenFilter> =
        FilterRegistrationBean(AdminTokenFilter(adminTokenGuard)).apply {
            addUrlPatterns("/api/admin/*")                     // servlet pattern also matches /api/admin
            order = Ordered.HIGHEST_PRECEDENCE + 10            // after RateLimitFilter, before Spring Security (-100)
        }
}
```

### 6. CORS headers on the waitlist 429
```kotlin
if (family == BucketFamily.WAITLIST_JOIN) {
    httpResponse.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN)
    corsConfiguration?.checkOrigin(httpRequest.getHeader(HttpHeaders.ORIGIN))?.let { origin ->
        httpResponse.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
        httpResponse.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After, X-RateLimit-Remaining, X-RateLimit-Reset")
    }
}
```

### 7. Hop canonicalization
```kotlin
// TrustedProxyMatcher companion
private val PORT = Regex("[0-9]{1,5}")
fun canonicalize(hop: String): String? {
    val host = when {
        hop.startsWith("[") -> {
            val end = hop.indexOf(']')
            if (end < 0) return null
            val rest = hop.substring(end + 1)
            if (rest.isNotEmpty() && !(rest.startsWith(":") && PORT.matches(rest.substring(1)))) return null
            hop.substring(1, end)
        }
        hop.count { it == ':' } == 1 -> {
            val (h, p) = hop.split(':')
            if (!PORT.matches(p)) return null
            h
        }
        else -> hop
    }
    if (!hasIpLiteralShape(host)) return null
    return parseLiteral(host)?.let { InetAddress.getByAddress(it).hostAddress }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| `ConcurrentHashMap<String, Bucket>` | Caffeine-backed bounded map behind one helper | This phase | Memory bounded per instance |
| `UrlPathHelper.getPathWithinApplication` (17-09) | `servletPath + pathInfo`, then `normalize()` | This phase | Dot segments no longer depend on StrictHttpFirewall |
| Admin guard per handler | Central servlet filter plus handler checks kept | This phase | New `/api/admin/*` routes are protected by default |
| Verbatim XFF hop as key | Canonical address bytes; unparseable → peer | This phase | `ip:port` fails safe instead of open |

**Deprecated/outdated:** none. `UrlPathHelper` is not deprecated in spring-web 7.0.7 (current review). It is simply no longer the source of truth for the limiter.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | The hidden-conversation JPQL (implicit `c.match.user1.id` inside EXISTS) compiles on Hibernate 7.2 | Code Example 1 | Compile or startup failure. Fall back to an explicit join or native SQL (given). |
| A2 | Postgres READ COMMITTED re-checks the cooldown WHERE after a lock wait, so concurrent joins send exactly one email | Pattern 3 | The concurrency test would see more than 1 email. The existing claim queries rely on the same behavior. |
| A3 | Evicting a bucket idle for one window is lossless with `refillIntervally` | Pattern 2 | A slightly earlier refill for an idle key. Negligible. |
| A4 | `UrlPathHelper.removeSemicolonContent` is public in Spring 7 | Pattern 7 | Compile error. Drop it; only MockMvc needs it. |
| A5 | JavaMail timeout property names for a future SMTP sender | Pattern 4 | KDoc only; no runtime effect today. |
| A6 | Default `rate-limit.max-tracked-keys` of 100000 is affordable | Code Example 2 | Memory sizing. Tunable by env var. |
| A7 | Admin throttle of 5/min per IP is acceptable for operators batch-converting entries | Pattern 5 | Operator scripts hit 429 and must honor `Retry-After`. See Open Question 1. |

## Open Questions (RESOLVED)

1. **Admin throttle counts every request, including successful ones.** RESOLVED
   - What we know: D-13 says "strict per-IP bucket on `/api/admin/**` (e.g. 5/min)". Converting 50 entries means 50 POSTs, which is about 10 minutes at 5/min.
   - Recommendation: keep counting every request, as D-13 says. Make the default configurable (`RATE_LIMIT_ADMIN_CAPACITY`). Document it with a `Retry-After`-aware batch loop. Flag this to the user at plan review, in case they would rather count only failed attempts.
   - **RESOLVED:** count every request, per D-13 as written. Applied in 18-07 (recorded decision in the objective, accepted risk T-18-24). The capacity is configurable through `rate-limit.admin-capacity` / `RATE_LIMIT_ADMIN_CAPACITY`, and 429s carry `Retry-After`; 18-11 declares and documents the key. The batch-conversion trade-off (about 10 minutes for 50 entries at the default) is surfaced to the user at plan review.
2. **Bucket token spent on a no-op re-join inside the cooldown.** See Pattern 3, point 4. Recommendation: keep it (simplest, strictest). RESOLVED
   - **RESOLVED:** keep it. The per-email bucket check stays first, so a no-op re-join inside the cooldown still spends one token; no refund. Applied in 18-06 (Open Question 2 note in the objective, accepted risk T-18-20).
3. **Record the stale-Bearer "Unchecked" audit item in the disposition file?** It is not a review finding. Recommendation: add an informational row `AUD-01 | info | fixed | 18-xx (audit "Unchecked" item: stale Bearer on admin routes)`, or record it only in the phase VERIFICATION. This is the planner's choice. RESOLVED
   - **RESOLVED:** add the row. 18-11 writes `AUD-01` (severity info, fixed, Source 18-10) into 17-REVIEW-DISPOSITION.md, and its verify gate requires the ID (threat T-18-40).

**Out-of-scope notes (noted, not fixed, per D-05):**
- `sendMessage` pushes live but never sets `delivered = true`, so every message is re-pushed once on the next reconnect (Pattern 1). Noted, not fixed. Recorded under "Known limitations" in 18-01.
- Rematch edge case: a message sent while matched, with the recipient offline through unmatch and rematch, is pushed on the first reconnect after the rematch (Pitfall 6). Noted, not fixed. Recorded under "Known limitations" in 18-01.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|---|---|---|---|---|
| Podman | Testcontainers (Postgres/PostGIS, MinIO) | ✓ | 5.8.2; `podman-machine-default` exists (last up 2 weeks ago; may need `podman machine start`) | none, start the machine |
| JDK | Build (toolchain 17) | ✓ | System `java` is 21.0.12. Gradle provisions or uses toolchain 17 (prior phases built fine). | — |
| Maven Central | Caffeine 3.2.3 download | ✓ | HTTP 200 on the POM | — |
| node (`/opt/homebrew/bin`) | gsd-tools only | ✓ when on PATH | — | `export PATH=/opt/homebrew/bin:$PATH` |

**Missing dependencies with no fallback:** none. **Missing with fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|---|---|
| Framework | JUnit 5 (spring-boot-starter-test), mockk 1.13.11, Spring MockMvc, Testcontainers 1.20.6 on Podman, Awaitility (used in `WaitlistPerEmailConcurrencyIntegrationTest`) |
| Config file | `src/test/resources/application.yml` (`ddl-auto: create-drop`, `flyway.enabled: false`, Hikari `maximum-pool-size: 4`); `BaseIntegrationTest` truncates all tables `@BeforeEach` |
| Quick run command | `./gradlew test --tests 'com.catspell.api.common.TrustedProxyMatcherTest'` (pure unit; roughly the Gradle startup cost) |
| Full suite command | `./gradlew test` (~15 min / ~430 tests; **run in the background**; `:test UP-TO-DATE` proves a prior green run) |

Testing facts the plans depend on:
- Integration tests build the schema from entities (`create-drop`), not Flyway. Entity annotations must carry `unique`/`nullable`; that's already the case for `waitlist_entries`.
- In-memory buckets persist for the life of a cached Spring context. Tests isolate by unique IPs and emails, not by reset. The Caffeine change does not alter that.
- Contexts are cached by identical annotations. Every new distinct `@TestPropertySource` adds a context, holding 4 Postgres connections. Prefer joining existing contexts.

### Phase Requirements → Test Map
| Fix | Behavior | Test Type | Automated Command | File Exists? |
|---|---|---|---|---|
| W1 (MOD-02/03) | Block or unmatch, then reconnect: no push to `/user/<id>/queue/notifications`, `delivered = true`; after a rematch, still no push; a visible control conversation still pushed | integration (broker-channel capture; Pitfall 5b) | `./gradlew test --tests 'com.catspell.api.moderation.BlockEnforcementIntegrationTest'` (add the tests there; shared context) | ✅ file / ❌ tests |
| WR-10 | Helper: same key returns the same bucket; idle key evicted after the window (fake ticker); `maximumSize` bounds size | unit | `./gradlew test --tests 'com.catspell.api.common.RateLimitBucketsTest'` | ❌ Wave 0 |
| WR-11 / WR-03 | Re-join inside the cooldown: 202, no rotation, no email; outside (backdated): rotate + email; 20 concurrent joins → exactly 1 email | integration | `./gradlew test --tests 'com.catspell.api.waitlist.*'` | ✅ (update) + new cooldown test |
| WR-04 | Re-join with a variant address keeps the first stored email; the confirm email goes to the stored address | integration | same | ✅ (update `WaitlistJoinIntegrationTest`) |
| D-09 / IN-08 | Renderer: TTL rendered (168 → "7 days", 36 → "36 hours"), no "still confirmed", link via UriComponentsBuilder | unit | `./gradlew test --tests 'com.catspell.api.email.WaitlistConfirmEmailRendererTest'` | ❌ Wave 0 |
| WR-05 | `saveAndFlush` before send (order) | unit (mockk) | `./gradlew test --tests 'com.catspell.api.waitlist.WaitlistServiceConvertTest'` | ❌ Wave 0 |
| WR-06 / D-10 | Timeout → 502 + rollback; cause chained; non-SUCCESS logged without address | integration + unit | `./gradlew test --tests 'com.catspell.api.waitlist.WaitlistConvertIntegrationTest'` | ✅ (extend; add `app.waitlist.invite-send-timeout-ms` to its existing property array) |
| WR-08 / D-11 | `GET /api/admin/does-not-exist` without token → 401; 401 before param conversion (`limit=abc`) | integration | `./gradlew test --tests 'com.catspell.api.waitlist.WaitlistAdminIntegrationTest'` | ✅ (extend) |
| D-12 | Correct admin token + `Bearer garbage` → 200 (fails before the fix) | integration (MockMvc) + direct filter | same + `WaitlistEnumerationSafetyIntegrationTest` | ✅ (extend) |
| D-13 length | Non-blank < 32 → context fails; blank → starts, 401 | unit (`ApplicationContextRunner`) | `./gradlew test --tests 'com.catspell.api.common.AdminTokenGuardStartupTest'` | ❌ Wave 0 |
| D-13 throttle | 3rd admin request from one IP at capacity 2 → 429 (even with a wrong token) | integration (shared rate-limit context) | `./gradlew test --tests 'com.catspell.api.common.RateLimit*'` | ✅ (extend) |
| WR-07 / D-14 | Join bucket independent of the login bucket; 429 on the join carries ACAO + Expose-Headers for an allowed origin, none for a foreign one, `Vary: Origin` | integration | `./gradlew test --tests 'com.catspell.api.waitlist.WaitlistCors429IntegrationTest'` | ❌ Wave 0 (one new context) |
| Current WR-01 | `172.16.0.0/12` and `2001:db8:ab00::/41` edges | unit | `./gradlew test --tests 'com.catspell.api.common.TrustedProxyMatcherTest'` | ✅ (extend; snippet in the current review) |
| Current WR-02 / D-15 | Rotating `ip:port` → 429 on the 3rd; `[v6]:port` canonical; malformed hop → peer's bucket | unit + integration | `TrustedProxyMatcherTest` + `RateLimitBypassIntegrationTest` | ✅ (update) |
| Current IN-01 / D-16 | `/api/auth/./login` and `/api/./waitlist` share the canonical bucket | unit (`RequestPaths`) + integration | `RateLimitBypassIntegrationTest` | ✅ (extend `OTHER_SPELLINGS` → move to the exact-bucket list) |
| Current IN-05 / D-17 | Two untrusted XFF requests → exactly one WARN naming the peer and the key | unit (Logback `ListAppender` on a directly built `RateLimitFilter`) | `./gradlew test --tests 'com.catspell.api.common.RateLimitFilterWarnTest'` | ❌ Wave 0 |
| IN-06 | `/api/waitlistX` with a stale Bearer → JWT runs (401) | direct filter | `WaitlistEnumerationSafetyIntegrationTest` | ✅ (extend) |
| IN-07 | Malformed success URL → context fails | unit (`ApplicationContextRunner` or construct directly) | new or extend | ❌ |

### Existing tests that must change (do not discover these during execution)
| File:line | Current assertion | Change |
|---|---|---|
| `WaitlistJoinIntegrationTest.kt:131-144` | "overwrites email with the latest trimmed submission"; expects `"Pending-Rejoin+landing@Example.com"` | Backdate `updated_at`, assert rotation, and assert `email == "pending-rejoin@example.com"` (pinned). Rename the test. |
| `WaitlistConfirmIntegrationTest` `pending re-join sends a second email...` and `pending re-join invalidates the first link...` (around :125, :248) | 2nd join right after the 1st sends a 2nd email | Backdate between joins. Add an inside-cooldown variant (no 2nd email). |
| `WaitlistPerEmailConcurrencyIntegrationTest` (around :79-98) | Exactly `PER_EMAIL_CAPACITY` (3) emails from 20 concurrent joins | Expect exactly **1** (cooldown). Rename. The bucket cap is proven by the limit test with backdating. |
| `WaitlistPerEmailLimitIntegrationTest` (`plus and case variants...`, `exhausting one address...`) | 2nd join rotates; `afterSecond["email"] == "b@example.com"` | Backdate before each rotation that should happen. The stored email stays the first trimmed value `"B+Tag@Example.COM"`. |
| `TrustedProxyMatcherTest.kt:57` | `TrustedProxyMatcher.isIpLiteral(it)` | Becomes `assertNull(TrustedProxyMatcher.canonicalize(it))` (`isIpLiteral` made private). |
| `RateLimitBypassIntegrationTest.kt:195-204` | Malformed hop shares one bucket keyed on the hop text | Expect the **peer's** bucket. Use a peer no other test uses (Pitfall: `127.0.0.1` is shared). Option: add a trusted range such as `198.51.100.0/24` via an identical property change on all three shared classes. |
| `WaitlistRateLimitIntegrationTest`, `RateLimitTrustedProxyIntegrationTest`, `RateLimitBypassIntegrationTest` `@TestPropertySource(properties = ["rate-limit.capacity=2"])` | Join throttled by the auth capacity | Add `"rate-limit.waitlist-capacity=2"` (and optionally `"rate-limit.admin-capacity=2"`), **identically** in all three. The registered-pattern test gains `/api/admin/*`. |
| `src/test/resources/application.yml:71-72` | Only `capacity: 10000` | Add `waitlist-capacity: 10000`, `admin-capacity: 10000`. Optionally mirror `per-email-refill-hours: 24`. |
| 5 classes with `test-admin-secret` | 17-character token | Shared ≥32-character constant (Pitfall 2) |

### Sampling Rate
- **Per task commit (stage):** the targeted `--tests` filter for the touched area, from the table above.
- **Per wave merge:** `./gradlew test --tests 'com.catspell.api.waitlist.*' --tests 'com.catspell.api.common.*' --tests 'com.catspell.api.invite.*' --tests 'com.catspell.api.moderation.*' --tests 'com.catspell.api.chat.*'`
- **Phase gate:** full `./gradlew test` green, run in the background, before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt`: helper semantics with a fake `Ticker` and a direct executor
- [ ] `src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt`: `ApplicationContextRunner`
- [ ] `src/test/kotlin/com/catspell/api/common/RateLimitFilterWarnTest.kt` and a `RequestPaths` unit test
- [ ] `src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt`
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt` (mockk unit; ordering, timeout, cause)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt`
- [ ] A shared test constant file for `TEST_ADMIN_TOKEN`
- No framework install is needed.

## Security Domain

`security_enforcement: true`, ASVS level 1, `security_block_on: high` (config.json).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---|---|---|
| V2 Authentication | yes | Admin shared secret ≥32 characters enforced at startup; constant-time compare (`MessageDigest.isEqual`); brute-force throttle per IP (D-13) |
| V3 Session Management | no | Stateless JWT; unchanged |
| V4 Access Control | yes | Central deny-by-default `/api/admin/*` filter ahead of all handlers (D-11); W1 enforces the block on the reconnect surface (MOD-02) |
| V5 Input Validation | yes | X-Forwarded-For hops: strict shape filter, JDK literal parse, no DNS, fail safe to the peer (D-15); normalized paths (D-16) |
| V6 Cryptography | no change | SHA-256 token hashing, SecureRandom tokens (unchanged) |
| V7 Error Handling & Logging | yes | WARN logs carry the exception type only; no email, token, admin token or `errorDetail` (D-10); one-shot misconfiguration WARN (D-17) |
| V11 Business Logic | yes | Anti-automation: resend cooldown + 3/24 h per email (D-07); separate join bucket (D-14) |
| V13 API / Web Service | yes | CORS: explicit origins only, no credentials, consistent on 429 (D-14) |
| V14 Configuration | yes | Fail-fast startup validation (admin token, redirect URLs, trusted proxies) |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---|---|---|
| Rate-limit bypass via port-rotating or unparseable XFF hops | Spoofing / DoS | Canonicalize; unparseable → peer key (D-15) |
| Rate-limit bypass via path spelling (`/./`, `//`, `%2e`) | Tampering | Key on the container-normalized path (D-16) |
| Memory exhaustion via unbounded bucket keys | DoS | Caffeine `maximumSize` + `expireAfterAccess` (D-18) |
| Email bombing a victim through re-joins | DoS / Repudiation | Cooldown + 3/24 h (D-07) |
| Waitlist-spot hijack via a `+suffix` variant | Spoofing / Elevation | Pin the stored address (D-08) |
| Admin secret brute force | Spoofing | Per-IP admin bucket ahead of the guard + 32-character minimum (D-13) |
| New admin route shipped without a guard | Elevation | Central filter on the whole prefix (D-11) |
| Hidden-conversation preview leak after a block | Information disclosure | Set-based hidden filter on reconnect (W1) |
| Invite code mailed but never stored | Repudiation / integrity | Flush before send; rollback on failure (D-10) |
| PII in logs | Information disclosure | Fixed-text WARN + exception type only |

## Disposition Bookkeeping Map (for the final plan)

Update `17-REVIEW-DISPOSITION.md`: the table, the frontmatter `findings:` list, and `open:` / `total:`. Use `fixed` with the Phase 18 plan reference, or `deferred` with the reason in the Source cell.

| ID in file | Origin | Disposition after Phase 18 |
|---|---|---|
| WR-01 | current review: non-octet CIDR tests | fixed |
| WR-02 | current review: `ip:port` hops fail open | fixed |
| IN-01 | current review: dot segments | fixed |
| IN-02 | current review: `isIpLiteral` naming | fixed |
| IN-03 | current review: IPv4-mapped CIDR message, zone-scoped peers | **deferred** (reason: out of scope per 18-CONTEXT; fails safe) |
| IN-04 | current review: docs env-var table | fixed |
| IN-05 | current review: silent misconfiguration | fixed |
| CR-01, CR-02, WR-09 | first review | already fixed (17-09), unchanged |
| WR-03 … WR-08 | first review | fixed |
| IN-06, IN-07 | first review | fixed |
| **WR-10 (new)** | first review WR-01: unbounded bucket maps | fixed |
| **WR-11 (new)** | first review WR-02: ~72 mails/day | fixed |
| **IN-08 (new)** | first review IN-01: hardcoded TTL in copy | fixed |
| **IN-09 (new)** | first review IN-02: admin KDoc / 401 before conversion | fixed (central filter) |
| **IN-10 (new)** | first review IN-03: list pagination | **deferred** (reason: out of scope per 18-CONTEXT) |
| **IN-11 (new)** | first review IN-04: unused `findByNormalizedEmail` | fixed (removed; replaced by `findStoredEmail`) |
| **IN-12 (new)** | first review IN-05: link building | fixed (`UriComponentsBuilder`) |

The file's footer warns that re-running the review gate drops a row whose ID is reused. Choosing IDs above the current review's maximum (WR-02, IN-05) and the first review's maximum (WR-09, IN-07) avoids that.

## Sources

### Primary (HIGH confidence)
- **In-repo code**, read this session with Read:
  - `ChatService.kt`, `MessageRepository.kt`, `Conversation.kt`, `ConversationRepository.kt`, `BlockRepository.kt`
  - `WaitlistService.kt`, `WaitlistEntryRepository.kt`, `WaitlistEntry.kt`, `WaitlistController.kt`, `WaitlistConfirmEmailRenderer.kt`
  - `RateLimitFilter.kt`, `TrustedProxyMatcher.kt`, `SecurityConfig.kt`, `JwtAuthenticationFilter.kt`, `AdminTokenGuard.kt`, `ProblemDetailAuthenticationEntryPoint.kt`
  - `EmailSender.kt`, `Exceptions.kt`, `InviteService.kt`, both `application.yml` files
  - Test files listed in the tables
- **In-repo code, read with shell `cat -n`:** `MatchService.kt`, `BlockService.kt`, `Match.kt`, `Block.kt`, `Message.kt`, `WebSocketSessionListener.kt`, `WebSocketConfig.kt`, `GlobalExceptionHandler.kt`, `LoggingEmailSender.kt`, the admin controllers, migrations V9-V12/V19/V20/V24, `build.gradle.kts`, `.env.example`, `docs/CONFIGURATION.md`.
- **Library sources in the Gradle cache:**
  - `spring-test-7.0.7-sources`: AbstractMockHttpServletRequestBuilder
  - `tomcat-embed-core-11.0.21-sources`: CoyoteAdapter
  - `spring-messaging-7.0.7-sources`: SimpMessagingTemplate, AbstractMessageBrokerConfiguration
  - `spring-boot-security-4.0.6-sources`: SecurityFilterProperties
  - `spring-boot-dependencies-4.0.6.pom`
- **jshell probes this session (spring-core/web 7.0.7):** InetAddress canonicalization, `cleanPath` vs `UriComponents.normalize`, `UriComponentsBuilder` links, `CorsConfiguration.checkOrigin`.
- **Maven Central:** caffeine maven-metadata.xml and the 3.2.3 POM.

### Secondary (MEDIUM confidence)
- Caffeine `Cache.java` v3.2.3 Javadoc (raw.githubusercontent.com): atomic `get(key, fn)`, `@NullMarked`
- Caffeine wiki: Population, Eviction, Testing
- Phase 17 reviews (current + `git show aa09317`), the milestone audit, 17-CONTEXT and 13-CONTEXT

### Tertiary (LOW confidence)
- JavaMail timeout property names (A5); Bucket4j memory per bucket (A6)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. BOM-managed version read from the cached POM; Central confirmed.
- Architecture: HIGH. Filter ordering, MockMvc path behavior, Tomcat normalization and broker destinations were all read from the library sources.
- Pitfalls: HIGH. Every affected test was located at file:line.
- Query compile details: MEDIUM (A1, A4).

**Research date:** 2026-10-03
**Valid until:** 2026-11-02 (stable stack; re-check if Spring Boot or Spring Security versions change)
