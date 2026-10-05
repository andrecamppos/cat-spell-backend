---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
reviewed: 2026-10-05T10:21:58Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt
  - src/main/kotlin/com/catspell/api/match/service/MatchService.kt
  - src/main/kotlin/com/catspell/api/chat/service/ChatService.kt
  - src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt
  - src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
status: issues_found
---

# Phase 18: Code Review Report (incremental, plan 18-12 / UAT gap G-18-1)

**Reviewed:** 2026-10-05T10:21:58Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

This is an incremental review of the staged gap-closure change (diff against HEAD `add8a2f`):
- the new `MessageRepository.markAllDeliveredForMatch` bulk UPDATE
- its single call site in the reactivation branch of `MatchService.createMatch`
- the rewritten KDoc on `ChatService.deliverUnreadMessages`
- integration tests H/I and the new or extended `MatchServiceTest` cases

It replaces the earlier phase-18 review, which is kept in git at `fcf9dd0`. New finding IDs start at CR-01 / WR-02 / IN-07, so they don't collide with the WR-01 and IN-01..IN-06 rows already in `18-REVIEW-DISPOSITION.md`.

**Supersedes earlier WR-01 (the D-04 rematch residual): resolved for the sequential case.** The sweep runs in the same transaction that clears `endedAt`, before `MatchCreatedEvent` is published, and covers both directions. Once the rematch commits, no message from before the hide is still `delivered = false`. So a later reconnect can't push a stale preview, whether or not the recipient reconnected while the conversation was hidden.

I confirmed this from the code:
- the JPQL implicit-path bulk UPDATE is valid on Hibernate 7 (context startup and runtime pass, per 18-12-SUMMARY)
- `createMatch` is the only place `endedAt` is reset to null
- both production callers (`DiscoveryService.swipe`, lines 249 and 296) reach it through the reactivation branch

Tests H/I fail without the line (mutation check recorded in the summary) and pass with it.

One residual remains: a narrow concurrency window between a reconnect and a rematch committing (WR-02). It comes from the order of the two reads in `deliverUnreadMessages`, now that the fix depends on two separate suppression points. No critical issues.

## Narrative Findings (AI reviewer)

## Warnings

### WR-02: Reconnect racing a rematch commit can still push stale pre-hide previews (the two suppression points read in the wrong order)

**File:** `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt:290-295` (interacting with `src/main/kotlin/com/catspell/api/match/service/MatchService.kt:46-51`; KDoc claim at `ChatService.kt:276`)

**Issue:** `deliverUnreadMessages` runs two separate statements under PostgreSQL READ COMMITTED:
1. It loads the undelivered messages (line 290).
2. It then resolves hidden conversations (line 295).

`WebSocketSessionListener.handleSessionConnected` is `@Async` and runs this on every STOMP connect, so it can overlap a rematch transaction (T1) started from another user's swipe. Take this interleaving:
- The reconnect transaction (T2) runs statement (1) before T1 commits. It still sees the pre-hide rows as `delivered = false`, because T1's sweep is uncommitted.
- T1 commits (`endedAt = null` plus the sweep).
- T2 runs statement (2), which now sees the match as active, so the conversation is not hidden.

T2 then pushes every pre-hide message as a `/queue/notifications` preview: exactly the stale resurfacing G-18-1 / D-04 are meant to prevent. The new KDoc says "Only the two together keep messages … from resurfacing", but the two points only compose if the hidden-set read is not later than the undelivered read. The window is milliseconds, but the trigger (a reconnect around the time of a rematch) is normal app behavior. A user who unblocks and re-likes is likely to have the app open.

**Fix:** Read the hidden set first, then the undelivered messages. Under READ COMMITTED, every ordering of T1's commit is then safe:
- T1 commits before the hidden read: the undelivered read already sees the swept rows.
- T1 commits between the two reads: the conversation counts as hidden, and the undelivered read sees the swept rows.
- T1 commits after both reads: the rows are suppressed rather than pushed.

```kotlin
@Transactional
fun deliverUnreadMessages(userId: UUID): Int {
    val participations = conversationParticipantRepository.findByUserId(userId)
    val conversationIds = participations.mapNotNull { it.conversation.id }
    if (conversationIds.isEmpty()) return 0

    // Resolve hidden BEFORE reading undelivered rows: a rematch that commits between the two
    // reads then sees its sweep reflected in the second read (G-18-1 / D-04 race).
    val hidden = conversationRepository.findHiddenConversationIdsForUser(userId).toSet()

    val undelivered = messageRepository.findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc(
        conversationIds, userId
    )
    if (undelivered.isEmpty()) return 0

    val (suppressed, visible) = undelivered.partition { it.conversation.id in hidden }
    // ... unchanged
}
```

Trade-off: a message sent live right after the rematch, within the same window, gets marked delivered without the reconnect duplicate push. It was already pushed live by `sendMessage`, so nothing is lost. The stronger alternative is to fold the hidden predicate into the undelivered query, so both are read from one statement snapshot.

Also soften the KDoc at line 276, or add a sentence saying the ordering is load-bearing.

## Info

### IN-07: Bare `@Modifying` bulk UPDATE leaves already-managed `Message` entities stale in the persistence context

**File:** `src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt:47-49`

**Issue:** The UPDATE bypasses the persistence context. Any `Message` already loaded in the same transaction keeps `delivered = false` in memory, and `Message` has no `@DynamicUpdate`. A later dirty write of that entity would rewrite the full row, including `delivered = false`. Today this can't happen: the only caller is `createMatch`, and its enclosing `DiscoveryService.swipe` transaction loads no `Message` rows. Leaving out `clearAutomatically` also matches the project's other `@Modifying` methods (see the WaitlistEntryRepository note). The risk is to future callers.

**Fix:** Add one line to the KDoc: "Callers must not hold managed `Message` entities in the same transaction (no `clearAutomatically`)". Alternatively use `@Modifying(flushAutomatically = true, clearAutomatically = true)`, but note that clearing would detach the `existing` match that `createMatch` returns.

### IN-08: Integration helper asserts the sweep result after the symptom assertions, which weakens failure diagnostics

**File:** `src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt:430-440`

**Issue:** `deliveredAtRematch` is captured at line 430, straight after the rematch, but asserted only at line 440. A regression in the sweep therefore first fails at line 436 ("no stale preview for B…, expected 0 but was 2"), which looks like a reconnect-path problem, not at the direct "rematch swept every pre-hide message" check. The test still discriminates correctly; only the first failure message points the wrong way.

**Fix:** Move the `assertEquals(listOf(true, true, true), deliveredAtRematch, …)` line up to just after line 430, before the reconnect calls.

---

_Reviewed: 2026-10-05T10:21:58Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
