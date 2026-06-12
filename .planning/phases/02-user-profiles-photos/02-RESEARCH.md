# Phase 2: User Profiles & Photos — Research

**Phase:** 2
**Researched:** 2025-06-11
**Confidence:** HIGH

## Research Question

What do I need to know to PLAN Phase 2 (User Profiles & Photos) well?

## 1. Database Schema Design

### user_profiles Table

Separate table from `users` — keeps auth and profile concerns decoupled (confirmed in CONTEXT.md specifics).

```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    bio VARCHAR(1000),          -- D-12: max 1000 chars
    date_of_birth DATE NOT NULL, -- D-11: store DOB, not age
    gender VARCHAR(20) NOT NULL,  -- D-02: Male / Female
    gender_preference VARCHAR(20) NOT NULL, -- D-02: Men / Women / Everyone
    age_min INT NOT NULL,         -- D-04: 18–99
    age_max INT NOT NULL,         -- D-04: 18–99
    max_distance_km INT NOT NULL, -- D-01: required
    location GEOMETRY(POINT, 4326), -- D-15: PostGIS POINT
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Key decisions:**
- `user_id` is UNIQUE — one profile per user (1:1 relationship)
- `gender` and `gender_preference` as VARCHAR enums validated by the application, not DB-level enums (easier to extend later)
- `location` as PostGIS GEOMETRY(POINT, 4326) — SRID 4326 = WGS 84 (GPS coordinates)
- Age range validated by app: min >= 18, max <= 99, min <= max

### user_photos Table

```sql
CREATE TABLE user_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    s3_key VARCHAR(500) NOT NULL,
    thumbnail_s3_key VARCHAR(500),
    display_order INT NOT NULL,  -- D-07: ordered photos
    content_type VARCHAR(50) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Key decisions:**
- `display_order` for photo ordering (D-07). First photo (order 0) is primary/thumbnail
- `s3_key` + `thumbnail_s3_key` — separate keys for original and thumbnail
- D-05: Max 6 photos enforced at service layer, not DB constraint
- D-06: Min 1 photo checked by completeness endpoint, not DB constraint

### PostGIS Extension

D-15 requires installing PostGIS in this phase. Flyway migration V3:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

**Important:** This requires the PostgreSQL container image to include PostGIS. Current `docker-compose.yml` uses `postgres:16-alpine` which does NOT include PostGIS. Must switch to `postgis/postgis:16-3.4-alpine`.

## 2. S3/MinIO Integration

### Library Choice: AWS SDK for Kotlin

Per STACK.md recommendation, use `aws.sdk.kotlin:s3`. This is the official Kotlin-native AWS SDK (coroutine-based). For presigned URLs, also need `aws.sdk.kotlin:s3-presigner` or use the Java AWS SDK's presigner.

**Important finding:** The AWS SDK for Kotlin S3 client is coroutine-based. Since the project uses Spring MVC (blocking), options:
1. **Use `runBlocking` in service methods** — acceptable for presigned URL generation (non-blocking S3 call, just signs locally)
2. **Use the AWS SDK for Java v2 instead** — non-coroutine, simpler integration with Spring MVC
3. **Use Spring Cloud AWS** — higher-level abstraction, auto-configured S3 client

**Recommended: AWS SDK for Java v2** (`software.amazon.awssdk:s3` + `software.amazon.awssdk:s3-presigner`). Reasons:
- Presigned URL generation is entirely local (no network call) — the SDK signs the URL using credentials
- No coroutine overhead for what is essentially a signing operation
- Better documented for Spring Boot integration
- MinIO is fully compatible with the AWS Java SDK v2

### Presigned URL Upload Flow

1. Client requests upload URL: `POST /api/profile/photos/upload-url` with `contentType`, `fileName`
2. Backend generates S3 key: `photos/{userId}/{uuid}.{ext}`
3. Backend generates presigned PUT URL (expires in 15 min)
4. Backend creates `user_photos` record with status `PENDING`
5. Client uploads directly to S3/MinIO using the presigned URL
6. Client confirms upload: `POST /api/profile/photos/{photoId}/confirm`
7. Backend verifies the object exists in S3, generates thumbnail, sets status to `ACTIVE`

**Alternative (simpler for MVP):** Skip the confirm step. Client uploads, then calls a "register photo" endpoint that validates the S3 object exists. Even simpler: direct upload through backend (multipart) — but presigned URLs are explicitly required by D-08.

**Recommended MVP flow (simplified):**
1. `POST /api/profile/photos/upload-url` → returns `{ uploadUrl, photoId, s3Key }`
2. Client PUTs file to `uploadUrl`
3. `POST /api/profile/photos/{photoId}/confirm` → backend validates S3 object, generates thumbnail, activates photo
4. This avoids orphaned photo records if client never uploads

### MinIO Local Dev Setup

Add MinIO to `docker-compose.yml`:

```yaml
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: catspell
    MINIO_ROOT_PASSWORD: catspell123
  ports:
    - "9000:9000"   # S3 API
    - "9001:9001"   # Console UI
  volumes:
    - minio-data:/data
```

