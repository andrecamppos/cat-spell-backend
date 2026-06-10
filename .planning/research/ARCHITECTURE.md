# Architecture Research

**Domain:** Dating app backend (Kotlin / Spring Boot)
**Researched:** 2025-06-09
**Confidence:** HIGH

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Mobile App (separate repo)              │
├─────────────────────────────────────────────────────────────┤
│                    REST API + WebSocket                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │   Auth   │  │ Profiles │  │ Matching │  │   Chat   │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │              │              │              │          │
├───────┴──────────────┴──────────────┴──────────────┴─────────┤
│                      Service Layer                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ AuthSvc  │  │ProfileSvc│  │MatchSvc  │  │ ChatSvc  │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │              │              │              │          │
├───────┴──────────────┴──────────────┴──────────────┴─────────┤
│                      Data Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  PostgreSQL   │  │   S3/MinIO   │  │  WebSocket   │       │
│  │  + PostGIS    │  │   (photos)   │  │   Broker     │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| Auth Controller | Registration, login, token refresh, password reset | `@RestController` with `/api/auth/**` endpoints |
| Profile Controller | User CRUD, cat CRUD, photo management | `@RestController` with `/api/users/**`, `/api/cats/**` |
| Discovery Controller | Feed generation, swipe actions (like/pass) | `@RestController` with `/api/discover/**` |
| Match Controller | Match listing, unmatch | `@RestController` with `/api/matches/**` |
| Chat WebSocket Handler | Real-time message delivery, typing indicators, read receipts | `@MessageMapping` STOMP endpoints |
| Chat REST Controller | Message history, conversation listing | `@RestController` with `/api/chat/**` |
| Auth Service | JWT creation/validation, password hashing, email verification | Spring Security + jjwt |
| Profile Service | User/cat profile logic, photo URL generation | JPA repositories + S3 presigned URLs |
| Match Service | Matching algorithm, score computation, mutual match detection | Custom scoring engine + JPA |
| Chat Service | Message persistence, conversation management | JPA + WebSocket message broker |
| Geolocation Service | Distance calculation, radius filtering | PostGIS `ST_DWithin` queries |

## Recommended Project Structure

```
src/main/kotlin/com/catspell/
├── config/                  # Spring configuration
│   ├── SecurityConfig.kt    # JWT filter, endpoint security
│   ├── WebSocketConfig.kt   # STOMP broker, endpoint registration
│   ├── S3Config.kt          # AWS S3 client bean
│   └── JacksonConfig.kt     # Kotlin module registration
├── auth/                    # Authentication domain
│   ├── controller/          # Auth REST endpoints
│   ├── service/             # Auth business logic
│   ├── dto/                 # Request/response DTOs
│   ├── entity/              # User entity (auth fields)
│   └── repository/          # User repository
├── profile/                 # Profile domain (users + cats)
│   ├── controller/          # Profile REST endpoints
│   ├── service/             # Profile business logic
│   ├── dto/                 # Profile DTOs
│   ├── entity/              # User profile, Cat, Photo entities
│   └── repository/          # Profile repositories
├── discovery/               # Discovery & matching domain
│   ├── controller/          # Discovery feed, swipe endpoints
│   ├── service/             # Feed generation, matching algorithm
│   ├── dto/                 # Discovery DTOs
│   └── repository/          # Swipe/match repositories
├── chat/                    # Chat domain
│   ├── controller/          # Chat REST + WebSocket handlers
│   ├── service/             # Message persistence, delivery
│   ├── dto/                 # Message DTOs
│   ├── entity/              # Conversation, Message entities
│   └── repository/          # Chat repositories
├── photo/                   # Photo management
│   ├── controller/          # Upload/delete endpoints
│   └── service/             # S3 presigned URL generation
├── common/                  # Shared utilities
│   ├── exception/           # Global exception handler
│   ├── security/            # JWT filter, auth utilities
│   └── dto/                 # Shared response wrappers
└── CatSpellApplication.kt   # Main entry point

src/main/resources/
├── db/migration/            # Flyway SQL migrations
├── application.yml          # Main config
└── application-dev.yml      # Dev profile (Docker Compose)

src/test/kotlin/com/catspell/
├── auth/                    # Auth tests
├── profile/                 # Profile tests
├── discovery/               # Discovery/matching tests
├── chat/                    # Chat tests
└── integration/             # Full integration tests (Testcontainers)
```

### Structure Rationale

- **Domain-based packages:** Each feature domain (auth, profile, discovery, chat) is self-contained with its own controller/service/repository. Easier to navigate and reason about than layer-based packaging.
- **Shared `common/`:** Cross-cutting concerns (exception handling, security filters, response wrappers) live in common to avoid duplication.
- **`photo/` as separate domain:** Photo operations (presigned URLs, validation) are used by both user and cat profiles — extract into shared module.

## Architectural Patterns

### Pattern 1: Domain-Scoped Packages

**What:** Organize by business domain (auth, profile, discovery, chat) not by technical layer (controllers, services, repositories).
**When to use:** Always for Spring Boot apps with 3+ domains.
**Trade-offs:** Slightly more files per package, but vastly better navigability and encapsulation.

