# Phase 12: Account Credentials - Pattern Map

**Mapped:** 2026-08-17
**Files analyzed:** 12 (new + modified)
**Analogs found:** 12 / 12

All new files have strong in-repo analogs from Phase 10 (password-recovery) and
Phase 11 (email-verification). This phase is almost entirely a "copy the
adjacent token-flow machinery and re-target it" exercise. No file lacks an
analog.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `auth/model/EmailChangeRequest.kt` (new) | model (entity) | CRUD | `auth/model/EmailVerificationToken.kt` | exact |
| `auth/model/EmailChangeRequestRepository.kt` (new) | model (repo) | CRUD | `auth/model/EmailVerificationTokenRepository.kt` | exact |
| `resources/db/migration/V18__create_email_change_requests_table.sql` (new) | migration | CRUD/DDL | `V16__create_email_verification_tokens_table.sql` | exact |
| `email/service/EmailChangeEmailRenderer.kt` (new) | service (renderer) | transform | `email/service/EmailVerificationEmailRenderer.kt` | exact |
| `auth/service/EmailChangeService.kt` (new) | service | request-response + email | `auth/service/EmailVerificationService.kt` + `PasswordResetService.kt` | exact |
| `AuthService.changePassword(...)` (modify `auth/service/AuthService.kt`) | service | request-response | `AuthService.resetPassword` (same file, 99-122) | exact |
| `AuthService.confirmEmailChange(...)` (modify `auth/service/AuthService.kt`) | service | request-response | `AuthService.verifyEmail` (same file, 124-146) | exact |
| `auth/model/AuthDtos.kt` (modify — add 3 DTOs) | model (DTO) | request-response | existing DTOs in same file (28-47) | exact |
| `auth/controller/AuthController.kt` (modify — add 3 endpoints) | controller | request-response | same file: authenticated `/me` (85-91) + public `/reset-password` (62-67); `extractUserId()` from `profile/controller/ProfileController.kt` (57-60) | exact |
| `common/exception/Exceptions.kt` (modify — add `InvalidCurrentPasswordException`) | model (exception) | — | `EmailNotVerifiedException` (line 9) | exact |
| `common/exception/GlobalExceptionHandler.kt` (modify — add handler) | config (advice) | request-response | `handleEmailNotVerified` (67-73) | exact |
| Security whitelist edits: `common/config/SecurityConfig.kt` (28), `common/security/JwtAuthenticationFilter.kt` (16-26), `common/security/RateLimitFilter.kt` (24-30) | config/middleware | request-response | existing `verify-email` / `reset-password` entries | exact |
| `auth/AccountCredentialsIntegrationTest.kt` (new test) | test | request-response | `auth/EmailVerificationIntegrationTest.kt` + `profile/ProfileIntegrationTest.kt` (Bearer auth) | exact |

---

## Pattern Assignments

### `auth/model/EmailChangeRequest.kt` (entity, CRUD)

**Analog:** `src/main/kotlin/com/catspell/api/auth/model/EmailVerificationToken.kt`

Copy the entity verbatim, add a `new_email` column, and rename the table.

**Full pattern** (`EmailVerificationToken.kt` 1-37):
```kotlin
package com.catspell.api.auth.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "email_verification_tokens")   // -> "email_change_requests"
class EmailVerificationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    // ADD: @Column(name = "new_email", nullable = false) var newEmail: String,

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
        if (other !is EmailVerificationToken) return false
        return id != null && id == other.id
    }
    override fun hashCode(): Int = javaClass.hashCode()
}
```
Note the identity-based `equals`/`hashCode` (compares `id`, guards null) — keep it.

---

### `auth/model/EmailChangeRequestRepository.kt` (repo, CRUD)

**Analog:** `src/main/kotlin/com/catspell/api/auth/model/EmailVerificationTokenRepository.kt`

Copy verbatim, retype to `EmailChangeRequest`. Keep the three methods —
`findByTokenHash`, `findAllByUserAndUsedAtIsNull`, and the atomic `markUsed`.

