---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 01
subsystem: chat
tags: [kotlin, spring-messaging, stomp, jpql, hibernate, moderation, testcontainers]

# Dependency graph
requires:
  - phase: 13-blocking-unmatch
    provides: "blocks table, bidirectional block predicate, ended-match soft state, match reactivation on rematch"
provides:
  - "ConversationRepository.findHiddenConversationIdsForUser(userId): one set-based query for the caller's ended or blocked conversations"
  - "ChatService.deliverUnreadMessages skips hidden conversations, marks their messages delivered, and returns the pushed count"
  - "Broker-channel capture test harness in BlockEnforcementIntegrationTest (brokerChannel ChannelInterceptor, notificationsTo, delivered)"
affects: [chat, moderation, websocket-reconnect, 18-verification]

# Actuals (#2632): chars/4 over the realized staged diff
actuals:
  tokens: 3804
  tasks: 2
  commits: 0
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Assert 'no push' at the broker: a ChannelInterceptor on the brokerChannel bean records every /user/<id>/queue/notifications destination"
    - "Reconnect suppression: partition undelivered messages against one hidden-conversation id set"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt
    - src/main/kotlin/com/catspell/api/chat/service/ChatService.kt
    - src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt

key-decisions:
  - "W1 hidden-conversation query stays JPQL (JOIN c.match m + EXISTS over Block in either direction); Hibernate 7 accepted it, so no native-SQL fallback was needed"
  - "deliverUnreadMessages now returns the pushed (visible) count rather than the total undelivered count; its only caller ignores the value"
  - "deliverUnreadMessages returns 0 before the hidden-set query when there is nothing undelivered, so the common reconnect stays at two queries"

patterns-established:
  - "Broker capture: inject @Qualifier(\"brokerChannel\") AbstractSubscribableChannel, add the interceptor in @BeforeEach and remove it in @AfterEach, clear the capture after setup sends"

requirements-completed: [MOD-02, MOD-03]

coverage:
  - id: D1
    description: "A message left undelivered before a block is not pushed on the recipient's reconnect and ends delivered = true; its row is kept"
    requirement: MOD-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#reconnect does not push a message left undelivered before a block and marks it delivered"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#reconnect does not push when the recipient blocked the sender"
        status: pass
    human_judgment: false
  - id: D2
    description: "A visible third-party conversation in the same reconnect is still pushed exactly once, and the pushed count is returned"
    requirement: MOD-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#reconnect still pushes a visible conversation while suppressing a blocked one"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests \"com.catspell.api.chat.*\" (ChatIntegrationTest 9, ConversationListIntegrationTest 10)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Unmatch, rematch after unmatch, and rematch after unblock never resurface the suppressed message"
    requirement: MOD-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#reconnect does not push a message left undelivered before an unmatch"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#message suppressed after an unmatch does not resurface after a rematch"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#message suppressed after a block does not resurface after unblock and rematch"
        status: pass
    human_judgment: false
  - id: D4
    description: "A block row alone, with the match still active, suppresses the push (the EXISTS branch of the query is live on its own)"
    requirement: MOD-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#a block row alone with the match still active suppresses the reconnect push"
        status: pass
    human_judgment: false

# Metrics
duration: 43min
completed: 2026-10-03
status: complete
---

# Phase 18 Plan 01: Reconnect Redelivery Skips Hidden Conversations Summary

**A WebSocket reconnect no longer pushes previews from blocked or unmatched conversations. One JPQL query (`findHiddenConversationIdsForUser`: ended match, or a block in either direction) splits undelivered messages into suppressed ones, which are marked delivered and never pushed, and visible ones, which are pushed. The behavior is proven by capturing every send on the STOMP broker channel.**

## Performance

- **Duration:** 43 min
- **Started:** 2026-10-03T20:05:08Z
- **Completed:** 2026-10-03T20:48:41Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Closed audit item W1 (MOD-02, MOD-03). After a block (either direction) or an unmatch, the recipient's reconnect sends nothing to `/user/<id>/queue/notifications` for the hidden conversation, and the message ends `delivered = true`. The row is kept as evidence (Phase 13 D-04/D-07).
- A suppressed message can't resurface after a rematch, whether after an unmatch or after unblock plus a mutual like. The same match row is reactivated, and the second reconnect pushes nothing.
- Hidden conversations are resolved in one set-based query per reconnect (D-05). `MatchService`, `BlockService`, `sendMessage` and `WebSocketSessionListener` are unchanged.
- The tests capture at the broker, so no STOMP client timing is involved: 7 new integration tests (A-G), plus the 5 existing ones still green.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
|------|--------------|
| Task 1 (RED) | `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt` |
| Task 1 (GREEN) | `src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt`, `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt`, `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt` |
| Task 2 | `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt` |
| Plan metadata | `.planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-01-SUMMARY.md`, `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md` (each staged if it changed) |

## TDD Evidence

**RED (Task 1).** I wrote Tests A and B before touching main code.
- Command: `./gradlew test --tests "com.catspell.api.moderation.BlockEnforcementIntegrationTest"`. Exit 1; 7 tests, 2 failed.
- Test A, `reconnect does not push a message left undelivered before a block and marks it delivered()`, failed with `AssertionFailedError: no reconnect preview for a blocked pair ==> expected: <0> but was: <1>`. One push to B was captured for the blocked pair.
- Test B, `reconnect still pushes a visible conversation while suppressing a blocked one()`, failed with `only the visible conversation is pushed ==> expected: <1> but was: <2>`.
- The 5 existing tests passed.
- `gsd-tools check tdd-red-evidence`, run on a record built from the JUnit XML, returned **`RED_EVIDENCE_OK` (target_test_failed)**.

