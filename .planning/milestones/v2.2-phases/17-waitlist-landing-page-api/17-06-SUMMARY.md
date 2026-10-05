---
phase: 17-waitlist-landing-page-api
plan: 06
subsystem: testing
tags: [waitlist, flyway, postgres, on-conflict, concurrency, testcontainers, mockmvc]

# Dependency graph
requires:
  - phase: 17-waitlist-landing-page-api
    provides: "17-01 V24 waitlist_entries schema, WaitlistService.join (native ON CONFLICT upsert + PENDING-only rotate), POST /api/waitlist with WAITLIST_JOIN_MESSAGE"
provides:
  - "WaitlistMigrationTest: V24 proven under Flyway + ddl-auto=validate on the private waitlist_migration_test database"
  - "WaitlistJoinIntegrationTest contract cases: 400-and-no-row validation, PENDING/CONFIRMED/INVITED re-join behind a byte-identical 202, 8-thread dedupe, 168h TTL bracket"
affects: [17-04, 17-05, phase-17-verification]

# Actuals (#2632) — chars/4 over the realized diff (new migration test + lines appended to the join test)
actuals:
  tokens: 3770
  tasks: 2
  commits: 0
plan_head_before: 6ae4f42581082686024784d210267d5ca2c05edc
plan_head_after: 6ae4f42581082686024784d210267d5ca2c05edc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Private Flyway migration database per migration test class (distinct DB name avoids DROP DATABASE races between suites)"
    - "Terminal-state re-join no-op proven by pushing updated_at into the past before the re-join"
    - "Concurrency proof: fixed pool + CountDownLatch start gate + Future.get() calling the service bean directly"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt
  modified:
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt

key-decisions:
  - "Missing-email-field body asserts title 'Bad Request' (unreadable-body handler); the four bean-validation cases assert 'Validation Error'"
  - "CONFIRMED/INVITED re-join tests set updated_at to NOW() - 1 day first, so any write by the re-join would show up"

patterns-established:
  - "Each migration test class owns a uniquely named private database on the shared Testcontainers Postgres"

requirements-completed: [WAIT-01, WAIT-02]

