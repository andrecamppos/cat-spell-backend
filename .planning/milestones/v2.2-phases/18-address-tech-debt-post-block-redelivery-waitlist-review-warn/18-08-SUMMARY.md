---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 08
subsystem: api
tags: [waitlist, invite, email, timeout, thread-pool, logging, wr-05, wr-06, d-10]

requires:
  - phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
    provides: "18-05 TEST_ADMIN_TOKEN in WaitlistConvertIntegrationTest; 18-06 WaitlistService constructor (resend cooldown, maxTrackedKeys, pinned stored email)"
provides:
  - "InviteService.create persists with saveAndFlush (invite row flushed before any caller mails the code)"
  - "WaitlistService.convertToInvite: render outside the error mapping, bounded send on a private 2-thread executor, timeout app.waitlist.invite-send-timeout-ms (default 10000)"
  - "WaitlistInviteDeliveryException(message, cause: Throwable? = null), with the cause chained on every delivery failure"
  - "One fixed-text, address-free WARN per delivery failure branch (rejected, timed out, failed with exception type, interrupted, not accepted)"
  - "@PreDestroy WaitlistService.shutdownInviteSendExecutor()"
affects: [18-11 application.yml + docs/CONFIGURATION.md (app.waitlist.invite-send-timeout-ms, late-delivery residual)]

actuals:
  tokens: 4500
  tasks: 2
  commits: 0
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40

tech-stack:
  added: []
  patterns:
    - "External I/O inside a transaction is bounded with a private (non-bean) ThreadPoolExecutor + Future.get(timeout) + cancel(true)"
    - "Delivery failures: one WARN with fixed text and at most the exception simple name; cause chained into the domain exception"
    - "Logback ListAppender on the service logger to assert log hygiene in plain mockk unit tests"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/invite/InviteServiceTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/invite/service/InviteService.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt

key-decisions:
  - "Phase 18-08 (D-10, WR-05): the invite send runs on a private ThreadPoolExecutor field in WaitlistService (2 daemon threads, queue 10), never a Spring bean, because Spring Boot 4.0.6 backs off applicationTaskExecutor when any Executor bean exists and would move every @Async listener onto it (deviation from RESEARCH Pattern 4 step 6, decided at planning)"
  - "Phase 18-08 (D-10): the send stays synchronous inside the convert transaction, bounded by app.waitlist.invite-send-timeout-ms (default 10000, validated > 0); any failure rolls back INVITED and the invite and returns the existing retryable 502"
  - "Phase 18-08 (WR-06): every delivery failure throws WaitlistInviteDeliveryException with the cause chained (TimeoutException, RejectedExecutionException, InterruptedException, or the sender's exception unwrapped from ExecutionException) and logs one fixed-text WARN; errorDetail, recipient and code are never logged"
  - "Phase 18-08: a renderer exception propagates unchanged (500), not as a delivery failure"
  - "Phase 18-08 residual (T-18-29, accepted): a provider that ignores interrupts may still deliver a rolled-back code after a timeout; redeeming it gets the generic invite-required 403 and the operator retry sends a valid code; documented by 18-11"

patterns-established:
  - "Bound external calls in transactions at the call site with a private executor + timeout; never register Executor beans in this app"

requirements-completed: [WAIT-04]

