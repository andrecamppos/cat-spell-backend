---
phase: 17-waitlist-landing-page-api
plan: 05
subsystem: api
tags: [waitlist, invite, admin, transactional, conditional-update, email, problem-detail, mockk, testcontainers]

# Dependency graph
requires:
  - phase: 16-invite-only-access-referral
    provides: "InviteService.create(referrerUserId: UUID?) — hashed single-use invite codes, raw code returned once"
  - phase: 17-waitlist-landing-page-api
    provides: "17-01 WaitlistEntry/WaitlistStatus/V24 + app.waitlist.invite-url key; 17-02 CONFIRMED state via claimConfirm; 17-04 AdminTokenGuard, WaitlistAdminController, SecurityConfig permitAll /api/admin/waitlist/**"
provides:
  - "WaitlistEntryRepository.markInvited(id, now, confirmed, invited): Int — single-winner CONFIRMED→INVITED claim"
  - "WaitlistService.convertToInvite(entryId): String — claim + organic invite + synchronous email in one transaction, full rollback on delivery failure"
  - "WaitlistInviteEmailRenderer.render(recipientEmail, rawCode) — code + catspell://register?code= link"
  - "WaitlistEntryNotConvertibleException → 409 WAITLIST_ENTRY_NOT_CONVERTIBLE; WaitlistInviteDeliveryException → 502 WAITLIST_INVITE_DELIVERY_FAILED"
  - "POST /api/admin/waitlist/{id}/invite → 201 ConvertWaitlistResponse(entryId, code), guard before lookup"
affects: [phase-17-verification, gsd-secure-phase, gsd-verify-work]

# Actuals (#2632) — chars/4 over the realized diff (2 new files + edits to 6 existing files)
actuals:
  tokens: 6300
  tasks: 2
  commits: 0
plan_head_before: 6ae4f42581082686024784d210267d5ca2c05edc
plan_head_after: 6ae4f42581082686024784d210267d5ca2c05edc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Claim-then-side-effect inside one @Transactional: conditional JPQL UPDATE as the single-winner gate, joined REQUIRED child transaction, synchronous external call whose failure is mapped to a domain exception so everything rolls back"
    - "Entity fields read before a non-clearAutomatically bulk UPDATE; status never re-read afterwards"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt
    - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt
    - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistDtos.kt

key-decisions:
  - "Convert returns the raw invite code in the 201 (mirrors POST /api/admin/invites) so the operator can deliver it by hand while LoggingEmailSender is the only sender"
  - "A failed invite delivery (ERROR status or thrown exception) rolls back claim + invite and maps to a dedicated 502 WAITLIST_INVITE_DELIVERY_FAILED; the entry stays CONFIRMED and retryable"
  - "No idempotent re-send: converting an INVITED entry is a 409 like PENDING, never a second invite"

patterns-established:
  - "Operator mutation endpoint: adminTokenGuard.require(token) first, then a service call whose state claim is a conditional UPDATE returning 0/1"

requirements-completed: [WAIT-04, WAIT-02]

