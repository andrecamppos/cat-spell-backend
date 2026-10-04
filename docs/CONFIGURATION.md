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
| `RATE_LIMIT_CAPACITY` | No | `10` | Auth endpoint requests per client IP per minute (`rate-limit.capacity`) |
| `RATE_LIMIT_WAITLIST_CAPACITY` | No | `10` | `POST /api/waitlist` requests per client IP per minute (`rate-limit.waitlist-capacity`) |
| `RATE_LIMIT_ADMIN_CAPACITY` | No | `5` | Requests to any `/api/admin` path per client IP per minute, every method, successes included (`rate-limit.admin-capacity`) |
| `RATE_LIMIT_MAX_TRACKED_KEYS` | No | `100000` | Maximum keys (client IPs, emails, reporters) held by each rate-limit bucket store (`rate-limit.max-tracked-keys`) |
| `RATE_LIMIT_TRUSTED_PROXIES` | No | `127.0.0.1,::1` | Peers allowed to set `X-Forwarded-For`: exact IPs or CIDR ranges, comma-separated. See [Rate Limiting](#rate-limiting) |
| `INVITE_ADMIN_TOKEN` | No | blank | Operator secret sent as `X-Admin-Token`. Blank disables the operator endpoints; a non-blank value shorter than 32 characters fails startup. See [Operator Endpoints](#operator-endpoints) |
| `WAITLIST_ALLOWED_ORIGINS` | No | blank | Browser origins allowed to call `POST /api/waitlist` cross-origin, comma-separated. Blank = no CORS (server-to-server only) |
| `WAITLIST_RESEND_COOLDOWN_MINUTES` | No | `15` | A re-join of a pending waitlist entry inside this window sends no new confirmation email (`app.waitlist.resend-cooldown-minutes`) |
| `WAITLIST_PER_EMAIL_REFILL_HOURS` | No | `24` | Refill window of the per-address confirmation-email budget (3 per window by default, `app.waitlist.per-email-refill-hours`) |
| `WAITLIST_INVITE_SEND_TIMEOUT_MS` | No | `10000` | Maximum time the waitlist convert waits for the invite email to send; must be > 0 (`app.waitlist.invite-send-timeout-ms`) |

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
  capacity: ${RATE_LIMIT_CAPACITY:10}   # auth endpoints: requests per client IP per minute
  waitlist-capacity: ${RATE_LIMIT_WAITLIST_CAPACITY:10}   # POST /api/waitlist: requests per client IP per minute
  admin-capacity: ${RATE_LIMIT_ADMIN_CAPACITY:5}   # every /api/admin request, any method: per client IP per minute
  max-tracked-keys: ${RATE_LIMIT_MAX_TRACKED_KEYS:100000}   # max keys held per bucket store; idle keys expire after one window
  trusted-proxies: "${RATE_LIMIT_TRUSTED_PROXIES:127.0.0.1,::1}"   # peers allowed to set X-Forwarded-For: exact IPs or CIDR, comma-separated (see docs/CONFIGURATION.md)
```

**What is throttled.** Each client IP has a separate bucket in each of three families. Every bucket refills over a one-minute window.

| Family | Requests | Capacity per client IP per minute |
|--------|----------|-----------------------------------|
| Auth | `/api/auth/register`, `/login`, `/refresh`, `/forgot-password`, `/resend-verification`, `/change-email` | `rate-limit.capacity` (10) |
| Waitlist join | `POST /api/waitlist` only | `rate-limit.waitlist-capacity` (10) |
| Operator routes | `/api/admin` and every path under it, any method, successful requests included | `rate-limit.admin-capacity` (5) |

The families don't share budget, so landing-page joins can't spend a shared IP's login budget and logins can't spend the join budget. `GET /api/waitlist/confirm` links and CORS preflight requests to the join are never throttled. The operator family runs before the `X-Admin-Token` check, so guesses at the operator secret are throttled too.

A throttled request gets `429 Too Many Requests` with a `Retry-After` header and the `X-RateLimit-Remaining` / `X-RateLimit-Reset` headers.

**Bounded memory.** Each bucket store holds at most `rate-limit.max-tracked-keys` keys (default 100000). A key that sees no traffic for one window expires; by then its bucket would have refilled anyway. The same bound applies to the per-email and per-reporter throttles in the auth, waitlist and report services. Buckets live in memory, so limits are per application instance: N instances allow up to N times the configured rate.

**Path matching.** Requests are matched on the container-normalized path, the same path the servlet mapping and the handler see. Percent-encoding is decoded, `;` path parameters are removed, and `/./`, `/../` and `//` segments are resolved. Spellings such as `/api/%77aitlist`, `/api/auth/./login` or `/api/auth/login;x=1` count against the canonical path's bucket.

**Client IP resolution.**

- If the directly connecting peer is not a trusted proxy, the bucket is keyed on its own socket address. Any `X-Forwarded-For` header it sends is ignored.
- If the peer is a trusted proxy, the key is the rightmost `X-Forwarded-For` hop that is not itself a trusted proxy. Hops are read across all `X-Forwarded-For` header lines in arrival order. If every hop is trusted, or there is none, the peer address is used.
- Each hop is canonicalized before both the trust check and key selection. `ip:port`, `[v6]` and `[v6]:port` are reduced to the address, and the address is re-rendered from its parsed bytes, so `::1` and `0:0:0:0:0:0:0:1` share a key, and the IPv4-mapped form `::ffff:10.0.0.1` matches `10.0.0.1`. A client therefore can't get a fresh bucket by rotating ports, and a trusted proxy written with a port is still recognized.
- A bare IPv6 address followed by a port without brackets (`2001:db8::1:80`) is ambiguous and is read as an address, never as address plus port.
- If a hop is still not an IP literal after canonicalization (for example `unknown` or a hostname), the request falls back to the peer's own bucket. Hop text is never resolved through DNS.

**`RATE_LIMIT_TRUSTED_PROXIES`** takes a comma-separated list of exact IPv4/IPv6 addresses or CIDR ranges, for example `127.0.0.1,::1,10.0.0.0/8`. Addresses are compared by value, so `::1` also matches `0:0:0:0:0:0:0:1`. A range never matches an address of the other family. An invalid entry fails startup. An empty value trusts no peer.

**Misconfiguration warning.** The first time a peer that is not a trusted proxy sends `X-Forwarded-For`, the application logs one WARN naming that peer, `rate-limit.trusted-proxies` and `RATE_LIMIT_TRUSTED_PROXIES`. It is logged once per application instance, and the header value is never logged. If you see it in production, your reverse proxy's address is probably missing from the list, and every client behind that proxy is sharing one bucket.

**Cross-origin 429 on the join.** A throttled `POST /api/waitlist` from an origin listed in `WAITLIST_ALLOWED_ORIGINS` gets `Access-Control-Allow-Origin` (the request's origin) and `Access-Control-Expose-Headers: Retry-After, X-RateLimit-Remaining, X-RateLimit-Reset`, so a browser landing page can read the 429 and its `Retry-After`. The response always carries `Vary: Origin`. Other origins get the 429 with no CORS grant. Credentials and wildcards are never allowed.

**Deployment.**

- Set the value to the address or range your reverse proxy connects from. Proxies that append to the header (nginx `$proxy_add_x_forwarded_for`, AWS ALB) and proxies that overwrite it both work.
- If the proxy's address is not listed, every client behind it shares one bucket, and the WARN above is logged.
- **Server-to-server landing page:** if the landing server calls `POST /api/waitlist` itself, list the landing server's address in `RATE_LIMIT_TRUSTED_PROXIES` and have it forward the visitor IP in `X-Forwarded-For`. Otherwise every join shares the landing server's single bucket, and 10 joins a minute throttle everyone.

**Unsupported shapes.**

- A trusted peer that relays a client-supplied `X-Forwarded-For` unchanged lets the client choose its own key.

### Operator Endpoints

The operator endpoints (`/api/admin/invites`, `/api/admin/waitlist`, and anything else under `/api/admin`) are protected by a shared secret, `INVITE_ADMIN_TOKEN` (`app.invite.admin-token`), sent in the `X-Admin-Token` header.

```yaml
app:
  invite:
    admin-token: ${INVITE_ADMIN_TOKEN:}   # deny-by-default when blank; NO guessable dev default; blank = operator endpoints disabled; otherwise >= 32 chars or startup fails (D-13)
```

- **Blank (the default):** the operator endpoints are disabled. Every `/api/admin` request gets `401`. No default token ships.
- **Non-blank and shorter than 32 characters:** the application does not start. **If you already run with a shorter token, rotate it to one of at least 32 characters before deploying this version.** The startup error never contains the token. To generate one: `openssl rand -base64 32`.
- **32 characters or more:** requests with that exact `X-Admin-Token` value are allowed. The comparison is constant-time.

**Every `/api/admin` path is checked.** One filter checks the token before Spring Security and the controllers run. Without the right token, any path under `/api/admin`, including an unknown path or a request with malformed parameters, gets the same generic `401 Not authorized`, so the response reveals nothing about which routes exist.

**`Authorization` is ignored on operator paths.** The JWT filter skips `/api/admin`, so a stale or invalid `Authorization: Bearer` header left in an operator's HTTP client no longer causes a 401. The `X-Admin-Token` header alone decides access.

**Throttle.** The operator family allows `RATE_LIMIT_ADMIN_CAPACITY` requests (default 5) per client IP per minute and counts every request, including successful ones. Scripts that convert waitlist entries in bulk must honor `Retry-After` on a 429 and pace themselves. Raise `RATE_LIMIT_ADMIN_CAPACITY` if 5 a minute is too slow for your workflow, keeping in mind that it also caps how fast someone can guess the token.

### Waitlist

```yaml
app:
  waitlist:
    confirm-token-ttl-hours: ${WAITLIST_CONFIRM_TTL_HOURS:168}   # 7 days (D-08)
    per-email-capacity: ${WAITLIST_PER_EMAIL_CAPACITY:3}
    per-email-refill-hours: ${WAITLIST_PER_EMAIL_REFILL_HOURS:24}   # 3 per 24 h per email (D-07)
    resend-cooldown-minutes: ${WAITLIST_RESEND_COOLDOWN_MINUTES:15}   # a PENDING re-join inside this window sends nothing (D-07)
    invite-send-timeout-ms: ${WAITLIST_INVITE_SEND_TIMEOUT_MS:10000}   # max wait for the convert invite email; must be > 0 (D-10)
    allowed-origins: ${WAITLIST_ALLOWED_ORIGINS:}   # blank = no CORS (server-to-server only)
```

(The confirm, redirect and invite URL keys are omitted here; see `application.yml`.)

**Joining.** `POST /api/waitlist` always answers the same way, whether or not the address is already on the list.

- **Resend cooldown.** Re-joining with a still-pending address sends a fresh confirmation link only if the last one went out more than `WAITLIST_RESEND_COOLDOWN_MINUTES` (default 15) ago. Inside the cooldown the re-join sends nothing and the previous link stays valid, so a double-submit doesn't invalidate the link the user just received. `0` disables the cooldown.
- **Per-address budget.** At most `WAITLIST_PER_EMAIL_CAPACITY` (3) confirmation emails per `WAITLIST_PER_EMAIL_REFILL_HOURS` (24) go to one normalized address. The budget is spent by every join attempt, including those inside the cooldown, which caps how much mail anyone can direct at a victim's inbox.
- **Address pinned at first insert.** The delivery address is the one stored when the entry was first created. A later join with a different spelling that normalizes to the same address (for example a `+suffix` variant) never changes where confirmation or invite emails go.
- **Confirmation email copy.** The email renders its expiry from `WAITLIST_CONFIRM_TTL_HOURS` (whole days when the value is a multiple of 24, otherwise hours) and the cooldown from `WAITLIST_RESEND_COOLDOWN_MINUTES`. It says only the newest link works.
- **CORS.** Only the origins in `WAITLIST_ALLOWED_ORIGINS` may call the join from a browser. Blank means server-to-server only; see the landing-page note under [Rate Limiting](#rate-limiting).

**Converting an entry to an invite.** `POST /api/admin/waitlist/{id}/invite` (the convert) creates the invite and sends the invite email in one transaction. The send is bounded by `WAITLIST_INVITE_SEND_TIMEOUT_MS` (default 10000). If the send fails or times out, the whole conversion is rolled back and the operator gets a retryable `502 WAITLIST_INVITE_DELIVERY_FAILED`. The log line names only the exception type, never the address or the code.

**Known residual: late delivery after a timeout.** If the mail provider ignores the timeout's interrupt, a timed-out send may still deliver its email later. That code belongs to the rolled-back conversion and was never saved, so redeeming it at sign-up fails with the generic invite error. When the operator retries the convert, the new email carries a valid code. Any real email provider added later must bound its own network I/O (connect and read timeouts) so a send can't hang past the timeout.

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
| **Production** | Set all env vars via deployment platform secrets; override `JWT_SECRET`, `DATABASE_*`, `S3_*`; set `RATE_LIMIT_TRUSTED_PROXIES` to your reverse proxy's address when running behind one; set `INVITE_ADMIN_TOKEN` to at least 32 characters (or leave it blank to disable the operator endpoints); set `WAITLIST_ALLOWED_ORIGINS` to the landing page's origin if a browser calls the join <!-- VERIFY: production deployment platform and secret management approach --> |

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
| `caffeine` | managed (3.2.3 via the Spring Boot BOM) | Bounded per-key rate-limit buckets |

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
| Rate limit (auth endpoints) | 10 req/min per client IP | `rate-limit.capacity` |
| Rate limit (waitlist join, `POST /api/waitlist`) | 10 req/min per client IP | `rate-limit.waitlist-capacity` |
| Rate limit (operator routes, `/api/admin/**`) | 5 req/min per client IP, every request | `rate-limit.admin-capacity` |
| Waitlist confirmation emails | 3 per 24 h per normalized address | `app.waitlist.per-email-capacity` / `app.waitlist.per-email-refill-hours` |
| Waitlist resend cooldown | 15 min | `app.waitlist.resend-cooldown-minutes` |
