<!-- generated-by: gsd-doc-writer -->
# Testing

## Overview

The project has **180 integration tests** across 20 test files. All tests run against real infrastructure (PostgreSQL + PostGIS, MinIO) via Testcontainers — no mocked databases.

## Running Tests

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.catspell.api.auth.AuthIntegrationTest"

# Run a specific domain's tests
./gradlew test --tests "com.catspell.api.discovery.*"

# Run tests with verbose output
./gradlew test --info
```

Tests require a container runtime (Docker or Podman). No external infrastructure needs to be running — Testcontainers handles everything.

## Test Infrastructure

Tests use **Testcontainers** to spin up real PostgreSQL (PostGIS) and MinIO containers. The base class `BaseIntegrationTest` handles container lifecycle:

- **PostgreSQL** — `postgis/postgis:16-3.4-alpine` with database `catspell`
- **MinIO** — `minio/minio:latest` on a random port, health-checked via `/minio/health/live`

Both containers are started once and shared across all test classes (static companion object). Connection properties are injected via `@DynamicPropertySource`:

```kotlin
registry.add("spring.datasource.url", postgres::getJdbcUrl)
registry.add("storage.s3.endpoint") { "http://${minio.host}:${minio.getMappedPort(9000)}" }
```

### Container Runtime

Tests detect and configure the container runtime automatically. The `build.gradle.kts` task configuration:
- Disables Ryuk (`TESTCONTAINERS_RYUK_DISABLED=true`)
- Auto-detects the Podman socket path if `DOCKER_HOST` is not set, running `podman machine inspect` to find the socket

If using Docker instead of Podman, tests work out of the box (Testcontainers defaults to Docker).

## Test Organization

All integration tests extend `BaseIntegrationTest` and use `@SpringBootTest`:

| Test Class | Domain | What It Tests |
|------------|--------|---------------|
| `CatSpellApplicationTests` | App | Context loads successfully |
| `AuthIntegrationTest` | Auth | Register, login, JWT validation |
| `RefreshTokenIntegrationTest` | Auth | Token refresh, rotation, reuse detection |
| `ErrorHandlingIntegrationTest` | Auth | Error response format, validation errors |
| `ProfileIntegrationTest` | Profile | Profile CRUD, location updates |
| `CompletenessIntegrationTest` | Profile | Profile completeness checks |
| `PhotoIntegrationTest` | Profile | Photo upload, confirm, delete, reorder |
| `CatProfileIntegrationTest` | Cat | Cat CRUD, limits |
| `CatPhotoIntegrationTest` | Cat | Cat photo upload, confirm, delete |
| `CatCascadeDeleteIntegrationTest` | Cat | Deleting cat cascades to photos |
| `DiscoveryIntegrationTest` | Discovery | Mixed feed (CAT + HUMAN cards), pagination |
| `OwnerProfileIntegrationTest` | Discovery | Owner/user profile endpoints |
| `SwipeMatchIntegrationTest` | Discovery | Swipe on cats and users, match detection, duplicate prevention |
| `MatchIntegrationTest` | Match | Match listing |
| `ChatIntegrationTest` | Chat | Send message, message history |
| `ConversationListIntegrationTest` | Chat | Conversation listing, unread counts |
| `HealthEndpointIntegrationTest` | Common | Actuator health endpoint |
| `OpenApiIntegrationTest` | Common | OpenAPI docs generation |
| `RateLimitIntegrationTest` | Common | Rate limiting behavior |

## Writing a New Test

1. Create a test class in `src/test/kotlin/com/catspell/api/<domain>/`
2. Extend `BaseIntegrationTest`
3. Annotate with `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
4. Use `TestRestTemplate` for HTTP requests

### Example Pattern

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyFeatureIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `should do something`() {
        // Register + login to get a token
        val token = registerAndGetToken("user@test.com", "password123")

        // Make authenticated request
        val headers = HttpHeaders().apply {
            setBearerAuth(token)
        }
        val response = restTemplate.exchange(
            "/api/endpoint",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }
}
```

## Test Configuration

Test-specific configuration is in `src/test/resources/application.yml`. Testcontainers dynamically inject connection properties via `@DynamicPropertySource` in `BaseIntegrationTest`.

Additional test resource: `src/test/resources/init-postgis.sql` — initialisation script for the test database.

## Test Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `spring-boot-starter-test` | 4.0.6 | JUnit 5, AssertJ, MockMvc |
| `spring-boot-starter-webmvc-test` | 4.0.6 | MockMvc + WebMvc test support |
| `spring-security-test` | managed | Security test utilities |
| `h2` | managed | In-memory database (available for unit tests) |
| `testcontainers:postgresql` | 1.20.6 | PostgreSQL container |
| `testcontainers:junit-jupiter` | 1.20.6 | JUnit 5 Testcontainers integration |
| `mockk` | 1.13.11 | Kotlin mocking framework |