**Full pattern** (`EmailVerificationTokenRepository.kt` 10-22):
```kotlin
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, UUID> {
    fun findByTokenHash(tokenHash: String): EmailVerificationToken?
    fun findAllByUserAndUsedAtIsNull(user: User): List<EmailVerificationToken>

    // Atomic single-use claim: conditional UPDATE guarded by usedAt IS NULL.
    // Returns rows updated (1 = claimed, 0 = already used). Closes the read-check-write race.
    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    fun markUsed(@Param("id") id: UUID, @Param("now") now: Instant): Int
}
```
`PasswordResetTokenRepository.kt` (10-22) is identical — same template.

---

### `resources/db/migration/V18__create_email_change_requests_table.sql` (migration, DDL)

**Analog:** `src/main/resources/db/migration/V16__create_email_verification_tokens_table.sql`

Copy verbatim, add a `new_email` column, rename table + indexes. **V18 is the
next sequential version.**

**Full pattern** (`V16` 1-11):
```sql
CREATE TABLE email_verification_tokens (   -- -> email_change_requests
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- ADD: new_email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens(user_id);
CREATE UNIQUE INDEX idx_email_verification_tokens_token_hash ON email_verification_tokens(token_hash);
```
Convention: `token_hash VARCHAR(255) NOT NULL UNIQUE`, FK `ON DELETE CASCADE`,
`idx_<table>_user_id`, unique `idx_<table>_token_hash`.

---

### `email/service/EmailChangeEmailRenderer.kt` (renderer, transform)

**Analog:** `src/main/kotlin/com/catspell/api/email/service/EmailVerificationEmailRenderer.kt`

Copy structure. **Critical difference (D-05):** `render(recipientEmail, rawToken)`
must be called with the request's `new_email` (the renderer itself is
agnostic — the caller passes the recipient). Use a new `app.confirm-email-change-url`
`@Value` deep-link key; adjust the copy.

**Full pattern** (`EmailVerificationEmailRenderer.kt` 1-43):
```kotlin
@Component
class EmailVerificationEmailRenderer(
    @Value("\${app.verify-email-url}") private val verifyEmailUrl: String   // -> app.confirm-email-change-url
) {
    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val verifyLink = "$verifyEmailUrl?token=$rawToken"
        val subject = "Verify your Cat Spell email"   // -> "Confirm your new Cat Spell email"
        val htmlBody = """<!DOCTYPE html><html><body> ... <a href="$verifyLink">...</a> ... </body></html>""".trimIndent()
        val textBody = """ ... $verifyLink ... """.trimIndent()
        return EmailMessage(to = recipientEmail, subject = subject, htmlBody = htmlBody, textBody = textBody)
    }
}
```
`PasswordResetEmailRenderer.kt` (1-43) uses `@Value("\${app.reset-password-url}")`
— identical shape, confirming the `app.*-url` deep-link convention.

`EmailMessage` shape (`email/service/EmailSender.kt` 3-8): `to`, `subject`,
`htmlBody`, `textBody`. `EmailSender.send(message): EmailResult` is the seam (18-20).

---

### `auth/service/EmailChangeService.kt` (service, request-response + email)

**Analog:** `src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt`
(issue path) + `PasswordResetService.kt` (per-email bucket + prior-token invalidation)

This is the change-email **step 1 (request)** service. Copy the constructor
shape (repos + `EmailSender` + renderer + `@Value` config), the per-email
Bucket4j guard, `generateRawToken`, and `hashToken`. Adapt `issueAndSend` to:
verify current password (delegate to `AuthService` or `passwordEncoder` — planner
decides), reject taken email with `DuplicateEmailException` (D-06), invalidate
prior unused requests, mint token into `email_change_requests`, and render to the
**new_email**.

