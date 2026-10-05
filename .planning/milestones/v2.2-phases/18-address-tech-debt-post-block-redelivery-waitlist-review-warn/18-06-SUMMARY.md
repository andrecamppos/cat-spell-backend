---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 06
subsystem: api
tags: [waitlist, rate-limit, cooldown, bucket4j, caffeine, wr-03, wr-04, wr-10, wr-11]

requires:
  - phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
    provides: "18-02 RateLimitBuckets (bounded, access-expiring per-key Bucket4j store)"
  - phase: 17-waitlist-landing-page-api
    provides: "waitlist join / rotate / confirm / convert flow and its integration suites"
provides:
  - "Resend cooldown in the rotate UPDATE: a PENDING re-join inside app.waitlist.resend-cooldown-minutes (default 15) is a silent no-op"
  - "Delivery address pinned at first insert: rotatePendingToken never writes email; the confirmation event uses findStoredEmail"
  - "Per-email bucket default widened to 3 per 24 h (app.waitlist.per-email-refill-hours:24) and moved onto RateLimitBuckets"
  - "WaitlistEntryRepository.findStoredEmail(normalizedEmail): String?; findByNormalizedEmail removed (IN-11)"
affects: [18-11 application.yml WAITLIST_PER_EMAIL_REFILL_HOURS default + docs, 18-07 RateLimitFilter migration]

actuals:
  tokens: 6900
  tasks: 2
  commits: 0
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40

tech-stack:
  added: []
  patterns:
    - "Cooldown gates live in the conditional UPDATE's WHERE clause (evaluated under the row lock), not in a read-then-write check"
    - "Waitlist tests escape the 15-minute cooldown by backdating updated_at 16 minutes (backdate helper), not by a test-only cooldown of 0"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt
    - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntry.kt
    - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt

key-decisions:
  - "D-07 / WR-11: a PENDING re-join inside the resend cooldown (default 15 min) matches 0 rows in the rotate UPDATE and sends nothing; outside it, it rotates and mails once"
  - "Open Question 2: the per-email bucket check stays first, so a no-op re-join inside the cooldown still spends one token (simplest and strictest; externally still does nothing)"
  - "D-08 / WR-04: the email column is never written after insert; the confirmation event and the later invite use the stored first address"
  - "resend-cooldown-minutes is validated >= 0 at startup; `<=` in the cutoff makes 0 mean always rotate"
  - "application.yml's WAITLIST_PER_EMAIL_REFILL_HOURS default is changed by 18-11, not here; this plan changes only the @Value default"

patterns-established:
  - "backdate(normalizedEmail) helper (UPDATE updated_at - INTERVAL '16 minutes') in four waitlist test classes"

requirements-completed: [WAIT-01, WAIT-02, WAIT-03, WAIT-04]

coverage:
  - id: D1
    description: "A PENDING re-join inside the cooldown changes nothing (hash, updated_at, email) and returns the identical 202; no second email is sent and the first link stays live"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt#a PENDING re-join inside the cooldown changes nothing and returns the identical 202"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#a pending re-join inside the cooldown sends no second email"
        status: pass
    human_judgment: false
  - id: D2
    description: "Outside the cooldown a PENDING re-join rotates the token, sends one fresh email, and invalidates the first link"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#pending re-join outside the cooldown sends a second email with a fresh token and the stored hash matches the newest"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#pending re-join invalidates the first link and only the newest token confirms"
        status: pass
    human_judgment: false
  - id: D3
    description: "A re-join never changes the stored email; a +suffix/case variant's fresh link goes to the first stored address"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt#a PENDING re-join outside the cooldown rotates the token hash and keeps the first stored email"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#a variant re-join outside the cooldown mails the first stored address"
        status: pass
    human_judgment: false
  - id: D4
    description: "20 concurrent joins for one new address send exactly one confirmation email (held 500 ms) with one distinct token equal to the stored hash"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt#20 concurrent joins for one new email send exactly one confirmation email"
        status: pass
    human_judgment: false
  - id: D5
    description: "The per-email bucket (RateLimitBuckets, default 3 per 24 h) still caps once the cooldown is escaped; variants share one bucket; other addresses unaffected"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt (3 tests)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Whole waitlist slice (incl. convert / invite, which reads entry.email) passes with the new semantics"
    requirement: WAIT-04
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.catspell.api.waitlist.* (15 classes, 94 tests, 0 failures)"
        status: pass
    human_judgment: false

duration: 22min
completed: 2026-10-04
status: complete
---

