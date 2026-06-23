# Milestones

## v1.0 MVP Backend (Shipped: 2026-06-16)

**Phases completed:** 6 phases, 13 plans, 50 tasks
**Stats:** 122 commits, 8,433 LOC Kotlin, 163 integration tests
**Timeline:** 8 days (2026-06-08 → 2026-06-16)

**Key accomplishments:**

- JWT authentication with refresh token rotation and theft detection (Phase 1)
- User profiles with S3 photo management, PostGIS geolocation, and Testcontainers test infra (Phase 2)
- Cat profile system with multi-cat support, photos, and cascade deletion (Phase 3)
- Cat-first discovery feed with PostGIS distance filtering, swipe actions, and mutual match detection (Phase 4)
- Real-time WebSocket chat with STOMP, offline delivery, mark-read, and conversation management (Phase 5)
- API hardening — OpenAPI docs, rate limiting, health indicators, 163 integration tests (Phase 6)

## v1.1 Mixed Discovery (Shipped: 2026-06-23)

**Phases completed:** 1 phase, 2 plans, 15 new tests (180 total)
**Stats:** 16 commits, 8,880 LOC Kotlin, 180 integration tests
**Timeline:** 2 days (2026-06-22 → 2026-06-23)

**Key accomplishments:**

- Mixed discovery feed with UNION ALL query — cat cards for cat owners, human cards for catless users (Phase 7)
- Schema migration: nullable cat_id with partial unique indexes for polymorphic swipe deduplication (Phase 7)
- Human card detail endpoint and cross-type mutual match detection (Phase 7)
- 15 new integration tests covering all mixed feed scenarios (Phase 7)

**Design change:** Users no longer need a cat to use the app. Discovery shows cat cards for users with cats (cat-first preserved) and human cards for users without cats.

---