**GREEN (Task 1).** I added `findHiddenConversationIdsForUser` and partitioned `deliverUnreadMessages`. The same command then exited 0 with 7/7 passing.

**Task 2 (expected green).** Tests C-G cover behavior that Task 1's query already implemented, so they passed on their first run: 12/12 in BlockEnforcementIntegrationTest, plus 19/19 in the chat package. To show they aren't vacuous, I ran a mutation check. I temporarily removed the `EXISTS` (block-row) branch from the query, and exactly one test failed: Test G, `a block row alone with the match still active suppresses the reconnect push ==> expected: <0> but was: <1>`. I then restored the clause; it is byte-identical to the staged version (`git diff` on the file is empty).

**REFACTOR.** None needed.

**Final verification on the restored tree:** `./gradlew test --tests "com.catspell.api.moderation.BlockEnforcementIntegrationTest" --tests "com.catspell.api.chat.*"` gave BUILD SUCCESSFUL. BlockEnforcementIntegrationTest 12/12, ChatIntegrationTest 9/9, ConversationListIntegrationTest 10/10.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt`: new `findHiddenConversationIdsForUser`. It is JPQL: `JOIN c.match m`, where `m.endedAt IS NOT NULL` or a `Block` exists between `m.user1`/`m.user2` in either direction. It has KDoc.
- `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt`: `deliverUnreadMessages` loads the hidden set once and partitions the undelivered messages. Suppressed messages get `delivered = true` and go through `saveAll`, with no push. Visible messages use the unchanged push + save loop. The method returns the pushed count. The KDoc cites D-04/D-05/D-06.
- `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt`: adds the broker-channel capture (`brokerChannel`, a `ChannelInterceptor` registered and removed per test, `notificationsTo`, `delivered`) and Tests A-G.

## Decisions Made
- **JPQL kept; no native fallback.** Hibernate 7 accepted `JOIN c.match m` plus the correlated `EXISTS` subquery at startup.
- **The return value is now the pushed count.** The signature is the same. The only caller, `WebSocketSessionListener`, ignores the value.
- **Early return when nothing is undelivered.** The hidden-set query runs only when there is at least one undelivered message, so a typical reconnect adds no query.

## Known Limitations (recorded, not changed: RESEARCH Pitfall 6 and Pattern 1, D-05)
- **Rematch with no reconnect in between.** Take a message sent while matched, with the recipient offline through an unmatch and a rematch and no reconnect in between. It is pushed on the first reconnect after the rematch. By then the conversation and its full history are visible again, so nothing hidden leaks. Teardown stays unchanged (D-05).
- **Live sends are re-pushed once on reconnect.** `sendMessage` pushes live but never sets `delivered = true`. So every message is re-pushed once on the recipient's next reconnect. This behavior predates this plan.
- **Reversibility (D-04, costly).** After suppression, a suppressed message can't be told apart from a pushed one. The `delivered` flag is the only record.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Started the Podman machine**
- **Found during:** Task 1, before the first test run.
- **Issue:** `podman-machine-default` was stopped, so Testcontainers could not reach a socket.
- **Fix:** Ran `podman machine start`, and ran Gradle with `/opt/homebrew/bin` on PATH; build.gradle.kts resolves `DOCKER_HOST` through `podman machine inspect`.
- **Files modified:** none.

**2. [Rule 3 - Blocking] Flaky container startup under amd64 emulation**
- **Found during:** Task 2, during the mutation check and the final verification.
- **Issue:** `postgis/postgis:16-3.4-alpine` is amd64-only. The VM runs it under qemu: `Rosetta: true` is configured, but `rosetta-activation.service` is inactive, so only the qemu-x86_64 binfmt handler is registered. PostGIS init then sometimes overran Testcontainers' 60 s wait for "ready to accept connections". Each time, every test in the JVM failed with `ExceptionInInitializerError` before any test logic ran.
- **Fix:** Restarted the Podman machine once and re-ran. No code or test-infra change: the `BaseIntegrationTest` timeout is out of scope. Every run where the containers started produced the expected result.
- **Files modified:** none.

---

**Total deviations:** 2 auto-fixed (2 blocking, both environment-only).
**Impact on plan:** None on the code. The plan's code and tests landed exactly as written.

## Issues Encountered
- The Podman VM's Rosetta is inactive, so the Postgres container sometimes starts too slowly. This was out of scope and not changed. Re-enabling Rosetta in the Podman machine, or switching to an arm64 PostGIS image, would make integration runs reliable. This matters for the ~15 min full suite.

## User Setup Required

None. No external service configuration required.

## Next Phase Readiness
- W1 is closed. Ready for 18-02.
- The ~15 min full suite should be run in the background at the phase gate. Container startup can still time out under emulation; re-running fixes it.

## Self-Check: PASSED
- (a) All 3 source paths plus this SUMMARY are in `git diff --cached --name-only`.
- (b) `git rev-parse HEAD` = `605ddb4d49266e805436c16b18bfc78746c46e40`.
- (c) `git diff --name-only` (unstaged) lists none of this plan's files.
- Acceptance criteria:
  - `fun findHiddenConversationIdsForUser` appears 1 time in the repository and 1 time in ChatService.
  - MatchService.kt and BlockService.kt have no diff against HEAD.
  - The test file contains `brokerChannel` and `/queue/notifications`.
  - The test file has 12 `@Test` methods, plus `INSERT INTO blocks` and `matchService.unmatch`.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-03*
