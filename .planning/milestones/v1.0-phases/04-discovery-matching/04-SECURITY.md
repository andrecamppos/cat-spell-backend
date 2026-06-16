---
phase: 4
slug: discovery-matching
status: verified
threats_open: 0
asvs_level: 1
created: 2025-06-15
---

# Phase 4 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| JWT → DiscoveryController | Authenticated user identity extracted from JWT | userId (UUID) |
| JWT → MatchController | Authenticated user identity extracted from JWT | userId (UUID) |
| Feed API → Client | Discovery feed returns cat-first data to mobile app | Cat profiles, rounded distance, owner display name |
| Owner Profile API → Client | Owner reveal returns public profile data | Display name, bio, age, gender, photos, cats |
| Match API → Client | Match list returns matched users' public data | Other user summary, cat summaries |
| Service → PostGIS | Spatial queries use stored location coordinates | GPS coordinates (server-side only) |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| 04-01-T1 | IDOR (Info Disclosure) | Swipe API | mitigate | No endpoint exposes raw swipe data; feed uses swipes only for exclusion via NOT EXISTS; match endpoint returns only mutual matches | closed |
| 04-01-T2 | Elevation of Privilege | Swipe API | mitigate | `SelfSwipeException` thrown in `DiscoveryService.swipe()` when `catOwnerId == userId` — validated before insert | closed |
| 04-01-T3 | Info Disclosure | Feed API | mitigate | Feed returns `distanceKm: Int` (rounded) only; no raw lat/lng in `FeedItemResponse`, `OwnerProfileResponse`, or any client-facing DTO | closed |
| 04-01-T4 | Info Disclosure | Feed API | accept | Feed enumeration via exhaustive swiping — acceptable for v1; rate limiting deferred to Phase 6 | closed |
| 04-01-T5 | Tampering | Match DB | mitigate | `UNIQUE INDEX idx_matches_pair ON (LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id))` + `DataIntegrityViolationException` catch in `MatchService.createMatch()` | closed |
| 04-02-T1 | Info Disclosure | Owner Profile | mitigate | `OwnerProfileResponse` exposes only displayName, bio, age (calculated via `Period.between`), gender, photos, cats — no email, no coordinates, no raw DOB | closed |
| 04-02-T2 | Info Disclosure | Match API | mitigate | `MatchController.getMatches()` uses `extractUserId()` from JWT — always scoped to authenticated user; no user ID parameter accepted | closed |
| 04-02-T3 | Info Disclosure | Owner Profile | accept | Any authenticated user can view any cat's owner profile (not restricted to discovery context) — acceptable for v1; profile contains only public-facing data | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-04-01 | 04-01-T4 | Feed enumeration via exhaustive swiping is low severity; rate limiting planned for Phase 6 | gsd-secure-phase | 2025-06-15 |
| AR-04-02 | 04-02-T3 | Unrestricted owner profile access — profile contains only public-facing data; v2 could restrict to feed-seen users | gsd-secure-phase | 2025-06-15 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2025-06-15 | 8 | 8 | 0 | gsd-secure-phase |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2025-06-15