**Constructor + config `@Value`** (`EmailVerificationService.kt` 22-31):
```kotlin
@Service
class EmailVerificationService(
    private val userRepository: UserRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val emailSender: EmailSender,
    private val emailVerificationEmailRenderer: EmailVerificationEmailRenderer,
    @Value("\${app.resend-verification.per-email-capacity:3}") private val perEmailCapacity: Long,
    @Value("\${app.resend-verification.per-email-refill-hours:1}") private val perEmailRefillHours: Long,
    @Value("\${app.verify-token.ttl-hours:24}") private val verifyTokenTtlHours: Long
)
```
New keys should follow the `app.change-email.per-email-*` / `app.confirm-email-change-token.ttl-hours:24`
naming (mirrors `app.forgot-password.*` / `app.reset-token.*`).

**Per-email Bucket4j guard** (`EmailVerificationService.kt` 33-43; identical in `PasswordResetService.kt` 32-42):
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
Per discretion note: this per-email guard should key on the **target new_email**
to prevent inbox-bombing a victim. Unlike forgot-password/resend, this flow is
authenticated and NOT enumeration-safe (D-06 returns a real 409), so on exhaustion
you may surface an error rather than silently returning.

**Issue path — mint / invalidate prior / render / send** (`EmailVerificationService.kt` 51-69):
```kotlin
fun issueAndSend(user: User) {
    val priorTokens = emailVerificationTokenRepository.findAllByUserAndUsedAtIsNull(user)
    if (priorTokens.isNotEmpty()) {
        val now = Instant.now()
        priorTokens.forEach { it.usedAt = now }
        emailVerificationTokenRepository.saveAll(priorTokens)
    }
    val rawToken = generateRawToken()
    val verificationToken = EmailVerificationToken(
        user = user,
        tokenHash = hashToken(rawToken),
        expiresAt = Instant.now().plus(verifyTokenTtlHours, ChronoUnit.HOURS)
    )
    emailVerificationTokenRepository.save(verificationToken)
    val message = emailVerificationEmailRenderer.render(user.email, rawToken)  // -> render(request.newEmail, rawToken)
    emailSender.send(message)
}
```

**Duplicate-email check** — from `AuthService.register` (26-34) — reuse for D-06:
```kotlin
if (userRepository.existsByEmail(request.email)) {
    throw DuplicateEmailException()
}
```

**Token generation + hashing** (`EmailVerificationService.kt` 96-106; same in `PasswordResetService.kt` 84-94):
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

---

### `AuthService.changePassword(...)` (modify `auth/service/AuthService.kt`)

**Analog:** `AuthService.resetPassword` (same file, lines 99-122) — near-exact template.

Difference (D-01): verify the **current password** with
`passwordEncoder.matches` instead of consuming a reset token; throw the new
`InvalidCurrentPasswordException` (403) on mismatch (D-02).

**Current-password verification idiom** (`AuthService.login` 51-53):
```kotlin
if (!passwordEncoder.matches(request.password, user.passwordHash)) {
    throw InvalidCredentialsException()   // -> InvalidCurrentPasswordException() for change flows
}
```

**Update hash + revoke all sessions** (`AuthService.resetPassword` 116-121):
```kotlin
val user = resetToken.user
user.passwordHash = passwordEncoder.encode(newPassword)!!
user.updatedAt = Instant.now()
userRepository.save(user)

revokeAllUserTokens(user)
```

**Revoke-all-sessions helper** (`AuthService` 165-169; currently `private` — planner
decides whether to expose or add a dedicated method, per D-03/discretion):
```kotlin
private fun revokeAllUserTokens(user: User) {
    val activeTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user)
    activeTokens.forEach { it.revoked = true }
    refreshTokenRepository.saveAll(activeTokens)
}
```
Annotate the new method `@Transactional` like `resetPassword`/`verifyEmail`.

---

### `AuthService.confirmEmailChange(...)` (modify `auth/service/AuthService.kt`)

**Analog:** `AuthService.verifyEmail` (same file, lines 124-146) — exact template
for atomic-claim + stamp.

Difference (D-08): after `markUsed`, look up the request's `new_email`, swap
`user.email`, stamp `emailVerifiedAt`, AND `revokeAllUserTokens` (identity change =
sensitive action, unlike `verifyEmail` which does NOT revoke).

