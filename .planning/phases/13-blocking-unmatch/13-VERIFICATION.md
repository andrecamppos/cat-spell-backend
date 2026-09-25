---
phase: 13
slug: blocking-unmatch
status: passed
method: integration-test-suite
created: 2026-09-25
updated: 2026-09-25
---

# Phase 13 — Verification

## Verification Method

**Suite:** `./gradlew test` (full project suite — BUILD SUCCESSFUL, no failures)
**Infrastructure:** Spring Boot Test + Testcontainers PostgreSQL+PostGIS + MinIO + JUnit 5
**New tests this phase:** `BlockServiceIntegrationTest` (7), `BlockEnforcementIntegrationTest` (5), `BlockEndpointIntegrationTest` (6)
**Migrations applied on boot:** V19 (`blocks`), V20 (`matches.ended_at`/`ended_reason`) — verified via Testcontainers Flyway migration through V20.

## Results

| Plan | Deliverable | Status |
|------|-------------|--------|
| 13-01 | V19/V20 migrations, Block entity/repo, feed block filter (both branches), deleteSwipesBetween | ✅ Pass |
| 13-02 | SelfBlockException(400), MatchService endMatch/unmatch/reactivation, BlockService | ✅ Pass |
| 13-03 | Profile-detail + swipe 404 guards, chat send/open guards, ended-conversation hiding | ✅ Pass |
| 13-04 | BlockController (block/unblock/list) + MatchController unmatch endpoint | ✅ Pass |
| **Full suite** | all phases (regression) | ✅ All pass |

## Requirements Verified

| REQ-ID | Description | Evidence |
|--------|-------------|----------|
| MOD-01 | A user can block another user | `BlockServiceIntegrationTest`, `BlockEndpointIntegrationTest` (POST /api/blocks → 204, idempotent, self → 400) |
| MOD-02 | Block enforced bidirectionally on every surface (feed, profile/owner detail, chat send, match lookup) | `BlockEnforcementIntegrationTest` (feed both branches, profile 404 both directions, chat send, match-lookup + swipe 404); feed filter proven in `DiscoveryIntegrationTest` |
| MOD-03 | Blocking a matched user retains conversation history but locks it (no new messages; no rediscovery) | `BlockEnforcementIntegrationTest#blocked matched pair cannot send or open chat and conversation hidden while messages persist` |
| MOD-04 | View block list + unblock; unblocking re-enables rediscovery | `BlockEndpointIntegrationTest` (GET/DELETE /api/blocks, IDOR isolation), `BlockEnforcementIntegrationTest#block prevents feed reappearance until unblock` |
| MOD-05 | Unmatch ends match/conversation without banning rediscovery | `BlockEndpointIntegrationTest#unmatch returns 204 then 404 on repeat`, `BlockEnforcementIntegrationTest#unmatch hides conversation for both and allows feed reappearance` |

## Success Criteria

- [x] Blocking hides both users from each other's discovery feed (cat + human branches) and profile/owner-detail endpoints
- [x] A blocked pair cannot open or send in chat; conversation history retained but locked (no new messages either way)
- [x] A user can view their block list and unblock; unblocking re-enables rediscovery
- [x] Unmatch ends the match/conversation but does NOT ban rediscovery (other user reappears)
- [x] Self-block is rejected; enforcement verified on feed, profile-detail, chat, and match lookup

## Notes

- Chat send is WebSocket-only (`@MessageMapping("/chat.send")`); the send-block guard is proven by driving `ChatService.sendMessage` directly (the exact path the WS controller invokes) plus the REST message-open 404.
- Teardown is soft-state only: `matches.ended_at`/`ended_reason` set, swipe rows between the pair cleared; match/conversation/message rows retained as evidence (D-07).
- Log noise during test teardown (`HHH000478` drop-table, `PSQLException: terminating connection`, push `Connection refused`) is expected Testcontainers/Hibernate shutdown behavior, not test failure — suite is BUILD SUCCESSFUL.
