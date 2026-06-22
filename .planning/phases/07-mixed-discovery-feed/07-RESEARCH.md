# Phase 7: Mixed Discovery Feed — Research

**Researched:** 2026-06-22
**Status:** Complete

## Executive Summary

Phase 7 transforms the discovery feed from cat-only to mixed (cat + human cards). The codebase is well-structured with clear separation between discovery, matching, and chat domains. All changes are confined to the `discovery` package + one Flyway migration + exception message updates. MatchService already operates on user IDs, requiring zero changes. The highest-risk area is the UNION query migration — the existing native SQL query is complex (distance, gender, age, exclusions) and must be correctly duplicated for the human card branch.

## Codebase Analysis

### Current Architecture

**Feed flow:** `DiscoveryController.getFeed()` → `DiscoveryService.getFeed()` → `SwipeRepository.findDiscoveryFeed()` (native SQL) → `FeedProjection` → `FeedItemResponse` → `FeedResponse`

**Swipe flow:** `DiscoveryController.swipe()` → `DiscoveryService.swipe()` → looks up `CatProfile` by catId → gets owner → saves `Swipe` → checks reverse likes via `findBySwiperIdAndTargetUserIdAndAction` → calls `MatchService.createMatch(userId, ownerId)` if mutual

### Files Requiring Modification

| File | Current State | Required Changes |
|------|--------------|------------------|
| `SwipeRepository.kt` | Single `findDiscoveryFeed` query on `cat_profiles` only; exclusion via `NOT EXISTS (swipes WHERE cat_id = cp.id)` | UNION query: cat card branch (one per user, first-created cat via `ROW_NUMBER()`) + human card branch (catless users); exclusion via `NOT EXISTS (swipes WHERE target_user_id = owner_id)` |
| `FeedProjection.kt` | All getters non-null except `breed`, `bio`, photo thumbnails | Add `getType(): String` discriminator; make `getCatId()`, `getName()`, `getAge()`, `getAgeUnit()` return nullable types |
| `DiscoveryDtos.kt` | `SwipeRequest(catId: UUID, action)`, `FeedItemResponse` with all cat fields, `FeedResponse(cats: List)` | `SwipeRequest` with optional `catId?`/`targetUserId?` + custom validator; `FeedItemResponse` gets `type` field + nullable cat fields; `FeedResponse.cards` replaces `.cats` |
| `Swipe.kt` | `catProfile: CatProfile` with `@JoinColumn(nullable = false)` | Make nullable: `catProfile: CatProfile?` with `@JoinColumn(nullable = true)` |
| `DiscoveryService.kt` | `swipe()` requires catId, resolves owner; `getFeed()` maps all-cat projections | `swipe()` branches on catId vs targetUserId; `getFeed()` handles polymorphic FeedProjection; add `getUserProfile(requesterId, userId)` for human card detail |
| `DiscoveryController.kt` | 3 endpoints: feed, owner-profile, swipe | Add `GET /api/discovery/users/{userId}/profile` endpoint |
| `Exceptions.kt` | `DuplicateSwipeException("Already swiped on this cat")`, `SelfSwipeException("Cannot swipe on your own cat")` | Generalize messages: "Already swiped on this profile", "Cannot swipe on yourself" |
| `GlobalExceptionHandler.kt` | Handles `DuplicateSwipeException` with "Already swiped on this cat" | Update default messages to match generalized exception messages |

### New Files Required

| File | Purpose |
|------|---------|
| `V13__make_swipe_cat_id_nullable.sql` | Flyway migration: `ALTER TABLE swipes ALTER COLUMN cat_id DROP NOT NULL`; drop `idx_swipes_unique`; create two partial unique indexes |

### Files NOT Requiring Changes

| File | Why |
|------|-----|
| `MatchService.kt` | `createMatch(userId1, userId2)` already works on user IDs — no cat dependency |
| `MatchRepository.kt` | Match queries are user-based, no cat FK |
| `User.kt`, `UserRepository.kt` | No changes needed |
| `CatProfile.kt`, `CatProfileRepository.kt` | Cat creation remains unchanged; no enforcement to remove |
| Chat domain (all files) | Conversations/messages are match-based, independent of discovery card type |

## Technical Approach

### 1. Flyway Migration V13