**Atomic claim + expiry check + stamp** (`AuthService.verifyEmail` 124-146):
```kotlin
@Transactional
fun verifyEmail(rawToken: String) {
    val tokenHash = hashToken(rawToken)
    val verificationToken = emailVerificationTokenRepository.findByTokenHash(tokenHash)
        ?: throw InvalidTokenException()
    if (verificationToken.expiresAt.isBefore(Instant.now())) {
        throw InvalidTokenException()
    }
    // Atomic single-use claim; 0 rows updated => already consumed.
    if (emailVerificationTokenRepository.markUsed(verificationToken.id!!, Instant.now()) == 0) {
        throw InvalidTokenException()
    }
    val user = verificationToken.user
    user.emailVerifiedAt = Instant.now()
    user.updatedAt = Instant.now()
    userRepository.save(user)
    // ADD for confirm-email-change: user.email = request.newEmail; revokeAllUserTokens(user)
}
```
`hashToken` (`AuthService` 148-152) is the shared SHA-256 hex helper (same as the
services). Reuse it for the confirm lookup.

---

### `auth/model/AuthDtos.kt` (modify — add 3 DTOs)

**Analog:** existing DTOs in the same file (28-47).

**Validation idiom** (`AuthDtos.kt` 6-38):
```kotlin
data class RegisterRequest(
    @field:Email(message = "must be a valid email address")
    val email: String,
    @field:Size(min = 8, message = "must be at least 8 characters")
    val password: String
)

data class ResetPasswordRequest(
    val token: String,
    @field:Size(min = 8, message = "must be at least 8 characters")
    val newPassword: String
)

data class VerifyEmailRequest(
    val token: String
)
```
Add: `ChangePasswordRequest(currentPassword, @field:Size(min=8) newPassword)`,
`ChangeEmailRequest(currentPassword, @field:Email newEmail)`,
`ConfirmEmailChangeRequest(token)`. `GenericMessageResponse(message)` (49-51)
already exists for the 202-style ack.

---

### `auth/controller/AuthController.kt` (modify — add 3 endpoints)

**Analog (public token endpoint):** `resetPassword` / `verifyEmail` handlers (62-74, same file).
**Analog (authenticated):** `/me` (85-91, same file) + `extractUserId()` from `ProfileController.kt` (57-60).

**Public token-only handler** — for `confirm-email-change` (D-07). Note `@SecurityRequirements`
(swagger) is used on public routes (`AuthController.kt` 62-67):
```kotlin
@SecurityRequirements
@PostMapping("/reset-password")
fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Void> {
    authService.resetPassword(request.token, request.newPassword)
    return ResponseEntity.ok().build()
}
```

**Authenticated principal resolution** — the two change endpoints have NO
`@SecurityRequirements` and resolve the caller via `extractUserId()`
(`ProfileController.kt` 57-60):
```kotlin
private fun extractUserId(): UUID {
    val authentication = SecurityContextHolder.getContext().authentication!!
    return UUID.fromString(authentication.principal as String)
}
```
`AuthController.me()` (85-91) shows the raw `SecurityContextHolder` access if a
local helper is preferred. `AuthController` currently has no `extractUserId()` —
add one (copy from `ProfileController`).

**202-style acknowledgement** — for change-email request (`AuthController.forgotPassword` 53-60):
```kotlin
@PostMapping("/change-email")   // authenticated: NO @SecurityRequirements
fun changeEmail(...): ResponseEntity<GenericMessageResponse> {
    ...
    return ResponseEntity.accepted().body(
        GenericMessageResponse("Check your new email address to confirm the change.")
    )
}
```

---

### `common/exception/Exceptions.kt` (modify) + `GlobalExceptionHandler.kt` (modify)

**Analog:** `EmailNotVerifiedException` (Exceptions.kt line 9) + `handleEmailNotVerified`
(GlobalExceptionHandler.kt 67-73) — the exact distinct-403-with-machine-code pattern (D-02).

