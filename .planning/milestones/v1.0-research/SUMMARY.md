# Project Research Summary

**Project:** Cat Spell Backend
**Domain:** Dating app backend (niche/cat-focused, Kotlin + Spring Boot)
**Researched:** 2025-06-09
**Confidence:** HIGH

## Executive Summary

Cat Spell is a dating app backend built on Kotlin + Spring Boot + PostgreSQL — a well-established stack for this type of application. The core differentiator is a "cat-first" discovery mechanic where users browse cat profiles and the owner is revealed behind a deliberate tap. This requires careful API design to enforce the reveal boundary and a multi-factor matching algorithm that combines cat compatibility with human preferences.

The recommended approach is a modular Spring Boot monolith organized by business domain (auth, profile, discovery, chat). PostGIS handles geolocation filtering, Spring WebSocket + STOMP powers real-time chat, and S3-compatible storage manages photos. The build order should follow the dependency chain: foundation → auth → profiles → discovery/matching → chat.

The main risks are: Kotlin/Hibernate friction (entity design), WebSocket security gaps, N+1 query performance in the discovery feed, and match race conditions. All are well-understood and preventable with proper patterns established early.

## Key Findings

### Recommended Stack

Kotlin 2.0 + Spring Boot 3.3 + PostgreSQL 16 with PostGIS. The stack is mature and well-documented. Key additions: Hibernate Spatial for geolocation, jjwt for JWT tokens, AWS SDK Kotlin for S3, and Spring WebSocket + STOMP for real-time chat.

**Core technologies:**
- **Spring Boot 3.3:** Full-featured framework with REST, WebSocket, Security, Data JPA — all batteries included
- **PostgreSQL + PostGIS:** Relational model fits user/cat/match data perfectly; PostGIS provides industrial-strength geospatial queries
- **Spring WebSocket + STOMP:** Built-in real-time messaging with subscriptions and routing, no external broker needed for v1
- **AWS SDK Kotlin + S3/MinIO:** Standard photo storage with presigned URLs for direct client uploads

### Expected Features

**Must have (table stakes):**
- Email/password auth with JWT
- User and cat profiles with photos
- Swipe/browse discovery feed
- Mutual matching
- Real-time chat
- Geolocation filtering

**Should have (competitive — Cat Spell differentiators):**
- Cat-first reveal mechanic (API enforces cat→owner reveal order)
- Cat compatibility scoring (temperament, energy, indoor/outdoor)
- Lifestyle signals from cat ownership patterns
- Multi-cat household with primary cat selection

**Defer (v2+):**
- Block/report/unmatch (add before wider launch)
- Push notifications
- Admin moderation panel
- OAuth social login

### Architecture Approach

Monolithic Spring Boot application organized by business domain (auth, profile, discovery, chat, photo). Each domain owns its controller/service/repository vertical slice. REST API for most operations, WebSocket for real-time chat. PostgreSQL as single data store with Flyway migrations.

**Major components:**
1. **Auth domain** — Registration, login, JWT management, password reset
2. **Profile domain** — User profiles, cat profiles, photo management, multi-cat household logic
3. **Discovery domain** — Feed generation, swipe actions, matching algorithm, mutual match detection
4. **Chat domain** — WebSocket real-time messaging, message persistence, conversation management

### Critical Pitfalls

1. **Kotlin entity gotchas** — Don't use data classes for JPA entities; use regular classes with `kotlin-jpa` plugin
2. **N+1 queries in feed** — Use `@EntityGraph` or `JOIN FETCH` for discovery feed queries
3. **WebSocket auth gap** — Validate JWT on WebSocket CONNECT and verify identity on every STOMP frame
4. **Match race condition** — Unique constraint on ordered match pairs + `ON CONFLICT DO NOTHING`
5. **Geospatial indexing** — Create GiST index from first migration; use `geography` type not `geometry`

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Foundation & Auth
**Rationale:** Everything depends on project setup and authentication
**Delivers:** Spring Boot project scaffold, database with Flyway, JWT auth (register, login, refresh, password reset)
**Addresses:** AUTH table stakes
**Avoids:** Kotlin entity pitfall (established early), JWT key management pitfall

### Phase 2: Profiles & Photos
**Rationale:** Profiles are prerequisites for discovery. Cat-first reveal is the core differentiator and must be designed before feed.
**Delivers:** User profiles, cat profiles with traits, multi-cat household, S3 photo upload
**Uses:** JPA entities, S3 SDK, Flyway migrations
**Implements:** Profile domain, Photo domain

### Phase 3: Discovery & Matching
**Rationale:** Depends on profiles and geolocation being in place. Core product loop.
**Delivers:** Geolocation storage + filtering, cat compatibility scoring, lifestyle signal scoring, combined match algorithm, discovery feed, swipe actions, mutual match detection
**Addresses:** Core differentiator features
**Avoids:** N+1 query pitfall, geospatial index pitfall, match race condition

### Phase 4: Real-Time Chat
**Rationale:** Chat depends on match existing. WebSocket has unique security concerns.
**Delivers:** WebSocket + STOMP infrastructure, real-time messaging, typing indicators, read receipts, message history
**Addresses:** Chat table stakes
**Avoids:** WebSocket auth pitfall, blocking handler pitfall

### Phase 5: API Polish & Hardening
**Rationale:** Final phase ensures production readiness across all domains.
**Delivers:** OpenAPI documentation, rate limiting, input validation hardening, error handling, health checks, integration tests
**Addresses:** Production readiness

### Phase Ordering Rationale

- Foundation → Auth → Profiles → Discovery → Chat follows the strict dependency chain
- Cat profiles before discovery ensures the cat-first mechanic is designed correctly
- Matching algorithm in same phase as discovery because they're tightly coupled
- Chat last because it's independent once matches exist
- Polish phase catches cross-cutting concerns after all features are built

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 3:** Matching algorithm design — scoring weights, normalization, tuning approach
- **Phase 4:** Spring WebSocket + STOMP security configuration — less documented than REST security

Phases with standard patterns (skip research-phase):
- **Phase 1:** Standard Spring Boot setup + JWT auth — very well documented
- **Phase 2:** Standard CRUD + S3 — established patterns

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Kotlin + Spring Boot is proven, well-documented |
| Features | HIGH | Dating app features are well-understood; cat-specific features are novel but simple |
| Architecture | HIGH | Monolith with domain packages is standard Spring Boot |
| Pitfalls | HIGH | Common Spring Boot + Kotlin pitfalls are well-documented |

**Overall confidence:** HIGH

### Gaps to Address

- Matching algorithm weights — need experimentation during Phase 3 to tune cat compatibility vs. human preference balance
- Chat scalability — embedded STOMP broker is fine for v1, but plan migration path to external broker

## Sources

### Primary (HIGH confidence)
- Spring Boot 3.x official documentation
- Hibernate Spatial + PostGIS documentation
- Kotlin + JPA best practices (JetBrains)

### Secondary (MEDIUM confidence)
- Dating app engineering blog posts (Tinder, Bumble scale lessons)
- AWS SDK for Kotlin documentation

### Tertiary (LOW confidence)
- Cat compatibility scoring — novel domain, no existing research (design from first principles)

---
*Research completed: 2025-06-09*
*Ready for roadmap: yes*
