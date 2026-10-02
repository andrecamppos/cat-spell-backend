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
  capacity: 10    # requests per minute per client IP
  trusted-proxies: ${RATE_LIMIT_TRUSTED_PROXIES:127.0.0.1,::1}
```

**What is throttled.** Each client IP gets one bucket of `rate-limit.capacity` requests per minute. The `/api/auth/*` endpoints and `POST /api/waitlist` share that bucket. `GET /api/waitlist/confirm` links and CORS preflight requests are never throttled. A throttled request gets `429 Too Many Requests` with a `Retry-After` header and the `X-RateLimit-Remaining` / `X-RateLimit-Reset` headers.

**Path matching.** Paths are matched after percent-decoding, so encoded spellings such as `/api/%77aitlist` share the canonical path's bucket.

**Client IP resolution.**

- If the directly connecting peer is not a trusted proxy, the bucket is keyed on its own socket address. Any `X-Forwarded-For` header it sends is ignored.
- If the peer is a trusted proxy, the key is the rightmost `X-Forwarded-For` hop that is not itself a trusted proxy. Hops are read across all `X-Forwarded-For` header lines in arrival order. If every hop is trusted, or there is none, the peer address is used.

**`RATE_LIMIT_TRUSTED_PROXIES`** takes a comma-separated list of exact IPv4/IPv6 addresses or CIDR ranges, for example `127.0.0.1,::1,10.0.0.0/8`. Addresses are compared by value, so `::1` also matches `0:0:0:0:0:0:0:1`. An invalid entry fails startup. An empty value trusts no peer.

**Deployment.**

- Set the value to the address or range your reverse proxy connects from. Proxies that append to the header (nginx `$proxy_add_x_forwarded_for`, AWS ALB) and proxies that overwrite it both work.
- If the proxy's address is not listed, every client behind it shares one bucket.
- **Server-to-server landing page:** list the landing server's address, and have it forward the visitor IP in `X-Forwarded-For`. Otherwise every join shares one bucket.

**Unsupported shapes.**

- A trusted peer that relays a client-supplied `X-Forwarded-For` unchanged lets the client choose its own key.
- Hops written as `ip:port` give each connection its own key. Strip ports at the proxy.

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
| Rate limit (auth + waitlist join) | 10 req/min per client IP | `rate-limit.capacity` |
