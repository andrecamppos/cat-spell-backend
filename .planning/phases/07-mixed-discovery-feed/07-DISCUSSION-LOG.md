# Phase 7: Mixed Discovery Feed - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-22
**Phase:** 7-Mixed Discovery Feed
**Areas discussed:** Feed mixing strategy, Response & DTO shape, Swipe model migration, Catless user feed eligibility

---

## Feed Mixing Strategy

### Q1: How should cat cards and human cards be mixed in the feed?

| Option | Description | Selected |
|--------|-------------|----------|
| Same random pool | UNION both card types into one query, same ORDER BY random(). Fully random order. | ✓ |
| Cat-first bias | Cat cards get weighted priority. Human cards fill gaps. | |
| You decide | Let Claude pick. | |

**User's choice:** Same random pool
**Notes:** Simplest approach, mirrors how most dating apps mix content.

### Q2: For multi-cat users, one card per cat or one card per user?

| Option | Description | Selected |
|--------|-------------|----------|
| One card per cat (keep current) | Each cat is a separate feed item. A user with 3 cats = 3 cards. | |
| One card per user (featured cat) | Only show the user's first/primary cat in the feed. | ✓ |
| You decide | Let Claude pick. | |

**User's choice:** One card per user (featured cat)
**Notes:** CAT-07 (primary cat designation) is deferred to v2. Default rule needed for picking which cat.

### Q3: How to pick which cat to show for multi-cat users?

| Option | Description | Selected |
|--------|-------------|----------|
| First created cat | Earliest created_at timestamp. Simple, deterministic. | ✓ |
| Most recently updated | Most recently edited cat. Incentivizes freshness. | |
| Random per session | Pick a random cat per feed generation. | |

**User's choice:** First created cat
**Notes:** None.

### Q4: Should swiping a cat card exclude all cats from the same owner?

| Option | Description | Selected |
|--------|-------------|----------|
| Exclude the owner entirely | Swiping on any cat excludes all of that owner's cats from future feeds. | ✓ |
| Keep per-cat exclusion | Only that specific cat is excluded. | |

**User's choice:** Exclude the owner entirely
**Notes:** Natural fit with one-card-per-user — you've already decided on that person.

---

## Response & DTO Shape

### Q1: How should the API represent both card types?

| Option | Description | Selected |
|--------|-------------|----------|
| Single list with type discriminator | One items list with CAT/HUMAN type field. Cat-specific fields nullable. | ✓ |
| Sealed/polymorphic DTOs | Jackson @JsonTypeInfo. CatFeedItem and HumanFeedItem subtypes. | |
| You decide | Let Claude pick. | |

**User's choice:** Single list with type discriminator
**Notes:** Matches ROADMAP success criterion #3.

### Q2: Field name for the list in FeedResponse?

| Option | Description | Selected |
|--------|-------------|----------|
| items | Generic. FeedResponse(items: List<FeedItem>). | |
| cards | Domain-specific, matches the swipe card metaphor. | ✓ |
| You decide | Let Claude pick. | |

**User's choice:** cards
**Notes:** Breaking change from `cats` — accepted since pre-launch.

### Q3: Human card fields in feed?

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror user profile fields | userId, displayName, bio, age, gender, photo, distanceKm. | |
| Minimal — just identity + photo | userId, displayName, userPhotoThumbnail, distanceKm. Full profile on tap. | ✓ |
| You decide | Let Claude pick. | |

**User's choice:** Minimal — just identity + photo
**Notes:** Keeps feed lightweight. Full profile fetched on tap like the cat→owner flow.

### Q4: Version the endpoint for breaking change?

| Option | Description | Selected |
|--------|-------------|----------|
| No versioning — just change it | Pre-launch, no external clients. Replace in place. | ✓ |
| URL versioning (/api/v2/discovery/feed) | Keep old endpoint, add new one. | |
| You decide | Let Claude pick. | |

**User's choice:** No versioning — just change it
**Notes:** None.

---