# Phase 18 Plan 06: Waitlist resend cooldown, pinned address, 3-per-24h bucket Summary

**The waitlist rotate UPDATE now carries a 15-minute resend cooldown evaluated under the row lock, never writes the email column, and the confirmation event goes to the address stored at first insert. The per-email bucket is widened to 3 per 24 h and moved onto the shared bounded `RateLimitBuckets`. Together these close WR-03 (double-submit), WR-04 (`+suffix` hijack), WR-10 (this call site) and WR-11 (mail bombing).**

## Performance

- **Duration:** 22 min
- **Started:** 2026-10-04T13:31:31Z
- **Completed:** 2026-10-04T13:53:20Z
- **Tasks:** 2 (Task 1 was a tracer)
- **Files modified:** 8

## Accomplishments
- `rotatePendingToken(normalizedEmail, hash, expiresAt, now, resendCutoff, pending)`: SET clause is only hash, expiry and updatedAt. WHERE adds `AND (e.confirmTokenHash IS NULL OR e.updatedAt <= :resendCutoff)`, so the first join always rotates, a re-join inside the cooldown matches 0 rows, and racing joins rotate at most once.
- `findStoredEmail(normalizedEmail): String?` (scalar JPQL) replaces the unused `findByNormalizedEmail` (IN-11).
- `WaitlistService.join` keeps the bucket check first, passes `resendCutoff = now - cooldown`, and on `rotated == 1` publishes `WaitlistConfirmationRequestedEvent(storedEmail, rawToken)`. New `@Value` params: `app.waitlist.resend-cooldown-minutes:15` (validated `>= 0`) and `rate-limit.max-tracked-keys:100000`. The refill default is now 24 h. The `ConcurrentHashMap` is gone.
- KDoc truth: `WaitlistEntry.email` is documented as pinned at first insert. `WaitlistEmailNormalizer` now says `+suffix` stripping and lowercasing can merge distinct mailboxes, and that the pinned address is why this is safe. Normalization behavior is unchanged.
- Tests: 3 new tests (join inside cooldown, confirm inside cooldown, variant mails the first address). 5 existing tests were updated so their joins land outside the cooldown via `backdate`.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
|------|--------------|
| Task 1 (RED) | src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt |
| Task 1 (GREEN) | src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt, src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt |
| Task 2 | src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt, src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntry.kt, src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt |
| Plan metadata | .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-06-SUMMARY.md, .planning/STATE.md, .planning/ROADMAP.md, .planning/REQUIREMENTS.md (whichever the state tools changed) |

## TDD Evidence

- **Task 1 RED:** The test changes were written before any production change.
  - Command: `./gradlew test --tests "com.catspell.api.waitlist.WaitlistJoinIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest"`. Exit 1: 25 tests, 4 failed, each on its own assertion.
  - `a pending re-join inside the cooldown sends no second email`: "a re-join inside the resend cooldown must not send a second email ==> expected: <1> but was: <2>" (the planned RED observation).
  - `a variant re-join outside the cooldown mails the first stored address`: "expected: <pin-first@example.com> but was: <Pin-First+x@example.com>".
  - `a PENDING re-join inside the cooldown changes nothing ...`: "hash must be unchanged inside the cooldown ==> expected: <2caa3e...> but was: <f2b12a...>".
  - `a PENDING re-join outside the cooldown ... keeps the first stored email`: "expected: <pending-rejoin@example.com> but was: <Pending-Rejoin+landing@Example.com>".
  - The 2 updated tests that only add `backdate` passed against the old code, as expected.
  - `gsd-tools check tdd-red-evidence` on the Surefire XML of both classes returned `RED_EVIDENCE_OK` (target_test_failed).
  - The first RED attempt hit the known qemu PostGIS readiness flake (25/25 `ExceptionInInitializerError` / `ContainerLaunchException`). I restarted the Podman machine and re-ran; the re-run above is the RED of record.
- **Task 1 GREEN:** After the repository and service changes, the same command passed: Join 12/12 and Confirm 13/13, 0 failures. The tracer gate re-ran it with `--rerun` and got the same result.
- **Task 2:** Task 1's GREEN already delivered the behavior these suites cover, so no new failing test can be written first. As evidence instead, the unmodified suites were run against the new code (`--tests WaitlistPerEmailConcurrencyIntegrationTest --tests WaitlistPerEmailLimitIntegrationTest`, exit 1, 3 of 4 failed). The concurrency test failed with "expected: <3> but was: <1>". The limit tests failed with "join 2 (within capacity) must rotate the confirm token" and "f@ has its own, untouched bucket", both because a second join now lands inside the cooldown. After rewriting the tests, `./gradlew test --tests "com.catspell.api.waitlist.*"` passed: 15 classes, 94 tests, 0 failures.
- **REFACTOR:** None needed.

