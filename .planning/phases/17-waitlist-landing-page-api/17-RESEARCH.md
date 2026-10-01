# Phase 17: Waitlist / Landing-Page API - Research

**Researched:** 2026-10-01
**Domain:** Public enumeration-safe double-opt-in waitlist + operator convert-to-invite (Kotlin 2.4 / Spring Boot 4.0.6 / Spring Security 7.1 / Spring Data JPA / Bucket4j 8.10 / Flyway / PostgreSQL 16)
**Confidence:** HIGH (nearly everything is reuse of in-repo patterns read this session; the new findings are the gaps called out below)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Confirmation link response (WAIT-02)
- **D-01:** `GET /api/waitlist/confirm?token=...` responds with a **`302` redirect to a configurable web landing-page URL** — a success URL on a valid single-use claim and a separate error URL on an invalid/expired/already-used token. The landing page is a separate **web** repo and the link is clicked in a browser, so a redirect (not JSON, not a `catspell://` deep link) is the right fit. Model the config keys on the existing `app.*-url` convention (e.g. `app.waitlist.confirm-success-url` / `app.waitlist.confirm-error-url`); exact key names are Claude's discretion.
- **D-02:** The `catspell://` deep-link style used by `verify-email`/`confirm-email-change` is **explicitly rejected** here — waitlist clicks happen on web before the user has the app.

#### Join / dedupe / re-join behavior (WAIT-01, WAIT-03)
- **D-03:** **Email normalization = trim + lowercase + strip the `+suffix`** from the local part (all domains) for the **dedupe/unique key and the per-email rate-limit bucket key**. This closes the most common per-email-limit bypass (Pitfall 5, `a+tag@`) without provider-specific dot rules. Full gmail-style dot-stripping was rejected as risky (merges distinct addresses at providers where dots are significant). Storing the raw-submitted address alongside the normalized key is Claude's discretion (existing register/login store verbatim; the normalized form is the dedupe/bucket key).
- **D-04:** **Re-join is state-dependent but always returns the identical enumeration-safe `202`:** an **unconfirmed (`PENDING`) re-join invalidates the prior token and sends a fresh confirm email** (mirrors `EmailVerificationService.issueAndSend` — invalidate prior unused tokens, mint + send one new token); an **already-`CONFIRMED` (or `INVITED`) re-join is a silent no-op** (no resend). The per-email rate-limit bucket bounds resend abuse. Callers cannot distinguish new / pending / confirmed.
- **D-05:** **Email only at join this phase** — no optional name / referral-source fields captured. WAIT-01's "optional info" is deferred; keeps the table + DTO minimal.

