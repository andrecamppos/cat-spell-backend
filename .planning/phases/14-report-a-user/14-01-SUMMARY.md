---
phase: 14-report-a-user
plan: 01
subsystem: database
tags: [flyway, jpa, postgres, bean-validation, problemdetail, spring-events]

requires:
  - phase: 13-blocking-unmatch
    provides: Block entity/repository + SelfBlockException + GlobalExceptionHandler patterns cloned here
provides:
  - V21 reports table (no dedupe, self-report + category CHECK constraints, reported index)
  - Report JPA entity + ReportRepository (countByReportedId)
  - ReportCategory enum (HARASSMENT, SPAM, FAKE_PROFILE, INAPPROPRIATE_CONTENT, OTHER)
  - ReportRequest / ReportResponse DTOs (Bean Validation + reporter-identity-safe response)
  - ReportCreatedEvent (IDs + strings only, async-safe)
  - SelfReportException + 400 ProblemDetail mapping
  - app.report.operator-email config key
affects: [14-02 ReportService, 14-03 notification listener, 14-04 controller]

tech-stack:
  added: []
  patterns:
    - "Flyway append-only migration cloned from V19 blocks table"
    - "Async-safe domain event carrying only IDs + precomputed strings (no JPA entity)"

key-files:
  created:
    - src/main/resources/db/migration/V21__create_reports_table.sql
    - src/main/kotlin/com/catspell/api/moderation/model/Report.kt
    - src/main/kotlin/com/catspell/api/moderation/model/ReportRepository.kt
    - src/main/kotlin/com/catspell/api/moderation/model/ReportCategory.kt
    - src/main/kotlin/com/catspell/api/moderation/model/ReportRequest.kt
    - src/main/kotlin/com/catspell/api/moderation/model/ReportResponse.kt
    - src/main/kotlin/com/catspell/api/moderation/event/ReportEvents.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt
    - src/main/resources/application.yml

key-decisions:
  - "No UNIQUE(reporter_id, reported_id) — every report persists to preserve the evidence trail (D-04)."
  - "category typed as ReportCategory enum so unknown/lowercase/null fails Jackson → 400 (no extra handler needed)."
  - "ReportResponse exposes only reportId — never surfaces reporter identity toward the reported user (D-11)."
  - "chk_reports_no_self + chk_reports_category CHECKs are DB-level defence-in-depth behind service-layer guards."

patterns-established:
  - "Report event carries category.name + precomputed details/createdAt (no LazyInitialization across async boundary)."

requirements-completed: [MOD-06, MOD-07, MOD-08]

coverage:
  - id: D1
    description: "V21 reports table with no-dedupe, self-report + category CHECK constraints and reported index; applies under Testcontainers"
    requirement: "MOD-06"
    verification:
      - kind: integration
        ref: "./gradlew test --tests \"*Block*\" (Flyway applies V21, context loads)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Report entity/repository, ReportCategory enum, ReportRequest/ReportResponse DTOs, ReportCreatedEvent"
    requirement: "MOD-06"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin"
        status: pass
    human_judgment: false
  - id: D3
    description: "SelfReportException → 400 ProblemDetail mapping and app.report.operator-email config key"
    requirement: "MOD-08"
    verification:
      - kind: integration
        ref: "./gradlew test --tests \"*Block*\" (context loads with new config key)"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-09-25
status: complete
---

# Phase 14 Plan 01: Report Foundation Summary

**V21 reports table (no dedupe, self/category CHECK backstops) plus Report entity/repository, ReportCategory enum, validated DTOs, async-safe ReportCreatedEvent, SelfReportException 400 mapping, and the operator-email config key.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 3
- **Files modified:** 10 (7 created, 3 modified)

## Accomplishments
- V21 Flyway migration creates the `reports` table cloned from V19 blocks, deliberately without a UNIQUE(reporter_id, reported_id) constraint (D-04), with `chk_reports_no_self`, `chk_reports_category`, and `idx_reports_reported`.
- `Report` JPA entity + `ReportRepository` (with `countByReportedId`) and the five-constant `ReportCategory` enum matching the SQL CHECK list byte-for-byte.
- `ReportRequest` (enum-typed category + `@NotBlank`/`@Size(max=1000)` details + `alsoBlock=false`), `ReportResponse(reportId)`, and the async-safe `ReportCreatedEvent`.
- `SelfReportException` mapped to a 400 ProblemDetail (title "Bad Request") and the `app.report.operator-email` config key (default ops@catspell.example).

## Task Commits

1. **Task 1: V21 migration + Report entity/repository + ReportCategory** - `40ad18d` (feat)
2. **Task 2: Request/Response DTOs + ReportCreatedEvent** - `705cc96` (feat)
3. **Task 3: SelfReportException + 400 mapping + operator-email config** - `0fb8fa7` (feat)

## Files Created/Modified
- `src/main/resources/db/migration/V21__create_reports_table.sql` - reports table
- `src/main/kotlin/com/catspell/api/moderation/model/{Report,ReportRepository,ReportCategory,ReportRequest,ReportResponse}.kt` - entity/repo/enum/DTOs
- `src/main/kotlin/com/catspell/api/moderation/event/ReportEvents.kt` - ReportCreatedEvent
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` - SelfReportException
- `src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt` - handleSelfReport (400)
- `src/main/resources/application.yml` - app.report.operator-email

## Decisions Made
- No UNIQUE pair constraint (D-04); enum-typed category for automatic 400; reporter-identity-safe response (D-11); DB CHECKs as defence-in-depth.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. (Hibernate `HHH000478` drop-table shutdown-hook logs during test teardown are pre-existing benign noise, not failures — build succeeded.)

## Next Phase Readiness
- All foundation symbols for 14-02 (ReportService), 14-03 (notification listener), 14-04 (controller) are in place and compile.

---
*Phase: 14-report-a-user*
*Completed: 2026-09-25*
