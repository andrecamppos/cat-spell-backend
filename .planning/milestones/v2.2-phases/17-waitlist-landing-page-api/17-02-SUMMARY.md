---
phase: 17-waitlist-landing-page-api
plan: 02
subsystem: api
tags: [waitlist, double-opt-in, spring-events, async, transactional-event-listener, jpql, sha-256, redirect]

# Dependency graph
requires:
  - phase: 17-waitlist-landing-page-api
    provides: "17-01 join slice: WaitlistService.join (rotated result), WaitlistEntryRepository, WaitlistController, SecurityConfig permitAll for GET /api/waitlist/confirm, app.waitlist.* config"
  - phase: 14-report-a-user
    provides: "@Async @TransactionalEventListener(AFTER_COMMIT) swallow-log listener pattern (ReportNotificationListener)"
provides:
  - WaitlistConfirmationRequestedEvent (primitives only, redacted toString)
  - WaitlistEmailListener (async AFTER_COMMIT confirmation send, swallow-log, no token/address in logs)
  - WaitlistConfirmEmailRenderer (single-use link app.waitlist.confirm-url?token=..., scanner-burn copy)
  - WaitlistEntryRepository.claimConfirm single-use conditional UPDATE
  - WaitlistService.confirm(rawToken) and the publish-on-rotate in join
  - GET /api/waitlist/confirm -> 302 to configured success/error URL with no-referrer + no-store
affects: [17-03, 17-04, 17-05, 17-06]

# Actuals (#2632) — chars/4 over the realized (staged) diff
actuals:
  tokens: 5100
  tasks: 2
  commits: 0
plan_head_before: 6ae4f42581082686024784d210267d5ca2c05edc
plan_head_after: 6ae4f42581082686024784d210267d5ca2c05edc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Publish a domain event inside the @Transactional join only when the conditional UPDATE returned 1; @Async AFTER_COMMIT listener does the mail I/O"
    - "Event data class overrides toString to redact secrets"
    - "Single-use token claim as one JPQL conditional UPDATE (hash + status + strict expiry) returning Int"
    - "Redirect endpoint whose Location is built only from config values (open-redirect guard)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/waitlist/event/WaitlistEvents.kt
    - src/main/kotlin/com/catspell/api/waitlist/event/WaitlistEmailListener.kt
    - src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt
    - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt

key-decisions:
  - "D-08 honored literally: any 0-row confirm claim (including a token a mail scanner already spent) redirects to the error URL; the hash stays on the row so an 'already confirmed -> success' variant needs no schema change later"
  - "Confirmation event is published only when rotatePendingToken returned 1, so CONFIRMED/INVITED re-joins send no mail (D-04)"
  - "GET /api/waitlist/confirm carries @SecurityRequirements like the public join endpoint, so OpenAPI shows no JWT requirement"

patterns-established:
  - "Waitlist mail: event -> @Async AFTER_COMMIT listener -> renderer -> EmailSender, logging fixed text only"
  - "Confirm link outcome: claim row count picks one of two configured URLs; never 400/JSON on the link route"

requirements-completed: [WAIT-02]

