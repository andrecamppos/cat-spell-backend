# Phase 10: Password Recovery - Pattern Map

**Mapped:** 2026-08-07
**Files analyzed:** 17 (9 new source + 4 modified source + 1 new migration + 3 new/extended tests)
**Analogs found:** 17 / 17 (every file has a strong in-repo analog)

> Kotlin/Spring Boot backend. Every building block already exists as a proven pattern in this repo — this map points each new/modified file at its closest analog and extracts the exact code to copy. Package convention: `com.catspell.api.<domain>.{controller,model,service}`; DB migrations in `src/main/resources/db/migration`.

---

## File Classification

| New/Modified File | New/Mod | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|---------|------|-----------|----------------|---------------|
| `email/service/EmailSender.kt` | NEW | provider (interface + value objects) | request-response | `push/service/PushProvider.kt` | exact |
| `email/service/LoggingEmailSender.kt` | NEW | provider (no-op impl) | request-response | `push/service/LoggingPushProvider.kt` | exact |
| `email/service/PasswordResetEmailRenderer.kt` | NEW | service (template renderer) | transform | *(no exact analog — see No Analog Found)* | role-match (weak) |
| `auth/model/PasswordResetToken.kt` | NEW | model (JPA entity) | CRUD | `auth/model/RefreshToken.kt` | exact |
| `auth/model/PasswordResetTokenRepository.kt` | NEW | repository | CRUD | `auth/model/RefreshTokenRepository.kt` | exact |
| `db/migration/V15__create_password_reset_tokens_table.sql` | NEW | migration | file-I/O (DDL) | `db/migration/V2__create_refresh_tokens_table.sql` | exact |
| `auth/service/PasswordResetService.kt` | NEW | service (forgot flow + per-email guard) | request-response / event-driven | `auth/service/AuthService.kt` + `common/security/RateLimitFilter.kt` (`createBucket`) | role-match (composite) |
| `auth/service/AuthService.kt` | MOD | service (add `resetPassword`) | CRUD (transactional) | `auth/service/AuthService.kt` `refreshToken` (self, in-file) | exact |
| `auth/controller/AuthController.kt` | MOD | controller | request-response | `auth/controller/AuthController.kt` (self, existing routes) | exact |
| `auth/model/AuthDtos.kt` | MOD | model (DTOs) | request-response | `auth/model/AuthDtos.kt` (`RegisterRequest`) | exact |
| `common/security/RateLimitFilter.kt` | MOD | middleware (servlet filter) | request-response | `RateLimitFilter.AUTH_PATHS` (self, in-file) | exact |
| `common/config/SecurityConfig.kt` | MOD | config | request-response | `SecurityConfig` `permitAll` list (self, in-file) | exact |
| `common/security/JwtAuthenticationFilter.kt` | MOD | middleware (servlet filter) | request-response | `JwtAuthenticationFilter.shouldNotFilter` (self, in-file) | exact |
| `test .../email/EmailSenderSelectionTest.kt` | NEW | test (context selection) | request-response | `push/PushProviderSelectionTest.kt` | exact |
| `test .../email/EmailSenderContractTest.kt` | NEW | test (contract) | request-response | `push/PushProviderSelectionTest.kt` + `push/TokenPruningTest.kt` (MockK) | role-match |
| `test .../auth/PasswordResetIntegrationTest.kt` | NEW | test (integration, MockMvc) | request-response | `auth/RefreshTokenIntegrationTest.kt` | exact |
| `test .../common/RateLimitIntegrationTest.kt` | MOD | test (extend) | request-response | `common/RateLimitIntegrationTest.kt` (self, `postRefresh` case) | exact |