```sql
-- Make cat_id nullable (human swipes have no cat)
ALTER TABLE swipes ALTER COLUMN cat_id DROP NOT NULL;

-- Drop the old unique index (swiper_id, cat_id) — covers only cat swipes
DROP INDEX idx_swipes_unique;

-- Partial unique index for cat swipes: one swipe per (swiper, cat)
CREATE UNIQUE INDEX idx_swipes_unique_cat
    ON swipes(swiper_id, cat_id)
    WHERE cat_id IS NOT NULL;

-- Partial unique index for human swipes: one swipe per (swiper, target_user)
CREATE UNIQUE INDEX idx_swipes_unique_human
    ON swipes(swiper_id, target_user_id)
    WHERE cat_id IS NULL;
```

**Risk:** Existing swipes all have `cat_id` set, so the nullable migration is safe. Partial indexes are well-supported in PostgreSQL.

### 2. UNION Feed Query

The core query change replaces the single `SELECT FROM cat_profiles` with a `UNION ALL` of two branches:

**Cat card branch:** Same as current query but with `ROW_NUMBER() OVER (PARTITION BY cp.user_id ORDER BY cp.created_at ASC) = 1` to pick one cat per user (first-created). Adds `'CAT' AS type` discriminator. Changes exclusion from `NOT EXISTS (swipes WHERE cat_id = cp.id)` to `NOT EXISTS (swipes WHERE target_user_id = cp.user_id)` (per-owner exclusion per D-03).

**Human card branch:** Selects from `user_profiles` directly for users with zero cats (`NOT EXISTS (SELECT 1 FROM cat_profiles WHERE user_id = up.user_id)`). Cat-specific fields (`cat_id`, `name`, `age`, etc.) are NULL. User photo replaces cat photo. Same distance/gender/age/completeness filters. Adds `'HUMAN' AS type`.

Both branches share:
- Distance filter: `ST_DWithin(up.location::geography, ...)`
- Gender preference: bidirectional check
- Age range: bidirectional check
- Profile completeness: `display_name`, `bio`, `date_of_birth`, `gender`, `location` all NOT NULL
- User photo: `EXISTS (SELECT 1 FROM user_photos WHERE user_id AND status = 'ACTIVE')`
- Self-exclusion: `user_id != :requesterId`
- Already-swiped exclusion: `NOT EXISTS (swipes WHERE swiper_id = :requesterId AND target_user_id = up.user_id)`

**Pagination:** The UNION is wrapped and `ORDER BY random() LIMIT :pageSize OFFSET :offset` is applied to the outer query. The existing `setseed()` call before the query ensures cursor stability across pages.

**Key difference from current query:** Cat card exclusion changes from per-cat (`swipes.cat_id = cp.id`) to per-owner (`swipes.target_user_id = cp.user_id`). This matches D-03: swiping on any card excludes the entire person, not just one cat.

### 3. Polymorphic FeedProjection

```kotlin
interface FeedProjection {
    fun getType(): String          // "CAT" or "HUMAN"
    fun getUserId(): UUID          // owner_id for CAT, user_id for HUMAN
    fun getDisplayName(): String   // owner display name for both
    fun getUserPhotoThumbnail(): String?
    fun getDistanceKm(): Int
    // Nullable cat-specific fields (null for HUMAN cards)
    fun getCatId(): UUID?
    fun getCatName(): String?
    fun getCatAge(): Int?
    fun getCatAgeUnit(): String?
    fun getCatBreed(): String?
    fun getCatBio(): String?
    fun getCatPhotoThumbnail(): String?
}
```

**Note:** Spring Data projection interfaces support nullable return types for native query columns that may be NULL. The UNION query aliases must match getter names (minus `get` prefix, following Spring conventions).

### 4. SwipeRequest Validation

Per D-09, custom validation ensures exactly one of `catId`/`targetUserId` is set:

```kotlin
data class SwipeRequest(
    val catId: UUID? = null,
    val targetUserId: UUID? = null,
    @field:NotNull
    @field:Pattern(regexp = "LIKE|PASS")
    val action: String
)
```

Validation options:
- **Option A: Custom `@Constraint` annotation** — Cleanest, follows existing Spring validation pattern. Class-level constraint validator checks `catId XOR targetUserId`.
- **Option B: Service-level validation** — `require((catId != null) xor (targetUserId != null))` in `DiscoveryService.swipe()`. Simpler, throws `IllegalArgumentException` caught by existing handler.

**Recommendation:** Option B (service-level) — simpler, consistent with how `LocationRequiredException` is already thrown from service layer. Custom constraint annotations add boilerplate for a single-use validation.

### 5. Swipe Branching in DiscoveryService

`swipe()` method branches on which field is provided:

- **catId provided:** Same as current flow — look up CatProfile, get owner, save Swipe with `catProfile = cat`.
- **targetUserId provided:** Look up User directly, save Swipe with `catProfile = null`. Self-swipe check uses `targetUserId == userId` directly.

