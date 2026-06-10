# Stack Research

**Domain:** Dating app backend (niche/cat-focused)
**Researched:** 2025-06-09
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.4.x | Primary language | User requirement. Concise, null-safe, excellent Spring Boot interop |
| Spring Boot | 4.0.x | Application framework | Mature, batteries-included for REST + WebSocket + Security + Data |
| PostgreSQL | 18.x | Primary database | User requirement. PostGIS for geolocation, JSONB for flexible profile data |
| Spring Data JPA / Hibernate | 6.x | ORM / data access | Standard for Spring Boot + PostgreSQL. Kotlin-friendly with `spring-data-jpa` |
| Spring Security | 7.1.x | Auth & authorization | JWT support via `spring-security-oauth2-resource-server`, battle-tested |
| Spring WebSocket + STOMP | — | Real-time chat | Built into Spring Boot, STOMP protocol gives message routing and subscriptions |
| Flyway | 10.x | Database migrations | Schema versioning, integrates natively with Spring Boot |
| Gradle (Kotlin DSL) | 9.x | Build tool | Standard for Kotlin projects, better than Maven for Kotlin builds |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| jjwt (io.jsonwebtoken) | 0.12.x | JWT token creation/validation | Authentication — issue and verify JWT access/refresh tokens |
| PostGIS + Hibernate Spatial | 6.x | Geospatial queries | Distance-based matching — ST_DWithin, ST_Distance |
| AWS SDK for Kotlin (S3) | 1.x | S3 photo uploads | Photo storage — presigned URLs for upload/download |
| Spring Boot Starter Validation | — | Request validation | DTO validation with Jakarta Bean Validation annotations |
| Jackson Kotlin Module | 2.17.x | JSON serialization | Kotlin data class serialization — register `KotlinModule` |
| Testcontainers | 1.19.x | Integration testing | Spin up PostgreSQL + MinIO containers for tests |
| MockK | 1.13.x | Mocking in tests | Kotlin-native mocking, better than Mockito for Kotlin |
| SpringDoc OpenAPI | 2.5.x | API documentation | Auto-generate OpenAPI/Swagger docs from controllers |
| Spring Boot Actuator | — | Health checks & metrics | Production readiness — health, info, metrics endpoints |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Docker Compose | Local dev environment | PostgreSQL + PostGIS + MinIO containers |
| MinIO | S3-compatible local storage | Drop-in S3 replacement for dev, same API |
| ktlint | Code formatting | Kotlin linting, integrates with Gradle |
| Detekt | Static analysis | Kotlin code quality checks |

## Setup

```bash
# Initialize with Spring Initializr or Gradle
# build.gradle.kts dependencies:
# implementation("org.springframework.boot:spring-boot-starter-web")
# implementation("org.springframework.boot:spring-boot-starter-data-jpa")
# implementation("org.springframework.boot:spring-boot-starter-security")
# implementation("org.springframework.boot:spring-boot-starter-websocket")
# implementation("org.springframework.boot:spring-boot-starter-validation")
# implementation("org.springframework.boot:spring-boot-starter-actuator")
# implementation("org.flywaydb:flyway-core")
# implementation("org.flywaydb:flyway-database-postgresql")
# implementation("io.jsonwebtoken:jjwt-api:0.12.6")
# runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
# runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
# implementation("org.hibernate.orm:hibernate-spatial")
# implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
# implementation("org.jetbrains.kotlin:kotlin-reflect")
# implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
# runtimeOnly("org.postgresql:postgresql")
# implementation("aws.sdk.kotlin:s3:1.2.28")
# testImplementation("org.springframework.boot:spring-boot-starter-test")
# testImplementation("org.testcontainers:postgresql:1.19.8")
# testImplementation("io.mockk:mockk:1.13.11")
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Spring Boot | Ktor | If you want a lighter-weight, Kotlin-native framework. Less ecosystem, more manual wiring |
| Hibernate / JPA | Exposed (JetBrains) | If you prefer a Kotlin-native SQL DSL. Less mature ORM, but more idiomatic Kotlin |
| Flyway | Liquibase | If you need XML-based changelog format or more complex migration rollback |
| STOMP WebSocket | Raw WebSocket | If you need lower-level control. STOMP gives routing/subscriptions for free |
| jjwt | Spring OAuth2 Resource Server | If using an external IdP (Keycloak, Auth0). For self-issued JWT, jjwt is simpler |
| AWS SDK Kotlin | MinIO Java SDK | If you only target MinIO. AWS SDK ensures S3 compatibility with any provider |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Spring WebFlux (reactive) | Adds complexity without clear benefit for this scale. Hibernate doesn't support reactive well | Spring MVC (blocking) with virtual threads (Java 21+) |
| MongoDB | Relational data (users, cats, matches) fits poorly in document store. Geospatial is weaker than PostGIS | PostgreSQL + PostGIS |
| Socket.IO | Java/Kotlin support is poor, designed for Node.js ecosystem | Spring WebSocket + STOMP |
| Kotlin Coroutines + R2DBC | Immature driver support, debugging complexity, Hibernate incompatible | Blocking JPA with virtual threads |

## Stack Patterns by Variant

**If scaling beyond 10k concurrent WebSocket connections:**
- Consider dedicated message broker (RabbitMQ or Redis Pub/Sub) behind STOMP
- Spring's built-in simple broker works fine for <10k connections

**If photo processing needed (resize, thumbnails):**
- Add imgproxy or Thumbor as a separate service
- Don't process images in the Spring Boot app

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Spring Boot 3.3.x | Java 17+ / Kotlin 2.0.x | Requires Jakarta EE 10 namespace |
| Hibernate Spatial 6.x | PostGIS 3.x | Must add `hibernate-spatial` dependency explicitly |
| Flyway 10.x | Spring Boot 3.3.x | Use `flyway-database-postgresql` module |
| jjwt 0.12.x | Java 17+ | Breaking changes from 0.11.x — use builder API |

## Sources

- Spring Boot official documentation — verified dependency versions
- Hibernate Spatial docs — PostGIS integration patterns
- AWS SDK for Kotlin docs — S3 client configuration
- Kotlin language docs — Kotlin 2.0 compatibility

---
*Stack research for: dating app backend (Kotlin/Spring Boot)*
*Researched: 2025-06-09*
