# Phase 14: Report a User - Pattern Map

**Mapped:** 2026-09-25
**Files analyzed:** 12 (11 new + 2 modified existing)
**Analogs found:** 12 / 12 (all have strong in-repo analogs)

> Every moving part of this phase already exists in a directly copyable form in the codebase. This map assigns each new/modified file its closest analog with concrete excerpts (file path + line numbers). Copy-and-adapt, do not design new mechanisms. Cross-check against `14-RESEARCH.md` Patterns 1–5 (they agree; this file grounds them in verified line numbers).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/resources/db/migration/V21__create_reports_table.sql` | migration | CRUD/DDL | `db/migration/V19__create_blocks_table.sql` | exact (same domain) |
| `moderation/model/Report.kt` | model (JPA entity) | CRUD | `moderation/model/Block.kt` | exact |
| `moderation/model/ReportRepository.kt` | model (repository) | CRUD | `moderation/model/BlockRepository.kt` | exact |
| `moderation/model/ReportCategory.kt` | model (enum) | transform | (no direct enum analog — `@Enumerated(STRING)` + DB CHECK; see No Analog) | new |
| `moderation/model/ReportRequest.kt` / `ReportResponse.kt` | model (DTO) | request-response | `auth/model/AuthDtos.kt` + `moderation/model/BlockedUserResponse.kt` | exact |
| `moderation/service/ReportService.kt` | service | CRUD + event-driven | `moderation/service/BlockService.kt` (+ `PasswordResetService.kt`, `EmailChangeService.kt`) | exact |
| `moderation/controller/ReportController.kt` | controller | request-response | `moderation/controller/BlockController.kt` (+ `AuthController` `@Valid`) | exact |
| `moderation/event/ReportEvents.kt` (`ReportCreatedEvent`) | model (event) | pub-sub | `push/event/PushEvents.kt` | exact |
| `moderation/event/ReportNotificationListener.kt` | service (listener) | event-driven | `push/event/PushNotificationListener.kt` | exact |
| `email/service/ReportEmailRenderer.kt` | service (renderer) | transform | `email/service/PasswordResetEmailRenderer.kt` | exact |
| `common/exception/Exceptions.kt` (+`SelfReportException`) | utility (exception) | — | `SelfBlockException` (line 32) | exact |
| `common/exception/GlobalExceptionHandler.kt` (+mapping) | middleware (advice) | request-response | `handleSelfBlock` (lines 151-156) | exact |
| `src/main/resources/application.yml` (+`app.report.*`) | config | — | `app.*` block (lines 38-41) | exact |
| `src/test/.../moderation/ReportEndpointIntegrationTest.kt` + `ReportServiceIntegrationTest.kt` | test | request-response / event-driven | `moderation/BlockEndpointIntegrationTest.kt` + `BlockServiceIntegrationTest.kt` | exact |

---

## Pattern Assignments

### `db/migration/V21__create_reports_table.sql` (migration, DDL)

**Analog:** `src/main/resources/db/migration/V19__create_blocks_table.sql` (full file, lines 1-10)

```sql
CREATE TABLE blocks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_blocks_pair UNIQUE (blocker_id, blocked_id),
    CONSTRAINT chk_blocks_no_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_blocks_reverse ON blocks(blocked_id, blocker_id);
```

**Adapt to V21 (grounded in this + RESEARCH Pattern 1):**
- Add `category VARCHAR(32) NOT NULL` + `details VARCHAR(1000) NOT NULL` columns.
- **Keep** `chk_reports_no_self CHECK (reporter_id <> reported_id)` (defence-in-depth behind `SelfReportException`).
- Add `chk_reports_category CHECK (category IN ('HARASSMENT','SPAM','FAKE_PROFILE','INAPPROPRIATE_CONTENT','OTHER'))` — VARCHAR+CHECK precedent is `V20` `ended_reason VARCHAR(20)`.
- **DO NOT** add `UNIQUE(reporter_id, reported_id)` — D-04 requires persisting *every* report (no dedupe). This is the ONE deviation from the `blocks` analog.
- Index on `reported_id` only (`CREATE INDEX idx_reports_reported ON reports(reported_id);`) for future operator "all reports against user X" queries.
- **V20 is the last shipped migration** (`ALTER TABLE matches ADD COLUMN ended_at...`); never edit V1–V20. New file is `V21`.

---

### `moderation/model/Report.kt` (model, JPA entity)

**Analog:** `moderation/model/Block.kt` (full file, lines 1-33)

```kotlin
package com.catspell.api.moderation.model

