---
phase: 7
slug: mixed-discovery-feed
status: verified
threats_open: 0
asvs_level: 1
created: 2025-06-23
---

# Phase 7 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> **Retroactive-STRIDE mode** — phase authored before formal threat modelling; register built from implementation files.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Client → API | All discovery endpoints require JWT authentication | SwipeRequest, FeedRequest, userId path params |
| API → Database | Native SQL UNION ALL query with parameterized inputs | User IDs, coordinates, pagination params |
| API → S3 | Photo S3 keys returned in feed/profile responses | thumbnail_s3_key strings (pre-existing, not introduced in Phase 7) |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-7-01 | Spoofing | GET /users/{userId}/profile | mitigate | JWT auth via SecurityConfig `anyRequest().authenticated()` + `extractUserId()` in controller | closed |
| T-7-02 | Spoofing | Human swipe path (targetUserId) | mitigate | Swiper identity from JWT auth context (`userId` param), not request body | closed |
| T-7-03 | Tampering | SwipeRequest validation | mitigate | `require((catId != null) xor (targetUserId != null))` in DiscoveryService.swipe() | closed |
| T-7-04 | Tampering | UNION ALL native query | mitigate | All parameters bound via Spring Data `@Param` — no string concatenation | closed |
| T-7-05 | Tampering | Duplicate human swipe | mitigate | Partial unique index `idx_swipes_unique_human` + `existsBySwiperIdAndTargetUserIdAndCatProfileIsNull()` app check | closed |
| T-7-06 | Repudiation | Human swipe actions | mitigate | Swipe entity persisted to DB with `createdAt` timestamp (Instant.now()) | closed |
| T-7-07 | Information Disclosure | getUserProfile IDOR | accept | Any authenticated user can view any profile by userId — consistent with existing getOwnerProfile(catId) pattern; required for discovery app functionality | closed |
| T-7-08 | Denial of Service | Feed pagination | mitigate | `pageSize.coerceIn(1, 50)` + `ST_DWithin` spatial filter bounds result set | closed |
| T-7-09 | Elevation of Privilege | Self-swipe on human card | mitigate | `if (targetUserId == userId) throw SelfSwipeException()` in human swipe branch | closed |
| T-7-10 | Elevation of Privilege | Feed self-exclusion | mitigate | `user_id != :requesterId` in both CAT and HUMAN branches of UNION ALL query | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-7-01 | T-7-07 | getUserProfile endpoint returns profile for any userId without feed-membership check. Consistent with existing getOwnerProfile endpoint pattern. Discovery apps require profile visibility for swiped/matched users. Rate limiting at infrastructure layer provides additional protection. | gsd-secure-phase (retroactive-STRIDE) | 2025-06-23 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2025-06-23 | 10 | 10 | 0 | gsd-secure-phase (retroactive-STRIDE) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2025-06-23
