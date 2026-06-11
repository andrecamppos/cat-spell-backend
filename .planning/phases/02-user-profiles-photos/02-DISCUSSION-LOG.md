# Phase 2: User Profiles & Photos - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2025-06-11
**Phase:** 2-User Profiles & Photos
**Areas discussed:** Dating preferences, Photo rules, Profile completeness, Location behavior

---

## Dating Preferences

### What preference fields should a user set?

| Option | Description | Selected |
|--------|-------------|----------|
| Gender + age range | Gender preference plus age range (e.g. 25–40). Classic dating app minimum. | |
| Gender + age + distance | Add preferred max distance radius on top of gender and age. | ✓ |
| Minimal — just gender | Only gender preference. Age range and distance added later. | |

**User's choice:** Gender + age + distance
**Notes:** Distance filtering is in Phase 4 (DISC-04) but the preference is captured here so users control their radius.

### How should gender identity and preference be modeled?

| Option | Description | Selected |
|--------|-------------|----------|
| Binary + everyone | Gender: Male/Female. Preference: Men/Women/Everyone. Simple, covers most users. | ✓ |
| Inclusive with custom | Male/Female/Non-binary/Custom. More inclusive but adds complexity. | |
| You decide | Let Claude pick. | |

**User's choice:** Binary + everyone
**Notes:** None

### Should preferences have default values?

| Option | Description | Selected |
|--------|-------------|----------|
| Defaults provided | Sensible defaults (Everyone, 18–99, 50km) — users jump in immediately. | |
| Must set explicitly | No defaults — user must choose during onboarding. Ensures intentional choices. | ✓ |
| You decide | Let Claude choose. | |

**User's choice:** Must set explicitly
**Notes:** None

### What age range bounds should the system enforce?

| Option | Description | Selected |
|--------|-------------|----------|
| 18–99 | Standard. Min 18 (legal), max 99. Wide open. | ✓ |
| 18–80 | Slightly tighter upper bound. | |
| You decide | Let Claude pick. | |

**User's choice:** 18–99
**Notes:** None

---

## Photo Rules

### How many photos allowed per profile?

| Option | Description | Selected |
|--------|-------------|----------|
| Max 6 | Standard for dating apps (Tinder, Bumble). | ✓ |
| Max 9 | Hinge-style grid. More room. | |
| Max 3 | Minimal for v1. | |
| You decide | Let Claude pick. | |

**User's choice:** Max 6 photos
**Notes:** None

### Minimum photo requirement?

| Option | Description | Selected |
|--------|-------------|----------|
| Minimum 1 photo | Required before profile is discoverable. Standard for dating apps. | ✓ |
| No minimum | Photos optional. Profile discoverable even without. | |
| You decide | Let Claude choose. | |

**User's choice:** Minimum 1 photo
**Notes:** None

### Photo ordering model?

| Option | Description | Selected |
|--------|-------------|----------|
| Ordered with primary | Display order stored. First photo = primary. Users can reorder via API. | ✓ |
| Unordered, explicit primary | No order, but user picks one as primary. Simpler backend. | |
| You decide | Let Claude pick. | |

**User's choice:** Ordered with primary
**Notes:** None

### Photo validation on upload?

| Option | Description | Selected |
|--------|-------------|----------|
| Basic validation | JPEG/PNG only, max 10MB. No server-side processing. | |
| Validation + resize | Same limits, plus server-side thumbnail generation (200×200). | ✓ |
| You decide | Let Claude determine. | |

**User's choice:** Validation + resize
**Notes:** Thumbnails improve mobile performance for list views.

---

## Profile Completeness

### When should profile become visible in discovery?

| Option | Description | Selected |
|--------|-------------|----------|
| Complete profile required | Name, bio, photo, gender, DOB, preferences, location — all required. | ✓ |
| Soft gate — name + photo + gender | Only minimal fields required. | |
| No gate — always visible | Discoverable immediately, even if incomplete. | |

**User's choice:** Complete profile required
**Notes:** None

### Date of birth vs age?

| Option | Description | Selected |
|--------|-------------|----------|
| Date of birth | Store DOB, calculate age dynamically. More accurate, needed for age filtering. | ✓ |
| Age as integer | User enters age directly. Simpler but goes stale. | |
| You decide | Let Claude pick. | |

**User's choice:** Date of birth
**Notes:** None

### Max bio length?

| Option | Description | Selected |
|--------|-------------|----------|
| 500 characters | Short and punchy — Tinder/Bumble style. | |
| 1000 characters | More room. About 2–3 short paragraphs. | ✓ |
| You decide | Let Claude pick. | |

**User's choice:** 1000 characters
**Notes:** None

### Profile completeness endpoint?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — completeness check | Returns missing fields + isComplete boolean. Mobile app uses for progress. | ✓ |
| No — implicit | Mobile app checks locally. Backend rejects incomplete profiles on discovery. | |
| You decide | Let Claude determine. | |

**User's choice:** Yes — completeness check endpoint
**Notes:** None

---

## Location Behavior

### How should GPS location be captured?

| Option | Description | Selected |
|--------|-------------|----------|
| On-demand update | Dedicated PUT endpoint. User/app explicitly triggers. | |
| On every app open | App sends location automatically when opened. Backend stores latest. | ✓ |
| You decide | Let Claude pick. | |

**User's choice:** On every app open
**Notes:** None

### How should location be stored?

| Option | Description | Selected |
|--------|-------------|----------|
| PostGIS geometry column | Install PostGIS now. POINT geometry. Native distance queries in Phase 4. | ✓ |
| Simple lat/lng doubles | Two DOUBLE columns. Phase 4 adds PostGIS later or uses Haversine. | |
| You decide | Let Claude choose. | |

**User's choice:** PostGIS geometry column
**Notes:** Phase 4 depends on this for DISC-04 distance filtering.

### Location visibility to other users?

| Option | Description | Selected |
|--------|-------------|----------|
| Distance only | Relative distance shown (e.g., "5 km away"). Never raw coordinates. | ✓ |
| Hidden entirely | Never shown. Only used for feed filtering. | |
| You decide | Let Claude decide. | |

**User's choice:** Distance only
**Notes:** Privacy-first approach, standard for dating apps.

---

## Claude's Discretion

No areas deferred to Claude's discretion — all decisions made by user.

## Deferred Ideas

None — discussion stayed within phase scope.
