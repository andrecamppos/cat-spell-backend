---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 12
subsystem: chat/match (reconnect redelivery, match reactivation)
tags: [kotlin, spring-data-jpa, jpql, hibernate, websocket, stomp, testcontainers, mockk]
gap_closure: true
gap_ids: [G-18-1]

requires:
  - phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
    provides: "plan 18-01 reconnect-path suppression in ChatService.deliverUnreadMessages (D-04/D-05) and the BlockEnforcementIntegrationTest broker-capture harness"
provides:
  - "MessageRepository.markAllDeliveredForMatch(matchId): one set-based @Modifying UPDATE marking a match's undelivered messages delivered"
  - "MatchService.createMatch reactivation branch sweeps undelivered messages before MatchCreatedEvent (G-18-1)"
  - "No-reconnect-between rematch integration proofs for the block and unmatch paths, each with a post-rematch delivery control"
  - "Unit proof that the sweep runs only on reactivation and before publish"
  - "Corrected deliverUnreadMessages KDoc (two suppression points) and the dated D-05 amendment"
affects: [chat, match, moderation, push, verify-work G-18-1]

actuals:
  tokens: 4300      # chars/4 over the realized staged diff (17023 chars)
  tasks: 3
  commits: 0        # MEASURED: stage-only run per the user's no-auto-commit rule; HEAD unchanged
plan_head_before: add8a2f1bf5a5ea3d693310c9bd5492308b7c74c
plan_head_after: add8a2f1bf5a5ea3d693310c9bd5492308b7c74c

tech-stack:
  added: []
  patterns:
    - "Bulk JPQL UPDATE with an implicit path join (m.conversation.match.id) under a bare @Modifying, which is accepted by Hibernate 7"
    - "Shared integration-test scenario helper taking a hide-then-restore lambda, so block and unmatch get identical assertions"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt
    - src/main/kotlin/com/catspell/api/match/service/MatchService.kt
    - src/main/kotlin/com/catspell/api/chat/service/ChatService.kt
    - src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt
    - .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-CONTEXT.md

key-decisions:
  - "G-18-1: MatchService.createMatch's reactivation branch runs MessageRepository.markAllDeliveredForMatch once, after save and before MatchCreatedEvent, so pre-block/pre-unmatch messages cannot resurface as previews when no reconnect happened while hidden (D-05 amended; endMatch unchanged)"
  - "The JPQL path form (m.conversation.match.id) shipped; the IN-subquery fallback was not needed"
  - "@Modifying stays bare (no clearAutomatically): clearing the swipe transaction's persistence context would detach the reactivated Match before its endedAt = null change flushes"

patterns-established:
  - "Rematch sweep: a state transition that makes hidden content visible again must settle pending delivery state in the same transaction, before the visibility event is published"

requirements-completed: [MOD-02, MOD-03]

coverage:
  - id: D1
    description: "Block, unblock and rematch with no reconnect in between: every pre-block message (both directions) is delivered at rematch time, and neither participant's first reconnect pushes a stale preview"
    requirement: MOD-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#pre-block messages do not resurface when the pair rematches before the recipient reconnects"
        status: pass
    human_judgment: false
  - id: D2
    description: "Unmatch and rematch with no reconnect in between: the same guarantee on the unmatch hide path"
    requirement: MOD-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#pre-unmatch messages do not resurface when the pair rematches before the recipient reconnects"
        status: pass
    human_judgment: false
  - id: D3
    description: "No over-suppression: a message sent after the rematch is pushed live once, starts undelivered, and is pushed once more on the next reconnect (returns 1)"
    requirement: MOD-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#pre-block messages do not resurface when the pair rematches before the recipient reconnects (post-rematch control)"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#pre-unmatch messages do not resurface when the pair rematches before the recipient reconnects (post-rematch control)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The sweep is one set-based UPDATE that runs only on the reactivation branch and before MatchCreatedEvent, never for a new or still-active match"
    requirement: MOD-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt#createMatch reactivating an ended match marks its undelivered messages delivered before publishing"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt#createMatch publishes MatchCreatedEvent exactly once on a genuinely new match"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt#createMatch does not publish when the match already exists"
        status: pass
    human_judgment: false
  - id: D5
    description: "No regression across the moderation, match, chat and push-trigger tests"
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.catspell.api.moderation.* --tests com.catspell.api.match.* --tests com.catspell.api.chat.* --tests com.catspell.api.push.PushTriggerIntegrationTest (81 tests, 0 failures)"
        status: pass
    human_judgment: false
  - id: D6
    description: "deliverUnreadMessages KDoc names both suppression points (code byte-identical), and 18-CONTEXT.md D-05 carries the dated G-18-1 amendment"
    verification:
      - kind: other
        ref: "git diff -U0 HEAD -- ChatService.kt: 0 non-KDoc changed lines; grep 'Amended 2026-10-05 (UAT G-18-1, plan 18-12)' 18-CONTEXT.md = 1"
        status: pass
    human_judgment: true
    rationale: "Whether the documentation prose reads accurately is a reviewer judgment. The automated checks only prove scope (KDoc-only diff, amendment present)."

