---
phase: 13-blocking-unmatch
plan: "04"
subsystem: api
tags: [kotlin, spring, rest, moderation, blocking, unmatch, controller]

requires:
  - phase: 13-blocking-unmatch (13-02)
    provides: BlockService (block/unblock/getBlockList), MatchService.unmatch
provides:
  - BlockController — POST/DELETE /api/blocks/{targetUserId} + GET /api/blocks
  - MatchController DELETE /api/matches/{targetUserId} unmatch endpoint
  - BlockEndpointIntegrationTest (204/400/404, idempotency, IDOR, minimal identity)
affects: []

tech-stack:
  added: []
  patterns:
    - "Actor is always the JWT principal via extractUserId — never a client-supplied id (IDOR)"
    - "block/unblock return 204 no-body — no block disclosure to the target (D-02)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/moderation/controller/BlockController.kt
  modified:
    - src/main/kotlin/com/catspell/api/match/controller/MatchController.kt
  created_tests:
    - src/test/kotlin/com/catspell/api/moderation/BlockEndpointIntegrationTest.kt

key-decisions:
  - "Unmatch endpoint mirrors block's by-userId shape: DELETE /api/matches/{targetUserId} (Claude's-discretion, symmetric with /api/blocks/{targetUserId})"

patterns-established:
  - "Pattern 1: moderation controller package (com.catspell.api.moderation.controller)"

requirements-completed: [MOD-01, MOD-04, MOD-05]

coverage:
  - id: D1
    description: "POST/GET /api/blocks: block returns 204 and GET lists the target with minimal identity (userId/displayName/photoThumbnail/blockedAt only)"
    requirement: "MOD-01"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEndpointIntegrationTest.kt#block then list shows minimal identity"
        status: pass
    human_judgment: false
  - id: D2
    description: "self-block returns RFC 7807 400; block + unblock are idempotent (repeat 204); GET reflects add/remove"
    requirement: "MOD-01"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEndpointIntegrationTest.kt#self block returns 400 problem body"
        status: pass
    human_judgment: false
  - id: D3
    description: "each caller sees only their own block list (IDOR-scoped to JWT principal)"
    requirement: "MOD-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEndpointIntegrationTest.kt#each caller sees only their own block list"
        status: pass
    human_judgment: false
  - id: D4
    description: "DELETE /api/matches/{targetUserId} unmatches an active match (204) and returns 404 on repeat; bans no rediscovery"
    requirement: "MOD-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/BlockEndpointIntegrationTest.kt#unmatch returns 204 then 404 on repeat"
        status: pass
    human_judgment: false

duration: 18min
completed: 2026-09-25
status: complete
---

# Phase 13 Plan 04: Moderation HTTP Surface Summary

**BlockController (POST/DELETE /api/blocks/{targetUserId} + GET /api/blocks) and a DELETE /api/matches/{targetUserId} unmatch endpoint, all JWT-principal-scoped, with 204/400/404 semantics proven by an endpoint integration test**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-09-25T10:20:00Z
- **Completed:** 2026-09-25T10:34:00Z
- **Tasks:** 3
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments
- `BlockController`: POST/DELETE `/api/blocks/{targetUserId}` return 204 (block idempotent, self → 400 via handler); GET `/api/blocks` returns the caller's own minimal-identity list. Actor is always the JWT principal (IDOR-scoped); block/unblock return no body (D-02).
- `MatchController` gains DELETE `/api/matches/{targetUserId}` delegating to `matchService.unmatch(principal, target)` — 204 on success, 404 when no active match, no rediscovery ban (MOD-05, D-06/D-09).
- `BlockEndpointIntegrationTest` proves 204/400/404, block+unblock idempotency, minimal-identity block list, per-caller IDOR isolation, and the unmatch 204-then-404 sequence.

## Task Commits

1. **Task 1: BlockController (block/unblock/list)** - `8316c06` (feat)
2. **Task 2: MatchController unmatch endpoint** - `480979f` (feat)
3. **Task 3: BlockEndpointIntegrationTest** - `14c6107` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/moderation/controller/BlockController.kt` - block/unblock/list endpoints
- `src/main/kotlin/com/catspell/api/match/controller/MatchController.kt` - unmatch endpoint
- `src/test/kotlin/com/catspell/api/moderation/BlockEndpointIntegrationTest.kt` - HTTP-level proof

## Decisions Made
- Unmatch endpoint uses the by-userId path shape (`DELETE /api/matches/{targetUserId}`) symmetric with `/api/blocks/{targetUserId}`.

## Deviations from Plan

None - plan executed exactly as written.

## TDD Gate Compliance
Task 3 was `tdd="true"` and is a pure test-authoring task (no production source), committed as `test(13-04)` = `14c6107`. The endpoints it asserts were implemented in Tasks 1–2 (`feat` commits). MVP+TDD gate is inactive for this phase.

## Issues Encountered
None. (Testcontainers/Hibernate schema-drop noise on context shutdown is expected — all 6 endpoint tests pass.)

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 13 complete — all four moderation operations (block, unblock, view list, unmatch) are exposed over HTTP and enforced across every read/send surface. Ready for phase verification and the next v2.2 phase (14 — Report a User).

---
*Phase: 13-blocking-unmatch*
*Completed: 2026-09-25*
