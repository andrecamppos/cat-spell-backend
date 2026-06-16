---
phase: 04-discovery-matching
plan: 01
subsystem: api
tags: [discovery, swipe, match, postgis, native-query, cursor-pagination]

requires:
  - phase: 03-cat-profiles
    provides: CatProfile entity, CatProfileRepository, CatPhoto, UserProfile with location/preferences
provides:
  - Swipe entity + SwipeRepository with native PostGIS discovery query
  - Match entity + MatchRepository with unique pair constraint
  - DiscoveryService with cursor-paginated feed + swipe with match detection
  - MatchService with race-condition-safe match creation
  - DiscoveryController REST endpoints at /api/discovery
  - V8 + V9 Flyway migrations for swipes + matches tables
affects: [04-02-owner-profile-match-list]

tech-stack:
  added: []
  patterns: [setseed via EntityManager for stable random ordering, Base64 cursor encoding, LEAST/GREATEST for unique match pairs]

key-files:
  created:
    - src/main/resources/db/migration/V8__create_swipes_table.sql
    - src/main/resources/db/migration/V9__create_matches_table.sql
    - src/main/kotlin/com/catspell/api/discovery/model/Swipe.kt
    - src/main/kotlin/com/catspell/api/discovery/model/SwipeRepository.kt
    - src/main/kotlin/com/catspell/api/discovery/model/FeedProjection.kt
    - src/main/kotlin/com/catspell/api/discovery/model/DiscoveryDtos.kt
    - src/main/kotlin/com/catspell/api/discovery/service/DiscoveryService.kt
    - src/main/kotlin/com/catspell/api/discovery/controller/DiscoveryController.kt
    - src/main/kotlin/com/catspell/api/match/model/Match.kt
    - src/main/kotlin/com/catspell/api/match/model/MatchRepository.kt
    - src/main/kotlin/com/catspell/api/match/model/MatchDtos.kt
    - src/main/kotlin/com/catspell/api/match/service/MatchService.kt
    - src/test/kotlin/com/catspell/api/discovery/DiscoveryIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/discovery/SwipeMatchIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt

key-decisions:
  - "Native SQL query for discovery feed — JPA/JPQL cannot express PostGIS ST_DWithin + setseed + bidirectional preference matching"
  - "EntityManager.createNativeQuery for setseed — avoids @Modifying on SELECT workaround"
  - "Base64 cursor encoding with seed+offset — stable random ordering across pagination"
  - "LEAST/GREATEST unique index on matches — prevents duplicate matches regardless of user ID order"
  - "Match creation with try/catch on DataIntegrityViolationException — race-condition-safe"
  - "Bidirectional preference filtering — both users must match each other's gender preference"
  - "DuplicateSwipeException returns 409, SelfSwipeException returns 400, LocationRequired returns 422"

patterns-established:
  - "Native PostGIS query via SwipeRepository @Query(nativeQuery=true)"
  - "Cursor-based pagination with seed for stable random ordering"
  - "Mutual match detection: check reverse LIKE swipes after saving a LIKE"
  - "Match unique pair via LEAST/GREATEST DB constraint + service-level ordering"

requirements-completed: [DISC-01, DISC-03, DISC-04, DISC-05, DISC-06]

duration: ~25min
completed: 2026-06-15
---

# Plan 04-01 Summary: Discovery Feed + Swipe + Match Detection

**Complete discovery vertical slice: DB migrations → entities → native PostGIS feed → swipe with mutual match → controller → 20 integration tests**

## Performance

- **Tasks:** 9/9 complete
- **Files created:** 14
- **Files modified:** 2

## Accomplishments
- V8 migration: swipes table with unique(swiper_id, cat_id) + index on target_user_id
- V9 migration: matches table with unique(LEAST/GREATEST user pair) + indexes per user
- Swipe JPA entity with ManyToOne to User (swiper + target) and CatProfile
- Match JPA entity with ManyToOne to User (user1 + user2) and matchedAt timestamp
- SwipeRepository with native discovery feed query: PostGIS ST_DWithin, bidirectional gender/age preferences, excludes own cats + already-swiped, random() ordering
- DiscoveryService: cursor-paginated feed with setseed, swipe action with mutual match detection
- MatchService: createMatch with LEAST/GREATEST ordering + DataIntegrityViolation fallback
- DiscoveryController: GET /api/discovery/feed, POST /api/discovery/swipe
- 4 custom exceptions: LocationRequired (422), ProfileIncomplete (422), DuplicateSwipe (409), SelfSwipe (400)
- 11 discovery feed tests: cat-first data, exclude own, distance filter, exclude swiped, gender prefs, age prefs, missing location, incomplete profile, no-photo cats, auth, pagination
- 9 swipe/match tests: LIKE, PASS, mutual match, duplicate, self-swipe, non-existent cat, auth, no-match-on-pass

## Task Commits

1. **Tasks 1–9: Full discovery + swipe + match vertical slice** — `5f2059b`

## Deviations from Plan

- None — all tasks executed as planned

## Next Phase Readiness
- Swipe + Match entities ready for owner profile reveal and match list in Plan 04-02
- DiscoveryController ready for additional endpoints (owner profile)
- MatchService ready for match listing logic

---
*Phase: 04-discovery-matching*
*Completed: 2026-06-15*