duration: 18min
completed: 2026-10-05
status: complete
---

# Phase 18 Plan 12: Rematch Redelivery Sweep (G-18-1) Summary

**Reactivating an ended match now runs one JPQL bulk UPDATE, `MessageRepository.markAllDeliveredForMatch`, before `MatchCreatedEvent` is published. Messages from before a block or unmatch can no longer come back as `/queue/notifications` previews after a rematch, whether or not anyone reconnected in between. Messages sent after the rematch still deliver normally.**

## Performance

- **Duration:** about 18 min
- **Started:** 2026-10-05T09:58:59Z
- **Completed:** 2026-10-05T10:16:35Z
- **Tasks:** 3/3
- **Files modified:** 6 (plus this SUMMARY, STATE.md and ROADMAP.md)

## Accomplishments

- Closed G-18-1 (18-REVIEW WR-01). The reactivation branch of `MatchService.createMatch` now sweeps the match's conversation in the same `@Transactional` swipe transaction, after `matchRepository.save(existing)` and before `eventPublisher.publishEvent(MatchCreatedEvent(...))`.
- Added integration Tests H (block → unblock → rematch) and I (unmatch → rematch). Neither reconnects between the hide and the rematch. They share the helper `assertNoStalePreviewWhenRematchPrecedesReconnect`, which also runs the post-rematch delivery control.
- Added a unit test for the sweep-before-publish order, plus zero-call checks on the new-match and still-active-match paths.
- Rewrote the `deliverUnreadMessages` KDoc to describe both suppression points (code unchanged). Added the dated D-05 amendment to 18-CONTEXT.md.

## Staged files (no commits; HEAD is still `add8a2f1bf5a5ea3d693310c9bd5492308b7c74c`)

| Task | Explicit paths staged |
| ---- | --------------------- |
| 1 (RED) | `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt` |
| 1 (GREEN) | `src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt`, `src/main/kotlin/com/catspell/api/match/service/MatchService.kt`, `src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt`, `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt` |
| 2 | `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt`, `src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt` |
| 3 | `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt`, `.planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-CONTEXT.md` |
| Metadata | `.planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-12-SUMMARY.md`, `.planning/STATE.md`, `.planning/ROADMAP.md` (plus `.planning/REQUIREMENTS.md` if the requirements step changed it) |

The commit steps were replaced by explicit-path `git add`, per the user's standing no-auto-commit rule (`stage_only_override`). The measured commit count is 0 by design.

## TDD evidence

- **RED (Task 1):** Test H was added before any main-code change. The verify command (`./gradlew test --tests "…BlockEnforcementIntegrationTest" --tests "…MatchServiceTest"`) exited 1, with 17 tests run and 1 failed. The failing test was `pre-block messages do not resurface when the pair rematches before the recipient reconnects`, with `AssertionFailedError: no stale preview for B when the rematch comes before the reconnect ==> expected: <0> but was: <2>`. The 2 are the two pre-block A→B messages pushed as stale previews. `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` (`target_test_failed`).
- **GREEN (Task 1):** After the repository method, the service call and the MatchServiceTest constructor fix, the same command exited 0. BlockEnforcementIntegrationTest ran 13/13 and MatchServiceTest 4/4. The tracer gate (end-of-phase mode, automated-only verify) passed on this run, so expansion went ahead.
- **Task 2:** Test I and the new unit test passed on their first run (14/14, 5/5), as expected, since they exercise code Task 1 already shipped.
- **REFACTOR:** none needed.

## JPQL form shipped

The **path form** shipped: `UPDATE Message m SET m.delivered = true WHERE m.conversation.match.id = :matchId AND m.delivered = false`, under a bare `@Modifying`. Hibernate 7 accepted it at context startup and at runtime, so the IN-subquery fallback was not used.

## Mutation check (Task 2)

