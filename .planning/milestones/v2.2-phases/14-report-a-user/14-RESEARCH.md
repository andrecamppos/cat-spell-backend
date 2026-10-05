# Phase 14: Report a User - Research

**Researched:** 2026-09-25
**Domain:** Backend moderation — report persistence + out-of-band operator notification (Kotlin / Spring Boot / PostgreSQL / JPA / Bucket4j)
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Operator notification routing (MOD-07)**
- **D-01:** Add a dedicated config key **`app.report.operator-email`** holding a **single operator address**. The report email is sent via the existing `EmailSender` seam using the already-configured from-address. A dedicated report email renderer (mirroring `PasswordResetEmailRenderer` et al.) builds the body from: reporter id, reported id, category, details, and timestamp.
- **D-02:** The operator email is dispatched **out-of-band** — persist the report inside the transaction, publish a domain event, and send from an `@Async @TransactionalEventListener(AFTER_COMMIT)` handler that swallows+logs failures (same shape as `PushNotificationListener`). A mail outage must never lose, delay, or roll back a persisted report.
- **D-03:** In dev/CI, `EMAIL_ENABLED=false` → the no-op logging `EmailSender` is used (no network sends); tests assert the send via a mock, consistent with the v2.1 email flows.

**Duplicate & abuse handling (MOD-06)**
- **D-04:** **Persist every report** (full evidence trail — repeat reports carry signal for the operator). No dedupe/idempotency on the (reporter, reported) pair.
- **D-05:** Guard against flooding with a **per-reporter Bucket4j rate limit** (reuse the existing Bucket4j approach; per-reporter bucket keyed by reporter userId). Over the cap returns **429**, consistent with existing rate-limit conventions. Exact capacity/refill defaults are Claude's discretion (follow the forgot-password `@Value` config-key pattern).

**Evidence captured in the report row (MOD-06)**
- **D-06:** Keep the `reports` row **minimal**: `reporter_id`, `reported_id`, `category`, `details`, `created_at` (plus surrogate PK). No conversation snapshot, no copied messages. The operator uses Phase 13's **retained, locked chat history** for out-of-band context.

