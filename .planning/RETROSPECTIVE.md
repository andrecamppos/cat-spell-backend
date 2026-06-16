# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — MVP Backend

**Shipped:** 2026-06-16
**Phases:** 6 | **Plans:** 13 | **Timeline:** 8 days

### What Was Built
- JWT authentication with refresh token rotation and theft detection
- User profiles with S3 photo management (presigned URLs, thumbnails) and PostGIS geolocation
- Cat profile system with multi-cat support (5-cat limit), photos, and cascade deletion
- Cat-first discovery feed with PostGIS distance filtering, swipe actions, and mutual match detection
- Real-time WebSocket STOMP chat with offline delivery, conversation management, and unread tracking
- API hardening: OpenAPI docs, Bucket4j rate limiting, S3/WebSocket/DB health indicators
- 163 integration tests across 19 test files using Testcontainers

### What Worked
- **Vertical MVP slicing** — each phase delivered a complete slice (DB → entity → service → controller → tests) that was immediately testable
- **Testcontainers from Phase 2 onward** — real PostgreSQL + PostGIS + MinIO in tests caught issues H2 would have missed (PostGIS functions, S3 operations)
- **Sequential phase dependency** — each phase built cleanly on the previous, no cross-phase conflicts
- **STOMP WebSocket** — Spring Boot's native STOMP support made real-time chat straightforward
- **Presigned URL pattern** — backend never handles file bytes, scalable from day one

### What Was Inefficient
- **REQUIREMENTS.md bookkeeping drift** — PROF and CHAT requirements remained unchecked despite phases being complete; traceability table status lagged behind actual state
- **STATE.md inconsistencies** — Phase 6 marked "Not Started" in milestone progress table while status field said "shipped"
- **Phase 2 status in ROADMAP.md** — still showed "Planned" despite being complete; overview table had corrupted row for Phase 4

### Patterns Established
- **Testcontainers as default** — all integration tests use real containers, no in-memory DB
- **Presigned URL photo flow** — upload URL → client upload → confirm → thumbnail generation
- **Cursor-based pagination** — used for discovery feed and chat message history
- **Lazy conversation creation** — conversations created on first message, not on match
- **RFC 7807 ProblemDetail** — consistent error response format across all endpoints
- **Configurable rate limiting** — production default (10/min) overridable in tests (10000)

### Key Lessons
1. **Keep traceability in sync** — requirement checkboxes and traceability table should be updated as part of phase completion, not deferred to milestone close
2. **Spring Boot 4.0 breaking changes** — health indicator package moved to `org.springframework.boot.health.contributor`; Bucket4j deprecated `Bandwidth.classic` in 8.x — always check migration guides
3. **Test email uniqueness matters** — concurrent test contexts sharing the same email prefixes caused 409 Conflict errors; unique prefixes per test class prevent collisions
4. **PostGIS native queries** — JPQL doesn't support PostGIS functions; native queries with `ST_DWithin` and `ST_DistanceSphere` are necessary for geolocation

### Cost Observations
- Model mix: Cascade balanced profile
- Sessions: ~12 sessions across 8 days
- Notable: Vertical MVP slicing kept each phase focused and completeable in 1-2 sessions

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Timeline | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 8 days | 6 | Initial vertical MVP delivery |

### Cumulative Quality

| Milestone | Tests | LOC | Test Files |
|-----------|-------|-----|------------|
| v1.0 | 163 | 8,433 | 19 |

### Top Lessons (Verified Across Milestones)

1. Vertical slicing (DB → API → tests) per phase enables clean incremental delivery
2. Real database testing (Testcontainers) catches issues that in-memory DBs hide