#### Anti-abuse (WAIT-03)
- **D-06:** **Disposable-domain filtering is deferred** (WAIT-03 lists it as optional). Double opt-in (bots that can't confirm never reach `CONFIRMED`) + per-IP/per-email Bucket4j throttling are sufficient anti-abuse for launch; a config-driven blocklist can be added later if abuse appears.
- **D-07:** Reuse the existing **`RateLimitFilter`** (Bucket4j, per-IP) and add a **per-email bucket** in the service layer (mirroring `EmailVerificationService.emailBucket`) keyed on the normalized email (D-03). Add the public `POST /api/waitlist` path to the filter's throttled-path set. Exact capacities/refill windows are Claude's discretion, following existing `rate-limit.*` / `app.resend-verification.*` conventions.

#### Confirmation token (WAIT-02)
- **D-08:** The confirm token reuses the **v2.1 hashed single-use token model** — SHA-256 at rest, raw token embedded only in the outbound email link (never persisted/logged), atomic single-use claim on confirm (conditional update, 0 rows → treated as invalid/expired → redirect to error URL). **TTL = 7 days** (lower-security than password reset/verify; a long window maximizes confirmation rate for a launch list).

#### Operator conversion (WAIT-04)
- **D-09:** The operator converts via **`POST /api/admin/waitlist/{id}/invite`** (entry identified by its **DB id**, not email — keeps the raw email out of the request path) **plus a `GET /api/admin/waitlist?status=confirmed`** list endpoint so the operator can see who to convert. Both sit behind the **existing `X-Admin-Token` shared-secret guard** (reuse the exact deny-by-default, constant-time `MessageDigest.isEqual` pattern from `InviteAdminController.requireValidAdminToken`; same `app.invite.admin-token` key). No admin role/RBAC introduced.
- **D-10:** Conversion is only valid on a **`CONFIRMED`** entry: it calls **`InviteService.create(referrerUserId = null)`** (organic — no referral attribution), emails the invite code/link via `EmailSender` (new renderer, mirror the existing `*EmailRenderer` + `app.*-url` pattern), and **marks the entry `INVITED`**. A **second convert of an already-`INVITED` entry is rejected** (409/400 — not an idempotent re-send), and converting a non-`CONFIRMED` entry is likewise rejected. Exact exception/status + whether the convert response returns the raw invite code is Claude's discretion (note: `InviteService.create` returns the raw code once).

### Claude's Discretion
- Exact `waitlist_entries` column shapes/types/indexes (subject to: unique on normalized-email dedupe key, hashed confirm token, single-use claim, status enum `PENDING`/`CONFIRMED`/`INVITED`), and the status state machine representation.
- Config key names for the confirm success/error URLs and any `app.waitlist.*` keys (confirm TTL, rate-limit capacities), following existing `app.*` / `@Value` conventions (no `@ConfigurationProperties`, per AGENTS.md).
- Service method signatures (`WaitlistService.join` / `confirm` / `convertToInvite` or similar), DTO/package naming (`com.catspell.api.waitlist`), and the exact transaction boundaries (confirm claim; convert + mark-INVITED + invite-create atomicity).
- Whether to store the raw-submitted email in addition to the normalized key.
- Exception class names + RFC 7807 copy for admin-auth failures (reuse `AdminAuthException`), bad/expired confirm token (redirect, no 7807 body needed), and convert-state violations.

### Deferred Ideas (OUT OF SCOPE)
- **Disposable-domain filtering** — deferred (D-06); double opt-in + rate limiting suffice for launch. Add a config-driven blocklist later if abuse appears.
- **Optional join fields (name / referral source)** — deferred (D-05); email only this phase.
- **Waitlist position / referral leaderboard** — v2 requirement WAIT2-01; out of scope.
- **Referral attribution from waitlist conversions** — declined (D-10); converted invites carry no referrer.
- **Idempotent re-convert / re-send of an already-invited entry** — declined (D-10); a second convert is rejected.
- **Full gmail-style dot normalization** — declined (D-03); risks merging distinct addresses at other providers.
- **Landing-page frontend** — separate repo; this phase builds only the backing API.

None else — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WAIT-01 | A public unauthenticated endpoint accepts a waitlist join (email + optional info) and returns an identical enumeration-safe response for new vs duplicate emails | Public-route wiring in all four places (Pattern 1); race-free `INSERT ... ON CONFLICT DO NOTHING` upsert so a concurrent duplicate cannot surface a 500 (Pattern 3, Pitfall 2); `202 GenericMessageResponse` mirroring `resend-verification`; async AFTER_COMMIT email send to remove the timing/failure side channel (Pattern 5). Optional info deferred per D-05. |
| WAIT-02 | Double opt-in — hashed single-use, time-limited confirmation token emailed; only confirmed entries count | Token columns on the entry row; rotation on PENDING re-join replaces the hash (D-04); single conditional UPDATE claim that covers not-found / expired / used in one statement (Pattern 4); `302` to fixed configured URLs (D-01); admin list filters `status = CONFIRMED`. |
| WAIT-03 | Per-IP + per-email rate limiting with email normalization (Bucket4j reuse); optional disposable-domain filtering | `RateLimitFilter` needs BOTH the path-set entry AND the `FilterRegistrationBean` URL pattern (Pitfall 1); per-email bucket mirroring `EmailVerificationService.emailBucket`, silent on exhaustion; normalizer spec incl. edge cases (Pattern 2). Disposable filtering deferred (D-06). |
| WAIT-04 | Operator converts a confirmed entry into an invite, which emails the invite code/link | `X-Admin-Token` guard reuse (extract to shared component); one transaction: conditional `CONFIRMED→INVITED` claim → `InviteService.create(null)` → synchronous send, rollback on send failure so the entry stays retryable (Pattern 6). |
</phase_requirements>

## Project Constraints (from AGENTS.md)

No `CLAUDE.md` exists; the repo uses `AGENTS.md`. `.planning/config.json` points `claude_md_path` at `./.windsurf/rules`, which contains no `*.md` rule files (checked this session). Directives:

- **Podman, not Docker.** Use `podman compose ...` for container ops. Testcontainers reaches Podman through the `build.gradle.kts` `DOCKER_HOST` shim. [VERIFIED: AGENTS.md; build.gradle.kts `tasks.withType<Test>` block]
- **Stack:** Kotlin 2.4 / JVM 17, Spring Boot 4.0.6, PostgreSQL 16 + PostGIS 3.4, Flyway migrations. [VERIFIED: build.gradle.kts plugins block — `kotlin("jvm") version "2.4.0"`, `id("org.springframework.boot") version "4.0.6"`]
- **Config:** custom keys (`app.*`, `email.*`, ...) go through `@Value` / `@ConditionalOnProperty`. **No `@ConfigurationProperties`, no config processor.** The IDE "Unknown property" warning is cosmetic.
- **Tests:** `./gradlew test`. Integration tests start their own containers through Testcontainers.
- **Fresh DB:** `podman compose down -v` replays all migrations.
- Never edit applied migrations V1–V23. The new migration is V24.

## Summary

This phase is almost all reuse. Every building block already exists and was read this session: SHA-256 hashed tokens with an atomic conditional-UPDATE claim, the `EmailSender` seam plus `*EmailRenderer`, the per-email Bucket4j map, the `RateLimitFilter`, the constant-time `X-Admin-Token` guard, and `InviteService.create(null)`. The planner should copy those patterns, not invent new ones. No new libraries are needed.

The research found five things the CONTEXT does not mention. The plan has to handle each one.

1. **Public routes are wired in four places.** People call it the "three-place whitelist", but there are four. `RateLimitFilterConfig` registers the filter only on `/api/auth/*`. Adding `/api/waitlist` to the `AUTH_PATHS` set alone does nothing in production. The registration URL pattern must change too.
2. **Concurrent joins can crash.** If two requests join with the same new email at once, the unique constraint throws `DataIntegrityViolationException`. That becomes a 500, which breaks the "identical `202`" rule. Use a native `ON CONFLICT DO NOTHING` insert instead.
3. **CORS is not configured anywhere** (grepped). A browser landing page on another origin cannot `POST` JSON to `/api/waitlist` until a narrowly scoped CORS mapping exists.
4. **No real email provider exists.** The only `EmailSender` bean is `LoggingEmailSender`. So the convert endpoint should return the raw invite code (like `POST /api/admin/invites` already does), and a failed send should roll back.
5. **Mail scanners will spend confirm links.** Corporate mail scanners (Safe Links, Proofpoint) open GET links before the user does. That burns the single-use token, so the real click lands on the error URL. D-01 and D-08 lock in GET plus single-use, so this is raised as an open question. The partial fix is copy on the error page.

**Primary recommendation:** Build a `com.catspell.api.waitlist` package modeled on `invite/`. Put the confirm token on the `waitlist_entries` row. Make join a race-free native upsert followed by a conditional token-rotate UPDATE, all in one `@Transactional`, with the confirm email sent `@Async` after commit. Make confirm a single conditional UPDATE that returns 302. Make convert a single transaction: claim `CONFIRMED→INVITED`, create the invite, send synchronously, and roll back if the send fails. Wire the public routes in all four places, and add a config-driven CORS mapping for `POST /api/waitlist` only.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Join (`POST /api/waitlist`) validation, normalization, dedupe | API / Backend | Database (unique constraint is the dedupe authority) | Untrusted public input. The DB constraint, not app code, guarantees one row per normalized email. |
| Per-IP throttle | API / Backend (servlet filter) | — | Existing `RateLimitFilter` runs at `HIGHEST_PRECEDENCE`, before Spring Security. |
| Per-email throttle | API / Backend (service) | — | Needs the normalized email, which only exists after body parsing. Mirrors `EmailVerificationService`. |
| Confirm token mint / hash / claim | API / Backend | Database (conditional UPDATE is the atomicity authority) | Single-use is enforced by the DB row lock, not by read-check-write. |
| Confirm landing UX | Browser (separate web repo) | API (302 to configured URL) | D-01. The API only redirects to fixed URLs. |
| Cross-origin join call | API (CORS config) | Browser | Without CORS headers the browser blocks the landing page's fetch. |
| Operator list / convert | API / Backend | — | Shared-secret guard. No UI this phase. |
| Invite issuance | API / Backend (`InviteService`) | Database | Reused unchanged. |
| Email delivery | API / Backend (`EmailSender` seam) | External provider (none yet) | Join: async after commit. Convert: synchronous inside the transaction. |

## Standard Stack

### Core (all already on the classpath; no new dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot (web, data-jpa, security, validation) | 4.0.6 | Controllers, JPA repos, security chain, bean validation | Project stack [VERIFIED: build.gradle.kts] |
| Spring Security | 7.1.0 (pinned via `extra["spring-security.version"] = "7.1.0"`) | `permitAll` whitelist, `http.cors {}` | [VERIFIED: build.gradle.kts] |
| `com.bucket4j:bucket4j-core` | 8.10.1 | Per-IP (filter) and per-email (service) buckets | Already used by `RateLimitFilter` and `EmailVerificationService` [VERIFIED: build.gradle.kts] |
| Flyway (`flyway-core`, `flyway-database-postgresql`) | Boot-managed | V24 migration | [VERIFIED: build.gradle.kts] |
| `java.security.MessageDigest` / `SecureRandom` | JDK 17 | SHA-256 token hash, 32-byte URL-safe tokens, constant-time compare | In-repo pattern (`InviteService.kt:71-81`) |

### Supporting (test)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Testcontainers postgresql / junit-jupiter | 1.20.6 | Real Postgres (`postgis/postgis:16-3.4-alpine`) | All integration tests through `BaseIntegrationTest` |
| MockK | 1.13.11 | `@Primary` relaxed `EmailSender` mock + `capture(sentMessages)` | Capturing confirm/invite emails (pattern in `EmailVerificationIntegrationTest`) |
| Awaitility | Boot-managed (transitive via `spring-boot-starter-test`) | Waiting for the async AFTER_COMMIT send | Already imported by `ReportNotificationIntegrationTest.kt:14` (`import org.awaitility.Awaitility.await`) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Token columns on `waitlist_entries` | Separate `waitlist_confirm_tokens` table (like `email_verification_tokens`) | A separate table needs a "mark prior unused tokens used" loop and a join. One token per entry, overwritten on PENDING re-join, invalidates the prior token by construction (D-04) and lets one conditional UPDATE do the claim. **Use on-row columns.** |
| Native `ON CONFLICT DO NOTHING` | `findBy…` then `save()` and catch `DataIntegrityViolationException` | Catching inside `@Transactional` leaves the transaction rollback-only, which gives `UnexpectedRollbackException` and a 500. That is an enumeration leak. **Use the native upsert.** |
| Async AFTER_COMMIT send for join | Synchronous send (as `EmailVerificationService.issueAndSend` does) | A synchronous send makes new/pending joins measurably slower than confirmed no-ops once a real provider exists (timing oracle), and a provider exception would turn into a 500. Async matches the `ReportNotificationListener` precedent. |
| Extract `AdminTokenGuard` component | Copy `requireValidAdminToken` into a second controller | Two copies of a security boundary can drift apart. Extract it and keep `InviteAdminEndpointIntegrationTest` as the regression guard. |

**Installation:** none. No new packages.

## Package Legitimacy Audit

This phase installs **no external packages**. Every library above is already declared in `build.gradle.kts` or comes in transitively through `spring-boot-starter-test` (Awaitility). The legitimacy gate does not apply.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
 Landing page (browser, other origin)                       Email client (browser)
        │ POST /api/waitlist {email}                             │ GET /api/waitlist/confirm?token=RAW
        │ (CORS preflight OPTIONS first)                         │
        ▼                                                        ▼
 ┌─ RateLimitFilter (servlet, HIGHEST_PRECEDENCE) ──┐     (not throttled; 256-bit token)
 │ per-IP bucket; 429 problem+json on exhaustion    │            │
 └──────────────┬───────────────────────────────────┘            │
                ▼                                                ▼
 Spring Security chain: CORS → JwtAuthFilter(skip) → permitAll
                │                                                │
                ▼                                                ▼
 WaitlistController.join                          WaitlistController.confirm
   @Valid @NotBlank @Email                          hash(token) → repo.claimConfirm(hash, now)
                │                                        │ 1 row            │ 0 rows / blank token
                ▼                                        ▼                  ▼
 WaitlistService.join (@Transactional)              302 → success URL   302 → error URL
   normalize(email) ──► per-email bucket ──empty──► return (silent)
                │ token
                ▼
   repo.insertIfAbsent(normalized, email)      [INSERT … ON CONFLICT DO NOTHING]
   repo.rotatePendingToken(normalized, email, hash, exp)  [UPDATE … WHERE status='PENDING']
                │ 1 row (new or pending)            │ 0 rows (CONFIRMED / INVITED)
                ▼                                   ▼
   publishEvent(ConfirmRequested(email, RAW))   no-op
                │
   ─── commit ──┴──► 202 GenericMessageResponse (identical in every branch)
                │
                ▼ @Async @TransactionalEventListener(AFTER_COMMIT)
   WaitlistEmailListener → WaitlistConfirmEmailRenderer → EmailSender.send (swallow-log)

 Operator (curl) ── X-Admin-Token ──►  WaitlistAdminController
   GET  /api/admin/waitlist?status=confirmed ──► guard → repo.findAllByStatus… → 200 [entries]
   POST /api/admin/waitlist/{id}/invite      ──► guard → WaitlistService.convertToInvite (@Transactional)
        findById ──none──► 404
        repo.markInvited(id, now) [UPDATE … WHERE status='CONFIRMED'] ──0──► 409
        InviteService.create(null) → RAW code (hash persisted)
        WaitlistInviteEmailRenderer → EmailSender.send ──ERROR/throws──► rollback (entry stays CONFIRMED)
        ──► 201 {entryId, code}
```

### Recommended Project Structure
```
src/main/kotlin/com/catspell/api/
├── waitlist/
│   ├── controller/
│   │   ├── WaitlistController.kt          # POST /api/waitlist, GET /api/waitlist/confirm
│   │   └── WaitlistAdminController.kt     # GET /api/admin/waitlist, POST /api/admin/waitlist/{id}/invite
│   ├── model/
│   │   ├── WaitlistEntry.kt               # JPA entity
│   │   ├── WaitlistStatus.kt              # enum PENDING, CONFIRMED, INVITED
│   │   ├── WaitlistEntryRepository.kt     # native upsert + conditional updates
│   │   └── WaitlistDtos.kt                # JoinWaitlistRequest, WaitlistEntryResponse, ConvertWaitlistResponse
│   ├── service/
│   │   ├── WaitlistService.kt             # join / confirm / list / convertToInvite
│   │   └── WaitlistEmailNormalizer.kt     # pure function, unit-testable (D-03)
│   └── event/
│       ├── WaitlistEvents.kt              # WaitlistConfirmationRequestedEvent(email, rawToken)
│       └── WaitlistEmailListener.kt       # @Async AFTER_COMMIT send, swallow-log
├── email/service/
│   ├── WaitlistConfirmEmailRenderer.kt    # @Value app.waitlist.confirm-url
│   └── WaitlistInviteEmailRenderer.kt     # @Value app.waitlist.invite-url
├── common/security/
│   └── AdminTokenGuard.kt                 # extracted requireValidAdminToken (used by both admin controllers)
└── common/exception/
    └── Exceptions.kt                      # + WaitlistEntryNotConvertibleException (→ 409)
src/main/resources/db/migration/V24__create_waitlist_entries.sql
```

### Pattern 1: Public-route wiring touches FOUR places (plus CORS)

**What:** Every public `/api/auth/*` endpoint today is wired in three code locations, and the rate-limit filter has a fourth (its URL-pattern registration).

Verbatim current state:

`SecurityConfig.kt:28-29` [VERIFIED: src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt:28-29]
```kotlin
it.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/confirm-email-change").permitAll()
it.requestMatchers("/api/admin/invites").permitAll()
```

`JwtAuthenticationFilter.kt:16-27` [VERIFIED: src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt:16-27]
```kotlin
override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = request.servletPath
    return path.startsWith("/api/auth/register") ||
            ...
            path.startsWith("/api/auth/confirm-email-change") ||
            path.startsWith("/v3/api-docs")
}
```
`doFilterInternal` returns 401 ("Invalid or expired token") on any malformed `Bearer` header, even on permitAll routes (lines 41-55). A landing page that sends a stale header would get a 401.

`RateLimitFilter.kt:24-31,38` and `RateLimitFilterConfig` `:83,89` [VERIFIED: src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt:24-31,38,83,89]
```kotlin
private val AUTH_PATHS = setOf(
    "/api/auth/register",
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/forgot-password",
    "/api/auth/resend-verification",
    "/api/auth/change-email"
)
...
if (!AUTH_PATHS.any { path.startsWith(it) }) {
...
@Value("\${rate-limit.capacity:10}") private val capacity: Long
...
registration.addUrlPatterns("/api/auth/*")
```

**Required changes:**
1. `SecurityConfig`: `requestMatchers("/api/waitlist", "/api/waitlist/confirm").permitAll()` and `requestMatchers("/api/admin/waitlist", "/api/admin/waitlist/**").permitAll()`. List both admin patterns. The `PathPattern` javadoc only says explicitly that `{*path}` matches the bare prefix; for `/**` it is implied, not stated [CITED: docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/pattern/PathPattern.html].
2. `JwtAuthenticationFilter.shouldNotFilter`: add `path.startsWith("/api/waitlist")`. This covers join and confirm. Do NOT add the admin paths: they carry no Bearer header and Phase 16 did not add `/api/admin/invites` either.
3. `RateLimitFilter`: throttle **only `POST /api/waitlist`, by exact match**. A `startsWith("/api/waitlist")` entry in `AUTH_PATHS` would also throttle `GET /api/waitlist/confirm` whenever the filter is mounted on all paths, which is what `RateLimitIntegrationTest` does with `addFilters(rateLimitFilter)`. Add a separate exact check, e.g. `httpRequest.method == "POST" && path == "/api/waitlist"`, alongside the `AUTH_PATHS.any { … }` check.
4. `RateLimitFilterConfig`: `registration.addUrlPatterns("/api/auth/*", "/api/waitlist")`. **Without this the filter never runs on the waitlist route in production**, and tests that build the filter by hand will not notice (Pitfall 1).
5. CORS (Pattern 7).

### Pattern 2: Email normalization (D-03), a pure function

```kotlin
// Source: D-03; HV @Email splits at lastIndexOf('@') [CITED: hibernate-validator AbstractEmailValidator.java]
// Kotlin String.lowercase() uses "Unicode mapping rules of the invariant locale" [CITED: kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/lowercase.html]
object WaitlistEmailNormalizer {
    fun normalize(raw: String): String {
        val trimmed = raw.trim().lowercase()
        val at = trimmed.lastIndexOf('@')
        if (at <= 0) return trimmed                      // validation rejects these anyway; never throw here
        val local = trimmed.substring(0, at)
        val domain = trimmed.substring(at)               // includes '@'
        val plus = local.indexOf('+')
        val base = if (plus > 0) local.substring(0, plus) else local   // plus == 0 ("+x@d") → keep as-is, avoid "@d" collisions
        return base + domain
    }
}
```
Edge cases the unit test must cover: `" A+Tag@Example.COM "` → `a@example.com`; `a+b+c@x.com` → `a@x.com`; `+only@x.com` → unchanged (no empty local); `a.b@gmail.com` keeps the dot (D-03 rejects dot-stripping); a quoted local part with `@` splits on the last `@`.

**DTO validation:** `@field:NotBlank @field:Email @field:Size(max = 255)`. Hibernate Validator's `@Email` returns **valid for empty strings** (`if ( value == null || value.length() == 0 ) { return true; }`) [CITED: github.com/hibernate/hibernate-validator AbstractEmailValidator.java], and the existing `ResendVerificationRequest` (AuthDtos.kt:55-57) has only `@Email`. Add `@NotBlank`, or `""` becomes a real "@-less" waitlist row.

### Pattern 3: Race-free join (upsert + conditional token rotate)

```kotlin
// Source: pattern adapted from InviteRepository.markConsumed (conditional UPDATE) + Postgres ON CONFLICT
interface WaitlistEntryRepository : JpaRepository<WaitlistEntry, UUID> {

    // Supplies EVERY NOT NULL column explicitly: the test schema is Hibernate create-drop (no DB DEFAULTs).
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO waitlist_entries (id, email, normalized_email, status, created_at, updated_at)
            VALUES (gen_random_uuid(), :email, :normalizedEmail, 'PENDING', :now, :now)
            ON CONFLICT (normalized_email) DO NOTHING
        """
    )
    fun insertIfAbsent(@Param("email") email: String, @Param("normalizedEmail") normalizedEmail: String, @Param("now") now: Instant): Int

    // 1 row = new or still-PENDING entry → send; 0 rows = CONFIRMED/INVITED → silent no-op (D-04).
    @Modifying
    @Query("""
        UPDATE WaitlistEntry e SET e.email = :email, e.confirmTokenHash = :hash,
               e.confirmTokenExpiresAt = :expiresAt, e.updatedAt = :now
        WHERE e.normalizedEmail = :normalizedEmail AND e.status = :pending
    """)
    fun rotatePendingToken(...): Int
}
```
Service flow (`@Transactional`): normalize → if `!emailBucket(normalized).tryConsume(1)` return (silent) → `insertIfAbsent` → mint raw token + hash → `rotatePendingToken` → if 1, `eventPublisher.publishEvent(WaitlistConfirmationRequestedEvent(trimmedEmail, rawToken))` → return. The controller always returns `ResponseEntity.accepted().body(GenericMessageResponse(...))`, the same shape as `AuthController.kt:83-89`:
```kotlin
// [VERIFIED: src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt:83-89]
@PostMapping("/resend-verification")
fun resendVerification(@Valid @RequestBody request: ResendVerificationRequest): ResponseEntity<GenericMessageResponse> {
    emailVerificationService.resend(request.email)
    return ResponseEntity.accepted().body(
        GenericMessageResponse("If an unverified account exists for that email, a verification link has been sent.")
    )
}
```
Overwriting `email` on a PENDING re-join (recommended answer to the "store raw email" discretion item) means the confirm link goes to the address just submitted. Confirming then proves control of the stored delivery address, which is where the invite is later sent. An attacker who submits `victim+x@` never receives a token addressed to `victim@`.

Per-email bucket: copy `EmailVerificationService.kt:35-43` exactly [VERIFIED: src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt:28-30,35-43]:
```kotlin
@Value("\${app.resend-verification.per-email-capacity:3}") private val perEmailCapacity: Long,
@Value("\${app.resend-verification.per-email-refill-hours:1}") private val perEmailRefillHours: Long,
...
private val emailBuckets = ConcurrentHashMap<String, Bucket>()
private fun emailBucket(normalizedEmail: String): Bucket = emailBuckets.computeIfAbsent(normalizedEmail) {
    val bandwidth = Bandwidth.builder()
        .capacity(perEmailCapacity)
        .refillIntervally(perEmailCapacity, Duration.ofHours(perEmailRefillHours))
        .build()
    Bucket.builder().addLimit(bandwidth).build()
}
```
New keys: `app.waitlist.per-email-capacity:3`, `app.waitlist.per-email-refill-hours:1`.

### Pattern 4: Confirm = one conditional UPDATE, then 302

```kotlin
@Modifying
@Query("""
    UPDATE WaitlistEntry e SET e.status = :confirmed, e.confirmedAt = :now, e.updatedAt = :now
    WHERE e.confirmTokenHash = :hash AND e.status = :pending AND e.confirmTokenExpiresAt > :now
""")
fun claimConfirm(@Param("hash") hash: String, @Param("now") now: Instant,
                 @Param("pending") pending: WaitlistStatus, @Param("confirmed") confirmed: WaitlistStatus): Int
```
- This one statement rejects unknown, expired, already-used, and rotated-away tokens alike. It is the same DB-row-lock guarantee as `InviteRepository.markConsumed` [VERIFIED: src/main/kotlin/com/catspell/api/invite/model/InviteRepository.kt:19-21 — `@Query("UPDATE Invite i SET i.consumedAt = :now, i.consumedBy = :inviteeId WHERE i.id = :id AND i.consumedAt IS NULL")`].
- The `status = PENDING` guard is the single-use mechanism. **Leave `confirm_token_hash` in place after confirm** (do not null it). It is only a hash, and keeping it means the "already confirmed → success" option in Open Question 1 needs no schema change.
- Controller: `@GetMapping("/confirm") fun confirm(@RequestParam(required = false) token: String?)`. A blank or null token goes to the error URL, never a 400, so every failure looks the same. Return `ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).header("Referrer-Policy", "no-referrer").header("Cache-Control", "no-store").build()` [ASSUMED: standard `ResponseEntity.HeadersBuilder` API; the compiler will confirm].
- Redirect targets are **fixed config values only**. Never echo request input into `Location` (open redirect).
- Raw token format is `Base64.getUrlEncoder().withoutPadding()` over 32 bytes [VERIFIED: InviteService.kt:71-75], so it is query-string safe without encoding.

### Pattern 5: Async AFTER_COMMIT confirm email (join only)

Precedent [VERIFIED: src/main/kotlin/com/catspell/api/moderation/event/ReportNotificationListener.kt:28-36]:
```kotlin
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onReportCreated(event: ReportCreatedEvent) {
    try {
        emailSender.send(reportEmailRenderer.render(operatorEmail, event))
    } catch (e: Exception) {
        log.warn("Operator report email failed for report {}: {}", event.reportId, e.message)
    }
}
```
`@EnableAsync` is already on `CatSpellApplication`. The event carries the raw token in memory only; never log the event. The listener log line must not include the token or the full email. `LoggingEmailSender` masks recipients and prints bodies only under the `dev` profile.

### Pattern 6: Convert = one transaction, synchronous send, rollback on failure

```kotlin
@Transactional
fun convertToInvite(entryId: UUID): ConvertWaitlistResponse {
    val entry = waitlistEntryRepository.findById(entryId).orElseThrow { ResourceNotFoundException("Waitlist entry not found") } // 404
    if (waitlistEntryRepository.markInvited(entryId, Instant.now(), CONFIRMED, INVITED) == 0) {
        throw WaitlistEntryNotConvertibleException()                                            // 409: PENDING or already INVITED
    }
    val rawCode = inviteService.create(null)                                                    // joins this tx (REQUIRED); organic, D-10
    val result = emailSender.send(waitlistInviteEmailRenderer.render(entry.email, rawCode))
    if (result.status != EmailSendStatus.SUCCESS) throw IllegalStateException("Invite email delivery failed") // rollback → entry stays CONFIRMED
    return ConvertWaitlistResponse(entryId = entryId, code = rawCode)
}
```
- `markInvited`: `UPDATE WaitlistEntry e SET e.status = :invited, e.invitedAt = :now, e.updatedAt = :now WHERE e.id = :id AND e.status = :confirmed`. Concurrent double-converts give exactly one 201 and one 409.
- `InviteService.create` is `@Transactional` [VERIFIED: InviteService.kt:33-42 — `fun create(referrerUserId: UUID?): String { ... inviteRepository.save(Invite(codeHash = hashToken(rawCode), referrerUserId = referrerUserId)); return rawCode }`]. It joins the outer transaction, so a rollback also removes the invite row.
- `EmailSender.send` returns `EmailResult(status: EmailSendStatus /* SUCCESS, ERROR */, ...)` [VERIFIED: src/main/kotlin/com/catspell/api/email/service/EmailSender.kt:10-20 — `enum class EmailSendStatus { SUCCESS, ERROR }`]. Failure can come back as a value, not only as an exception, so check it.
- **Return the raw code** (201, mirroring `InviteAdminController`, which returns `IssueInviteResponse(inviteService.create(...))` with `HttpStatus.CREATED`). No real provider exists today. `LoggingEmailSender` is the only `EmailSender` (`@ConditionalOnProperty(name = ["email.enabled"], havingValue = "false", matchIfMissing = true)` [VERIFIED: LoggingEmailSender.kt:9]), so the operator response is the only reliable way to deliver the code.
- Run the admin-token check **before** `findById`. Unauthenticated callers then get 401 for every id and cannot probe which ids exist.
- Do not call `findById` *after* `markInvited` in the same persistence context without `@Modifying(clearAutomatically = true)`, or the entity will show the stale status.
- 409 mapping: add `WaitlistEntryNotConvertibleException` with a handler that returns `ProblemDetail` 409 `title = "Conflict"` and `code = "WAITLIST_ENTRY_NOT_CONVERTIBLE"`. If it rolls back on delivery failure, either use the existing `IllegalStateException` → 409 handler [VERIFIED: GlobalExceptionHandler.kt:201-206] or add a dedicated 502 (discretion). The existing `ResourceNotFoundException` → 404 handler is at GlobalExceptionHandler.kt:112-117.

### Pattern 7: Narrow, config-driven CORS for the join only

Spring Security processes CORS before authorization when `http.cors {}` is enabled and a `CorsConfigurationSource` bean exists [CITED: docs.spring.io/spring-security/reference/servlet/integrations/cors.html]:
```kotlin
@Bean
fun corsConfigurationSource(@Value("\${app.waitlist.allowed-origins:}") origins: String): UrlBasedCorsConfigurationSource {
    val source = UrlBasedCorsConfigurationSource()
    val allowed = origins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (allowed.isNotEmpty()) {
        val cfg = CorsConfiguration().apply {
            allowedOrigins = allowed          // explicit origins, never "*"
            allowedMethods = listOf("POST")
            allowedHeaders = listOf("Content-Type")
            allowCredentials = false
            maxAge = 3600
        }
        source.registerCorsConfiguration("/api/waitlist", cfg)   // join only; confirm is a top-level navigation (no CORS)
    }
    return source
}
// SecurityConfig: http.cors { }  (picks up the bean)
```
Blank default = no CORS headers = server-to-server only. That is the safe default; production sets `WAITLIST_ALLOWED_ORIGINS`.

### Anti-Patterns to Avoid
- **Distinct responses by state.** No `409 already on list`, no 429 from the per-email bucket, no different body or headers. Only the per-IP filter may 429, and that reveals nothing about membership.
- **Catching `DataIntegrityViolationException` inside the transaction.** It leaves the transaction rollback-only and produces a 500. Use `ON CONFLICT`.
- **Storing or logging the raw token or raw invite code.** Hash only. The raw values appear only in the email body, and for the code, once in the admin response.
- **Echoing a client-supplied redirect target.** The confirm `Location` must come from config.
- **Reading `findById` after a `@Modifying` update in the same persistence context** without clearing it.
- **Publishing the join event outside a transaction.** `@TransactionalEventListener` silently drops events published with no active transaction (unless `fallbackExecution = true`) [ASSUMED: Spring default behavior]. Keep `join` `@Transactional`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Token entropy / hashing | Custom token scheme, UUIDs as tokens | `SecureRandom` 32 bytes + Base64url + SHA-256 hex (copy `InviteService.generateRawCode`/`hashToken`) | Proven in-repo, 256-bit, URL-safe |
| Single-use enforcement | Read → check → save | Conditional JPQL `UPDATE … WHERE status = :expected` returning row count | DB row lock closes the race |
| Dedupe under concurrency | App-level "exists?" check | `UNIQUE(normalized_email)` + `ON CONFLICT DO NOTHING` | Only the DB can serialize concurrent inserts |
| Rate limiting | Counters in a map | Bucket4j `Bandwidth`/`Bucket` (existing filter + service map) | Already the project standard. New infra is out of scope (REQUIREMENTS.md "New rate-limiting infrastructure") |
| Constant-time secret compare | `==` / `equals` | `MessageDigest.isEqual` (extract the existing guard) | Timing-safe; deny-by-default when blank |
| Email validation | Regex | `@NotBlank @Email @Size(max=255)` | Bean validation already wired to 400 `Validation Error` ProblemDetail |
| CORS | Manual `Access-Control-*` headers in a filter | `CorsConfigurationSource` + `http.cors {}` | Handles preflight ordering before auth |

**Key insight:** every hard part here (single-use, dedupe, throttling) is a concurrency or side-channel problem. The in-repo answers push the guarantee into the database (conditional UPDATE, UNIQUE, ON CONFLICT) or into an existing library. Do the same.

## Common Pitfalls

### Pitfall 1: Rate-limit filter "added" but never invoked
**What goes wrong:** `"/api/waitlist"` goes into `AUTH_PATHS`, the tests pass, and production has no per-IP throttle.
**Why it happens:** `RateLimitFilterConfig` registers the filter only for `"/api/auth/*"` (RateLimitFilter.kt:89). `RateLimitIntegrationTest` builds `RateLimitFilter()` by hand and mounts it on all paths with `addFilters`, so it skips the registration pattern entirely.
**How to avoid:** add `"/api/waitlist"` to `addUrlPatterns`. Add a test that proves the *registered* filter throttles the waitlist path: a dedicated `@SpringBootTest` with `@TestPropertySource(properties = ["rate-limit.capacity=2"])` and the auto-configured MockMvc, which applies registered servlet filters with their URL patterns [ASSUMED: Boot MockMvc auto-config applies FilterRegistrationBean URL patterns; if not, assert on `FilterRegistrationBean.urlPatterns` directly]. The test yml sets `rate-limit.capacity: 10000`, so the default context never throttles.
**Warning signs:** no `X-RateLimit-Remaining` header on `POST /api/waitlist` from the real app.

### Pitfall 2: Concurrent duplicate join → 500 (enumeration leak)
**What goes wrong:** two simultaneous joins for the same new address, and the second hits the unique constraint.
**How to avoid:** Pattern 3 native upsert. Add a concurrency test: N threads joining the same email all get 202 and exactly one row exists.

### Pitfall 3: Mail scanners burn the confirm token
**What goes wrong:** Outlook Safe Links, Proofpoint and similar scanners GET every link before the recipient sees it. The scanner's request performs the single-use confirm, so the human click lands on the error URL [CITED: github.com/Patrick9263/pickpic/issues/323; github.com/Macrophage87/PhotoAlbum/issues/53 — community reports, MEDIUM].
**Why it matters here:** the entry *does* end up CONFIRMED. A scanner only fetches mail that was actually delivered to a real mailbox, which is what double opt-in is meant to prove. Data correctness holds; only the UX breaks.
**How to avoid (within D-01/D-08):** the landing page's error-URL copy should say "This link was already used or has expired. If you already confirmed, you're on the list." Escalation options are in Open Question 1.

### Pitfall 4: Test-classpath `application.yml` shadows main
**What goes wrong:** a new `@Value("\${app.waitlist.confirm-url}")` with no default makes every `@SpringBootTest` context fail with "Could not resolve placeholder".
**Why it happens:** `src/test/resources/application.yml` redeclares the whole config, including `app.reset-password-url`, `app.verify-email-url`, `app.confirm-email-change-url`, `app.report.operator-email` and `app.invite.*`. That shows it replaces the main file rather than overlaying it. `EmailVerificationEmailRenderer` uses `@Value("\${app.verify-email-url}")` with no default (EmailVerificationEmailRenderer.kt:8) [VERIFIED: file read]. The shadowing mechanism itself is [ASSUMED], but the evidence is strong.
**How to avoid:** give every new `@Value` a safe default **and** add an `app.waitlist:` block to both `src/main/resources/application.yml` and `src/test/resources/application.yml`.

### Pitfall 5: The new entity breaks the existing Flyway-validate test
**What goes wrong:** `InviteMigrationTest` is the only test that runs with `spring.flyway.enabled=true` and `ddl-auto=validate` [VERIFIED: grep this session]. It validates **every** entity, so adding `WaitlistEntry` without a matching V24 (or with mismatched types or nullability) turns it red.
**How to avoid:** create the entity and V24 in the same plan/commit. Add a `WaitlistMigrationTest` copied from `InviteMigrationTest`, using its **own** database name (e.g. `waitlist_migration_test`) so it does not race the invite test's `DROP DATABASE`.

### Pitfall 6: Native SQL vs create-drop schema
**What goes wrong:** the test schema comes from Hibernate `create-drop` (test yml `ddl-auto: create-drop`, `flyway.enabled: false`), so the migration's `DEFAULT gen_random_uuid()` / `DEFAULT NOW()` do not exist in tests. A native INSERT that relies on DB defaults throws NOT NULL violations in tests only.
**How to avoid:** the native insert passes `id`, `status`, `created_at` and `updated_at` explicitly (Pattern 3). Annotate the entity with `@Column(unique = true)` on `normalized_email` and `confirm_token_hash` so create-drop produces the unique index that `ON CONFLICT (normalized_email)` needs.

### Pitfall 7: Timing side channel on join
**What goes wrong:** with a real provider, new/pending joins (DB write plus a 100-500 ms send) are slower than confirmed no-ops.
**How to avoid:** Pattern 5 async AFTER_COMMIT send. The remaining DB-only difference is negligible.

### Pitfall 8: Unbounded per-email bucket map + spoofable `X-Forwarded-For` (pre-existing)
**What goes wrong:** `resolveClientIp` trusts the first `X-Forwarded-For` value (RateLimitFilter.kt:64-70), so a client can rotate it and get a fresh per-IP bucket on every request. The per-email `ConcurrentHashMap` grows with every distinct normalized email. Together, a public endpoint can be made to grow memory without bound.
**How to avoid:** this is out of scope ("New rate-limiting infrastructure" is excluded). Record it as an accepted risk in the threat model, with the mitigation that production runs behind a proxy that **overwrites** `X-Forwarded-For`. Double opt-in still keeps flood rows out of `CONFIRMED`.

### Pitfall 9: `@Email` accepts `""`
See Pattern 2. Add `@NotBlank`.

## Code Examples

### V24 migration (recommended shape)
```sql
-- Source: modeled on V23__create_invites_and_referrals.sql + V21 CHECK-constrained enum column
CREATE TABLE waitlist_entries (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                     VARCHAR(255) NOT NULL,   -- delivery address (trimmed; last submitted while PENDING)
    normalized_email          VARCHAR(255) NOT NULL,   -- D-03 dedupe + per-email bucket key
    status                    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    confirm_token_hash        VARCHAR(64),             -- SHA-256 hex; raw token never stored (D-08)
    confirm_token_expires_at  TIMESTAMPTZ,
    confirmed_at              TIMESTAMPTZ,
    invited_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_waitlist_entries_normalized_email UNIQUE (normalized_email),
    CONSTRAINT uq_waitlist_entries_confirm_token_hash UNIQUE (confirm_token_hash),
    CONSTRAINT chk_waitlist_entries_status CHECK (status IN ('PENDING','CONFIRMED','INVITED'))
);
CREATE INDEX idx_waitlist_entries_status_confirmed_at ON waitlist_entries(status, confirmed_at);
```
Precedent for a STRING enum plus CHECK: `Report.kt:23-25` (`@Enumerated(EnumType.STRING) @Column(name = "category", nullable = false, length = 32)`) with V21's `CONSTRAINT chk_reports_category CHECK (category IN ('HARASSMENT','SPAM','FAKE_PROFILE','INAPPROPRIATE_CONTENT','OTHER'))` [VERIFIED: Report.kt:23-25; V21__create_reports_table.sql:9]. Postgres allows multiple NULLs under a UNIQUE constraint, so nullable `confirm_token_hash` is fine.

### Admin guard extraction
```kotlin
// Source: verbatim logic from InviteAdminController.kt:40-48 [VERIFIED]
@Component
class AdminTokenGuard(@Value("\${app.invite.admin-token:}") private val adminToken: String) {
    fun require(provided: String?) {
        if (adminToken.isBlank() ||
            provided == null ||
            !MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), adminToken.toByteArray(Charsets.UTF_8))
        ) {
            throw AdminAuthException()
        }
    }
}
```
`AdminAuthException` is `class AdminAuthException(message: String = "Not authorized") : RuntimeException(message)` [VERIFIED: Exceptions.kt:43] and maps to a generic 401 with no `code` property [VERIFIED: GlobalExceptionHandler.kt:85-91].

### Config block (add to BOTH application.yml files)
```yaml
app:
  waitlist:
    confirm-url: ${WAITLIST_CONFIRM_URL:http://localhost:8080/api/waitlist/confirm}      # link base in the email (?token=)
    confirm-success-url: ${WAITLIST_CONFIRM_SUCCESS_URL:http://localhost:3000/waitlist/confirmed}
    confirm-error-url: ${WAITLIST_CONFIRM_ERROR_URL:http://localhost:3000/waitlist/link-invalid}
    confirm-token-ttl-hours: ${WAITLIST_CONFIRM_TTL_HOURS:168}                           # 7 days (D-08)
    per-email-capacity: ${WAITLIST_PER_EMAIL_CAPACITY:3}
    per-email-refill-hours: ${WAITLIST_PER_EMAIL_REFILL_HOURS:1}
    invite-url: ${WAITLIST_INVITE_URL:catspell://register}                               # ?code= appended; see Open Q 3
    allowed-origins: ${WAITLIST_ALLOWED_ORIGINS:}                                        # blank = no CORS
```
All URL defaults are [ASSUMED] placeholders that the operator must set per environment. Existing app keys for reference: `app.invite.admin-token: ${INVITE_ADMIN_TOKEN:}   # deny-by-default when blank; NO guessable dev default` [VERIFIED: src/main/resources/application.yml:46-48].

### Email capture in tests (existing pattern)
```kotlin
// [VERIFIED: src/test/kotlin/com/catspell/api/auth/EmailVerificationIntegrationTest.kt]
@TestConfiguration
class MockEmailConfig {
    @Bean @Primary
    fun emailSender(): EmailSender = mockk(relaxed = true)
}
// @BeforeEach: clearMocks(emailSender); every { emailSender.send(capture(sentMessages)) } returns EmailResult(EmailSendStatus.SUCCESS, messageId = "test")
// async join send → await().atMost(Duration.ofSeconds(5)).untilAsserted { ... }   (ReportNotificationIntegrationTest.kt:75)
```
Pull the raw token out of `sentMessages.last().textBody` with a regex on `token=([A-Za-z0-9_-]+)`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| GET link performs the state change | GET shows an interstitial page; a POST (button) performs the change, because scanners don't submit forms | Widely adopted as link scanners spread (community fixes 2025-2026) [CITED: github.com/j-vincent-chan/prospera/pull/11, MEDIUM] | Conflicts with locked D-01/D-08. Surfaced as Open Question 1, not adopted. |
| `WebSecurityConfigurerAdapter` CORS | `CorsConfigurationSource` bean + `http.cors {}` lambda DSL | Spring Security 6+ | Use the bean + DSL form (Pattern 7) |

**Deprecated/outdated:** none relevant. Bucket4j 8.x `Bandwidth.builder()` is already the project's form.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `src/test/resources/application.yml` shadows (does not merge with) the main `application.yml` | Pitfall 4 | Low. Adding keys to both files plus `@Value` defaults is safe either way. |
| A2 | Boot's auto-configured MockMvc applies `FilterRegistrationBean` URL patterns | Pitfall 1 | Medium. If wrong, assert `rateLimitFilterRegistration().urlPatterns` contains `/api/waitlist` instead. |
| A3 | `@TransactionalEventListener` drops events published outside a transaction by default | Anti-patterns | Low. Keeping `join` `@Transactional` makes it moot. |
| A4 | `ResponseEntity.status(FOUND).location(URI)` builder API | Pattern 4 | Very low. The compiler checks it. |
| A5 | The landing page calls the API from the browser cross-origin, so CORS is needed | Pattern 7 / Open Q 2 | Medium. If it proxies server-side, CORS is unnecessary, BUT every request then arrives from one IP and shares one per-IP bucket unless the proxy forwards `X-Forwarded-For`. |
| A6 | Invite email link base defaults to `catspell://register` (the invitee registers in the app with `inviteCode`) | Config / Open Q 3 | Low. It is config; the raw code is also shown as text in the body. |
| A7 | Production sits behind a proxy that overwrites `X-Forwarded-For` | Pitfall 8 | Medium. Without it, per-IP limits are bypassable (pre-existing, out of scope). |
| A8 | `PathPattern` `/api/admin/waitlist/**` also matches the bare `/api/admin/waitlist` | Pattern 1 | None if both patterns are listed, as recommended. |
| A9 | All config URL defaults (localhost landing-page paths) | Config block | Low. Placeholders; the operator sets env vars. |

## Open Questions

1. **Mail-scanner token burn vs D-01/D-08 (GET + single-use)**
   - What we know: scanners will spend tokens before the human clicks. Data stays correct (the entry is CONFIRMED); only the click UX fails.
   - What's unclear: whether the user accepts error-page copy as the only mitigation.
   - Recommendation: honor D-08 as locked, and have the landing-page error copy cover "already confirmed". Optional, and **needs user confirmation because it changes D-08**: when the claim matches 0 rows but the hash belongs to an entry already `CONFIRMED`/`INVITED`, redirect to the success URL instead. The on-row hash is kept (Pattern 4), so no schema change is needed. The stronger GET-interstitial-then-POST fix contradicts D-01 and should go to a later phase.
2. **CORS / landing-page integration mode**
   - What we know: no CORS config exists. A cross-origin browser `fetch` with JSON gets blocked.
   - Recommendation: ship Pattern 7 with a blank-by-default `app.waitlist.allowed-origins`. It is inert until configured and makes either integration mode work.
3. **Invite email link target**
   - What we know: invitees redeem by typing `inviteCode` at register (Phase 16). There is no existing invite URL key.
   - Recommendation: `app.waitlist.invite-url` (default `catspell://register`, configurable to an app-store or web page) with `?code=` appended, and the raw code also printed in the body.
4. **Admin list size**
   - Recommendation: `GET /api/admin/waitlist?status=confirmed&limit=100` (default 100, max 500, ordered by `confirmed_at ASC`). Status is case-insensitive; an invalid value throws `IllegalArgumentException` → 400. Full pagination is unnecessary for launch.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Podman (Testcontainers runtime) | `./gradlew test` | ✓ | podman 5.8.2; `podman-machine-default` "Currently running" | — |
| JDK 17 | Build/test | ✓ | openjdk 17.0.20 | — |
| Gradle wrapper | Build/test | ✓ | `gradle-9.5.1-bin.zip` (wrapper properties) | — |
| PostgreSQL 16 + PostGIS / MinIO | Integration tests | ✓ (via Testcontainers `postgis/postgis:16-3.4-alpine`, `minio/minio:latest`) | — | — |
| Real email provider | Actual delivery of confirm/invite mail | ✗ (only `LoggingEmailSender` exists) | — | Logging sender in dev/CI; the convert response returns the raw code for manual delivery |

**Missing dependencies with no fallback:** none block this phase.
**Missing dependencies with fallback:** real email provider. Dev/CI keep using the logging sender, and the operator can deliver codes from the convert response.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + MockMvc; MockK 1.13.11; Awaitility (transitive) |
| Config file | `build.gradle.kts` (`useJUnitPlatform()`, `TESTCONTAINERS_RYUK_DISABLED=true`, Podman `DOCKER_HOST` shim); `src/test/resources/application.yml` (Flyway off, `create-drop`, `rate-limit.capacity: 10000`) |
| Base class | `com.catspell.api.BaseIntegrationTest` (shared Postgres+MinIO containers; per-test `TRUNCATE … CASCADE` of all public tables) |
| Quick run command | `./gradlew test --tests "com.catspell.api.waitlist.*"` |
| Compile gate | `./gradlew compileKotlin -q` (plus `compileTestKotlin -q`) |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| WAIT-01 | New, PENDING-duplicate, CONFIRMED-duplicate and INVITED-duplicate joins all return identical status + body; unauthenticated; a stray invalid `Bearer` header is ignored | integration (MockMvc, byte-compare bodies) | `./gradlew test --tests "*WaitlistEnumerationSafetyIntegrationTest"` | ❌ Wave 0 |
| WAIT-01 | Concurrent joins for the same new email → all 202, exactly 1 row | integration (thread pool) | `./gradlew test --tests "*WaitlistJoinIntegrationTest"` | ❌ Wave 0 |
| WAIT-01 | Blank / malformed / >255 email → 400 Validation Error | integration | `./gradlew test --tests "*WaitlistJoinIntegrationTest"` | ❌ Wave 0 |
| WAIT-02 | Join stores only SHA-256 hash (raw token absent from DB); email contains a link with the raw token | integration (jdbcTemplate + captured email, Awaitility) | `./gradlew test --tests "*WaitlistConfirmIntegrationTest"` | ❌ Wave 0 |
| WAIT-02 | Valid token → 302 to success URL and status CONFIRMED; reuse, expired (set `confirm_token_expires_at` in the past via jdbc), unknown, blank, and rotated-away tokens → 302 to error URL with status unchanged | integration | `./gradlew test --tests "*WaitlistConfirmIntegrationTest"` | ❌ Wave 0 |
| WAIT-02 | PENDING re-join rotates the token (old link → error URL, new link → success); CONFIRMED re-join sends no email | integration | `./gradlew test --tests "*WaitlistConfirmIntegrationTest"` | ❌ Wave 0 |
| WAIT-02 | Concurrent confirm with one token → exactly one success | integration (concurrency) | `./gradlew test --tests "*WaitlistConfirmIntegrationTest"` | ❌ Wave 0 |
| WAIT-03 | Normalizer: trim/lowercase/+suffix, keeps dots, `+x@` edge, last-`@` split | unit (pure) | `./gradlew test --tests "*WaitlistEmailNormalizerTest"` | ❌ Wave 0 |
| WAIT-03 | Per-email limit: `a@x`, `A+1@X`, `a+2@x` share a bucket; after capacity is used up, still 202 but no more emails sent | integration (`@TestPropertySource app.waitlist.per-email-capacity=2`) | `./gradlew test --tests "*WaitlistRateLimitIntegrationTest"` | ❌ Wave 0 |
| WAIT-03 | Per-IP limit: the **registered** filter throttles `POST /api/waitlist` → 429 problem+json; `GET /api/waitlist/confirm` is not throttled | integration (`@TestPropertySource rate-limit.capacity=2`, distinct `X-Forwarded-For`) | `./gradlew test --tests "*WaitlistRateLimitIntegrationTest"` | ❌ Wave 0 |
| WAIT-03 | CORS: with `app.waitlist.allowed-origins` set, a preflight from that origin gets `Access-Control-Allow-Origin`; another origin gets none | integration (MockMvc `options()` + `Origin`) | `./gradlew test --tests "*WaitlistCorsIntegrationTest"` | ❌ Wave 0 |
| WAIT-04 | Admin list/convert: missing/wrong/blank-configured token → 401 with no side effects (checked before id lookup) | integration | `./gradlew test --tests "*WaitlistAdminIntegrationTest"` | ❌ Wave 0 |
| WAIT-04 | Convert CONFIRMED → 201 with code; an invite row exists with `code_hash = sha256(code)` and `referrer_user_id IS NULL`; entry INVITED; invite email sent to the entry address containing the code | integration | `./gradlew test --tests "*WaitlistAdminIntegrationTest"` | ❌ Wave 0 |
| WAIT-04 | Convert PENDING → 409, convert INVITED → 409, unknown id → 404; email send returns ERROR → rollback (no invite row, entry still CONFIRMED) | integration | `./gradlew test --tests "*WaitlistAdminIntegrationTest"` | ❌ Wave 0 |
| WAIT-04 | `GET /api/admin/waitlist?status=confirmed` returns only CONFIRMED entries; invalid status → 400 | integration | `./gradlew test --tests "*WaitlistAdminIntegrationTest"` | ❌ Wave 0 |
| (schema) | V24 applies on an empty DB with `ddl-auto=validate`; unique normalized_email, CHECK status | integration (Flyway on a private DB, as in `InviteMigrationTest`) | `./gradlew test --tests "*WaitlistMigrationTest"` | ❌ Wave 0 |
| (regression) | Extracted `AdminTokenGuard` keeps invite admin behavior | integration (existing) | `./gradlew test --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest"` | ✅ |
| (regression) | Existing auth rate limits unchanged | integration (existing) | `./gradlew test --tests "com.catspell.api.common.RateLimitIntegrationTest"` | ✅ |

### Sampling Rate
- **Per task commit:** `./gradlew compileKotlin -q` + `./gradlew test --tests "com.catspell.api.waitlist.*"`
- **Per wave merge:** `./gradlew test --tests "com.catspell.api.waitlist.*" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.common.*"`, then `./gradlew test`
- **Phase gate:** full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistEmailNormalizerTest.kt` — WAIT-03 normalization (plain JUnit, no Spring)
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt` — V24 schema; own DB name `waitlist_migration_test`
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt` — WAIT-01 validation + concurrency
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt` — WAIT-01 identical responses
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt` — WAIT-02
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt` — WAIT-03 per-IP (registered filter) + per-email
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistCorsIntegrationTest.kt` — CORS mapping
- [ ] `src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt` — WAIT-04 (`@TestPropertySource app.invite.admin-token=test-admin-secret`)
- [ ] `src/test/resources/application.yml` — add the `app.waitlist:` block (Pitfall 4)
- Framework install: none.

## Security Domain

`security_enforcement: true`, ASVS level 1, block on high.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | partial | Shared-secret `X-Admin-Token`, deny-by-default, `MessageDigest.isEqual` (extracted `AdminTokenGuard`) |
| V3 Session Management | no | Stateless; no sessions minted by waitlist flows |
| V4 Access Control | yes | Admin routes permitAll + guard as the sole boundary; guard runs before any lookup; public routes expose no data |
| V5 Input Validation | yes | `@NotBlank @Email @Size(max=255)`; normalizer never throws; `@RequestParam(required=false)` token → error redirect; UUID path var |
| V6 Cryptography | yes | `SecureRandom` 32-byte tokens, SHA-256 at rest (high-entropy tokens, so no salt or KDF needed); never hand-roll |
| V7 Error/Logging | yes | No raw token, raw code or full email in logs; `LoggingEmailSender` masks recipients and prints bodies only under `dev` |
| V11 Business Logic | yes | Anti-automation via per-IP + per-email Bucket4j; double opt-in; single-use state transitions through conditional UPDATEs |
| V13 API | yes | Explicit-origin CORS on `POST /api/waitlist` only; RFC 7807 errors; the 202 body reveals no membership |
| V14 Configuration | yes | Blank admin token denies all; blank CORS origins = none; redirect URLs from config only |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Membership enumeration through join response, status, timing, or 500 on race | Information Disclosure | Identical 202 body; silent per-email exhaustion; `ON CONFLICT` upsert; async after-commit send |
| Waitlist flooding / list poisoning by bots | Denial of Service / Tampering | Per-IP filter + per-email bucket; only CONFIRMED entries count or are convertible |
| `+tag` bypass of per-email limit or dedupe | Tampering | D-03 normalization on both keys |
| Confirm token guessing / replay / race | Spoofing / Elevation | 256-bit token, hash at rest, 7-day TTL, conditional single-use UPDATE |
| Open redirect via the confirm endpoint | Spoofing | `Location` only from config keys |
| Token leakage via Referer, caches or logs | Information Disclosure | `Referrer-Policy: no-referrer`, `Cache-Control: no-store` on the 302; never log the raw token |
| Admin secret brute force / timing | Spoofing | Constant-time compare; deny-by-default; operator uses a long random secret |
| Admin id probing | Information Disclosure | Guard before `findById` |
| Double convert → two invites for one entry | Tampering | Conditional `CONFIRMED→INVITED` UPDATE inside the convert transaction |
| Fabricated referral attribution | Tampering | `InviteService.create(null)` only (D-10) |
| Cross-origin abuse via permissive CORS | Spoofing | Explicit origin list, POST only, no credentials, never `*` |
| `X-Forwarded-For` spoofing / unbounded bucket map memory growth | DoS | **Accepted pre-existing risk** (new rate-limit infra out of scope). Rely on a proxy that overwrites XFF; record in SECURITY.md |

## Sources

### Primary (HIGH confidence, read this session)
- `src/main/kotlin/com/catspell/api/invite/service/InviteService.kt`, `invite/controller/InviteAdminController.kt`, `invite/model/Invite.kt`, `invite/model/InviteRepository.kt`
- `src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt`, `auth/service/PasswordResetService.kt`, `auth/model/EmailVerificationTokenRepository.kt`, `auth/controller/AuthController.kt`, `auth/model/AuthDtos.kt`
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`, `common/security/JwtAuthenticationFilter.kt`, `common/config/SecurityConfig.kt`, `common/exception/Exceptions.kt`, `common/exception/GlobalExceptionHandler.kt`
- `src/main/kotlin/com/catspell/api/email/service/EmailSender.kt`, `EmailVerificationEmailRenderer.kt`, `LoggingEmailSender.kt`; `moderation/event/ReportNotificationListener.kt`; `moderation/model/Report.kt`
- `src/main/resources/application.yml`, `src/test/resources/application.yml`, `db/migration/V16`, `V21`, `V23`; migration directory listing (V23 is latest → V24 next)
- `src/test/kotlin/com/catspell/api/BaseIntegrationTest.kt`, `invite/InviteMigrationTest.kt`, `invite/InviteAdminEndpointIntegrationTest.kt`, `auth/EmailVerificationIntegrationTest.kt`, `common/RateLimitIntegrationTest.kt`, `moderation/ReportNotificationIntegrationTest.kt`
- `build.gradle.kts`, `AGENTS.md`, `.planning/config.json`, `17-CONTEXT.md`, `REQUIREMENTS.md`, `STATE.md`, `research/ARCHITECTURE.md`, `research/PITFALLS.md`, Phase 16 `16-RESEARCH.md`

### Secondary (MEDIUM, official docs)
- [Spring Security CORS reference](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html): `CorsConfigurationSource` + `http.cors {}`, CORS before authorization
- [Spring Framework PathPattern javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/pattern/PathPattern.html): `{*path}` matches the bare prefix; `/**` zero-segment behavior not stated
- [Kotlin `String.lowercase()`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/lowercase.html): invariant locale
- [Hibernate Validator AbstractEmailValidator.java](https://raw.githubusercontent.com/hibernate/hibernate-validator/main/engine/src/main/java/org/hibernate/validator/internal/constraintvalidators/AbstractEmailValidator.java): empty string valid; split on last `@`

### Tertiary (LOW-MEDIUM, community reports)
- [pickpic #323](https://github.com/Patrick9263/pickpic/issues/323), [PhotoAlbum #53](https://github.com/Macrophage87/PhotoAlbum/issues/53), [prospera PR #11](https://github.com/j-vincent-chan/prospera/pull/11): mail scanners burning GET single-use links; the interstitial+POST fix

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. No new dependencies; versions read from `build.gradle.kts`.
- Architecture: HIGH. Every pattern is an in-repo precedent read this session; the upsert and CORS additions are standard Postgres and Spring.
- Pitfalls: HIGH for the codebase-derived ones (filter registration, Flyway-validate test, create-drop defaults, missing CORS, missing provider); MEDIUM for mail-scanner prevalence.

**Research date:** 2026-10-01
**Valid until:** 2026-10-31 (stable stack; in-repo facts valid until these files change)