Duplicate detection also branches:
- Cat swipe: `existsBySwiperIdAndCatProfileId(userId, catId)` (existing)
- Human swipe: Need new `existsBySwiperIdAndTargetUserIdAndCatProfileIsNull(userId, targetUserId)` to avoid matching cat swipes to the same user

Mutual match detection remains unified: `findBySwiperIdAndTargetUserIdAndAction(targetUserId, userId, "LIKE")` already queries by target user ID regardless of cat/human. This handles cross-type matching naturally (D-11).

### 6. Human Card Detail Endpoint

New endpoint `GET /api/discovery/users/{userId}/profile`:

Response shape mirrors `OwnerProfileResponse` (same fields: userId, displayName, bio, age, gender, photos, cats). In fact, this method can delegate to the existing `getOwnerProfile` logic minus the cat lookup — or better, extract a shared `getUserProfileDetail(requesterId, userId)` that both endpoints call.

The existing `OwnerProfileResponse` already includes a `cats` list, so for human cards this will just be empty. No new DTO needed.

### 7. Exception Message Updates

Two exceptions need generalized messages:
- `DuplicateSwipeException`: "Already swiped on this cat" → "Already swiped on this profile"
- `SelfSwipeException`: "Cannot swipe on your own cat" → "Cannot swipe on yourself"

These are default messages in `Exceptions.kt`. The `GlobalExceptionHandler` uses `ex.message` which falls back to the default. Changing the defaults is sufficient.

## Validation Architecture

### Test Strategy

**Existing tests (13 discovery tests):** All use `setupCompleteUser()` which creates a cat + photos. All check `json["cats"]` field (becomes `json["cards"]`). Tests must be updated for:
1. DTO field rename: `cats` → `cards`
2. New `type` field in feed items
3. Feed items now have different shapes (cat vs human)

**New test scenarios required:**

| Test | What it validates |
|------|-------------------|
| Human card appears in feed | Catless user with complete profile + user photo shows as HUMAN card |
| Human card has null cat fields | HUMAN card's catId, catName, breed, etc. are null |
| CAT card has all fields populated | Cat-owning user appears as CAT card with all fields |
| Feed mixes both types | Feed contains both CAT and HUMAN cards |
| One card per user (multi-cat) | User with 2+ cats appears as single CAT card (first-created cat) |
| Auto-switch on cat deletion | User who deletes all cats appears as HUMAN card in next feed |
| Catless user swipe (targetUserId) | Swipe with targetUserId works, catId is null |
| Cat swipe still works (catId) | Swipe with catId works as before |
| Exactly-one validation | Swipe with both catId AND targetUserId → 400 |
| Neither field validation | Swipe with neither catId nor targetUserId → 400 |
| Human-to-human mutual match | Two catless users like each other → match created |
| Cat-to-human cross-match | Cat owner likes catless user and vice versa → match |
| Human card detail endpoint | GET /api/discovery/users/{userId}/profile returns correct data |
| Human card excluded after swipe | Swiping on human card excludes that user from future feeds |
| Per-owner exclusion for cat cards | Swiping on cat card excludes the owner (not just that cat) from feed |

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| UNION query performance | Medium | Both branches use same indexes (user_profiles GIST for distance). UNION ALL avoids dedup overhead. Monitor query plan with EXPLAIN ANALYZE. |
| ROW_NUMBER subquery for one-cat-per-user | Low | Small per-user cat counts (max 5). Window function on cat_profiles is cheap. |
| Partial unique index correctness | Low | Well-tested PostgreSQL feature. Two indexes cover the complete uniqueness constraint space. |
| Cursor pagination with UNION | Low | `setseed()` + `random()` + `OFFSET` pattern is the same — just wraps the UNION. Cursor stability preserved. |
| Breaking change to FeedResponse.cats → .cards | Low | Pre-launch, no external clients. Accepted in D-06. |
| Existing test breakage | Medium | All 13 discovery tests reference `json["cats"]` — must update systematically. Test helper `setupCompleteUser` creates a cat, which is fine for CAT card tests. Need new helper for catless user setup. |

## Dependencies

- **Phase 4 (Discovery & Matching):** Direct code dependency — modifying the core discovery module built in Phase 4.
- **Phase 6 (API Polish & Integration Tests):** Test infrastructure dependency — extending the test suite built in Phase 6.
- **No external dependencies:** All changes use existing Spring Boot, JPA, PostgreSQL, and Flyway capabilities.

---
*Research completed: 2026-06-22*
*Source: Codebase analysis of discovery module, database schema, and test suite*