> ⚠️ **RESEARCH divergence flagged for planner (RECOV-05 hashing):** CONTEXT.md suggests reusing `PasswordEncoder` (BCrypt) to hash the reset token. RESEARCH.md rejects this — BCrypt's random salt is non-deterministic so you can't look up by hash. Use **SHA-256** (`MessageDigest`) for the stored `token_hash`, and keep BCrypt (`PasswordEncoder`) only for the *new password*. This falls under "Claude's Discretion: the exact hashing helper" in CONTEXT.md.

> ⚠️ **RESEARCH correction flagged (RECOV-07 rate limiting):** `/forgot-password` is **NOT** limited "for free." `RateLimitFilter` only limits the hard-coded `AUTH_PATHS` set (`register`/`login`/`refresh`). The two new public endpoints must be added to **three** places: `RateLimitFilter.AUTH_PATHS`, `SecurityConfig.permitAll`, and `JwtAuthenticationFilter.shouldNotFilter`.

---

## Pattern Assignments

### `email/service/EmailSender.kt` (provider interface + value objects, request-response) — NEW

**Analog:** `src/main/kotlin/com/catspell/api/push/service/PushProvider.kt` (exact — this is the template shape)

**Full analog** (`PushProvider.kt:1-20`):
```kotlin
package com.catspell.api.push.service

data class PushPayload(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val collapseKey: String? = null
)

enum class PushSendStatus { SUCCESS, UNREGISTERED, ERROR }

data class PushResult(
    val status: PushSendStatus,
    val messageId: String? = null,
    val errorDetail: String? = null
)

interface PushProvider {
    fun send(token: String, payload: PushPayload): PushResult
}
```

**Copy pattern (per RESEARCH Pattern 1):** one file holding `EmailMessage(to, subject, htmlBody, textBody)` data class + `EmailSendStatus { SUCCESS, ERROR }` enum + `EmailResult(status, messageId?, errorDetail?)` data class + `interface EmailSender { fun send(message: EmailMessage): EmailResult }`. Note `UNREGISTERED` is push-specific — drop it for email.

---

### `email/service/LoggingEmailSender.kt` (no-op provider, request-response) — NEW

**Analog:** `src/main/kotlin/com/catspell/api/push/service/LoggingPushProvider.kt` (exact)

**Full analog** (`LoggingPushProvider.kt:1-20`):
```kotlin
package com.catspell.api.push.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["push.enabled"], havingValue = "false", matchIfMissing = true)
class LoggingPushProvider : PushProvider {

    private val log = LoggerFactory.getLogger(LoggingPushProvider::class.java)

    override fun send(token: String, payload: PushPayload): PushResult {
        log.info("[no-op push] token={} title='{}'", maskToken(token), payload.title)
        return PushResult(PushSendStatus.SUCCESS, messageId = "logged")
    }

    private fun maskToken(token: String): String =
        if (token.length <= 8) "***" else "${token.take(6)}...${token.takeLast(4)}"
}
```

**Copy notes:**
- Gate on `@ConditionalOnProperty(name = ["email.enabled"], havingValue = "false", matchIfMissing = true)` — no-op is the default so tests/local dev never hit a network (EMAIL-02).
- **Mask the recipient in logs** — mirror `maskToken`; write a `maskEmail` helper. **Never log the raw reset token** (Anti-Patterns / V7 Logging).
- Return `EmailResult(EmailSendStatus.SUCCESS, messageId = "logged")`.
- A future concrete provider (SendGrid/etc.) mirrors `FcmPushProvider` with `@ConditionalOnProperty(havingValue = "true")` — **NOT built this phase (D-03, deferred)**. See `FcmPushProvider.kt:14-15` for the enabled-gate + constructor-injection shape.

---

### `email/service/PasswordResetEmailRenderer.kt` (template renderer, transform) — NEW

**Analog:** none exact (see No Analog Found). Closest structural sibling is any `@Service`/`@Component` in this repo; `AuthService.kt:16-23` shows the `@Value` env-injection idiom to reuse for the base link URL.

