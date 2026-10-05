---
phase: 13
slug: blocking-unmatch
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-09-25
---

# Phase 13 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| client → discovery feed / profile / chat surfaces | Requester identity is bound to the JWT principal (`extractUserId()`); no surface may reveal a user on either side of a block | user IDs, profile/photo data |
| authenticated caller → BlockService / MatchService | Caller may only create/remove blocks they own and unmatch a match they participate in | block/match relationship |
| BlockService → MatchService teardown | Block triggers the same silent soft-state teardown as unmatch plus the rediscovery ban; no path hard-deletes evidence | match state, swipe rows |
| read/send paths → isBlockedEitherWay | Predicate is synchronous + strongly consistent (DB every call, no cache) so there is no stale window | block existence |
| entity ↔ migration (V19/V20) | Block/Match entity mappings must match migrations or the app fails ddl-auto validate at boot | schema |
| DB constraint layer | Self-block and duplicate-block rejectable at the DB even if the service check is bypassed | block rows |
| block/unblock response → blocked party | Responses must not disclose the block to the target | HTTP status only |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-13-01 | Information Disclosure | Feed leaking a blocked user on one branch/direction | high | mitigate | Bidirectional `NOT EXISTS ... blocks` on BOTH UNION branches in `SwipeRepository.findDiscoveryFeed` (V:52,80) | closed |
| T-13-02 | Tampering | Rediscovery ban leaking from a non-block source (unmatch behaving like block) | high | mitigate | Feed filter reads `blocks` table ONLY; `deleteSwipesBetween` touches swipes only | closed |
| T-13-03 | Elevation of Privilege | Self-block corrupting pair state / self-referential rows | medium | mitigate | `CHECK (blocker_id <> blocked_id)` in V19 backstops service reject | closed |
| T-13-04 | Denial of Service | Hard-delete teardown destroying report evidence | high | mitigate | Soft-state `ended_at`/`ended_reason` (V20) only; `deleteSwipesBetween` touches swipes exclusively | closed |
| T-13-05 | Tampering | Schema drift (entity vs V19/V20) blocking prod boot | high | mitigate | Column parity Block/Match ↔ V19/V20; verified by Testcontainers boot (MatchIntegrationTest/DiscoveryIntegrationTest) | closed |
| T-13-06 | Denial of Service | Unindexed reverse block lookup degrading the feed | medium | mitigate | `idx_blocks_reverse` on `(blocked_id, blocker_id)` in V19 | closed |
| T-13-07 | Elevation of Privilege | IDOR — acting on another user's block/unmatch relationship | high | mitigate | block/unblock keyed on authenticated `blockerId`; `unmatch(extractUserId(), targetUserId)` | closed |
| T-13-08 | Information Disclosure | Blocked user learning a block via teardown side effects | high | mitigate | Teardown is silent soft state; no exception/response surfaced to the other party | closed |
| T-13-09 | Spoofing/Tampering | Stale or cached block check letting a blocked user through | high | mitigate | `isBlockedEitherWay` hits the DB every call (`readOnly`, no cache field) | closed |
| T-13-10 | Tampering | Teardown destroying report evidence (hard delete) | high | mitigate | `endMatch` flips `ended_at`/`ended_reason` only; `deleteSwipesBetween` touches swipes only | closed |
| T-13-11 | Elevation of Privilege | Self-block / self-referential state corruption | medium | mitigate | `SelfBlockException` guard before any write + V19 DB CHECK backstop | closed |
| T-13-12 | Tampering | Unmatch silently banning rediscovery (semantics conflated) | high | mitigate | unmatch writes zero `blocks` rows; only `block()` inserts one | closed |
| T-13-13 | Information Disclosure | Enumeration — 404 vs 403 revealing a block exists | high | mitigate | Every gated profile/chat surface throws the identical `ResourceNotFoundException` 404 as the missing-resource path | closed |
| T-13-14 | Information Disclosure | Partial enforcement leaking a blocked user across a surface | high | mitigate | `isBlockedEitherWay` guards `getOwnerProfile`, `getUserProfile`, `swipe` (both branches), `sendMessage` (both paths), `getMessages`, `getConversations`; feed enforced in 13-01 | closed |
| T-13-15 | Spoofing | Blocked user messaging victim via a stale block window | high | mitigate | `isBlockedEitherWay` + `match.endedAt` checked live per request; no cache | closed |
| T-13-16 | Tampering | Hiding via deleting conversations/messages (evidence loss) | high | mitigate | `getConversations` filters on `ended_at IS NULL`; zero row deletions | closed |
| T-13-17 | Repudiation | Unmatch/block semantics conflated in enforcement (feed) | medium | mitigate | Distinct paths: unmatch → reappear; block → hidden until unblock | closed |
| T-13-18 | Elevation of Privilege | IDOR — blocking/unblocking or listing on behalf of another user | high | mitigate | Acting user is always `SecurityContextHolder` principal; GET list scoped to caller | closed |
| T-13-19 | Information Disclosure | Block-list endpoint leaking another user's blocks | high | mitigate | `getBlockList(extractUserId())` returns only the caller's rows | closed |
| T-13-20 | Information Disclosure | Response revealing the block to the blocked party | high | mitigate | block/unblock return 204 no-body (`ResponseEntity.noContent().build()`); no target-facing notification | closed |
| T-13-21 | Elevation of Privilege | Unmatching a match the caller is not part of | high | mitigate | `MatchService.unmatch` resolves by the caller's pair and 404s otherwise | closed |
| T-13-22 | Spoofing | Self-block corrupting state via the endpoint | medium | mitigate | `SelfBlockException` → 400 (`GlobalExceptionHandler`, BAD_REQUEST) before any write | closed |
| T-13-SC | Tampering | npm/pip/cargo installs (supply chain) | low | accept | No new dependencies this phase (tech-stack added: []) — nothing to audit | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-13-SC | T-13-SC | No new dependencies added this phase (tech-stack `added: []`); no supply-chain surface to audit | gsd-secure-phase | 2026-09-25 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-25 | 23 | 23 | 0 | gsd-secure-phase (L1 grep-depth verification) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-25