S3 configuration in `application.yml`:

```yaml
storage:
  s3:
    endpoint: ${S3_ENDPOINT:http://localhost:9000}
    region: ${S3_REGION:us-east-1}
    bucket: ${S3_BUCKET:catspell-photos}
    access-key: ${S3_ACCESS_KEY:catspell}
    secret-key: ${S3_SECRET_KEY:catspell123}
```

### Bucket Initialization

MinIO needs the bucket created on first use. Options:
1. **Application startup** — `@PostConstruct` or `ApplicationRunner` that creates bucket if it doesn't exist
2. **MinIO init script** in docker-compose
3. **Manual step** in dev setup

Recommended: Application startup with `createBucketIfNotExists()` in the S3 service.

## 3. Thumbnail Generation

D-09 requires server-side thumbnail generation (200×200 for list views). STACK.md recommends against processing images in Spring Boot ("Use imgproxy or Thumbor as a separate service"). However, for MVP:

**Recommended: Java's `javax.imageio.ImageIO` or Thumbnailator library**

Thumbnailator is a mature, lightweight library for Java/Kotlin:
```kotlin
// build.gradle.kts
implementation("net.coobird:thumbnailator:0.4.20")

// Usage
Thumbnails.of(inputStream)
    .size(200, 200)
    .outputFormat("jpeg")
    .toOutputStream(outputStream)
```

**MVP approach:** Generate thumbnail synchronously during the `confirm` step. This is acceptable because:
- Photo uploads are infrequent (user onboarding + occasional updates)
- Thumbnailator is fast for single images
- Can be moved to async processing later if needed

**Where to generate:**
1. Download original from S3
2. Generate 200×200 thumbnail in memory
3. Upload thumbnail to S3 at `thumbnails/{userId}/{uuid}.jpg`
4. Update `user_photos.thumbnail_s3_key`

## 4. API Endpoint Design

Following Phase 1's pattern: `@RestController` + `@RequestMapping("/api/...")`.

### Profile Endpoints

| Method | Path | Auth | Description | Req |
|--------|------|------|-------------|-----|
| POST | `/api/profile` | ✓ | Create profile | PROF-01 |
| GET | `/api/profile` | ✓ | Get own profile | PROF-01 |
| PUT | `/api/profile` | ✓ | Update profile | PROF-02 |
| PUT | `/api/profile/location` | ✓ | Update GPS location | PROF-05 |
| GET | `/api/profile/completeness` | ✓ | Check completeness | D-13 |

### Photo Endpoints

| Method | Path | Auth | Description | Req |
|--------|------|------|-------------|-----|
| POST | `/api/profile/photos/upload-url` | ✓ | Get presigned upload URL | PROF-03 |
| POST | `/api/profile/photos/{id}/confirm` | ✓ | Confirm upload + thumbnail | PROF-03 |
| DELETE | `/api/profile/photos/{id}` | ✓ | Delete photo | PROF-04 |
| PUT | `/api/profile/photos/reorder` | ✓ | Reorder photos | D-07 |
| GET | `/api/profile/photos` | ✓ | List own photos | PROF-03 |

### Security Considerations

- All profile/photo endpoints require authentication (already handled by `anyRequest().authenticated()` in SecurityConfig)
- Profile CRUD must verify `userId` matches authenticated user (service-layer check via `SecurityContextHolder`)
- Photo deletion must verify ownership
- Presigned URL content-type must be validated (D-08: JPEG/PNG only)
- File size enforced via presigned URL conditions (D-08: max 10MB)

## 5. Testing Strategy

### Critical Issue: H2 vs PostGIS

Current tests use H2 in-memory database with `ddl-auto: create-drop`. **H2 does not support PostGIS.** The `GEOMETRY(POINT, 4326)` column type and `CREATE EXTENSION postgis` will fail in H2.

**Options:**
1. **Switch to Testcontainers PostgreSQL+PostGIS** — proper testing, requires test infrastructure change
2. **Store location as separate lat/lon doubles** — simpler but contradicts D-15
3. **Conditional test config** — H2 for non-geo tests, Testcontainers for geo tests

**Recommended: Option 1 — Testcontainers**

Since D-15 explicitly requires PostGIS geometry columns and Testcontainers is already in the recommended stack (STACK.md), this is the right time to switch. Benefits:
- Tests run against real PostgreSQL with PostGIS
- Flyway migrations execute in tests (validates migrations)
- No more H2/PostgreSQL behavioral differences
- Sets up infrastructure for Phase 4 (distance queries)

**Testcontainers setup:**
```kotlin
// build.gradle.kts
testImplementation("org.testcontainers:postgresql:1.19.8")
testImplementation("org.testcontainers:junit-jupiter:1.19.8")

// Remove or keep h2 for fast unit tests only
```

**Test application.yml changes:**
```yaml
spring:
  datasource:
    url: jdbc:tc:postgis/postgis:16-3.4-alpine:///catspell
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway in tests too
  flyway:
    enabled: true
```

