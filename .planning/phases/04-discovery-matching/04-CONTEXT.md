# Phase 4: Discovery & Matching - Context

**Gathered:** 2026-06-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the cat-first discovery feed with geolocation filtering, swipe actions (like/pass), seen-profile tracking, and mutual match detection. The feed serves cat profiles with minimal owner hints, filtered by distance and dating preferences (bidirectional). Users swipe on cats but the relationship is recorded user-to-user. Mutual likes create a match. Users can view their match list with the other user's profile and cats.

</domain>

<decisions>
## Implementation Decisions

### Feed Response & Pagination
- **D-01:** Feed cards include cat data (name, age, breed, bio, photos) plus minimal owner hint (owner's display name and first photo). Full owner profile accessible only via separate detail endpoint
- **D-02:** Cursor-based pagination (keyset), 20 cats per page — stable under concurrent swipes, smooth mobile scrolling
- **D-03:** Random ordering within the user's max distance radius — more variety per session, avoids always showing the same nearby cats first
- **D-04:** Distance to cat's owner included in feed card as rounded integer km (e.g., "5 km away") — consistent with Phase 2 decision D-16 (relative distance only, never raw coords)

### Swipe Actions & Undo
- **D-05:** Single `swipes` table with columns: swiper_id, cat_id, action (LIKE/PASS), timestamp. Both likes and passes stored for easy "already seen" tracking
- **D-06:** No undo for v1 — passes are permanent. Once passed, that cat won't reappear in the feed
- **D-07:** Swipe action recorded against owner_user_id (not cat_id). The cat is the discovery gateway, but the relationship is user-to-user. Simplifies match detection
- **D-08:** Swipe endpoint returns inline match check — `{matched: true/false, matchId: ...}` so the mobile app can show "It's a match!" immediately

### Match Detection & Response
- **D-09:** Matches table stores user pair only: user1_id, user2_id, matched_at. No cat reference — the match is between people, cats were the discovery mechanism
- **D-10:** Match list endpoint returns the other user's display name, first photo, and a summary of their cats (names + first photo each). Full context for the match list screen
- **D-11:** One-sided likes are invisible — users only learn about interest when a mutual match forms. Standard dating app approach
- **D-12:** One match per user pair — unique constraint on (user1_id, user2_id). First mutual like creates the match; subsequent likes of other cats from the same owner are just swipes

### Feed Filtering Logic
- **D-13:** Full preference filtering applied: distance (PostGIS ST_DWithin) + age range + gender preference. Bidirectional — only show cats whose owners match the user's preferences AND whose owners' preferences match back
- **D-14:** 400 error returned if requesting user has no location set — "Location required for discovery." Mobile app should prompt location enable
- **D-15:** Feed only shows cats from users with complete profiles (display name, bio, DOB, gender, preferences, 1+ user photo, location). Ensures quality matches
- **D-16:** Own cats always excluded from feed — explicit filter in the discovery query

### Claude's Discretion
No areas deferred to Claude's discretion — all decisions made by user.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md` — Core value (cat-first discovery), constraints (Kotlin + Spring Boot, PostgreSQL, S3-compatible), out-of-scope items
- `.planning/REQUIREMENTS.md` — DISC-01 through DISC-07 requirement definitions and traceability
- `.planning/ROADMAP.md` §Phase 4 — Success criteria for this phase

### Prior Phase Context
- `.planning/phases/01-foundation-auth/01-CONTEXT.md` — Package structure decisions (D-01–D-04), token lifecycle, error format (RFC 7807). Phase 4 MUST follow the same domain-first vertical slice pattern (`com.catspell.api.discovery.*`)
- `.planning/phases/02-user-profiles-photos/02-CONTEXT.md` — Photo upload decisions, profile completeness pattern (D-10), PostGIS setup (D-15), relative distance only (D-16), dating preferences (D-01–D-04), DOB for age calculation (D-11)
- `.planning/phases/03-cat-profiles/03-CONTEXT.md` — Cat data model decisions (D-01–D-04), cat photo rules (D-05–D-08), multi-cat limits (D-09), each cat as separate feed card (D-10), min 1 photo for discovery (D-06)

### Stack Research
- `.planning/research/STACK.md` — Recommended versions, dependencies, Kotlin entity gotchas
- `.planning/research/SUMMARY.md` — Architecture approach, critical pitfalls (Kotlin entity gotchas, N+1 queries)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UserProfile` entity (`com.catspell.api.profile.model.UserProfile`) — Has `location` as PostGIS `Point(4326)`, `maxDistanceKm`, `gender`, `genderPreference`, `ageMin`, `ageMax`, `dateOfBirth` — all needed for feed filtering
- `CatProfile` entity (`com.catspell.api.cat.model.CatProfile`) — ManyToOne to `User`, has name, age, ageUnit, breed, bio. FK target for swipes
- `CatPhoto` entity (`com.catspell.api.cat.model.CatPhoto`) — s3Key, thumbnailS3Key, displayOrder, status. First ACTIVE photo needed for feed cards
- `UserPhoto` entity (`com.catspell.api.profile.model.UserPhoto`) — First ACTIVE photo needed for owner hint in feed cards
- `ProfileService.checkCompleteness()` — Profile completeness logic to reuse for feed filtering gate
- `CatProfileRepository` — `findByUserId`, `countByUserId`. Will need new query methods for feed
- `UserProfileRepository` — `findByUserId`. Will need PostGIS spatial queries (ST_DWithin)
- `SecurityConfig` — Add permit rules for new discovery/match endpoints
- `GlobalExceptionHandler` — RFC 7807 error handling ready for discovery-specific errors

### Established Patterns
- Domain-first vertical slices: `com.catspell.api.{domain}.controller/service/model/`
- JPA entities as classes (not data classes) with `equals`/`hashCode` overrides (kotlin-jpa plugin)
- DTOs as Kotlin data classes with Jakarta validation annotations
- Flyway versioned migrations: next available is V8
- `extractUserId()` pattern in controllers via `SecurityContextHolder`

### Integration Points
- `SecurityConfig.securityFilterChain` — add permit rules for `/api/discovery/**` and `/api/matches/**`
- Flyway migrations V8+ — `swipes` table, `matches` table, spatial indexes
- `UserProfileRepository` — add native PostGIS queries (`@Query` with `ST_DWithin`, `ST_Distance`)
- PostGIS extension already enabled (V3 migration) — ready for spatial queries

</code_context>

<specifics>
## Specific Ideas

- Swipes table stores both the cat_id (which cat was swiped on) and the target_user_id (the cat's owner) — cat_id for feed exclusion, target_user_id for match detection
- Feed query joins cat_profiles → users → user_profiles with PostGIS ST_DWithin filter, preference matching, and NOT EXISTS subquery against swipes table
- Cursor for pagination can use a randomized seed + offset approach to maintain stable random ordering across pages within a session
- Match detection on LIKE: check if target_user has a LIKE swipe against any cat belonging to the current user. If yes, create match row

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 4-Discovery & Matching*
*Context gathered: 2026-06-13*