## Verification

- Task 1 `<verify>`: passed (Join 12, Confirm 13, 0 failures). Tracer feedback gate: the run is interactive in `end-of-phase` mode and the verify is automated only, so I re-ran it and it passed: "Tracer verified end-to-end — expanding".
- Task 1 acceptance:
  - `e.email = :email` appears 0 times.
  - `resendCutoff` appears 3 times in the repository.
  - There is no `findByNormalizedEmail` in src/main.
  - `resend-cooldown-minutes:15` appears 1 time, `per-email-refill-hours:24` 1 time, and `ConcurrentHashMap` 0 times in WaitlistService.
  - `git diff --quiet HEAD -- V24__create_waitlist_entries.sql` exits 0.
- Task 2 acceptance:
  - `never merged` appears 0 times and `overwritten on a PENDING re-join` 0 times.
  - The concurrency test asserts `assertEquals(1, sentMessages.size` inside `await().during(Duration.ofMillis(500))`.
  - The waitlist slice passes.
- Plan `<verification>`: `./gradlew test --tests "com.catspell.api.waitlist.*"` passes. V24 is unchanged, and there is no `findByNormalizedEmail` in src/main.
- Prohibition (no logging of email/token): WaitlistService and WaitlistEntryRepository have no logger calls.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt`: cooldown-gated rotate UPDATE that never writes email, plus `findStoredEmail`; `findByNormalizedEmail` removed
- `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt`: cooldown cutoff, event sent to the stored address, `RateLimitBuckets` per-email store, 24 h refill default
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntry.kt`: email column comment says the address is pinned at first insert
- `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt`: KDoc admits possible mailbox merging and points to the pinned address
- `src/test/kotlin/com/catspell/api/waitlist/{WaitlistJoin,WaitlistConfirm,WaitlistPerEmailConcurrency,WaitlistPerEmailLimit}IntegrationTest.kt`: `backdate` helper, new cooldown and pinned-address tests, updated expectations

## Decisions Made
- Open Question 2: the bucket check stays first, so a no-op re-join inside the cooldown still spends a per-email token. This is accepted as T-18-20: mail always goes to the pinned owner, at most 3 per 24 h.
- D-07 refines Phase 17 D-04: outside the cooldown, a PENDING re-join still rotates and mails.
- application.yml's `WAITLIST_PER_EMAIL_REFILL_HOURS` default is changed by 18-11. Only the `@Value` default changed here. The test yml still sets `per-email-refill-hours: 1`, which these tests don't depend on.

## Deviations from Plan

None. The plan was executed as written.

## Issues Encountered
- **Known Testcontainers flake:** The first Task 1 RED run failed every test with `ContainerLaunchException` for `postgis/postgis:16-3.4-alpine`. I restarted the Podman machine and re-ran. No Testcontainers or image configuration was changed.
- **IDE build interference:** There were two spurious failures, both because the shared `build/` directory was being rebuilt concurrently. One was a `compileTestKotlin FAILED` where a standalone `compileTestKotlin` was clean. The other was a `FileNotFoundException` for `PasswordResetToken.class` in an unrelated file. Each was fixed by re-running once, with no code changes.

## Known Stubs

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness
- 18-11 must change application.yml's `WAITLIST_PER_EMAIL_REFILL_HOURS` default to 24. It should also declare and document `app.waitlist.resend-cooldown-minutes` (default 15) and `rate-limit.max-tracked-keys`.
- After this plan, the only `ConcurrentHashMap<String, Bucket>` left in src/main is in RateLimitFilter (18-07).

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-04*

## Self-Check: PASSED

- (a) All 11 paths this plan changed appear in `git diff --cached --name-only`: the 8 code/test files, this SUMMARY, `.planning/STATE.md` and `.planning/ROADMAP.md`. REQUIREMENTS.md was not changed, because `requirements.ready-ids` reported 0/4 ready while sibling plans still declare WAIT-01..04.
- (b) `git rev-parse HEAD` = 605ddb4d49266e805436c16b18bfc78746c46e40 (unchanged; no commits).
- (c) `git diff --name-only` (unstaged) is empty.
- STATE.md archived milestone sections are unchanged: v1.0 still reads "All phases complete", and v1.1 / v2.0 / v2.1 still carry their "✅ Milestone complete (shipped ...)" lines.
