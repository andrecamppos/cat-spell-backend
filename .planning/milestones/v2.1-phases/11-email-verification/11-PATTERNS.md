# Phase 11: Email Verification - Pattern Map

**Mapped:** 2026-08-11
**Files analyzed:** 13 (create + modify)
**Analogs found:** 13 / 13 (every new/modified file has a Phase 10 analog)

> This phase is a near-exact mirror of the already-built **Phase 10 (password
> recovery)**. Every new file has a direct analog; every modified file has a
> Phase 10 change to copy line-for-line. Stack: Kotlin 2.4 / Spring Boot 4.0.6 /
> Spring Data JPA / PostgreSQL 16 / Flyway / Bucket4j. Config via `@Value` env
> injection (no `@ConfigurationProperties`), per AGENTS.md.

---

## Flyway version status (READ THIS FIRST)

**Highest migration currently present: `V15`** (`V15__create_password_reset_tokens_table.sql`).
Migrations are strictly sequential `V1..V15`. Full list confirmed on disk:
`V1..V15`, no gaps.

**Next numbers for this phase (per CONTEXT.md D-09 + Claude's Discretion):**
- **`V16__create_email_verification_tokens_table.sql`** — new token table (mirror V15).
- **`V17__add_email_verified_at_to_users.sql`** — add nullable column + grandfather backfill.

---

## File Classification

| New/Modified File | Action | Role | Data Flow | Closest Analog | Match |
|-------------------|--------|------|-----------|----------------|-------|
| `auth/service/EmailVerificationService.kt` | create | service | request-response | `auth/service/PasswordResetService.kt` | exact |
| `auth/model/EmailVerificationToken.kt` | create | model (entity) | CRUD | `auth/model/PasswordResetToken.kt` | exact |
| `auth/model/EmailVerificationTokenRepository.kt` | create | model (repository) | CRUD | `auth/model/PasswordResetTokenRepository.kt` | exact |
| `email/service/EmailVerificationEmailRenderer.kt` | create | service (renderer) | transform | `email/service/PasswordResetEmailRenderer.kt` | exact |
| `auth/model/AuthDtos.kt` (add DTOs) | modify | model (DTO) | request-response | existing `ForgotPasswordRequest` / `ResetPasswordRequest` | exact |
| `common/exception/Exceptions.kt` (add exception) | modify | exception | — | `InvalidCredentialsException` / `InvalidTokenException` | exact |
| `common/exception/GlobalExceptionHandler.kt` (add handler) | modify | exception handler | request-response | `handleInvalidCredentials` (403 variant) | role-match |
| `auth/service/AuthService.kt` (`register` + `login`) | modify | service | request-response | its own `register`/`login`/`resetPassword` | self |
| `auth/controller/AuthController.kt` (2 endpoints) | modify | controller | request-response | `forgotPassword` / `resetPassword` handlers | exact |
| `common/config/SecurityConfig.kt` (whitelist) | modify | config | request-response | existing `permitAll()` line | exact |
| `common/security/JwtAuthenticationFilter.kt` (whitelist) | modify | middleware | request-response | existing `shouldNotFilter` chain | exact |
| `common/security/RateLimitFilter.kt` (AUTH_PATHS) | modify | middleware | request-response | existing `AUTH_PATHS` set | exact |
| `auth/model/User.kt` + `V17` migration | modify + create | model + migration | CRUD | `User.kt` timestamp columns + `V15` | exact |
| `resources/application.yml` (`app.verify-email-url`) | modify | config | — | `app.reset-password-url` | exact |

**Package placement (mirror Phase 10 exactly):** token entity/repo →
`com.catspell.api.auth.model`; verification service →
`com.catspell.api.auth.service`; email renderer →
`com.catspell.api.email.service`.

---

## Pattern Assignments

### `auth/service/EmailVerificationService.kt` (service, request-response) — CREATE

**Analog:** `src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt` (full file, lines 1-95)

This is the **primary template**. Clone its shape and rename Reset→Verification.

**Imports + constructor injection** (lines 1-30) — `@Value` env config with defaults,
`SecureRandom`, `MessageDigest`, `Base64`, `HexFormat`, Bucket4j:
```kotlin
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailSender: EmailSender,
    private val passwordResetEmailRenderer: PasswordResetEmailRenderer,
    @Value("\${app.forgot-password.per-email-capacity:3}") private val perEmailCapacity: Long,
    @Value("\${app.forgot-password.per-email-refill-hours:1}") private val perEmailRefillHours: Long,
    @Value("\${app.reset-token.ttl-minutes:30}") private val resetTokenTtlMinutes: Long
) {
```
> For verification: use config keys like `app.resend-verification.per-email-capacity`,
> `app.resend-verification.per-email-refill-hours`, and **`app.verify-token.ttl-hours:24`**
> (D-08 — 24h, not 30min; use `ChronoUnit.HOURS`).

**Per-email Bucket4j guard** (lines 32-42) — copy verbatim:
```kotlin
private val secureRandom = SecureRandom()
private val emailBuckets = ConcurrentHashMap<String, Bucket>()

private fun emailBucket(normalizedEmail: String): Bucket = emailBuckets.computeIfAbsent(normalizedEmail) {
    val bandwidth = Bandwidth.builder()
        .capacity(perEmailCapacity)
        .refillIntervally(perEmailCapacity, Duration.ofHours(perEmailRefillHours))
        .build()
    Bucket.builder().addLimit(bandwidth).build()
}
```

**Enumeration-safe flow + prior-token invalidation** (lines 44-82) — this is the
core of the `resendVerification(email)` method (D-04/D-05). Copy the structure,
adding the "already verified → return normally" branch:
```kotlin
fun requestReset(email: String) {
    val normalizedEmail = email.trim().lowercase()

    // Per-email guard: on exhaustion, silently skip the send and still return
    // normally — never surface a 429 or any existence signal.
    if (!emailBucket(normalizedEmail).tryConsume(1)) {
        return
    }

    val user = userRepository.findByEmail(email) ?: return
    // >>> FOR VERIFICATION: also `if (user.emailVerifiedAt != null) return` here
    //     (already-verified must be indistinguishable from unknown — D-04).

    // Invalidate any prior unused tokens so only the freshest link is usable.
    val priorTokens = passwordResetTokenRepository.findAllByUserAndUsedAtIsNull(user)
    if (priorTokens.isNotEmpty()) {
        val now = Instant.now()
        priorTokens.forEach { it.usedAt = now }
        passwordResetTokenRepository.saveAll(priorTokens)
    }

    val rawToken = generateRawToken()
    val resetToken = PasswordResetToken(
        user = user,
        tokenHash = hashToken(rawToken),
        expiresAt = Instant.now().plus(resetTokenTtlMinutes, ChronoUnit.MINUTES)
    )
    passwordResetTokenRepository.save(resetToken)

    val message = passwordResetEmailRenderer.render(user.email, rawToken)
    emailSender.send(message)
}
```

**Token gen + hash helpers** (lines 84-94) — copy verbatim (32 random bytes →
Base64url no-padding; SHA-256 → hex):
```kotlin
private fun generateRawToken(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun hashToken(rawToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
    return HexFormat.of().formatHex(hash)
}
```

> **Design decision for the planner:** the *verify* action (mark verified +
> claim token) can live either on this service or on `AuthService` (like
> `resetPassword` lives on `AuthService`). Recommended: mirror Phase 10 — put
> the token-claim/verify logic as an `AuthService.verifyEmail(rawToken)` method
> (see AuthService section), and keep `EmailVerificationService` focused on the
> enumeration-safe **resend/issue** path. Either is defensible; be consistent.

---

### `auth/model/EmailVerificationToken.kt` (entity, CRUD) — CREATE

**Analog:** `src/main/kotlin/com/catspell/api/auth/model/PasswordResetToken.kt` (full file, lines 1-37)

Copy verbatim, rename class + `@Table(name = "email_verification_tokens")`.
Identical columns: `id` (UUID gen), `user` (LAZY `@ManyToOne`, `user_id` NOT NULL),
`token_hash` (unique), `expires_at`, nullable `used_at`, `created_at`.
Keep the id-based `equals`/`hashCode`.
```kotlin
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "used_at")
    var usedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordResetToken) return false
        return id != null && id == other.id
    }
    override fun hashCode(): Int = javaClass.hashCode()
}
```

---

### `auth/model/EmailVerificationTokenRepository.kt` (repository, CRUD) — CREATE

**Analog:** `src/main/kotlin/com/catspell/api/auth/model/PasswordResetTokenRepository.kt` (full file, lines 1-23)

Copy verbatim. Keep all three methods — the atomic single-use `markUsed` guard
is essential (closes the read-check-write race):
```kotlin
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByTokenHash(tokenHash: String): PasswordResetToken?
    fun findAllByUserAndUsedAtIsNull(user: User): List<PasswordResetToken>

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    fun markUsed(@Param("id") id: UUID, @Param("now") now: Instant): Int
}
```

---

### `email/service/EmailVerificationEmailRenderer.kt` (renderer, transform) — CREATE

**Analog:** `src/main/kotlin/com/catspell/api/email/service/PasswordResetEmailRenderer.kt` (full file, lines 1-43)

Copy verbatim; swap the `@Value` key to **`app.verify-email-url`** and rewrite the
copy for verification. Same `render(recipientEmail, rawToken)` → `EmailMessage` shape.
The link is built as `"$verifyEmailUrl?token=$rawToken"`.
```kotlin
@Component
class PasswordResetEmailRenderer(
    @Value("\${app.reset-password-url}") private val resetPasswordUrl: String
) {
    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val resetLink = "$resetPasswordUrl?token=$rawToken"
        val subject = "Reset your Cat Spell password"
        val htmlBody = """...""".trimIndent()
        val textBody = """...""".trimIndent()
        return EmailMessage(to = recipientEmail, subject = subject, htmlBody = htmlBody, textBody = textBody)
    }
}
```

**The EmailSender seam is reused AS-IS — do NOT create a new sender.**
- `email/service/EmailSender.kt` (lines 1-20): `EmailMessage(to, subject, htmlBody, textBody)`,
  `EmailResult`, `interface EmailSender { fun send(message): EmailResult }`.
- `email/service/LoggingEmailSender.kt` (lines 1-26): `@ConditionalOnProperty(name=["email.enabled"], havingValue="false", matchIfMissing=true)` no-op provider with `maskEmail`. This satisfies EMAIL-02 (no real sends in dev/tests). Verification renderer plugs into the same `emailSender.send(...)` call.

---

### `auth/model/AuthDtos.kt` — MODIFY (add request/response DTOs)

**Analog:** existing DTOs in same file (`src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt`, lines 28-42).

Add, mirroring `ResetPasswordRequest` / `ForgotPasswordRequest` / `GenericMessageResponse`:
```kotlin
data class ForgotPasswordRequest(
    @field:Email(message = "must be a valid email address")
    val email: String
)

data class ResetPasswordRequest(
    val token: String,
    @field:Size(min = 8, message = "must be at least 8 characters")
    val newPassword: String
)

data class GenericMessageResponse(
    val message: String
)
```
> New DTOs needed: `VerifyEmailRequest(val token: String)` and
> `ResendVerificationRequest(@field:Email val email: String)`. Reuse the existing
> `GenericMessageResponse` for the 202/generic bodies.
> **Note on register (D-01):** register now returns **201 with no tokens** — reuse
> `GenericMessageResponse` for its new "check your email" body instead of `AuthResponse`.

---

### `common/exception/Exceptions.kt` + `GlobalExceptionHandler.kt` — MODIFY (403 EMAIL_NOT_VERIFIED)

**Analog (exception):** `Exceptions.kt` lines 5-7 (`InvalidCredentialsException`, `InvalidTokenException`).
```kotlin
class InvalidCredentialsException(message: String = "Invalid credentials") : RuntimeException(message)
class InvalidTokenException(message: String = "Invalid or expired token") : RuntimeException(message)
```
Add:
```kotlin
class EmailNotVerifiedException(message: String = "Email address not verified") : RuntimeException(message)
```

**Analog (handler):** `GlobalExceptionHandler.kt` lines 53-65 (`handleInvalidCredentials` / `handleInvalidToken`).
These return a **RFC 7807 `ProblemDetail`**. For the 403 with machine-readable code
(D-03), copy the shape but use `HttpStatus.FORBIDDEN` and add the `code` property so
the mobile app can route:
```kotlin
@ExceptionHandler(InvalidCredentialsException::class)
fun handleInvalidCredentials(ex: InvalidCredentialsException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials")
    problem.title = "Unauthorized"
    return problem
}
```
New handler to add (mirrors above; note `setProperty("code", ...)` — same technique
`handleProfileIncomplete` uses at line 117 with `setProperty("missingFields", ...)`):
```kotlin
@ExceptionHandler(EmailNotVerifiedException::class)
fun handleEmailNotVerified(ex: EmailNotVerifiedException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Email address not verified")
    problem.title = "Forbidden"
    problem.setProperty("code", "EMAIL_NOT_VERIFIED")
    return problem
}
```

---

### `auth/service/AuthService.kt` — MODIFY (`register` no-token 201 + `login` hard-gate + verifyEmail)

**File:** `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt`

**`register` change (D-01)** — current (lines 28-46) auto-logs-in by minting
access+refresh tokens. Change to: create the unverified user, trigger the
verification email, and return **no tokens**. Current code to replace:
```kotlin
fun register(request: RegisterRequest): AuthResponse {
    if (userRepository.existsByEmail(request.email)) {
        throw DuplicateEmailException()
    }
    val user = User(
        email = request.email,
        passwordHash = passwordEncoder.encode(request.password)!!
    )
    val savedUser = userRepository.save(user)
    val accessToken = jwtService.generateAccessToken(savedUser.id!!, savedUser.email)
    val refreshToken = createRefreshToken(savedUser)
    return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
}
```
> After save, call the verification-issue path (inject `EmailVerificationService`
> and call its issue/resend method, OR issue the first token inline). Return type
> changes away from `AuthResponse` (controller returns `GenericMessageResponse`).

**`login` hard-gate (D-03)** — current (lines 48-63). Add the verified check
**after** `passwordEncoder.matches` (so an attacker without the password learns
nothing) and **before** minting tokens:
```kotlin
fun login(request: LoginRequest): AuthResponse {
    val user = userRepository.findByEmail(request.email)
        ?: throw InvalidCredentialsException()

    if (!passwordEncoder.matches(request.password, user.passwordHash)) {
        throw InvalidCredentialsException()
    }
    // >>> INSERT GATE HERE (D-03):
    //     if (user.emailVerifiedAt == null) throw EmailNotVerifiedException()

    val accessToken = jwtService.generateAccessToken(user.id!!, user.email)
    val refreshToken = createRefreshToken(user)
    return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
}
```

**`verifyEmail` — mirror `resetPassword` (lines 93-116).** This is the exact
template for the token-claim/verify logic (atomic single-use via `markUsed`,
expiry check, set state):
```kotlin
@Transactional
fun resetPassword(rawToken: String, newPassword: String) {
    val tokenHash = hashToken(rawToken)
    val resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
        ?: throw InvalidTokenException()

    if (resetToken.expiresAt.isBefore(Instant.now())) {
        throw InvalidTokenException()
    }
    // Atomically claim the token (single-use, race-free).
    if (passwordResetTokenRepository.markUsed(resetToken.id!!, Instant.now()) == 0) {
        throw InvalidTokenException()
    }
    val user = resetToken.user
    user.passwordHash = passwordEncoder.encode(newPassword)!!
    user.updatedAt = Instant.now()
    userRepository.save(user)
    revokeAllUserTokens(user)
}
```
> For `verifyEmail`: same structure, but instead of setting `passwordHash`, set
> `user.emailVerifiedAt = Instant.now()` (+ `user.updatedAt`). Per D-02 the verify
> endpoint returns **200 with no tokens** and does NOT call `revokeAllUserTokens`
> (register minted no session to revoke; user logs in fresh afterward).
> Reuse the existing private `hashToken` helper (lines 118-122).

---

### `auth/controller/AuthController.kt` — MODIFY (add `/verify-email` + `/resend-verification`)

**File:** `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt`

**Analogs:** `forgotPassword` (lines 47-54, generic 202) and `resetPassword`
(lines 56-61, 200 no body). Every public endpoint carries `@SecurityRequirements`.
```kotlin
@SecurityRequirements
@PostMapping("/forgot-password")
fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<GenericMessageResponse> {
    passwordResetService.requestReset(request.email)
    return ResponseEntity.accepted().body(
        GenericMessageResponse("If an account exists for that email, a password reset link has been sent.")
    )
}

@SecurityRequirements
@PostMapping("/reset-password")
fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Void> {
    authService.resetPassword(request.token, request.newPassword)
    return ResponseEntity.ok().build()
}
```
New endpoints to add (mirror exactly):
- `POST /verify-email` → `authService.verifyEmail(request.token)`, returns **200** (`ResponseEntity.ok().build()`), like `reset-password` (D-02).
- `POST /resend-verification` → `emailVerificationService.resend(request.email)`, always returns **202** generic body (`ResponseEntity.accepted().body(GenericMessageResponse(...))`), like `forgot-password` (D-04).

Also **modify `register`** (lines 26-31) to return `GenericMessageResponse` at 201
instead of `AuthResponse` (D-01):
```kotlin
@SecurityRequirements
@PostMapping("/register")
fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
    val response = authService.register(request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
}
```
Inject `emailVerificationService` into the constructor alongside `authService` /
`passwordResetService` (lines 21-24).

---

## Shared Patterns

### THREE-PLACE public-endpoint whitelist (CRITICAL — applies to both new endpoints)

A new public `/api/auth/*` endpoint must be added in **three** places. Note the
Phase 10 asymmetry: `reset-password` is in SecurityConfig + JwtAuthenticationFilter
but **NOT** in `RateLimitFilter.AUTH_PATHS` (it's protected by its token, not per-IP).
For Phase 11, `verify-email` follows `reset-password` (skip AUTH_PATHS is fine, but
including it is harmless), while **`resend-verification` MUST be added to AUTH_PATHS**
(per-IP layer, D-05).

**1. `common/config/SecurityConfig.kt` line 28** — add to `permitAll()`:
```kotlin
it.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
```
→ append `"/api/auth/verify-email", "/api/auth/resend-verification"`.

**2. `common/security/JwtAuthenticationFilter.kt` lines 16-24** — add to `shouldNotFilter`:
```kotlin
override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = request.servletPath
    return path.startsWith("/api/auth/register") ||
            path.startsWith("/api/auth/login") ||
            path.startsWith("/api/auth/refresh") ||
            path.startsWith("/api/auth/forgot-password") ||
            path.startsWith("/api/auth/reset-password") ||
            path.startsWith("/v3/api-docs")
}
```
→ add `path.startsWith("/api/auth/verify-email")` and `path.startsWith("/api/auth/resend-verification")`.

**3. `common/security/RateLimitFilter.kt` lines 24-29** — add to `AUTH_PATHS`
(**at minimum `resend-verification`**, D-05):
```kotlin
private val AUTH_PATHS = setOf(
    "/api/auth/register",
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/forgot-password"
)
```
→ add `"/api/auth/resend-verification"` (and optionally `"/api/auth/verify-email"`).

### Two-layer rate limiting (D-05)
- **Per-IP layer:** `RateLimitFilter` (above) — Bucket4j keyed on client IP, returns
  429 with `Retry-After`. Just add the path to `AUTH_PATHS`; no code change needed.
- **Per-email layer:** the `ConcurrentHashMap<String, Bucket>` guard inside
  `EmailVerificationService` (copied from `PasswordResetService` lines 34-56) —
  on exhaustion, **silently skip send + return 202** (no 429, no existence signal).

### Config via `@Value` (AGENTS.md — no `@ConfigurationProperties`)
Add to `resources/application.yml` under the existing `app:` block (line 38-39):
```yaml
app:
  reset-password-url: ${RESET_PASSWORD_URL:catspell://reset-password}
```
→ add `verify-email-url: ${VERIFY_EMAIL_URL:catspell://verify-email}`. TTL/bucket
knobs (`app.verify-token.ttl-hours`, `app.resend-verification.*`) are injected via
`@Value` defaults directly in the service constructor — no yaml entry required
(matches how `app.reset-token.ttl-minutes` / `app.forgot-password.*` have no yaml keys).

---

## Migrations (Flyway — next after V15)

### `V16__create_email_verification_tokens_table.sql` — CREATE
**Analog:** `V15__create_password_reset_tokens_table.sql` (lines 1-11). Copy verbatim,
rename table + indexes. FK `ON DELETE CASCADE`, unique `token_hash` (D-07, per CONTEXT integration points):
```sql
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

### `V17__add_email_verified_at_to_users.sql` — CREATE (add column + grandfather backfill)
**Analog:** `users` table style from `V1__create_users_table.sql` (TIMESTAMPTZ columns).
Per D-06 add a **nullable `email_verified_at TIMESTAMPTZ`** (NULL = unverified), and
per D-09/VERIFY-05 backfill all existing rows so nobody is locked out. Backfill value
(`NOW()` vs `created_at`) is Claude's discretion — e.g.:
```sql
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;
```

### `auth/model/User.kt` — MODIFY (add entity field for the new column)
**Analog:** existing timestamp columns (lines 20-24). Add matching the `used_at`/
`created_at` nullable-Instant style:
```kotlin
@Column(name = "created_at", nullable = false, updatable = false)
var createdAt: Instant = Instant.now(),
@Column(name = "updated_at", nullable = false)
var updatedAt: Instant = Instant.now()
```
→ add `@Column(name = "email_verified_at") var emailVerifiedAt: Instant? = null`.
> `spring.jpa.hibernate.ddl-auto: validate` (application.yml line 9) means the entity
> field MUST match the V17 column exactly or the app fails to boot.

---

## No Analog Found

None. Every file in this phase has a direct Phase 10 analog — this is a mirror phase.

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/catspell/api/{auth,email,common}`, `src/main/resources/db/migration`, `src/main/resources/application.yml`
**Files scanned:** 18
**Highest Flyway version present:** V15 → next V16, V17
**Pattern extraction date:** 2026-08-11
