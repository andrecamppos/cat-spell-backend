<!-- GSD:docs-update -->
# Architecture

## Overview

Cat Spell Backend is a Spring Boot 4 monolith written in Kotlin, exposing a REST + WebSocket API. It follows a **domain-sliced package layout** where each business domain (auth, profile, cat, discovery, match, chat) is self-contained with its own controllers, services, models, and repositories.

## High-Level Diagram

```
┌──────────────┐     HTTP/WS      ┌────────────────────────────┐
│  Mobile App  │ ───────────────► │  Spring Boot 4 (Ktor-free) │
└──────────────┘                  │                            │
                                  │  ┌──────────────────────┐  │
                                  │  │ REST Controllers     │  │
                                  │  │ (Auth, Profile, Cat, │  │
                                  │  │  Discovery, Match,   │  │
                                  │  │  Conversations)      │  │
                                  │  └──────────┬───────────┘  │
                                  │             │              │
                                  │  ┌──────────▼───────────┐  │
                                  │  │ Service Layer        │  │
                                  │  │ (Business logic,     │  │
                                  │  │  validation, match   │  │
                                  │  │  detection)          │  │
                                  │  └──────────┬───────────┘  │
                                  │             │              │
                                  │  ┌──────────▼───────────┐  │
                                  │  │ JPA Repositories     │  │
                                  │  │ (Spring Data JPA +   │  │
                                  │  │  Hibernate Spatial)  │  │
                                  │  └──────────┬───────────┘  │
                                  │             │              │
                                  │  ┌──────────▼───────────┐  │
                                  │  │ WebSocket (STOMP)    │  │
                                  │  │ Chat messaging       │  │
                                  │  └──────────────────────┘  │
                                  └────────────┬───────────────┘
                                               │
                        ┌──────────────────────┼──────────────────┐
                        │                      │                  │
                ┌───────▼──────┐  ┌────────────▼───┐  ┌──────────▼──────┐
                │ PostgreSQL   │  │ S3 / MinIO     │  │ In-memory       │
                │ + PostGIS    │  │ (photo store)  │  │ STOMP broker    │
                └──────────────┘  └────────────────┘  └─────────────────┘
```

## Package Layout

```
com.catspell.api
├── CatSpellApplication.kt          # @SpringBootApplication entry point
├── auth/
│   ├── controller/AuthController    # POST /api/auth/{register,login,refresh}, GET /api/auth/me
│   ├── model/                       # User, RefreshToken entities, DTOs, repositories
│   └── service/AuthService          # Registration, login, token rotation with reuse detection
├── profile/
│   ├── controller/
│   │   ├── ProfileController        # CRUD /api/profile, PUT /api/profile/location, GET completeness
│   │   └── PhotoController          # /api/profile/photos — upload URL, confirm, delete, reorder, list
│   ├── model/                       # UserProfile (with PostGIS Point), UserPhoto, DTOs, repositories
│   └── service/
│       ├── ProfileService           # Profile CRUD, age/gender validation, location updates
│       ├── PhotoService             # Presigned uploads, thumbnail generation, reorder
│       └── StorageService           # S3 client wrapper (presigned URLs, get/put/delete)
├── cat/
│   ├── controller/
│   │   ├── CatProfileController     # CRUD /api/cats
│   │   └── CatPhotoController       # /api/cats/{catId}/photos — upload, confirm, delete, reorder, list
│   ├── model/                       # CatProfile, CatPhoto entities, DTOs, repositories
│   └── service/
│       ├── CatProfileService        # Cat CRUD, cascade photo cleanup on delete
│       └── CatPhotoService          # Cat photo upload/confirm/delete/reorder
├── discovery/
│   ├── controller/DiscoveryController  # GET /api/discovery/feed, GET owner profile, POST /api/discovery/swipe
│   ├── model/                          # Swipe entity, FeedItemProjection, DTOs, SwipeRepository
│   └── service/DiscoveryService        # Geo-based feed (PostGIS), cursor pagination, match detection on swipe
├── match/
│   ├── controller/MatchController   # GET /api/matches
│   ├── model/                       # Match entity, DTOs, MatchRepository
│   └── service/MatchService         # Idempotent match creation, ordered user-pair dedup
├── chat/
│   ├── config/WebSocketConfig       # STOMP broker config (/ws endpoint, /topic, /queue, /app prefixes)
│   ├── controller/
│   │   ├── ConversationController   # GET /api/conversations, POST mark-read, GET messages
│   │   └── ChatController           # @MessageMapping /app/chat.send (STOMP)
│   ├── model/                       # Conversation, ConversationParticipant, Message entities + repos
│   └── service/
│       ├── ChatService              # Send messages, list conversations, read receipts, unread delivery
│       └── WebSocketSessionListener # Session connect/disconnect hooks
└── common/
    ├── config/
    │   ├── SecurityConfig           # JWT filter chain, BCrypt, public endpoint whitelist
    │   └── OpenApiConfig            # Grouped OpenAPI definitions (auth, user, cats, discovery, chat)
    ├── exception/
    │   ├── Exceptions.kt            # Domain exceptions (12 types)
    │   └── GlobalExceptionHandler   # RFC 7807 ProblemDetail error responses
    ├── health/
    │   ├── S3HealthIndicator        # /actuator/health S3 connectivity check
    │   └── WebSocketHealthIndicator # /actuator/health WebSocket status
    └── security/
        ├── JwtService               # Token generation and validation (HS512)
        ├── JwtAuthenticationFilter   # OncePerRequestFilter extracting Bearer tokens
        └── WebSocketAuthInterceptor  # STOMP CONNECT JWT validation
```

