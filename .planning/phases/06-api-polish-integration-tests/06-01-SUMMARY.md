# Summary: Plan 06-01 — OpenAPI Docs + Rate Limiting + Health Checks

**Status:** ✅ Complete
**Completed:** 2026-06-16

## What was built

1. **OpenAPI Spec Generation** — springdoc-openapi-starter-webmvc-api serves JSON spec at `/v3/api-docs` without Swagger UI. Five `GroupedOpenApi` beans (auth, user, cats, discovery, chat). Global `bearerAuth` SecurityScheme with auth endpoints excluded via `@SecurityRequirements`.

2. **Rate Limiting** — Bucket4j servlet filter (`RateLimitFilter`) at `HIGHEST_PRECEDENCE` intercepts `/api/auth/*` paths. 10 requests/minute/IP with ConcurrentHashMap buckets. IP resolved from `X-Forwarded-For` or `remoteAddr`. Returns RFC 7807 `application/problem+json` 429 with `Retry-After`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers.

3. **Health Indicators** — `S3HealthIndicator` uses `headBucket` for lightweight S3 connectivity check. `WebSocketHealthIndicator` reports active session count from `SimpUserRegistry`. Actuator health configured with `show-details=when-authorized`.

## Files created
- `src/main/kotlin/com/catspell/api/common/config/OpenApiConfig.kt`
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`
- `src/main/kotlin/com/catspell/api/common/health/S3HealthIndicator.kt`
- `src/main/kotlin/com/catspell/api/common/health/WebSocketHealthIndicator.kt`

## Files modified
- `build.gradle.kts` — added springdoc-openapi 2.8.8 + bucket4j-core 8.10.1
- `src/main/resources/application.yml` — springdoc + actuator config
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` — permit `/v3/api-docs/**`
- `src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt` — skip `/v3/api-docs`
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` — `@SecurityRequirements` on auth endpoints

## Notes
- Spring Boot 4.0 moved health classes from `org.springframework.boot.actuate.health` to `org.springframework.boot.health.contributor`
- Bucket4j `Bandwidth.classic`/`Refill` API deprecated in 8.x; used `Bandwidth.builder()` instead
- 136/137 existing tests pass; 1 pre-existing failure in `DiscoveryIntegrationTest.feed respects bidirectional age range` (unrelated to Phase 6)
