---
phase: 13-blocking-unmatch
plan: "01"
subsystem: database
tags: [postgres, flyway, jpa, hibernate, moderation, blocking]

requires:
  - phase: 09-matching
    provides: matches table + Match entity + SwipeRepository discovery feed
provides:
  - blocks pair table (V19) with idempotency UNIQUE, self-block CHECK, reverse index
  - matches soft-state teardown columns (V20) ended_at + ended_reason
  - Block JPA entity + BlockRepository with bidirectional existsBlockBetween predicate
  - BlockedUserResponse / BlockListResponse minimal-identity DTOs
  - discovery-feed bidirectional block exclusion in both UNION branches
  - MatchRepository active-only match list query
  - SwipeRepository.deleteSwipesBetween teardown primitive
affects: [13-02, 13-03, 13-04]

tech-stack:
  added: []
  patterns:
    - "Bidirectional pair predicate (existsBlockBetween) as single source of block truth (D-03)"
    - "Soft-state teardown via ended_at/ended_reason columns, never hard delete (D-07)"
    - "Feed block filter reads blocks table ONLY so unmatch never bans rediscovery (D-09)"

key-files:
  created:
    - src/main/resources/db/migration/V19__create_blocks_table.sql
    - src/main/resources/db/migration/V20__add_match_teardown_state.sql
    - src/main/kotlin/com/catspell/api/moderation/model/Block.kt
    - src/main/kotlin/com/catspell/api/moderation/model/BlockRepository.kt
    - src/main/kotlin/com/catspell/api/moderation/model/BlockedUserResponse.kt
  modified:
    - src/main/kotlin/com/catspell/api/match/model/Match.kt
    - src/main/kotlin/com/catspell/api/match/model/MatchRepository.kt
    - src/main/kotlin/com/catspell/api/discovery/model/SwipeRepository.kt

key-decisions:
  - "Conversation lock/hidden state derived from matches.ended_at (single source of truth) — no separate conversations column (D-07)"
  - "Reverse index idx_blocks_reverse keeps the bidirectional predicate indexed in both directions"

patterns-established:
  - "Pattern 1: moderation model package (com.catspell.api.moderation.model) for block domain"
  - "Pattern 2: append-only Flyway migrations (V19/V20) — never edit V1–V18"

requirements-completed: [MOD-01, MOD-02, MOD-05]

coverage:
  - id: D1
    description: "blocks table (V19) with idempotency UNIQUE, self-block CHECK, reverse index applies on schema boot"
    requirement: "MOD-01"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/match/MatchIntegrationTest.kt"
        status: pass
    human_judgment: false
  - id: D2
    description: "matches soft-state teardown columns (V20) ended_at/ended_reason added with no backfill; matches list is active-only"
    requirement: "MOD-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/match/MatchIntegrationTest.kt"
        status: pass
    human_judgment: false
  - id: D3
    description: "discovery feed excludes blocked pairs bidirectionally in both UNION branches (blocks-only); deleteSwipesBetween re-opens rediscovery"
    requirement: "MOD-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/discovery/DiscoveryIntegrationTest.kt"
        status: pass
    human_judgment: false
  - id: D4
    description: "Block entity + BlockRepository (bidirectional existsBlockBetween, idempotency probe, directional unblock delete, block-list query) compile and match V19"
    requirement: "MOD-01"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin"
        status: pass
    human_judgment: false

duration: 22min
completed: 2026-09-25
status: complete
---

# Phase 13 Plan 01: Blocking + Unmatch Data Foundation Summary

**blocks pair table (V19) + matches soft-state teardown (V20), Block entity/repository with bidirectional existsBlockBetween predicate, discovery-feed block exclusion in both branches, and deleteSwipesBetween teardown primitive**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-09-25T08:38:00Z
- **Completed:** 2026-09-25T09:00:00Z
- **Tasks:** 3
- **Files modified:** 8 (5 created, 3 modified)

## Accomplishments
- V19 creates the `blocks` pair table with `UNIQUE(blocker_id, blocked_id)` idempotency, `CHECK (blocker_id <> blocked_id)` self-block backstop, FKs `ON DELETE CASCADE`, and a reverse index `idx_blocks_reverse` on `(blocked_id, blocker_id)`.
- V20 adds nullable `ended_at TIMESTAMPTZ` + `ended_reason VARCHAR(20)` to `matches` (soft-state teardown, no backfill).
- `Block` entity + `BlockRepository` expose the bidirectional `existsBlockBetween(a, bId)` predicate (single source of the block check, D-03), plus idempotency probe, directional unblock delete, and block-list query.
- `BlockedUserResponse`/`BlockListResponse` minimal-identity DTOs (userId, displayName, photoThumbnail, blockedAt — D-13).
- `Match` gains `endedAt`/`endedReason`; `findByUser1IdOrUser2Id` is now active-only; `findByUserPair` still returns any match for reactivation.
- `findDiscoveryFeed` excludes blocked pairs bidirectionally in BOTH UNION branches (reads blocks ONLY, D-09); `deleteSwipesBetween` clears both-direction swipe rows (D-08).

## Task Commits

1. **Task 1: V19 blocks + V20 match teardown migrations** - `d9f9329` (feat)
2. **Task 2: Block entity, BlockRepository, block-list DTOs** - `ead33e0` (feat)
3. **Task 3: Match soft-state, active-only list, feed block filter, deleteSwipesBetween** - `71c2838` (feat)

## Files Created/Modified
- `src/main/resources/db/migration/V19__create_blocks_table.sql` - blocks pair table + reverse index + self-block CHECK
- `src/main/resources/db/migration/V20__add_match_teardown_state.sql` - matches ended_at/ended_reason columns
- `src/main/kotlin/com/catspell/api/moderation/model/Block.kt` - Block JPA entity
- `src/main/kotlin/com/catspell/api/moderation/model/BlockRepository.kt` - bidirectional predicate + block-list queries
- `src/main/kotlin/com/catspell/api/moderation/model/BlockedUserResponse.kt` - block-list DTOs
- `src/main/kotlin/com/catspell/api/match/model/Match.kt` - endedAt/endedReason soft-state fields
- `src/main/kotlin/com/catspell/api/match/model/MatchRepository.kt` - active-only match list query
- `src/main/kotlin/com/catspell/api/discovery/model/SwipeRepository.kt` - feed block filter + deleteSwipesBetween

## Decisions Made
- Conversation lock/hidden state is derived from `matches.ended_at` rather than a dedicated conversations column — single source of truth for teardown (D-07).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. (Shutdown-hook `drop table` log noise during test teardown is expected Hibernate behavior against the Flyway-managed schema, not a test failure — all tests passed.)

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Schema + repository layer ready for Plan 13-02's BlockService/MatchService teardown wiring.
- `existsBlockBetween` and `deleteSwipesBetween` are the primitives Wave 2/3 consume.

---
*Phase: 13-blocking-unmatch*
*Completed: 2026-09-25*