## Domain Modules

### Auth
Stateless JWT authentication. Access tokens (HS512, 1h expiry) are passed as `Authorization: Bearer` headers. Refresh tokens are stored in the database with rotation — each refresh invalidates the previous token and issues a new one. Token reuse detection revokes all tokens for the user.

### Profile
User profiles include display name, bio, date of birth, gender, preferences, age range, max distance, and a PostGIS `Point` for location. The completeness endpoint checks required fields before allowing discovery. Photos are uploaded via presigned S3 URLs, then confirmed after the client uploads directly to S3. Thumbnails (200×200 JPEG) are generated server-side on confirmation.

### Cat
Each user may have up to 5 cat profiles. Cat profiles have name, age (with unit: MONTHS/YEARS), breed, and bio. Cat photos follow the same presigned-upload flow as user photos, scoped under `/api/cats/{catId}/photos`. Up to 10 photos per cat. Deleting a cat cascades to its photos (S3 objects + DB records).

### Discovery
The feed uses PostGIS `ST_DWithin` to find cats within the user's `maxDistanceKm`, excluding the user's own cats and already-swiped cats. Results are randomised per session using `setseed()` and cursor-paginated with base64-encoded `seed,offset` cursors. Swiping records a LIKE or PASS; mutual likes trigger automatic match creation.

### Match
Matches are created idempotently using ordered user-pair deduplication (`user1 < user2`). A unique constraint on `(user1_id, user2_id)` prevents race-condition duplicates. The match list endpoint returns the other user's profile summary plus their cats.

### Chat
Conversations are created lazily on first message for a match. Messages are sent via STOMP (`/app/chat.send`) and broadcast to `/topic/chat/{conversationId}`. Notifications go to `/user/{userId}/queue/notifications`. The REST API provides conversation listing (with unread counts), message history (cursor-paginated), and read receipts.

## Data Layer

- **ORM:** Spring Data JPA + Hibernate 6 with Hibernate Spatial for PostGIS geometry types
- **Migrations:** Flyway with 12 versioned SQL migrations (`V1` through `V12`)
- **Entities:** `allOpen` plugin applied to `@Entity`, `@MappedSuperclass`, `@Embeddable` for JPA proxy compatibility

### Key Entities

| Entity | Table | Notes |
|--------|-------|-------|
| `User` | `users` | Email + password hash |
| `RefreshToken` | `refresh_tokens` | Token rotation, revocation tracking |
| `UserProfile` | `user_profiles` | PostGIS Point for location |
| `UserPhoto` | `user_photos` | S3 key, thumbnail key, display order, status |
| `CatProfile` | `cat_profiles` | FK to User, age unit enum |
| `CatPhoto` | `cat_photos` | FK to CatProfile, same upload flow as UserPhoto |
| `Swipe` | `swipes` | FK to swiper, target user, cat profile |
| `Match` | `matches` | Unique constraint on ordered user pair |
| `Conversation` | `conversations` | FK to Match |
| `ConversationParticipant` | `conversation_participants` | FK to Conversation + User, lastReadAt |
| `Message` | `messages` | FK to Conversation + sender |

## Cross-Cutting Concerns

### Security
- Stateless sessions (`SessionCreationPolicy.STATELESS`)
- CSRF disabled (API-only, no browser forms)
- Public endpoints: `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/v3/api-docs/**`, `/actuator/health`, `/ws/**`
- All other endpoints require a valid JWT
- WebSocket connections authenticated via STOMP `CONNECT` frame interceptor

### Error Handling
All errors return [RFC 7807 Problem Detail](https://www.rfc-editor.org/rfc/rfc7807) JSON via `GlobalExceptionHandler`. Validation errors include a `violations` array with field-level details.

### Observability
- Spring Actuator health endpoint at `/actuator/health` (public, details require auth)
- Custom health indicators for S3 connectivity and WebSocket status
- Rate limiting via Bucket4j

### API Documentation
OpenAPI 3 definitions via springdoc, grouped by domain: auth, user, cats, discovery, chat. Available at `/v3/api-docs` (Swagger UI disabled by default).
