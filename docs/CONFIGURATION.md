<!-- generated-by: gsd-doc-writer -->
# Configuration

All configuration is managed through Spring Boot's externalized configuration. Values can be set via environment variables, `application.yml`, or Spring profiles.

## Environment Variables

Copy `.env.example` to `.env` and fill in the values:

```bash
cp .env.example .env
```

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5432/catspell` | JDBC URL for PostgreSQL + PostGIS |
| `DATABASE_USERNAME` | Yes | `catspell` | Database username |
| `DATABASE_PASSWORD` | Yes | `catspell` | Database password |
| `JWT_SECRET` | Yes | Dev default provided | Base64-encoded secret for HS512 signing (≥64 bytes) |
| `S3_ENDPOINT` | Yes | `http://localhost:9002` | S3-compatible endpoint URL |
| `S3_REGION` | Yes | `us-east-1` | S3 region |
| `S3_BUCKET` | Yes | `catspell-photos` | S3 bucket name for photos |
| `S3_ACCESS_KEY` | Yes | `catspell` | S3 access key |
| `S3_SECRET_KEY` | Yes | `catspell123` | S3 secret key |

All variables have development defaults. For production, **`JWT_SECRET`**, **`DATABASE_PASSWORD`**, **`S3_ACCESS_KEY`**, and **`S3_SECRET_KEY`** must be overridden with secure values.

### Generating a JWT Secret

```bash
openssl rand -base64 64
```

Set the result as the `JWT_SECRET` environment variable.

## Application Configuration (`application.yml`)

### Database

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/catspell}
    username: ${DATABASE_USERNAME:catspell}
    password: ${DATABASE_PASSWORD:catspell}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
```

- **`ddl-auto: validate`** — Hibernate validates the schema against entities but does not modify it. All schema changes go through Flyway migrations.
- **`open-in-view: false`** — Disables the Open Session in View anti-pattern.

### JWT

```yaml
jwt:
  secret: ${JWT_SECRET:<dev-default-provided>}
  access-token-expiry: 3600000      # 1 hour in milliseconds
  refresh-token-expiry-days: 30     # 30 days
```

A base64-encoded development default is provided in `application.yml`. **Never use it in production** — see [Generating a JWT Secret](#generating-a-jwt-secret).

### S3 / MinIO Storage

```yaml
storage:
  s3:
    endpoint: ${S3_ENDPOINT:http://localhost:9002}
    region: ${S3_REGION:us-east-1}
    bucket: ${S3_BUCKET:catspell-photos}
    access-key: ${S3_ACCESS_KEY:catspell}
    secret-key: ${S3_SECRET_KEY:catspell123}
```

For local development, MinIO runs on port 9002 (API, mapped from container port 9000) and 9001 (console). See `docker-compose.yml` for the port mapping.

### Rate Limiting

```yaml
rate-limit:
  capacity: 10    # requests per minute per IP (auth endpoints only)
```

Configurable via `rate-limit.capacity` property. Applies to `/api/auth/*` endpoints. Returns `429 Too Many Requests` with `Retry-After` and `X-RateLimit-Remaining` headers.

### OpenAPI

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    enabled: false
```

Swagger UI is disabled by default. API docs are available as JSON at `/v3/api-docs`. Grouped endpoints: `auth`, `user`, `cats`, `discovery`, `chat`.

### Actuator

```yaml
management:
  endpoint:
    health:
      show-components: when-authorized
      show-details: when-authorized
  endpoints:
    web:
      exposure:
        include: health
```

Only the `health` endpoint is exposed. Component details (S3 connectivity, WebSocket status) are visible to authenticated users only.

### Server

```yaml
server:
  port: 8080
```

## Spring Profiles

### `dev` Profile

Activate with `--spring.profiles.active=dev` or `SPRING_PROFILES_ACTIVE=dev`.

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    com.catspell.api: DEBUG
```

Enables SQL logging with formatted output and DEBUG-level logging for security and application code.

## Per-Environment Overrides

| Environment | How to configure |
|-------------|-----------------|
| **Local dev** | `.env` file + `docker-compose.yml` defaults |
| **Dev profile** | `SPRING_PROFILES_ACTIVE=dev` — enables SQL logging |
| **Production** | Set all env vars via deployment platform secrets; override `JWT_SECRET`, `DATABASE_*`, `S3_*` <!-- VERIFY: production deployment platform and secret management approach --> |

## Gradle Configuration

### `gradle.properties`

```properties
kotlin.code.style=official
org.gradle.parallel=true
```

### Key Build Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `spring-boot-starter-web` | 4.0.6 | REST API |
| `spring-boot-starter-data-jpa` | 4.0.6 | JPA / Hibernate |
| `spring-boot-starter-security` | 4.0.6 | Spring Security 7.1 |
| `spring-boot-starter-validation` | 4.0.6 | Bean validation |
| `spring-boot-starter-websocket` | 4.0.6 | WebSocket / STOMP |
| `spring-boot-starter-actuator` | 4.0.6 | Health endpoints |
| `spring-boot-flyway` + `flyway-database-postgresql` | 4.0.6 | Database migrations |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 | JWT token handling |
| `jackson-module-kotlin` | managed | Kotlin serialization support |
| `hibernate-spatial` | managed | PostGIS geometry types |
| `aws-sdk-s3` | 2.25.60 | S3-compatible object storage |
| `thumbnailator` | 0.4.20 | Server-side image thumbnails |
| `springdoc-openapi-starter-webmvc-api` | 2.8.8 | OpenAPI documentation |
| `bucket4j-core` | 8.10.1 | Rate limiting |

## Business Limits

| Limit | Value | Source |
|-------|-------|--------|
| Max photos per user | 6 | `PhotoLimitExceededException` |
| Max cats per user | 5 | `CatLimitExceededException` |
| Max photos per cat | 10 | `CatPhotoLimitExceededException` |
| Allowed photo types | JPEG, PNG | `InvalidPhotoTypeException` |
| Feed default page size | 20 | `DiscoveryController` |
| Min user age | 18 years | `CreateProfileRequest` validation |
| Access token expiry | 1 hour | `jwt.access-token-expiry` |
| Refresh token expiry | 30 days | `jwt.refresh-token-expiry-days` |
| Rate limit (auth) | 10 req/min per IP | `rate-limit.capacity` |
