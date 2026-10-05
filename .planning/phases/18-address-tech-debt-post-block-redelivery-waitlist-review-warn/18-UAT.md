---
status: complete
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
source: [18-VERIFICATION.md, 18-12-SUMMARY.md]
started: 2026-10-04T17:30:00Z
updated: 2026-10-05T14:20:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Decide the D-04 rematch residual (18-REVIEW WR-01)
expected: Either (a) accept as within scope (no hidden content leaks, the goal's literal wording holds), record an override and correct the deliverUnreadMessages KDoc; or (b) treat it as a gap: amend D-05, add a set-based mark-delivered UPDATE on the MatchService.createMatch reactivation branch, add a no-reconnect-between test, and fix the KDoc.
result: pass
reported: "b"
resolution: "Decision (b) recorded 2026-10-04 and implemented by 18-12-PLAN (gap G-18-1 resolved 2026-10-05). The fix is re-tested in tests 2-7."

### 2. Block → unblock → rematch with no reconnect between: no stale previews (18-12 D1)
expected: Block, unblock and rematch with no reconnect in between: every pre-block message (both directions) is delivered at rematch time, and neither participant's first reconnect pushes a stale preview
result: pass
source: automated
coverage_id: D1

### 3. Unmatch → rematch with no reconnect between: no stale previews (18-12 D2)
expected: Unmatch and rematch with no reconnect in between: the same guarantee on the unmatch hide path
result: pass
source: automated
coverage_id: D2

### 4. Post-rematch messages still deliver normally (18-12 D3)
expected: No over-suppression: a message sent after the rematch is pushed live once, starts undelivered, and is pushed once more on the next reconnect (returns 1)
result: pass
source: automated
coverage_id: D3

### 5. Sweep is one set-based UPDATE, only on reactivation, before MatchCreatedEvent (18-12 D4)
expected: The sweep is one set-based UPDATE that runs only on the reactivation branch and before MatchCreatedEvent, never for a new or still-active match
result: pass
source: automated
coverage_id: D4

### 6. No regression across moderation, match, chat and push-trigger tests (18-12 D5)
expected: No regression across the moderation, match, chat and push-trigger tests
result: pass
source: automated
coverage_id: D5

### 7. Rematch-sweep documentation reads accurately (18-12 D6)
expected: The deliverUnreadMessages KDoc names both suppression points accurately (code changed only by the WR-02 hidden-first read reorder, 57c6a39), and the 18-CONTEXT.md D-05 amendment correctly records the reactivation-branch exception with endMatch teardown unchanged.
result: pass

## Summary

total: 7
passed: 7
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-18-1
  truth: "Messages to a recipient that were left undelivered when a conversation was hidden (block/unmatch) never resurface as /queue/notifications previews after the pair rematches, whether or not the recipient reconnected between the block/unmatch and the rematch (D-04)."
  status: resolved
  resolved_by: 18-12-PLAN.md
  resolved_at: 2026-10-05
  reason: "User reported: b (treat the D-04 rematch residual as a gap rather than accept it as within scope)"
  severity: major
  test: 1
  root_cause: "Hidden-conversation suppression (mark delivered = true without pushing) runs only inside ChatService.deliverUnreadMessages, i.e. only when the recipient reconnects while the conversation is still hidden. sendMessage pushes live but never sets delivered = true, so every message received live before the block/unmatch is still delivered = false. MatchService.createMatch's reactivation branch clears endedAt/endedReason and publishes MatchCreatedEvent but does not touch message state. When the recipient's next reconnect comes after the rematch, findHiddenConversationIdsForUser no longer returns the conversation, and all those stale messages are pushed as previews. The existing 'does not resurface after rematch' tests always reconnect between the block and the rematch, so they miss this path."
  artifacts:
    - path: "src/main/kotlin/com/catspell/api/match/service/MatchService.kt"
      issue: "Reactivation branch (lines 41-46) clears endedAt and publishes MatchCreatedEvent without marking the match's undelivered messages delivered"
    - path: "src/main/kotlin/com/catspell/api/chat/service/ChatService.kt"
      issue: "deliverUnreadMessages KDoc (lines 263-267) claims suppressed messages 'can't resurface if the pair later rematches', which is only conditionally true. The only delivered writes are at lines 286 and 304"
    - path: "src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt"
      issue: "No set-based bulk 'mark all undelivered delivered for a match' query exists"
    - path: ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-CONTEXT.md"
      issue: "D-05 says the fix lives only on the reconnect path. It needs an amendment allowing one set-based UPDATE on the createMatch reactivation branch (teardown in endMatch stays unchanged)"
  missing:
    - "Add a @Modifying set-based UPDATE to MessageRepository marking all delivered = false messages of the match's conversation as delivered (fall back to a conversation.id IN (subquery on match.id) form if the conversation.match path won't compile as a bulk UPDATE)"
    - "Call it in MatchService.createMatch's reactivation branch (endedAt != null) before publishing MatchCreatedEvent; it must be one statement, not a per-message loop"
    - "Integration test: A sends B messages while matched (live-pushed, undelivered) -> block (and separately unmatch) -> unblock + mutual re-like reactivates the match -> B reconnects for the first time -> zero previews pushed for the pre-block messages, and messages sent after the rematch are still delivered normally"
    - "Correct the ChatService.deliverUnreadMessages KDoc so it describes both suppression points accurately"
    - "Amend D-05 in 18-CONTEXT.md to record the reactivation-branch exception"
  debug_session: ""
  diagnosis_source: "18-REVIEW.md WR-01 (evidence re-confirmed in code 2026-10-05; no separate debug session spawned)"
