---
phase: 14-report-a-user
plan: 03
subsystem: api
tags: [spring-events, async, transactional-event-listener, email, mockk, awaitility, testcontainers]

requires:
  - phase: 14-report-a-user (plan 01)
    provides: ReportCreatedEvent, app.report.operator-email config key
  - phase: 14-report-a-user (plan 02)
    provides: ReportService.report(...) which publishes ReportCreatedEvent in-tx
  - phase: 08-push-delivery-foundation
    provides: EmailSender/EmailMessage seam + @Async @TransactionalEventListener(AFTER_COMMIT) pattern
provides:
  - ReportEmailRenderer.render(operatorEmail, event) -> EmailMessage
  - ReportNotificationListener (@Async @TransactionalEventListener AFTER_COMMIT, swallow+log)
  - Out-of-band operator notification proven (notified after commit; report survives email failure)
affects: [14-04 controller]

tech-stack:
  added: []
  patterns:
    - "Async AFTER_COMMIT operator email decoupled from the report write (persist-then-notify)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/email/service/ReportEmailRenderer.kt
    - src/main/kotlin/com/catspell/api/moderation/event/ReportNotificationListener.kt
    - src/test/kotlin/com/catspell/api/moderation/ReportNotificationIntegrationTest.kt
  modified:
    - src/test/resources/application.yml

key-decisions:
  - "Listener is @Async + @TransactionalEventListener(AFTER_COMMIT) so the operator email fires only after the report commits and off the request thread."
  - "Send is wrapped in try/catch (log.warn) so a mail outage never propagates or rolls back the persisted report (D-02, MOD-07)."
  - "Email addressed solely to app.report.operator-email; reported user is never a recipient (D-11)."
  - "Renderer builds the body from event fields only (IDs + strings) — no JPA entity deref across the async boundary (Pitfall 2)."

patterns-established:
  - "Renderer @Component owns email body assembly; listener owns dispatch + failure swallowing."

requirements-completed: [MOD-07]

coverage:
  - id: D1
    description: "ReportEmailRenderer renders an operator-addressed email containing reporter/reported/category/details/timestamp"
    requirement: "MOD-07"
    verification:
      - kind: integration
        ref: "ReportNotificationIntegrationTest#operator is emailed once after the report commits (asserts to == operatorEmail)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Async AFTER_COMMIT listener emails the operator exactly once after the report commits"
    requirement: "MOD-07"
    verification:
      - kind: integration
        ref: "ReportNotificationIntegrationTest#operator is emailed once after the report commits"
        status: pass
    human_judgment: false
  - id: D3
    description: "A failing email send neither propagates to the caller nor rolls back the persisted report"
    requirement: "MOD-07"
    verification:
      - kind: integration
        ref: "ReportNotificationIntegrationTest#report survives an email-send failure"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-09-25
status: complete
---

# Phase 14 Plan 03: Out-of-band Operator Notification Summary

**@Async AFTER_COMMIT ReportNotificationListener emails the operator via a dedicated ReportEmailRenderer, swallowing send failures so the report always survives — proven with MockK + Awaitility under Testcontainers.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 2
- **Files modified:** 4 (3 created, 1 modified)

## Accomplishments
- `ReportEmailRenderer` (@Component) builds an operator-addressed `EmailMessage` (subject + html/text bodies) from `ReportCreatedEvent` fields only.
- `ReportNotificationListener` (@Async @TransactionalEventListener AFTER_COMMIT) sends the email and swallow-logs any exception (log.warn) so the report is never lost/delayed/rolled back.
- `ReportNotificationIntegrationTest` proves: operator emailed exactly once after commit (to == operator email, never the reported user); and the report row survives a thrown `EmailSender.send`.

## Task Commits

1. **Task 1: ReportEmailRenderer + ReportNotificationListener** - `6e82dcc` (feat)
2. **Task 2: ReportNotificationIntegrationTest (+ test config fix)** - `ef016b4` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/email/service/ReportEmailRenderer.kt` - operator email body
- `src/main/kotlin/com/catspell/api/moderation/event/ReportNotificationListener.kt` - async AFTER_COMMIT dispatch
- `src/test/kotlin/com/catspell/api/moderation/ReportNotificationIntegrationTest.kt` - notification test
- `src/test/resources/application.yml` - added app.report.operator-email (test config parity)

## Decisions Made
- Followed the plan; the only added change was a test-config key (see Deviations).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added `app.report.operator-email` to the test-classpath application.yml**
- **Found during:** Task 2 (ReportNotificationIntegrationTest)
- **Issue:** `src/test/resources/application.yml` fully shadows the main config and lacked `app.report.operator-email`, so the listener's `@Value("${app.report.operator-email}")` (no default, per plan) failed context load with a PlaceholderResolutionException in tests. (The 14-02 service test passed earlier only because the listener did not yet exist.)
- **Fix:** Added `app.report.operator-email: ops@catspell.example` under the existing `app:` block in the test yml, mirroring the sibling `app.reset-password-url` / `verify-email-url` / `confirm-email-change-url` keys already present there.
- **Files modified:** src/test/resources/application.yml
- **Verification:** `./gradlew test --tests "*ReportNotificationIntegrationTest*"` and `--tests "*Report*"` both BUILD SUCCESSFUL.
- **Committed in:** `ef016b4` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for the test context to load; production config already carried the key (14-01). No scope creep.

## Issues Encountered
- PlaceholderResolutionException in tests, root-caused to the shadowing test application.yml (resolved via the deviation above).

## Next Phase Readiness
- The full report pipeline (persist → optional block → out-of-band operator email) is complete and tested; 14-04 can expose it over HTTP.

---
*Phase: 14-report-a-user*
*Completed: 2026-09-25*
