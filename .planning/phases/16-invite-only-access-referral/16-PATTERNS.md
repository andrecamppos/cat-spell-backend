# Phase 16: Invite-Only Access & Referral - Pattern Map

**Mapped:** 2026-09-29
**Files analyzed:** 15 (8 new, 7 modified)
**Analogs found:** 15 / 15 (all have strong in-repo analogs)

> Every mechanic this phase needs already exists in the codebase for email/password
> tokens and for the `moderation/` domain. The correct implementation is *transcription
> of proven patterns into a new `invite/` domain*, not novel design. The analog files
> below are the exact copy-from sources.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `invite/controller/InviteAdminController.kt` (NEW) | controller | request-response | `moderation/controller/ReportController.kt` | role-match (+ shared-secret guard from RESEARCH Pattern 3) |
| `invite/service/InviteService.kt` (NEW) | service | CRUD + transform | `auth/service/EmailVerificationService.kt` + `moderation/service/ReportService.kt` | exact (generation/hash) + role-match |
| `invite/model/Invite.kt` (NEW) | model (entity) | CRUD | `moderation/model/Report.kt` | exact |
| `invite/model/InviteRepository.kt` (NEW) | model (repository) | CRUD + atomic claim | `auth/model/PasswordResetTokenRepository.kt` | exact |
| `invite/model/Referral.kt` (NEW) | model (entity) | CRUD | `moderation/model/Report.kt` | exact |
| `invite/model/ReferralRepository.kt` (NEW) | model (repository) | CRUD | `moderation/model/ReportRepository.kt` | exact |
| `invite/model/IssueInviteRequest.kt` (NEW) | model (DTO) | request-response | `moderation/model/ReportRequest.kt` | exact |
| `invite/model/IssueInviteResponse.kt` (NEW) | model (DTO) | request-response | `moderation/model/ReportResponse.kt` | exact |
| `db/migration/V23__create_invites_and_referrals.sql` (NEW) | migration | DDL | `db/migration/V21__create_reports_table.sql` | exact |
| `auth/service/AuthService.kt` (MOD) | service | request-response | self (`register` + `hashToken` + `markUsed` usage in `resetPassword`) | exact |
| `auth/model/AuthDtos.kt` (MOD) | model (DTO) | request-response | self (`RegisterRequest`) | exact |
| `common/config/SecurityConfig.kt` (MOD) | config | request-response | self (`permitAll` whitelist line 28) | exact |
| `common/exception/Exceptions.kt` (MOD) | model (exception) | — | self (`EmailNotVerifiedException` line 9) | exact |
| `common/exception/GlobalExceptionHandler.kt` (MOD) | config (advice) | request-response | self (`handleEmailNotVerified` lines 67-73) | exact |
| `src/main/resources/application.yml` (MOD) | config | — | self (`app.report.*` / `app.age.*` lines 42-45) | exact |

---

## Pattern Assignments

### `invite/model/Invite.kt` (NEW — entity, CRUD)

**Analog:** `moderation/model/Report.kt` (lines 1-40)

