<!-- generated-by: gsd-doc-writer -->
# Development

## Project Structure

The project follows a **domain-sliced** layout. Each feature domain has its own package with `controller`, `model`, and `service` sub-packages:

```
src/main/kotlin/com/catspell/api/
├── auth/          # Authentication (register, login, JWT refresh)
├── profile/       # User profiles and photo uploads
├── cat/           # Cat profiles and cat photo uploads
├── discovery/     # Mixed feed, swipes, owner/user profiles
├── match/         # Match listing
├── chat/          # Conversations, messages, WebSocket STOMP
└── common/        # Shared config, exceptions, security, health
```

Each domain package follows the pattern:
- `controller/` — REST controllers (`@RestController`) or WebSocket controllers (`@Controller` + `@MessageMapping`)
- `model/` — JPA entities, DTOs (data classes), and Spring Data repositories
- `service/` — Business logic

## Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew bootRun` | Run the application (default profile) |
| `./gradlew bootRun --args='--spring.profiles.active=dev'` | Run with SQL logging and debug output |
| `./gradlew test` | Run all integration tests |
| `./gradlew build` | Compile + test + produce JAR |
| `./gradlew clean` | Remove build outputs |
| `./gradlew dependencies` | Show dependency tree |

## Adding a New Feature

1. **Create a new package** under `com.catspell.api.<domain>/`
2. **Add the entity** in `model/` — annotate with `@Entity`. The `allOpen` plugin in `build.gradle.kts` handles JPA proxy compatibility for `@Entity`, `@MappedSuperclass`, and `@Embeddable`.
3. **Add a migration** in `src/main/resources/db/migration/` following the Flyway naming convention: `V{next_number}__{description}.sql`
4. **Create the repository** as a `JpaRepository` interface in `model/`
5. **Implement the service** in `service/`
6. **Add the controller** in `controller/` — use `@RestController` and `@RequestMapping("/api/<domain>")`
7. **Add custom exceptions** in `common/exception/Exceptions.kt` and handle them in `GlobalExceptionHandler`
8. **Write integration tests** — extend `BaseIntegrationTest` (see [Testing](TESTING.md))

## Database Migrations

Migrations are managed by Flyway and live in `src/main/resources/db/migration/`. Current migrations (V1–V13):

| Migration | Description |
|-----------|-------------|
| V1 | Create users table |
| V2 | Create refresh tokens table |
| V3 | Enable PostGIS extension |
| V4 | Create user profiles (with geometry column) |
| V5 | Create user photos |
| V6 | Create cat profiles |
| V7 | Create cat photos |
| V8 | Create swipes table |
| V9 | Create matches table |
| V10 | Create conversations table |
| V11 | Create conversation participants table |
| V12 | Create messages table |
| V13 | Make swipe cat_id nullable (human card swipes) |

### Adding a New Migration

Create a new SQL file:

```
src/main/resources/db/migration/V14__description.sql
```

Flyway runs pending migrations automatically on application startup. Hibernate `ddl-auto` is set to `validate` — it checks entity mappings against the schema but never modifies it.

## Error Handling

All exceptions are mapped to [RFC 7807 Problem Detail](https://www.rfc-editor.org/rfc/rfc7807) responses by `GlobalExceptionHandler`. To add a new error type:

1. Define the exception class in `common/exception/Exceptions.kt`
2. Add a handler method in `GlobalExceptionHandler` returning a `ProblemDetail`

Current domain exceptions (12 types):

| Exception | HTTP Status | When |
|-----------|------------|------|
| `DuplicateEmailException` | 409 | Registration with existing email |
| `InvalidCredentialsException` | 401 | Wrong email/password on login |
| `InvalidTokenException` | 401 | Expired or reused refresh token |
| `ResourceNotFoundException` | 404 | Entity not found |
| `PhotoLimitExceededException` | 400 | More than 6 user photos |
| `InvalidPhotoTypeException` | 400 | Non-JPEG/PNG upload |
| `CatLimitExceededException` | 409 | More than 5 cats |
| `CatPhotoLimitExceededException` | 400 | More than 10 cat photos |
| `LocationRequiredException` | 400 | Discovery without location set |
| `ProfileIncompleteException` | 400 | Discovery with incomplete profile |
| `DuplicateSwipeException` | 409 | Swiping on same profile twice |
| `SelfSwipeException` | 400 | Swiping on own profile |

## Security

Authentication uses JWT Bearer tokens. The `JwtAuthenticationFilter` extracts and validates tokens on every request. Public endpoints are configured in `SecurityConfig`.

To add a new public endpoint, update `SecurityConfig.securityFilterChain()`:

```kotlin
it.requestMatchers("/api/your-endpoint").permitAll()
```

## Photo Upload Flow

Both user photos and cat photos follow the same presigned-upload pattern:

1. **Request upload URL** — client sends content type, server returns a presigned S3 PUT URL + photo ID
2. **Client uploads** directly to S3 using the presigned URL
3. **Confirm upload** — client calls the confirm endpoint, server verifies the object exists in S3, generates a thumbnail, and marks the photo as `ACTIVE`

## Code Style

- Kotlin official code style (`kotlin.code.style=official` in `gradle.properties`)
- Constructor injection via primary constructors (no `@Autowired`)
- Data classes for DTOs, JPA entities use regular classes with mutable properties
- Jakarta Bean Validation annotations on request DTOs
- Parallel Gradle builds enabled (`org.gradle.parallel=true`)

## Branch Conventions

Branch names follow the pattern: `phase/{zero-padded-number}-{kebab-case-description}` (e.g., `phase/07-mixed-discovery-feed`)
