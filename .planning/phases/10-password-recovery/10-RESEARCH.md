# Phase 10: Password Recovery - Research

**Researched:** 2026-08-07
**Domain:** Kotlin/Spring Boot self-service password recovery (secure single-use reset tokens, transactional-email seam, enumeration-safe endpoints, service-layer rate limiting)
**Confidence:** HIGH

## Summary

Phase 10 adds a `POST /api/auth/forgot-password` → emailed single-use reset link → `POST /api/auth/reset-password` flow, plus a reusable `EmailSender` abstraction (interface + no-op/logging provider; concrete provider deferred). Every building block already exists in this repo as a proven pattern: the `PushProvider`/`LoggingPushProvider` pair is the exact template for `EmailSender`/`LoggingEmailSender`; `RefreshToken` + Flyway migration + `RefreshTokenRepository` is the template for the reset-token entity; `AuthService` already owns `revokeAllUserTokens`, `PasswordEncoder`, and `@Value` config injection; `RateLimitFilter` shows the Bucket4j idiom to mirror for the per-email guard. **No new external dependencies are required** — Bucket4j, BCrypt, JDK `SecureRandom`/`MessageDigest`, JPA, and SLF4J are all already on the classpath.

The one genuinely important technical decision is **how to hash the reset token**. CONTEXT.md and the design note suggest "reuse `PasswordEncoder` (BCrypt), same principle as `passwordHash`." That is the wrong tool here: BCrypt embeds a random per-hash salt, so the stored hash is non-deterministic and **cannot be looked up by hash** — you would have to load every outstanding token and `matches()` each one. The industry-standard and OWASP-aligned approach for a *high-entropy* reset token is a **deterministic SHA-256 hash** stored in a unique, indexed column, looked up directly on redemption. BCrypt exists to slow brute-force of *low-entropy* passwords; a 256-bit CSPRNG token needs only a preimage-resistant hash. This RESEARCH recommends SHA-256 (a decision that falls under "Claude's Discretion: the exact hashing helper" in CONTEXT.md) and flags the divergence for the planner.

The second important finding is a **factual correction to CONTEXT D-04**: `/forgot-password` is **not** rate-limited "for free." The existing `RateLimitFilter` is registered on URL pattern `/api/auth/*` but internally only limits an explicit `AUTH_PATHS` set of exactly three paths (`register`, `login`, `refresh`). New endpoints pass through unlimited. Likewise `SecurityConfig.permitAll(...)` and `JwtAuthenticationFilter.shouldNotFilter(...)` each hard-code the same three paths. The two new public endpoints must be added to **all three** locations or they will 401 (security), skip JWT bypass, and evade per-IP rate limiting.

**Primary recommendation:** Mirror the `push` package seam for `EmailSender`; create a `PasswordResetToken` JPA entity + Flyway `V15` migration + repository following the `RefreshToken` pattern; generate a 32-byte `SecureRandom` token, email the raw value, store its **SHA-256** hash (indexed, unique); consume it atomically inside a `@Transactional` reset method on `AuthService` that updates `passwordHash`, marks the token used, and calls `revokeAllUserTokens`; return generic `202` (forgot) and `200`-no-body (reset); add both endpoints to `SecurityConfig`, `JwtAuthenticationFilter`, and `RateLimitFilter.AUTH_PATHS`, and add a per-email Bucket4j guard in the service layer.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Reset link & email content**
- **D-01:** The reset link base URL is **environment-configured** (e.g. `app.reset-password-url`); the backend appends the raw token. It is a universal/deep link into the mobile app — **no web reset page** is built (there is no web frontend, and the mobile app lives in a separate repo).
- **D-02:** The email body is **rendered by the backend** (simple HTML/text template owned in this repo), not a provider-hosted template. Keeps content provider-agnostic so the concrete provider stays swappable.

**Provider scope this phase**
- **D-03:** Phase 10 ships **only the `EmailSender` abstraction + a no-op/logging provider** (satisfies EMAIL-01/EMAIL-02 for local dev + tests). Concrete provider (SendGrid/Postmark/Resend/SES) selection is **deferred** per `transactional-email-infra.md` — the first consumer builds the seam, later features reuse it.

**Rate limiting**
- **D-04:** **Reuse the existing per-IP `RateLimitFilter`** (already covers `/api/auth/*`, so `/forgot-password` is IP-limited for free) **and add a lightweight per-email Bucket4j guard** in the service layer, because email-bombing targets a specific address across many IPs. Reuses the Bucket4j library — no new rate-limiting infrastructure (matches REQUIREMENTS "Out of Scope").

**Reset response & session behavior**
- **D-05:** `POST /forgot-password` returns **202 Accepted** with a generic enumeration-safe body regardless of whether the email is registered.
- **D-06:** Reset token **TTL = 30 minutes**.
- **D-07:** `POST /reset-password` returns **200 with no tokens** — a successful reset **revokes all refresh tokens** (via existing `revokeAllUserTokens`) and forces a fresh login (no auto-login).

### Claude's Discretion
- Token entity/table name, column layout, and the exact hashing helper (mirror the password-hashing / refresh-token patterns already in `auth`).
- Per-email bucket capacity/refill values and the dedicated forgot-password IP capacity (tune to sane anti-abuse defaults).
- Package/file placement for `EmailSender` (mirror `push/service` layout).

### Deferred Ideas (OUT OF SCOPE)
- **Concrete transactional email provider selection + wiring** — deferred to when it's actually needed (per seed). Phase 10 ships only the abstraction + no-op/logging provider.
- Email verification (Phase 11) and logged-in credential changes (Phase 12) reuse this phase's email infrastructure — not in scope here.