import com.catspell.api.auth.model.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "blocks")
class Block(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    var blocker: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    var blocked: User,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Block) return false
        return id != null && id == other.id
    }
    override fun hashCode(): Int = javaClass.hashCode()
}
```

**Adapt for `Report`:** rename `@Table(name = "reports")`, FK columns → `reporter_id` / `reported_id` (fields `reporter` / `reported`), add:
```kotlin
@Enumerated(EnumType.STRING)
@Column(name = "category", nullable = false, length = 32)
var category: ReportCategory,

@Column(name = "details", nullable = false, length = 1000)
var details: String,
```
Keep the same `id` / `createdAt` / `equals`/`hashCode` shape verbatim.

---

### `moderation/model/ReportRepository.kt` (model, repository)

**Analog:** `moderation/model/BlockRepository.kt` (lines 1-9)

```kotlin
package com.catspell.api.moderation.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlockRepository : JpaRepository<Block, UUID> {
    // ... custom queries
}
```

**Adapt:** `interface ReportRepository : JpaRepository<Report, UUID>`. Phase 14 needs **no custom queries** for the happy path — `save(...)` from `JpaRepository` is sufficient (D-04 persists every row; no dedupe lookup). A `countByReporterIdAndReportedId` / `findByReportedId...` derived method is optional for tests only.

---

### `moderation/model/ReportRequest.kt` / `ReportResponse.kt` (model, DTO)

**Analog (validation):** `auth/model/AuthDtos.kt` (lines 3-12) — Bean Validation on request DTOs:
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
**Analog (simple response record):** `moderation/model/BlockedUserResponse.kt` (lines 6-15) — plain `data class` response.

**Adapt (grounded in RESEARCH Pattern 5):**
```kotlin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ReportRequest(
    val reportedUserId: UUID,
    val category: ReportCategory,          // unknown enum string → HttpMessageNotReadableException → 400
    @field:NotBlank(message = "details is required")
    @field:Size(max = 1000, message = "details must be at most 1000 characters")
    val details: String,
    val alsoBlock: Boolean = false
)