**Imports + entity shape** (Report.kt lines 1-40) — copy the `@Entity`/`@Table`, `@Id @GeneratedValue(strategy = GenerationType.UUID) var id: UUID? = null`, `@ManyToOne(fetch = LAZY) @JoinColumn(...)` for FK relations, `@Column(... nullable=...)` fields, `createdAt: Instant = Instant.now()`, and the id-based `equals`/`hashCode`:
```kotlin
@Entity
@Table(name = "reports")
class Report(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    var reporter: User,
    // ...
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Report) return false
        return id != null && id == other.id
    }
    override fun hashCode(): Int = javaClass.hashCode()
}
```
**Invite-specific fields (Claude's discretion, per RESEARCH V23 example):** `codeHash: String` (`@Column(name="code_hash", nullable=false, unique=true, length=64)`), nullable `referrerUserId: UUID?` (store as a plain `@Column(name="referrer_user_id")` UUID rather than a `@ManyToOne` — the service only needs the id, D-11), `consumedAt: Instant?` (`@Column(name="consumed_at")`, NULL = unconsumed → the single-use claim target), `consumedBy: UUID?`. Note `Report` uses `@ManyToOne` for FKs; for `referrerUserId`/`consumedBy` prefer a raw nullable `UUID` column to keep `consume`/referral logic id-only.

---

### `invite/model/Referral.kt` (NEW — entity, CRUD)

**Analog:** `moderation/model/Report.kt` (same pattern as above). Fields per D-12 / RESEARCH V23: `referrerId: UUID` (NOT NULL), `inviteeId: UUID` (NOT NULL), `inviteId: UUID` (NOT NULL), `createdAt: Instant`. Use raw `UUID` columns (FKs enforced in DDL), not `@ManyToOne`.

---

### `invite/model/InviteRepository.kt` (NEW — repository, atomic single-use claim)

**Analog:** `auth/model/PasswordResetTokenRepository.kt` (lines 1-23) — THE canonical atomic single-use claim. Copy the `findBy*` lookup + `@Modifying @Query(...WHERE ... IS NULL)` returning `Int`:
```kotlin
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByTokenHash(tokenHash: String): PasswordResetToken?
    // ...
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    fun markUsed(@Param("id") id: UUID, @Param("now") now: Instant): Int
}
```
**Invite adaptation:** `fun findByCodeHash(codeHash: String): Invite?` and
```kotlin
@Modifying
@Query("UPDATE Invite i SET i.consumedAt = :now, i.consumedBy = :inviteeId " +
       "WHERE i.id = :id AND i.consumedAt IS NULL")
fun markConsumed(@Param("id") id: UUID, @Param("inviteeId") inviteeId: UUID,
                 @Param("now") now: Instant): Int   // 1 = claimed, 0 = already consumed
```
The `WHERE consumed_at IS NULL` guard is evaluated under a DB row lock, so exactly one concurrent caller gets a non-zero result — closing the read-check-write race (Pitfall 2). Import `@Modifying`, `@Query`, `@Param` (see analog lines 3-8).

---

### `invite/model/ReferralRepository.kt` (NEW — repository, CRUD)

**Analog:** `moderation/model/ReportRepository.kt` (lines 1-8) — minimal repo:
```kotlin
interface ReportRepository : JpaRepository<Report, UUID> {
    fun countByReportedId(reportedId: UUID): Long
}
```
`ReferralRepository` needs only `JpaRepository<Referral, UUID>` (the service calls `.save(...)`); add finders only if a test needs them.

---

### `invite/service/InviteService.kt` (NEW — service, CRUD + transform)

**Analogs:** `auth/service/EmailVerificationService.kt` (generation/hash, lines 33, 96-106) + `auth/service/AuthService.kt` (`hashToken` + atomic-claim usage, lines 122-124, 205-209) + `moderation/service/ReportService.kt` (service shape, `@Value` config, `@Transactional`, existence check).

**`SecureRandom` + Base64url generator** (copy verbatim from `EmailVerificationService.kt` lines 33, 96-100):
```kotlin
private val secureRandom = SecureRandom()

private fun generateRawToken(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)   // ~256-bit, URL-safe, non-sequential
}
```

**SHA-256 hash-at-rest** (copy verbatim from `EmailVerificationService.kt` lines 102-106 / `AuthService.kt` lines 205-209):
```kotlin
private fun hashToken(rawToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
    return HexFormat.of().formatHex(hash)
}
```
Imports needed (from `EmailVerificationService` lines 13-19): `java.security.MessageDigest`, `java.security.SecureRandom`, `java.util.Base64`, `java.util.HexFormat`, `java.time.Instant`, `java.util.UUID`.

**`create(referrerUserId: UUID?)`** — mirror `ReportService.report` existence-check (line 53 `if (!userRepository.existsById(...)) throw ResourceNotFoundException(...)`), but per D-12 reject unknown referrer with `400` (throw an `IllegalArgumentException` → 400 via handler line 176, or a dedicated exception): generate raw code → `hashToken` → `save(Invite(codeHash=..., referrerUserId=...))` → return the RAW code once (D-06, never persisted raw).

**`validate(code: String?)`** — hash the code, `findByCodeHash(...)`; if code null/blank, not found, or `consumedAt != null` → throw the SINGLE generic `InviteRequiredException` (D-10, all three cases indistinguishable — Pitfall 1). Return the `Invite`.

**`consume(invite, invitee)`** — atomic claim then conditional referral (RESEARCH Pattern 2):
```kotlin
@Transactional
fun consume(invite: Invite, invitee: User) {
    if (inviteRepository.markConsumed(invite.id!!, invitee.id!!, Instant.now()) == 0) {
        throw InviteRequiredException()   // raced/already consumed → SAME generic 403 (D-10); tx rolls back
    }
    val referrerId = invite.referrerUserId ?: return          // bootstrap code → NO referral row (D-11, Pitfall 6)
    if (referrerId == invitee.id) return                      // defensive self-referral guard (D-12)
    referralRepository.save(Referral(referrerId = referrerId, inviteeId = invitee.id!!, inviteId = invite.id!!))
}
```

**Service scaffolding** (from `ReportService.kt` lines 22-30): `@Service` class, constructor-injected repos + `UserRepository`, `@Transactional` on mutating methods, `@Value` for any config.

---

### `invite/controller/InviteAdminController.kt` (NEW — controller, request-response + shared-secret guard)

**Analog:** `moderation/controller/ReportController.kt` (lines 1-38) for the `@RestController` + `@RequestMapping` + `@PostMapping` + `@Valid @RequestBody` + `ResponseEntity.status(HttpStatus.CREATED).body(...)` shape:
```kotlin
@RestController
@RequestMapping("/api/reports")
class ReportController(private val reportService: ReportService) {
    @PostMapping
    fun report(@Valid @RequestBody request: ReportRequest): ResponseEntity<ReportResponse> {
        val reportId = reportService.report(/* ... */)
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse(reportId))
    }
}
```
**Shared-secret guard** (RESEARCH Pattern 3, D-03; the `@Value` config key style copied from `LocalAgeVerifier.kt` line 16 / `ReportService.kt` lines 28-29). Inject `@Value("\${app.invite.admin-token:}")` (deny-by-default when blank), read the header via `@RequestHeader(value = "X-Admin-Token", required = false) token: String?`, and compare constant-time:
```kotlin
private fun requireValidAdminToken(provided: String?) {
    if (adminToken.isBlank() || provided == null ||
        !MessageDigest.isEqual(provided.toByteArray(), adminToken.toByteArray())) {
        throw AdminAuthException()   // → generic 401 (D-03), no detail leak
    }
}
```
> NOTE: `ReportController` derives the caller from `SecurityContextHolder` (lines 34-37) — the invite admin route has NO authenticated principal (it is `permitAll`), so the ONLY access barrier is `requireValidAdminToken`. Do not read `SecurityContextHolder` here.

---

### `invite/model/IssueInviteRequest.kt` / `IssueInviteResponse.kt` (NEW — DTOs)

**Analogs:** `moderation/model/ReportRequest.kt` (lines 1-17) and `ReportResponse.kt` (lines 1-7):
```kotlin
data class ReportResponse(val reportId: UUID)
```
- `IssueInviteRequest`: `data class IssueInviteRequest(val referrerUserId: UUID? = null)` — nullable, no `@NotBlank` (bootstrap codes omit it).
- `IssueInviteResponse`: `data class IssueInviteResponse(val code: String)` — the raw code, returned once (D-06).

---

### `db/migration/V23__create_invites_and_referrals.sql` (NEW — migration, DDL)

**Analog:** `db/migration/V21__create_reports_table.sql` (lines 1-12) — the exact conventions: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `UUID ... REFERENCES users(id) ON DELETE ...`, `TIMESTAMPTZ NOT NULL DEFAULT NOW()`, named `CONSTRAINT chk_..._no_self CHECK (a <> b)`, and `CREATE INDEX idx_...`:
```sql
CREATE TABLE reports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ...
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reports_no_self CHECK (reporter_id <> reported_id),
    ...
);
CREATE INDEX idx_reports_reported ON reports(reported_id);
```
Apply to `invites` (`code_hash VARCHAR(64) NOT NULL UNIQUE`, nullable `referrer_user_id UUID REFERENCES users(id) ON DELETE SET NULL`, `consumed_at TIMESTAMPTZ`, `consumed_by`) + `referrals` (`referrer_id`/`invitee_id`/`invite_id` NOT NULL FKs, `chk_referrals_no_self`, `uq_referrals_invitee UNIQUE(invitee_id)`). Full DDL in RESEARCH.md lines 389-410. **Append-only: never edit V1–V22; this is V23** (V22 is the latest, confirmed in `db/migration/`).

---

### `auth/service/AuthService.kt` (MOD — service; compose the invite gate)

**Analog:** self. Current `register` (lines 35-55) has the age gate first (line 39), duplicate-email check (line 41), `userRepository.save` (line 52), then `emailVerificationService.issueAndSend` (line 54). Insert per D-08:
1. Add constructor deps: `private val inviteService: InviteService` and `@Value("\${app.invite.enabled:false}") private val inviteEnabled: Boolean` (mirror the existing `@Value` on line 32).
2. Add `@Transactional` to `register` (currently absent — REQUIRED for validate→save→consume atomicity, Pitfall 3; the annotation is already imported at line 14 and used on `resetPassword`/`verifyEmail`).
3. Order: `ageVerifier.requireAdult(...)` → `val invite = if (inviteEnabled) inviteService.validate(request.inviteCode) else null` → existing `existsByEmail`/`save` → `invite?.let { inviteService.consume(it, savedUser) }` → `issueAndSend`.

The existing `resetPassword` (lines 110-132) is the reference for the atomic-claim usage idiom (`if (repo.markUsed(...) == 0) throw ...`, lines 122-124) that `InviteService.consume` mirrors.

---

### `auth/model/AuthDtos.kt` (MOD — DTO)

**Analog:** self. Add to `RegisterRequest` (lines 9-19) an OPTIONAL field — NO `@field:NotBlank` (Pitfall 5, D-08/D-09):
```kotlin
data class RegisterRequest(
    @field:Email(message = "must be a valid email address")
    val email: String,
    @field:Size(min = 8, message = "must be at least 8 characters")
    val password: String,
    @field:NotNull
    @field:Past(message = "date of birth must be in the past")
    val dateOfBirth: LocalDate,
    val inviteCode: String? = null   // NEW — optional at DTO layer; requiredness enforced in service when gated
)
```

---

### `common/config/SecurityConfig.kt` (MOD — config)

**Analog:** self, line 28 (`it.requestMatchers("/api/auth/register", ...).permitAll()`). Add the admin route to a `permitAll` matcher (D-03) so no auth filter blocks it; the shared-secret check in the controller is the gate:
```kotlin
it.requestMatchers("/api/admin/invites").permitAll()
```

---

### `common/exception/Exceptions.kt` (MOD — exception classes)

**Analog:** self, line 9 (`class EmailNotVerifiedException(message: String = "...") : RuntimeException(message)`). Add two (names are Claude's discretion, D-10/D-03):
```kotlin
class InviteRequiredException(message: String = "A valid invite is required to sign up") : RuntimeException(message)
class AdminAuthException(message: String = "Not authorized") : RuntimeException(message)
```

---

### `common/exception/GlobalExceptionHandler.kt` (MOD — advice)

**Analog:** self, `handleEmailNotVerified` (lines 67-73) — 403 + generic `code`; and `handleInvalidCredentials` (lines 53-58) — 401 shape for the admin auth failure:
```kotlin
@ExceptionHandler(EmailNotVerifiedException::class)
fun handleEmailNotVerified(ex: EmailNotVerifiedException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Email address not verified")
    problem.title = "Forbidden"
    problem.setProperty("code", "EMAIL_NOT_VERIFIED")
    return problem
}
```
Add:
- `handleInviteRequired` → `HttpStatus.FORBIDDEN`, `title = "Forbidden"`, `setProperty("code", "INVITE_REQUIRED")` — ONE body for invalid/consumed/missing (D-10, Pitfall 1).
- `handleAdminAuth` → `HttpStatus.UNAUTHORIZED`, `title = "Unauthorized"`, generic detail, no `code` hint (D-03).

---

### `src/main/resources/application.yml` (MOD — config)

**Analog:** self, `app:` block lines 38-45 (`app.report.operator-email`, `app.age.minimum-age` with `${ENV:default}`). Add under `app:`:
```yaml
  invite:
    enabled: ${INVITE_ENABLED:false}
    admin-token: ${INVITE_ADMIN_TOKEN:}   # deny-by-default when blank (Pitfall 4); NO guessable dev default
```
Note `email.enabled`/`push.enabled` (lines 31, 36) confirm the `enabled: ${ENV:false}` boolean-toggle convention. Per AGENTS.md these are bound via `@Value`, NOT `@ConfigurationProperties` (the IDE "Unknown property" warning is cosmetic).

---

## Shared Patterns

### Hashed single-use token discipline (SecureRandom + SHA-256 + atomic claim)
**Source:** `auth/service/EmailVerificationService.kt` (lines 33, 96-106) + `auth/model/PasswordResetTokenRepository.kt` (lines 20-22) + `auth/service/AuthService.kt` (lines 122-124, 205-209)
**Apply to:** `InviteService` (generate/hash codes), `InviteRepository.markConsumed` (single-use claim), `Invite` entity (`code_hash`, `consumed_at`).
Copy verbatim — do NOT hand-roll a code alphabet, a plaintext store, or a read-check-write claim (Pitfalls 2 & the "storing raw codes" anti-pattern).

### RFC 7807 error mapping with machine-readable `code`
**Source:** `common/exception/GlobalExceptionHandler.kt` (`handleEmailNotVerified` 67-73 for 403+code; `handleInvalidCredentials` 53-58 for 401; `handleIllegalArgument` 176-181 for 400)
**Apply to:** the invite 403 (single generic `INVITE_REQUIRED` for invalid==consumed==missing) and the admin 401. `ProblemDetail.forStatusAndDetail(...)` + `problem.title` + `problem.setProperty("code", ...)`.

### `@Value` custom `app.*` config keys (no `@ConfigurationProperties`)
**Source:** `age/LocalAgeVerifier.kt` (line 16) + `moderation/service/ReportService.kt` (lines 28-29) + `application.yml` lines 38-45
**Apply to:** `app.invite.enabled` (in `AuthService`), `app.invite.admin-token` (in `InviteAdminController`). Pattern: `@Value("\${app.invite.enabled:false}") private val x: Boolean`.

### `permitAll` public-endpoint whitelist
**Source:** `common/config/SecurityConfig.kt` line 28
**Apply to:** `/api/admin/invites` (D-03) — combined with the in-controller shared-secret guard (the app has NO admin role/JWT user).

### New-domain package layout
**Source:** `moderation/` (`controller/`, `service/`, `model/`, `event/`)
**Apply to:** new `com.catspell.api.invite` package (`controller/`, `service/`, `model/`) — no `event/` needed this phase.

### Constant-time secret comparison
**Source:** JDK `MessageDigest.isEqual(...)` (RESEARCH Standard Stack; not yet used in-repo — this is the one refinement beyond existing precedent)
**Apply to:** `InviteAdminController.requireValidAdminToken` — never use `==`/`String.equals` (Pitfall 4 timing side channel).

## No Analog Found

None. Every new/modified file has a strong in-repo analog. The single element with no
existing in-repo usage is `MessageDigest.isEqual` (constant-time compare) — a JDK
primitive, documented in RESEARCH.md Standard Stack (line 112) and Pattern 3.

## Metadata

**Analog search scope:** `src/main/kotlin/com/catspell/api/{auth,moderation,age,common}/`, `src/main/resources/{application.yml,db/migration/}`
**Files scanned:** 15 analog files read in full (all ≤ 227 lines; no large-file paging needed)
**Pattern extraction date:** 2026-09-29