coverage:
  - id: D1
    description: "A hung invite sender is cut off by the send timeout: POST /api/admin/waitlist/{id}/invite returns 502 WAITLIST_INVITE_DELIVERY_FAILED in under 5 s, the conversion rolls back (CONFIRMED, invited_at NULL, no invites), and a retry returns 201"
    requirement: WAIT-04
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#http convert with a hung sender times out with 502 and rolls the conversion back"
        status: pass
    human_judgment: false
  - id: D2
    description: "The invite row is flushed at creation (saveAndFlush, never plain save) and create(null) runs strictly before emailSender.send"
    requirement: WAIT-04
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/invite/InviteServiceTest.kt#create flushes the invite row once and stores only the hash of the returned code"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt#a successful convert creates the invite before sending and returns the raw code"
        status: pass
    human_judgment: false
  - id: D3
    description: "Delivery failures chain their cause (sender exception, TimeoutException), log exactly one WARN with no recipient, errorDetail or code, and renderer failures propagate unchanged without a send"
    requirement: WAIT-04
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt (5 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Whole waitlist and invite slices still pass with the bounded send and saveAndFlush"
    requirement: WAIT-04
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.catspell.api.waitlist.* --tests com.catspell.api.invite.* (25 classes, 129 tests, 0 failures)"
        status: pass
    human_judgment: false

duration: 21min
completed: 2026-10-04
status: complete
---

# Phase 18 Plan 08: Flush, bounded send, roll back (WR-05 / WR-06) Summary

**The waitlist invite conversion now flushes the invite row before sending, renders outside the error mapping, and awaits the send on a private 2-thread executor for at most `app.waitlist.invite-send-timeout-ms` (default 10000). Any failure still rolls the conversion back with the retryable 502, but now with a chained cause and one address-free WARN.**

## Performance

- **Duration:** 21 min
- **Started:** 2026-10-04T14:44:19Z
- **Completed:** 2026-10-04T15:05:25Z
- **Tasks:** 2 (Task 1 was a tracer)
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments

- `InviteService.create` uses `inviteRepository.saveAndFlush(...)`, so the INSERT reaches the database before any caller mails the code. This covers the waitlist conversion and `InviteAdminController`. The KDoc says so.
- `WaitlistService.convertToInvite`:
  - claim and `inviteService.create(null)` are unchanged;
  - `waitlistInviteEmailRenderer.render(...)` runs outside any catch, so a renderer bug is a 500, not a 502;
  - `sendBounded` submits a `Callable { emailSender.send(message) }` to a private `ThreadPoolExecutor` (2 core / 2 max daemon threads named `waitlist-invite-send-N`, 60 s keep-alive with `allowCoreThreadTimeOut(true)`, `LinkedBlockingQueue(10)`), then calls `future.get(inviteSendTimeoutMs, MILLISECONDS)`.
- The failure branches each log one fixed-text WARN and throw `WaitlistInviteDeliveryException(cause = ...)`:
  - rejected: "send rejected: send queue full";
  - timed out: `cancel(true)`, then "send timed out after {} ms";
  - failed: the `ExecutionException` is unwrapped, then "send failed: {}" with the simple class name;
  - interrupted: the interrupt flag is restored and `cancel(true)` is called;
  - a non-SUCCESS result: "was not accepted by the email provider". `errorDetail` is never read.
- `WaitlistInviteDeliveryException(message, cause: Throwable? = null)`. `GlobalExceptionHandler` is unchanged and still maps to 502 `WAITLIST_INVITE_DELIVERY_FAILED`.
- `@PreDestroy fun shutdownInviteSendExecutor()` calls `shutdownNow()`. No executor bean is registered, and a comment explains why.

## New configuration keys

| Key | Default | Binding | Notes |
|-----|---------|---------|-------|
| `app.waitlist.invite-send-timeout-ms` | `10000` | `@Value` on the WaitlistService constructor | Validated `> 0` at startup. 18-11 declares it in application.yml and docs. The integration test sets 1000. |

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
|------|--------------|
| Task 1 (RED) | src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt |
| Task 1 (GREEN) | src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt, src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt |
| Task 2 (RED) | src/test/kotlin/com/catspell/api/invite/InviteServiceTest.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt |
| Task 2 (GREEN) | src/main/kotlin/com/catspell/api/invite/service/InviteService.kt, src/test/kotlin/com/catspell/api/invite/InviteServiceTest.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt |
| Plan metadata | .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-08-SUMMARY.md, .planning/STATE.md, .planning/ROADMAP.md, .planning/REQUIREMENTS.md (whichever the state tools changed) |

## TDD Evidence

- **Task 1 RED:** The test was written before any production change, and the property `app.waitlist.invite-send-timeout-ms=1000` was added to the main class's `@TestPropertySource`.
  - Command: `./gradlew test -Pkotlin.compiler.execution.strategy=in-process --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"`. Exit 1: 17 tests, 1 failed.
  - Failure: `http convert with a hung sender times out with 502 and rolls the conversion back()`, "java.lang.AssertionError: Status expected:<502> but was:<201>". The test took 10.161 s: the old code waited out the full 10 s hang and returned 201.
  - `gsd-tools check tdd-red-evidence` on the JUnit XML returned `RED_EVIDENCE_OK` (target_test_failed).
- **Task 1 GREEN:** The same command passed 17/17 with 0 failures. The hung-sender test took 1.137 s. The captured log shows one WARN each for "send timed out after 1000 ms", "send failed: RuntimeException", and (twice) "not accepted by the email provider".
- **Task 1 tracer gate:** This was an interactive run in `end-of-phase` mode with an automated-only verify. I re-ran it with `--rerun`: 17/17, BUILD SUCCESSFUL. Tracer verified end-to-end, so expansion continued.
- **Task 2 RED:** Both unit test files were written before the InviteService change.
  - Command: `./gradlew test -Pkotlin.compiler.execution.strategy=in-process --tests "com.catspell.api.waitlist.WaitlistServiceConvertTest" --tests "com.catspell.api.invite.InviteServiceTest"`. Exit 1: 6 tests, 1 failed.
  - Failure: `create flushes the invite row once and stores only the hash of the returned code()`, "Verification failed: call 1 of 1: InviteRepository(#1).saveAndFlush(any())) was not called. Calls to same mock: 1) InviteRepository(#1).save(...)".
  - `check tdd-red-evidence` returned `RED_EVIDENCE_OK`.
  - WaitlistServiceConvertTest passed 5/5 at this point, because Task 1's GREEN already delivered the ordering, timeout, cause, renderer and log behavior. These are pinning tests over that behavior, not a fresh RED.
- **Task 2 GREEN:** After `saveAndFlush`, `./gradlew test ... --tests "com.catspell.api.waitlist.WaitlistServiceConvertTest" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"` was BUILD SUCCESSFUL. InviteServiceTest 1/1, WaitlistServiceConvertTest 5/5, WaitlistConvertIntegrationTest 17/17, and every invite integration class green: 11 classes, 49 tests, 0 failures.
- **REFACTOR:** None needed.

## Verification

- Task 1 acceptance:
  - `invite-send-timeout-ms:10000` appears 1 time.
  - `cause: Throwable? = null` appears 1 time.
  - `ThreadPoolTaskExecutor` does not appear in src/main.
  - `@Bean` appears 0 times in WaitlistService.
  - `errorDetail` appears 0 times.
  - `WaitlistInviteDeliveryException(cause` appears 4 times (at least 3 required).
- Task 2 acceptance:
  - `inviteRepository.save(` appears 0 times and `saveAndFlush` 1 time in InviteService.
  - WaitlistServiceConvertTest has 5 `@Test` methods and uses `verifyOrder`.
  - InviteServiceTest has `verify(exactly = 1) { inviteRepository.saveAndFlush(...) }`.
- Plan `<verification>`: `./gradlew test --tests "com.catspell.api.waitlist.*" --tests "com.catspell.api.invite.*"` exited 0 (BUILD SUCCESSFUL in 8m 34s): 25 classes, 129 tests, 0 failures, 0 errors.
- Prohibitions: no Executor/TaskExecutor bean was added (by grep). The send is still synchronous inside `@Transactional convertToInvite`, and the integration test proves the rollback.

## Files Created/Modified

- `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt`: private send executor, `@PreDestroy` shutdown, the `inviteSendTimeoutMs` constructor parameter, `log`, `sendBounded`, and the rewritten convert KDoc (flush, bounded send, rollback, residual)
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt`: `WaitlistInviteDeliveryException` gets a `cause` parameter, and the comment notes the chaining
- `src/main/kotlin/com/catspell/api/invite/service/InviteService.kt`: `saveAndFlush` plus a KDoc sentence
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt`: `invite-send-timeout-ms=1000` added to the main class's properties, plus the hung-sender test (the deny-by-default class is unchanged)
- `src/test/kotlin/com/catspell/api/invite/InviteServiceTest.kt` (new): saveAndFlush, hash and null-referrer test
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt` (new): 5 mockk tests with a Logback ListAppender

## Decisions Made

- **Executor is not a bean (deviation from RESEARCH Pattern 4 step 6, decided during planning).** RESEARCH proposed a `ThreadPoolTaskExecutor` bean. Spring Boot 4.0.6 auto-configures `applicationTaskExecutor` only when no `Executor` bean exists, so such a bean would move every `@Async` listener (push, email, report, WebSocket reconnect) onto the 2-thread invite pool. The pool is a private field instead, shut down with `@PreDestroy`.
- **Interrupted branch logs too.** The plan lists the WARN text for the rejected, timeout, failure and non-SUCCESS branches. The must-have says every failure branch logs one WARN, so the interrupted branch logs a fixed `Waitlist invite email send interrupted` as well.
- **Late-delivery residual (T-18-29, accepted).** `cancel(true)` interrupts the worker, but a provider that ignores interrupts may still deliver a code that was rolled back. Redeeming it fails with the generic invite-required 403, and the operator's retry sends a valid code. The row lock and pooled connection are held for at most the timeout. This is recorded in the convert KDoc, and 18-11 documents it.

## Deviations from Plan

None. The plan was executed as written. The non-bean executor was already the plan's own recorded deviation from RESEARCH, and the interrupted-branch WARN follows the plan's must-have.

## Issues Encountered

None. No Testcontainers flake or IDE build interference happened during this plan.

## Known Stubs

None.

## User Setup Required

None. The new key has a safe default (10000 ms).

## Next Phase Readiness

- Ready for 18-09. 18-11 must declare `app.waitlist.invite-send-timeout-ms` (default 10000) in application.yml and docs/CONFIGURATION.md, and document the late-delivery residual.

## Self-Check: PASSED

- (a) All 6 plan paths are in `git diff --cached --name-only`: WaitlistService.kt, Exceptions.kt, InviteService.kt, WaitlistConvertIntegrationTest.kt, InviteServiceTest.kt, WaitlistServiceConvertTest.kt (verified after staging; this SUMMARY is staged right after it is written).
- (b) `git rev-parse HEAD` is 605ddb4d49266e805436c16b18bfc78746c46e40.
- (c) `git diff --name-only` (unstaged) lists none of this plan's files.
- Both created test files exist on disk.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-04*