**Copy notes (RESEARCH D-01/D-02, "plain Kotlin string-template rendering"):**
- `@Component` class with `@Value("\${app.reset-password-url}") private val resetPasswordUrl: String` injected (mirror `AuthService`'s `@Value("\${jwt.refresh-token-expiry-days:30}")` at `AuthService.kt:22`).
- Build the deep link `"$resetPasswordUrl?token=$rawToken"` and render HTML + plain-text bodies via Kotlin string templates; return an `EmailMessage`.
- Do **not** add Thymeleaf (RESEARCH Alternatives Considered — rejected; no new dependency).

---

### `auth/model/PasswordResetToken.kt` (JPA entity, CRUD) — NEW

**Analog:** `src/main/kotlin/com/catspell/api/auth/model/RefreshToken.kt` (exact)

**Full analog** (`RefreshToken.kt:1-40`):
```kotlin
package com.catspell.api.auth.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false, unique = true)
    var token: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column(name = "replaced_by")
    var replacedBy: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RefreshToken) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
```

**Copy notes (RESEARCH Pattern 2):**
- `@Table(name = "password_reset_tokens")`.
- Columns: `id` (UUID PK), `user` (`@ManyToOne(LAZY)` + `@JoinColumn("user_id")`), `tokenHash` (`@Column(name = "token_hash", nullable = false, unique = true)`), `expiresAt`, `usedAt` (`@Column(name = "used_at")` nullable — single-use marker, replaces `revoked`/`replacedBy`), `createdAt`.
- **Keep the `equals`/`hashCode` by-id override verbatim** (`RefreshToken.kt:33-39`).

---

### `auth/model/PasswordResetTokenRepository.kt` (repository, CRUD) — NEW

**Analog:** `src/main/kotlin/com/catspell/api/auth/model/RefreshTokenRepository.kt` (exact)

**Full analog** (`RefreshTokenRepository.kt:1-9`):
```kotlin
package com.catspell.api.auth.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByToken(token: String): RefreshToken?
    fun findAllByUserAndRevokedFalse(user: User): List<RefreshToken>
}
```

**Copy notes:** `interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID>` with derived queries `fun findByTokenHash(tokenHash: String): PasswordResetToken?` (redemption lookup) and optionally `fun findAllByUserAndUsedAtIsNull(user: User): List<PasswordResetToken>` (to invalidate prior unused tokens on re-request). Derived-query naming mirrors `findByToken` / `findAllByUserAndRevokedFalse`.

---

### `db/migration/V15__create_password_reset_tokens_table.sql` (migration, DDL) — NEW

**Analog:** `src/main/resources/db/migration/V2__create_refresh_tokens_table.sql` (exact). **Next number is V15** — V14 is the latest existing migration (`V14__create_device_tokens_table.sql`).

**Full analog** (`V2__create_refresh_tokens_table.sql:1-12`):
```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE UNIQUE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
```

**Copy notes:** `password_reset_tokens` table with `token_hash VARCHAR(255) NOT NULL UNIQUE`, `expires_at TIMESTAMP WITH TIME ZONE NOT NULL`, `used_at TIMESTAMP WITH TIME ZONE` (nullable), `user_id ... ON DELETE CASCADE`. Add `idx_..._user_id` and a `UNIQUE INDEX idx_..._token_hash`. Copy the `gen_random_uuid()` PK default and `NOW()` timestamp default verbatim.

---

### `auth/service/PasswordResetService.kt` (service: forgot flow + per-email guard, request-response/event-driven) — NEW

**Analog A — service skeleton & config injection:** `AuthService.kt:16-23` (constructor DI + `@Value`).
```kotlin
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    @Value("\${jwt.refresh-token-expiry-days:30}") private val refreshTokenExpiryDays: Long
) {
```

**Analog B — enumeration-safe lookup:** `AuthService.login` (`AuthService.kt:45-47`) uses `userRepository.findByEmail(...)`; and `UserRepository.kt:6-8` provides `findByEmail` / `existsByEmail`. For forgot-flow, look up but **always** return generic 202 whether or not the user exists (D-05/RECOV-04).

**Analog C — per-email Bucket4j guard (RESEARCH Pattern 4):** mirror `RateLimitFilter.createBucket` (`RateLimitFilter.kt:69-75`) but key a `ConcurrentHashMap<String, Bucket>` by normalized email instead of IP:
```kotlin
// RateLimitFilter.kt:22, 41, 69-75 (the createBucket + ConcurrentHashMap idiom to mirror)
private val buckets = ConcurrentHashMap<String, Bucket>()
// ...
val bucket = buckets.computeIfAbsent(clientIp) { createBucket() }
// ...
private fun createBucket(): Bucket {
    val bandwidth = Bandwidth.builder()
        .capacity(capacity)
        .refillIntervally(capacity, Duration.ofMinutes(1))
        .build()
    return Bucket.builder().addLimit(bandwidth).build()
}
```
**Copy notes:**
- `private val emailBuckets = ConcurrentHashMap<String, Bucket>()`; `computeIfAbsent(normalizedEmail) { ... }`.
- Capacity/refill via `@Value` (RESEARCH suggests e.g. `capacity 3` / `refillIntervally(3, Duration.ofHours(1))`) — tune to sane anti-abuse defaults (Claude's Discretion).
- **On per-email exhaustion: silently skip the send and still return 202** — never surface a 429 (enumeration leak; Anti-Patterns).
- Token issuance (RESEARCH Pattern 3): `SecureRandom().nextBytes(ByteArray(32))` → `Base64.getUrlEncoder().withoutPadding()` for the raw token; `MessageDigest.getInstance("SHA-256")` + `HexFormat.of().formatHex(...)` for the stored `token_hash`. Persist `PasswordResetToken{ user, tokenHash, expiresAt = now + 30m }` (D-06). Email the **raw** token only.

---

### `auth/service/AuthService.kt` — MODIFY (add `resetPassword`, transactional)

**Analog (self, in-file):** `AuthService.refreshToken` transaction style (`AuthService.kt:62-88`) + `revokeAllUserTokens` (`AuthService.kt:101-105`).

**Transaction + validate-then-mutate pattern** (`AuthService.kt:62-88`):
```kotlin
@Transactional(noRollbackFor = [InvalidTokenException::class])
fun refreshToken(request: RefreshRequest): AuthResponse {
    val storedToken = refreshTokenRepository.findByToken(request.refreshToken)
        ?: throw InvalidTokenException()
    if (storedToken.revoked) {
        revokeAllUserTokens(storedToken.user)
        throw InvalidTokenException("Token reuse detected")
    }
    if (storedToken.expiresAt.isBefore(Instant.now())) {
        throw InvalidTokenException()
    }
    // ... mutate + save ...
}
```

**Reusable session-revocation helper** (`AuthService.kt:101-105`):
```kotlin
private fun revokeAllUserTokens(user: User) {
    val activeTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user)
    activeTokens.forEach { it.revoked = true }
    refreshTokenRepository.saveAll(activeTokens)
}
```

**Password hashing precedent** (`AuthService.kt:32`): `passwordHash = passwordEncoder.encode(request.password)!!`

**Copy notes (RESEARCH Pattern 5):**
- New `@Transactional fun resetPassword(rawToken: String, newPassword: String)`: SHA-256 the raw token → `findByTokenHash` → reject if absent / `usedAt != null` / expired with `throw InvalidTokenException()` → set `usedAt = Instant.now()` → `user.passwordHash = passwordEncoder.encode(newPassword)!!` → `user.updatedAt = Instant.now()` → `userRepository.save(user)` → `revokeAllUserTokens(user)`.
- **Decision for planner:** `revokeAllUserTokens` is currently `private` (`AuthService.kt:101`). Either put `resetPassword` on `AuthService` (so it can call the private helper directly) or make the helper `internal`/`fun` and inject `AuthService` into `PasswordResetService`. RESEARCH recommends putting `resetPassword` on `AuthService` to reuse the private helper.
- Reuse existing `InvalidTokenException` (`Exceptions.kt:7`) — already mapped to **401** by `GlobalExceptionHandler.kt:60-65`. No new exception class needed.

---

### `auth/controller/AuthController.kt` — MODIFY (add `forgotPassword` + `resetPassword`)

**Analog (self, in-file):** existing public routes (`AuthController.kt:21-40`).

**Existing pattern to copy** (`AuthController.kt:21-40`):
```kotlin
@SecurityRequirements
@PostMapping("/register")
fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
    val response = authService.register(request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
}

@SecurityRequirements
@PostMapping("/login")
fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
    val response = authService.login(request)
    return ResponseEntity.ok(response)
}
```

**Copy notes:**
- `@SecurityRequirements` on both new routes (marks them public in the OpenAPI doc, matching the other public auth routes).
- `@PostMapping("/forgot-password")` → call `passwordResetService.requestReset(request.email)` → `ResponseEntity.status(HttpStatus.ACCEPTED).body(...)` (202, generic body — D-05). Note `HttpStatus.ACCEPTED` vs the `HttpStatus.CREATED` used by register.
- `@PostMapping("/reset-password")` → `authService.resetPassword(request.token, request.newPassword)` → `ResponseEntity.ok().build()` (200, **no body/tokens** — D-07).
- Use `@Valid @RequestBody` on both, exactly like the existing routes.
- Inject `PasswordResetService` into the controller constructor alongside `authService` (`AuthController.kt:17-19`).

---

### `auth/model/AuthDtos.kt` — MODIFY (add request DTOs + generic response)

**Analog (self, in-file):** `RegisterRequest` validation (`AuthDtos.kt:6-12`).

**Existing validation pattern** (`AuthDtos.kt:1-12`):
```kotlin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email(message = "must be a valid email address")
    val email: String,

    @field:Size(min = 8, message = "must be at least 8 characters")
    val password: String
)
```

**Copy notes:**
- `ForgotPasswordRequest(@field:Email val email: String)` — reuse the exact `@field:Email` annotation.
- `ResetPasswordRequest(val token: String, @field:Size(min = 8) val newPassword: String)` — reuse the exact `@field:Size(min = 8, ...)` password rule (mirrors `RegisterRequest.password`).
- Optional `GenericMessageResponse(val message: String)` for the 202 body (or return a `Map`/`ProblemDetail`-free simple body). RESEARCH proposes `GenericMessageResponse`.

---

### `common/security/RateLimitFilter.kt` — MODIFY (add `/forgot-password` to `AUTH_PATHS`)

**Analog (self, in-file):** the `AUTH_PATHS` set (`RateLimitFilter.kt:24-28`):
```kotlin
private val AUTH_PATHS = setOf(
    "/api/auth/register",
    "/api/auth/login",
    "/api/auth/refresh"
)
```

**Copy notes:** add `"/api/auth/forgot-password"` to the set (RECOV-07 per-IP). The filter's `path.startsWith(it)` matching (`RateLimitFilter.kt:35`) then covers it automatically. Consider whether the shared per-IP capacity (default 10/min) is appropriate for forgot-password or whether a dedicated capacity is warranted (Claude's Discretion — the filter currently uses a single `capacity` for all paths). The 429 RFC-7807 body shape is already produced at `RateLimitFilter.kt:50-57` — no change needed there.

---

### `common/config/SecurityConfig.kt` — MODIFY (whitelist both new endpoints)

**Analog (self, in-file):** the `permitAll` list (`SecurityConfig.kt:27-34`):
```kotlin
.authorizeHttpRequests {
    it.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
    it.requestMatchers("/v3/api-docs/**").permitAll()
    it.requestMatchers("/actuator/health").permitAll()
    it.requestMatchers("/ws/**").permitAll()
    it.requestMatchers("/error").permitAll()
    it.anyRequest().authenticated()
}
```

**Copy notes:** add `"/api/auth/forgot-password"` and `"/api/auth/reset-password"` to the first `requestMatchers(...).permitAll()` call, or add a new `requestMatchers(...)` line. Without this, both endpoints will 401 (Anti-Patterns).

---

### `common/security/JwtAuthenticationFilter.kt` — MODIFY (bypass JWT for both endpoints)

**Analog (self, in-file):** `shouldNotFilter` (`JwtAuthenticationFilter.kt:16-22`):
```kotlin
override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = request.servletPath
    return path.startsWith("/api/auth/register") ||
            path.startsWith("/api/auth/login") ||
            path.startsWith("/api/auth/refresh") ||
            path.startsWith("/v3/api-docs")
}
```

**Copy notes:** add `|| path.startsWith("/api/auth/forgot-password") || path.startsWith("/api/auth/reset-password")`. This is the third of the three whitelist locations that must all be updated together.

---

### `test .../email/EmailSenderSelectionTest.kt` (EMAIL-02) — NEW

**Analog:** `src/test/kotlin/com/catspell/api/push/PushProviderSelectionTest.kt` (exact)

**Full analog** (`PushProviderSelectionTest.kt:1-36`):
```kotlin
class PushProviderSelectionTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(
            LoggingPushProvider::class.java,
            FcmPushProvider::class.java,
            FirebaseConfig::class.java
        )

    @Test
    fun `logging provider selected when push disabled or missing`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(LoggingPushProvider::class.java)
            assertThat(context).doesNotHaveBean(FcmPushProvider::class.java)
        }
    }
}
```

**Copy notes:** `ApplicationContextRunner().withUserConfiguration(LoggingEmailSender::class.java)`; assert `hasSingleBean(LoggingEmailSender::class.java)` when `email.enabled` unset/false (EMAIL-02, no network). Since no concrete provider exists this phase, drop the "context fails fast when enabled" case (that mirrors `FcmPushProvider`, which isn't built here).

---

### `test .../email/EmailSenderContractTest.kt` (EMAIL-01) — NEW

**Analog:** `PushProviderSelectionTest.kt` (context/assertion style) + `TokenPruningTest.kt:32-40` (MockK bean-override idiom).

**MockK provider-double pattern** (`TokenPruningTest.kt:32-40`):
```kotlin
@TestConfiguration
class MockPushConfig {
    @Bean
    @Primary
    fun pushProvider(): PushProvider = mockk()
}

@Autowired
lateinit var pushProvider: PushProvider
```

**Copy notes:** verify `LoggingEmailSender.send(EmailMessage)` returns `EmailResult(SUCCESS, ...)` and doesn't throw; assert `EmailMessage` shape (to/subject/htmlBody/textBody). Where the reset flow needs to assert the email content/link without a real send, mock `EmailSender` via a `@TestConfiguration @Bean @Primary fun emailSender(): EmailSender = mockk()` and `io.mockk.every { ... }` (RESEARCH Wave 0 fixture note).

---

### `test .../auth/PasswordResetIntegrationTest.kt` (RECOV-01..06) — NEW

**Analog:** `src/test/kotlin/com/catspell/api/auth/RefreshTokenIntegrationTest.kt` (exact)

**Harness pattern** (`RefreshTokenIntegrationTest.kt:17-39`):
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var refreshTokenRepository: RefreshTokenRepository

    private fun registerAndGetTokens(email: String, password: String = "password123"): Pair<String, String> {
        val body = mapOf("email" to email, "password" to password)
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return Pair(json["accessToken"].asText(), json["refreshToken"].asText())
    }
```

**"Force expiry via repo" precedent** (`RefreshTokenIntegrationTest.kt:113-127`) — reuse to test expired/used reset tokens:
```kotlin
val storedToken = refreshTokenRepository.findByToken(refreshToken)!!
storedToken.expiresAt = Instant.now().minusSeconds(3600)
refreshTokenRepository.save(storedToken)
// ... perform and expect status().isUnauthorized
```

**Copy notes:**
- Extend `BaseIntegrationTest` (`BaseIntegrationTest.kt:13` — Testcontainers Postgres, `@BeforeEach` truncation). `@SpringBootTest @AutoConfigureMockMvc`.
- Autowire `mockMvc`, `objectMapper`, `passwordResetTokenRepository`, `userRepository`, `refreshTokenRepository`.
- Cases: forgot returns 202 for both registered & unregistered email with **identical body** (RECOV-04); reset with valid token sets new hash & allows login (RECOV-03); used token → 401 and expired token → 401 (RECOV-05, force via repo like `RefreshTokenIntegrationTest.kt:113-127`); token stored hashed not raw (RECOV-05 — assert repo row `tokenHash != rawToken`); after reset, prior refresh tokens rejected (RECOV-06 — reuse the `status().isUnauthorized` assertion on a stored refresh token).
- To read the emailed raw token in tests, mock `EmailSender` (`@TestConfiguration @Bean @Primary = mockk()`, capture the `EmailMessage`) per the `TokenPruningTest` pattern.

---

### `test .../common/RateLimitIntegrationTest.kt` (RECOV-07 per-IP) — MODIFY (extend)

**Analog (self, in-file):** the existing per-endpoint 429 cases, e.g. `postRefresh` + `should rate limit refresh endpoint` (`RateLimitIntegrationTest.kt:44-47, 101-107`):
```kotlin
private fun postRefresh(ip: String) = post("/api/auth/refresh")
    .contentType(MediaType.APPLICATION_JSON)
    .content("""{"refreshToken":"fake-token"}""")
    .header("X-Forwarded-For", ip)

@Test
fun `should rate limit refresh endpoint`() {
    val ip = "10.0.8.1"
    repeat(10) { mockMvcWithFilter.perform(postRefresh(ip)) }
    mockMvcWithFilter.perform(postRefresh(ip))
        .andExpect(status().isTooManyRequests)
}
```

**Copy notes:** add a `postForgotPassword(ip)` helper and a `should rate limit forgot-password endpoint` test that fires N+1 requests from one `X-Forwarded-For` IP and expects `status().isTooManyRequests` — proving `/forgot-password` is now in `AUTH_PATHS`. A separate per-email guard test belongs in `PasswordResetIntegrationTest` (email-keyed, not IP-keyed) and must assert it **still returns 202** (not 429) when the per-email cap is hit.

---

## Shared Patterns

### Provider seam (interface + value objects + `@ConditionalOnProperty` no-op)
**Source:** `push/service/PushProvider.kt` + `push/service/LoggingPushProvider.kt` (+ `FcmPushProvider.kt:14-15` for the future enabled-gate)
**Apply to:** `EmailSender.kt`, `LoggingEmailSender.kt`
- Interface + `data class` payload + `enum` status + `data class` result in one file.
- No-op impl is `@Component @ConditionalOnProperty(havingValue = "false", matchIfMissing = true)` so it's the default; concrete impl (deferred) is `havingValue = "true"`.

### Durable token entity + Flyway migration + repository
**Source:** `RefreshToken.kt` + `RefreshTokenRepository.kt` + `V2__create_refresh_tokens_table.sql`
**Apply to:** `PasswordResetToken.kt`, `PasswordResetTokenRepository.kt`, `V15__...sql`
- UUID PK, `@ManyToOne(LAZY)` user FK + `@JoinColumn`, indexed unique token column, `Instant` timestamps, `equals`/`hashCode` by id.
- Migration: `gen_random_uuid()` PK default, `... REFERENCES users(id) ON DELETE CASCADE`, `NOW()` default, `CREATE UNIQUE INDEX` on the token column.

### `@Value` env-config injection
**Source:** `AuthService.kt:22` (`@Value("\${jwt.refresh-token-expiry-days:30}")`), `RateLimitFilter.kt:80` (`@Value("\${rate-limit.capacity:10}")`)
**Apply to:** `PasswordResetEmailRenderer` (`app.reset-password-url`), `PasswordResetService` (per-email bucket capacity/refill, TTL). Keys should be documented in `.env.example` per CONTEXT code_context.

### RFC 7807 error handling via `GlobalExceptionHandler`
**Source:** `common/exception/GlobalExceptionHandler.kt` (`InvalidTokenException` → 401 at lines 60-65) + `common/exception/Exceptions.kt:7`
**Apply to:** `AuthService.resetPassword` — reuse existing `InvalidTokenException` for absent/used/expired tokens; it already maps to a 401 `ProblemDetail`. No new handler or exception class needed. Bean Validation failures (`@Email`, `@Size`) are already mapped to 400 at `GlobalExceptionHandler.kt:22-34`.

### Bucket4j token-bucket idiom
**Source:** `RateLimitFilter.kt:22, 41, 69-75` (`ConcurrentHashMap<String, Bucket>` + `computeIfAbsent` + `Bandwidth.builder().capacity(..).refillIntervally(..).build()`)
**Apply to:** per-email guard in `PasswordResetService` (key by normalized email, not IP; on exhaustion skip send + return 202, never 429).

### Enumeration-safe logging & masking
**Source:** `LoggingPushProvider.maskToken` (`LoggingPushProvider.kt:18-19`)
**Apply to:** `LoggingEmailSender` (`maskEmail`), and everywhere touching the reset token — **never log the raw token**, mask emails (V7 Error/Logging; Anti-Patterns).

### Integration-test harness
**Source:** `BaseIntegrationTest.kt` (Testcontainers Postgres + `@BeforeEach` truncation) + `RefreshTokenIntegrationTest.kt` (`@SpringBootTest @AutoConfigureMockMvc`, MockMvc + Jackson helpers, force-expiry-via-repo)
**Apply to:** `PasswordResetIntegrationTest.kt`, extended `RateLimitIntegrationTest.kt`. MockK bean override (`@TestConfiguration @Bean @Primary = mockk()`) from `TokenPruningTest.kt:32-40` for stubbing `EmailSender`.

---

## No Analog Found

Files with no close in-repo match (planner should lean on RESEARCH.md Code Examples):

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `email/service/PasswordResetEmailRenderer.kt` | service (template renderer) | transform | No existing HTML/text email or template-rendering class in the repo. Follow RESEARCH D-02 "plain Kotlin string-template rendering" + reuse the `@Value` env-injection idiom from `AuthService.kt:22`. No Thymeleaf (rejected in RESEARCH). |

Partial-analog note: `PasswordResetService.kt` and `AuthService.resetPassword` have no single 1:1 file — they compose three established patterns (service DI/config from `AuthService`, transactional consume from `AuthService.refreshToken`, per-email bucket from `RateLimitFilter.createBucket`).

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/catspell/api/{auth,push,common}`, `src/main/resources/db/migration`, `src/test/kotlin/com/catspell/api/{auth,push,common}`
**Files scanned (read in full):** PushProvider, LoggingPushProvider, FcmPushProvider, AuthController, AuthService, RefreshToken, RefreshTokenRepository, User, UserRepository, AuthDtos, RateLimitFilter, SecurityConfig, JwtAuthenticationFilter, Exceptions, GlobalExceptionHandler, V2 migration, FcmSmokeTest, PushProviderSelectionTest, TokenPruningTest, RefreshTokenIntegrationTest, RateLimitIntegrationTest, BaseIntegrationTest (22 analogs)
**Pattern extraction date:** 2026-08-07
