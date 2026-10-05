# Phase 14: Report a User - Context

**Gathered:** 2026-09-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver **report a user**: an authenticated user reports another user with a **fixed category enum** plus a **required free-text details** field. The report is **persisted first**, then the operator is **notified out-of-band** (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, mirroring the v2.0 push pipeline) so a slow or failing email never blocks or rolls back the write. An optional **`alsoBlock`** flag reuses Phase 13's `BlockService.block()` in the same action. New `reports` table (Flyway **V21**; V19/V20 are taken by Phase 13). New code lands in the existing `moderation/` package.

**In scope:** report endpoint (category + required details + optional `alsoBlock`), `reports` table + entity/repository, `ReportService`, out-of-band operator email notification (renderer + AFTER_COMMIT async listener), per-reporter rate limiting, self-report rejection, reporter-identity non-disclosure.

**Out of scope (own phases / deferred):** age gate (Phase 15), invite gate (Phase 16), waitlist (Phase 17); report triage/status workflow (reviewed/actioned/dismissed), auto-suspend after N reports, automated abuse scoring, admin moderation panel — all v2.x+ (auto-suspend is an explicit anti-feature per research).

</domain>

<decisions>
## Implementation Decisions

### Operator notification routing (MOD-07)
- **D-01:** Add a dedicated config key **`app.report.operator-email`** holding a **single operator address**. The report email is sent via the existing `EmailSender` seam using the already-configured from-address. A dedicated report email renderer (mirroring `PasswordResetEmailRenderer` et al.) builds the body from: reporter id, reported id, category, details, and timestamp.
- **D-02:** The operator email is dispatched **out-of-band** — persist the report inside the transaction, publish a domain event, and send from an `@Async @TransactionalEventListener(AFTER_COMMIT)` handler that swallows+logs failures (same shape as `PushNotificationListener`). A mail outage must never lose, delay, or roll back a persisted report.
- **D-03:** In dev/CI, `EMAIL_ENABLED=false` → the no-op logging `EmailSender` is used (no network sends); tests assert the send via a mock, consistent with the v2.1 email flows.

### Duplicate & abuse handling (MOD-06)
- **D-04:** **Persist every report** (full evidence trail — repeat reports carry signal for the operator). No dedupe/idempotency on the (reporter, reported) pair.
- **D-05:** Guard against flooding with a **per-reporter Bucket4j rate limit** (reuse the existing Bucket4j approach; per-reporter bucket keyed by reporter userId). Over the cap returns **429**, consistent with existing rate-limit conventions. Exact capacity/refill defaults are Claude's discretion (follow the forgot-password `@Value` config-key pattern).

### Evidence captured in the report row (MOD-06)
- **D-06:** Keep the `reports` row **minimal**: `reporter_id`, `reported_id`, `category`, `details`, `created_at` (plus surrogate PK). No conversation snapshot, no copied messages. The operator uses Phase 13's **retained, locked chat history** for out-of-band context.

