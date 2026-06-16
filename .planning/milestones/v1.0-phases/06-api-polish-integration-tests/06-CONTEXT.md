# Phase 6: API Polish & Integration Tests - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Harden the existing API for production readiness. Add OpenAPI documentation (spec-only, no Swagger UI), rate limiting on authentication endpoints (Bucket4j), custom health indicators (S3, WebSocket), and audit integration test coverage across all domains to fill gaps. This is a cross-cutting hardening pass — no new features, no production validation changes.

</domain>

<decisions>
## Implementation Decisions

### OpenAPI Documentation
- **D-01:** Spec only — generate OpenAPI JSON/YAML at `/v3/api-docs` but do NOT expose Swagger UI. Clients import the spec into Postman/Insomnia
- **D-02:** Coarser endpoint grouping with 5 tags: Auth, User (profile+photos), Cats (profiles+photos), Discovery & Matching, Chat (messages+conversations)
- **D-03:** Minimal annotations — auto-generated from DTOs and Jakarta validation. Add `@Operation` summary/description only where auto-generation is unclear or ambiguous
- **D-04:** Global Bearer JWT `SecurityScheme` (type=HTTP, scheme=bearer, bearerFormat=JWT). All authenticated endpoints auto-marked. Auth endpoints (register, login, refresh) excluded from the security requirement

### Rate Limiting
- **D-05:** Auth endpoints only — rate limit `/api/auth/register`, `/api/auth/login`, and `/api/auth/refresh`. Other endpoints are already behind JWT authentication
- **D-06:** Rate key is IP address. Use `X-Forwarded-For` header when behind a reverse proxy
- **D-07:** Bucket4j token-bucket library with a servlet Filter intercepting auth paths. In-memory buckets using `ConcurrentHashMap` — sufficient for single-instance v1. Add `bucket4j-core` dependency
- **D-08:** 10 requests per minute per IP. Include standard rate limit headers on all auth responses: `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After` (on 429). Return 429 Too Many Requests when exceeded

### Health Checks
- **D-09:** Custom `HealthIndicator` implementations for S3/MinIO connectivity and WebSocket broker status, beyond the auto-configured DataSource health check
- **D-10:** No `/actuator/info` endpoint — health check is sufficient for v1
- **D-11:** `management.endpoint.health.show-details=when-authorized` — anonymous requests get aggregate UP/DOWN only, authenticated users see full component breakdown (db, s3, websocket)

### Test Coverage
- **D-12:** Systematic audit of existing integration tests across all domains. Identify missing happy paths, error cases, and edge cases. Fill gaps endpoint-by-endpoint
- **D-13:** Dedicated integration tests for Phase 6 code: rate limiting (verify 429 after threshold), health endpoints (verify component status), and OpenAPI spec generation (verify spec is served and valid)
- **D-14:** Test-only pass for the gap audit — do not modify production validation code. Existing Jakarta validation annotations are already sufficient from prior phases

### Claude's Discretion
No areas deferred to Claude's discretion — all decisions made by user.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md` — Core value (cat-first discovery), constraints (Kotlin + Spring Boot, PostgreSQL, S3-compatible), out-of-scope items
- `.planning/REQUIREMENTS.md` — Full requirement definitions and traceability for all v1 requirements
- `.planning/ROADMAP.md` §Phase 6 — Success criteria for this phase

### Prior Phase Context
- `.planning/phases/01-foundation-auth/01-CONTEXT.md` — Package structure decisions (domain-first vertical slices), JWT token lifecycle, RFC 7807 error format via `GlobalExceptionHandler`. Phase 6 must follow the same `com.catspell.api.common.*` pattern for cross-cutting concerns
- `.planning/phases/02-user-profiles-photos/02-CONTEXT.md` — Photo upload patterns, profile completeness, PostGIS setup. Reference for understanding user profile test coverage
- `.planning/phases/03-cat-profiles/03-CONTEXT.md` — Cat data model, photo rules, cascade deletion. Reference for understanding cat profile test coverage
- `.planning/phases/04-discovery-matching/04-CONTEXT.md` — Discovery feed filtering, swipe model, match detection. Reference for understanding discovery/match test coverage
- `.planning/phases/05-real-time-chat/05-CONTEXT.md` — WebSocket STOMP setup, conversation model, offline delivery. Reference for understanding chat test coverage and WebSocket health indicator target

