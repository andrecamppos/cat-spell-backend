# Phase 1: Foundation & Auth — Research

**Phase:** 1 — Foundation & Auth
**Researched:** 2025-06-09
**Confidence:** HIGH

## Research Summary

Phase 1 delivers the project scaffold, database infrastructure, and complete JWT authentication. This is a well-understood domain with mature tooling. The primary risks are Kotlin/Hibernate friction and JWT key management — both preventable with correct patterns established from the start.

## Technical Approach

### 1. Project Scaffold (Spring Boot + Kotlin)

**Recommended setup:**
- Spring Boot 3.3.x with Kotlin 2.0.x
- Gradle Kotlin DSL (`build.gradle.kts`)
- Java 21 target (virtual threads available if needed later)
- Base package: `com.catspell.api`

**Kotlin-specific configuration:**
- `kotlin-spring` compiler plugin (opens classes for Spring proxying)
- `kotlin-jpa` compiler plugin (generates no-arg constructors for entities)
- `kotlin-allopen` configured for `@Entity`, `@MappedSuperclass`, `@Embeddable`
- Register `KotlinModule` with Jackson for data class serialization

**Application structure for Phase 1:**
```
src/main/kotlin/com/catspell/api/
├── CatSpellApplication.kt
├── auth/
│   ├── controller/AuthController.kt
│   ├── service/AuthService.kt
│   └── model/
│       ├── User.kt (entity)
│       ├── RefreshToken.kt (entity)
│       ├── AuthRequest.kt (DTOs)
│       └── AuthResponse.kt (DTOs)
├── common/
│   ├── config/
│   │   ├── SecurityConfig.kt
│   │   └── JacksonConfig.kt
│   ├── security/
│   │   ├── JwtService.kt
│   │   └── JwtAuthenticationFilter.kt
│   └── exception/
│       └── GlobalExceptionHandler.kt
```

### 2. Database & Migrations (PostgreSQL + Flyway)

**Flyway setup:**
- Migration path: `src/main/resources/db/migration/`
- Naming: `V1__create_users_table.sql`, `V2__create_refresh_tokens_table.sql`
- Set `spring.jpa.hibernate.ddl-auto=validate` — Flyway owns schema, Hibernate validates

**Docker Compose for local dev:**
```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: catspell
      POSTGRES_USER: catspell
      POSTGRES_PASSWORD: catspell
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
volumes:
  pgdata:
```

**Users table schema (V1):**
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email ON users(email);
```

**Refresh tokens table (V2):**
```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE UNIQUE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
```

### 3. JWT Authentication (jjwt 0.12.x)

**Token strategy (per CONTEXT.md decisions):**
- Access token: JWT, 1 hour expiry (D-05)
- Refresh token: opaque UUID stored in DB, rotating, 30 days inactivity expiry (D-06, D-07)
- Multi-device: each device gets own refresh token (D-08)

**Key management:**
- Use HMAC-SHA512 with a 512-bit secret loaded from environment variable `JWT_SECRET`
- Never commit secrets to source code or config files
- Generate with: `openssl rand -base64 64`
- Alternative: RSA key pair for production (but HMAC is simpler for v1)

**JWT claims:**
```json
{
  "sub": "<user-id-uuid>",
  "email": "<user-email>",
  "iat": 1234567890,
  "exp": 1234571490
}
```

**jjwt 0.12.x API (breaking changes from 0.11.x):**
```kotlin
// Token creation
val token = Jwts.builder()
    .subject(userId.toString())
    .claim("email", email)
    .issuedAt(Date())
    .expiration(Date(System.currentTimeMillis() + accessTokenExpiry))
    .signWith(secretKey, Jwts.SIG.HS512)
    .compact()

// Token validation
val claims = Jwts.parser()
    .verifyWith(secretKey)
    .build()
    .parseSignedClaims(token)
    .payload
```

**Refresh token rotation flow:**
1. Client sends refresh token to `POST /api/auth/refresh`
2. Server looks up token in DB, checks not revoked and not expired
3. Server revokes old token (set `revoked=true`, `replaced_by=new_token`)
4. Server creates new refresh token in DB
5. Server issues new access token + new refresh token
6. **Theft detection:** If a revoked token is reused, revoke ALL tokens for that user (token family compromised)

### 4. Spring Security Configuration

**SecurityFilterChain setup:**
```kotlin
@Bean
fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
        .csrf { it.disable() } // Stateless API, no CSRF needed
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
            it.requestMatchers("/actuator/health").permitAll()
            it.anyRequest().authenticated()
        }
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        .exceptionHandling {
            it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        }
    return http.build()
}
```

**JwtAuthenticationFilter:**
- Extends `OncePerRequestFilter`
- Extracts Bearer token from `Authorization` header
- Validates JWT using jjwt
- Sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`
- Skips filter for public endpoints

