# Pitfalls Research

**Domain:** Dating-app backend — safety/moderation + gated access
**Researched:** 2026-09-24
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: Block enforced on only some surfaces

**What goes wrong:**
Block hides a user from the discovery feed but they can still open an existing conversation and send messages, or still appear via the owner-profile / human-card detail endpoints.

**Why it happens:**
The block predicate is added to the feed query but not to chat send, match lookup, or the profile detail endpoints. There are multiple read paths (`DiscoveryService.getFeed`, `getOwnerProfile`, `getUserProfile`, chat send, conversation fetch).

**How to avoid:**
Enumerate every surface where user A can see or contact user B and add the bidirectional block check to each: discovery feed (both UNION branches), owner/human profile detail, chat send, conversation open, and match creation.

**Warning signs:**
A test that blocks then still fetches the blocked user's profile returns 200; blocked user receives a chat message.

**Phase to address:**
The block/moderation phase — success criteria must assert enforcement on discovery, profile detail, AND chat.

---

### Pitfall 2: Block vs unmatch semantics conflated

**What goes wrong:**
Unmatch also permanently bans rediscovery (or block fails to ban it), so the two features behave identically or wrongly.

**Why it happens:**
Both tear down a match/conversation, so the differentiating rule (block filters the feed; unmatch does NOT) gets lost.

**How to avoid:**
Only the `blocks` table feeds the discovery `NOT EXISTS` filter. Unmatch ends the match/conversation but writes nothing that excludes future rediscovery. Encode this in explicit tests: after unmatch, the other user can reappear; after block, they cannot.

**Warning signs:**
Product complaint "I unmatched someone and now never see anyone from that pool" or "I blocked someone and they still show up."

**Phase to address:**
Moderation phase — separate acceptance criteria for block-rediscovery-banned vs unmatch-rediscovery-allowed.

---

### Pitfall 3: DOB collected too late for a hard 18+ gate

**What goes wrong:**
The age gate can't hard-block at signup because `date_of_birth` currently lives on `user_profiles` (set during profile completion), not on the registration payload — so an under-18 can create an account and only get blocked later.

**Why it happens:**
Existing schema puts DOB on the profile; the discovery age filter reads it there. A signup-time gate needs DOB earlier.

**How to avoid:**
Decide explicitly: collect DOB at register time (recommended for a true hard gate) and hard-block under-18 before account creation, or accept a profile-completion gate and document the weaker guarantee. If moving DOB, plan the migration + backfill carefully (existing accounts).

**Warning signs:**
Under-18 account exists in `users` with no enforced DOB; gate lives only in the mobile client.

**Phase to address:**
Age-verification phase — resolve DOB placement first; it blocks the gate design.

---

### Pitfall 4: Invite gate not enumeration-safe / codes guessable or reusable

**What goes wrong:**
Invalid-vs-consumed invite codes return different errors (enumeration), codes are short/sequential (guessable), or a single code can be redeemed by many accounts when it should be single-use.

**Why it happens:**
Rolling a bespoke code path instead of reusing the hashed single-use token discipline; returning granular errors for debugging.

**How to avoid:**
Generate codes with `SecureRandom` (URL-safe, high-entropy), store hashed, enforce single-use via an atomic claim (`use_count`/status compare-and-set), and return a generic "invalid or expired invite" for both bad and consumed codes.

**Warning signs:**
Codes like `INVITE-1002`; the same code onboards multiple users; different HTTP bodies for invalid vs used.

**Phase to address:**
Invite-only phase.

---

### Pitfall 5: Waitlist public endpoint leaks membership or gets flooded

**What goes wrong:**
The join endpoint returns "already signed up" (enumeration) or accepts unlimited submissions (bot flood, disposable emails), poisoning the launch list.

**Why it happens:**
Public unauthenticated endpoint without enumeration-safe responses or throttling; forgetting to normalize emails so `a+x@` / `a.x@gmail` bypass per-email limits.

**How to avoid:**
Return an identical `202` regardless of whether the email is new/duplicate; apply Bucket4j per-IP + per-email throttle (reuse `RateLimitFilter`); normalize emails before dedupe/rate-limit; use double-opt-in so bots that can't confirm never reach CONFIRMED; optionally drop disposable domains.

**Warning signs:**
Response differs for known vs new email; thousands of unconfirmed entries from one IP.

**Phase to address:**
Waitlist phase.

---

### Pitfall 6: Report email notification blocks or rolls back the request

**What goes wrong:**
Sending the operator notification synchronously makes the report endpoint slow, or an email failure rolls back the persisted report.

**Why it happens:**
Calling `EmailSender` inline inside the report transaction.

**How to avoid:**
Persist the report first; dispatch the operator email out-of-band (`@TransactionalEventListener(AFTER_COMMIT)` / async), exactly like the v2.0 push pipeline — a failing/slow send must never block or roll back persistence.

**Warning signs:**
Report latency tracks email latency; a mail outage loses reports.

