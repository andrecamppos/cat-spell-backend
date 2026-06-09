# Walking Skeleton — Cat Spell Backend

**Phase:** 1
**Generated:** 2025-06-09

## Capability Proven End-to-End

A new user can register with email and password, receive JWT tokens, log in, and access a protected endpoint — proving the full Spring Boot + PostgreSQL + JWT stack works end-to-end.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Spring Boot 3.3.x (Kotlin 2.0.x) | User requirement; mature ecosystem with REST, Security, Data JPA, WebSocket all built-in |
| Build tool | Gradle 8.x (Kotlin DSL) | Standard for Kotlin projects, better Kotlin support than Maven |
| Data layer | PostgreSQL 16 + Spring Data JPA / Hibernate 6 + Flyway 10 | User requirement; relational model fits user/cat/match data; Flyway for versioned migrations |
| Auth | JWT access tokens (jjwt 0.12.x) + rotating DB-stored refresh tokens | Stateless API auth for mobile clients; rotation detects token theft |
| Local dev | Docker Compose (PostgreSQL) | Reproducible local environment, matches production DB |
| Package structure | Domain-first vertical slices (`com.catspell.api.{domain}.*`) | Per user decision D-01; each domain owns controller/service/model sub-packages |
| Directory layout | `auth/controller/`, `auth/service/`, `auth/model/` + `common/config/`, `common/security/`, `common/exception/` | Per user decisions D-02, D-03, D-04 |

## Stack Touched in Phase 1

- [x] Project scaffold (Spring Boot + Gradle + Kotlin, build config, application.yml)
- [x] Routing — `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`
- [x] Database — user registration (write), credential lookup (read), refresh token CRUD
- [ ] ~~UI — N/A (backend-only project, no frontend)~~
- [x] Deployment — Docker Compose for PostgreSQL + `./gradlew bootRun` documented

## Out of Scope (Deferred to Later Slices)

- User profiles (display name, bio, photos) — Phase 2
- Cat profiles — Phase 3
- Discovery feed and matching — Phase 4
- Real-time chat — Phase 5
- Email verification — deferred to v2
- Password reset — deferred to v2
- OAuth/social login — deferred to v2
- Rate limiting on auth endpoints — Phase 6

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions:

- Phase 2: User can create and edit their profile with photos and location
- Phase 3: User can create and manage cat profiles with photos
- Phase 4: User can discover cats in a feed, swipe, and get matched
- Phase 5: Matched users can chat in real time via WebSocket
- Phase 6: API hardened with docs, validation, rate limiting, and integration tests
