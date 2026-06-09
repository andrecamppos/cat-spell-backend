# Cat Spell Backend

## What This Is

The backend API for Cat Spell — a dating app for cat lovers and cat owners. It powers a "see the cat before the owner" experience where users browse cat profiles in the swipe screen and can optionally tap through to the owner's profile. The backend serves a mobile app (built in a separate repo) via REST and WebSocket APIs, handling user/cat profiles, cat-influenced matching, real-time chat, and geolocation-based discovery.

## Core Value

Cat-first discovery — users fall for the cat first, then meet the person. The reveal mechanic and cat-influenced matching must work flawlessly; everything else supports this core loop.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Email/password authentication with JWT tokens
- [ ] User profiles (bio, photos, preferences)
- [ ] Cat profiles (name, age, breed, photos, personality traits)
- [ ] Multi-cat households with primary/featured cat selection
- [ ] Cat-first swipe feed (serves cat profiles, owner profile accessible from detail view)
- [ ] Cat-influenced matching algorithm (cat compatibility + lifestyle signals)
- [ ] GPS geolocation storage and distance-based filtering with configurable radius
- [ ] Mutual match detection (both users like each other's cats)
- [ ] Real-time WebSocket chat for matched users
- [ ] S3-compatible photo upload and storage
- [ ] REST API for mobile app consumption

### Out of Scope

- Mobile app — separate project/repo
- Admin moderation panel — v2
- Block/report/unmatch — v2
- OAuth/social login — v2
- Push notifications — v2
- Chat icebreakers or photo sharing in chat — v2
- Payment/subscription features — v2

## Context

- **Domain:** Niche dating app targeting cat lovers/owners — a passionate but underserved audience
- **Architecture:** Backend-only. The mobile app is a separate project consuming this API
- **Reveal mechanic:** Two stages — Stage 1: Cat profile shown in swipe screen. Stage 2: Owner profile accessible by tapping into cat detail view (not visible from swipe screen)
- **Multi-cat:** Users can register multiple cats. One cat is designated as "primary/featured" for the swipe feed; additional cats are visible in the detail view
- **Matching signals:** Cat compatibility (temperament, energy level, indoor/outdoor preferences, friendliness with other cats) combined with owner lifestyle signals (cat count, breeds, care style)
- **Chat:** WebSocket-based real-time messaging with typing indicators and read receipts, unlocked after mutual match

## Constraints

- **Tech stack:** Kotlin + Spring Boot — non-negotiable
- **Database:** PostgreSQL — non-negotiable
- **Photo storage:** S3-compatible (AWS S3 or MinIO for local dev)
- **Scope:** Backend API only — no frontend, no mobile app code in this repo
- **Auth:** Email + password with JWT for v1 (no OAuth)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin + Spring Boot | Owner preference, strong ecosystem for backend APIs | — Pending |
| PostgreSQL | Relational data model fits user/cat/match relationships well, PostGIS for geolocation | — Pending |
| Cat-first swipe (two-stage reveal) | Core differentiator — keeps the experience playful and curiosity-driven | — Pending |
| Primary cat for swipe feed | Simplifies multi-cat UX while preserving household visibility in detail view | — Pending |
| WebSocket chat over polling | Real-time feel expected in modern dating apps | — Pending |
| S3-compatible storage | Industry standard, MinIO for local dev parity | — Pending |
| Defer moderation to v2 | Focus v1 on core matching/chat loop | — Pending |

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
*Last updated: 2025-06-09 after initialization*
