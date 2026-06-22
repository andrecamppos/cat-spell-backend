---
phase: 7
plan: 01
title: "Schema & Model Foundation"
status: complete
started: "2026-06-22T15:04:00Z"
completed: "2026-06-22T15:24:00Z"
---

## Summary

Implemented the structural foundation for the mixed discovery feed. All schema, model, DTO, and exception changes are in place.

## What was built

- **V13 Flyway migration** — `cat_id` made nullable in `swipes` table, old unique index replaced with two partial unique indexes (`idx_swipes_unique_cat` for cat swipes, `idx_swipes_unique_human` for human swipes)
- **Swipe entity** — `catProfile` changed from `CatProfile` to `CatProfile?` (nullable)
- **FeedProjection** — rewritten with `getType()` discriminator, nullable cat-specific getters (`getCatName()`, `getCatAge()`, etc.), and renamed user getters (`getUserId()`, `getDisplayName()`, `getUserPhotoThumbnail()`)
- **DiscoveryDtos** — `SwipeRequest` now has optional `catId`/`targetUserId` with exactly-one validation; `FeedItemResponse` has `type` field and nullable cat fields; `FeedResponse.cats` renamed to `cards`
- **SwipeRepository** — query aliases updated to match new FeedProjection; `existsBySwiperIdAndTargetUserIdAndCatProfileIsNull` added; exclusion query uses `target_user_id` instead of `cat_id`
- **DiscoveryService** — `getFeed()` uses new getter names; `swipe()` branches on catId vs targetUserId with validation, duplicate detection, and mutual match for both paths
- **Exception messages** — generalized to cat-agnostic ("Already swiped on this profile", "Cannot swipe on yourself")
- **Test updates** — all 12 existing DiscoveryIntegrationTest assertions updated for new JSON field names

## Self-Check: PASSED

- [x] V13 migration makes cat_id nullable with partial unique indexes
- [x] Swipe entity accepts null catProfile
- [x] FeedProjection supports both CAT and HUMAN card types
- [x] DTOs have type discriminator, nullable cat fields, renamed fields
- [x] Exception messages are cat-agnostic
- [x] Project compiles cleanly (`compileKotlin` exits 0)
- [x] All 165 tests pass

## Key files created

- `src/main/resources/db/migration/V13__make_swipe_cat_id_nullable.sql`

## Key files modified

- `src/main/kotlin/com/catspell/api/discovery/model/Swipe.kt`
- `src/main/kotlin/com/catspell/api/discovery/model/FeedProjection.kt`
- `src/main/kotlin/com/catspell/api/discovery/model/DiscoveryDtos.kt`
- `src/main/kotlin/com/catspell/api/discovery/model/SwipeRepository.kt`
- `src/main/kotlin/com/catspell/api/discovery/service/DiscoveryService.kt`
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt`
- `src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt`
- `src/test/kotlin/com/catspell/api/discovery/DiscoveryIntegrationTest.kt`

## Deviations

- **Scope expansion**: Updated SwipeRepository query aliases, DiscoveryService mapping/swipe logic, and test assertions beyond the 6 files listed in plan — necessary to maintain compilation and test passage. Plan 07-02 will further rewrite the repository query (UNION ALL) and add the human card endpoint.