data class ReportResponse(val reportId: UUID)   // 201 body (discretion; A2)
```
> Typing `category` as the `ReportCategory` enum makes an unknown value fail Jackson deserialization → `handleHttpMessageNotReadable` (400). Blank/oversized details → `handleMethodArgumentNotValid` (400 with `violations` array). Both already wired (see Shared Patterns).

---

### `moderation/service/ReportService.kt` (service, CRUD + event-driven)

**Primary analog:** `moderation/service/BlockService.kt` (lines 16-41) — `@Service` + `@Transactional`, self-guard, `getReferenceById` for FK associations, `save`, compose downstream service:

```kotlin
@Service
class BlockService(
    private val blockRepository: BlockRepository,
    private val matchService: MatchService,
    private val userRepository: UserRepository,
    // ...
) {
    @Transactional
    fun block(blockerId: UUID, blockedId: UUID) {
        if (blockerId == blockedId) throw SelfBlockException()
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) return
        blockRepository.save(
            Block(
                blocker = userRepository.getReferenceById(blockerId),
                blocked = userRepository.getReferenceById(blockedId)
            )
        )
        matchService.endMatch(blockerId, blockedId, "BLOCK")
    }
}
```

**Rate-limit analog:** `PasswordResetService.kt` (lines 27-42) — `@Value` config keys + `ConcurrentHashMap<String, Bucket>` + `computeIfAbsent` bucket factory:
```kotlin
@Value("\${app.forgot-password.per-email-capacity:3}") private val perEmailCapacity: Long,
@Value("\${app.forgot-password.per-email-refill-hours:1}") private val perEmailRefillHours: Long
// ...
private val emailBuckets = ConcurrentHashMap<String, Bucket>()
private fun emailBucket(normalizedEmail: String): Bucket = emailBuckets.computeIfAbsent(normalizedEmail) {
    val bandwidth = Bandwidth.builder()
        .capacity(perEmailCapacity)
        .refillIntervally(perEmailCapacity, Duration.ofHours(perEmailRefillHours))
        .build()
    Bucket.builder().addLimit(bandwidth).build()
}
```

**429-surfacing analog (CRITICAL — differs from PasswordResetService):** `EmailChangeService.kt` (lines 75-80) — authenticated flow throws a REAL 429, NOT the silent skip:
```kotlin
if (!emailBucket(normalizedEmail).tryConsume(1)) {
    throw ResponseStatusException(
        HttpStatus.TOO_MANY_REQUESTS,
        "Too many email-change requests for this address. Try again later."
    )
}
```
> ⚠️ Pitfall 4 (RESEARCH): `PasswordResetService` *silently returns* on bucket exhaustion (enumeration-safe). Report is authenticated → copy the `EmailChangeService` `throw ResponseStatusException(TOO_MANY_REQUESTS)` form instead. Import `org.springframework.web.server.ResponseStatusException` + `org.springframework.http.HttpStatus` (as EmailChangeService does, lines 14/17).

**Event-publish analog:** `ChatService.kt` line 37 (constructor: `private val eventPublisher: ApplicationEventPublisher`) + lines 113-116 (publish inside the `@Transactional` method):
```kotlin
import org.springframework.context.ApplicationEventPublisher
// ...
eventPublisher.publishEvent(
    MessageSentEvent(recipientId = otherUserId, conversationId = conversation.id!!, ...)
)
```

**404-for-target (deviation from BlockService):** `BlockService` relies on the FK; Report must do an explicit existence check (Pitfall 3):
```kotlin
if (!userRepository.existsById(reportedId)) throw ResourceNotFoundException("User not found")
```
Once existence confirmed, `userRepository.getReferenceById(reporterId/reportedId)` for both FKs is safe (matches `BlockService.kt:36-37`).

**Assembled `report(...)` method (composition, grounded in RESEARCH Code Examples):**
guard order self-report(400 `SelfReportException`) → target-exists(404 `ResourceNotFoundException`) → rate-limit(429 `ResponseStatusException`) → `reportRepository.save(...)` → `if (alsoBlock) blockService.block(reporterId, reportedId)` → `eventPublisher.publishEvent(ReportCreatedEvent(...))`. Inject `blockService` (compose, D-09) — the nested `@Transactional block()` joins the outer tx (Spring default propagation REQUIRED), so report + block commit atomically and the AFTER_COMMIT email fires only after both succeed.

---

### `moderation/controller/ReportController.kt` (controller, request-response)

**Analog:** `moderation/controller/BlockController.kt` (lines 15-42) — `@RestController` + `@RequestMapping`, JWT principal extraction, `ResponseEntity`:

```kotlin
@RestController
@RequestMapping("/api/blocks")
class BlockController(
    private val blockService: BlockService
) {
    @PostMapping("/{targetUserId}")
    fun block(@PathVariable targetUserId: UUID): ResponseEntity<Void> {
        blockService.block(extractUserId(), targetUserId)
        return ResponseEntity.noContent().build()
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
```

**Adapt (grounded in RESEARCH controller example):** `@RequestMapping("/api/reports")`, single `@PostMapping` taking `@Valid @RequestBody request: ReportRequest` (add `import jakarta.validation.Valid` + `org.springframework.web.bind.annotation.RequestBody`, mirroring `AuthController` `@Valid` usage), returns `ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse(reportId))`. Copy the `extractUserId()` helper verbatim. **No field/response reaches the reported user** (D-11) — response returns only the reporter's new `reportId`.

---

### `moderation/event/ReportEvents.kt` — `ReportCreatedEvent` (model, event)

**Analog:** `push/event/PushEvents.kt` (lines 1-23) — plain-data events, IDs + precomputed strings only, NEVER JPA entities:

```kotlin
package com.catspell.api.push.event

import java.util.UUID

/**
 * ... They carry only IDs and precomputed strings (never JPA entities) so the async AFTER_COMMIT
 * listener has no lazy-load dependency on a closed persistence context (RESEARCH Pitfall 1).
 */
data class MatchCreatedEvent(
    val matchId: UUID,
    val userId1: UUID,
    val userId2: UUID
)
```

**Adapt:** `data class ReportCreatedEvent(val reportId: UUID, val reporterId: UUID, val reportedId: UUID, val category: String, val details: String, val createdAt: Instant)` in package `com.catspell.api.moderation.event`. Pass `category.name` (String), not the enum, and precomputed `details` + `createdAt` — never the `Report` entity (Pitfall 2 → `LazyInitializationException`).

---

### `moderation/event/ReportNotificationListener.kt` (service, event-driven listener)

**Analog:** `push/event/PushNotificationListener.kt` (lines 16-31) — `@Component` + `@Async @TransactionalEventListener(AFTER_COMMIT)` + swallow/log:

```kotlin
@Component
class PushNotificationListener(
    private val pushNotificationService: PushNotificationService
) {
    private val log = LoggerFactory.getLogger(PushNotificationListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMatchCreated(event: MatchCreatedEvent) {
        try {
            pushNotificationService.notifyMatch(listOf(event.userId1, event.userId2), event.matchId)
        } catch (e: Exception) {
            log.warn("Match push dispatch failed for match {}: {}", event.matchId, e.message)
        }
    }
}
```

**Adapt (grounded in RESEARCH Pattern 2):**
```kotlin
@Component
class ReportNotificationListener(
    private val emailSender: EmailSender,
    private val reportEmailRenderer: ReportEmailRenderer,
    @Value("\${app.report.operator-email}") private val operatorEmail: String
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
> Imports mirror the analog: `org.springframework.scheduling.annotation.Async`, `org.springframework.transaction.event.{TransactionPhase, TransactionalEventListener}`, `org.slf4j.LoggerFactory`. `@EnableAsync` is already on `CatSpellApplication` — no config change. The swallow/log `try/catch` is mandatory (D-02 / MOD-07): a mail outage must never roll back or delay the persisted report.

---

### `email/service/ReportEmailRenderer.kt` (service, renderer/transform)

**Analog:** `email/service/PasswordResetEmailRenderer.kt` (lines 1-43) — `@Component`, `render(...)` returns an `EmailMessage`, html + text bodies via trimmed template strings:

```kotlin
@Component
class PasswordResetEmailRenderer(
    @Value("\${app.reset-password-url}") private val resetPasswordUrl: String
) {
    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val subject = "Reset your Cat Spell password"
        val htmlBody = """...""".trimIndent()
        val textBody = """...""".trimIndent()
        return EmailMessage(to = recipientEmail, subject = subject, htmlBody = htmlBody, textBody = textBody)
    }
}
```

**`EmailMessage` contract** (`email/service/EmailSender.kt` lines 3-8): `to`, `subject`, `htmlBody`, `textBody` (all `String`). `EmailSender.send()` returns `EmailResult` (line 18-20).

**Adapt:** `fun render(operatorEmail: String, event: ReportCreatedEvent): EmailMessage`. Body includes reporter id, reported id, category, details, timestamp (D-01). Subject e.g. `"New user report: <category>"`. `to = operatorEmail`. Keep the html/text dual-body shape. **Do NOT** build body inline in the listener/service (Don't-Hand-Roll: email body assembly).
> In dev/CI, `LoggingEmailSender` (`@ConditionalOnProperty email.enabled=false, matchIfMissing=true`, lines 8-9) is the active `EmailSender` and never throws — tests assert the send via a MockK spy/mock.

---

### `common/exception/Exceptions.kt` (+`SelfReportException`) & `GlobalExceptionHandler.kt` (+mapping)

**Exception analog:** `Exceptions.kt` line 32:
```kotlin
class SelfBlockException(message: String = "Cannot block yourself") : RuntimeException(message)
```
**Add** (alongside it): `class SelfReportException(message: String = "Cannot report yourself") : RuntimeException(message)`. `ResourceNotFoundException` (line 13) already exists — reuse for the 404 target case.

**Handler analog:** `GlobalExceptionHandler.kt` `handleSelfBlock` (lines 151-156):
```kotlin
@ExceptionHandler(SelfBlockException::class)
fun handleSelfBlock(ex: SelfBlockException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Cannot block yourself")
    problem.title = "Bad Request"
    return problem
}
```
**Add** an identical `@ExceptionHandler(SelfReportException::class)` → 400 `ProblemDetail`, title "Bad Request".
> No new handler needed for 429 (`ResponseStatusException` is handled natively by Spring), 404 (`handleResourceNotFound`, lines 83-88), unknown category (`handleHttpMessageNotReadable`, lines 90-99), or validation (`handleMethodArgumentNotValid`, lines 22-34) — all already wired.

---

### `application.yml` (+`app.report.*`)

**Analog:** `application.yml` `app:` block (lines 38-41):
```yaml
app:
  reset-password-url: ${RESET_PASSWORD_URL:catspell://reset-password}
  verify-email-url: ${VERIFY_EMAIL_URL:catspell://verify-email}
  confirm-email-change-url: ${CONFIRM_EMAIL_CHANGE_URL:catspell://confirm-email-change}
```
**Add** under `app:`:
```yaml
  report:
    operator-email: ${REPORT_OPERATOR_EMAIL:ops@catspell.example}
```
Rate-limit keys (`app.report.per-reporter-capacity`, `...refill-hours`) can stay `@Value(...:default)`-only (as `app.forgot-password.*` are — they have defaults but no yml entry required). Tests must have `app.report.operator-email` resolvable (default suffices).

---

### Integration tests

**Endpoint-test analog:** `moderation/BlockEndpointIntegrationTest.kt` (lines 18-101 for scaffolding, 105-132 for assertions) — `@SpringBootTest @AutoConfigureMockMvc`, extends `BaseIntegrationTest()` (Testcontainers), `registerAndGetToken` / `setupUser` / `userId(email)` helpers, `MockMvc` + `jsonPath` assertions:
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class BlockEndpointIntegrationTest : BaseIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    // setupUser(...) -> TestUser(token, catId, id)
    @Test
    fun `self block returns 400 problem body`() {
        val a = setupUser("ep-selfA@example.com", "EpSelfA", "MALE", "EpSelfCat")
        mockMvc.perform(post("/api/blocks/${a.id}").header("Authorization", "Bearer ${a.token}"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.detail").exists())
    }
}
```
> Note import quirks: `tools.jackson.databind.ObjectMapper` (not `com.fasterxml`) and `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (Spring Boot 4 packages) — copy verbatim from `BlockEndpointIntegrationTest.kt:4,8`.

**Service-test analog (MockK + spies + repo assertions):** `moderation/BlockServiceIntegrationTest.kt` (lines 32-45) — `@Autowired` the service + repositories directly, `assertThrows<SelfBlockException>` for service-layer guards, count rows via repository/`jdbcTemplate`.

**Report test coverage (from RESEARCH Requirements→Test Map):**
- Valid report persists a `reports` row (201); repeat (reporter,reported) persists a SECOND row (no dedupe, D-04).
- Blank details / >1000 chars / unknown category → 400; non-existent target → 404; over-cap → 429.
- After commit, operator `EmailSender.send()` invoked — assert via a MockK mock/spy `EmailSender` bean (dev default is `LoggingEmailSender`); the reported user receives NOTHING (D-11).

---

## Shared Patterns

### JWT principal extraction (auth)
**Source:** `BlockController.kt:38-41`
**Apply to:** `ReportController`
```kotlin
private fun extractUserId(): UUID {
    val authentication = SecurityContextHolder.getContext().authentication!!
    return UUID.fromString(authentication.principal as String)
}
```

### Error → HTTP mapping (RFC 7807 ProblemDetail)
**Source:** `GlobalExceptionHandler.kt` — throw a domain exception, add/reuse an `@ExceptionHandler` returning `ProblemDetail`.
**Apply to:** all `ReportService` guards.
- self-report → add `handleSelfReport` (copy of `handleSelfBlock`, lines 151-156) → 400
- non-existent target → `ResourceNotFoundException` → `handleResourceNotFound` (lines 83-88) → 404 [existing]
- rate limit → `ResponseStatusException(TOO_MANY_REQUESTS)` → Spring native → 429
- blank/oversized details → Bean Validation → `handleMethodArgumentNotValid` (lines 22-34) → 400 + `violations` [existing]
- unknown category → `HttpMessageNotReadableException` → `handleHttpMessageNotReadable` (lines 90-99) → 400 [existing]

### Persist-then-notify out-of-band (event-driven) — MOST IMPORTANT
**Source:** `ChatService.kt:37,113` (publish inside `@Transactional`) + `PushNotificationListener.kt:16-31` (`@Async @TransactionalEventListener(AFTER_COMMIT)` + swallow/log)
**Apply to:** `ReportService.report()` (publish `ReportCreatedEvent`) + `ReportNotificationListener` (send operator email). Event carries IDs + strings only; listener swallows all exceptions.

### Per-key Bucket4j throttle
**Source:** `PasswordResetService.kt:34-42` (bucket construction) + `EmailChangeService.kt:75-80` (429 surfacing for authenticated flows)
**Apply to:** `ReportService` (keyed by reporter `userId`; throw 429, do NOT silent-skip).

### Bean Validation on request DTOs
**Source:** `AuthDtos.kt:3-12`
**Apply to:** `ReportRequest` (`@field:NotBlank`, `@field:Size(max=1000)` on `details`; typed enum `category`).

### Testcontainers integration test scaffold
**Source:** `BlockEndpointIntegrationTest.kt:18-101` + `BlockServiceIntegrationTest.kt:32-45`
**Apply to:** both new report test classes. Extend `BaseIntegrationTest()`, reuse `setupUser`/`userId` helpers, Spring Boot 4 import packages.

---

## No Analog Found

| File | Role | Data Flow | Reason / Guidance |
|------|------|-----------|-------------------|
| `moderation/model/ReportCategory.kt` | model (enum) | transform | No existing domain enum uses `@Enumerated(STRING)` + DB CHECK in this codebase. Create a plain Kotlin `enum class ReportCategory { HARASSMENT, SPAM, FAKE_PROFILE, INAPPROPRIATE_CONTENT, OTHER }` in `moderation/model/`. DB CHECK constraint in V21 must list the exact same names. (RESEARCH A1: naming is discretion; the five semantics are fixed by D-08.) The persistence pattern (`@Enumerated(EnumType.STRING)`) is standard JPA; the CHECK-constraint precedent is `matches.ended_reason VARCHAR(20)` from V20. |

> Everything else has a strong, verified in-repo analog.

## Metadata

**Analog search scope:** `src/main/kotlin/com/catspell/api/{moderation,push,auth,email,common}/`, `src/main/resources/{db/migration,application.yml}`, `src/test/kotlin/com/catspell/api/moderation/`
**Files scanned:** 18 (analogs read in full or targeted ranges)
**Pattern extraction date:** 2026-09-25
