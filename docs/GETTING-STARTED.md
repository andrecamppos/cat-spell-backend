<!-- GSD:docs-update -->
# Getting Started

This guide walks you through setting up Cat Spell Backend for local development from scratch.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 17+ | Kotlin/JVM runtime |
| Podman (or Docker) | Latest | Container runtime for PostgreSQL + MinIO |
| Git | Latest | Source control |

## 1. Clone the Repository

```bash
git clone <repo-url>
cd cat-spell-backend
```

## 2. Configure Environment

Copy the example environment file:

```bash
cp .env.example .env
```

The defaults work for local development. See [Configuration](CONFIGURATION.md) for all available settings.

## 3. Start Infrastructure

Start PostgreSQL (with PostGIS) and MinIO (S3-compatible storage):

```bash
podman compose up -d
```

This starts:
- **PostgreSQL 16 + PostGIS 3.4** on port `5432` (database: `catspell`, user: `catspell`, password: `catspell`)
- **MinIO** on port `9000` (API) and `9001` (console, user: `catspell`, password: `catspell123`)

Verify containers are running:

```bash
podman compose ps
```

## 4. Run the Application

```bash
./gradlew bootRun
```

Flyway automatically runs all database migrations on startup. The API will be available at **http://localhost:8080**.

### With Dev Profile

For SQL logging and debug output:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 5. Verify the Setup

Check the health endpoint:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status": "UP"}
```

Check the OpenAPI docs:

```bash
curl http://localhost:8080/v3/api-docs | head -20
```

## 6. Try the API

### Register a User

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "password123"}'
```

Response:

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "550e8400-..."
}
```

### Create a Profile

```bash
curl -X POST http://localhost:8080/api/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "displayName": "Cat Lover",
    "bio": "I love cats!",
    "dateOfBirth": "1995-06-15",
    "gender": "FEMALE",
    "genderPreference": "EVERYONE",
    "ageMin": 18,
    "ageMax": 40,
    "maxDistanceKm": 50
  }'
```

### Register a Cat

```bash
curl -X POST http://localhost:8080/api/cats \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "name": "Whiskers",
    "age": 3,
    "ageUnit": "YEARS",
    "breed": "Persian",
    "bio": "Fluffy and friendly"
  }'
```

## 7. Stop Infrastructure

```bash
podman compose down
```

To also remove stored data:

```bash
podman compose down -v
```

## Next Steps

- [Development](DEVELOPMENT.md) — project structure, coding conventions, adding new features
- [Testing](TESTING.md) — running and writing tests
- [API Reference](API.md) — complete endpoint documentation