> ⚠️ **Correction to D-04 (see Common Pitfalls #1):** `/forgot-password` is **NOT** rate-limited "for free." `RateLimitFilter` only limits an explicit hard-coded `AUTH_PATHS` set (`register`, `login`, `refresh`) — it does not limit arbitrary `/api/auth/*` paths. `/forgot-password` must be **added to `AUTH_PATHS`** to get per-IP limiting. The intent of D-04 (reuse the per-IP filter + add a per-email guard) is honored, but it requires a one-line edit, not zero work.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EMAIL-01 | Transactional email through a provider-abstracted `EmailSender` seam (swappable, stubbed in tests) | Architecture Patterns → "EmailSender seam (mirror PushProvider)"; Code Examples → `EmailSender`/`LoggingEmailSender` |
| EMAIL-02 | Env-configurable sending; no-op/logging provider for local dev + tests (no real network in tests) | `@ConditionalOnProperty(email.enabled, havingValue=false, matchIfMissing=true)` pattern from `LoggingPushProvider`; `PushProviderSelectionTest` template for provider-selection test |
| RECOV-01 | User requests reset by submitting email | `POST /api/auth/forgot-password` on `AuthController`; DTO `ForgotPasswordRequest{ email }` |
| RECOV-02 | User receives email with single-use, time-limited reset link | `SecureRandom` token + `EmailSender.send` + env-configured `app.reset-password-url` (D-01); TTL 30 min (D-06) |
| RECOV-03 | User sets new password via valid token + new password | `POST /api/auth/reset-password` → `AuthService.resetPassword(token, newPassword)` |
| RECOV-04 | Identical generic response whether or not email is registered (no enumeration) | Enumeration-safe `202` (D-05); service does lookup but always returns same body; timing-uniformity note in Security Domain |
| RECOV-05 | Tokens stored hashed, single-use, short TTL; used/expired rejected | SHA-256 hashed `token_hash` column (indexed unique), `used_at`/`expires_at`, atomic consume in `@Transactional` |
| RECOV-06 | On success, all active sessions (refresh tokens) revoked | Reuse `AuthService.revokeAllUserTokens(user)` (`AuthService.kt:101-105`) inside `resetPassword` |
| RECOV-07 | forgot-password rate-limited (per-email and/or per-IP) | Add `/forgot-password` to `RateLimitFilter.AUTH_PATHS` (per-IP) + new per-email Bucket4j guard in service layer (D-04) |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Forgot/reset HTTP endpoints, DTO validation | API / Controller (`AuthController`) | — | House style: all auth endpoints live under `@RequestMapping("/api/auth")` |
| Token generation, hashing, consumption, session revocation | Service (`AuthService` or new `PasswordResetService`) | Data (repositories) | Business logic + transaction boundary belong in the service layer, as with `register`/`refreshToken` |
| Reset-token persistence | Data (`PasswordResetToken` entity + repository + Flyway migration) | — | Mirrors `RefreshToken` durable storage pattern |
| Email delivery abstraction | Service (`email/service` package) | Infra (concrete provider, deferred) | Provider-agnostic seam mirroring `push/service`; concrete provider is an infra concern deferred to a later phase |
| Email body rendering | Service (backend-rendered template, D-02) | — | Content owned in-repo to stay provider-agnostic |
| Per-IP rate limiting | Web filter (`RateLimitFilter`) | — | Existing servlet-filter tier; cross-cutting, pre-controller |
| Per-email rate limiting (email-bomb guard) | Service | — | Needs the parsed email from the request body, which is only available after the filter tier; must live in the service (D-04) |
| Public-route auth bypass | Security config (`SecurityConfig`, `JwtAuthenticationFilter`) | — | Both new endpoints are unauthenticated and must be whitelisted in the security tier |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot (web, data-jpa, security, validation) | 4.0.6 | Endpoints, persistence, security, DTO validation | Already the app framework [VERIFIED: build.gradle.kts:34-40] |
| Kotlin | 2.4.0 (JVM 17) | Language | Project standard [VERIFIED: build.gradle.kts:4-6,16] |
| Bucket4j core | 8.10.1 | Per-email token-bucket rate limiting | Already used by `RateLimitFilter`; REQUIREMENTS mandates reuse (no new infra) [VERIFIED: build.gradle.kts:52] |
| Flyway (core + postgresql) | via Spring Boot BOM | `V15` reset-token table migration | Established migration mechanism; `V1..V14` exist [VERIFIED: build.gradle.kts:40-42, db/migration/] |
| BCrypt (`BCryptPasswordEncoder`) | Spring Security 7.1.0 | Hashing the **new password** only (not the token) | Already the `PasswordEncoder` bean [VERIFIED: SecurityConfig.kt:44] |
| JDK `java.security.SecureRandom` | JDK 17 | CSPRNG for the raw reset token | Standard CSPRNG; no dependency needed [CITED: OWASP Cryptographic Storage Cheat Sheet] |
| JDK `java.security.MessageDigest` (SHA-256) | JDK 17 | Deterministic hash of the reset token for storage/lookup | Preimage-resistant, indexable; correct for high-entropy tokens [CITED: crypto.stackexchange.com/q/25682] |
| SLF4J (`LoggerFactory`) | via Spring Boot | Logging in the no-op email provider | Matches `LoggingPushProvider` [VERIFIED: LoggingPushProvider.kt:11] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.util.Base64.getUrlEncoder().withoutPadding()` | JDK 17 | URL-safe encode the raw token bytes for the deep link | Encoding the 32 random bytes into the emailed link so it survives a URL |
| MockK | 1.13.11 | Mock `EmailSender` in service unit tests | Already the mocking lib; see `TokenPruningTest` mocking `PushProvider` [VERIFIED: TokenPruningTest.kt:36-40] |
| Testcontainers PostgreSQL | 1.20.6 | Integration tests against real Postgres | `BaseIntegrationTest` template [VERIFIED: BaseIntegrationTest.kt] |
| Spring `ApplicationContextRunner` | test scope | Verify no-op provider is selected when `email.enabled` unset/false | `PushProviderSelectionTest` template [VERIFIED: PushProviderSelectionTest.kt] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| SHA-256 deterministic hash of token | BCrypt via `PasswordEncoder` (as CONTEXT suggests) | BCrypt's random salt makes the hash non-deterministic → **cannot look up by hash**; forces an O(n) scan-and-`matches()` over all outstanding tokens. Unnecessary work-factor for a 256-bit CSPRNG value. **Rejected.** [CITED: crypto.stackexchange.com/q/25682, security.stackexchange.com/q/244836] |
| Plain Kotlin string-template email rendering | Thymeleaf (`spring-boot-starter-thymeleaf`) | Thymeleaf adds a dependency + template resources for a single, simple 2-part (HTML+text) email. D-02 says "simple HTML/text template" — a dedicated Kotlin renderer class needs **no new dependency** and stays trivially provider-agnostic. Recommend plain rendering; Thymeleaf only if templates proliferate. |
| Opaque random token + DB lookup | Signed JWT reset token | OWASP notes JWTs "can introduce additional vulnerability"; single-use/revocation is harder (JWTs are stateless) and D-07's revoke-on-use needs DB state anyway. **Rejected.** [CITED: OWASP Forgot Password Cheat Sheet] |

**Installation:** None. All required libraries are already declared in `build.gradle.kts`. No `npm/pip/cargo`-style install step; **no `## Package Legitimacy Audit` needed** (no external packages added this phase).

## Architecture Patterns

### System Architecture Diagram

```
FORGOT PASSWORD
  Client
    │ POST /api/auth/forgot-password { email }
    ▼
  RateLimitFilter (per-IP, Bucket4j)  ──[/forgot-password ∈ AUTH_PATHS]──▶ 429 if exceeded
    │
    ▼
  SecurityConfig.permitAll + JwtAuthenticationFilter.shouldNotFilter  (public route)
    │
    ▼
  AuthController.forgotPassword
    │
    ▼
  PasswordResetService.requestReset(email)
    ├─▶ per-email Bucket4j guard  ──[exceeded]──▶ skip send (still return 202)
    ├─▶ UserRepository.findByEmail(email)
    │        ├─ user absent ──▶ (do nothing)
    │        └─ user present:
    │             ├─ SecureRandom → raw token (32 bytes) → Base64url
    │             ├─ SHA-256(raw) → token_hash
    │             ├─ save PasswordResetToken{ user, token_hash, expires_at = now+30m }
    │             ├─ build reset link: ${app.reset-password-url}?token=<raw>
    │             ├─ render HTML+text body (backend-owned template)
    │             └─ EmailSender.send(EmailMessage)   (LoggingEmailSender in dev/test)
    └─▶ ALWAYS return generic 202 Accepted   (enumeration-safe, D-05)

RESET PASSWORD
  Client
    │ POST /api/auth/reset-password { token, newPassword }
    ▼
  (public route, same whitelist)
    ▼
  AuthController.resetPassword  (@Valid → newPassword @Size(min=8))
    ▼
  AuthService.resetPassword(rawToken, newPassword)   @Transactional
    ├─ SHA-256(rawToken) → hash
    ├─ repo.findByTokenHash(hash)  ─ absent / used_at != null / expired ──▶ InvalidTokenException (401)
    ├─ mark token used (used_at = now)          ← single-use, atomic within tx
    ├─ user.passwordHash = passwordEncoder.encode(newPassword); user.updatedAt = now
    ├─ revokeAllUserTokens(user)                 ← RECOV-06 / D-07
    └─ return 200, no body, no tokens            ← D-07 (forces fresh login)
```

### Recommended Project Structure
```
src/main/kotlin/com/catspell/api/
├── auth/
│   ├── controller/AuthController.kt          # add forgotPassword + resetPassword
│   ├── model/
│   │   ├── AuthDtos.kt                        # add ForgotPasswordRequest, ResetPasswordRequest, GenericMessageResponse
│   │   ├── PasswordResetToken.kt             # NEW entity (mirror RefreshToken.kt)
│   │   └── PasswordResetTokenRepository.kt   # NEW (mirror RefreshTokenRepository.kt)
│   └── service/
│       ├── AuthService.kt                     # add resetPassword(...); expose/reuse revokeAllUserTokens
│       └── PasswordResetService.kt           # NEW: forgot flow + per-email rate guard + token issuance
├── email/                                     # NEW package (mirror push/)
│   └── service/
│       ├── EmailSender.kt                     # interface + EmailMessage + EmailResult/status
│       ├── LoggingEmailSender.kt              # @ConditionalOnProperty(email.enabled=false, matchIfMissing=true)
│       └── PasswordResetEmailRenderer.kt      # backend-rendered HTML+text (D-02)
└── common/security/RateLimitFilter.kt         # add "/api/auth/forgot-password" to AUTH_PATHS

src/main/resources/db/migration/V15__create_password_reset_tokens_table.sql   # NEW
```

### Pattern 1: Provider Seam (mirror `PushProvider`)
**What:** An interface + value objects + a no-op logging implementation gated on an `@ConditionalOnProperty` flag, so tests and local dev never hit the network and the concrete provider is deferred.
**When to use:** For EMAIL-01/EMAIL-02.
**Example (adapt directly from the push package):**
```kotlin
// email/service/EmailSender.kt  — mirror push/service/PushProvider.kt
data class EmailMessage(
    val to: String,
    val subject: String,
    val htmlBody: String,
    val textBody: String
)
enum class EmailSendStatus { SUCCESS, ERROR }
data class EmailResult(val status: EmailSendStatus, val messageId: String? = null, val errorDetail: String? = null)

interface EmailSender {
    fun send(message: EmailMessage): EmailResult
}

// email/service/LoggingEmailSender.kt — mirror push/service/LoggingPushProvider.kt
@Component
@ConditionalOnProperty(name = ["email.enabled"], havingValue = "false", matchIfMissing = true)
class LoggingEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)
    override fun send(message: EmailMessage): EmailResult {
        log.info("[no-op email] to={} subject='{}'", maskEmail(message.to), message.subject)
        return EmailResult(EmailSendStatus.SUCCESS, messageId = "logged")
    }
    private fun maskEmail(email: String): String = /* local-part masking */ email.replaceBefore("@", "***")
}
```
*Source: `push/service/PushProvider.kt`, `push/service/LoggingPushProvider.kt` (VERIFIED in repo).* A future concrete provider mirrors `FcmPushProvider` with `@ConditionalOnProperty(havingValue = "true")` — **not built this phase (D-03)**.

### Pattern 2: Durable Token Entity + Flyway Migration (mirror `RefreshToken`)
**What:** JPA entity with UUID PK, `@ManyToOne` user FK, indexed columns, plus a hand-written Flyway migration.
**When to use:** For the reset-token store (RECOV-05).
**Example:**
```kotlin
// auth/model/PasswordResetToken.kt — mirror RefreshToken.kt (incl. equals/hashCode by id)
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) var user: User,
    @Column(name = "token_hash", nullable = false, unique = true) var tokenHash: String,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant,
    @Column(name = "used_at") var usedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant = Instant.now()
) { /* equals/hashCode by id, as in RefreshToken.kt:33-39 */ }
```
```sql
-- db/migration/V15__create_password_reset_tokens_table.sql  — mirror V2
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE UNIQUE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens(token_hash);
```
```kotlin
// auth/model/PasswordResetTokenRepository.kt — mirror RefreshTokenRepository.kt
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByTokenHash(tokenHash: String): PasswordResetToken?
    fun findAllByUserAndUsedAtIsNull(user: User): List<PasswordResetToken>   // to invalidate prior tokens on re-request
}
```
*Source: `RefreshToken.kt`, `RefreshTokenRepository.kt`, `V2__create_refresh_tokens_table.sql` (VERIFIED).* Next migration number is **V15** (V14 is the latest [VERIFIED: db/migration/]).

### Pattern 3: Secure Token Generation + Deterministic Hash
**What:** CSPRNG token, hash-before-store, raw-in-email-only.
**Example:**
```kotlin
private val secureRandom = SecureRandom()

fun generateRawToken(): String {
    val bytes = ByteArray(32)                       // 256 bits of entropy
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

fun hashToken(rawToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
    return HexFormat.of().formatHex(hash)           // deterministic → indexable/unique
}
```
*Source: OWASP Forgot Password / Cryptographic Storage Cheat Sheets [CITED]; crypto.stackexchange.com/q/25682 [CITED].*

### Pattern 4: Per-Email Bucket4j Guard (mirror `RateLimitFilter.createBucket`)
**What:** A `ConcurrentHashMap<String, Bucket>` keyed by normalized (lowercased/trimmed) email inside the service; consume 1 token per forgot-password request.
**When to use:** RECOV-07 / D-04 — stops email-bombing a single address across many IPs (which the per-IP filter cannot catch).
**Example:**
```kotlin
private val emailBuckets = ConcurrentHashMap<String, Bucket>()
private fun emailBucket(email: String): Bucket = emailBuckets.computeIfAbsent(email) {
    val bandwidth = Bandwidth.builder()
        .capacity(forgotPasswordPerEmailCapacity)                 // @Value, e.g. 3
        .refillIntervally(forgotPasswordPerEmailCapacity, Duration.ofHours(1))
        .build()
    Bucket.builder().addLimit(bandwidth).build()
}
// in requestReset(): if (!emailBucket(normalized).tryConsume(1)) return  // silently skip send, still 202
```
*Source: `RateLimitFilter.createBucket` (VERIFIED: RateLimitFilter.kt:69-75).* **Enumeration-safe behavior:** on per-email exhaustion, still return the generic `202` and simply do not send — never surface a 429 that would signal the address exists.

### Pattern 5: Atomic Single-Use Consumption
**What:** Look up by hash, validate (not used, not expired), mark used, update password, revoke sessions — all inside one `@Transactional` method so a double-submit can't reset twice.
**Example:**
```kotlin
@Transactional
fun resetPassword(rawToken: String, newPassword: String) {
    val hash = hashToken(rawToken)
    val token = passwordResetTokenRepository.findByTokenHash(hash) ?: throw InvalidTokenException()
    if (token.usedAt != null || token.expiresAt.isBefore(Instant.now())) throw InvalidTokenException()
    token.usedAt = Instant.now()
    val user = token.user
    user.passwordHash = passwordEncoder.encode(newPassword)!!
    user.updatedAt = Instant.now()
    userRepository.save(user)
    revokeAllUserTokens(user)                       // RECOV-06 / D-07
    // controller returns 200 with no body / no tokens (D-07)
}
```
*Source: `AuthService.refreshToken` transaction style (VERIFIED: AuthService.kt:62-88); `revokeAllUserTokens` (AuthService.kt:101-105).*

### Anti-Patterns to Avoid
- **BCrypt-hashing the reset token** — non-deterministic salt kills hash lookup; wrong tool for high-entropy values. Use SHA-256.
- **Storing the raw token** — a DB dump then becomes a live account-takeover vector. Store only the hash; raw lives only in the email.
- **Returning 404/different body/different status when the email is unknown** — enumeration leak. Always the same `202` body (D-05).
- **Returning 429 from the per-email guard** — leaks that the address exists; instead skip the send silently and return `202`.
- **Adding the endpoints to the controller only** — they will 401 (not in `SecurityConfig.permitAll`), be JWT-filtered (not in `JwtAuthenticationFilter.shouldNotFilter`), and evade per-IP limiting (not in `RateLimitFilter.AUTH_PATHS`). Update all three.
- **Auto-logging the user in after reset** — D-07 forbids it; return no tokens.
- **Logging the raw token or full email** — mask like `LoggingPushProvider.maskToken` (VERIFIED: LoggingPushProvider.kt:18-19).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Random token | Custom RNG / `Math.random`/`UUID` for the secret | `java.security.SecureRandom` (32 bytes) | Only a CSPRNG is unpredictable; `UUID.randomUUID` is 122 bits and semantically a token, acceptable but 32-byte SecureRandom is the OWASP-blessed choice |
| Token hashing | Custom hashing/XOR/truncation | JDK `MessageDigest` SHA-256 | Preimage-resistant, standard, deterministic for lookup |
| Rate limiting | New filter/counter/Redis | Existing Bucket4j (`RateLimitFilter` per-IP + service per-email) | REQUIREMENTS "Out of Scope": reuse Bucket4j, no new infra |
| Session revocation | New revoke logic | `AuthService.revokeAllUserTokens` | Already implemented and tested (AuthService.kt:101-105) |
| Password hashing (new password) | Anything custom | Injected `PasswordEncoder` (BCrypt) | Same encoder as register/login (SecurityConfig.kt:44) |
| Email provider abstraction | Ad-hoc SMTP call | `EmailSender` seam mirroring `PushProvider` | Consistent, swappable, testable; concrete provider deferred (D-03) |
| Problem/error responses | Custom JSON | RFC 7807 `ProblemDetail` via `GlobalExceptionHandler` | House style; `InvalidTokenException` already mapped to 401 (GlobalExceptionHandler.kt:60-64) |

**Key insight:** This phase is almost entirely *composition of existing patterns*. The only new primitive is SHA-256 token hashing; everything else is a copy-adapt of `push/`, `RefreshToken`, `RateLimitFilter`, and `AuthService`.

## Common Pitfalls

### Pitfall 1: Assuming `/forgot-password` is already rate-limited and public
**What goes wrong:** CONTEXT D-04 states `/forgot-password` is IP-limited "for free" via `/api/auth/*`. It is not. `RateLimitFilter` is *registered* on `/api/auth/*` (RateLimitFilter.kt:86) but its `doFilter` only limits paths in the hard-coded `AUTH_PATHS` set = `{register, login, refresh}` (RateLimitFilter.kt:24-28,35). Non-listed `/api/auth/*` paths fall through unlimited. Separately, `SecurityConfig` permits only those same three paths (SecurityConfig.kt:28) and `JwtAuthenticationFilter.shouldNotFilter` bypasses only those three (JwtAuthenticationFilter.kt:18-21).
**Why it happens:** Three independent whitelists each hard-code the same three paths.
**How to avoid:** In the plan, add **both** `/api/auth/forgot-password` and `/api/auth/reset-password` to: (1) `SecurityConfig.permitAll(...)`, (2) `JwtAuthenticationFilter.shouldNotFilter(...)`, and (3) add at least `/api/auth/forgot-password` to `RateLimitFilter.AUTH_PATHS` (RECOV-07). Add a verification step that hits `/forgot-password` without a JWT and expects `202`, not `401`.
**Warning signs:** Integration test for forgot-password returns `401`; per-IP 429 never triggers on forgot-password.

### Pitfall 2: BCrypt-hashing the token breaks lookup
**What goes wrong:** Following "reuse `PasswordEncoder`" literally produces a salted, non-deterministic hash you cannot query by; redemption then requires scanning every token and `matches()`-ing.
**Why it happens:** Conflating password storage (low-entropy, needs slow salted hash) with token storage (high-entropy, needs fast deterministic preimage-resistant hash).
**How to avoid:** Use SHA-256 with a unique index on `token_hash`; look up directly. Reserve `PasswordEncoder` for the *new password*.
**Warning signs:** Repository has no `findByTokenHash`; code loads all tokens for a user and loops.

### Pitfall 3: Enumeration leak via status/body/timing
**What goes wrong:** Different response, or a much faster response, when the email is unknown lets attackers enumerate accounts.
**Why it happens:** Early-return on "user not found," or per-email 429, or skipping the (slow) hashing/DB work for unknown emails.
**How to avoid:** Always return the same `202` + generic body (D-05); do the work (or equivalent) regardless; never 429 the per-email guard to the client. Timing uniformity is an OWASP recommendation (see Security Domain) — for ASVS L1 the identical-response requirement (RECOV-04) is the hard gate; note timing as a defense-in-depth item.
**Warning signs:** `forgotPassword` has an `if (user == null) return notFound()` branch.

### Pitfall 4: Reset-token table missing from test schema
**What goes wrong:** Tests use `ddl-auto: create-drop` with **Flyway disabled** (test/resources/application.yml:3-7), so the schema is generated from JPA entities, while production uses the Flyway `V15` migration. If the entity and the migration drift, tests pass but production `ddl-auto: validate` (main application.yml:9) fails at startup.
**Why it happens:** Two sources of truth for schema (entities in tests, migrations in prod).
**How to avoid:** Keep `PasswordResetToken` column names/types exactly matching `V15`. Add a verification step running the app (or an integration test with Flyway) against Postgres so `validate` passes. This mirrors how `RefreshToken`/`V2` coexist today.
**Warning signs:** App boots in tests but fails `SchemaManagementException` in a Flyway-enabled context.

### Pitfall 5: Non-unique token / race on double-submit
**What goes wrong:** Two rapid `reset-password` calls with the same token both succeed, or two issued tokens collide.
**How to avoid:** `UNIQUE` index on `token_hash`; consume inside a single `@Transactional` that checks-and-sets `used_at`. Optionally invalidate prior unused tokens for the user when a new one is requested (`findAllByUserAndUsedAtIsNull`).
**Warning signs:** `used_at` checked in a separate transaction from the update.

## Runtime State Inventory

**Not applicable** — Phase 10 is a greenfield feature addition (new endpoints, new entity/table, new email seam), not a rename/refactor/migration of existing runtime state. No existing stored data, service config, OS registrations, secrets, or build artifacts are being renamed. The only stateful additions are net-new: the `password_reset_tokens` table (created by `V15`) and two new env vars (`app.reset-password-url`, optional `email.enabled`). None require data migration of existing records.

## Code Examples

### DTOs + Controller (mirror existing `AuthController` conventions)
```kotlin
// auth/model/AuthDtos.kt (additions)
data class ForgotPasswordRequest(
    @field:Email(message = "must be a valid email address")
    val email: String
)
data class ResetPasswordRequest(
    val token: String,
    @field:Size(min = 8, message = "must be at least 8 characters")
    val newPassword: String
)
data class GenericMessageResponse(val message: String)
```
```kotlin
// auth/controller/AuthController.kt (additions) — @SecurityRequirements marks routes public in OpenAPI
@SecurityRequirements
@PostMapping("/forgot-password")
fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<GenericMessageResponse> {
    passwordResetService.requestReset(request.email)
    return ResponseEntity.accepted().body(   // 202, D-05
        GenericMessageResponse("If an account exists for that email, a reset link has been sent.")
    )
}

@SecurityRequirements
@PostMapping("/reset-password")
fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Void> {
    authService.resetPassword(request.token, request.newPassword)
    return ResponseEntity.ok().build()       // 200, no body/tokens, D-07
}
```
*Source: `AuthController.kt:21-40` (`@SecurityRequirements`, `@Valid`, `ResponseEntity` conventions) VERIFIED.*

### Provider-selection test (mirror `PushProviderSelectionTest`)
```kotlin
class EmailSenderSelectionTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(LoggingEmailSender::class.java)
    @Test fun `logging sender selected when email disabled or missing`() {
        runner.run { ctx -> assertThat(ctx).hasSingleBean(LoggingEmailSender::class.java) }
    }
}
```
*Source: `PushProviderSelectionTest.kt` VERIFIED — this is the EMAIL-02 "no real network in tests" proof.*

### Config wiring
```yaml
# application.yml (additions)
app:
  reset-password-url: ${RESET_PASSWORD_URL:catspell://reset-password}   # D-01 deep link
email:
  enabled: ${EMAIL_ENABLED:false}                                        # D-03 no-op default
```
```
# .env.example (additions)
# Password reset deep link base (mobile universal/deep link; backend appends ?token=...)
RESET_PASSWORD_URL=catspell://reset-password
# EMAIL_ENABLED=true activates a real provider (deferred); false = no-op logging sender.
EMAIL_ENABLED=false
```
*Source: `@Value` injection style in `AuthService.kt:22` and `RateLimitFilter.kt:80`; `application.yml` `push.enabled` block:30-33; `.env.example` PUSH block VERIFIED.*

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Store raw reset token in DB | Store SHA-256 hash; raw only in email | Long-standing OWASP guidance | DB leak no longer yields usable tokens |
| Email/expose new password | Email a single-use tokenized link | Long-standing | Passwords never travel/rest in plaintext |
| BCrypt everything | BCrypt for passwords, SHA-256 for high-entropy tokens | Long-standing crypto guidance | Correct tool per entropy; enables hash lookup |
| Different response for unknown email | Identical generic response | OWASP Forgot Password Cheat Sheet | No account enumeration |

**Deprecated/outdated:** None specific to this stack. Bucket4j 8.x builder API (`Bandwidth.builder().capacity().refillIntervally()`) is current and already in use (RateLimitFilter.kt:70-73).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | SHA-256 (not BCrypt) is the right token hash — treated as the "hashing helper" discretion granted in CONTEXT | Summary / Standard Stack / Pitfall 2 | If the user insists on BCrypt for consistency, plan must add a per-user token scan on redemption (O(n) `matches`) instead of indexed lookup. Recommend confirming SHA-256 with the user. |
| A2 | Per-email guard should **skip send + still 202** on exhaustion (not 429) to preserve enumeration safety | Pattern 4 / Pitfall 3 | If the product wants an explicit 429 for abuse feedback, that reintroduces an enumeration signal on the per-email path; needs a product call. |
| A3 | Per-email capacity ≈ 3/hour, per-IP forgot-password reuses default capacity (10/min) | Pattern 4 | Values are Claude's discretion per CONTEXT; wrong values are easily tuned via `@Value`, low risk. |
| A4 | Reset deep link is passed as `?token=<raw>` query param on a `catspell://` scheme URL | Config wiring | The mobile deep-link scheme/format is owned by the separate mobile repo; the exact scheme string is a placeholder to confirm. Backend only needs the env-configured base + appended token. |
| A5 | New endpoints must be added to `RateLimitFilter.AUTH_PATHS`, `SecurityConfig`, and `JwtAuthenticationFilter` (correcting D-04's "for free" claim) | User Constraints note / Pitfall 1 | Verified against source; low risk. If missed, endpoints 401 and evade rate limiting. |
| A6 | `email.enabled` (mirroring `push.enabled`) is the config flag name for provider selection | Pattern 1 / Config | Naming is cosmetic (Claude's discretion on placement); low risk. |

## Open Questions

1. **Hash algorithm confirmation (SHA-256 vs BCrypt).**
   - What we know: CONTEXT suggests reusing `PasswordEncoder`; CONTEXT also grants "the exact hashing helper" as discretion. OWASP + crypto guidance strongly favor SHA-256 for high-entropy tokens.
   - What's unclear: Whether the user reads "mirror the password-hashing pattern" as a hard requirement to use BCrypt.
   - Recommendation: Proceed with SHA-256 (correct + enables indexed lookup); note the rationale in the plan so the reviewer can object if they truly want BCrypt.

2. **Mobile deep-link URL format (D-01).**
   - What we know: It's an env-configured universal/deep link into the mobile app; no web page.
   - What's unclear: Exact scheme/host and whether the token goes as a query param or path segment.
   - Recommendation: Make it fully env-driven (`RESET_PASSWORD_URL`) and append `?token=`; the mobile repo owns the concrete scheme. Placeholder `catspell://reset-password`.

3. **Cleanup of expired/used tokens.**
   - What we know: Not in scope of the requirements; `RefreshToken` has no scheduled purge either.
   - What's unclear: Whether a scheduled purge is desired.
   - Recommendation: Out of scope for Phase 10; rows are small and harmless. Optionally invalidate a user's prior unused tokens when they request a new one (already covered by `findAllByUserAndUsedAtIsNull`).

## Environment Availability

**Step 2.6: SKIPPED for new external tooling** — Phase 10 adds **no new external dependency, service, or CLI**. All libraries (Bucket4j, Spring Security/BCrypt, JPA/Flyway, JDK `SecureRandom`/`MessageDigest`, SLF4J, MockK, Testcontainers) are already declared and in use. The concrete email provider that *would* introduce an external dependency (SendGrid/Postmark/etc.) is **explicitly deferred (D-03)**; this phase ships only the no-op logging sender, which needs nothing external.

For completeness, the existing test/runtime dependency already relied upon:

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker/Podman (Testcontainers) | Integration tests (`BaseIntegrationTest`) | Assumed present (existing test suite already requires it) | — | build.gradle.kts auto-detects podman socket (build.gradle.kts:68-77) |
| PostgreSQL (PostGIS image) | Prod + integration tests | Provided via Testcontainers in tests | postgis/postgis:16-3.4 | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None new this phase.

## Validation Architecture

> `workflow.nyquist_validation` is `true` (config.json:20) — section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (JUnit Platform) + Spring Boot Test + MockMvc + MockK 1.13.11 + AssertJ; Testcontainers 1.20.6 |
| Config file | `build.gradle.kts` (`tasks.withType<Test> { useJUnitPlatform() }`, lines 65-78); `src/test/resources/application.yml` |
| Quick run command | `./gradlew test --tests "com.catspell.api.auth.*"` (scoped to auth package) |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EMAIL-01 | `EmailSender` seam send() invoked; message shape correct | unit | `./gradlew test --tests "*EmailSenderContractTest"` | ❌ Wave 0 |
| EMAIL-02 | No-op `LoggingEmailSender` selected when `email.enabled` unset/false; no network | unit (ApplicationContextRunner) | `./gradlew test --tests "*EmailSenderSelectionTest"` | ❌ Wave 0 |
| RECOV-01 | `POST /forgot-password` accepts `{email}`, returns 202 | integration (MockMvc) | `./gradlew test --tests "*PasswordResetIntegrationTest"` | ❌ Wave 0 |
| RECOV-02 | Reset token issued, hashed row persisted, email sent with link containing raw token | integration + unit | `./gradlew test --tests "*PasswordResetIntegrationTest"` | ❌ Wave 0 |
| RECOV-03 | Valid token + newPassword sets new hash; can log in with new password | integration | `./gradlew test --tests "*PasswordResetIntegrationTest"` | ❌ Wave 0 |
| RECOV-04 | Identical 202 body for registered vs unregistered email | integration | `./gradlew test --tests "*PasswordResetIntegrationTest"` | ❌ Wave 0 |
| RECOV-05 | Token stored hashed (not raw); used token rejected; expired token rejected | integration + unit | `./gradlew test --tests "*PasswordResetIntegrationTest"` | ❌ Wave 0 |
| RECOV-06 | After reset, prior refresh tokens revoked (old refresh → 401/invalid) | integration | `./gradlew test --tests "*PasswordResetIntegrationTest"` | ❌ Wave 0 |
| RECOV-07 | Per-IP limit on forgot-password (add to AUTH_PATHS) + per-email guard caps sends | integration | `./gradlew test --tests "*RateLimit*"` / `*PasswordReset*"` | ⚠️ Partial (extend `RateLimitIntegrationTest.kt`) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.catspell.api.auth.*"` and `--tests "*Email*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full `./gradlew test` green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/catspell/api/email/EmailSenderSelectionTest.kt` — covers EMAIL-02 (mirror `PushProviderSelectionTest.kt`)
- [ ] `src/test/kotlin/com/catspell/api/email/EmailSenderContractTest.kt` — covers EMAIL-01 (mirror `PushProviderContractTest.kt`)
- [ ] `src/test/kotlin/com/catspell/api/auth/PasswordResetIntegrationTest.kt` — covers RECOV-01..06 (extend `BaseIntegrationTest`, mirror `RefreshTokenIntegrationTest.kt`)
- [ ] Extend `src/test/kotlin/com/catspell/api/common/RateLimitIntegrationTest.kt` with a `forgot-password` case (RECOV-07 per-IP) + a per-email guard test
- [ ] Test fixture: an `EmailSender` capture double (MockK or a recording fake) so tests can assert the reset link/token without a real send — mirror how `TokenPruningTest` mocks `PushProvider` (TokenPruningTest.kt:36-40)

*(Framework itself is fully present — only new test files are needed.)*

## Security Domain

> `security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: high` (config.json:42-44).

### Applicable ASVS Categories (L1)
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication (incl. credential recovery) | yes | OWASP Forgot Password flow: CSPRNG single-use, time-limited, hashed token; identical generic response; rate limiting |
| V3 Session Management | yes | Revoke all refresh tokens on reset (`revokeAllUserTokens`, D-07/RECOV-06); no auto-login |
| V4 Access Control | yes | Endpoints public but do not act until a valid token is presented; token bound to a specific `user_id` in DB (never trust client-supplied identity) |
| V5 Input Validation | yes | Jakarta Bean Validation: `@Email` on forgot request, `@Size(min=8)` on new password (mirrors `RegisterRequest` AuthDtos.kt:6-12); `@Valid` in controller |
| V6 Cryptography | yes | `SecureRandom` (CSPRNG) for token; SHA-256 (`MessageDigest`) for storage hash; BCrypt (`PasswordEncoder`) for the new password — never hand-roll |
| V7 Error/Logging | yes | RFC 7807 `ProblemDetail` via `GlobalExceptionHandler`; mask email/token in logs; do not log raw tokens |

### Known Threat Patterns for Kotlin/Spring password recovery
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Account enumeration via forgot-password | Information Disclosure | Identical `202` body + status regardless of account existence (D-05/RECOV-04); avoid per-email 429 leak; consider uniform response time |
| Reset-token brute force | Spoofing / Tampering | 256-bit CSPRNG token; short 30-min TTL (D-06); per-IP + per-email rate limiting (RECOV-07) |
| Token theft via DB leak | Information Disclosure | Store SHA-256 hash only; raw token exists solely in the email trust boundary (RECOV-05) |
| Token replay / double-use | Tampering | Single-use: mark `used_at` and reject used tokens atomically inside `@Transactional`; UNIQUE index (RECOV-05) |
| Session persistence after compromise | Elevation of Privilege | Revoke ALL refresh tokens on successful reset (RECOV-06/D-07) |
| Email bombing / provider-cost abuse | Denial of Service | Per-IP `RateLimitFilter` (add path to AUTH_PATHS) + per-email Bucket4j guard (D-04/RECOV-07) |
| SQL injection on token lookup | Tampering | Spring Data JPA derived query `findByTokenHash` — parameterized, no raw SQL |
| Cross-account reset via client-supplied id | Elevation of Privilege | Never accept a user id from the client; derive user strictly from the DB token→user FK |

## Sources

### Primary (HIGH confidence)
- Repository source (VERIFIED via Read/grep): `AuthService.kt`, `AuthController.kt`, `User.kt`, `RefreshToken.kt`, `RefreshTokenRepository.kt`, `UserRepository.kt`, `PushProvider.kt`, `LoggingPushProvider.kt`, `FcmPushProvider.kt`, `PushSendService.kt`, `RateLimitFilter.kt`, `SecurityConfig.kt`, `JwtAuthenticationFilter.kt`, `GlobalExceptionHandler.kt`, `Exceptions.kt`, `AuthDtos.kt`, `BaseIntegrationTest.kt`, `RateLimitIntegrationTest.kt`, `PushProviderSelectionTest.kt`, `FcmSmokeTest.kt`, `build.gradle.kts`, `application.yml` (main+test), `.env.example`, `db/migration/V1,V2,V14`.
- OWASP Forgot Password Cheat Sheet — CSPRNG, single-use, expiry, hashed storage, identical response, rate limit. https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html [CITED]
- OWASP Email Validation & Verification Cheat Sheet — password-reset recommendations (single-use, time-limited, no enumeration, rate limit). [CITED]

### Secondary (MEDIUM confidence)
- crypto.stackexchange.com/q/25682 — "Don't hash reset token with BCrypt; use SHA-256 (no salt) for deterministic lookup on high-entropy tokens." [CITED]
- security.stackexchange.com/q/244836 — BCrypt is for low-entropy passwords; a single SHA-256 round suffices for reset tokens. [CITED]
- Spring Security Password Storage reference — BCrypt/salting rationale (confirms BCrypt is for passwords). https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html [CITED]

### Tertiary (LOW confidence)
- securepatterns.dev / encryptcodec.com secure-reset-flow write-ups — corroborate 32-byte CSPRNG + SHA-256 + atomic conditional consume + revoke sessions. Directionally consistent with OWASP; used only as corroboration. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified present in `build.gradle.kts`; no new packages.
- Architecture: HIGH — every pattern is a direct copy-adapt of verified in-repo code (`push/`, `RefreshToken`, `RateLimitFilter`, `AuthService`).
- Pitfalls: HIGH — pitfalls #1 and #4 verified directly against source; #2/#3/#5 backed by OWASP + crypto references.
- Security: HIGH — OWASP Forgot Password Cheat Sheet is the authoritative source and maps 1:1 to the requirements.

**Research date:** 2026-08-07
**Valid until:** 2026-09-06 (stable stack; ~30 days). Re-verify only if the email provider is chosen (out of scope this phase) or Spring Boot/Bucket4j majors change.
