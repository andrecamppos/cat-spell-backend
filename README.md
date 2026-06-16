<!-- GSD:docs-update -->
# Cat Spell Backend

A cat-first dating app backend API built with **Kotlin**, **Spring Boot 4**, and **PostgreSQL + PostGIS**. Users create profiles, register their cats, discover nearby cats via a geolocation-based feed, swipe to match, and chat in real time over WebSockets.

## Key Features

- **JWT Authentication** — register, login, token refresh with rotation and reuse detection
- **User Profiles** — display name, bio, age, gender, preferences, location (PostGIS), profile completeness checks
- **Photo Management** — presigned S3 uploads, server-side thumbnail generation, reorder and delete (user + cat photos)
- **Cat Profiles** — CRUD for cats with name, age, breed, bio; up to 5 cats per user
- **Discovery Feed** — geolocation-based, cursor-paginated, randomised feed of nearby cats
- **Swipe & Match** — like/pass swipes with mutual-match detection and deduplication
- **Real-time Chat** — STOMP over WebSocket, per-conversation messages, read receipts, unread counts, push notifications
- **OpenAPI** — grouped API docs via springdoc (`/v3/api-docs`)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4, JVM 17 |
| Framework | Spring Boot 4.0.6 |
| Database | PostgreSQL 16 + PostGIS 3.4 |
| Migrations | Flyway (12 versioned migrations) |
| Auth | JWT (jjwt 0.12.6), BCrypt |
| Storage | AWS S3 SDK 2.25.60 (MinIO locally) |
| WebSocket | Spring WebSocket + STOMP |
| API Docs | springdoc-openapi 2.8.8 |
| Rate Limiting | Bucket4j 8.10.1 |
| Thumbnails | Thumbnailator 0.4.20 |
| Spatial | Hibernate Spatial |
| Testing | JUnit 5, Testcontainers, MockK, H2 |

## Quick Start

**Prerequisites:** JDK 17+, [Podman](https://podman.io/) (or Docker)

```bash
# Clone and enter the project
git clone <repo-url> && cd cat-spell-backend

# Copy environment config
cp .env.example .env

# Start PostgreSQL + MinIO
podman compose up -d

# Run the app
./gradlew bootRun

# Run tests
./gradlew test

# Stop services
podman compose down
```

The API starts on **http://localhost:8080**. OpenAPI docs are at `/v3/api-docs`.

## Project Structure

```
src/main/kotlin/com/catspell/api/
├── auth/          # Registration, login, JWT refresh
├── profile/       # User profiles and photo uploads
├── cat/           # Cat profiles and cat photo uploads
├── discovery/     # Feed, swipe, owner profiles
├── match/         # Match creation and listing
├── chat/          # Conversations, messages, WebSocket
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