```kotlin
// Each domain owns its full vertical slice
com.catspell.discovery.controller.DiscoveryController
com.catspell.discovery.service.MatchingService
com.catspell.discovery.repository.SwipeRepository
```

### Pattern 2: DTO Projection for API Responses

**What:** Never expose JPA entities directly in API responses. Use DTOs that project exactly the fields needed.
**When to use:** Every API endpoint.
**Trade-offs:** More mapping code, but decouples internal model from API contract.

```kotlin
// Entity — internal
@Entity
class Cat(
    @Id val id: UUID,
    val name: String,
    val breed: String?,
    @ManyToOne val owner: User, // internal relationship
)

// DTO — API response
data class CatProfileResponse(
    val id: UUID,
    val name: String,
    val breed: String?,
    val photos: List<String>,
    val traits: List<String>,
    // No owner reference — cat-first reveal
)
```

### Pattern 3: JWT Stateless Auth with Refresh Tokens

**What:** Short-lived access tokens (15 min) + long-lived refresh tokens (7 days). Access token in header, refresh token for renewal.
**When to use:** Mobile app backends where session cookies don't work.
**Trade-offs:** Can't invalidate access tokens server-side (use short expiry). Refresh tokens need server-side storage for revocation.

## Data Flow

### Request Flow (REST)

```
[Mobile App]
    ↓ (HTTP + Bearer JWT)
[SecurityFilterChain] → JWT Validation → SecurityContext
    ↓
[Controller] → DTO Mapping → [Service] → Business Logic → [Repository] → [PostgreSQL]
    ↓
[Response DTO] → JSON Serialization → [Mobile App]
```

### WebSocket Flow (Chat)

```
[Mobile App]
    ↓ (WS CONNECT + JWT token)
[HandshakeInterceptor] → JWT Validation
    ↓
[STOMP SUBSCRIBE] → /user/queue/messages
    ↓
[STOMP SEND] → /app/chat.send
    ↓
[MessageMapping Handler] → [ChatService] → Persist to DB → [SimpleBroker] → Deliver to recipient
```

### Key Data Flows

1. **Discovery flow:** App requests feed → DiscoveryService queries eligible profiles (not yet seen, within radius, passes matching algorithm) → Returns cat profiles sorted by match score
2. **Swipe flow:** User likes/passes → SwipeService records action → If mutual like detected → MatchService creates match → Notification to both users
3. **Chat flow:** User sends message via STOMP → ChatService persists → Broker routes to recipient's subscription → Read receipt returned via STOMP

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-5k users | Monolith is fine. Single Spring Boot instance, PostgreSQL, embedded STOMP broker |
| 5k-50k users | Add Redis for WebSocket session registry (multi-instance). Database connection pooling (HikariCP). CDN for S3 photos |
| 50k+ users | External message broker (RabbitMQ) for STOMP. Read replicas for PostgreSQL. Separate chat service. Matching algorithm caching |

### Scaling Priorities

1. **First bottleneck:** Database queries for discovery feed (complex geospatial + scoring). Fix: Indexed PostGIS queries, materialized scoring, pagination
2. **Second bottleneck:** WebSocket connections per instance. Fix: Redis-backed session registry, horizontal scaling with sticky sessions or external broker

## Anti-Patterns

### Anti-Pattern 1: Exposing JPA Entities in API

**What people do:** Return `@Entity` objects directly from controllers
**Why it's wrong:** Lazy loading exceptions, circular references (User↔Cat), leaks internal fields, locks API to DB schema
**Do this instead:** Always map to DTOs. Use Kotlin extension functions for clean mapping

### Anti-Pattern 2: Blocking in WebSocket Handlers

**What people do:** Database queries directly in `@MessageMapping` handlers
**Why it's wrong:** Blocks the WebSocket thread pool, degrades all connected users
**Do this instead:** Offload persistence to a service with `@Async` or use virtual threads (Java 21+)

### Anti-Pattern 3: N+1 Queries in Feed Generation

**What people do:** Load cat profiles one-by-one when generating discovery feed
**Why it's wrong:** Feed of 20 cats = 20+ DB queries = slow response
**Do this instead:** Use `JOIN FETCH` or `@EntityGraph` to batch-load related data in single query

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| AWS S3 / MinIO | Presigned URL generation (no proxy) | App generates upload URL, client uploads directly to S3. Saves bandwidth |
| SMTP (email) | Spring Mail with async sending | Email verification, password reset. Use async to avoid blocking auth flow |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Auth ↔ Profile | Shared User entity | Auth owns credentials, Profile owns bio/photos. Same DB table, different concerns |
| Discovery ↔ Profile | Service call | Discovery reads profiles via ProfileService, doesn't access repositories directly |
| Chat ↔ Match | Service call | Chat checks match exists before allowing messages |

## Sources

- Spring Boot 4.x official documentation
- Spring WebSocket + STOMP documentation
- Hibernate Spatial + PostGIS integration guides
- Dating app architecture case studies (public engineering blogs)

---
*Architecture research for: dating app backend (Kotlin/Spring Boot)*
*Researched: 2025-06-09*
