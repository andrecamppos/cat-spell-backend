---
phase: 14-report-a-user
plan: 04
subsystem: api
tags: [spring-mvc, rest, jwt, bean-validation, problemdetail, testcontainers, mockmvc]

requires:
  - phase: 14-report-a-user (plan 01)
    provides: ReportRequest / ReportResponse DTOs
  - phase: 14-report-a-user (plan 02)
    provides: ReportService.report(...) with self/404/429 guards and alsoBlock composition
  - phase: 13-blocking-unmatch
    provides: BlockController extractUserId() JWT-principal pattern
provides:
  - ReportController — authenticated POST /api/reports → 201 { reportId }
  - Full HTTP report contract (201 + 400/404/429/401 + 1000/1001 boundary + alsoBlock) proven
affects: []

tech-stack:
  added: []
  patterns:
    - "Controller derives reporter id from the JWT principal (SecurityContextHolder), never the body"
    - "@Valid @RequestBody + enum-typed field push validation/deserialization 400s ahead of the service"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/moderation/controller/ReportController.kt
    - src/test/kotlin/com/catspell/api/moderation/ReportEndpointIntegrationTest.kt
  modified: []

key-decisions:
  - "Success is 201 Created with only { reportId } — no reporter-identifying data toward the reported user (D-11)."
  - "Controller delegates all guards: DTO owns blank/oversized/unknown-category 400s; service owns self(400)/404/429."
  - "Reporter id is always extractUserId() from the JWT principal, never the request body (anti-spoofing)."

patterns-established:
  - "Thin authenticated REST controller over an invariant-owning service; exceptions surface via GlobalExceptionHandler / Spring native mapping."

requirements-completed: [MOD-06, MOD-08]

coverage:
  - id: D1
    description: "POST /api/reports returns 201 with reportId for a valid authenticated request"
    requirement: "MOD-06"
    verification:
      - kind: integration
        ref: "ReportEndpointIntegrationTest#valid report returns 201 with reportId"
        status: pass
    human_judgment: false
  - id: D2
    description: "Rejection paths: 400 blank/1001-char/lowercase-category/self(title Bad Request), 404 unknown target, 429 over cap, 401 unauthenticated; 1000-char boundary passes"
    requirement: "MOD-06"
    verification:
      - kind: integration
        ref: "ReportEndpointIntegrationTest#blank details / details of exactly 1000 chars returns 201 and 1001 returns 400 / unknown lowercase category / self report / non-existent target / over per-reporter cap / unauthenticated"
        status: pass
    human_judgment: false
  - id: D3
    description: "alsoBlock=true returns 201 and blocks the reported user"
    requirement: "MOD-08"
    verification:
      - kind: integration
        ref: "ReportEndpointIntegrationTest#alsoBlock true returns 201 and blocks the reported user"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-09-25
status: complete
---

# Phase 14 Plan 04: ReportController Summary

**Authenticated POST /api/reports returns 201 { reportId }, derives the reporter from the JWT principal, and delegates every guard — full HTTP contract (success + 400/404/429/401 + 1000/1001 boundary + alsoBlock) proven under Testcontainers.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 2
- **Files modified:** 2 (both created)

## Accomplishments
- `ReportController` (@RestController `/api/reports`) exposes a single `@PostMapping` taking `@Valid @RequestBody ReportRequest`, calls `reportService.report(extractUserId(), ...)`, and returns 201 with `ReportResponse(reportId)`.
- Reporter id comes from the JWT principal via `extractUserId()` (never the body); the 201 body carries only `reportId` (D-11).
- `ReportEndpointIntegrationTest` proves 201 happy path, 400 (blank, 1001-char, lowercase category, self-report with title "Bad Request"), 201 at 1000-char boundary, 404 unknown target, 429 over cap, 401 unauthenticated, and 201 + block established for alsoBlock=true.

## Task Commits

1. **Task 1: ReportController (POST /api/reports → 201)** - `9944fef` (feat)
2. **Task 2: ReportEndpointIntegrationTest** - `2bc783f` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/moderation/controller/ReportController.kt` - authenticated report endpoint
- `src/test/kotlin/com/catspell/api/moderation/ReportEndpointIntegrationTest.kt` - HTTP contract test

## Decisions Made
- Followed the plan exactly: 201 { reportId }, principal-derived reporter, pure delegation to DTO/service guards.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. `./gradlew test --tests "*ReportEndpointIntegrationTest*"` and the full `./gradlew test` suite both BUILD SUCCESSFUL (no regression).

## Next Phase Readiness
- Report-a-User feature is complete end-to-end (persist → optional block → out-of-band operator email → HTTP API), fully tested; phase 14 ready for verification/ship.

---
*Phase: 14-report-a-user*
*Completed: 2026-09-25*