coverage:
  - id: D1
    description: "A join that creates or rotates a PENDING entry sends exactly one confirmation email after commit to the trimmed address; its text body carries app.waitlist.confirm-url?token=<raw> and sha256(raw) equals confirm_token_hash"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#first join sends exactly one confirmation email whose token hashes to the stored hash"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#pending re-join sends a second email with a fresh token and the stored hash matches the newest"
        status: pass
    human_judgment: false
  - id: D2
    description: "Re-joins for CONFIRMED or INVITED entries send no email (D-04 silent no-op, caps third-party mail bombing)"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#re-join of a CONFIRMED entry sends no email"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#re-join of an INVITED entry sends no email"
        status: pass
    human_judgment: false
  - id: D3
    description: "WaitlistConfirmationRequestedEvent.toString redacts the token and the email; the listener logs neither"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#confirmation event toString redacts the token and the email"
        status: pass
      - kind: other
        ref: "! grep -Eq 'log\\.[a-z]+\\(.*event\\.(rawToken|email)' src/main/kotlin/com/catspell/api/waitlist/event/WaitlistEmailListener.kt"
        status: pass
    human_judgment: false
  - id: D4
    description: "GET /api/waitlist/confirm with a valid token returns 302 to the configured success URL with Referrer-Policy no-referrer and Cache-Control no-store, and sets CONFIRMED + confirmed_at"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#valid token confirms the entry and redirects to the success URL with no-referrer and no-store"
        status: pass
    human_judgment: false
  - id: D5
    description: "Reused, unknown, blank, missing, expired-by-1s and rotated-away tokens all 302 to the configured error URL and change no row; concurrent confirms with one token yield exactly one success"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#reused token redirects to the error URL and leaves confirmed_at unchanged"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#unknown, blank and missing tokens redirect to the error URL and change no row"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#token expired by one second redirects to the error URL while an unexpired token succeeds"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#pending re-join invalidates the first link and only the newest token confirms"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt#eight concurrent confirms of one token yield exactly one success"
        status: pass
    human_judgment: false
  - id: D6
    description: "Confirmation email copy (wording, scanner-burn reassurance line) reads correctly in a real mail client"
    requirement: WAIT-02
    verification: []
    human_judgment: true
    rationale: "Copy quality and mail-client rendering are not asserted by any test; the landing-page error copy lives in the separate web repo (manual-only check in 17-VALIDATION.md)"

# Metrics
duration: 8min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 02: Waitlist Double Opt-In Summary

**A join that creates or rotates a PENDING entry now emails a single-use `app.waitlist.confirm-url?token=...` link through an `@Async` AFTER_COMMIT listener (redacted event, no secrets in logs). `GET /api/waitlist/confirm` claims the SHA-256-hashed token in one conditional UPDATE (`status = PENDING AND expiresAt > now`) and 302-redirects to the configured web success or error URL with `no-referrer` and `no-store`.**