**Who can be reported + validation (MOD-06, MOD-08)**
- **D-07:** A user can report **anyone by userId** (mirrors block D-10) — reachable from a match, a chat, or a discovery/profile card (enables preemptive reporting). Reporting a **non-existent** target returns **404** (reuse `ResourceNotFoundException`).
- **D-08:** **Details** must be **non-blank**, **max 1000 characters** (Bean Validation on the request DTO → RFC 7807 400 on violation). **Category** must be one of the fixed enum values: `HARASSMENT`, `SPAM`, `FAKE_PROFILE`, `INAPPROPRIATE_CONTENT`, `OTHER` (exact enum naming is Claude's discretion; the five semantic categories are fixed by MOD-06). An unknown category is a 400.

**Carried forward from Phase 13 (do not re-decide)**
- **D-09:** **`alsoBlock=true` delegates to `BlockService.block(reporterId, reportedId)`** (MOD-08) — do not duplicate the block relationship write. Block is idempotent + runs the shared teardown, so a report-with-block on a matched pair also ends the match and bans rediscovery.
- **D-10:** **Self-report is rejected at the service layer** with a 400, mirroring `SelfBlockException` (add a parallel `SelfReportException` + `GlobalExceptionHandler` mapping).
- **D-11:** **Reporter identity is never exposed to the reported user** — no notification, no field, no side effect visible to the reported party (retaliation risk per research). The report is silent to the target.

### Claude's Discretion
- Exact `reports` table schema/column types and index choices (e.g., index on `reported_id` for future operator queries), category storage (varchar vs enum-check-constraint), and the `Report` entity/repository shape under `moderation/model/`.
- Endpoint URL/verb + request/response DTO shapes and status code for success (e.g., `201 Created` with the report id) — follow existing controller/RFC 7807 conventions.
- Rate-limit capacity/refill defaults and config-key names (follow the `app.forgot-password.*` `@Value` pattern).
- The domain-event data class + async listener naming/placement (mirror `push/event/` — carry only IDs + precomputed strings, no JPA entities across the async boundary).
- Report email subject/body copy and renderer class placement under `email/`.

### Deferred Ideas (OUT OF SCOPE)
- Report triage/status workflow (reviewed / actioned / dismissed) with audit trail — v2.x (MOD2-01).
- Automated content/abuse scoring or threshold-based auto-actioning / auto-suspend — v2.x, explicit anti-feature for this milestone (MOD2-02).
- Admin moderation panel — v2.x, scoped project-wide as post-block/report (MOD2-03).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MOD-06 | A user can report another user with a fixed category (harassment, spam, fake profile, inappropriate content, other) plus a required free-text details field | `reports` table (V21) + `Report` entity mirroring `Block.kt`; Kotlin `ReportCategory` enum + `@Enumerated(STRING)`; Bean Validation `@field:NotBlank`/`@field:Size(max=1000)` on the request DTO (see AuthDtos.kt pattern); unknown enum → `HttpMessageNotReadableException` → 400 (already handled). See **Architecture Patterns**, **Code Examples**. |
| MOD-07 | A report is persisted and the operator is notified out-of-band (async / AFTER_COMMIT) — a slow or failing email never blocks or rolls back the report | Mirror `PushNotificationListener` + `PushEvents` exactly: publish a `ReportCreatedEvent` (IDs + precomputed strings only) inside the `@Transactional` service, consume it via `@Async @TransactionalEventListener(AFTER_COMMIT)`, swallow+log failures. `@EnableAsync` already on `CatSpellApplication`. New `ReportEmailRenderer` + `EmailSender.send()` to `app.report.operator-email`. See **Pattern 2**, **Code Examples**. |
| MOD-08 | The reporter can optionally block the reported user in the same action ("also block" flag) | `alsoBlock=true` → `BlockService.block(reporterId, reportedId)` in the same `@Transactional` method (propagation REQUIRED, commits together). Block is idempotent + runs shared teardown. See **Pattern 3**. |
</phase_requirements>

## Summary

Phase 14 is a **small, low-risk, additive backend slice** that lands entirely inside the existing `moderation/` package and reuses seams already proven in v2.0 (AFTER_COMMIT async dispatch) and v2.1 (EmailSender + `*EmailRenderer` + Bucket4j `@Value` per-key rate limit). There is **no new external dependency** — Spring, Spring Data JPA, Bean Validation, and Bucket4j are all already on the classpath and in active use [VERIFIED: in-repo `PasswordResetService.kt`, `RateLimitFilter.kt`, `AuthDtos.kt`]. The work is: one Flyway migration (`V21`), a `Report` JPA entity + repository, a `ReportCategory` enum, request/response DTOs, a `ReportService`, a `ReportController`, a `ReportCreatedEvent` + `@Async @TransactionalEventListener(AFTER_COMMIT)` listener, a `ReportEmailRenderer`, a `SelfReportException` + `GlobalExceptionHandler` mapping, and one new config key `app.report.operator-email`.

The dominant correctness requirement is **transaction/notification decoupling** (MOD-07): the report must be persisted and committed *before* the operator email is attempted, and any email failure must be swallowed. The codebase already implements exactly this shape for push notifications — `MatchService`/`ChatService` publish domain events inside their `@Transactional` methods and `PushNotificationListener` consumes them with `@Async @TransactionalEventListener(AFTER_COMMIT)`, catching and logging all exceptions [VERIFIED: in-repo `PushNotificationListener.kt`, `ChatService.kt:113`]. Phase 14 mirrors this one-to-one for the operator email.

The security surface is well-bounded and maps cleanly to existing patterns: authenticated reporter (JWT principal via `SecurityContextHolder`, as in `BlockController`), self-report rejection (`SelfReportException` → 400, mirroring `SelfBlockException`), 404 for a non-existent target (`ResourceNotFoundException`), per-reporter flood throttle (Bucket4j → 429 via `ResponseStatusException`, as in `EmailChangeService`), Bean-Validation input limits on free-text details, and strict reporter-identity non-disclosure (the report is silent to the target; reporter id travels only to the operator email).

**Primary recommendation:** Copy the `moderation/` block slice + the `push/event/` async pattern + the `PasswordResetService` Bucket4j/renderer pattern verbatim; add a `V21` `reports` table with a `CHECK` category constraint and a `reported_id` index; persist-then-publish inside one `@Transactional` `ReportService.report(...)`, delegate to `BlockService.block()` when `alsoBlock`, and notify the operator from an AFTER_COMMIT async listener.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Accept report request (auth, DTO validation, extract reporter id) | API / Controller (`ReportController`) | — | Mirrors `BlockController`; JWT principal + `@Valid @RequestBody` [VERIFIED: `BlockController.kt`, `AuthController.kt:38`] |
| Business rules (self-report guard, target-exists check, rate limit, persist, alsoBlock, publish event) | API / Backend (`ReportService`) | Database | `@Transactional` service owns invariants + orchestration, exactly like `BlockService` [VERIFIED: `BlockService.kt`] |
| Report persistence (evidence row) | Database / Storage (`reports` table, `Report` entity/repo) | — | Flyway V21 + Spring Data JPA, same as `blocks` (V19) [VERIFIED: `V19__create_blocks_table.sql`, `Block.kt`] |
| Optional block-on-report | API / Backend (`BlockService.block()`) | Database | Reuse Phase 13; compose don't duplicate (D-09) [VERIFIED: `BlockService.kt:31`] |
| Operator notification (out-of-band) | API / Backend (`ReportCreatedEvent` + `@Async` AFTER_COMMIT listener → `EmailSender`) | External (email provider seam) | Decoupled from the request/persistence thread; mirrors push pipeline (D-02) [VERIFIED: `PushNotificationListener.kt`] |
| Email body rendering | API / Backend (`ReportEmailRenderer`) | — | Mirrors `PasswordResetEmailRenderer` [VERIFIED: `PasswordResetEmailRenderer.kt`] |
| Per-reporter flood throttle | API / Backend (in-service Bucket4j `ConcurrentHashMap<String, Bucket>`) | — | Reuse `PasswordResetService`/`EmailChangeService` per-key bucket pattern (D-05) [VERIFIED: `PasswordResetService.kt:34-42`] |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | Web/controller, `@Transactional`, `@Async`, `@TransactionalEventListener`, DI | Already the app framework [VERIFIED: AGENTS.md; `PushNotificationListener.kt`] |
| Kotlin | 2.4 / JVM 17 | Language | Project standard [VERIFIED: AGENTS.md] |
| Spring Data JPA / Hibernate | (Boot-managed) | `Report` entity + `ReportRepository` | Same as `Block`/`BlockRepository` [VERIFIED: `Block.kt`, `BlockRepository.kt`] |
| PostgreSQL + Flyway | 16 / (Boot-managed) | `reports` table via `V21` migration | Append-only migrations; last shipped is V20 [VERIFIED: `V20__add_match_teardown_state.sql`] |
| Bean Validation (jakarta.validation) | (Boot starter-validation) | `@field:NotBlank`, `@field:Size(max=1000)` on details | Already used on auth DTOs [VERIFIED: `AuthDtos.kt:7,10`] |
| Bucket4j (io.github.bucket4j) | (existing) | Per-reporter flood throttle | Already used for per-key rate limiting [VERIFIED: `PasswordResetService.kt:8-9`] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| MockK | (existing test dep) | Mock `EmailSender` in tests to assert the operator send (D-03) | Unit/integration verification of notification [CITED: .windsurf/rules STACK — MockK 1.13.x; VERIFIED in-repo test usage of Testcontainers] |
| Testcontainers (PostgreSQL) | (existing test dep) | Integration tests spin up real Postgres | Phase gate integration tests [VERIFIED: AGENTS.md "tests use Testcontainers"; `BaseIntegrationTest`] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@Async @TransactionalEventListener(AFTER_COMMIT)` | Synchronous `EmailSender.send()` inside the report txn | REJECTED — violates D-02/MOD-07 + PITFALLS Pitfall 6: a slow/failing mail send would block or roll back the persisted report |
| Kotlin enum + `@Enumerated(STRING)` + DB `CHECK` | Postgres native `ENUM` type | Native enum needs migration ceremony to extend; VARCHAR+CHECK matches `matches.ended_reason VARCHAR(20)` precedent [VERIFIED: `V20`] and is easier to evolve |
| In-service Bucket4j bucket (per-reporter) | Servlet `RateLimitFilter` (per-IP) | `RateLimitFilter` keys on client IP and is wired only to `/api/auth/*` [VERIFIED: `RateLimitFilter.kt:89`]; D-05 requires per-*reporter-userId* keying, which belongs in the service like `PasswordResetService` |

**Installation:** None. No new packages. All required libraries (Spring, Spring Data JPA, Bean Validation, Bucket4j, MockK, Testcontainers) are already declared and in active use [VERIFIED: in-repo `PasswordResetService.kt`, `AuthDtos.kt`, `RateLimitFilter.kt`].

## Package Legitimacy Audit

**OMITTED — no external packages are installed in this phase.** This is a backend-only slice that reuses libraries already on the classpath (Spring Boot 4.0.6, Spring Data JPA, jakarta.validation, Bucket4j, MockK, Testcontainers). No `build.gradle.kts` dependency change is expected. The package-legitimacy gate is therefore not applicable [VERIFIED: in-repo confirms all needed libs already imported].

## Architecture Patterns

### System Architecture Diagram

```
[Authenticated user taps Report]
        │  POST /api/reports (JWT)  { reportedUserId, category, details, alsoBlock }
        ▼
┌──────────────────────────────────────────────────────────────┐
│ ReportController                                               │
│  - extract reporterId from SecurityContextHolder principal     │
│  - @Valid @RequestBody: details non-blank & ≤1000, category    │
│    is a known enum (else 400 before service is entered)         │
└───────────────┬───────────────────────────────────────────────┘
                ▼  reporterId, reportedUserId, category, details, alsoBlock
┌──────────────────────────────────────────────────────────────┐
│ ReportService.report(...)   @Transactional                    │
│  1. if reporterId == reportedUserId → SelfReportException(400) │
│  2. if !userRepository.existsById(reportedUserId)              │
│         → ResourceNotFoundException(404)                        │
│  3. if !reporterBucket(reporterId).tryConsume(1)               │
│         → 429 (ResponseStatusException TOO_MANY_REQUESTS)       │
│  4. reportRepository.save(Report(...))   ← EVIDENCE PERSISTED   │
│  5. if alsoBlock → blockService.block(reporterId, reportedId)  │
│  6. eventPublisher.publishEvent(ReportCreatedEvent(ids+strings))│
└───────────────┬───────────────────────────────────────────────┘
                │  (TX COMMITS)                    │ returns 201 + reportId
                ▼  AFTER_COMMIT, @Async            ▼  (silent to reported user — D-11)
┌──────────────────────────────────────────────┐ [Reporter gets 201 Created]
│ ReportNotificationListener                    │
│  @Async @TransactionalEventListener(AFTER_    │
│   COMMIT)                                      │
│  try { EmailSender.send(                       │
│     ReportEmailRenderer.render(event)) }       │
│  catch (e) { log.warn(...) }  ← swallow (D-02) │
└───────────────┬───────────────────────────────┘
                ▼  to app.report.operator-email
        [Operator inbox]  (dev/CI: LoggingEmailSender no-op)
```
Data flow: request → controller (validate + authn) → service (guards → persist → optional block → publish) → commit → async operator email. The reported user receives nothing at any stage (D-11).

### Recommended Project Structure

```
src/main/kotlin/com/catspell/api/
├── moderation/
│   ├── controller/   ReportController.kt          # NEW (sits beside BlockController)
│   ├── service/      ReportService.kt             # NEW (sits beside BlockService)
│   ├── event/        ReportEvents.kt              # NEW — ReportCreatedEvent (or under push-style event/)
│   │                 ReportNotificationListener.kt# NEW — @Async AFTER_COMMIT
│   └── model/        Report.kt, ReportRepository.kt, ReportCategory.kt,
│                     ReportRequest.kt, ReportResponse.kt   # NEW
├── email/service/    ReportEmailRenderer.kt       # NEW (beside PasswordResetEmailRenderer)
├── common/exception/ Exceptions.kt (+SelfReportException), GlobalExceptionHandler.kt (+mapping)
└── resources/db/migration/  V21__create_reports_table.sql  # NEW (V20 is last shipped)
```
> Placement of the event/listener is Claude's discretion (D-05 discretion). Two acceptable options: (a) a new `moderation/event/` package, or (b) mirror `push/event/` naming. Keep it inside `moderation/` since the report domain owns it.

### Pattern 1: JPA evidence entity + Flyway V21 (mirror `blocks`)

**What:** A minimal `reports` row with surrogate PK, two FK columns to `users`, category, details, timestamp.
**When to use:** Persisting the report evidence (D-06).
**Example (V21 migration — grounded in `V19__create_blocks_table.sql`):**
```sql
-- Source: pattern from src/main/resources/db/migration/V19__create_blocks_table.sql
CREATE TABLE reports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category     VARCHAR(32) NOT NULL,
    details      VARCHAR(1000) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reports_no_self CHECK (reporter_id <> reported_id),
    CONSTRAINT chk_reports_category CHECK (category IN
        ('HARASSMENT','SPAM','FAKE_PROFILE','INAPPROPRIATE_CONTENT','OTHER'))
);
-- Index for future operator queries "all reports against user X" (D-06 note / discretion)
CREATE INDEX idx_reports_reported ON reports(reported_id);
```
> NOTE: unlike `blocks`, there is **NO** `UNIQUE(reporter_id, reported_id)` — D-04 requires persisting *every* report (no dedupe). The `chk_reports_no_self` check is defence-in-depth behind the service-layer `SelfReportException` (D-10). `details VARCHAR(1000)` mirrors the Bean-Validation `@Size(max=1000)` (D-08); the DB length cap is a backstop, not the primary validation.

### Pattern 2: Persist-then-notify out-of-band (mirror push pipeline) — MOD-07 / D-02

**What:** Publish a plain-data domain event inside the `@Transactional` write; an `@Async @TransactionalEventListener(AFTER_COMMIT)` listener sends the email and swallows failures.
**When to use:** The operator notification — the single most important correctness rule of this phase.
**Example (grounded in `PushNotificationListener.kt` + `ChatService.kt`):**
```kotlin
// event: carry ONLY ids + precomputed strings across the async boundary — never JPA entities
// Source: src/main/kotlin/com/catspell/api/push/event/PushEvents.kt
data class ReportCreatedEvent(
    val reportId: UUID,
    val reporterId: UUID,
    val reportedId: UUID,
    val category: String,
    val details: String,
    val createdAt: Instant
)

// listener: Source: src/main/kotlin/com/catspell/api/push/event/PushNotificationListener.kt
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
> `@EnableAsync` is already present on `CatSpellApplication` [VERIFIED: `CatSpellApplication.kt:8`], so no config change is needed. `EmailSender.send()` returns an `EmailResult` and the `LoggingEmailSender` never throws [VERIFIED: `EmailSender.kt`, `LoggingEmailSender.kt`], but a real provider could — hence the `try/catch`.

### Pattern 3: Compose block-on-report inside the same transaction — MOD-08 / D-09

**What:** When `alsoBlock`, call `BlockService.block(reporterId, reportedId)` from within `ReportService.report(...)`.
**When to use:** The `alsoBlock=true` path.
**Example:**
```kotlin
// Source: src/main/kotlin/com/catspell/api/moderation/service/BlockService.kt:31
if (alsoBlock) {
    blockService.block(reporterId, reportedId) // idempotent + runs shared teardown (ends match, bans rediscovery)
}
```
> Both `ReportService.report` and `BlockService.block` are `@Transactional`; the nested call joins the outer transaction (Spring default propagation REQUIRED), so the report row and the block row commit atomically, and the AFTER_COMMIT operator email fires only after both succeed.

### Pattern 4: Per-reporter Bucket4j throttle → 429 — D-05

**What:** A `ConcurrentHashMap<String, Bucket>` keyed by reporter userId; over-cap throws `ResponseStatusException(TOO_MANY_REQUESTS)`.
**When to use:** Flood/abuse guard on every report attempt.
**Example (grounded in `PasswordResetService.kt` bucket + `EmailChangeService.kt` 429 surfacing):**
```kotlin
// bucket construction — Source: PasswordResetService.kt:34-42
private val reporterBuckets = ConcurrentHashMap<String, Bucket>()
private fun reporterBucket(reporterId: UUID): Bucket = reporterBuckets.computeIfAbsent(reporterId.toString()) {
    val bandwidth = Bandwidth.builder()
        .capacity(perReporterCapacity)
        .refillIntervally(perReporterCapacity, Duration.ofHours(perReporterRefillHours))
        .build()
    Bucket.builder().addLimit(bandwidth).build()
}
// over-cap 429 — Source: EmailChangeService.kt:75-80 (authenticated flow surfaces a REAL 429, not silent)
if (!reporterBucket(reporterId).tryConsume(1)) {
    throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many reports. Try again later.")
}
```
> Config keys are discretion; follow the `app.forgot-password.*` `@Value(...:default)` style, e.g. `@Value("\${app.report.per-reporter-capacity:5}")` and `@Value("\${app.report.per-reporter-refill-hours:1}")`. Report is an **authenticated** flow, so surface a real 429 (like `EmailChangeService`), NOT the silent-skip used by the enumeration-safe `PasswordResetService`/`EmailVerificationService`.

### Pattern 5: DTO validation + unknown-category → 400 — D-08

**What:** `@Valid @RequestBody` on the controller; `@field:NotBlank` + `@field:Size(max=1000)` on `details`; `category` typed as the `ReportCategory` enum so an unknown value fails Jackson deserialization.
**Example (grounded in `AuthDtos.kt`):**
```kotlin
// Source: src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt:6-11
data class ReportRequest(
    val reportedUserId: UUID,
    val category: ReportCategory,                              // unknown value → 400 (see note)
    @field:NotBlank(message = "details is required")
    @field:Size(max = 1000, message = "details must be at most 1000 characters")
    val details: String,
    val alsoBlock: Boolean = false
)
```
> Two valid category-error mechanisms, both yield 400: (a) a bad enum string throws `HttpMessageNotReadableException` → already mapped to 400 by `GlobalExceptionHandler.handleHttpMessageNotReadable` [VERIFIED: `GlobalExceptionHandler.kt:90-99`]; or (b) accept `category: String` and validate against the enum in the service, throwing `IllegalArgumentException` → 400 [VERIFIED: `GlobalExceptionHandler.kt:158`]. Prefer (a) — it keeps validation declarative. `MethodArgumentNotValidException` from Bean Validation is also already mapped to a 400 with a `violations` array [VERIFIED: `GlobalExceptionHandler.kt:22-34`].

### Anti-Patterns to Avoid
- **Synchronous operator email inside the report transaction:** blocks/rolls back the persisted report (PITFALLS Pitfall 6). Use AFTER_COMMIT async.
- **Passing the `Report` JPA entity into the event/listener:** the async listener runs after the persistence context is closed → `LazyInitializationException`. Carry only ids + strings (as `PushEvents` documents) [VERIFIED: `PushEvents.kt:5-9`].
- **`getReferenceById` for the target instead of an existence check:** `getReferenceById` returns a lazy proxy without hitting the DB, so a non-existent target would fail late (FK violation at flush) instead of a clean 404. Use `userRepository.existsById(reportedId)` and throw `ResourceNotFoundException` (D-07) [VERIFIED: `BlockService.kt:36` uses `getReferenceById` and relies on the FK — report must NOT, because D-07 mandates 404].
- **Reflecting reporter identity to the reported user:** any notification, response field, or observable side effect to the target is a retaliation vector (D-11 / PITFALLS Security). Keep the action silent to the target.
- **Adding a `UNIQUE(reporter_id, reported_id)` constraint:** would break D-04 (persist every report). Do not add it.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Out-of-band notification after commit | A manual thread / `CompletableFuture` / "send later" queue | `@Async @TransactionalEventListener(AFTER_COMMIT)` (existing pattern) | Spring guarantees the listener runs only after commit and off-thread; already proven in push pipeline [VERIFIED: `PushNotificationListener.kt`] |
| Per-reporter rate limiting | A hand-rolled counter/timestamp map | Bucket4j `ConcurrentHashMap<String, Bucket>` (existing pattern) | Token-bucket refill semantics + already a dependency [VERIFIED: `PasswordResetService.kt`] |
| Request validation | Manual `if (details.isBlank())` checks in the service | jakarta Bean Validation `@field:NotBlank`/`@field:Size` + `@Valid` | Declarative, produces RFC 7807 400 automatically [VERIFIED: `AuthDtos.kt`, `GlobalExceptionHandler.kt`] |
| Email body assembly | Inline string-building in the service/listener | A dedicated `ReportEmailRenderer` (mirror `PasswordResetEmailRenderer`) | Keeps I/O text out of business logic; consistent with v2.1 [VERIFIED: `PasswordResetEmailRenderer.kt`] |
| Optional block | Duplicate INSERT into `blocks` | `BlockService.block()` | Idempotent + runs shared teardown; single source of truth (D-09) [VERIFIED: `BlockService.kt:31`] |
| Error → HTTP mapping | Manual `ResponseEntity` status wiring per error | Throw domain exception; add `@ExceptionHandler` in `GlobalExceptionHandler` | RFC 7807 bodies produced centrally [VERIFIED: `GlobalExceptionHandler.kt`] |

**Key insight:** Every moving part of this phase already exists in the codebase in a directly copyable form. The plan should be "clone and adapt existing slices," not "design new mechanisms."

## Common Pitfalls

### Pitfall 1: Operator email blocks or rolls back the report
**What goes wrong:** Calling `EmailSender.send()` inline in the report transaction makes report latency track email latency, and an email exception rolls back the persisted report.
**Why it happens:** Convenience — the send is right there in the service method.
**How to avoid:** Persist first, publish `ReportCreatedEvent`, send from `@Async @TransactionalEventListener(AFTER_COMMIT)` with a swallowing try/catch (D-02, Pattern 2).
**Warning signs:** A test that makes `EmailSender.send()` throw and then finds no `reports` row; report endpoint p99 tracks mail RTT. [CITED: .planning/research/PITFALLS.md Pitfall 6]

### Pitfall 2: Lazy entity crosses the async boundary
**What goes wrong:** The listener accesses a lazy field of a `Report`/`User` after the persistence context closed → `LazyInitializationException`, email silently lost.
**Why it happens:** Passing the entity (not plain data) in the event.
**How to avoid:** The event carries only ids + precomputed strings (reporter id, reported id, category, details, timestamp) — as `PushEvents` already documents [VERIFIED: `PushEvents.kt:5-9`].
**Warning signs:** `LazyInitializationException` in listener logs; email works in a single-tx test but fails in async runtime.

### Pitfall 3: 404 vs FK-violation for a non-existent target
**What goes wrong:** Using `getReferenceById(reportedId)` yields a proxy; a bogus target surfaces as a 500-ish DataIntegrityViolation at flush, not a clean 404.
**Why it happens:** Copying `BlockService` verbatim (it relies on the FK).
**How to avoid:** Explicit `userRepository.existsById(reportedId)` → `ResourceNotFoundException` (D-07) before saving.
**Warning signs:** Reporting a random UUID returns 500 or a constraint-violation body instead of 404.

### Pitfall 4: Silencing 429 like the recovery flows
**What goes wrong:** Copying `PasswordResetService`'s *silent* bucket skip (`return` on exhaustion) instead of surfacing a 429.
**Why it happens:** Both use the same bucket construction; the reset flow is enumeration-safe and intentionally silent.
**How to avoid:** Report is authenticated (no enumeration concern) — throw `ResponseStatusException(TOO_MANY_REQUESTS)` like `EmailChangeService.kt:76` (D-05).
**Warning signs:** Flooding never returns 429; the caller can't tell it was throttled.

### Pitfall 5: Leaking reporter identity to the target
**What goes wrong:** A push/email/response field lets the reported user learn who reported them → retaliation.
**Why it happens:** Reusing a notification helper that touches the reported user, or echoing reporter data broadly.
**How to avoid:** No event, notification, or response reaches the reported user; reporter id travels only in the operator email (D-11). The endpoint response returns only the new report id to the reporter.
**Warning signs:** Any test asserting the reported user receives *anything* after being reported. [CITED: .planning/research/PITFALLS.md Security-Specific]

## Code Examples

### ReportService skeleton (composition of Patterns 2–5)
```kotlin
// Grounded in BlockService.kt, PasswordResetService.kt, EmailChangeService.kt
@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val userRepository: UserRepository,
    private val blockService: BlockService,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${app.report.per-reporter-capacity:5}") private val perReporterCapacity: Long,
    @Value("\${app.report.per-reporter-refill-hours:1}") private val perReporterRefillHours: Long
) {
    private val reporterBuckets = ConcurrentHashMap<String, Bucket>()

    @Transactional
    fun report(reporterId: UUID, reportedId: UUID, category: ReportCategory,
               details: String, alsoBlock: Boolean): UUID {
        if (reporterId == reportedId) throw SelfReportException()                 // 400 (D-10)
        if (!userRepository.existsById(reportedId))                               // 404 (D-07)
            throw ResourceNotFoundException("User not found")
        if (!reporterBucket(reporterId).tryConsume(1))                           // 429 (D-05)
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many reports. Try again later.")

        val report = reportRepository.save(                                       // persist first (D-04)
            Report(
                reporter = userRepository.getReferenceById(reporterId),
                reported = userRepository.getReferenceById(reportedId),
                category = category,
                details = details
            )
        )
        if (alsoBlock) blockService.block(reporterId, reportedId)                 // compose (D-09)

        eventPublisher.publishEvent(                                              // notify out-of-band (D-02)
            ReportCreatedEvent(report.id!!, reporterId, reportedId, category.name, details, report.createdAt)
        )
        return report.id!!
    }
    // reporterBucket(...) as in Pattern 4
}
```
> Once the target's existence is confirmed, `getReferenceById` for BOTH FK associations is safe and matches `BlockService` [VERIFIED: `BlockService.kt:36-37`].

### ReportController (mirror BlockController auth + AuthController @Valid)
```kotlin
// Grounded in BlockController.kt:38-41 and AuthController.kt:38
@RestController
@RequestMapping("/api/reports")
class ReportController(private val reportService: ReportService) {
    @PostMapping
    fun report(@Valid @RequestBody request: ReportRequest): ResponseEntity<ReportResponse> {
        val reportId = reportService.report(
            extractUserId(), request.reportedUserId, request.category, request.details, request.alsoBlock
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse(reportId)) // 201 (discretion)
    }
    private fun extractUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication!!.principal as String)
}
```

### SelfReportException + handler (mirror SelfBlock)
```kotlin
// Exceptions.kt — Source: Exceptions.kt:32
class SelfReportException(message: String = "Cannot report yourself") : RuntimeException(message)

// GlobalExceptionHandler.kt — Source: GlobalExceptionHandler.kt:151-156
@ExceptionHandler(SelfReportException::class)
fun handleSelfReport(ex: SelfReportException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Cannot report yourself")
    problem.title = "Bad Request"
    return problem
}
```

### New config key (application.yml, mirror `app.*` block)
```yaml
# Source: src/main/resources/application.yml:38-41
app:
  report:
    operator-email: ${REPORT_OPERATOR_EMAIL:ops@catspell.example}
```
> Tests must set this property (or rely on the default) so the listener wiring loads. It's an authenticated internal address (single operator, D-01), not user-supplied.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Notify inline in the write path | Persist-then-notify via AFTER_COMMIT async events | Established in v2.0 push pipeline | Report must reuse it (D-02) [VERIFIED: `PushNotificationListener.kt`] |
| Per-IP servlet rate-limit filter | Per-key in-service Bucket4j buckets for authenticated per-user throttles | v2.1 recovery flows | Report keys per reporter userId, not IP (D-05) [VERIFIED: `PasswordResetService.kt`] |

**Deprecated/outdated:**
- The `.windsurf/rules` STACK section lists Spring Boot 3.3.x / Kotlin 2.0.x — this is stale aspirational stack text; the **actual** runtime is Spring Boot 4.0.6 / Kotlin 2.4 / JVM 17 per AGENTS.md and confirmed by in-repo imports (e.g., `org.springframework.boot.webmvc.test.autoconfigure...`) [VERIFIED: AGENTS.md; `BlockEndpointIntegrationTest.kt:8`]. Treat AGENTS.md as authoritative.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The five category enum constants are named `HARASSMENT`, `SPAM`, `FAKE_PROFILE`, `INAPPROPRIATE_CONTENT`, `OTHER` | Pattern 1/5 | LOW — exact naming is explicitly Claude's discretion (D-08); only the five semantics are fixed. The DB CHECK must match whatever names are chosen. |
| A2 | Success status is `201 Created` returning the new report id | Pattern/Controller | LOW — explicitly discretion (D-08 canonical refs); could be `204 No Content` if the plan prefers to return nothing (but returning the id aids the reporter/UX). |
| A3 | Default rate-limit is capacity 5 / refill 1h per reporter | Pattern 4 | LOW — explicitly discretion (D-05); tune to product expectations. Wrong value only affects throttle aggressiveness, not correctness. |
| A4 | `details VARCHAR(1000)` DB length backstop alongside Bean Validation `@Size(max=1000)` | Pattern 1 | LOW — cosmetic backstop; primary enforcement is Bean Validation. Could use `TEXT` and rely solely on validation. |
| A5 | Order of guards is self-report(400) → not-found(404) → rate-limit(429) | Code Examples | LOW — any order is defensible; this order avoids consuming a rate-limit token for obviously-invalid (self/nonexistent) requests. |

**If this table is empty:** N/A — all items above are LOW-risk discretion calls, not blocking unknowns.

## Open Questions

1. **Category enum home (`moderation/model` vs shared).**
   - What we know: D-08 fixes five semantic categories; naming/placement is discretion.
   - What's unclear: whether any other feature will consume `ReportCategory`.
   - Recommendation: put `ReportCategory` in `moderation/model/`; nothing else needs it this milestone.

2. **Event/listener package placement.**
   - What we know: D-05 discretion; must carry ids+strings only.
   - What's unclear: `moderation/event/` vs mirroring `push/event/`.
   - Recommendation: `moderation/event/` (domain owns its events); the shape is identical to `push/event/`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL (+PostGIS) | `reports` table / integration tests | ✓ | 16 / 3.4 | — (Testcontainers spins its own for tests; local via `podman compose up -d`) |
| Podman | Local Postgres/MinIO for `bootRun` | ✓ (project standard) | — | AGENTS.md: use `podman compose` (NOT docker) |
| Testcontainers | Integration tests | ✓ | (existing test dep) | — (tests self-provision containers) |
| Email provider | Operator notification (prod only) | ✓ seam | — | dev/CI: `LoggingEmailSender` no-op (`EMAIL_ENABLED=false`, D-03) [VERIFIED: `LoggingEmailSender.kt:9`] |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** Email provider — dev/CI falls back to the no-op `LoggingEmailSender` by default.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (`@SpringBootTest` + `@AutoConfigureMockMvc`) + Testcontainers (PostgreSQL) + MockK |
| Config file | none dedicated — extends `com.catspell.api.BaseIntegrationTest` (Testcontainers base) [VERIFIED: `BlockEndpointIntegrationTest.kt:20`] |
| Quick run command | `./gradlew test --tests "com.catspell.api.moderation.Report*"` |
| Full suite command | `./gradlew test` (integration tests spin up their own containers via Testcontainers — AGENTS.md) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MOD-06 | Report with valid category + details persists a `reports` row | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |
| MOD-06 | Blank details → 400; details >1000 chars → 400; unknown category → 400 | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |
| MOD-06 | Reporting a non-existent userId → 404 | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |
| MOD-06 | Repeat report of same (reporter,reported) persists a SECOND row (no dedupe, D-04) | integration | `./gradlew test --tests "*ReportServiceIntegrationTest*"` | ❌ Wave 0 |
| MOD-06 | Over per-reporter cap → 429 (D-05) | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |
| MOD-07 | Report persists AND operator `EmailSender.send()` is invoked after commit (assert via MockK/spy) | integration | `./gradlew test --tests "*ReportServiceIntegrationTest*"` | ❌ Wave 0 |
| MOD-07 | Report survives an email-send failure (`EmailSender.send` throws → row still committed, no 5xx) | integration | `./gradlew test --tests "*ReportNotificationIntegrationTest*"` | ❌ Wave 0 |
| MOD-08 | `alsoBlock=true` creates a `blocks` row + tears down any match (delegates to BlockService) | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |
| MOD-08 | `alsoBlock=false` creates NO block row | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |
| MOD-06/08 | Self-report → 400 `SelfReportException` (RFC 7807 body) | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |
| MOD-07 (D-11) | Reported user receives NO notification/observable side effect | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |
| MOD-06 | Unauthenticated report request → 401 | integration | `./gradlew test --tests "*ReportEndpointIntegrationTest*"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.catspell.api.moderation.Report*"`
- **Per wave merge:** `./gradlew test --tests "com.catspell.api.moderation.*"`
- **Phase gate:** `./gradlew test` (full suite) green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/catspell/api/moderation/ReportEndpointIntegrationTest.kt` — HTTP surface (201, 400×3, 404, 429, 401, self-report, alsoBlock on/off, target silence) — covers MOD-06/07/08
- [ ] `src/test/kotlin/com/catspell/api/moderation/ReportServiceIntegrationTest.kt` — persist-every-report (no dedupe), operator send invoked after commit — covers MOD-06/07
- [ ] `src/test/kotlin/com/catspell/api/moderation/ReportNotificationIntegrationTest.kt` — report survives an email-send failure (swallow+log) — covers MOD-07
- Shared fixtures: reuse the `BaseIntegrationTest` + register/login/profile helper pattern from `BlockEndpointIntegrationTest.kt` (copyable). To assert the operator send / simulate a failure, provide a MockK-spied or throwing `EmailSender` `@TestConfiguration` bean.
- Framework install: none — JUnit5 + Testcontainers + MockK already present.

## Project Constraints (from .windsurf/rules + AGENTS.md)

- **GSD workflow enforcement:** do not make direct repo edits outside a GSD workflow; use `/gsd-execute-phase` for planned phase work [CITED: .windsurf/rules "GSD Workflow Enforcement"].
- **Tech stack is non-negotiable:** Kotlin + Spring Boot + PostgreSQL; backend API only (no frontend/mobile code in this repo) [CITED: .windsurf/rules "Constraints"].
- **Containers: Podman, not Docker.** Use `podman compose ...` for local Postgres/MinIO; do not assume Docker [CITED: AGENTS.md "Containers"].
- **Tests use Testcontainers:** `./gradlew test` spins up its own containers [CITED: AGENTS.md "Testing"].
- **Custom config keys bound via `@Value`/`@ConditionalOnProperty`** (no `@ConfigurationProperties`); the IDE "Unknown property" warning for `app.*`/`email.*` is cosmetic — follow this for `app.report.operator-email` and the rate-limit keys [CITED: AGENTS.md "Stack"].
- **Flyway is append-only:** next migration is `V21`; never edit `V1`–`V20` [CITED: CONTEXT.md D + AGENTS.md; VERIFIED `V20` is latest].
- **Actual runtime is Spring Boot 4.0.6 / Kotlin 2.4 / JVM 17** (AGENTS.md), superseding the older 3.3.x/2.0.x text inside `.windsurf/rules` STACK.

## Sources

### Primary (HIGH confidence — in-repo, read this session)
- `src/main/kotlin/com/catspell/api/moderation/service/BlockService.kt` — block reuse, self-guard, idempotent teardown, `getReferenceById`
- `src/main/kotlin/com/catspell/api/moderation/model/Block.kt`, `BlockRepository.kt`, `controller/BlockController.kt` — entity/repo/controller shape to mirror
- `src/main/kotlin/com/catspell/api/push/event/PushEvents.kt`, `PushNotificationListener.kt` — AFTER_COMMIT `@Async` pattern + ids-only event rule
- `src/main/kotlin/com/catspell/api/email/service/EmailSender.kt`, `LoggingEmailSender.kt`, `PasswordResetEmailRenderer.kt` — seam + no-op default + renderer
- `src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt`, `EmailChangeService.kt` — Bucket4j per-key bucket + real-429 surfacing via `ResponseStatusException`
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt`, `GlobalExceptionHandler.kt` — `SelfBlockException`/`ResourceNotFoundException` + RFC 7807 mappings (400/404/429/validation)
- `src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt`, `controller/AuthController.kt` — `@field:` Bean Validation + `@Valid @RequestBody`
- `src/main/resources/db/migration/V19__create_blocks_table.sql`, `V20__add_match_teardown_state.sql`, `application.yml` — migration + config patterns
- `src/main/kotlin/com/catspell/api/CatSpellApplication.kt` — `@EnableAsync` present
- `src/test/kotlin/com/catspell/api/moderation/BlockEndpointIntegrationTest.kt` — Testcontainers/MockMvc test harness to clone
- `.planning/research/{SUMMARY,PITFALLS,FEATURES,ARCHITECTURE}.md` — milestone research (Pitfall 6, anti-features, `ReportService` responsibilities)
- `AGENTS.md`, `.windsurf/rules` — project constraints

### Secondary / Tertiary
- None required — every claim is grounded in in-repo code or the locked CONTEXT.md/milestone research; no external web lookups were necessary for this reuse-only phase.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; all libraries verified in active in-repo use
- Architecture: HIGH — every pattern is a direct clone of an existing, shipped in-repo slice
- Pitfalls: HIGH — grounded in milestone PITFALLS + verified against actual code seams

**Research date:** 2026-09-25
**Valid until:** ~2026-10-25 (stable — reuses established internal patterns; re-check only if the moderation/push/email seams change)