**Exception** (`Exceptions.kt` 9):
```kotlin
class EmailNotVerifiedException(message: String = "Email address not verified") : RuntimeException(message)
// ADD: class InvalidCurrentPasswordException(message: String = "Current password is incorrect") : RuntimeException(message)
```

**Handler with machine-readable `code`** (`GlobalExceptionHandler.kt` 67-73):
```kotlin
@ExceptionHandler(EmailNotVerifiedException::class)
fun handleEmailNotVerified(ex: EmailNotVerifiedException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Email address not verified")
    problem.title = "Forbidden"
    problem.setProperty("code", "EMAIL_NOT_VERIFIED")   // -> new handler: code = "INVALID_CURRENT_PASSWORD"
    return problem
}
```
**D-06 reuse:** `DuplicateEmailException` already maps to 409 (`handleDuplicateEmail`
46-51) — no new handler needed for the taken-email case.

---

### Security whitelist edits (3 places — only `confirm-email-change` is public, D-07)

Per the established rule, a public `/api/auth/*` endpoint must be added in
**three** locations. The two change endpoints stay authenticated and must NOT be
whitelisted.

**1. `common/config/SecurityConfig.kt` line 28** — add to `permitAll` matcher list:
```kotlin
it.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/verify-email", "/api/auth/resend-verification").permitAll()
// ADD: "/api/auth/confirm-email-change"
```

**2. `common/security/JwtAuthenticationFilter.kt` shouldNotFilter (16-26)** — add a `startsWith` clause:
```kotlin
override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = request.servletPath
    return path.startsWith("/api/auth/register") ||
            ... ||
            path.startsWith("/api/auth/resend-verification") ||
            // ADD: path.startsWith("/api/auth/confirm-email-change") ||
            path.startsWith("/v3/api-docs")
}
```

**3. `common/security/RateLimitFilter.kt` AUTH_PATHS (24-30)** — per-IP set. Add the
authenticated change-email request path here (per discretion) to get per-IP
coverage; `confirm-email-change` may also be added:
```kotlin
private val AUTH_PATHS = setOf(
    "/api/auth/register",
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/forgot-password",
    "/api/auth/resend-verification"
    // ADD: "/api/auth/change-email" (and/or "/api/auth/confirm-email-change")
)
```
(The filter is registered for `/api/auth/*` in `RateLimitFilterConfig` 80-91, but
only paths in `AUTH_PATHS` are actually throttled — see the `startsWith` guard 36-40.)

---

### `auth/AccountCredentialsIntegrationTest.kt` (new test)

**Analog:** `auth/EmailVerificationIntegrationTest.kt` (email-capture + token-flow)
+ `profile/ProfileIntegrationTest.kt` (Bearer-auth helper for the authenticated endpoints).

**Mock EmailSender + capture setup** (`EmailVerificationIntegrationTest.kt` 30-65):
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmailVerificationIntegrationTest.MockEmailConfig::class)
class EmailVerificationIntegrationTest : BaseIntegrationTest() {

    @TestConfiguration
    class MockEmailConfig {
        @Bean @Primary
        fun emailSender(): EmailSender = mockk(relaxed = true)
    }

    private val sentMessages = mutableListOf<EmailMessage>()

    @BeforeEach
    fun setupEmailCapture() {
        clearMocks(emailSender)
        sentMessages.clear()
        every { emailSender.send(capture(sentMessages)) } returns EmailResult(EmailSendStatus.SUCCESS, messageId = "test")
    }
}
```

**Token extraction from captured email** (`EmailVerificationIntegrationTest.kt` 91-97):
```kotlin
private fun capturedVerifyToken(): String {
    val message = sentMessages.last()
    val match = Regex("token=([A-Za-z0-9_-]+)").find(message.textBody)
        ?: error("No verification token found in captured email body")
    return match.groupValues[1]
}
```

**Single-use / expiry / prior-token-invalidation assertions** — mirror
`EmailVerificationIntegrationTest.kt` 153-171 (reused token -> 401, force-expired
via repo -> 401) and 206-219 (fresh token invalidates prior).

**Bearer-auth for the authenticated change endpoints** (`ProfileIntegrationTest.kt` 35-39, 63-69):
```kotlin
val result = /* register + verify + login */ .andReturn()
val token = objectMapper.readTree(result.response.contentAsString)["accessToken"].asText()