I temporarily deleted the `messageRepository.markAllDeliveredForMatch(existing.id!!)` line and ran the verify command. The first attempt failed in `:compileKotlin` with "Daemon compilation failed": the Kotlin incremental daemon reported every import as unresolved, which was an infrastructure glitch with the file itself intact. The re-run gave 19 tests and **3 failed**, exactly the expected set:
- `pre-block messages do not resurface when the pair rematches before the recipient reconnects`: expected <0> but was <2> notifications to B
- `pre-unmatch messages do not resurface when the pair rematches before the recipient reconnects`: expected <0> but was <2> notifications to B
- `createMatch reactivating an ended match marks its undelivered messages delivered before publishing`: MockK `verifyOrder` reported "fewer calls happened than demanded"

Tests A–G, including D and E (which reconnect between teardown and rematch), stayed green. I then restored the line. `git diff --quiet -- MatchService.kt` (working tree vs index) exited 0, and the final restored run was green (14/14, 5/5).

## Regression slice (Task 3)

`./gradlew test --tests "com.catspell.api.moderation.*" --tests "com.catspell.api.match.*" --tests "com.catspell.api.chat.*" --tests "com.catspell.api.push.PushTriggerIntegrationTest"` gave BUILD SUCCESSFUL, **81 tests, 0 failures, 0 errors**:

| Suite | Tests |
| ----- | ----- |
| ChatIntegrationTest | 9 |
| ConversationListIntegrationTest | 10 |
| MatchIntegrationTest | 8 |
| MatchServiceTest | 5 |
| BlockEndpointIntegrationTest | 6 |
| BlockEnforcementIntegrationTest | 14 |
| BlockServiceIntegrationTest | 7 |
| ReportEndpointIntegrationTest | 9 |
| ReportNotificationIntegrationTest | 2 |
| ReportServiceIntegrationTest | 7 |
| PushTriggerIntegrationTest | 4 |

The known ChatIntegrationTest cursor-pagination flake **did not occur** on this run.

## Acceptance criteria

- Task 1: `fun markAllDeliveredForMatch` = 1; bare `@Modifying` = 1; `UPDATE Message m SET m.delivered = true WHERE` = 1; the call in MatchService = 1; non-comment `messageRepository.` uses in MatchService = 1; `endMatch` body diff against HEAD exits 0; BlockService.kt and ChatService.kt unchanged at Task 1 (exit 0); `@Test` count = 13. All pass.
- Task 2: `@Test` count = 14 in BlockEnforcementIntegrationTest and 5 in MatchServiceTest; "rematches before the recipient reconnects" = 2; `verifyOrder` = 2 (import plus use); restore-vs-index diff exits 0. All pass.
- Task 3: `markAllDeliveredForMatch` = 1 and `G-18-1` = 1 in ChatService.kt; non-KDoc changed lines in ChatService.kt = 0 (git exit 0); original D-05 sentence = 1; amendment line = 1; `markAllDeliveredForMatch` in 18-CONTEXT.md = 1; BlockService.kt unchanged (exit 0). All pass.

## Files Created/Modified

- `src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt`: new `markAllDeliveredForMatch` bulk UPDATE with KDoc, plus imports for `Modifying`, `Query` and `Param`
- `src/main/kotlin/com/catspell/api/match/service/MatchService.kt`: new `messageRepository` constructor dependency (after `swipeRepository`); one sweep call in the reactivation branch; one comment extended
- `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt`: `deliverUnreadMessages` KDoc rewritten (only KDoc lines changed)
- `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt`: shared scenario helper, Test H and Test I
- `src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt`: `messageRepository` mock in the constructor, the reactivation sweep/order test, and zero-call checks in the two named tests
- `.planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-CONTEXT.md`: dated D-05 amendment line, with the original sentence kept

## Decisions Made

- Shipped the JPQL path form, because Hibernate 7 accepted it.
- Kept `@Modifying` bare, to avoid detaching the reactivated Match before its flush (as the plan required).
- Used one parameterized helper for Tests H and I, so the block and unmatch proofs cannot drift apart.

## Deviations from Plan

None. The plan was executed as written, with commits replaced by explicit-path staging per `stage_only_override`, and the HEAD invariant read as `add8a2f` in place of the stale `9a8ea56`.

## Issues Encountered

- During the mutation check, one Kotlin compile-daemon failure (`Daemon compilation failed`, with every import unresolved) was an infrastructure glitch. The re-run compiled cleanly and produced the expected mutation failures.

## Threat Flags

None. No new network endpoints, auth paths or schema changes. The new write runs on the existing authenticated swipe path, as T-18-41/T-18-42 already cover.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- G-18-1 is ready for `/gsd-verify-work` to close. Phase 18 has no remaining plans.
- Nothing blocks. Everything is staged, not committed; the user reviews and commits.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-05*

## Self-Check: PASSED