### Who can be reported + validation (MOD-06, MOD-08)
- **D-07:** A user can report **anyone by userId** (mirrors block D-10) — reachable from a match, a chat, or a discovery/profile card (enables preemptive reporting). Reporting a **non-existent** target returns **404** (reuse `ResourceNotFoundException`).
- **D-08:** **Details** must be **non-blank**, **max 1000 characters** (Bean Validation on the request DTO → RFC 7807 400 on violation). **Category** must be one of the fixed enum values: `HARASSMENT`, `SPAM`, `FAKE_PROFILE`, `INAPPROPRIATE_CONTENT`, `OTHER` (exact enum naming is Claude's discretion; the five semantic categories are fixed by MOD-06). An unknown category is a 400.

### Carried forward from Phase 13 (do not re-decide)
- **D-09:** **`alsoBlock=true` delegates to `BlockService.block(reporterId, reportedId)`** (MOD-08) — do not duplicate the block relationship write. Block is idempotent + runs the shared teardown, so a report-with-block on a matched pair also ends the match and bans rediscovery.
- **D-10:** **Self-report is rejected at the service layer** with a 400, mirroring `SelfBlockException` (add a parallel `SelfReportException` + `GlobalExceptionHandler` mapping).
- **D-11:** **Reporter identity is never exposed to the reported user** — no notification, no field, no side effect visible to the reported party (retaliation risk per research). The report is silent to the target.

### Claude's Discretion
- Exact `reports` table schema/column types and index choices (e.g., index on `reported_id` for future operator queries), category storage (varchar vs enum-check-constraint), and the `Report` entity/repository shape under `moderation/model/`.
- Endpoint URL/verb + request/response DTO shapes and status code for success (e.g., `201 Created` with the report id) — follow existing controller/RFC 7807 conventions.
- Rate-limit capacity/refill defaults and config-key names (follow the `app.forgot-password.*` `@Value` pattern).
- The domain-event data class + async listener naming/placement (mirror `push/event/` — carry only IDs + precomputed strings, no JPA entities across the async boundary).
- Report email subject/body copy and renderer class placement under `email/`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/ROADMAP.md` §"Phase 14: Report a User" — goal, success criteria, `reports` table note, AFTER_COMMIT reuse
- `.planning/REQUIREMENTS.md` — MOD-06, MOD-07, MOD-08 (the requirements this phase closes)

### Milestone research (v2.2)
- `.planning/research/SUMMARY.md` §"Phase 14: Report a User", §"Recommended Approach", §"Open Questions" (operator identity/routing for report emails — resolved here by D-01)
- `.planning/research/PITFALLS.md` §"Pitfall 6: Report email notification blocks or rolls back the request" and §"Security-Specific Pitfalls" (self-report rejection; never reflect reporter identity to the reported user)
- `.planning/research/FEATURES.md` §"Table Stakes" (Report row), §"Optional block-on-report", §"Anti-Features" (auto-suspend / free-text-only)
- `.planning/research/ARCHITECTURE.md` — `moderation/` package layout; `ReportService` responsibilities (persist, notify out-of-band, delegate to `BlockService` on alsoBlock)

### Prior phase context (block relationship this phase reuses)
- `.planning/phases/13-blocking-unmatch/13-CONTEXT.md` — D-10 (block anyone by userId), D-11/D-12 (block ⊇ unmatch, idempotent, self-block rejected), retained-locked chat history invariant

### In-repo code (read before modifying)
- `src/main/kotlin/com/catspell/api/moderation/service/BlockService.kt` — `block(blockerId, blockedId)` reused by `alsoBlock` (D-09); package to extend with `ReportService`
- `src/main/kotlin/com/catspell/api/push/event/PushEvents.kt` + `src/main/kotlin/com/catspell/api/push/event/PushNotificationListener.kt` — the `@Async @TransactionalEventListener(AFTER_COMMIT)` pattern to mirror for the operator email (D-02)
- `src/main/kotlin/com/catspell/api/email/service/EmailSender.kt` — the seam + `EmailMessage`/`EmailResult` contract for the operator notification (D-01/D-03)
- `src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt` — reference for `EmailSender` usage, a `*EmailRenderer`, and the Bucket4j `@Value`-configured rate-limit pattern (D-05)
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` + `GlobalExceptionHandler.kt` — add `SelfReportException` → 400 mapping alongside `SelfBlockException` (D-10)
- `src/main/resources/db/migration/V20__add_match_teardown_state.sql` — latest migration; new `reports` table is **V21**, never edit V1–V20

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BlockService.block(blockerId, blockedId)` — idempotent, self-block-guarded, runs shared teardown; `alsoBlock` calls it directly (no duplicated relationship write).
- `PushNotificationListener` / `PushEvents` — exact AFTER_COMMIT + `@Async` template for out-of-band operator email; carry only IDs/strings across the async boundary.
- `EmailSender` seam + a `*EmailRenderer` class (e.g. `PasswordResetEmailRenderer`) — provider-abstracted, no-op logging default in dev/CI; assert sends via mock.
- Bucket4j per-key bucket pattern in `PasswordResetService` (`ConcurrentHashMap<String, Bucket>` + `@Value` capacity/refill) — reuse keyed by reporter userId for the report rate limit.
- `SelfBlockException` + its `GlobalExceptionHandler` mapping — template for `SelfReportException` (400).
- `ResourceNotFoundException` (404) convention — for an unknown reported target.

### Established Patterns
- Package-per-domain with controller/service/model; new report code extends the existing `moderation/` package.
- `@Transactional` service methods; JPA entities + Spring Data repositories; Flyway append-only migrations (next is **V21**).
- `@EnableAsync` is already on the application; AFTER_COMMIT listeners already run off-thread.
- RFC 7807 error bodies; Bean Validation on request DTOs.

### Integration Points
- New `ReportController` + `ReportService` under `moderation/` (authenticated `POST` report endpoint).
- `ReportService` → `BlockService.block()` when `alsoBlock=true`.
- `ReportService` publishes a report-created domain event → new `@Async @TransactionalEventListener(AFTER_COMMIT)` listener → `EmailSender.send(...)` to `app.report.operator-email`.
- New `reports` table via Flyway V21.

</code_context>

<specifics>
## Specific Ideas

- Reporting must be **silent to the reported user** — no notification, no observable side effect; reporter identity is never surfaced to the target (retaliation risk).
- Repeat reports are **all kept** as evidence signal; only a per-reporter throttle prevents flooding — no dedupe.
- The report row stays lean; the operator leans on the **already-retained locked chat history** from Phase 13 for context rather than snapshotting evidence into the report.

</specifics>

<deferred>
## Deferred Ideas

- Report triage/status workflow (reviewed / actioned / dismissed) with audit trail — v2.x (MOD2-01).
- Automated content/abuse scoring or threshold-based auto-actioning / auto-suspend — v2.x, explicit anti-feature for this milestone (MOD2-02).
- Admin moderation panel — v2.x, scoped project-wide as post-block/report (MOD2-03).

None else — discussion stayed within phase scope.

</deferred>

---

*Phase: 14-report-a-user*
*Context gathered: 2026-09-25*
