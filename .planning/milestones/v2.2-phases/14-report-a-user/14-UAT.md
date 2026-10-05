---
status: complete
phase: 14-report-a-user
source: [14-01-SUMMARY.md, 14-02-SUMMARY.md, 14-03-SUMMARY.md, 14-04-SUMMARY.md]
started: 2026-09-28T11:28:01Z
updated: 2026-09-28T11:35:13Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running server; wipe ephemeral DB state (podman compose down -v), start Postgres+MinIO, and run ./gradlew bootRun from an empty schema. Flyway applies all migrations including V21 (reports table) without error, context boots clean, and a POST /api/reports for a valid authenticated request returns 201 with a reportId.
result: pass

### 2. V21 reports table with constraints and index
expected: V21 reports table with no-dedupe, self-report + category CHECK constraints and reported index; applies under Testcontainers
result: pass
source: automated
coverage_id: 14-01-D1

### 3. Report entity/repository, enum, DTOs, event
expected: Report entity/repository, ReportCategory enum, ReportRequest/ReportResponse DTOs, ReportCreatedEvent
result: pass
source: automated
coverage_id: 14-01-D2

### 4. SelfReportException 400 mapping + operator-email config
expected: SelfReportException → 400 ProblemDetail mapping and app.report.operator-email config key
result: pass
source: automated
coverage_id: 14-01-D3

### 5. ReportService persists every report (no dedupe)
expected: ReportService persists every report (no dedupe) and returns the new report id
result: pass
source: automated
coverage_id: 14-02-D1

### 6. Guard chain (self/not-found/over-cap)
expected: Guard chain: self-report → SelfReportException, non-existent target → ResourceNotFoundException, over-cap → 429 ResponseStatusException (independent per-reporter buckets)
result: pass
source: automated
coverage_id: 14-02-D2

### 7. alsoBlock composes BlockService atomically
expected: alsoBlock composes BlockService.block atomically; alsoBlock=false creates no block row
result: pass
source: automated
coverage_id: 14-02-D3

### 8. ReportCreatedEvent published in-tx (no inline email)
expected: ReportCreatedEvent (IDs + strings) published in-tx; no inline email send
result: pass
source: automated
coverage_id: 14-02-D4

### 9. ReportEmailRenderer renders operator email
expected: ReportEmailRenderer renders an operator-addressed email containing reporter/reported/category/details/timestamp
result: pass
source: automated
coverage_id: 14-03-D1

### 10. Async AFTER_COMMIT operator notification (exactly once)
expected: Async AFTER_COMMIT listener emails the operator exactly once after the report commits
result: pass
source: automated
coverage_id: 14-03-D2

### 11. Report survives email-send failure
expected: A failing email send neither propagates to the caller nor rolls back the persisted report
result: pass
source: automated
coverage_id: 14-03-D3

### 12. POST /api/reports returns 201 with reportId
expected: POST /api/reports returns 201 with reportId for a valid authenticated request
result: pass
source: automated
coverage_id: 14-04-D1

### 13. Rejection paths (400/404/429/401) + boundary
expected: Rejection paths: 400 blank/1001-char/lowercase-category/self, 404 unknown target, 429 over cap, 401 unauthenticated; 1000-char boundary passes
result: pass
source: automated
coverage_id: 14-04-D2

### 14. alsoBlock=true returns 201 and blocks target
expected: alsoBlock=true returns 201 and blocks the reported user
result: pass
source: automated
coverage_id: 14-04-D3

## Summary

total: 14
passed: 14
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
