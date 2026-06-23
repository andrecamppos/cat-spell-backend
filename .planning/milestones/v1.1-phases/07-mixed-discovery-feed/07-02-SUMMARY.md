---
phase: 7
plan: 02
title: "Mixed Feed Query, Swipe Logic & Integration Tests"
status: complete
started: "2026-06-22T16:25:00Z"
completed: "2026-06-22T16:50:00Z"
---

## Summary

Implemented UNION ALL mixed feed query, human card detail endpoint, and 15 new integration tests covering all mixed feed scenarios.

## What was built

- **UNION ALL feed query** — Cat branch uses ROW_NUMBER for one-cat-per-user (first created); Human branch selects catless users with complete profiles. Both branches share identical distance/gender/age/completeness filters. Exclusion uses `target_user_id` in both branches. Wrapped in outer random ORDER BY with pagination.
- **`getUserProfile()` in DiscoveryService** — Returns `OwnerProfileResponse` for any user by ID (catless users get empty cats list).
- **`GET /api/discovery/users/{userId}/profile`** — New endpoint in DiscoveryController for human card detail.
- **`setupCatlessUser` test helper** — Creates user with profile, location, and photo but no cat.
- **`extractUserId` test helper** — Calls `/api/auth/me` to get userId from token.
- **15 new integration tests**:
  - Human card in mixed feed (type=HUMAN, null cat fields)
  - Cat card with type discriminator (type=CAT)
  - Feed mixes both CAT and HUMAN cards
  - One card per user for multi-cat owner
  - Swipe on human card with targetUserId
  - Swipe rejects both catId and targetUserId
  - Swipe rejects neither catId nor targetUserId
  - Human card excluded after swipe
  - Cat card excluded by owner after swipe
  - Mutual match between catless users
  - Cross-type mutual match (cat owner ↔ catless user)
  - Human card detail endpoint returns profile
  - Human card detail requires authentication
  - Self swipe on human card returns 400
  - Duplicate human swipe returns 409

## Self-Check: PASSED

- [x] UNION query produces mixed feed with CAT and HUMAN cards
- [x] Cat cards show first-created cat per user (ROW_NUMBER)
- [x] Human cards show catless users with complete profiles
- [x] Swipe validates exactly one of catId/targetUserId
- [x] Human swipe saves Swipe with null catProfile
- [x] Mutual match works cat↔human and human↔human
- [x] Per-owner exclusion works for all card types
- [x] Human card detail endpoint works
- [x] All existing tests updated and passing
- [x] 15 new integration tests pass
- [x] Full test suite green: 180/180

## Key files modified

- `src/main/kotlin/com/catspell/api/discovery/model/SwipeRepository.kt`
- `src/main/kotlin/com/catspell/api/discovery/service/DiscoveryService.kt`
- `src/main/kotlin/com/catspell/api/discovery/controller/DiscoveryController.kt`
- `src/test/kotlin/com/catspell/api/discovery/DiscoveryIntegrationTest.kt`

## Deviations

- Used unique locations (Sydney) for `feed returns cat profiles with cat-first data` test to prevent feed contamination from HUMAN cards created by other tests at the default NYC location.
