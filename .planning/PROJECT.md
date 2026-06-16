# Cat Spell Backend

## What This Is

The backend API for Cat Spell — a dating app for cat lovers and cat owners. It powers a "see the cat before the owner" experience where users browse cat profiles in the swipe screen and can optionally tap through to the owner's profile. The backend serves a mobile app (built in a separate repo) via REST and WebSocket APIs, handling user/cat profiles, swipe-based discovery with geolocation filtering, mutual match detection, real-time chat, and production-ready API hardening.

## Core Value

Cat-first discovery — users fall for the cat first, then meet the person. The reveal mechanic and cat-influenced matching must work flawlessly; everything else supports this core loop.

## Requirements

### Validated

- ✓ Email/password authentication with JWT tokens — v1.0 (Phase 1)
- ✓ Refresh token rotation with theft detection — v1.0 (Phase 1)
- ✓ RFC 7807 error handling — v1.0 (Phase 1)
- ✓ User profiles (bio, photos, preferences) — v1.0 (Phase 2)
- ✓ GPS geolocation storage and distance-based filtering with configurable radius — v1.0 (Phase 2)
- ✓ S3-compatible photo upload and storage with presigned URLs — v1.0 (Phase 2)
- ✓ Cat profiles (name, age, breed, photos) with multi-cat support — v1.0 (Phase 3)
- ✓ Cat-first swipe feed (serves cat profiles, owner profile accessible from detail view) — v1.0 (Phase 4)
- ✓ Mutual match detection (both users like each other's cats) — v1.0 (Phase 4)
- ✓ Real-time WebSocket chat with STOMP for matched users — v1.0 (Phase 5)
- ✓ Conversation list with unread counts and mark-read — v1.0 (Phase 5)
- ✓ Offline message delivery on WebSocket reconnect — v1.0 (Phase 5)
- ✓ OpenAPI/Swagger documentation auto-generated — v1.0 (Phase 6)
- ✓ Rate limiting on authentication endpoints — v1.0 (Phase 6)
- ✓ Health indicators (S3, WebSocket, DB) — v1.0 (Phase 6)
- ✓ 163 integration tests across all domains — v1.0 (Phase 6)

### Active

- [ ] Cat compatibility scoring (temperament, energy, indoor/outdoor)
- [ ] Lifestyle signal scoring from cat ownership patterns
- [ ] Primary/featured cat designation for swipe feed
- [ ] Typing indicators and read receipts in chat
- [ ] Block/report/unmatch safety features

### Out of Scope

- Mobile app — separate project/repo
- Admin moderation panel — v2 (after block/report is built)
- OAuth/social login — v2 (email+password sufficient for MVP)
- Push notifications — v2 (requires mobile app integration)
- Chat media sharing — v2 (text-only proven sufficient)
- Payment/subscription features — v2 (premature before community)
- Video profiles — high storage/bandwidth cost, moderation burden
- AI-generated cat descriptions — removes personal touch

## Context

- **Current state:** v1.0 shipped. 8,433 LOC Kotlin, 163 integration tests, 122 commits.
- **Tech stack:** Kotlin + Spring Boot 4.0, PostgreSQL + PostGIS, S3 (MinIO local), WebSocket STOMP, Flyway, Testcontainers
- **Domain:** Niche dating app targeting cat lovers/owners
- **Architecture:** Backend-only REST + WebSocket API. Mobile app is a separate project.
- **Reveal mechanic:** Two stages — Stage 1: Cat profile shown in swipe screen. Stage 2: Owner profile accessible by tapping into cat detail view
- **Multi-cat:** Users can register up to 5 cats. Discovery feed shows cats (not owners). All cats visible in detail view.
- **Chat:** WebSocket STOMP messaging with lazy conversation creation, offline delivery, and unread tracking. Unlocked after mutual match.
- **Testing:** Full Testcontainers-based integration tests (PostgreSQL + PostGIS + MinIO). No H2.

## Constraints

- **Tech stack:** Kotlin + Spring Boot — non-negotiable
- **Database:** PostgreSQL + PostGIS — non-negotiable
- **Photo storage:** S3-compatible (AWS S3 or MinIO for local dev)
- **Scope:** Backend API only — no frontend, no mobile app code in this repo
- **Auth:** Email + password with JWT for v1 (no OAuth)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin + Spring Boot 4.0 | Owner preference, strong ecosystem for backend APIs | ✓ Good — productive, mature ecosystem |
| PostgreSQL + PostGIS | Relational model fits user/cat/match; PostGIS for geolocation | ✓ Good — native distance queries performant |
| Cat-first swipe (two-stage reveal) | Core differentiator — playful and curiosity-driven | ✓ Good — clean API separation enforced |
| 5-cat limit per user | Prevents abuse while supporting multi-cat households | ✓ Good — reasonable constraint |
| WebSocket STOMP over polling | Real-time feel expected in modern dating apps | ✓ Good — Spring Boot native support |
| Lazy conversation creation | Conversation created on first message, not on match | ✓ Good — avoids empty conversation clutter |
| S3 presigned URLs | Client uploads directly to S3, backend never handles file bytes | ✓ Good — scalable, MinIO local dev parity |
| Testcontainers over H2 | Real PostgreSQL + PostGIS in tests, no dialect mismatches | ✓ Good — caught real bugs H2 would miss |
| Bucket4j rate limiting | Lightweight, no Redis dependency for MVP | ✓ Good — simple ConcurrentHashMap sufficient |
| Defer moderation to v2 | Focus v1 on core matching/chat loop | — Pending (needed before public launch) |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-16 after v1.0 milestone*
