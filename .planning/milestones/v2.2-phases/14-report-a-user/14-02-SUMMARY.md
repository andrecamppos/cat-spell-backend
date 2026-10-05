---
phase: 14-report-a-user
plan: 02
subsystem: api
tags: [spring, transactional, bucket4j, application-events, testcontainers]

requires:
  - phase: 14-report-a-user (plan 01)
    provides: Report/ReportRepository/ReportCategory, ReportCreatedEvent, SelfReportException, ResourceNotFoundException
  - phase: 13-blocking-unmatch
    provides: BlockService.block(blockerId, blockedId) reused for alsoBlock composition
provides:
  - ReportService.report(reporterId, reportedId, category, details, alsoBlock) — @Transactional
  - Guard chain self-report(400) → target-exists(404) → per-reporter rate-limit(429)
  - No-dedupe persistence (every report row saved)
  - alsoBlock atomic composition with BlockService
  - ReportCreatedEvent published in-tx (delivered AFTER_COMMIT by 14-03)
affects: [14-03 notification listener, 14-04 controller]

tech-stack:
  added: []
  patterns:
    - "Per-reporter Bucket4j token bucket keyed by reporter userId (cloned from PasswordResetService)"
    - "Authenticated flow surfaces a real 429 via ResponseStatusException (not enumeration-safe silent skip)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/moderation/service/ReportService.kt
    - src/test/kotlin/com/catspell/api/moderation/ReportServiceIntegrationTest.kt
  modified: []

key-decisions:
  - "Guard order self→404→429→save→block→publish so invalid self/nonexistent requests never consume a rate-limit token (RESEARCH A5)."
  - "existsById(reportedId) before save for a clean 404 instead of a late FK-violation 500 (Pitfall 3)."
  - "Rate-limit exhaustion throws 429 (authenticated flow) rather than silently skipping (Pitfall 4)."
  - "alsoBlock composes BlockService.block within the same @Transactional so report+block commit atomically (D-09)."
  - "Only ReportCreatedEvent (IDs + strings) is published — no inline email send in the tx (D-02, MOD-07)."

patterns-established:
  - "Out-of-band notification via ApplicationEventPublisher inside the tx; delivery deferred to AFTER_COMMIT listener."

requirements-completed: [MOD-06, MOD-07, MOD-08]

coverage:
  - id: D1
    description: "ReportService persists every report (no dedupe) and returns the new report id"
    requirement: "MOD-06"
    verification:
      - kind: integration
        ref: "ReportServiceIntegrationTest#valid report persists exactly one row and returns id / repeat report of the same pair persists a second row (no dedupe)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Guard chain: self-report → SelfReportException, non-existent target → ResourceNotFoundException, over-cap → 429 ResponseStatusException (independent per-reporter buckets)"
    requirement: "MOD-08"
    verification:
      - kind: integration
        ref: "ReportServiceIntegrationTest#self report / non-existent target / exceeding per-reporter cap throws 429 while a different reporter is unaffected"
        status: pass
    human_judgment: false
  - id: D3
    description: "alsoBlock composes BlockService.block atomically; alsoBlock=false creates no block row"
    requirement: "MOD-08"
    verification:
      - kind: integration
        ref: "ReportServiceIntegrationTest#alsoBlock true creates a block row for the pair / alsoBlock false creates no block row"
        status: pass
    human_judgment: false
  - id: D4
    description: "ReportCreatedEvent (IDs + strings) published in-tx; no inline email send"
    requirement: "MOD-07"
    verification:
      - kind: other
        ref: "source review: publishEvent present, grep shows no emailSender/.send( in ReportService"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-09-25
status: complete
---

# Phase 14 Plan 02: ReportService Summary

**Transactional ReportService with a self→404→429 guard chain, no-dedupe persistence, atomic alsoBlock composition, and an out-of-band ReportCreatedEvent — all proven green under Testcontainers.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 2
- **Files modified:** 2 (both created)

## Accomplishments
- `ReportService.report(...)` is `@Transactional`, guards in order self-report(400) → existsById(404) → per-reporter Bucket4j(429), then persists the report, optionally composes `BlockService.block`, and publishes `ReportCreatedEvent`.
- Per-reporter token bucket keyed by reporter userId (default capacity 5 / 1h refill via `app.report.per-reporter-capacity`/`-refill-hours`).
- No dedupe: repeat reports of the same pair each persist a row (D-04).
- `ReportServiceIntegrationTest` proves persist, no-dedupe (2 rows), self-report, 404, 429 (+ independent buckets), and alsoBlock true/false.

## Task Commits

1. **Task 1: ReportService (guards, alsoBlock, event publish)** - `3dc3eea` (feat)
2. **Task 2: ReportServiceIntegrationTest** - `213d2a6` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/moderation/service/ReportService.kt` - transactional report service
- `src/test/kotlin/com/catspell/api/moderation/ReportServiceIntegrationTest.kt` - service integration test

## Decisions Made
- Followed the plan exactly: guard order, explicit existsById 404, thrown 429, atomic alsoBlock, event-only notification.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. (Hibernate `HHH000478` schema-drop stack traces at context shutdown are pre-existing benign teardown noise; both `*ReportServiceIntegrationTest*` and the `*Report* + *Block*` regression run were BUILD SUCCESSFUL.)

## Next Phase Readiness
- ReportCreatedEvent is published in-tx and ready for the 14-03 AFTER_COMMIT notification listener.
- ReportService.report(...) is ready for the 14-04 controller to call with the JWT principal as reporterId.

---
*Phase: 14-report-a-user*
*Completed: 2026-09-25*
