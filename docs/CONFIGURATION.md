<!-- GSD:docs-update -->
# Configuration

All configuration is managed through Spring Boot's externalized configuration. Values can be set via environment variables, `application.yml`, or Spring profiles.

## Environment Variables

Copy `.env.example` to `.env` and fill in the values:

```bash
cp .env.example .env
```

### Required Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5432/catspell` |
| `DATABASE_USERNAME` | Database username | `catspell` |
| `DATABASE_PASSWORD` | Database password | `catspell` |
| `JWT_SECRET` | Base64-encoded secret for HS512 signing (must be ≥64 bytes) | Dev default provided |
| `S3_ENDPOINT` | S3-compatible endpoint URL | `http://localhost:9000` |
| `S3_REGION` | S3 region | `us-east-1` |
| `S3_BUCKET` | S3 bucket name for photos | `catspell-photos` |
| `S3_ACCESS_KEY` | S3 access key | `catspell` |
| `S3_SECRET_KEY` | S3 secret key | `catspell123` |

### Generating a JWT Secret

For production, generate a strong secret:

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
  secret: ${JWT_SECRET}
  access-token-expiry: 3600000      # 1 hour in milliseconds
  refresh-token-expiry-days: 30     # 30 days
```

### S3 / MinIO Storage

```yaml
storage:
  s3:
    endpoint: ${S3_ENDPOINT:http://localhost:9000}
    region: ${S3_REGION:us-east-1}
    bucket: ${S3_BUCKET:catspell-photos}
    access-key: ${S3_ACCESS_KEY:catspell}
    secret-key: ${S3_SECRET_KEY:catspell123}
```

For local development, MinIO runs on port 9000 (API) and 9001 (console). The bucket is created automatically if it does not exist. <!-- VERIFY: confirm bucket auto-creation behavior in StorageService -->

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

Only the `health` endpoint is exposed. Component details (S3 connectivity, WebSocket status) are visible to authenticated users.

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

## Gradle Configuration

### `gradle.properties`

```properties
kotlin.code.style=official
org.gradle.parallel=true
```

### Key Build Dependencies

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-data-jpa` | JPA / Hibernate |
| `spring-boot-starter-security` | Spring Security 7.1 |
| `spring-boot-starter-validation` | Bean validation |
| `spring-boot-starter-websocket` | WebSocket / STOMP |
| `spring-boot-starter-actuator` | Health endpoints |
| `spring-boot-flyway` + `flyway-database-postgresql` | Database migrations |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` 0.12.6 | JWT token handling |
| `jackson-module-kotlin` | Kotlin serialization support |
| `hibernate-spatial` | PostGIS geometry types |
| `aws-sdk-s3` 2.25.60 | S3-compatible object storage |
| `thumbnailator` 0.4.20 | Server-side image thumbnails |
| `springdoc-openapi-starter-webmvc-api` 2.8.8 | OpenAPI documentation |
| `bucket4j-core` 8.10.1 | Rate limiting |

## Business Limits

| Limit | Value |
|-------|-------|
| Max photos per user | 6 |
| Max cats per user | 5 |
| Max photos per cat | 10 |
| Allowed photo types | JPEG, PNG |
| Max photo file size | 10 MB |
| Thumbnail size | 200×200 px |
| Feed page size range | 1–50 |
| Min user age | 18 years |
| Access token expiry | 1 hour |
| Refresh token expiry | 30 days |
