<!-- generated-by: gsd-doc-writer -->
# Cat Spell Backend

Backend API for **Cat Spell** — a dating app for cat lovers. Users with cats show cat-first in the discovery feed (fall for the cat, then meet the person). Users without cats appear as human cards. Built with **Kotlin 2.4**, **Spring Boot 4.0.6**, and **PostgreSQL 16 + PostGIS 3.4**.

## Key Features

- **JWT Authentication** — register, login, token refresh with rotation and theft detection
- **User Profiles** — display name, bio, age, gender, preferences, GPS location (PostGIS), profile completeness checks
- **Photo Management** — presigned S3 uploads, server-side thumbnail generation, reorder and delete (user and cat photos)
- **Cat Profiles** — CRUD for cats with name, age, breed, bio; up to 5 cats per user; cat ownership is optional
- **Mixed Discovery Feed** — geolocation-based, cursor-paginated feed; cat cards for cat owners, human cards for catless users
- **Swipe & Match** — LIKE/PASS swipes on cat profiles or user profiles, mutual-match detection, deduplication
- **Real-time Chat** — STOMP over WebSocket, lazy conversation creation, unread counts, mark-read, offline message delivery
- **API Hardening** — RFC 7807 error responses, Bucket4j rate limiting on auth endpoints, health indicators (S3, WebSocket, DB)
- **OpenAPI** — grouped API docs via springdoc (`/v3/api-docs`)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4, JVM 17 |
| Framework | Spring Boot 4.0.6 |
| Database | PostgreSQL 16 + PostGIS 3.4 |
| Migrations | Flyway (13 versioned migrations) |
| Auth | JWT (jjwt 0.12.6), BCrypt |
| Storage | AWS S3 SDK 2.25.60 (MinIO for local dev) |
| WebSocket | Spring WebSocket + STOMP |
| API Docs | springdoc-openapi 2.8.8 |
| Rate Limiting | Bucket4j 8.10.1 |
| Thumbnails | Thumbnailator 0.4.20 |
| Spatial | Hibernate Spatial |
| Testing | JUnit 5, Testcontainers 1.20.6, MockK 1.13.11 |

## Quick Start

**Prerequisites:** JDK 17+, [Podman](https://podman.io/) or Docker

```bash
# Clone and enter the project
git clone <repo-url> && cd cat-spell-backend

# Copy environment config
cp .env.example .env

# Start PostgreSQL + MinIO
podman compose up -d          # or: docker compose up -d

# Run the app
./gradlew bootRun

# Run tests (starts its own containers via Testcontainers)
./gradlew test
```

The API starts on **http://localhost:8080**. OpenAPI spec is at `/v3/api-docs`.

## Project Structure

```
src/main/kotlin/com/catspell/api/
├── auth/          # Registration, login, JWT refresh
├── profile/       # User profiles and photo uploads
├── cat/           # Cat profiles and cat photo uploads
├── discovery/     # Mixed feed, swipe, owner/user profiles
├── match/         # Match listing
├── chat/          # Conversations, messages, WebSocket STOMP
└── common/        # Security, exceptions, health, OpenAPI config
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Getting Started](docs/GETTING-STARTED.md)
- [Development](docs/DEVELOPMENT.md)
- [Testing](docs/TESTING.md)
- [API Reference](docs/API.md)
- [Configuration](docs/CONFIGURATION.md)
- [Deployment](docs/DEPLOYMENT.md)
