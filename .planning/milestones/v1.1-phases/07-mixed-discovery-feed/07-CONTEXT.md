# Phase 7: Mixed Discovery Feed - Context

**Gathered:** 2026-06-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Transform the discovery feed from cat-only to a mixed feed. Users with cats appear as cat cards (cat-first reveal preserved, one card per user showing their first-created cat). Users without cats appear as human cards (user profile shown directly). Cat ownership becomes optional — the app opens to all cat lovers, not just cat owners. Everyone sees all card types; the card format (CAT/HUMAN) is determined by the target user's cat ownership status.

</domain>

<decisions>
## Implementation Decisions

### Feed Mixing Strategy
- **D-01:** Cat and human cards are mixed in the same random pool — UNION both card types in one query, same `ORDER BY random()` with `setseed()` for cursor stability. No cat-first bias or ratio control.
- **D-02:** One card per user in the feed. Multi-cat users show their first-created cat (earliest `created_at`). Replaces current one-card-per-cat behavior.
- **D-03:** Swiping on any card (cat or human) excludes the target **owner/user entirely** from future feeds. No per-cat exclusion — you've already decided on that person.
- **D-04:** Everyone sees everything. Card type (CAT/HUMAN) is determined by the target user's cat ownership, not the viewer's. Catless users can swipe on cat cards and vice versa.

### Response & DTO Shape
- **D-05:** Single list with type discriminator — `FeedResponse.cards: List<FeedItem>` with a `type` field (`CAT` or `HUMAN`). Cat-specific fields (catId, catName, breed, catAge, catBio) are nullable (null for HUMAN cards).
- **D-06:** Field name `cards` (was `cats`) in `FeedResponse`. Breaking change accepted — pre-launch, no external clients.
- **D-07:** Human cards in the feed are minimal: `userId`, `displayName`, `userPhotoThumbnail`, `distanceKm`. Full profile fetched on tap via detail endpoint.
- **D-08:** No API versioning — replace `/api/discovery/feed` in place. Pre-launch, breaking changes are acceptable.

### Swipe Model Migration
- **D-09:** `SwipeRequest` has two optional fields: `catId: UUID?` and `targetUserId: UUID?`, with custom validation ensuring exactly one is set. Client sends whichever matches the card type.
- **D-10:** `cat_id` becomes nullable in `swipes` table via Flyway migration. Two partial unique indexes replace the current single unique index: `UNIQUE(swiper_id, cat_id) WHERE cat_id IS NOT NULL` for cat swipes, `UNIQUE(swiper_id, target_user_id) WHERE cat_id IS NULL` for human swipes.
- **D-11:** Mutual match detection uses unified `target_user_id` check — does the target user have ANY LIKE swipe (cat or human) where `target_user_id = current user`? Handles cross-type matching naturally (catless ↔ cat-owner).

### Catless User Feed Eligibility
- **D-12:** Same eligibility bar as cat owners minus the cat requirement: complete profile (`display_name`, `bio`, `date_of_birth`, `gender`, `location` all NOT NULL) + at least one active user photo. No cat photo required.
- **D-13:** Registration unchanged. Cat creation is a separate optional step (POST /api/cats). Backend removes any enforcement that users must have a cat to use discovery.
- **D-14:** Auto-switch — if a user with cats deletes all their cats, they automatically appear as a human card. The feed query handles this based on current cat count. No explicit opt-in needed.
- **D-15:** New discovery endpoint `GET /api/discovery/users/{userId}/profile` for human card detail view. Discovery-specific, keeps the discovery API self-contained. Separate from the cat→owner flow (`GET /api/discovery/cats/{catId}/owner` remains unchanged).

### Claude's Discretion
No areas deferred to Claude's discretion — all decisions explicitly chosen.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Discovery & Matching (existing code)
- `src/main/kotlin/com/catspell/api/discovery/service/DiscoveryService.kt` — Current feed query logic, swipe handling, owner profile endpoint
- `src/main/kotlin/com/catspell/api/discovery/model/SwipeRepository.kt` — Native SQL feed query (needs UNION), swipe duplicate detection
- `src/main/kotlin/com/catspell/api/discovery/model/Swipe.kt` — Swipe entity (catProfile becomes nullable)
- `src/main/kotlin/com/catspell/api/discovery/model/DiscoveryDtos.kt` — Current DTOs (SwipeRequest, FeedItemResponse, FeedResponse — all need changes)
- `src/main/kotlin/com/catspell/api/discovery/model/FeedProjection.kt` — Spring Data projection interface (needs polymorphic version)
- `src/main/kotlin/com/catspell/api/discovery/controller/DiscoveryController.kt` — New endpoint needed for human card detail

### Match Service
- `src/main/kotlin/com/catspell/api/match/service/MatchService.kt` — Match creation already works on user IDs (no changes expected)

### Database Migrations
- `src/main/resources/db/migration/V8__create_swipes_table.sql` — Current schema: cat_id NOT NULL, unique index (swiper_id, cat_id)

### Project Context
- `.planning/PROJECT.md` — Core value, constraints, key decisions
- `.planning/ROADMAP.md` — Phase 7 goal and success criteria (DISC-08 through DISC-12)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MatchService.createMatch(userId1, userId2)` — Already works on user IDs, no cat dependency. Match detection for human swipes will use this as-is.
- `SwipeRepository.findBySwiperIdAndTargetUserIdAndAction` — Already queries by target user ID. Can be reused for unified mutual match check.
- `OwnerProfileResponse` — Response shape can inform the new human card detail endpoint.
- Cursor pagination with `setseed()` — Existing pattern works for mixed UNION query.

### Established Patterns
- Native SQL queries via `@Query(nativeQuery = true)` with Spring Data projections — The UNION query will follow this same pattern.
- `FeedProjection` interface for mapping native query results — Will need a new polymorphic version with nullable cat fields + type discriminator.
- Flyway versioned migrations — New migration adds nullable cat_id and partial indexes.
- Custom exception hierarchy (DuplicateSwipeException, SelfSwipeException) — Extend for human swipe edge cases.

### Integration Points
- `DiscoveryController` — Add new `GET /api/discovery/users/{userId}/profile` endpoint.
- `SwipeRepository.findDiscoveryFeed` — Replace with UNION query that combines cat cards (one per user, first-created cat) and human cards.
- `Swipe` entity — `catProfile` becomes nullable (`@JoinColumn(nullable = true)`).
- `SwipeRequest` — Two optional UUID fields with custom validator.
- Feed exclusion logic — Change from per-cat (`NOT EXISTS swipes WHERE cat_id = cp.id`) to per-owner (`NOT EXISTS swipes WHERE target_user_id = owner_id`).

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 7-Mixed Discovery Feed*
*Context gathered: 2026-06-22*