Changes staged, not committed (user's no-auto-commit rule).

## Performance

- **Duration:** 8 min
- **Started:** 2026-10-02T09:08:09Z
- **Completed:** 2026-10-02T09:16:30Z
- **Tasks:** 2
- **Files modified:** 7 (4 created, 3 modified)

## Accomplishments
- `WaitlistService.join` publishes `WaitlistConfirmationRequestedEvent(trimmed, rawToken)` only when `rotatePendingToken` returned 1. It is still `@Transactional`, so the AFTER_COMMIT listener fires only after the new hash is durable.
- `WaitlistEmailListener` sends the mail off-thread and swallow-logs failures using fixed text plus the exception class name. It never logs the token, the address, or `e.message`.
- `WaitlistConfirmEmailRenderer` builds the html and text bodies. They say the link works once and expires in 7 days, include the scanner-burn reassurance line, and include an ignore-if-not-you line.
- `WaitlistEntryRepository.claimConfirm` is the single-use claim. It rejects unknown, expired (strict `>`), reused and rotated-away tokens. The hash is kept on the row.
- `GET /api/waitlist/confirm` returns 302 only, never 400 or JSON. The Location is taken only from `app.waitlist.confirm-success-url` / `confirm-error-url`.

## Task Outcomes (staged, not committed)

| Task | Files staged | Verification |
|------|--------------|--------------|
| 1. Joining sends one single-use confirmation link after commit (tdd=true) | WaitlistEvents.kt, WaitlistEmailListener.kt, WaitlistConfirmEmailRenderer.kt, WaitlistService.kt, WaitlistConfirmIntegrationTest.kt | RED: 5 of 5 failed on their intended assertions (email count, toString). GREEN: `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest"` PASS (5 tests). All 5 acceptance criteria PASS. |
| 2. GET /api/waitlist/confirm single-use claim + 302 (tdd=true) | WaitlistEntryRepository.kt, WaitlistService.kt, WaitlistController.kt, WaitlistConfirmIntegrationTest.kt | RED: 6 new tests failed on status/Location/count assertions against a `confirm = false` stub with no endpoint. The 5 Task 1 tests stayed green. GREEN: `./gradlew test --tests "...WaitlistConfirmIntegrationTest" --tests "...WaitlistJoinIntegrationTest"` PASS (11 + 1 tests). All 4 acceptance criteria PASS. |

Plan-level verification:
- `./gradlew compileKotlin -q` exits 0.
- `./gradlew test --tests "com.catspell.api.waitlist.*"` exits 0: 23 tests (Confirm 11, Normalizer 8, Join 1, PerEmailLimit 3), 0 failures.
- The post-wave step (`waitlist.* + invite.* + common.*`, then the full `./gradlew test`) is scoped by the plan to run after wave 2 merges with 17-03. It is left to the orchestrator's post-wave verification.

## TDD Notes
- `workflow.tdd_mode=false`, so the RED/GREEN commit gate does not apply. RED and GREEN were run as real test executions, and staging replaced commits.
- Task 1 RED used a plain `data class` event (no redaction, no publish). Task 2 RED used a `confirm(): Boolean = false` stub and no GET handler (404). Both REDs failed on the planned behavior assertions, not on compile or load errors.
- REFACTOR: none needed.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/waitlist/event/WaitlistEvents.kt`: confirmation event with redacted `toString`.
- `src/main/kotlin/com/catspell/api/waitlist/event/WaitlistEmailListener.kt`: `@Async` AFTER_COMMIT send, swallow-log.
- `src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt`: confirmation email. `confirm-url` has a default.
- `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt`: event publisher injected, publish-on-rotate, `confirm(rawToken)`.
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt`: `claimConfirm`.
- `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt`: `GET /confirm` returning 302, with success/error URL `@Value`s.
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt`: 11 tests covering email-after-commit, the no-email cases, redaction, and the confirm outcomes, including the 1-second expiry boundary and the 8-thread race.

## Decisions Made
- D-08 is applied literally: an already-spent token goes to the error URL. Research Open Question 1 (mail scanners) is mitigated only by the email copy, as the plan states.
- `@SecurityRequirements` on the confirm endpoint (see deviation 1).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical] `@SecurityRequirements` on the public confirm endpoint**
- **Found during:** Task 2
- **Issue:** Public endpoints in this codebase carry `@SecurityRequirements` so OpenAPI does not advertise a JWT requirement. 17-01 added it to the join for the same reason. The plan's confirm spec did not include it.
- **Fix:** Added `@SecurityRequirements` to `WaitlistController.confirm`.
- **Files modified:** src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt
- **Verification:** Compiles. All 11 confirm tests pass.
- **Staged in:** Task 2 staging

---

**Total deviations:** 1 auto-fixed (1 missing critical, documentation consistency)
**Impact on plan:** None on behavior. No scope creep.

## Known Stubs
None. The 17-01 stub (unused `rotated`, no confirmation email) is resolved by this plan. Broken-windows entry #1 is marked fixed in `.planning/WINDOWS.md`.

## Issues Encountered
None.

## User Setup Required
None. `WAITLIST_CONFIRM_URL`, `WAITLIST_CONFIRM_SUCCESS_URL` and `WAITLIST_CONFIRM_ERROR_URL` already exist from 17-01 and have local defaults. Production needs real web URLs, which is a deploy-time config concern.

## Next Phase Readiness
- Ready for 17-03 (per-IP throttle/CORS), 17-04 (admin list of CONFIRMED entries), 17-05 (convert CONFIRMED to invite) and 17-06 (contract tests).
- Pending: the post-wave full-suite run after 17-03 is applied.
- Changes are staged but not committed. The user needs to review and commit.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*

## Self-Check: PASSED
- All 7 plan files plus this SUMMARY exist on disk and show in `git diff --cached --name-only` with a clean working-tree diff. Staging was checked in place of commits, per the no-auto-commit override. HEAD is still 6ae4f42, so no commits were made.
- All acceptance criteria were re-run and PASS. Plan-level `compileKotlin` and `waitlist.*` tests PASS.
- WAIT-02 was not marked complete in REQUIREMENTS.md. The shared-ID gate (#2388) holds it until sibling plans 17-05 and 17-06, which also declare it, finish.
