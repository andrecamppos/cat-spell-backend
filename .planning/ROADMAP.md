# Roadmap: Cat Spell Backend

**Created:** 2025-06-09
**Milestone:** v1.0 — MVP Backend
**Phases:** 6
**Mode:** Vertical MVP

## Overview

| # | Phase | Goal | Requirements | Plans |
|---|-------|------|--------------|-------|
| 1 | Foundation & Auth | Runnable Spring Boot app with JWT auth | AUTH-01, AUTH-02, AUTH-03 | — |
| 2 | User Profiles & Photos | Complete user identity with photos and location | PROF-01, PROF-02, PROF-03, PROF-04, PROF-05 | — |
| 3 | Cat Profiles | Cat identity system — the core data model | CAT-01, CAT-02, CAT-03, CAT-04, CAT-05 | — |
| 4 | Discovery & Matching | Cat-first feed, swipe actions, geo filtering, mutual matching | DISC-01, DISC-02, DISC-03, DISC-04, DISC-05, DISC-06, DISC-07 | — |
| 5 | Real-Time Chat | WebSocket messaging between matched users | CHAT-01, CHAT-02, CHAT-03 | — |
| 6 | API Polish & Integration Tests | Production-ready API with docs, validation, and test coverage | — | — |

---

## Phase Details

### Phase 1: Foundation & Auth
**Goal:** Deliver a runnable Spring Boot application with PostgreSQL, Flyway migrations, and complete JWT authentication (register, login, refresh).
**Mode:** mvp
**Requirements:** AUTH-01, AUTH-02, AUTH-03
**Plans:** 3 (01-scaffold+auth [W1], 02-refresh-tokens [W2], 03-error-handling [W2])
**Planning Status:** ✅ Complete (2025-06-09)
**Execution Status:** ✅ Complete (2025-06-09) — 3/3 plans done, 26 tests passing
**Success Criteria:**
1. Spring Boot app starts and connects to PostgreSQL (Podman for local dev)
2. User can register with email and password via REST endpoint
3. User can log in and receive a JWT access token and refresh token
4. User can refresh an expired access token using a valid refresh token
5. Protected endpoints reject requests without valid JWT

### Phase 2: User Profiles & Photos
**Goal:** Deliver user profile management with photo uploads to S3-compatible storage and GPS location storage.
**Mode:** mvp
**Requirements:** PROF-01, PROF-02, PROF-03, PROF-04, PROF-05
**Success Criteria:**
1. User can create a profile with display name, bio, and dating preferences
2. User can edit their own profile fields
3. User can upload photos via S3 presigned URLs (MinIO for local dev)
4. User can delete their own photos
5. User can set and update GPS coordinates on their profile

### Phase 3: Cat Profiles
**Goal:** Deliver the cat profile system — users can create, manage, and showcase their cats with photos.
**Mode:** mvp
**Requirements:** CAT-01, CAT-02, CAT-03, CAT-04, CAT-05
**Success Criteria:**
1. User can create a cat profile with name, age, and breed
2. User can upload photos for a cat profile via S3 presigned URLs
3. User can edit a cat profile's details
4. User can delete a cat profile
5. User can have multiple cat profiles linked to their account

### Phase 4: Discovery & Matching
**Goal:** Deliver the cat-first discovery feed with geolocation filtering, swipe actions, seen-profile tracking, and mutual match detection.
**Mode:** mvp
**Requirements:** DISC-01, DISC-02, DISC-03, DISC-04, DISC-05, DISC-06, DISC-07
**Success Criteria:**
1. Discovery feed returns cat profiles (not owner profiles) — cat-first reveal enforced
2. User can view a cat's owner profile via a separate endpoint (accessible from cat detail, not from swipe)
3. User can like or pass on a cat profile
4. Feed filters results by configurable distance radius using PostGIS
5. Previously seen profiles (liked or passed) are excluded from future feeds
6. Mutual match is created when both users like each other's cats
7. User can view their list of matches

### Phase 5: Real-Time Chat
**Goal:** Deliver WebSocket-based real-time messaging between matched users with message persistence and conversation listing.
**Mode:** mvp
**Requirements:** CHAT-01, CHAT-02, CHAT-03
**Success Criteria:**
1. Matched users can send and receive text messages in real time via WebSocket (STOMP)
2. Messages are persisted to PostgreSQL
3. User can retrieve paginated message history for a conversation
4. User can view a list of their conversations (one per match)
5. WebSocket connections are authenticated via JWT

### Phase 6: API Polish & Integration Tests
**Goal:** Harden the API for production readiness — OpenAPI docs, input validation, error handling, rate limiting, health checks, and integration test coverage.
**Mode:** mvp
**Success Criteria:**
1. OpenAPI/Swagger documentation is auto-generated and accessible
2. All endpoints have proper input validation with meaningful error messages
3. Global exception handler returns consistent error response format
4. Rate limiting is applied to authentication endpoints
5. Health check and info actuator endpoints are exposed
6. Integration tests cover all critical paths using Testcontainers

---

## Dependency Graph

```
Phase 1: Foundation & Auth
    ↓
Phase 2: User Profiles & Photos
    ↓
Phase 3: Cat Profiles
    ↓
Phase 4: Discovery & Matching
    ↓
Phase 5: Real-Time Chat
    ↓
Phase 6: API Polish & Integration Tests
```

Phases are sequential — each builds on the previous. Phase 6 is a cross-cutting hardening pass.

---
*Roadmap created: 2025-06-09*
*Last updated: 2025-06-09 after initial creation*
