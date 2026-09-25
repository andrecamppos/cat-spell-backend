# Phase 13: Blocking & Unmatch - Context

**Gathered:** 2026-09-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver user-protection primitives: **block**, **unblock**, **view block list**, and **unmatch**. Introduce a `blocks(blocker_id, blocked_id)` relationship and a **bidirectional block predicate** that is consulted synchronously on every surface where one user can see or contact another — discovery feed (both UNION branches), profile/owner detail, chat send/conversation open, and match lookup. Block bans rediscovery; unmatch does not.

This phase establishes the block relationship + predicate that Phase 14 (Report `alsoBlock`) and all read paths reuse. New Flyway migration (`V19+`; last shipped is `V18`).

**In scope:** block/unblock/list endpoints, unmatch endpoint, bidirectional enforcement across discovery/profile-detail/chat/match, soft-state teardown (retain messages), self-block rejection.

**Out of scope (own phases / deferred):** report a user (Phase 14), age gate (Phase 15), invite gate (Phase 16), waitlist (Phase 17), report triage/auto-suspend/admin moderation panel (v2.x+).

</domain>

<decisions>
## Implementation Decisions

### Blocked-surface responses (MOD-02)
- **D-01:** A blocked user hitting the *other* party's profile-detail (`getOwnerProfile` / `getUserProfile`) or the discovery feed gets a **404 "pretend-not-exist"** response — neither party can see the other, and the block is not disclosed. This matches the existing `ResourceNotFoundException` (404) convention for missing profiles and is the safest anti-harassment default.
- **D-02:** Chat send / conversation open across a blocked pair is likewise rejected as if the resource is gone (no new messages either way). No explicit "you are blocked" 403 is surfaced.
- **D-03:** Enforcement is **bidirectional** — the check must consider a block in *either* direction (`blocker→blocked` OR `blocked→blocker`) on every surface.

### Conversation / match teardown visibility (MOD-03, MOD-05)
- **D-04:** On **block** of a matched pair, the existing conversation **vanishes from both users' chat lists**. History is **retained server-side** (evidence for future reports; never hard-deleted) but not shown to either party.
- **D-05:** On **unmatch**, the conversation **also vanishes for both users** (same treatment as block for list visibility). History retained server-side.
- **D-06:** The difference between block and unmatch is rediscovery, not chat-list behavior: after **unmatch** the pair *can* reappear in each other's feed; after **block** they cannot (until unblock).
- **D-07:** Match "ended" and conversation "locked/hidden" state must be represented via soft state (new column(s) on `matches` and/or `conversations`, or a derived predicate) — **never** hard-delete matches/messages. Exact schema is Claude's discretion (see below).

### Block vs unblock rediscovery semantics (MOD-04, MOD-05)
- **D-08:** On **unblock**, rediscovery is re-enabled but it is a **fresh start** — the pair must **swipe again** to re-match. A prior mutual like does **not** auto-recreate the match. Block clears/ignores the prior swipe history between the pair so unblock cleanly re-opens rediscovery.
- **D-09:** Only the `blocks` table feeds the discovery `NOT EXISTS` filter. Unmatch writes nothing that excludes future rediscovery.

### Block scope & entry points (MOD-01)
- **D-10:** A user can block **anyone by userId** — reachable from a match, a chat, or a discovery/profile card (not restricted to matched users). Enables preemptive blocking from discovery.
- **D-11:** **Block ⊇ unmatch:** blocking a matched user performs the full teardown (end match + hide conversation for both) **plus** the rediscovery ban. Unmatch performs the same teardown **without** the rediscovery ban.
- **D-12:** **Self-block is rejected.** Block is idempotent (re-blocking an already-blocked user is a no-op success).

### Block list endpoint (MOD-04)
- **D-13:** "View my block list" returns **minimal identity per blocked user**: userId, display name, photo thumbnail, and when-blocked timestamp. Enough to recognize and unblock; no bio/cats.