## Swipe Model Migration

### Q1: How should SwipeRequest accept human card swipes?

| Option | Description | Selected |
|--------|-------------|----------|
| Two optional fields with validation | catId: UUID? and targetUserId: UUID?, exactly one set. | ✓ |
| Discriminated request (type + id) | type: CAT/HUMAN and targetId: UUID. Single ID field. | |
| You decide | Let Claude pick. | |

**User's choice:** Two optional fields with validation
**Notes:** Clean contract — client sends whichever applies.

### Q2: How to handle duplicate prevention with nullable cat_id?

| Option | Description | Selected |
|--------|-------------|----------|
| Two partial unique indexes | UNIQUE(swiper_id, cat_id) WHERE cat_id IS NOT NULL + UNIQUE(swiper_id, target_user_id) WHERE cat_id IS NULL. | ✓ |
| Single composite index with COALESCE | UNIQUE(swiper_id, COALESCE(cat_id, target_user_id)). | |
| You decide | Let Claude pick. | |

**User's choice:** Two partial unique indexes
**Notes:** PostgreSQL supports partial indexes natively. Cleaner semantics.

### Q3: Mutual match detection for human swipes?

| Option | Description | Selected |
|--------|-------------|----------|
| Symmetric user-based check | Check if target has LIKE swipe with target_user_id = current AND cat_id IS NULL. | |
| Unified check via target_user_id | Check ANY LIKE swipe with target_user_id = current user. Handles cross-type matching. | ✓ |
| You decide | Let Claude pick. | |

**User's choice:** Unified check via target_user_id
**Notes:** Handles cross-type matching naturally (catless ↔ cat-owner).

### Q4: Can cat owners swipe on human cards and vice versa?

| Option | Description | Selected |
|--------|-------------|----------|
| Everyone sees everything | Both card types visible to all users. Card type = target's cat ownership. | ✓ |
| Type-matched only | Cat owners see cat cards only. Catless users see human cards only. | |

**User's choice:** Everyone sees everything
**Notes:** Most inclusive. Card type is about how the target is presented, not who can see it.

---

## Catless User Feed Eligibility

### Q1: Minimum profile for catless users to appear in feed?

| Option | Description | Selected |
|--------|-------------|----------|
| Same as cat owners minus cat requirement | Complete profile + at least one active user photo. | ✓ |
| Stricter — require multiple photos | 2+ active user photos since no cat photo to attract attention. | |
| You decide | Let Claude pick. | |

**User's choice:** Same as cat owners minus cat requirement
**Notes:** Consistent eligibility bar.

### Q2: Registration/onboarding changes?

| Option | Description | Selected |
|--------|-------------|----------|
| Registration stays same, cat creation optional | Backend removes any cat enforcement for discovery. | ✓ |
| No backend changes needed | Backend already doesn't enforce cat creation during registration. | |

**User's choice:** Registration stays the same, cat creation becomes optional
**Notes:** None.

### Q3: Auto-switch when user deletes all cats?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-switch to human card | Feed query handles based on current cat count. | ✓ |
| Hide until they act | Removed from feed until they add a cat or opt in. | |
| You decide | Let Claude pick. | |

**User's choice:** Auto-switch to human card
**Notes:** No explicit opt-in needed. Simplest behavior.

### Q4: Human card detail view endpoint?

| Option | Description | Selected |
|--------|-------------|----------|
| New endpoint: GET /api/discovery/users/{userId}/profile | Discovery-specific. Keeps discovery API self-contained. | ✓ |
| Reuse owner profile endpoint pattern | GET /api/discovery/users/{userId}. Same shape as OwnerProfileResponse. | |
| You decide | Let Claude pick. | |

**User's choice:** New endpoint: GET /api/discovery/users/{userId}/profile
**Notes:** Separate from cat→owner flow which remains unchanged.

---

## Claude's Discretion

No areas deferred to Claude's discretion — all decisions explicitly chosen.

## Deferred Ideas

None — discussion stayed within phase scope.