coverage:
  - id: D1
    description: "Converting a CONFIRMED entry issues exactly one organic invite (code_hash = sha256(code), referrer_user_id NULL, no referrals row), marks the entry INVITED with invited_at, and emails the code + catspell://register?code=<code> to the stored address"
    requirement: "WAIT-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#converting a confirmed entry issues one organic invite and emails the code"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#http convert of a confirmed entry returns 201 with the entry id and a code stored hashed"
        status: pass
    human_judgment: false
  - id: D2
    description: "Only CONFIRMED (double opt-in completed) entries convert: PENDING and INVITED are rejected (409 WAITLIST_ENTRY_NOT_CONVERTIBLE) with no invite and no email; unknown id is 404"
    requirement: "WAIT-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#converting a pending entry is rejected with no invite and no email"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#converting an already invited entry is rejected with no invite and no email"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#http convert of a pending entry returns 409 not convertible"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#http second convert of the same entry returns 409 with the same body"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#http convert of an unknown id returns 404"
        status: pass
    human_judgment: false
  - id: D3
    description: "Delivery failure (ERROR or thrown) → 502 WAITLIST_INVITE_DELIVERY_FAILED with full rollback (no invite row, entry CONFIRMED, invited_at NULL); a later retry succeeds"
    requirement: "WAIT-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#sender error rolls the conversion back and a later retry succeeds"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#sender exception rolls the conversion back"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#http convert with a failing sender returns 502 and leaves the entry confirmed"
        status: pass
    human_judgment: false
  - id: D4
    description: "4 concurrent converts of one CONFIRMED entry → exactly one success, three WaitlistEntryNotConvertibleException, one invite row, one email"
    requirement: "WAIT-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#concurrent converts of one confirmed entry produce exactly one invite"
        status: pass
    human_judgment: false
  - id: D5
    description: "Missing/wrong X-Admin-Token → 401 identically for existing and random ids with no row change, invite or email; blank configured token → 401 for every request"
    requirement: "WAIT-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt (4 http 401 cases)"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt#WaitlistConvertDenyByDefaultIntegrationTest (2 cases)"
        status: pass
    human_judgment: false

# Metrics
duration: 15min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 05: Waitlist Entry to Invite Conversion Summary

**`POST /api/admin/waitlist/{id}/invite` turns one CONFIRMED waitlist entry into exactly one organic invite in a single transaction. A conditional `CONFIRMED→INVITED` UPDATE picks one winner, then `InviteService.create(null)` issues the invite and a synchronous email delivers the code and its `catspell://register?code=` link. If delivery fails, the request returns 502 and the whole conversion rolls back.**

