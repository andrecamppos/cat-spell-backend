---
status: complete
phase: 13-blocking-unmatch
source: [13-01-SUMMARY.md, 13-02-SUMMARY.md, 13-03-SUMMARY.md, 13-04-SUMMARY.md]
started: 2026-09-25T00:00:00Z
updated: 2026-09-25T00:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: |
  Stop any running server. Wipe the DB volume (podman compose down -v), then
  podman compose up -d and ./gradlew bootRun. Flyway replays all migrations
  including V19 (blocks table) and V20 (match teardown columns) from an empty
  schema with no errors, the server boots on :8080, and a primary request
  (e.g. POST /api/blocks then GET /api/blocks) returns live data.
result: pass

### 2. blocks table (V19) with idempotency UNIQUE, self-block CHECK, reverse index applies on schema boot
expected: blocks table (V19) with idempotency UNIQUE, self-block CHECK, reverse index applies on schema boot
result: pass
source: automated
coverage_id: 13-01-D1

### 3. matches soft-state teardown columns (V20) ended_at/ended_reason added with no backfill; matches list is active-only
expected: matches soft-state teardown columns (V20) ended_at/ended_reason added with no backfill; matches list is active-only
result: pass
source: automated
coverage_id: 13-01-D2

### 4. discovery feed excludes blocked pairs bidirectionally in both UNION branches (blocks-only); deleteSwipesBetween re-opens rediscovery
expected: discovery feed excludes blocked pairs bidirectionally in both UNION branches (blocks-only); deleteSwipesBetween re-opens rediscovery
result: pass
source: automated
coverage_id: 13-01-D3

### 5. Block entity + BlockRepository (bidirectional existsBlockBetween, idempotency probe, directional unblock delete, block-list query) compile and match V19
expected: Block entity + BlockRepository (bidirectional existsBlockBetween, idempotency probe, directional unblock delete, block-list query) compile and match V19
result: pass
source: automated
coverage_id: 13-01-D4

### 6. SelfBlockException maps to RFC 7807 400
expected: SelfBlockException maps to RFC 7807 400
result: pass
source: automated
coverage_id: 13-02-D1

### 7. BlockService.block rejects self, is idempotent, runs block⊇unmatch teardown (match ended reason=BLOCK, both-direction swipes cleared, match+messages retained)
expected: BlockService.block rejects self, is idempotent, runs block⊇unmatch teardown (match ended reason=BLOCK, both-direction swipes cleared, match+messages retained)
result: pass
source: automated
coverage_id: 13-02-D2

### 8. unblock deletes only the caller's directional row (IDOR-scoped), idempotent, no auto-rematch; isBlockedEitherWay true both ways after block and false after unblock
expected: unblock deletes only the caller's directional row (IDOR-scoped), idempotent, no auto-rematch; isBlockedEitherWay true both ways after block and false after unblock
result: pass
source: automated
coverage_id: 13-02-D3

### 9. MatchService.unmatch is participant-scoped (404 when no active match) and writes no block row; createMatch reactivates ended matches
expected: MatchService.unmatch is participant-scoped (404 when no active match) and writes no block row; createMatch reactivates ended matches
result: pass
source: automated
coverage_id: 13-02-D4

### 10. getBlockList returns minimal-identity entries newest first
expected: getBlockList returns minimal-identity entries newest first
result: pass
source: automated
coverage_id: 13-02-D5

### 11. profile-detail (cat-owner + user-profile) and discovery feed enforce bidirectional block with a pretend-not-exist 404
expected: profile-detail (cat-owner + user-profile) and discovery feed enforce bidirectional block with a pretend-not-exist 404
result: pass
source: automated
coverage_id: 13-03-D1

### 12. chat send + open rejected on both entry paths for blocked/ended pairs; ended conversations hidden from both lists while message rows persist
expected: chat send + open rejected on both entry paths for blocked/ended pairs; ended conversations hidden from both lists while message rows persist
result: pass
source: automated
coverage_id: 13-03-D2

### 13. match-lookup surface: matched-then-blocked pair absent from both match lists; blocked pair's fresh swipe returns 404
expected: match-lookup surface: matched-then-blocked pair absent from both match lists; blocked pair's fresh swipe returns 404
result: pass
source: automated
coverage_id: 13-03-D3

### 14. block-vs-unmatch rediscovery distinction: unmatch hides conversation and re-opens feed; block hides feed until unblock
expected: block-vs-unmatch rediscovery distinction: unmatch hides conversation and re-opens feed; block hides feed until unblock
result: pass
source: automated
coverage_id: 13-03-D4

### 15. regression: active conversations still listed (ConversationListIntegrationTest)
expected: regression: active conversations still listed (ConversationListIntegrationTest)
result: pass
source: automated
coverage_id: 13-03-D5

### 16. POST/GET /api/blocks: block returns 204 and GET lists the target with minimal identity (userId/displayName/photoThumbnail/blockedAt only)
expected: POST/GET /api/blocks: block returns 204 and GET lists the target with minimal identity (userId/displayName/photoThumbnail/blockedAt only)
result: pass
source: automated
coverage_id: 13-04-D1

### 17. self-block returns RFC 7807 400; block + unblock are idempotent (repeat 204); GET reflects add/remove
expected: self-block returns RFC 7807 400; block + unblock are idempotent (repeat 204); GET reflects add/remove
result: pass
source: automated
coverage_id: 13-04-D2

### 18. each caller sees only their own block list (IDOR-scoped to JWT principal)
expected: each caller sees only their own block list (IDOR-scoped to JWT principal)
result: pass
source: automated
coverage_id: 13-04-D3

### 19. DELETE /api/matches/{targetUserId} unmatches an active match (204) and returns 404 on repeat; bans no rediscovery
expected: DELETE /api/matches/{targetUserId} unmatches an active match (204) and returns 404 on repeat; bans no rediscovery
result: pass
source: automated
coverage_id: 13-04-D4

## Summary

total: 19
passed: 19
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