**Impact:** All existing Phase 1 tests (26 tests) must still pass with Testcontainers. Since they don't use PostGIS features, they should work without changes beyond the test config update.

### Test Categories for Phase 2

1. **Profile CRUD integration tests** — create, read, update profile
2. **Photo upload flow tests** — presigned URL, confirm, thumbnail generation
3. **Photo management tests** — delete, reorder, max 6 limit
4. **Profile completeness tests** — missing fields, all complete
5. **Location update tests** — GPS coordinate storage
6. **Validation tests** — age bounds, bio length, photo types
7. **Authorization tests** — can only modify own profile/photos

## 6. Package Structure

Following Phase 1's domain-first vertical slice pattern:

```
com.catspell.api.profile/
├── controller/
│   ├── ProfileController.kt
│   └── PhotoController.kt
├── service/
│   ├── ProfileService.kt
│   ├── PhotoService.kt
│   └── StorageService.kt       # S3 abstraction
├── model/
│   ├── UserProfile.kt           # JPA entity
│   ├── UserPhoto.kt             # JPA entity
│   ├── UserProfileRepository.kt
│   ├── UserPhotoRepository.kt
│   ├── ProfileDtos.kt           # Request/Response DTOs
│   └── PhotoDtos.kt             # Request/Response DTOs
└── config/
    └── S3Config.kt              # S3 client bean configuration
```

`StorageService` abstracts S3 operations — makes it easy to swap implementations for testing.

## 7. Dependencies to Add

### build.gradle.kts

```kotlin
// PostGIS + Hibernate Spatial
implementation("org.hibernate.orm:hibernate-spatial")

// S3 (AWS SDK Java v2)
implementation("software.amazon.awssdk:s3:2.25.60")
implementation("software.amazon.awssdk:s3-presigner:2.25.60")

// Thumbnail generation
implementation("net.coobird:thumbnailator:0.4.20")

// Testcontainers (move from H2)
testImplementation("org.testcontainers:postgresql:1.19.8")
testImplementation("org.testcontainers:junit-jupiter:1.19.8")
```

**Note on Hibernate Spatial:** Requires the PostgreSQL JDBC driver to support PostGIS types. The `org.postgresql:postgresql` driver already in dependencies handles this. Hibernate Spatial adds the JPA type mappings.

## 8. Docker Compose Changes

1. **Switch PostgreSQL image** to `postgis/postgis:16-3.4-alpine` (includes PostGIS extension)
2. **Add MinIO service** for local S3-compatible storage
3. **Add MinIO volume** for data persistence

## 9. Flyway Migration Plan

| Version | Name | Content |
|---------|------|---------|
| V3 | `enable_postgis_extension` | `CREATE EXTENSION IF NOT EXISTS postgis` |
| V4 | `create_user_profiles_table` | Profile table with all fields + PostGIS POINT column |
| V5 | `create_user_photos_table` | Photos table with ordering and S3 keys |

Indexes:
- `user_profiles.user_id` — UNIQUE (already via constraint)
- `user_profiles.location` — GiST index (for Phase 4 queries, install early per pitfall #5)
- `user_photos.user_id` — B-tree (lookup by user)
- `user_photos(user_id, display_order)` — composite for ordered retrieval

## 10. Validation Architecture

### Request Validation (Jakarta Bean Validation)

| Field | Constraint | Source |
|-------|-----------|--------|
| displayName | @NotBlank, @Size(max=100) | D-10 |
| bio | @Size(max=1000) | D-12 |
| dateOfBirth | @NotNull, @Past, custom min 18 years | D-04 |
| gender | @NotNull, enum validation | D-02 |
| genderPreference | @NotNull, enum validation | D-02 |
| ageMin | @Min(18), @Max(99) | D-04 |
| ageMax | @Min(18), @Max(99), >= ageMin | D-04 |
| maxDistanceKm | @Min(1) | D-01 |
| latitude | @Min(-90), @Max(90) | GPS bounds |
| longitude | @Min(-180), @Max(180) | GPS bounds |
| contentType | JPEG or PNG only | D-08 |

### Cross-field Validation

- `ageMin <= ageMax` — custom validator or service-level check
- Age calculated from DOB must be >= 18 — service-level check

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| H2→Testcontainers migration breaks existing tests | HIGH | Run Phase 1 tests after migration to verify |
| MinIO not started = S3 errors in dev | MEDIUM | Startup check in StorageService + clear error message |
| Thumbnail generation OOM on large images | LOW | Validate file size before processing (D-08: 10MB max) |
| Presigned URL expiry race condition | LOW | 15-min expiry, confirm endpoint validates S3 object |
| PostGIS extension not available | HIGH | Use postgis/postgis Docker image, verify in CI |

## RESEARCH COMPLETE

Research covers all Phase 2 requirements (PROF-01 through PROF-05) and all 16 implementation decisions from CONTEXT.md. Key findings: switch to Testcontainers for PostGIS support, use AWS SDK Java v2 for S3 (not Kotlin coroutine SDK), add Thumbnailator for thumbnail generation, and implement a presigned URL upload flow with confirmation step.
