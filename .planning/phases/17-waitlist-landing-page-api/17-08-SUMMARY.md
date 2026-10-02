---
phase: 17-waitlist-landing-page-api
plan: 08
subsystem: testing
tags: [waitlist, postgres, information_schema, bucket4j, concurrency, testcontainers, mockk, awaitility]

# Dependency graph
requires:
  - phase: 17-waitlist-landing-page-api (17-01)
    provides: V24 waitlist_entries schema and WaitlistService.join with the shared per-email Bucket4j bucket map
  - phase: 17-waitlist-landing-page-api (17-06)
    provides: WaitlistMigrationTest on its private waitlist_migration_test database
provides:
  - Exact-column-set assertion on waitlist_entries (D-05 email-only prohibition enforced in CI)
  - 20-thread per-email mint-cap proof for one new address at the real configured capacity (3)
affects: [17-VERIFICATION re-verify, waitlist schema changes, WaitlistService per-email throttle]

# Actuals (#2632): chars/4 over the realized diff
actuals:
  tokens: 1500
  tasks: 2
  commits: 0
plan_head_before: 6ae4f42581082686024784d210267d5ca2c05edc
plan_head_after: 6ae4f42581082686024784d210267d5ca2c05edc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Schema prohibitions enforced by an exact literal column set read from information_schema.columns, not derived from the entity or migration"
    - "Concurrency cap proofs hold the count with Awaitility during(...) so a late extra send cannot pass a momentary match"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt
  modified:
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt

key-decisions:
  - "The concurrency test awaits with during(500ms) inside the 5s window, so the email count must stay at exactly 3, not just reach 3 once"
  - "The concurrency test defines its own nested MockEmailConfig (its own cached context) and uses no @TestPropertySource, so the real per-email-capacity of 3 is the one exercised"

patterns-established:
  - "Exact column-set assertion: queryForList(column_name FROM information_schema.columns) compared against a literal setOf with assertEquals so both sets show on failure"

requirements-completed: [WAIT-03]

coverage:
  - id: D1
    description: "waitlist_entries has exactly the ten D-05 email-only columns; any added or removed column fails the build"
    requirement: "WAIT-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt#waitlist_entries has exactly the ten expected columns and no others"
        status: pass
    human_judgment: false
  - id: D2
    description: "20 concurrent WaitlistService.join calls for one new address mint exactly per-email-capacity (3) confirm emails, leave one row, and the stored hash is the SHA-256 of one sent token"
    requirement: "WAIT-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt#20 concurrent joins for one new email mint at most per-email-capacity confirm tokens"
        status: pass
    human_judgment: false

# Metrics
duration: 5min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 08: Waitlist Schema and Per-Email Concurrency Test Gaps Summary

**Two tests close the last 17-VERIFICATION items. The first checks that waitlist_entries has exactly the ten email-only columns, read from information_schema. The second runs 20 threads against one new address and proves the shared Bucket4j bucket allows exactly 3 confirm tokens.**

## Performance

- **Duration:** about 5 min
- **Started:** 2026-10-02T12:00:23Z
- **Completed:** 2026-10-02T12:05:08Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- The D-05 "email-only storage" prohibition is now enforced by a test (17-VERIFICATION gap 2). `WaitlistMigrationTest` compares the live `information_schema.columns` set for `waitlist_entries` against a literal ten-element `setOf(...)`. An added IP, user-agent, referrer or profile column now fails the build, and so does a dropped column.
- The 17-01 backstop truth is now `verification: explicit`. In the test, 20 threads race `WaitlistService.join("concurrency-cap@example.com")`. The test passes only if, for 500ms inside a 5s window, exactly 3 confirmation emails exist. It also checks that exactly one row exists and that the stored `confirm_token_hash` is the SHA-256 of one of the 3 distinct sent tokens.
- No production code changed. Both items were test-tier gaps in code that was already correct.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `6ae4f42`.

| Task | Paths staged |
|------|--------------|
| 1: Assert the exact waitlist_entries column set | `src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt` |
| 2: Prove the per-email bucket caps concurrent mints | `src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt` |
| Plan metadata | this SUMMARY, `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md` |

## Files Created/Modified
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt`: adds one test, `waitlist_entries has exactly the ten expected columns and no others`. It sits next to the other schema-shape tests. The class now has 8 tests, all passing.
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt`: new file with one test. It extends `BaseIntegrationTest` and uses a nested MockK `@Primary` `EmailSender` that captures into a `CopyOnWriteArrayList`. The harness is `Executors.newFixedThreadPool(20)` with a `CountDownLatch` start gate and `Future.get(30s)`. The token regex and SHA-256 helpers are copied from `WaitlistConfirmIntegrationTest`.

## Verification Results
- `./gradlew compileTestKotlin -q`: exit 0.
- `./gradlew test --tests WaitlistMigrationTest`: 8 tests, 0 failures.
- `./gradlew test --tests WaitlistPerEmailConcurrencyIntegrationTest`: passed on 4 consecutive runs in total. Runs 2 to 4 were forced with `--rerun`, and 2 of them came after the final edit. The combined run of both classes also passed (8 + 1 tests, 0 failures).
- The full `./gradlew test` suite was deliberately not run here. The orchestrator runs it once after this wave.

## Decisions Made
- `during(500ms)` was added to the Awaitility assertion. Plain `untilAsserted { size == 3 }` could pass on a momentary 3 even if a 4th email arrived later. Holding the count at 3 for a window proves "never more than 3".
- The pool size is the literal `newFixedThreadPool(20)`, not a named constant, so the plan's artifact grep (`contains: "newFixedThreadPool(20)"`) matches.

## Deviations from Plan

None. The plan was executed as written. The `during(500ms)` hold is a stricter form of the planned 5-second Awaitility assertion, not a change of scope.

**Total deviations:** 0
**Impact on plan:** None.

## Issues Encountered
- No mutation check was run (temporarily breaking `emailBucket(...).tryConsume` to show the test fails). That would have meant editing production code that is staged but not committed, so it was skipped. Two things make the test sensitive to the cap anyway. It needs exactly 3 emails held for 500ms, and 20 threads with no bucket would produce up to 20.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness
- Both remaining 17-VERIFICATION items (gap 2 D-05 column set, and the 17-01 per-email concurrency backstop) now have enforcing tests. Phase 17 has all 8 plans summarized and is ready for the orchestrator's full-suite run and re-verification.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*

## Self-Check: PASSED