### Claude's Discretion
- Exact schema for teardown state (status/ended_at column on `matches`, a lock/hidden flag on `conversations`, vs a purely `blocks`-derived predicate) — pick the cleanest soft-state model that satisfies D-04/D-05/D-07.
- Whether swipe rows are physically deleted vs flagged on block, as long as D-08 (fresh re-swipe on unblock) holds.
- Whether the block predicate is enforced via inline SQL `NOT EXISTS` in the feed query vs a `BlockService` method for imperative checks (research recommends both: inline in the feed query, service method elsewhere).
- Package layout under `moderation/` (controller/service/model) per the architecture research.
- Endpoint URL/verb shapes and RFC 7807 error bodies (follow existing conventions).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/ROADMAP.md` §"Phase 13: Blocking & Unmatch" — goal, success criteria, `blocks` table note (V19+)
- `.planning/REQUIREMENTS.md` — MOD-01, MOD-02, MOD-03, MOD-04, MOD-05 (the requirements this phase closes)

### Milestone research (v2.2)
- `.planning/research/ARCHITECTURE.md` §"Pattern 1: Bidirectional block filter on read paths", §"Block enforcement fan-out", §"Anti-Patterns" (no cached block checks, no hard-delete, no circular block module) — the block-predicate design this phase must follow
- `.planning/research/PITFALLS.md` §"Pitfall 1: Block enforced on only some surfaces" and §"Pitfall 2: Block vs unmatch semantics conflated" — the two failure modes success criteria must guard against
- `.planning/research/FEATURES.md` §"Table Stakes" (block/unblock/unmatch rows) and §"Feature Dependencies" — behavior expectations
- `.planning/research/SUMMARY.md` — milestone-level framing and phase rationale

### In-repo code (read before modifying)
- `src/main/kotlin/com/catspell/api/discovery/model/SwipeRepository.kt` — `findDiscoveryFeed` native query; block `NOT EXISTS` goes into **both** UNION branches alongside the existing swipe-dedupe exclusion
- `src/main/kotlin/com/catspell/api/discovery/service/DiscoveryService.kt` — `getFeed`, `getOwnerProfile`, `getUserProfile` (profile-detail surfaces that need the block check)
- `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt` — `sendMessage` / `findOrCreateConversation` / `getConversations` (send guard + chat-list hiding)
- `src/main/kotlin/com/catspell/api/match/service/MatchService.kt` — `createMatch` / `findExistingMatch` (match lookup guard + teardown)
- `src/main/kotlin/com/catspell/api/match/model/Match.kt` — no status field today (teardown state must be added)
- `src/main/kotlin/com/catspell/api/chat/model/Conversation.kt` — no lock/status field today
- `src/main/resources/db/migration/V18__create_email_change_requests_table.sql` — latest migration; new work is `V19+`, never edit V1–V18

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SwipeRepository.findDiscoveryFeed` native query already uses `NOT EXISTS (SELECT 1 FROM swipes ...)` for swipe dedupe — the exact same technique/shape adds the bidirectional block filter in both UNION (CAT + HUMAN) branches.
- `MatchService.findByUserPair` / normalized `(u1,u2)` ordering pattern is a model for a symmetric `blocks` pair lookup.
- Soft-state / immutable-message-history invariant from v1.0 chat — teardown must retain messages, only flip access/visibility.
- RFC 7807 error handling and `ResourceNotFoundException` (404) convention — reuse for the "pretend-not-exist" block responses (D-01/D-02).

### Established Patterns
- Package-per-domain (`discovery/`, `chat/`, `match/`) with controller/service/model — new code lands in a `moderation/` package.
- `@Transactional` service methods; JPA entities + Spring Data repositories; Flyway append-only migrations.
- Match creation happens inside `DiscoveryService.swipe` via `matchService.createMatch` on reverse-like — unblock's "fresh re-swipe" (D-08) rides this existing path (no auto-match code needed).

### Integration Points
- Discovery feed query (both branches) — inline block `NOT EXISTS`.
- `DiscoveryService.getOwnerProfile` / `getUserProfile` — imperative block check → 404.
- `ChatService.sendMessage` + conversation open — block/unmatch send guard; `getConversations` — hide vanished threads.
- `MatchService.createMatch` / `findExistingMatch` — block check + teardown on block/unmatch.
- New `moderation/` package: `BlockController` + `BlockService` (owns `blocks` table + bidirectional predicate) exposed for read paths and reused by Phase 14 report `alsoBlock`.

</code_context>

<specifics>
## Specific Ideas

- Block and unmatch should feel identical in the chat list (both threads simply disappear for both users); the ONLY user-visible difference is that unmatched people can come back in discovery and blocked people cannot.
- Blocking should never tell the blocked user anything — silent, 404-style, non-disclosing.

</specifics>

<deferred>
## Deferred Ideas

- Report a user + `alsoBlock` flag — Phase 14 (reuses `BlockService` from this phase).
- Report triage/status workflow, auto-suspend after N reports, admin moderation panel — v2.x+ (explicitly out of scope; auto-suspend is an anti-feature per research).

None else — discussion stayed within phase scope.

</deferred>

---

*Phase: 13-blocking-unmatch*
*Context gathered: 2026-09-24*
