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

## Milestone: v1.1 — Mixed Discovery

**Shipped:** 2026-06-23
**Phases:** 1 | **Plans:** 2 | **Timeline:** 2 days

### What Was Built
- Mixed discovery feed with UNION ALL query — cat cards for cat owners, human cards for catless users
- Schema migration: nullable cat_id with partial unique indexes for polymorphic swipe deduplication
- Human card detail endpoint (`GET /api/discovery/users/{userId}/profile`)
- Cross-type mutual match detection (cat owner ↔ catless user)
- 15 new integration tests covering all mixed feed scenarios (180 total)

### What Worked
- **Tight phase scoping** — single phase with 2 plans delivered a complete feature in 2 days
- **Plan 01 as foundation** — isolating schema/model changes let plan 02 focus purely on query + tests
- **Test-driven validation** — 15 new tests covered every edge case (human swipe, cross-type match, exclusion)
- **Existing test infrastructure** — Testcontainers + helper patterns from v1.0 made new test writing fast

### What Was Inefficient
- **Scope bleed in plan 01** — had to touch more files than planned (repository, service, test assertions) to keep compilation green; plan boundaries could have been tighter
- **Test location contamination** — shared NYC coordinates between tests caused cross-test interference; required unique locations per scenario

### Patterns Established
- **Polymorphic feed items** — `type` discriminator (CAT/HUMAN) in DTOs for client rendering
- **Partial unique indexes** — replace single unique constraint when a column becomes nullable
- **Exactly-one validation** — `catId XOR targetUserId` pattern for mutually exclusive optional fields
- **ROW_NUMBER per owner** — one card per multi-cat user in discovery (first-created cat)

### Key Lessons
1. **Plan boundaries at compilation boundaries** — if changing a model requires updating all consumers, include consumers in the same plan
2. **Test isolation via unique coordinates** — PostGIS distance queries are sensitive to shared test data; each scenario needs its own location
3. **UNION ALL for polymorphic queries** — cleaner than conditional JOINs when result shapes share a common projection

### Cost Observations
- Model mix: Cascade balanced profile
- Sessions: ~3 sessions across 2 days
- Notable: Compact feature delivery — 2 plans, 2 days, zero rework

---

## Milestone: v2.1 — Account Recovery & Email Verification

**Shipped:** 2026-08-24
**Phases:** 3 (10-12) | **Plans:** 14 | **Timeline:** 2026-08-08 → 2026-08-19

### What Was Built
- Reusable, provider-abstracted `EmailSender` seam with a no-op logging default (no network sends in dev/CI)
- Password recovery — enumeration-safe forgot/reset with SHA-256 hashed single-use 30-min tokens, per-email + per-IP Bucket4j rate limiting, and full session revocation on reset
- Email verification on signup — hashed single-use 24h tokens, `403 EMAIL_NOT_VERIFIED` hard login gate, enumeration-safe resend, and a V17 migration grandfathering existing accounts
- Account credential self-service — change-password (revoke all other sessions) and change-email (confirm the new address before it takes effect, 409 if already in use)
- Full Testcontainers integration coverage; entire suite migrated to the no-token register + login-gate contract

### What Worked
- **Pattern reuse across the milestone** — Phase 10's `EmailSender` seam and hashed-token layer were reused essentially unchanged by Phases 11 and 12, keeping each later phase small
- **Mirroring proven patterns** — the token store mirrored the existing `RefreshToken` pattern and the email seam mirrored the `PushProvider` abstraction, so there was little novel design risk
- **Enumeration-safety as a first-class requirement** — designing generic responses and rate limiting up front avoided retrofitting security later
- **No-op logging email provider** — kept dev/CI free of network dependencies while still exercising the full send path via a mocked sender in tests
- **Reusing Bucket4j** — explicitly choosing to reuse existing rate-limiting infra (an Out-of-Scope decision) avoided new infrastructure

### What Was Inefficient
- **Three-place security whitelist repetition** — each public endpoint had to be whitelisted in exactly three security places; easy to miss one and a recurring source of friction across Phases 10-12
- **Test-suite migration cost** — flipping register to the no-token + login-gate contract (Phase 11) required a sweeping update across the whole integration suite via a shared `markEmailVerified` helper
- **Retrospective/state drift** — v2.0's retrospective section was never appended before this milestone close (backfilled into the trend tables here)

### Patterns Established
- **Provider-abstracted seam + no-op default** — same shape as `PushProvider`; concrete provider swappable, stubbed in tests
- **Hashed single-use expiring token** — SHA-256 at rest, atomic single-use claim, short TTL, reused/expired rejected — one model across reset, verify, and email-change
- **Confirm-before-swap** — sensitive changes (email) are staged in a separate table and only applied after the new address is verified
- **Distinct ProblemDetail codes** — `403 EMAIL_NOT_VERIFIED`, `403 INVALID_CURRENT_PASSWORD` for precise client handling
- **Session revocation on credential change** — reset / change-password / confirm-email-change all revoke refresh tokens

### Key Lessons
1. **Establish the reusable seam first** — building the email/token foundation in Phase 10 made Phases 11-12 thin; front-load shared infrastructure
2. **Codify the security-whitelist checklist** — the "three places" for public-endpoint whitelisting should be a documented step to avoid repeated misses
3. **Grandfather migrations for hard gates** — introducing a login gate on existing users requires a backfill (V17) so no current user is locked out on rollout
4. **Contract changes ripple through tests** — a change to a core contract (register response) can require a suite-wide migration; isolate via a shared helper

### Cost Observations
- Sessions: multi-session across ~11 days (2026-08-08 → 2026-08-19)
- Notable: heavy pattern reuse kept later phases small despite 14 plans total

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Timeline | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 8 days | 6 | Initial vertical MVP delivery |
| v1.1 | 2 days | 1 | Compact feature phase — schema + query + tests |
| v2.0 | ~5 weeks | 2 | Provider abstraction + async event-driven delivery |
| v2.1 | ~11 days | 3 | Reusable seam front-loaded, then thin dependent phases |

### Cumulative Quality

| Milestone | Tests | LOC | Test Files |
|-----------|-------|-----|------------|
| v1.0 | 163 | 8,433 | 19 |
| v1.1 | 180 | 8,880 | 19 |
| v2.0 | 221 | 10,608 | — |
| v2.1 | 260 | 12,774 | 36 |

### Top Lessons (Verified Across Milestones)

1. Vertical slicing (DB → API → tests) per phase enables clean incremental delivery
2. Real database testing (Testcontainers) catches issues that in-memory DBs hide
3. Test data isolation (unique coordinates, unique emails) prevents cross-test interference
4. Front-load reusable seams/abstractions (`PushProvider`, `EmailSender`) so dependent phases stay thin
5. Keep planning bookkeeping (REQUIREMENTS/STATE/RETROSPECTIVE) in sync at phase close, not deferred to milestone close
