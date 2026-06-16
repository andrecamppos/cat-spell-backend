# Phase 4: Discovery & Matching — Research

**Researched:** 2026-06-14
**Confidence:** HIGH
**Phase Goal:** Deliver the cat-first discovery feed with geolocation filtering, swipe actions, seen-profile tracking, and mutual match detection.

## Executive Summary

Phase 4 builds the core product loop — the cat-first discovery feed, swipe actions (like/pass), and mutual match detection. The codebase already has all prerequisites in place: PostGIS enabled (V3 migration), GiST spatial index on `user_profiles.location` (V4), complete user profiles with dating preferences/location/photos, and cat profiles with photos. The primary technical challenges are: (1) crafting an efficient native SQL feed query that joins across 4 tables with PostGIS spatial filtering + bidirectional preference matching + already-seen exclusion, (2) cursor-based pagination with randomized ordering, and (3) race-safe mutual match detection.

## Schema Design

### V8: Swipes Table

```sql
CREATE TABLE swipes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    swiper_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cat_id          UUID NOT NULL REFERENCES cat_profiles(id) ON DELETE CASCADE,
    target_user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action          VARCHAR(10) NOT NULL CHECK (action IN ('LIKE', 'PASS')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Prevent duplicate swipes on same cat
CREATE UNIQUE INDEX idx_swipes_unique ON swipes(swiper_id, cat_id);
-- Fast lookup for match detection: "did target_user like any of my cats?"
CREATE INDEX idx_swipes_target_action ON swipes(target_user_id, action) WHERE action = 'LIKE';
-- Fast lookup for feed exclusion: "which cats has this user already seen?"
CREATE INDEX idx_swipes_swiper ON swipes(swiper_id);
```

**Key design:** Per CONTEXT.md D-07, swipes record the `target_user_id` (cat's owner) alongside `cat_id`. The `cat_id` drives feed exclusion (don't show the same cat twice), while `target_user_id` drives match detection (did the other user like any of MY cats?).

### V9: Matches Table

```sql
CREATE TABLE matches (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user1_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user2_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    matched_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Per D-12: one match per user pair. Use LEAST/GREATEST to normalize ordering
CREATE UNIQUE INDEX idx_matches_pair ON matches(LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id));
CREATE INDEX idx_matches_user1 ON matches(user1_id);
CREATE INDEX idx_matches_user2 ON matches(user2_id);
```

