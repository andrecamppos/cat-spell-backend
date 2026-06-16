# Phase 6: API Polish & Integration Tests — Research

**Researched:** 2026-06-16
**Phase Goal:** Harden the API for production readiness — OpenAPI docs, rate limiting, health checks, and integration test coverage.
**Confidence:** HIGH

## 1. OpenAPI Documentation (springdoc-openapi)

### Dependency

Add `springdoc-openapi-starter-webmvc-api` (spec-only, no Swagger UI) to `build.gradle.kts`:
```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.8")
```

**Do NOT use** `springdoc-openapi-starter-webmvc-ui` — the CONTEXT.md decision (D-01) explicitly excludes Swagger UI.

### Configuration

In `application.yml`:
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    enabled: false
```

### OpenApiConfig Bean

Create `com.catspell.api.common.config.OpenApiConfig` with:
- Global `SecurityScheme` (type=HTTP, scheme=bearer, bearerFormat=JWT) via `@SecurityScheme` annotation on the config class
- `@SecurityRequirement(name = "bearerAuth")` at global level
- Auth endpoints (`/api/auth/**`) excluded from security requirement via `@SecurityRequirements` (empty) on each auth controller method

Five `GroupedOpenApi` beans for coarser tags (D-02):
1. `auth` → `/api/auth/**`
2. `user` → `/api/profile/**`, `/api/photos/**`
3. `cats` → `/api/cats/**`
4. `discovery` → `/api/discovery/**`, `/api/swipe/**`, `/api/matches/**`
5. `chat` → `/api/chat/**`, `/api/conversations/**`

### SecurityConfig Update

Add `/v3/api-docs/**` to `requestMatchers(...).permitAll()` in `SecurityConfig`.
Add `/v3/api-docs/**` to `shouldNotFilter()` in `JwtAuthenticationFilter`.

### Annotation Strategy (D-03)

Minimal annotations — springdoc auto-generates from DTOs and Jakarta validation. Add `@Operation(summary=..., description=...)` only where auto-generation is unclear. **Do NOT** annotate every endpoint.

## 2. Rate Limiting (Bucket4j)

### Dependency

```kotlin
implementation("com.bucket4j:bucket4j-core:8.14.0")
```

### Implementation Approach

Create `com.catspell.api.common.security.RateLimitFilter` as a `jakarta.servlet.Filter` (not a Spring `OncePerRequestFilter`). Register as a `@Bean` `FilterRegistrationBean` with URL patterns `/api/auth/*`.

**Filter chain order:** RateLimitFilter → JwtAuthenticationFilter → controllers.

### Token Bucket Configuration (D-08)

- 10 tokens per minute per IP
- `Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)))` — full refill each minute
- In-memory `ConcurrentHashMap<String, Bucket>` — sufficient for single-instance v1

### IP Resolution (D-06)

Extract client IP from `X-Forwarded-For` header (first entry), fallback to `request.remoteAddr`.

### Rate Limit Headers (D-08)

On every auth response:
- `X-RateLimit-Remaining: {tokens}`
- `X-RateLimit-Reset: {epoch-seconds}`

On 429:
- `Retry-After: {seconds}`
- Response body: RFC 7807 ProblemDetail format consistent with GlobalExceptionHandler

### GlobalExceptionHandler Integration

Add `RateLimitExceededException` to `Exceptions.kt` and handler in `GlobalExceptionHandler`:
```kotlin
@ExceptionHandler(RateLimitExceededException::class)
fun handleRateLimitExceeded(ex: RateLimitExceededException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message ?: "Rate limit exceeded")
    problem.title = "Too Many Requests"
    return problem
}
```

Actually, since this is a servlet `Filter`, the 429 response should be written directly in the filter (not via exception handler), because the filter runs before the Spring MVC dispatcher. Write the ProblemDetail JSON directly:
```kotlin
response.status = 429
response.contentType = "application/problem+json"
response.writer.write("""{"title":"Too Many Requests","status":429,"detail":"Rate limit exceeded. Try again in $retryAfter seconds."}""")
```

## 3. Health Checks (Spring Boot Actuator)

### Existing Setup

`spring-boot-starter-actuator` is already in `build.gradle.kts`. The `/actuator/health` endpoint is already permitted in `SecurityConfig` and `JwtAuthenticationFilter`.

### application.yml Configuration (D-11)

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
  endpoints:
    web:
      exposure:
        include: health
```

- Anonymous requests → aggregate UP/DOWN only
- Authenticated requests → full component breakdown (db, s3, websocket)

No `/actuator/info` endpoint (D-10).

### Custom Health Indicators

#### S3HealthIndicator (D-09)

Create `com.catspell.api.common.health.S3HealthIndicator`:
- Implements `HealthIndicator`
- Calls `s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())` — lightweight connectivity check
- UP if bucket accessible, DOWN with error details if not
- Inject `S3Client` and `@Value("\${storage.s3.bucket}")` bucket name

#### WebSocketHealthIndicator (D-09)

Create `com.catspell.api.common.health.WebSocketHealthIndicator`:
- Implements `HealthIndicator`
- Checks if the STOMP simple broker is running by inspecting `SimpUserRegistry` bean (active session count) or checking `SubProtocolWebSocketHandler` via `ApplicationContext`
- Simplest approach: inject `SimpUserRegistry`, report UP with `activeSessionCount` in details
- Alternative: check if the WebSocket endpoint `/ws` is configured (always UP if config is loaded, degraded approach)

**Recommended approach:** Use `SimpUserRegistry` — it's auto-configured and provides meaningful health data:
```kotlin
@Component
class WebSocketHealthIndicator(
    private val userRegistry: SimpUserRegistry
) : HealthIndicator {
    override fun health(): Health {
        return Health.up()
            .withDetail("activeSessions", userRegistry.userCount)
            .build()
    }
}
```

## 4. Integration Test Coverage Audit (D-12, D-13, D-14)

### Current Test Inventory

| Domain | Test File | Count | Coverage Area |
|--------|-----------|-------|---------------|
| Auth | `AuthIntegrationTest.kt` | ~8 | Register, login |
| Auth | `RefreshTokenIntegrationTest.kt` | ~7 | Token refresh, rotation |
| Auth | `ErrorHandlingIntegrationTest.kt` | ~11 | Error responses, validation |
| Profile | `ProfileIntegrationTest.kt` | ~14 | CRUD, location, validation |
| Profile | `PhotoIntegrationTest.kt` | ~16 | Upload, confirm, delete, reorder |
| Profile | `CompletenessIntegrationTest.kt` | ~3 | Profile completeness checks |
| Cat | `CatProfileIntegrationTest.kt` | ~12 | CRUD, limits, validation |
| Cat | `CatPhotoIntegrationTest.kt` | ~11 | Photo upload, limits, ownership |
| Cat | `CatCascadeDeleteIntegrationTest.kt` | ~3 | Cascade deletion |
| Discovery | `DiscoveryIntegrationTest.kt` | ~10 | Feed, filters, exclusions |
| Discovery | `SwipeMatchIntegrationTest.kt` | ~10 | Like, pass, match detection |
| Discovery | `OwnerProfileIntegrationTest.kt` | ~4 | Owner reveal |
| Match | `MatchIntegrationTest.kt` | ~4 | Match list |
| Chat | `ChatIntegrationTest.kt` | ~9 | WebSocket messaging, history |
| Chat | `ConversationListIntegrationTest.kt` | ~10 | Conversation list, unread |

**Estimated total: ~130+ tests across 15 test files.**

### Gap Analysis Strategy

Domains to audit for missing test cases:
1. **Auth** — Missing: invalid email format, weak password, expired refresh, concurrent refresh
2. **Profile** — Missing: update non-existent profile edge cases, invalid location coordinates
3. **Cat** — Good coverage with cascade deletion tests
4. **Discovery** — Missing: empty feed (no eligible cats), edge cases for distance filter boundaries
5. **Match** — Missing: match list empty state, match list with multiple matches pagination
6. **Chat** — Missing: send message to non-match (should fail), conversation list empty state

### Phase 6 New Test Files (D-13)

Create new test files for Phase 6 code:
- `RateLimitIntegrationTest.kt` — verify 429 after 10 requests, verify headers, verify reset
- `HealthEndpointIntegrationTest.kt` — verify `/actuator/health` returns UP, component details for authenticated users
- `OpenApiIntegrationTest.kt` — verify `/v3/api-docs` returns valid JSON, verify security scheme present

## 5. Security & Filter Chain Considerations

### Filter Registration Order

Current filter chain: SecurityFilterChain → JwtAuthenticationFilter → Controllers

After Phase 6: SecurityFilterChain → RateLimitFilter (servlet filter on `/api/auth/*`) → JwtAuthenticationFilter → Controllers

The `RateLimitFilter` should be registered with `FilterRegistrationBean` and `setOrder(Ordered.HIGHEST_PRECEDENCE)` to ensure it runs before Spring Security's filter chain. This way, rate limiting happens even before authentication processing.

### SecurityConfig Changes Summary

```kotlin
it.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
it.requestMatchers("/actuator/health").permitAll()
it.requestMatchers("/v3/api-docs/**").permitAll()  // NEW
it.requestMatchers("/ws/**").permitAll()
it.requestMatchers("/error").permitAll()
it.anyRequest().authenticated()
```

## 6. Dependency Summary

### New Dependencies for build.gradle.kts

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.8")
implementation("com.bucket4j:bucket4j-core:8.14.0")
```

### Existing Dependencies Already Sufficient

- `spring-boot-starter-actuator` — already present
- `spring-boot-starter-validation` — already present
- `spring-boot-starter-test` — already present
- `testcontainers` — already present

## 7. File Inventory

### New Files

| File | Purpose |
|------|---------|
| `common/config/OpenApiConfig.kt` | OpenAPI grouping, security scheme |
| `common/security/RateLimitFilter.kt` | Bucket4j rate limit servlet filter |
| `common/health/S3HealthIndicator.kt` | S3/MinIO health check |
| `common/health/WebSocketHealthIndicator.kt` | WebSocket broker health check |
| `test/.../RateLimitIntegrationTest.kt` | Rate limiting tests |
| `test/.../HealthEndpointIntegrationTest.kt` | Health endpoint tests |
| `test/.../OpenApiIntegrationTest.kt` | OpenAPI spec tests |

### Modified Files

| File | Change |
|------|--------|
| `build.gradle.kts` | Add springdoc + bucket4j dependencies |
| `application.yml` | Add springdoc + actuator health config |
| `SecurityConfig.kt` | Permit `/v3/api-docs/**` |
| `JwtAuthenticationFilter.kt` | Skip filter for `/v3/api-docs` paths |
| `GlobalExceptionHandler.kt` | Add `RateLimitExceededException` handler (optional — filter handles directly) |

## 8. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Bucket4j version incompatibility with Spring Boot 4.0.x | Build failure | Use latest bucket4j-core 8.x which targets Java 17+ |
| springdoc 2.x incompatibility with Spring Boot 4.0.x | Build failure | springdoc-openapi 2.8+ supports Spring Boot 4.0; verify exact version |
| S3 health check slows down health endpoint | Slow health checks | headBucket is lightweight (~10ms); acceptable |
| Rate limit filter order issues | Rate limiting bypassed | Use FilterRegistrationBean with explicit order |
| ConcurrentHashMap memory growth | Memory leak with many IPs | Acceptable for v1 single-instance; add eviction in v2 |

## Validation Architecture

### Testability Assessment

All Phase 6 features are testable with the existing Testcontainers setup:
- **OpenAPI spec** — GET `/v3/api-docs` in integration test, assert valid JSON with expected structure
- **Rate limiting** — Send 11 requests to `/api/auth/login`, assert 429 on 11th, verify headers
- **Health checks** — GET `/actuator/health` authenticated vs anonymous, verify component details
- **Test gap audit** — No validation needed; this is a test-writing task

### Verification Commands

```bash
# Run all tests
./gradlew test

# Run specific Phase 6 tests
./gradlew test --tests "*RateLimitIntegrationTest"
./gradlew test --tests "*HealthEndpointIntegrationTest"
./gradlew test --tests "*OpenApiIntegrationTest"

# Verify OpenAPI spec serves
curl http://localhost:8080/v3/api-docs

# Verify health endpoint
curl http://localhost:8080/actuator/health
```

---

## RESEARCH COMPLETE

Phase 6 research covers: OpenAPI spec (springdoc-openapi-starter-webmvc-api), rate limiting (Bucket4j servlet filter), custom health indicators (S3 + WebSocket), and integration test gap audit. All features are cross-cutting hardening — no new domain entities or migrations needed.
