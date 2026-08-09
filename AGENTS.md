# AGENTS.md

Project-specific notes for AI agents working in this repository.

## Containers

- **This project uses Podman, not Docker.** Use `podman compose ...` (or `podman-compose ...` as a fallback on some setups) for all container operations. Do not assume Docker.
- Local services are defined in `docker-compose.yml`: PostgreSQL 16 + PostGIS 3.4 (port 5432) and MinIO (ports 9002/9001).

## Running the app

```bash
cp .env.example .env          # first time only
podman compose up -d          # start Postgres + MinIO
./gradlew bootRun             # boots on http://localhost:8080; Flyway runs migrations on startup
```

- **Fresh DB (cold start):** `podman compose down -v` wipes the `pgdata` volume so Flyway replays all migrations from an empty schema. Omit `-v` to keep local data.

## Testing

```bash
./gradlew test                # integration tests spin up their own containers via Testcontainers
```

## Stack

- Kotlin 2.4 / JVM 17, Spring Boot 4.0.6, PostgreSQL 16 + PostGIS 3.4, Flyway migrations.
- Custom config keys (`email.*`, `app.*`, `push.*`, `storage.s3.*`) are bound via `@Value` / `@ConditionalOnProperty` (no `@ConfigurationProperties`, no config-processor). The Spring VSCode extension flags these as "Unknown property" — this is a cosmetic IDE-only warning, not an error.