**Phase to address:**
Report phase.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Age gate in mobile client only | Fast, no schema change | No server-side guarantee; trivially bypassed; legal exposure | Never for a real 18+ gate |
| Report as free-text only (no category) | Less UI/schema | Hard to triage/aggregate later | Never — category enum is cheap now |
| Block enforced only on feed | Quick demo | Safety hole on chat/profile surfaces | Never |
| Skipping referral attribution now | Less work | Can't retrofit who-invited-whom after signups happen | Only if referrals are explicitly dropped (they aren't) |
| Storing raw invite codes | Simpler compare | Table leak = account access | Never |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| `EmailSender` for operator/report | Assuming a real send in dev/tests | Default no-op logging provider; assert via mock, no network sends |
| `RateLimitFilter` for public waitlist | Adding a second filter / new dependency | Add endpoint keys to the existing Bucket4j filter |
| Public endpoint security | Forgetting one of the whitelist locations | Follow the established three-place public-endpoint whitelist pattern |
| Flyway | Editing a shipped migration to add a column | Append `V19+`; never touch `V1..V18` |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Block filter without reverse index | Slow feed as blocks grow | Index `blocks(blocked_id, blocker_id)` in addition to PK `(blocker_id, blocked_id)` | 10k+ blocks |
| N+1 block checks in chat lists | Latency on conversation list | Batch/`NOT EXISTS` join, not per-row lookups | Any real conversation volume |
| Unbounded waitlist growth from bots | Table bloat, bad launch metrics | Double-opt-in + throttle + disposable-domain filter | Launch spike |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Cached/eventually-consistent block check | Blocked user still contacts victim during stale window | Synchronous strongly-consistent DB check; invalidate cache synchronously if any |
| Self-block / self-report / self-invite | Data corruption, self-referral fraud | Reject `blocker == blocked`, `reporter == target`, and self-referral at service layer |
| Enumeration via invite/waitlist errors | Leaks which emails/codes exist | Generic responses for invalid vs consumed vs duplicate |
| Reflecting reporter identity to reported user | Retaliation risk | Never expose who reported whom to the reported party |
| IDOR on block-list / unblock | Acting on another user's blocks | Object-level authz scoped to the authenticated user (same discipline as device-token endpoints) |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No unblock path | Users stuck with mis-taps | List + unblock (in scope) |
| Silent block (no confirmation) | User unsure it worked | Return clear success; ensure enforcement is immediate |
| Losing chat history on block | Confusion / lost context for reports | Retain history locked (this milestone's chosen behavior) |

## "Looks Done But Isn't" Checklist

- [ ] **Block:** enforced on discovery feed AND profile-detail AND chat send AND match — verify all four, both directions.
- [ ] **Unmatch:** other user CAN reappear in discovery afterward — verify it does NOT write a rediscovery ban.
- [ ] **Age gate:** enforced server-side at account creation, not just client — verify an under-18 register call is rejected.
- [ ] **Invite gate:** flipping `app.invite.enabled=false` fully opens signup — verify both states.
- [ ] **Invite codes:** single-use + generic error for invalid/consumed — verify reuse is rejected identically.
- [ ] **Waitlist:** identical `202` for new vs duplicate; unconfirmed entries never count as confirmed.
- [ ] **Report:** persisted even if email send fails; operator notified out-of-band; optional alsoBlock actually blocks.
- [ ] **Referral:** referrer→invitee linkage recorded at consumption time.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Under-18 accounts created (late gate) | MEDIUM | Add server gate, audit existing DOBs, suspend/flag under-18, backfill |
| Block surface leak found post-launch | MEDIUM | Add missing `NOT EXISTS`/service check, regression test, hotfix |
| Waitlist flooded pre-launch | LOW | Purge unconfirmed + disposable, tighten throttle, require confirm |
| Invite codes leaked/guessed | MEDIUM | Revoke affected codes, rotate generation, hash-at-rest audit |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Partial block enforcement | Moderation (block/unmatch/report) | Multi-surface block tests, both directions |
| Block vs unmatch conflated | Moderation | Distinct rediscovery tests |
| DOB too late for gate | Age verification | Under-18 register rejected server-side |
| Guessable/reusable invite codes | Invite-only | SecureRandom + single-use + generic-error tests |
| Waitlist enumeration/flood | Waitlist | 202-parity + throttle + double-opt-in tests |
| Report send blocks request | Moderation (report) | Report persists on email failure; async notify |

## Sources

- vibeengines.com, techinterview.org (User Blocking / Invitation / Referral LLDs) — enforcement + fraud pitfalls (MEDIUM–HIGH)
- getqueueup.com, waitlister.me, getlaunchlist.com, Taldres/laravel-waitlist — waitlist enumeration/flood defenses + email normalization (MEDIUM)
- Hinge Help, Apple age-assurance, CA AB-1043 — age-gate guarantees + legal drivers (HIGH)
- In-repo v2.0/v2.1 patterns: AFTER_COMMIT async dispatch, hashed single-use tokens, enumeration-safe responses, three-place whitelist, `SwipeRepository`/`DiscoveryService` read paths, `db/migration/V18` (HIGH)

---
*Pitfalls research for: dating-app backend — safety + gated access*
*Researched: 2026-09-24*
