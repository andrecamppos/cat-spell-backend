---
phase: 13-blocking-unmatch
plan: "03"
subsystem: api
tags: [kotlin, spring, moderation, blocking, chat, discovery, enforcement]

requires:
  - phase: 13-blocking-unmatch (13-02)
    provides: BlockService.isBlockedEitherWay, MatchService.unmatch, Match.endedAt
provides:
  - DiscoveryService profile-detail + swipe bidirectional block 404 guards
  - ChatService send + open guards (block + ended-match) on both entry paths
  - ConversationRepository active-only conversation list filter
  - BlockEnforcementIntegrationTest (four surfaces, both directions, block-vs-unmatch)
affects: [13-04]

tech-stack:
  added: []
  patterns:
    - "Pretend-not-exist 404 on every gated surface — no block-revealing 403 (D-01/D-02)"
    - "Synchronous per-request block/ended check, no caching (D-03)"
    - "Read-time visibility filter; message/conversation rows retained (D-04/D-07)"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/catspell/api/discovery/service/DiscoveryService.kt
    - src/main/kotlin/com/catspell/api/chat/service/ChatService.kt
    - src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt
  created_tests:
    - src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt

key-decisions:
  - "sendMessage/getMessages reuse the existing 'Conversation not found' message for both the block and the ended-match guard — indistinguishable from a genuinely absent thread"
  - "swipe block guard placed at the top of each branch so the reverse-like → createMatch reactivation is unreachable for a blocked pair"

patterns-established:
  - "Pattern 1: every read/send surface consults BlockService.isBlockedEitherWay live per request"

requirements-completed: [MOD-02, MOD-03]

coverage:
  - id: D1
    description: "profile-detail (cat-owner + user-profile) and discovery feed enforce bidirectional block with a pretend-not-exist 404"
    requirement: "MOD-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#block hides both users from each others feed and profile-detail 404 both directions"
        status: pass
    human_judgment: false
  - id: D2
    description: "chat send + open rejected on both entry paths for blocked/ended pairs; ended conversations hidden from both lists while message rows persist"
    requirement: "MOD-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#blocked matched pair cannot send or open chat and conversation hidden while messages persist"
        status: pass
    human_judgment: false
  - id: D3
    description: "match-lookup surface: matched-then-blocked pair absent from both match lists; blocked pair's fresh swipe returns 404"
    requirement: "MOD-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#matched-then-blocked pair absent from matches and fresh swipe returns 404"
        status: pass
    human_judgment: false
  - id: D4
    description: "block-vs-unmatch rediscovery distinction: unmatch hides conversation and re-opens feed; block hides feed until unblock"
    requirement: "MOD-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt#unmatch hides conversation for both and allows feed reappearance"
        status: pass
    human_judgment: false
  - id: D5
    description: "regression: active conversations still listed (ConversationListIntegrationTest)"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/chat/ConversationListIntegrationTest.kt"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-09-25
status: complete
---

# Phase 13 Plan 03: Block Enforcement Fan-Out Summary

**Bidirectional block/ended-match enforcement across the remaining three surfaces — profile-detail + swipe match-lookup (DiscoveryService), chat send/open (ChatService), and ended-conversation hiding — all pretend-not-exist 404s, proven end-to-end**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-09-25T10:08:00Z
- **Completed:** 2026-09-25T10:19:00Z
- **Tasks:** 3
- **Files modified:** 4 (1 test created, 3 modified)

## Accomplishments
- `DiscoveryService.getOwnerProfile`/`getUserProfile` throw a pretend-not-exist 404 when `isBlockedEitherWay` (D-01/D-02/D-03).
- `DiscoveryService.swipe` guards BOTH the cat and human branches at the top, so the reverse-like → `createMatch` path can neither form nor reactivate a match for a blocked pair (MOD-02, D-03).
- `ChatService.sendMessage` and `getMessages` reject with a 404 on bidirectional block OR ended match, covering both the conversationId and matchId entry paths (MOD-02/MOD-03, D-02/D-04/D-05).
- `ConversationRepository.findConversationsByUserId` excludes ended (blocked/unmatched) matches — threads vanish from both lists while message rows are retained as evidence (D-04/D-07).
- `BlockEnforcementIntegrationTest` proves all four surfaces bidirectionally, message/history retention, and the block-vs-unmatch rediscovery distinction.

## Task Commits

1. **Task 1: DiscoveryService profile + swipe block guards** - `287e151` (feat)
2. **Task 2: ChatService send/open guards + ConversationRepository filter** - `1d084a1` (feat)
3. **Task 3: BlockEnforcementIntegrationTest** - `1d51d1b` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/discovery/service/DiscoveryService.kt` - block 404 guards on getOwnerProfile/getUserProfile/swipe
- `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt` - send/open block + ended-match guards
- `src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt` - active-only conversation list
- `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt` - four-surface enforcement proof

## Decisions Made
- Chat send is WebSocket-only (`@MessageMapping("/chat.send")`), so the send-rejection assertions drive `ChatService.sendMessage` directly (the same service path the WS controller calls); message-open uses the REST `GET /api/conversations/{id}/messages` 404.

## Deviations from Plan

None - plan executed exactly as written.

## TDD Gate Compliance
Task 3 was `tdd="true"` and is a pure test-authoring task (no production source), committed as `test(13-03)` = `1d51d1b`. The behavior it asserts was implemented in Tasks 1–2 (`feat` commits). MVP+TDD gate is inactive for this phase.

## Issues Encountered
None. (Testcontainers/Hibernate schema-drop noise on context shutdown is expected — all tests pass.)

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All read/send surfaces are enforced. Plan 13-04 only needs to expose the HTTP endpoints (BlockController + unmatch) over the already-proven service layer.

---
*Phase: 13-blocking-unmatch*
*Completed: 2026-09-25*