mockMvc.perform(
    post("/api/auth/change-password")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(mapOf("currentPassword" to "...", "newPassword" to "...")))
).andExpect(status().isOk)
```
Cover ACCT-01..05: wrong current password -> 403 `INVALID_CURRENT_PASSWORD`;
change-password revokes all sessions; change-email to taken address -> 409;
confirm swaps email + stamps `email_verified_at` + revokes sessions; token
single-use/expiry.

---

## Shared Patterns

### Atomic single-use token claim
**Source:** `EmailVerificationTokenRepository.markUsed` (20-22) / `PasswordResetTokenRepository.markUsed` (20-22), consumed in `AuthService.verifyEmail`/`resetPassword` (112-114, 136-138)
**Apply to:** `EmailChangeRequestRepository` + `AuthService.confirmEmailChange`
```kotlin
@Modifying
@Query("UPDATE EmailChangeRequest t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
fun markUsed(@Param("id") id: UUID, @Param("now") now: Instant): Int
// caller: if (repo.markUsed(id, Instant.now()) == 0) throw InvalidTokenException()
```

### SHA-256 hex token hashing
**Source:** `AuthService.hashToken` (148-152), duplicated in both services (verification 102-106, reset 90-94)
**Apply to:** `EmailChangeService` (issue) + `AuthService.confirmEmailChange` (lookup)
```kotlin
private fun hashToken(rawToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return HexFormat.of().formatHex(digest.digest(rawToken.toByteArray(Charsets.UTF_8)))
}
```

### Revoke-all-sessions ("fresh login" on sensitive action)
**Source:** `AuthService.revokeAllUserTokens` (165-169), invoked by `resetPassword` (121)
**Apply to:** `changePassword` (D-03) and `confirmEmailChange` (D-08). Currently
`private` — planner decides how to factor/expose.

### Distinct 403 with machine-readable `code`
**Source:** `GlobalExceptionHandler.handleEmailNotVerified` (67-73) + `EmailNotVerifiedException` (Exceptions.kt 9)
**Apply to:** new `InvalidCurrentPasswordException` -> 403 `code = "INVALID_CURRENT_PASSWORD"`

### RFC 7807 ProblemDetail handlers
**Source:** every handler in `GlobalExceptionHandler.kt` uses `ProblemDetail.forStatusAndDetail(...)` + `problem.title = ...`
**Apply to:** the new exception handler.

### `@Value` env config with defaults (no `@ConfigurationProperties`)
**Source:** services' constructor `@Value` params (verification 28-30, reset 27-29); renderers' `app.*-url` keys (verification 8, reset 8)
**Apply to:** `EmailChangeService` TTL + per-email bucket keys, and `EmailChangeEmailRenderer` `app.confirm-email-change-url`.

### Per-email Bucket4j guard
**Source:** `EmailVerificationService` 33-43 / `PasswordResetService` 32-42
**Apply to:** `EmailChangeService`, keyed on the target `new_email` (anti-inbox-bombing).

### Three-place public-endpoint whitelist
**Source:** `SecurityConfig` (28), `JwtAuthenticationFilter.shouldNotFilter` (16-26), `RateLimitFilter.AUTH_PATHS` (24-30)
**Apply to:** `confirm-email-change` only (public). The two change endpoints stay authenticated.

---

## No Analog Found

None. Every new/modified file maps to an exact in-repo analog from Phase 10/11.

---

## Metadata

**Analog search scope:**
`src/main/kotlin/com/catspell/api/{auth,common,email,profile}`, `src/main/resources/db/migration`, `src/test/kotlin/com/catspell/api/{auth,profile}`
**Files scanned:** 18
**Pattern extraction date:** 2026-08-17