### Stack Research
- `.planning/research/STACK.md` — Recommended versions, dependencies, Kotlin entity gotchas
- `.planning/research/SUMMARY.md` — Architecture approach, critical pitfalls

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GlobalExceptionHandler` (`com.catspell.api.common.exception.GlobalExceptionHandler`) — Already handles 13 exception types with RFC 7807 ProblemDetail. Rate limiting 429 response should follow the same pattern
- `SecurityConfig` (`com.catspell.api.common.config.SecurityConfig`) — Already permits `/actuator/health`. Need to add OpenAPI spec path (`/v3/api-docs/**`) and update health endpoint rules
- `BaseIntegrationTest` (`com.catspell.api.BaseIntegrationTest`) — Testcontainers setup for PostgreSQL (PostGIS) and MinIO. All test classes extend this. Phase 6 tests follow the same pattern
- `JwtAuthenticationFilter` (`com.catspell.api.common.security.JwtAuthenticationFilter`) — Reference for how auth works; rate limit filter sits before this in the filter chain
- `build.gradle.kts` — `spring-boot-starter-actuator` already present. Need to add `springdoc-openapi-starter-webmvc-api` (spec-only, no UI) and `bucket4j-core`

### Established Patterns
- Domain-first vertical slices: `com.catspell.api.{domain}.controller/service/model/`
- Cross-cutting concerns under `com.catspell.api.common.*` (config, exception, security)
- JPA entities as classes with `equals`/`hashCode` overrides (kotlin-jpa plugin)
- DTOs as Kotlin data classes with Jakarta validation annotations
- Flyway versioned migrations (current: V10+)
- `extractUserId()` pattern in controllers via `SecurityContextHolder`
- Integration tests: one `*IntegrationTest.kt` per domain, extending `BaseIntegrationTest`

### Integration Points
- `SecurityConfig.securityFilterChain` — permit OpenAPI spec paths, configure health endpoint detail access
- `application.yml` — actuator configuration (health detail visibility), springdoc configuration
- `build.gradle.kts` — new dependencies: `springdoc-openapi-starter-webmvc-api`, `bucket4j-core`
- New `RateLimitFilter` servlet filter — registered before `JwtAuthenticationFilter`, intercepts auth paths only
- New `S3HealthIndicator` and `WebSocketHealthIndicator` health indicator beans under `com.catspell.api.common.health`
- New `OpenApiConfig` bean under `com.catspell.api.common.config` — defines `GroupedOpenApi` beans for coarser tags and `SecurityScheme`

</code_context>

<specifics>
## Specific Ideas

- Rate limit filter should use Bucket4j's `Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)))` for 10 req/min with full refill each minute
- 429 response body should follow RFC 7807 ProblemDetail format consistent with GlobalExceptionHandler: `{"title": "Too Many Requests", "status": 429, "detail": "Rate limit exceeded. Try again in N seconds."}`
- S3 health indicator can call `s3Client.headBucket()` on the configured bucket — lightweight connectivity check
- WebSocket health indicator can check if the STOMP broker relay is running (SimpleBrokerMessageHandler is active)
- OpenAPI tag grouping via `GroupedOpenApi.builder().group("auth").pathsToMatch("/api/auth/**")` etc.
- Existing 17 test files with ~130+ tests across: auth (3 files), profile (3 files), cat (3 files), discovery (3 files), match (1 file), chat (2 files), plus BaseIntegrationTest

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 6-API Polish & Integration Tests*
*Context gathered: 2026-06-15*