### 5. Auth API Endpoints

**POST /api/auth/register** (D-09, D-10, D-11, D-12)
- Input: `{ email, password }`
- Validates: email format, password min 8 chars
- Creates user with BCrypt-hashed password
- Returns 201 with `{ accessToken, refreshToken }`
- Error: 409 if email already exists

**POST /api/auth/login**
- Input: `{ email, password }`
- Validates credentials against stored hash
- Returns 200 with `{ accessToken, refreshToken }`
- Error: 401 with vague "Invalid credentials" (D-15)

**POST /api/auth/refresh**
- Input: `{ refreshToken }`
- Validates token exists, not revoked, not expired
- Rotates: revokes old, creates new
- Returns 200 with `{ accessToken, refreshToken }`
- Error: 401 if token invalid/revoked/expired

### 6. Error Handling (RFC 7807)

**Spring Boot 3.3 built-in `ProblemDetail` (D-13, D-14, D-15):**

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Validation failed"
        )
        problem.title = "Validation Error"
        problem.setProperty("violations", ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to it.defaultMessage)
        })
        return problem
    }
}
```

**Error response format:**
```json
{
  "type": "about:blank",
  "title": "Validation Error",
  "status": 400,
  "detail": "Validation failed",
  "violations": [
    { "field": "password", "message": "must be at least 8 characters" }
  ]
}
```

### 7. Kotlin JPA Entity Patterns

**Critical: Do NOT use data classes for JPA entities (Pitfall #6)**

```kotlin
@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
```

**Key rules:**
- Use `var` for all fields (Hibernate needs mutability)
- Default `id` to `null` (assigned on persist)
- `equals`/`hashCode` based on ID only
- Use `kotlin-jpa` plugin for no-arg constructor generation
- Use `Instant` for timestamps (not `LocalDateTime`) for timezone safety

### 8. Testing Strategy

**Unit tests:**
- `AuthService` — registration, login, token refresh logic
- `JwtService` — token creation, validation, expiry

**Integration tests (Testcontainers):**
- Full auth flow: register → login → access protected endpoint → refresh
- Duplicate email registration
- Invalid credentials
- Expired/revoked refresh token

**Test setup:**
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
```

## Validation Architecture

### Validation Points

| Layer | What's Validated | How |
|-------|-----------------|-----|
| Controller | Request DTOs (email format, password length) | Jakarta Bean Validation (`@Valid`, `@Email`, `@Size`) |
| Service | Business rules (email uniqueness, credential match) | Service-level checks, throws domain exceptions |
| Database | Data integrity (unique email, FK constraints) | PostgreSQL constraints |
| Security Filter | JWT validity (signature, expiry) | jjwt library validation |

### Acceptance Criteria Mapping

| Requirement | Validation Method |
|------------|-------------------|
| AUTH-01 (register) | Integration test: POST /api/auth/register returns 201 with tokens |
| AUTH-02 (login + JWT) | Integration test: POST /api/auth/login returns 200 with valid JWT; protected endpoint accepts JWT |
| AUTH-03 (refresh) | Integration test: POST /api/auth/refresh with valid token returns new token pair; old token rejected |

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Kotlin entity gotchas | HIGH (if unaware) | MEDIUM | Use regular classes + kotlin-jpa plugin from day one |
| JWT secret in source | MEDIUM | HIGH | Environment variable, never in config files |
| BCrypt timing attacks | LOW | LOW | BCrypt is constant-time by design |
| Flyway migration conflicts | LOW | LOW | Sequential V-prefix numbering, single developer for v1 |

## Dependencies

**Phase 1 has no upstream phase dependencies** — this is the foundation.

**Downstream phases depend on:**
- Spring Boot project scaffold (all phases)
- `User` entity and `users` table (Phase 2+)
- JWT authentication filter (Phase 2+)
- `SecurityConfig` public/protected endpoint pattern (Phase 2+)
- Error handling pattern via `GlobalExceptionHandler` (Phase 2+)

## Sources

- Spring Boot 3.3 reference documentation
- jjwt 0.12.x migration guide and API docs
- Spring Security 6.x reference (SecurityFilterChain configuration)
- Flyway documentation (Spring Boot integration)
- Kotlin + JPA best practices (JetBrains and Spring official guides)
- OWASP Authentication Cheat Sheet (password storage, token management)

---
## RESEARCH COMPLETE

*Phase 1 research completed: 2025-06-09*
*Confidence: HIGH — standard Spring Boot + JWT auth, well-documented stack*
