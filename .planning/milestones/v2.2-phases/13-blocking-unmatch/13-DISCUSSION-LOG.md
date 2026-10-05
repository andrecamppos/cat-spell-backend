# Phase 13: Blocking & Unmatch - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-24
**Phase:** 13-blocking-unmatch
**Areas discussed:** Blocked-surface responses, Teardown state model, Swipe history on block/unblock, Block scope & entry points, Block list

---

## Blocked-surface responses

| Option | Description | Selected |
|--------|-------------|----------|
| 404 pretend-not-exist | Both parties look like they don't exist / aren't reachable; hides the block; matches existing 404-on-missing-profile behavior; safest anti-harassment default | ✓ |
| 403 explicit blocked | Clear "this action is blocked" error; more transparent but reveals the block relationship | |
| Split: 404 profile, 403 chat | Hide profile (404) but explicit locked/403 on the existing conversation | |

**User's choice:** 404 pretend-not-exist
**Notes:** Enforced bidirectionally on profile detail, chat send, and feed.

---

## Teardown state model (conversation visibility)

| Option | Description | Selected |
|--------|-------------|----------|
| Vanishes for both | Conversation disappears from both chat lists; history retained server-side | ✓ |
| Stays, read-only locked | Convo remains visible but locked; history readable, no new messages | |
| Blocker vanishes, other locked | Asymmetric | |

**User's choice:** Vanishes for both (block)
**Notes:** Unmatch given the same treatment (see below). History never hard-deleted (evidence for reports).

---

## Unmatch conversation visibility

| Option | Description | Selected |
|--------|-------------|----------|
| Vanishes for both | Same as block: convo disappears from both chat lists, history retained; pair can re-match later via discovery | ✓ |
| Vanishes for initiator only | Asymmetric; other user still sees it | |
| Stays, read-only locked | Convo stays visible but locked | |

**User's choice:** Vanishes for both
**Notes:** Only user-visible difference between block and unmatch is rediscovery, not chat-list behavior.

---

## Swipe history on block/unblock

| Option | Description | Selected |
|--------|-------------|----------|
| Fresh start, re-swipe | Block clears/ignores prior swipe history; unblock re-enables rediscovery; must swipe again to re-match | ✓ |
| Auto re-match | Prior mutual like preserved; unblock instantly restores match + conversation | |
| Keep swipes, no auto-match | Swipe rows preserved (no reappearance in feed) but no match auto-created | |

**User's choice:** Fresh start, re-swipe
**Notes:** Avoids surprise instant re-matches after unblock.

---

## Block scope & entry points

| Option | Description | Selected |
|--------|-------------|----------|
| Anyone by userId; block ⊇ unmatch | Block any user by userId (match, chat, or discovery/profile card); blocking a matched user ends match + locks convo + bans rediscovery; unmatch does teardown without the ban | ✓ |
| Only matched users | Block only reachable for matched/chatted users | |

**User's choice:** Anyone by userId; block ⊇ unmatch
**Notes:** Self-block rejected (pre-locked by roadmap); block idempotent.

---

## Block list

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal identity | userId, display name, thumbnail, when-blocked | ✓ |
| Just userId + timestamp | Bare minimum | |
| Full profile summary | bio, cats, etc. | |

**User's choice:** Minimal identity

---

## Claude's Discretion

- Exact soft-state schema for teardown (match status/ended_at, conversation lock/hidden flag, vs derived predicate).
- Physical delete vs flag for swipe rows on block (as long as fresh re-swipe on unblock holds).
- Inline SQL `NOT EXISTS` vs `BlockService` method for the block predicate (research recommends both).
- `moderation/` package layout, endpoint shapes, RFC 7807 error bodies.

## Deferred Ideas

- Report a user + `alsoBlock` flag — Phase 14 (reuses this phase's `BlockService`).
- Report triage/status, auto-suspend after N reports, admin moderation panel — v2.x+ (out of scope).