coverage:
  - id: D1
    description: "V24 applies on an empty DB under Flyway + validate; column nullability matches the DDL; the three named constraints exist; duplicate normalized_email, duplicate non-null hash and status BOGUS are rejected; PENDING/created_at defaults; multiple NULL hashes allowed"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt (7 tests)"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt (5 tests, run alongside)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Empty, whitespace-only, malformed and 256-char emails return 400 'Validation Error'; a body with no email field returns 400 'Bad Request'; none writes a row"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt (5 validation tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "PENDING re-join rotates the hash and overwrites email with the latest trimmed submission; CONFIRMED and INVITED re-joins leave hash, email, status and updated_at unchanged; every re-join response matches the first join's status and body byte for byte"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt#a PENDING re-join rotates the token hash and overwrites email with the latest trimmed submission"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt#a CONFIRMED re-join changes nothing and returns the identical 202"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt#an INVITED re-join changes nothing and returns the identical 202"
        status: pass
    human_judgment: false
  - id: D4
    description: "8 concurrent WaitlistService.join calls for one new email complete without an exception and leave exactly one row"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt#8 concurrent joins for the same new email complete without error and leave exactly one row"
        status: pass
    human_judgment: false
  - id: D5
    description: "confirm_token_expires_at lies within [before+168h-1ms, after+168h+1ms]; a 167h mutation of the expected TTL makes the test fail"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt#confirm_token_expires_at is the join instant plus 168 hours"
        status: pass
    human_judgment: false

# Metrics
duration: 17min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 06: Waitlist Join Contract Tests Summary

**V24 schema constraints proven on a private Flyway database, plus integration tests for the join contract: 400 with no row written for bad input, PENDING-only token rotation behind a byte-identical 202, exactly one row after 8 concurrent joins, and a 168-hour confirm-token expiry checked within 1 ms.**

Changes staged, not committed (user's no-auto-commit rule).

## Performance

- **Duration:** 17 min
- **Started:** 2026-10-02T09:42:42Z
- **Completed:** 2026-10-02T09:59:28Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- `WaitlistMigrationTest` (7 tests) replays V1..V24 into its own `waitlist_migration_test` database under `ddl-auto=validate`. It checks the nullability of all 9 columns and that the three named constraints exist. It also checks that the UNIQUE and CHECK constraints reject bad inserts, the PENDING and `created_at` defaults, and that multiple NULL hashes are allowed.
- `WaitlistJoinIntegrationTest` grew from 1 to 11 tests. The 17-01 tracer test is unchanged. The new tests cover 5 validation cases, 3 re-join state cases, 1 concurrency case and 1 TTL case.
- No defects were found in the 17-01, 17-02 or 17-03 production code. No production file was touched.

## Task Outcomes (staged, not committed)

| Task | Files staged | Verification |
|------|--------------|--------------|
| 1. Prove the V24 schema on a private Flyway database | `src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt` | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistMigrationTest" --tests "com.catspell.api.invite.InviteMigrationTest"`: PASS (7 + 5 tests). Acceptance: the DB name is present, the class does not extend BaseIntegrationTest, and all constraint, default and NULL-hash checks are present and pass. PASS. |
| 2. Prove the join contract: validation, re-join states, concurrency, TTL | `src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt` | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest"`: PASS (11 tests, tracer included). Acceptance: `Executors.newFixedThreadPool(8)` + `CountDownLatch` are used, and all 3 re-join tests compare status and body byte for byte with the first join. PASS. |

Plan-level verification:
- `./gradlew compileTestKotlin -q`: exit 0.
- The combined run of the three classes: BUILD SUCCESSFUL (7 + 11 + 5 tests).
- Full suite `./gradlew test`: exit 0, 61 classes, 380 tests, 0 failures, 0 errors, 1 skipped (the existing `FcmSmokeTest`). It took 12m 37s.

## TDD Notes
- These are characterization tests for behavior 17-01 already built, so every assertion passed on its first run. The TDD "unexpected GREEN" here is expected: the plan proves existing code and adds none. `workflow.tdd_mode=false`, so the RED/GREEN commit gate does not apply.
- To show the assertions actually detect errors, the expected TTL was temporarily changed to 167h. The TTL test then FAILED. The change was reverted to 168h and the test is green again.
- REFACTOR: none needed.

## Files Created/Modified
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistMigrationTest.kt`: V24 schema proof on a private database (does not extend BaseIntegrationTest).
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt`: adds the validation, re-join, concurrency and TTL tests, and autowires `WaitlistService`.

## Decisions Made
- The missing-field body (`{}`) is checked for title `Bad Request`. The Kotlin non-null `email` constructor parameter makes Jackson fail, and `handleHttpMessageNotReadable` handles that failure. The other four invalid inputs are checked for `Validation Error`.
- The terminal-state re-join tests set `updated_at` to one day in the past before re-joining with an upper-cased variant of the address. A matching `updated_at` afterwards therefore proves nothing was written to the row.
- Added one extra schema check the plan did not list: a duplicate non-null `confirm_token_hash` is rejected. It directly exercises `uq_waitlist_entries_confirm_token_hash`, the D-08 single-use key.

## Deviations from Plan

None. The plan was executed as written. The extra duplicate-hash migration test only strengthens Task 1's existing scope, and no production code changed.

## Issues Encountered
- None. The full suite takes about 12.5 minutes, so it ran in the background.

## Threat Surface
- T-17-29 is mitigated. The migration test drops and creates only `waitlist_migration_test`, which is neither `catspell` nor `invite_migration_test`. InviteMigrationTest passes alongside it.

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- The join contract that WAIT-01 and WAIT-02 depend on is now covered by regression tests. Plans 17-04 and 17-05 can build on it.
- REQUIREMENTS.md: WAIT-01 is ready to mark complete. WAIT-02 is still held by the shared-ID gate until the sibling plan that also declares it has a SUMMARY.
- The changes are staged but not committed, for the user to review and commit.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*

## Self-Check: PASSED
- Both test files and this SUMMARY exist on disk and appear in `git diff --cached --name-only` with no unstaged changes. Staging was checked in place of commits, per the no-auto-commit override. HEAD is still 6ae4f42, so no commits were made.
