# Summary: Plan 06-02 — Integration Test Audit + Phase 6 Tests

**Status:** ✅ Complete
**Completed:** 2026-06-16

## What was built

1. **Phase 6 Integration Tests** — `RateLimitIntegrationTest` (7 tests: headers, 429 response, Retry-After, problem+json body, per-endpoint coverage). `HealthEndpointIntegrationTest` (6 tests: anonymous vs authenticated, S3/WebSocket/DB component details). `OpenApiIntegrationTest` (4 tests: spec serving, security scheme, API paths, no Swagger UI).

2. **Auth Test Gap Audit** — Added 6 tests to `AuthIntegrationTest`: refresh token flow (success, invalid token, reuse detection), invalid email registration, refreshToken field assertions in register/login responses. Added `X-Forwarded-For` unique IP generation to bypass rate limiting.

3. **Discovery/Match Test Gap Audit** — Added 2 tests to `DiscoveryIntegrationTest`: empty feed (no eligible cats returns empty array), pagination edge case (pageSize larger than available cats).

4. **Rate Limit Test Isolation** — Made `RateLimitFilter` capacity configurable via `rate-limit.capacity` property (default: 10). Test config uses 10000 to prevent unintended 429s across the full suite.

5. **Email Collision Fix** — Prefixed `RefreshTokenIntegrationTest` emails with `rt-` to avoid 409 Conflict with duplicate emails in `AuthIntegrationTest`.

## Files created
- `src/test/kotlin/com/catspell/api/common/RateLimitIntegrationTest.kt`
- `src/test/kotlin/com/catspell/api/common/HealthEndpointIntegrationTest.kt`
- `src/test/kotlin/com/catspell/api/common/OpenApiIntegrationTest.kt`

## Files modified
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt` — configurable capacity via constructor + `@Value`
- `src/test/resources/application.yml` — added management, springdoc, and rate-limit config
- `src/test/kotlin/com/catspell/api/auth/AuthIntegrationTest.kt` — added refresh token tests, unique IP headers
- `src/test/kotlin/com/catspell/api/auth/RefreshTokenIntegrationTest.kt` — prefixed emails to avoid collisions
- `src/test/kotlin/com/catspell/api/discovery/DiscoveryIntegrationTest.kt` — added empty feed and pagination edge case tests

## Test Coverage

| Domain | Files | Tests |
|--------|-------|-------|
| Auth | 3 | ~30 |
| Profile | 3 | ~33 |
| Cat | 3 | ~26 |
| Discovery | 3 | ~34 |
| Match | 1 | ~8 |
| Chat | 2 | ~19 |
| Common | 3 | ~12 |
| App | 1 | 1 |
| **Total** | **19** | **163** |

## Notes
- Test `application.yml` completely overrides `src/main/resources/application.yml` — all needed config (management, springdoc) must be duplicated there
- Spring Boot 4.0 health indicator bean names use camelCase (`webSocket` not `websocket`)
- `RateLimitFilter` uses `ConcurrentHashMap` — shared across tests in same context, so high capacity needed for test isolation
