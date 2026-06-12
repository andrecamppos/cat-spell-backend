# Phase 3: Cat Profiles - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 3-Cat Profiles
**Areas discussed:** Cat data model, Cat photo rules, Multi-cat & discovery, Deletion behavior

---

## Cat Data Model

| Option | Description | Selected |
|--------|-------------|----------|
| Integer age in years | Simple number (e.g. 3). Easy to input, no precision pressure | |
| Age + unit | Number + unit enum (YEARS / MONTHS). Supports kittens and adults | ✓ |
| Date of birth | Like UserProfile — store DOB, calculate dynamically | |

**User's choice:** Age + unit
**Notes:** Cats' exact birthdays are often unknown, especially rescues. Unit enum allows "4 months" for kittens.

| Option | Description | Selected |
|--------|-------------|----------|
| Free-text string | User types whatever they want. Flexible but inconsistent | |
| Predefined list + Other | Common breeds list with custom option | |
| Free-text + optional | Free-text but not required (nullable) | ✓ |

**User's choice:** Free-text + optional
**Notes:** Many cat owners don't know the breed or it's a mix. Making it optional removes friction.

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, required | Mandatory bio, max 500 chars | |
| Yes, optional | Optional bio, nullable, max 500 chars | ✓ |
| No bio | No description field — photos only | |

**User's choice:** Yes, optional
**Notes:** Bio available for owners who want to describe personality, but not blocking.

| Option | Description | Selected |
|--------|-------------|----------|
| Required, max 50 chars | Every cat needs a name, compact limit | |
| Required, max 100 chars | Required, generous limit matching UserProfile.displayName | ✓ |
| You decide | Claude picks reasonable default | |

**User's choice:** Required, max 100 chars

---

## Cat Photo Rules

| Option | Description | Selected |
|--------|-------------|----------|
| Same as user: 6 max | Consistent limits, reuse same constant | |
| Lower: 4 max | Fewer photos, lighter feed | |
| Higher: 10 max | Cats are the star — more photos, more engagement | ✓ |

**User's choice:** Higher: 10 max
**Notes:** Cats are the hero content of the app — owners should be able to showcase them generously.

| Option | Description | Selected |
|--------|-------------|----------|
| At least 1 photo | Same as user profile rule | ✓ |
| At least 2 photos | Higher bar for quality | |
| No minimum | Photos optional for discovery | |

**User's choice:** At least 1 photo

| Option | Description | Selected |
|--------|-------------|----------|
| Same flow, separate service | New CatPhotoService mirroring PhotoService with cat-specific S3 paths | ✓ |
| Shared photo service | Extract generic photo service for both user and cat photos | |
| You decide | Claude picks best approach | |

**User's choice:** Same flow, separate service
**Notes:** Keeps domain separation clean. CatPhotoService in `com.catspell.api.cat.*` mirrors the Phase 2 pattern.

| Option | Description | Selected |
|--------|-------------|----------|
| Identical constraints | JPEG/PNG, 10MB max, 200×200 thumbnails | ✓ |
| Same types, larger thumbnails | JPEG/PNG, 10MB, but 400×400 thumbnails | |
| You decide | Claude picks | |

**User's choice:** Identical constraints

---

## Multi-Cat & Discovery

| Option | Description | Selected |
|--------|-------------|----------|
| Max 3 cats | Reasonable limit, diverse feed | |
| Max 5 cats | Generous, covers multi-cat households | ✓ |
| No limit | Unlimited cats, rely on feed logic | |

**User's choice:** Max 5 cats

| Option | Description | Selected |
|--------|-------------|----------|
| Each cat is a separate card | Individual cats in discovery, may see multiple from same owner | ✓ |
| One random cat per user | Discovery picks one cat per user | |
| You decide in Phase 4 | Don't lock now, data model supports both | |

**User's choice:** Each cat is a separate card
**Notes:** Core to the "cat-first discovery" value — users swipe on cats, not people.

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, mirror user pattern | Completeness endpoint for cats | |
| No, simpler validation | Validate required fields on create, photo min in Phase 4 query | ✓ |
| You decide | Claude picks | |

**User's choice:** No, simpler validation
**Notes:** Simpler approach — validate name + age on create, let Phase 4 discovery query enforce photo minimum.

---

## Deletion Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Full cascade delete | Delete cat → delete cat_photos rows → delete S3 objects | ✓ |
| Soft delete cat only | Mark as deleted, keep photos for recovery | |
| You decide | Consistent with user photo deletion (hard delete) | |

**User's choice:** Full cascade delete
**Notes:** Clean removal, no orphans. Consistent with user photo deletion pattern.

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, full cascade | User deletion cascades to all cats + cat photos. Set up JPA cascade now | ✓ |
| Defer to Phase 6 | Just set up FK constraints, handle later | |
| You decide | DB-level cascade, full flow in Phase 6 | |

**User's choice:** Yes, full cascade
**Notes:** Set up both JPA cascade annotations and DB-level ON DELETE CASCADE constraints.

---

## Claude's Discretion

No areas deferred to Claude's discretion.

## Deferred Ideas

None — discussion stayed within phase scope.
