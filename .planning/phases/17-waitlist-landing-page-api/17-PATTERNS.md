# Phase 17: Waitlist / Landing-Page API - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 22 (16 new, 6 modified, plus tests)
**Analogs found:** 21 / 22 (all analog paths verified with `git ls-files`)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/resources/db/migration/V24__create_waitlist_entries.sql` | migration | DDL | `src/main/resources/db/migration/V23__create_invites_and_referrals.sql` (+ V21 CHECK enum) | exact |
| `waitlist/model/WaitlistEntry.kt` | model (JPA entity) | CRUD | `invite/model/Invite.kt`; enum column style from `moderation/model/Report.kt:23-25` | exact |
| `waitlist/model/WaitlistStatus.kt` | model (enum) | — | Report category enum (`Report.kt`) | role-match |
| `waitlist/model/WaitlistEntryRepository.kt` | repository | CRUD + atomic claim | `invite/model/InviteRepository.kt` | exact |
| `waitlist/model/WaitlistDtos.kt` | DTO | request-response | `invite/model/IssueInviteRequest.kt` / `IssueInviteResponse.kt`; `auth` `ResendVerificationRequest` (AuthDtos.kt:55-57) | exact |
| `waitlist/service/WaitlistService.kt` | service | CRUD + token | `invite/service/InviteService.kt` + `auth/service/EmailVerificationService.kt` | exact |
| `waitlist/service/WaitlistEmailNormalizer.kt` | utility | transform | none (pure function) — use RESEARCH Pattern 2 | no analog |
| `waitlist/event/WaitlistEvents.kt` | event | event-driven | `moderation/event/ReportEvents.kt` | exact |
| `waitlist/event/WaitlistEmailListener.kt` | listener | event-driven (async AFTER_COMMIT) | `moderation/event/ReportNotificationListener.kt` | exact |
| `waitlist/controller/WaitlistController.kt` | controller | request-response (202 / 302) | `auth/controller/AuthController.kt:83-89` (resend-verification) | role-match |
| `waitlist/controller/WaitlistAdminController.kt` | controller | request-response (admin) | `invite/controller/InviteAdminController.kt` | exact |
| `common/security/AdminTokenGuard.kt` | middleware/guard | request-response | extracted from `InviteAdminController.kt:40-48` | exact |
| `email/service/WaitlistConfirmEmailRenderer.kt` | utility (renderer) | transform | `email/service/EmailVerificationEmailRenderer.kt` | exact |
| `email/service/WaitlistInviteEmailRenderer.kt` | utility (renderer) | transform | `email/service/EmailVerificationEmailRenderer.kt` | exact |
| `common/exception/Exceptions.kt` (modify) | config | — | `AdminAuthException` line 43 | exact |
| `common/exception/GlobalExceptionHandler.kt` (modify) | config | — | `handleAdminAuth` lines 85-91, `handleResourceNotFound` 112-117 | exact |
| `invite/controller/InviteAdminController.kt` (modify) | controller | — | itself — swap private guard for `AdminTokenGuard` | exact |
| `common/config/SecurityConfig.kt` (modify) | config | — | lines 28-29 permitAll | exact |
| `common/security/JwtAuthenticationFilter.kt` (modify) | middleware | — | `shouldNotFilter` lines 16-27 | exact |
| `common/security/RateLimitFilter.kt` (modify) | middleware | — | `AUTH_PATHS` 24-31, check 38, `addUrlPatterns` 89 | exact |
| CORS `CorsConfigurationSource` bean (in SecurityConfig) | config | — | none in repo | no analog |
| `src/main/resources/application.yml` + `src/test/resources/application.yml` (modify) | config | — | `app.invite.*` block (main yml 46-48) | exact |
| Tests: `src/test/kotlin/com/catspell/api/waitlist/*` | test | — | `invite/InviteAdminEndpointIntegrationTest.kt`, `invite/InviteMigrationTest.kt`, `invite/InviteEnumerationSafetyIntegrationTest.kt`, `invite/InviteSingleUseIntegrationTest.kt`, `auth/EmailVerificationIntegrationTest.kt`, `moderation/ReportNotificationIntegrationTest.kt`, `common/RateLimitIntegrationTest.kt` | exact |

(Paths without a prefix are under `src/main/kotlin/com/catspell/api/`.)

## Pattern Assignments

### `waitlist/model/WaitlistEntry.kt` (entity)

**Analog:** `src/main/kotlin/com/catspell/api/invite/model/Invite.kt` (full file, lines 1-39)

```kotlin
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invites")
class Invite(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    var codeHash: String,
    ...
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Invite) return false
        return id != null && id == other.id
    }
    override fun hashCode(): Int = javaClass.hashCode()
}
```
Apply: `@Column(unique = true)` on `normalized_email` and `confirm_token_hash` (create-drop must produce the unique index `ON CONFLICT` needs — RESEARCH Pitfall 6). Status column: `@Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)` (copy style of `Report.kt:23-25`). Column types/nullability must match V24 exactly (`InviteMigrationTest` validates every entity).

### `waitlist/model/WaitlistEntryRepository.kt`

**Analog:** `src/main/kotlin/com/catspell/api/invite/model/InviteRepository.kt` lines 1-22

```kotlin
interface InviteRepository : JpaRepository<Invite, UUID> {
    fun findByCodeHash(codeHash: String): Invite?

    @Modifying
    @Query("UPDATE Invite i SET i.consumedAt = :now, i.consumedBy = :inviteeId WHERE i.id = :id AND i.consumedAt IS NULL")
    fun markConsumed(@Param("id") id: UUID, @Param("inviteeId") inviteeId: UUID, @Param("now") now: Instant): Int
}
```
Apply to `rotatePendingToken`, `claimConfirm`, `markInvited` (all conditional JPQL UPDATE returning Int; status passed as enum `@Param`). `insertIfAbsent` is a native `INSERT ... ON CONFLICT (normalized_email) DO NOTHING` that supplies id/status/created_at/updated_at explicitly — see RESEARCH Pattern 3. List: `findAllByStatusOrderByConfirmedAtAsc(status)`.

### `waitlist/service/WaitlistService.kt`

**Analogs:** `src/main/kotlin/com/catspell/api/invite/service/InviteService.kt`; `src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt`

Imports / token helpers (InviteService.kt lines 10-17, 26, 71-81):
```kotlin
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

private val secureRandom = SecureRandom()

private fun generateRawCode(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun hashToken(rawCode: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(rawCode.toByteArray(Charsets.UTF_8))
    return HexFormat.of().formatHex(hash)
}
```

Atomic-claim-or-throw pattern (InviteService.kt lines 61-65):
```kotlin
@Transactional
fun consume(invite: Invite, invitee: User) {
    if (inviteRepository.markConsumed(invite.id!!, invitee.id!!, Instant.now()) == 0) {
        throw InviteRequiredException()
    }
```
Apply to `convertToInvite` (`markInvited == 0` → `WaitlistEntryNotConvertibleException`) and `confirm` (`claimConfirm == 0` → return false → error redirect).

Per-email bucket (EmailVerificationService.kt lines 28-30, 35-43):
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
Rename keys to `app.waitlist.per-email-capacity` / `app.waitlist.per-email-refill-hours`. Exhaustion is silent (return, still 202).

Convert: `inviteService.create(null)` (InviteService.kt 33-42) joins the outer `@Transactional`; check `EmailResult.status != EmailSendStatus.SUCCESS` → throw to roll back (EmailSender.kt 10-20). Full flow in RESEARCH Pattern 6.

### `waitlist/event/WaitlistEvents.kt` + `WaitlistEmailListener.kt`

**Analogs:** `src/main/kotlin/com/catspell/api/moderation/event/ReportEvents.kt` (data class carrying only primitives, never the entity) and `ReportNotificationListener.kt` lines 19-37:
```kotlin
@Component
class ReportNotificationListener(
    private val emailSender: EmailSender,
    private val reportEmailRenderer: ReportEmailRenderer,
    ...
) {
    private val log = LoggerFactory.getLogger(ReportNotificationListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReportCreated(event: ReportCreatedEvent) {
        try {
            emailSender.send(reportEmailRenderer.render(operatorEmail, event))
        } catch (e: Exception) {
            log.warn("Operator report email failed for report {}: {}", event.reportId, e.message)
        }
    }
}
```
Event = `WaitlistConfirmationRequestedEvent(email, rawToken)`. Log line must not contain token or full email. Publish only inside `@Transactional join`.

### `waitlist/controller/WaitlistController.kt` (public)

**Analog:** `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` lines 83-89
```kotlin
@PostMapping("/resend-verification")
fun resendVerification(@Valid @RequestBody request: ResendVerificationRequest): ResponseEntity<GenericMessageResponse> {
    emailVerificationService.resend(request.email)
    return ResponseEntity.accepted().body(
        GenericMessageResponse("If an unverified account exists for that email, a verification link has been sent.")
    )
}
```
Join returns one fixed `GenericMessageResponse` in every branch. Confirm: `@GetMapping("/confirm")` with `@RequestParam(required = false) token: String?` → `ResponseEntity.status(HttpStatus.FOUND).location(URI.create(configuredUrl))` plus `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. Location only from `@Value` config (no analog — the existing confirm endpoints use deep links, rejected by D-02).

DTO: `@field:NotBlank @field:Email @field:Size(max = 255) val email: String` (the analog `ResendVerificationRequest` lacks `@NotBlank` — add it, RESEARCH Pitfall 9).

### `waitlist/controller/WaitlistAdminController.kt`

**Analog:** `src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt` lines 1-38
```kotlin
@RestController
@RequestMapping("/api/admin/invites")
class InviteAdminController(
    private val inviteService: InviteService,
    @Value("\${app.invite.admin-token:}") private val adminToken: String
) {
    @PostMapping
    fun issue(
        @RequestHeader(value = "X-Admin-Token", required = false) token: String?,
        @Valid @RequestBody body: IssueInviteRequest
    ): ResponseEntity<IssueInviteResponse> {
        requireValidAdminToken(token)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(IssueInviteResponse(inviteService.create(body.referrerUserId)))
    }
```
Mount at `/api/admin/waitlist`; `GET` with `@RequestParam status` → 200 list; `POST /{id}/invite` → 201 `{entryId, code}`. Call the guard first, before any lookup (no id probing).

### `common/security/AdminTokenGuard.kt` (extract)

**Source:** `InviteAdminController.kt` lines 40-48 (moved verbatim into a `@Component` that takes `@Value("\${app.invite.admin-token:}")`):
```kotlin
private fun requireValidAdminToken(provided: String?) {
    if (adminToken.isBlank() ||
        provided == null ||
        !MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), adminToken.toByteArray(Charsets.UTF_8))
    ) {
        throw AdminAuthException()
    }
}
```
Then change `InviteAdminController` to inject the guard. `InviteAdminEndpointIntegrationTest` is the regression guard.

### `email/service/WaitlistConfirmEmailRenderer.kt` / `WaitlistInviteEmailRenderer.kt`

**Analog:** `src/main/kotlin/com/catspell/api/email/service/EmailVerificationEmailRenderer.kt` lines 1-43
```kotlin
@Component
class EmailVerificationEmailRenderer(
    @Value("\${app.verify-email-url}") private val verifyEmailUrl: String
) {
    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val verifyLink = "$verifyEmailUrl?token=$rawToken"
        ...
        return EmailMessage(to = recipientEmail, subject = subject, htmlBody = htmlBody, textBody = textBody)
    }
}
```
Differences: give every new `@Value` a default (`${app.waitlist.confirm-url:...}`) — the analog has none, and the test yml replaces main yml (RESEARCH Pitfall 4). Invite renderer appends `?code=`.

### `common/exception/Exceptions.kt` + `GlobalExceptionHandler.kt`

**Analog:** `Exceptions.kt:43` `class AdminAuthException(message: String = "Not authorized") : RuntimeException(message)`; handler shape `GlobalExceptionHandler.kt` lines 85-91 / 112-117:
```kotlin
@ExceptionHandler(ResourceNotFoundException::class)
fun handleResourceNotFound(ex: ResourceNotFoundException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
    problem.title = "Not Found"
    return problem
}
```
Add `WaitlistEntryNotConvertibleException` → 409, `title = "Conflict"`, `setProperty("code", "WAITLIST_ENTRY_NOT_CONVERTIBLE")` (code-property style from the `INVITE_REQUIRED` handler, lines ~78-83).

### `V24__create_waitlist_entries.sql`

**Analog:** `V23__create_invites_and_referrals.sql` (UUID PK `gen_random_uuid()`, `TIMESTAMPTZ`, named constraints) + `V21__create_reports_table.sql:9` (`CONSTRAINT chk_reports_category CHECK (category IN (...))`). Recommended DDL in RESEARCH "Code Examples → V24". Never edit V1–V23.

### Modified wiring files (exact lines)

- `common/config/SecurityConfig.kt:28-29` — add `it.requestMatchers("/api/waitlist", "/api/waitlist/confirm").permitAll()` and `it.requestMatchers("/api/admin/waitlist", "/api/admin/waitlist/**").permitAll()` next to `it.requestMatchers("/api/admin/invites").permitAll()`; add `http.cors { }`.
- `common/security/JwtAuthenticationFilter.kt:16-27` — add `path.startsWith("/api/waitlist") ||` to `shouldNotFilter` (not admin paths).
- `common/security/RateLimitFilter.kt:38` — add exact `httpRequest.method == "POST" && path == "/api/waitlist"` alongside `AUTH_PATHS.any { path.startsWith(it) }`; line 89 `registration.addUrlPatterns("/api/auth/*", "/api/waitlist")` (without this, no prod throttle — Pitfall 1).
- `application.yml` (main + test) — new `app.waitlist:` block per RESEARCH "Config block", placed after `app.invite.*` (main lines 46-48).

## Shared Patterns

### Admin auth
**Source:** `InviteAdminController.kt:40-48` → `AdminTokenGuard`. **Apply to:** both admin controllers.

### Hashed single-use token
**Source:** `InviteService.kt:71-81` (generate/hash) + `InviteRepository.kt:19-21` (conditional UPDATE). **Apply to:** join rotate, confirm claim, convert claim.

### Enumeration-safe responses
**Source:** `AuthController.kt:83-89` (fixed 202 body); `InviteService.validate` (all failures → one exception). **Apply to:** join (one 202), confirm (one error URL for every failure).

### Error handling
**Source:** `GlobalExceptionHandler.kt` ProblemDetail handlers. **Apply to:** admin controller (401 via `AdminAuthException`, 404 via `ResourceNotFoundException`, 409 new exception).

### Tests
- Admin endpoint: `src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt` — `@SpringBootTest @AutoConfigureMockMvc @TestPropertySource(properties = ["app.invite.admin-token=test-admin-secret"])`, extends `BaseIntegrationTest`, uses `jdbcTemplate` counts and `sha256Hex` helper to assert hash-at-rest.
- Migration: `src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt` — Flyway enabled + `ddl-auto=validate` on a dedicated DB (`MIGRATION_DB` const); copy with `waitlist_migration_test`.
- Enumeration / single-use: `invite/InviteEnumerationSafetyIntegrationTest.kt`, `invite/InviteSingleUseIntegrationTest.kt`.
- Email capture: `auth/EmailVerificationIntegrationTest.kt` (`@TestConfiguration MockEmailConfig`, `@Primary` relaxed MockK `EmailSender`, `capture(sentMessages)`).
- Async send: `moderation/ReportNotificationIntegrationTest.kt` (Awaitility `await`).
- Rate limit: `common/RateLimitIntegrationTest.kt` (hand-built filter — does NOT cover URL registration; add a registration-level test).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `waitlist/service/WaitlistEmailNormalizer.kt` | utility | transform | No email normalization exists (register/login store verbatim). Use RESEARCH Pattern 2. |
| CORS `CorsConfigurationSource` bean | config | — | CORS not configured anywhere in repo. Use RESEARCH Pattern 7. |
| 302 redirect in confirm endpoint | controller fragment | — | Existing confirm endpoints use `catspell://` deep links (rejected, D-02). Use RESEARCH Pattern 4. |

## Metadata

**Analog search scope:** `src/main/kotlin/com/catspell/api/{invite,auth,email,moderation,common}`, `src/main/resources/db/migration`, `src/test/kotlin/com/catspell/api/{invite,auth,moderation,common}`
**Files scanned:** ~25
**Pattern extraction date:** 2026-10-01
