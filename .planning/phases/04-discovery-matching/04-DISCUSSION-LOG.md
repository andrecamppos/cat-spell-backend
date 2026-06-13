# Phase 4: Discovery & Matching - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-13
**Phase:** 4-Discovery & Matching
**Areas discussed:** Feed response & pagination, Swipe actions & undo, Match detection & response, Feed filtering logic

---

## Feed Response & Pagination

| Option | Description | Selected |
|--------|-------------|----------|
| Cat-only card | Zero owner info in feed response — owner accessible only via separate detail endpoint | |
| Cat + minimal owner hint | Cat data + owner's display name and first photo. Cat-first in UI, but API includes basic owner data | ✓ |
| You decide | Let Claude pick the best approach | |

**User's choice:** Cat + minimal owner hint
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Cursor-based, 20 per page | Keyset pagination — stable under concurrent swipes | ✓ |
| Offset-based, 20 per page | Simple offset/limit — easier but can show duplicates | |
| You decide | Let Claude pick | |

**User's choice:** Cursor-based, 20 per page
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Nearest first | Sort by distance ascending — deterministic, simple PostGIS query | |
| Random within radius | Randomized order within max distance — more variety per session | ✓ |
| You decide | Let Claude pick | |

**User's choice:** Random within radius
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, rounded distance | Distance in km rounded to nearest integer — consistent with Phase 2 D-16 | ✓ |
| No distance in feed | Distance only shown on owner reveal endpoint | |
| You decide | Let Claude pick | |

**User's choice:** Yes, rounded distance
**Notes:** None

---

## Swipe Actions & Undo

| Option | Description | Selected |
|--------|-------------|----------|
| Single swipes table | One table with action column (LIKE/PASS), all swipe history in one place | ✓ |
| Separate likes table | Only store likes, seen-tracking via separate table | |
| You decide | Let Claude pick | |

**User's choice:** Single swipes table
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| No undo | Passes are permanent for v1. Keeps feed simple, prevents gaming | ✓ |
| Passes expire after 30 days | Passed cats reappear after 30 days. Likes never expire | |
| You decide | Let Claude pick | |

**User's choice:** No undo
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Swipe on cat_id | User likes/passes a specific cat. Match checks if other user liked any of this user's cats | |
| Swipe on owner via cat | Action recorded against owner_user_id. Cat is gateway, relationship is user-to-user | ✓ |
| You decide | Let Claude pick | |

**User's choice:** Swipe on owner via cat
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, inline match check | Swipe returns {matched: true/false, matchId}. Mobile app shows "It's a match!" immediately | ✓ |
| Separate check, no inline | Swipe just stores action. Match detection async or via polling | |
| You decide | Let Claude pick | |

**User's choice:** Yes, inline match check
**Notes:** None

---

## Match Detection & Response

| Option | Description | Selected |
|--------|-------------|----------|
| User pair + cat refs | matches table includes cat_id_that_triggered the match | |
| User pair only | matches table: user1_id, user2_id, matched_at. No cat reference | ✓ |
| You decide | Let Claude pick | |

**User's choice:** User pair only
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Other user + their cats | Each match shows other user's name, photo, and cat summaries | ✓ |
| Other user only | Just display name and first photo. Cat details via separate endpoints | |
| You decide | Let Claude pick | |

**User's choice:** Other user + their cats
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| No, hidden until match | One-sided likes invisible. Standard dating app approach | ✓ |
| Yes, show incoming likes | Users can see who liked their cats | |
| You decide | Let Claude pick | |

**User's choice:** No, hidden until match
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| One match per user pair | Unique constraint on (user1_id, user2_id). No duplicate matches | ✓ |
| You decide | Let Claude handle dedup | |

**User's choice:** One match per user pair
**Notes:** None

---

## Feed Filtering Logic

| Option | Description | Selected |
|--------|-------------|----------|
| Full preference filtering | Distance + age range + gender preference. Bidirectional matching | ✓ |
| Distance + gender only | Skip age filtering for v1 | |
| Distance only | All preference matching deferred to v2 | |
| You decide | Let Claude pick | |

**User's choice:** Full preference filtering
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Return 400 error | Clear error: "Location required for discovery" | ✓ |
| Return empty feed | Silently return empty with hint field | |
| You decide | Let Claude pick | |

**User's choice:** Return 400 error
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, only complete profiles | Full profile completeness required to appear in feed | ✓ |
| Only require location + 1 cat photo | Lighter gate for feed visibility | |
| You decide | Let Claude pick | |

**User's choice:** Yes, only complete profiles
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| No, exclude own cats | Always exclude requesting user's own cats from feed | ✓ |
| You decide | Let Claude handle | |

**User's choice:** No, exclude own cats
**Notes:** None

---

## Claude's Discretion

No areas deferred to Claude's discretion — all decisions made by user.

## Deferred Ideas

None — discussion stayed within phase scope.
