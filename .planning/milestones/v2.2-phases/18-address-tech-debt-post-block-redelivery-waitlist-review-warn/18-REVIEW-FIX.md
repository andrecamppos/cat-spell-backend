---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
fixed_at: 2026-10-05T10:58:52Z
review_path: .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-REVIEW.md
iteration: 1
findings_in_scope: 1
fixed: 1
skipped: 0
status: all_fixed
---

# Phase 18: Code Review Fix Report

**Fixed at:** 2026-10-05T10:58:52Z
**Source review:** .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-REVIEW.md
**Iteration:** 1

**Note on scope:** `fix_scope` is `critical_warning`. REVIEW.md reported 0 Critical findings, 1 Warning finding (WR-02), and 2 Info findings (IN-07, IN-08) — Info findings are out of scope for this run and were not touched.

**Note on commit policy:** This project's standing rule (per user's global thumb-rules and `.claude/projects/.../memory/gsd-stage-not-commit.md`) is "never auto-commit — stage only." The fix below was applied and staged with `git add`, but **not committed**. The user will review and commit when ready.

**Summary:**
- Findings in scope: 1
- Fixed: 1
- Skipped: 0

## Fixed Issues

### WR-02: Reconnect racing a rematch commit can still push stale pre-hide previews (the two suppression points read in the wrong order)

**Files modified:** `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt`
**Staged (not committed):** yes — `git add src/main/kotlin/com/catspell/api/chat/service/ChatService.kt`
**Verification:** `./gradlew compileKotlin -q` — compiled cleanly, no errors in the modified file. Full test suite was not run (out of scope for per-finding verification per the 3-tier strategy; this is a logic/ordering fix — see note below).

**Applied fix:** Reordered the two reads in `deliverUnreadMessages` (lines 294-309) so the hidden-conversation set (`conversationRepository.findHiddenConversationIdsForUser`) is resolved **before** the undelivered-messages query (`messageRepository.findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc`), matching the fix suggested in REVIEW.md exactly. Under PostgreSQL READ COMMITTED, this ordering makes every interleaving of a concurrent rematch commit (`MatchService.createMatch`'s reactivation-branch sweep) safe:
- rematch commits before the hidden read → the undelivered read already sees the swept rows (not stale)
- rematch commits between the two reads → the conversation now counts as hidden, so the (still pre-sweep) undelivered rows are suppressed rather than pushed
- rematch commits after both reads → nothing changes, rows are suppressed as before the fix

Also updated the KDoc on `deliverUnreadMessages` (lines 282-290) to add an explicit paragraph stating that the read ordering is load-bearing and explaining why, addressing the review's secondary suggestion ("Also soften the KDoc at line 276, or add a sentence saying the ordering is load-bearing").

**Deviation from the literal REVIEW.md snippet:** The review's illustrative code block assigned the hidden-read result to a new variable named `hidden` as if introducing it for the first time, and showed the function re-fetching `conversationIds` afterward. The actual current source already has a variable named `hidden` declared immediately before the partition call, and `conversationIds` is computed once at the top of the function (unchanged region). The applied fix is the minimal equivalent: move the existing `val hidden = ...` line (and its one-line "why" comment, replaced by a comment referencing the new KDoc) to immediately after the early-return on empty `conversationIds`, and move the existing `val undelivered = ...` block to after it. No new variables were introduced and no unrelated lines were touched, which keeps the diff smaller than blindly pasting the review's snippet while preserving the exact fix semantics.

## Skipped Issues

None — the one in-scope finding (WR-02) was fixed. IN-07 and IN-08 are out of scope for `fix_scope: critical_warning` and were intentionally not addressed in this run.

---

_Fixed: 2026-10-05T10:58:52Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