**Race condition mitigation (PITFALLS.md #5):** The `LEAST/GREATEST` unique index ensures that regardless of which user triggers the match first, only one match row is created. The insert uses `INSERT INTO matches ... ON CONFLICT DO NOTHING RETURNING *` — the "loser" of the race simply gets no row back and the existing match is returned via a follow-up SELECT.

## Feed Query Architecture

### The Discovery Query

The feed query is the most complex piece in this phase. It must:
1. Find cat profiles belonging to OTHER users (D-16: exclude own cats)
2. Filter by distance using PostGIS `ST_DWithin` (D-13)
3. Apply bidirectional preference matching — age range + gender (D-13)
4. Exclude cats already swiped on by the requesting user (D-05)
5. Only include cats from users with complete profiles (D-15)
6. Include only cats with at least 1 ACTIVE photo (canonical ref: Phase 3 D-06)
7. Return cat data + minimal owner hint (D-01)
8. Random ordering within the distance radius (D-03)
9. Cursor-based pagination, 20 per page (D-02)

### Native SQL Approach

This query requires a native SQL `@Query` in the repository because:
- PostGIS functions (`ST_DWithin`, `ST_Distance`, `ST_SetSRID`, `ST_MakePoint`) are not supported by JPQL
- The bidirectional preference matching (checking both directions) needs complex WHERE clauses
- The NOT EXISTS subquery against swipes is most efficient in native SQL
- Random ordering with a session seed (`setseed()` + `random()`) is PostgreSQL-specific

```sql
SELECT cp.id AS cat_id, cp.name, cp.age, cp.age_unit, cp.breed, cp.bio,
       cp.user_id AS owner_id,
       up.display_name AS owner_display_name,
       (SELECT cph.thumbnail_s3_key FROM cat_photos cph
        WHERE cph.cat_profile_id = cp.id AND cph.status = 'ACTIVE'
        ORDER BY cph.display_order ASC LIMIT 1) AS cat_photo_thumbnail,
       (SELECT uph.thumbnail_s3_key FROM user_photos uph
        WHERE uph.user_id = cp.user_id AND uph.status = 'ACTIVE'
        ORDER BY uph.display_order ASC LIMIT 1) AS owner_photo_thumbnail,
       ROUND(ST_Distance(up.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000) AS distance_km
FROM cat_profiles cp
JOIN user_profiles up ON up.user_id = cp.user_id
JOIN user_profiles requester ON requester.user_id = :requesterId
WHERE cp.user_id != :requesterId
  -- Distance filter (D-13)
  AND ST_DWithin(up.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, requester.max_distance_km * 1000)
  -- Bidirectional gender preference (D-13)
  AND (requester.gender_preference = 'EVERYONE' OR requester.gender_preference = up.gender)
  AND (up.gender_preference = 'EVERYONE' OR up.gender_preference = requester.gender)
  -- Bidirectional age filter (D-13)
  AND EXTRACT(YEAR FROM AGE(CURRENT_DATE, up.date_of_birth)) BETWEEN requester.age_min AND requester.age_max
  AND EXTRACT(YEAR FROM AGE(CURRENT_DATE, requester.date_of_birth)) BETWEEN up.age_min AND up.age_max
  -- Profile completeness gate (D-15)
  AND up.display_name IS NOT NULL AND up.bio IS NOT NULL
  AND up.date_of_birth IS NOT NULL AND up.gender IS NOT NULL
  AND up.location IS NOT NULL
  AND EXISTS (SELECT 1 FROM user_photos uph2 WHERE uph2.user_id = cp.user_id AND uph2.status = 'ACTIVE')
  -- Cat must have at least 1 active photo
  AND EXISTS (SELECT 1 FROM cat_photos cph2 WHERE cph2.cat_profile_id = cp.id AND cph2.status = 'ACTIVE')
  -- Exclude already-swiped cats (D-05, D-06)
  AND NOT EXISTS (SELECT 1 FROM swipes s WHERE s.swiper_id = :requesterId AND s.cat_id = cp.id)
ORDER BY random()
LIMIT :pageSize OFFSET :offset
```

### N+1 Mitigation (PITFALLS.md #1)

The feed query uses correlated subqueries to fetch the first cat photo and first owner photo inline, avoiding lazy-loading N+1 issues entirely. All data comes back in a single query mapped to a flat projection DTO — no entity graph traversal needed.

### Pagination Strategy (D-02)

**Approach: Seed-based random with offset pagination.**

Cursor-based keyset pagination doesn't naturally compose with `random()` ordering. Instead:
- Client sends a `seed` parameter (float 0-1) on the first request; the server stores it in the response
- Server calls `SELECT setseed(:seed)` before the feed query to get deterministic random ordering within a session
- Subsequent pages use `OFFSET` against the seeded random to get stable ordering
- New session = new seed = reshuffled feed

This is simpler than materializing a randomized view and adequate for v1 scale. The `seed` travels in the cursor response, not as a separate parameter.

**Cursor response format:**
```json
{
  "cats": [...],
  "cursor": {
    "seed": 0.42,
    "offset": 20,
    "hasMore": true
  }
}
```

## Match Detection Flow

### On LIKE Swipe

1. Insert swipe row: `INSERT INTO swipes (swiper_id, cat_id, target_user_id, action) VALUES (:me, :catId, :ownerId, 'LIKE')`
2. Check reverse: `SELECT EXISTS (SELECT 1 FROM swipes WHERE swiper_id = :ownerId AND target_user_id = :me AND action = 'LIKE')`
3. If reverse exists → create match: `INSERT INTO matches (user1_id, user2_id) VALUES (LEAST(:me, :ownerId), GREATEST(:me, :ownerId)) ON CONFLICT DO NOTHING RETURNING *`
4. Return `{matched: true, matchId: ...}` or `{matched: false}` per D-08

### On PASS Swipe

1. Insert swipe row with action = 'PASS'
2. No match check needed
3. Return `{matched: false}`

## API Endpoints

### Discovery Domain (`/api/discovery`)

| Method | Path | Description | Auth | Req |
|--------|------|-------------|------|-----|
| `GET` | `/api/discovery/feed` | Cat-first discovery feed | JWT | DISC-01, DISC-04, DISC-05 |
| `POST` | `/api/discovery/swipe` | Like or pass on a cat | JWT | DISC-03, DISC-06 |
| `GET` | `/api/discovery/cats/{catId}/owner` | View cat's owner profile | JWT | DISC-02 |

### Match Domain (`/api/matches`)

| Method | Path | Description | Auth | Req |
|--------|------|-------------|------|-----|
| `GET` | `/api/matches` | List user's matches | JWT | DISC-07 |

### Feed Request Parameters

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `cursor` | String | No | null | Base64-encoded cursor from previous response |
| `pageSize` | Int | No | 20 | Items per page (max 50) |

### Swipe Request Body

```json
{
  "catId": "uuid",
  "action": "LIKE" | "PASS"
}
```

### Swipe Response

```json
{
  "swipeId": "uuid",
  "matched": true,
  "matchId": "uuid-or-null"
}
```

### Owner Profile Response (D-01, D-02)

Returns the cat's owner profile accessible from the cat detail view:
```json
{
  "userId": "uuid",
  "displayName": "string",
  "bio": "string",
  "age": 28,
  "gender": "FEMALE",
  "photos": [
    { "s3Key": "...", "thumbnailS3Key": "..." }
  ],
  "cats": [
    { "id": "uuid", "name": "Whiskers", "age": 3, "breed": "Siamese", "photoThumbnail": "..." }
  ]
}
```

### Match List Response (D-10)

```json
{
  "matches": [
    {
      "matchId": "uuid",
      "matchedAt": "2026-06-14T...",
      "otherUser": {
        "userId": "uuid",
        "displayName": "Jane",
        "photoThumbnail": "..."
      },
      "otherUserCats": [
        { "name": "Luna", "photoThumbnail": "..." }
      ]
    }
  ]
}
```

## Package Structure

Following established domain-first vertical slices:

```
com.catspell.api.discovery/
├── controller/
│   └── DiscoveryController.kt     — GET /feed, POST /swipe, GET /cats/{id}/owner
├── service/
│   └── DiscoveryService.kt        — Feed query, swipe logic, owner profile
├── model/
│   ├── Swipe.kt                   — JPA entity (regular class, not data class)
│   ├── SwipeRepository.kt         — JPA repository + native feed query
│   ├── DiscoveryDtos.kt           — Request/response DTOs (data classes)
│   └── FeedProjection.kt          — Interface projection for native query results
└── (no separate config — uses existing SecurityConfig)

com.catspell.api.match/
├── controller/
│   └── MatchController.kt         — GET /matches
├── service/
│   └── MatchService.kt            — Match creation, match listing
└── model/
    ├── Match.kt                   — JPA entity
    ├── MatchRepository.kt         — JPA repository
    └── MatchDtos.kt               — Response DTOs
```

## Error Handling

New exceptions following existing pattern in `Exceptions.kt`:

| Exception | HTTP Status | Trigger |
|-----------|-------------|---------|
| `LocationRequiredException` | 400 | User has no location set (D-14) |
| `ProfileIncompleteException` | 400 | User's profile is not complete |
| `DuplicateSwipeException` | 409 | User already swiped on this cat |
| `SelfSwipeException` | 400 | User tries to swipe on own cat |
| `CatNotFoundException` | 404 | Cat ID doesn't exist |
| `AlreadyMatchedException` | 409 | Users are already matched (edge case) |

All return RFC 7807 `ProblemDetail` via `GlobalExceptionHandler`.

## Security Configuration

Add permit rules in `SecurityConfig.securityFilterChain`:
- All new endpoints require authentication (`.anyRequest().authenticated()` already handles this)
- No new permit rules needed — discovery and match endpoints should be JWT-protected
- The existing `SecurityConfig` pattern with `.anyRequest().authenticated()` already covers this

## Integration Points with Existing Code

| Existing Component | How Phase 4 Uses It |
|-------------------|---------------------|
| `User` entity | FK references in swipes and matches tables |
| `UserProfile` entity | Location, preferences, completeness for feed filtering |
| `UserPhoto` repository | First active photo for owner hint in feed cards |
| `CatProfile` entity | Core feed data source |
| `CatPhoto` repository | First active photo for cat cards |
| `ProfileService.checkCompleteness()` | Reuse logic for D-15 (or inline in SQL for efficiency) |
| `GeometryFactory(PrecisionModel(), 4326)` | Same SRID 4326 for consistency with existing location handling |
| `GlobalExceptionHandler` | Add handlers for new discovery exceptions |
| Flyway migrations | V8 (swipes), V9 (matches) — next available after V7 |

## Test Strategy

### Test Infrastructure
- Extend `BaseIntegrationTest` (Testcontainers with PostGIS + MinIO)
- Tests use `ddl-auto: create-drop` (existing pattern — no Flyway in tests)
- Need helper methods to create users with profiles, locations, cats, and photos for test setup

### Test Scenarios

**DiscoveryIntegrationTest:**
1. Feed returns cat profiles with cat-first data (no owner bio, just hint)
2. Feed filters by max distance (PostGIS ST_DWithin)
3. Feed excludes own cats
4. Feed excludes already-swiped cats
5. Feed respects bidirectional gender preferences
6. Feed respects bidirectional age range
7. Feed requires requester to have location set (returns 400)
8. Feed only shows cats from complete profiles
9. Feed only shows cats with at least 1 active photo
10. Feed returns 20 items per page by default
11. Cursor-based pagination returns different cats on next page
12. Feed returns distance in rounded km

**SwipeIntegrationTest:**
13. User can LIKE a cat → returns matched=false
14. User can PASS a cat → returns matched=false
15. Mutual like creates match → returns matched=true + matchId
16. Duplicate swipe on same cat returns 409
17. Cannot swipe on own cat
18. Swiped cat excluded from subsequent feed requests

**MatchIntegrationTest:**
19. Match list returns other user's display name, photo, and cat summary
20. Match list is empty when no matches exist
21. Race condition: concurrent likes create only one match (unique constraint)
22. Owner profile endpoint returns full owner data + all cats

## Validation Architecture

### Critical Paths Requiring Validation

1. **Feed Query Correctness:** The native SQL query is complex — spatial filtering, bidirectional preferences, completeness, exclusion. Each filter must be tested independently.
2. **Match Atomicity:** The `INSERT ON CONFLICT` pattern must be verified under concurrent access.
3. **Data Isolation:** Users must never see their own cats. Swiped cats must never reappear.

### Sampling Strategy

- **Feed filters:** Each filter (distance, gender, age, completeness, already-seen) gets a dedicated test that verifies cats matching the filter appear and cats failing the filter don't
- **Match detection:** Test both orderings (A likes B's cat first vs. B likes A's cat first) to verify LEAST/GREATEST normalization
- **Edge cases:** Empty feed (no eligible cats), user with no location, user with no cats

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| N+1 in feed query | LOW | HIGH | Native SQL with inline subqueries — no lazy loading |
| Match race condition | MEDIUM | HIGH | UNIQUE index on LEAST/GREATEST + ON CONFLICT DO NOTHING |
| Slow feed on large dataset | LOW (v1) | MEDIUM | GiST spatial index exists; swipe indexes added in V8 |
| Incorrect preference filtering | MEDIUM | HIGH | Dedicated integration test per filter dimension |
| Cursor stability across concurrent swipes | LOW | LOW | Seed-based random acceptable for v1; users expect some variance |

---
*Research completed: 2026-06-14*
*Ready for planning: yes*

## RESEARCH COMPLETE