Changes staged, not committed (user's no-auto-commit rule).

## Performance

- **Duration:** 15 min
- **Started:** 2026-10-02T10:22:29Z
- **Completed:** 2026-10-02T10:37:51Z
- **Tasks:** 2
- **Files modified:** 8 (2 created, 6 modified)

## Accomplishments
- `WaitlistService.convertToInvite` loads the entry (404 if unknown), then claims it with `markInvited`. That is one conditional JPQL UPDATE whose `status = :confirmed` guard is checked under the row lock, and a 0-row result throws the 409 exception. It then issues an organic invite (`referrer_user_id` NULL, no referrals row) and sends the invite email synchronously.
- If delivery fails, whether `EmailSender.send` returns ERROR or throws, it raises `WaitlistInviteDeliveryException`. The claim and the invite then roll back together, so the entry stays CONFIRMED with `invited_at` NULL and a retry succeeds.
- `WaitlistInviteEmailRenderer` shows the code as plain text plus the link `app.waitlist.invite-url` + `?code=` (default `catspell://register`).
- `GlobalExceptionHandler` maps the new exceptions to 409 `WAITLIST_ENTRY_NOT_CONVERTIBLE` (Conflict) and 502 `WAITLIST_INVITE_DELIVERY_FAILED` (Bad Gateway). No existing handler changed.
- `WaitlistAdminController.convert` calls `adminTokenGuard.require(token)` as its first statement, so an existing id and a random id get the same 401 without the right token.

## Staged Files (per task)

| Task | Files staged | Verification |
|------|--------------|--------------|
| 1: Convert a confirmed entry atomically | `waitlist/model/WaitlistEntryRepository.kt`, `waitlist/service/WaitlistService.kt`, `common/exception/Exceptions.kt`, `email/service/WaitlistInviteEmailRenderer.kt` (new), `test/.../waitlist/WaitlistConvertIntegrationTest.kt` (new) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"`: 7/7 pass; acceptance criteria 1-5 PASS |
| 2: POST /api/admin/waitlist/{id}/invite | `common/exception/GlobalExceptionHandler.kt`, `waitlist/controller/WaitlistAdminController.kt`, `waitlist/model/WaitlistDtos.kt`, `test/.../waitlist/WaitlistConvertIntegrationTest.kt` (extended) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistConvert*" --tests "com.catspell.api.waitlist.WaitlistAdmin*"`: 31/31 pass (16 + 2 + 11 + 2); `./gradlew test --tests "com.catspell.api.waitlist.*" --tests "com.catspell.api.invite.*" --tests "com.catspell.api.common.*"`: 24 classes, 132 tests, 0 failures; acceptance criteria 1-4 PASS |

**Plan-level verification:** `./gradlew compileKotlin -q` exit 0. The waitlist + invite + common slice is green (132 tests). I did not run the full `./gradlew test` phase gate (~12 min) in this plan. It remains the gate before `/gsd-verify-work`.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt`: `markInvited` conditional UPDATE (1 = claimed, 0 = PENDING / INVITED / lost race)
- `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt`: injects `InviteService`, `EmailSender`, `WaitlistInviteEmailRenderer`; adds `convertToInvite`
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt`: `WaitlistEntryNotConvertibleException`, `WaitlistInviteDeliveryException` (both extend `RuntimeException` directly)
- `src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt`: invite email (subject "Your Cat Spell invite is here")
- `src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt`: 409 and 502 ProblemDetail handlers
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistDtos.kt`: `ConvertWaitlistResponse(entryId, code)`
- `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt`: `@PostMapping("/{id}/invite")` → 201
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt`: `WaitlistConvertIntegrationTest` (7 service + 9 HTTP cases) and `WaitlistConvertDenyByDefaultIntegrationTest` (2 cases)

## Decisions Made
- The raw code is returned in the 201 body. Today the only sender is `LoggingEmailSender`, which reports SUCCESS without delivering, so the operator has to be able to deliver the code by hand. This mirrors `POST /api/admin/invites`.
- Delivery failure returns a dedicated 502 with a full rollback instead of leaving an orphan invite (T-17-28).
- The recipient email is read from the entity loaded before the bulk UPDATE, and the status is never re-read afterward (the UPDATE is not `clearAutomatically`).
- The deny-by-default class sets `app.invite.admin-token=` explicitly, as 17-04 does, so an exported `INVITE_ADMIN_TOKEN` cannot cause a false failure.

## TDD Notes
`workflow.tdd_mode` is false, so the RED gate was not enforced. I still followed RED → GREEN, staging instead of committing:
- **Task 1 RED:** The test file and the two exception classes were written first, with a `convertToInvite` stub that threw `NotImplementedError`. All 7 target tests failed on behavior: the expected exception or result never arrived. GREEN: 7/7.
- **Task 2 RED:** The HTTP tests were added before the route existed. 10 of the 11 new tests failed on status assertions (got 404). `http convert of an unknown id returns 404` passed early because a missing route also yields 404 "Not Found"; it now exercises the real `ResourceNotFoundException` path. GREEN: 18/18 in the file.
- No REFACTOR step was needed.

## Deviations from Plan

None. The plan was executed as written.

## Issues Encountered
- My first attempt to clear `build/test-results` before the slice run was denied by the sandbox. I instead counted only the report files written after a marker timestamp. The tests were unaffected.

## User Setup Required
None. `WAITLIST_INVITE_URL` was already defined in 17-01 (default `catspell://register`), and `INVITE_ADMIN_TOKEN` is the existing operator secret.

## Next Phase Readiness
- Every plan in Phase 17 now has a SUMMARY. Next, run the full `./gradlew test` phase gate, then `/gsd-verify-work 17`. The WAIT-04 "unclassified" probe row in the plan's `<assumptions>` stays open for the verifier.
- No blockers.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*

## Self-Check: PASSED

- All 8 key files and this SUMMARY exist on disk and are staged with no unstaged residue (verified via `git diff --cached --name-only` + `git diff --quiet`).
- No commits expected (stage-only rule); HEAD unchanged at 6ae4f42.
- Task acceptance criteria re-run: all PASS. Slice: 132 tests, 0 failures. No stub patterns in the touched files.
