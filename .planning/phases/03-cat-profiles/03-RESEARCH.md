# Phase 3: Cat Profiles — Research

**Researched:** 2026-06-12
**Confidence:** HIGH
**Approach:** Mirror established Phase 2 patterns (profile + photo) for cat domain

## Executive Summary

Phase 3 is a **pattern replication** phase — the cat profile + cat photo system mirrors the existing user profile + user photo system almost 1:1. The primary risk is not technical novelty but fidelity to established patterns. Key differences: `ManyToOne` (user→cats) instead of `OneToOne` (user→profile), age stored as number + unit enum instead of date-of-birth, and a higher photo limit (10 vs 6).

## Codebase Pattern Analysis

### Entity Pattern (Source of Truth)

All JPA entities follow this pattern:
- **Class, not data class** — `kotlin-jpa` plugin handles no-arg constructor
- UUID PK with `@GeneratedValue(strategy = GenerationType.UUID)`
- `var` properties with column annotations
- `equals`/`hashCode` override: ID-based equality, `javaClass.hashCode()` for hashCode
- Timestamps: `createdAt` (immutable), `updatedAt` (mutable)
- Lazy fetch for relationships: `@ManyToOne(fetch = FetchType.LAZY)`

**Reference files:**
- `com.catspell.api.auth.model.User` — base entity pattern
- `com.catspell.api.profile.model.UserProfile` — `@OneToOne` to User with FK
- `com.catspell.api.profile.model.UserPhoto` — `@ManyToOne` to User, S3 fields, status lifecycle

### DTO Pattern

- Kotlin `data class` with Jakarta `@field:` validation annotations
- Separate request/response classes in a `*Dtos.kt` file
- `@NotBlank`, `@Size(max=N)`, `@NotNull`, `@Min`, `@Max` for validation
- No nested DTOs — flat structures

**Reference:** `ProfileDtos.kt`, `PhotoDtos.kt`

### Repository Pattern

- Interface extending `JpaRepository<Entity, UUID>`
- Custom query methods via Spring Data naming convention
- No `@Query` annotations used yet — convention-based queries only

**Reference:** `UserProfileRepository`, `UserPhotoRepository`

### Service Pattern

- `@Service` + constructor injection
- `@Transactional` on write methods
- Companion object for constants (MAX_PHOTOS, ALLOWED_CONTENT_TYPES)
- Throw domain exceptions (`ResourceNotFoundException`, `PhotoLimitExceededException`)
- Private `toResponse()` mapping method

**Reference:** `PhotoService`, `ProfileService`

### Controller Pattern

- `@RestController` + `@RequestMapping("/api/{domain}")`
- Private `extractUserId()` via `SecurityContextHolder`
- `@Valid @RequestBody` for validated DTOs
- Return `ResponseEntity<T>` with explicit status codes
- No cross-cutting controller base class

**Reference:** `ProfileController`, `PhotoController`

### Photo Upload Flow (CRITICAL — must replicate exactly)

1. Client calls `POST /upload-url` with `{contentType, fileName}`
2. Server validates content type (JPEG/PNG only), checks photo count limit
3. Server creates `PENDING` photo record, generates S3 presigned URL
4. Returns `{photoId, uploadUrl, s3Key}` — client uploads directly to S3
5. Client calls `POST /{photoId}/confirm`
6. Server verifies object exists in S3, downloads original, generates 200×200 thumbnail
7. Uploads thumbnail to separate S3 key, sets photo status to `ACTIVE`
8. Returns confirmed photo details

**S3 key convention:**
- User photos: `photos/{userId}/{uuid}.{ext}`
- User thumbnails: `thumbnails/{userId}/{photoId}.jpg`
- Cat photos (new): `cats/{catId}/{uuid}.{ext}`
- Cat thumbnails (new): `thumbnails/cats/{catId}/{photoId}.jpg`

### Flyway Migration Pattern

- Versioned: `V{N}__description.sql`
- Next available: **V6** (V1–V5 exist)
- Tables use `snake_case`, UUID PK with `gen_random_uuid()`, `TIMESTAMPTZ` for timestamps
- FK constraints with `ON DELETE CASCADE`
- Indexes on FK columns and query columns

### Security Configuration

`SecurityConfig` uses `anyRequest().authenticated()` — **no changes needed** for new cat endpoints. All authenticated endpoints are automatically protected.

### Testing Pattern

- `BaseIntegrationTest` abstract class with Testcontainers (PostGIS + MinIO)
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate`
- Test files: `{Domain}IntegrationTest.kt` in matching package
- Helper methods for registration/login to get JWT tokens
- 54 existing integration tests passing

## Technical Decisions

### D-01: Age Storage (YEARS/MONTHS enum)

**Implementation:** Kotlin enum `AgeUnit { YEARS, MONTHS }` stored as `VARCHAR(10)` in DB. JPA `@Enumerated(EnumType.STRING)`. Entity has `age: Int` + `ageUnit: AgeUnit` fields. DB columns: `age INT NOT NULL` + `age_unit VARCHAR(10) NOT NULL`.

### D-05/D-06/D-07: CatPhotoService

**Implementation:** New `CatPhotoService` class mirroring `PhotoService` with these differences:
- `MAX_PHOTOS = 10` (vs 6 for user photos)
- Injects `CatPhotoRepository` + `StorageService` + `CatProfileRepository`
- S3 key prefix: `cats/{catId}/` instead of `photos/{userId}/`
- Thumbnail prefix: `thumbnails/cats/{catId}/` instead of `thumbnails/{userId}/`
- Photo ownership checked via catId→catProfile→user chain

### D-09: Multi-Cat Limit

**Implementation:** Check `catProfileRepository.countByUserId(userId)` before creating. Throw `CatLimitExceededException` if >= 5. New exception class in `com.catspell.api.common.exception`.

### D-12/D-13: Cascade Deletion

**Database level:** Both `cat_profiles` and `cat_photos` tables use `ON DELETE CASCADE`:
- `cat_profiles.user_id REFERENCES users(id) ON DELETE CASCADE`
- `cat_photos.cat_profile_id REFERENCES cat_profiles(id) ON DELETE CASCADE`

**JPA level:** `CatProfile` entity defines `@OneToMany(mappedBy = "catProfile", cascade = [CascadeType.ALL], orphanRemoval = true)` for cat photos.

**Service level:** `deleteCatProfile()` must also delete S3 objects (originals + thumbnails) — DB cascade handles rows, but S3 cleanup requires explicit service code.

## Database Schema Design

### V6__create_cat_profiles.sql
```sql
CREATE TABLE cat_profiles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    age         INT NOT NULL,
    age_unit    VARCHAR(10) NOT NULL,
    breed       VARCHAR(100),
    bio         VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cat_profiles_user_id ON cat_profiles(user_id);
```

### V7__create_cat_photos.sql
```sql
CREATE TABLE cat_photos (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cat_profile_id      UUID NOT NULL REFERENCES cat_profiles(id) ON DELETE CASCADE,
    s3_key              VARCHAR(500) NOT NULL,
    thumbnail_s3_key    VARCHAR(500),
    display_order       INT NOT NULL,
    content_type        VARCHAR(50) NOT NULL,
    file_size_bytes     BIGINT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cat_photos_cat_profile_id ON cat_photos(cat_profile_id);
CREATE INDEX idx_cat_photos_cat_order ON cat_photos(cat_profile_id, display_order);
```

## API Endpoint Design

### Cat Profile CRUD — `/api/cats`
| Method | Path | Description | Status |
|--------|------|-------------|--------|
| POST | `/api/cats` | Create cat profile | 201 |
| GET | `/api/cats` | List user's cats | 200 |
| GET | `/api/cats/{catId}` | Get single cat | 200 |
| PUT | `/api/cats/{catId}` | Update cat profile | 200 |
| DELETE | `/api/cats/{catId}` | Delete cat + photos + S3 | 204 |

### Cat Photo Management — `/api/cats/{catId}/photos`
| Method | Path | Description | Status |
|--------|------|-------------|--------|
| POST | `/api/cats/{catId}/photos/upload-url` | Request presigned URL | 200 |
| POST | `/api/cats/{catId}/photos/{photoId}/confirm` | Confirm upload | 200 |
| DELETE | `/api/cats/{catId}/photos/{photoId}` | Delete photo | 204 |
| PUT | `/api/cats/{catId}/photos/reorder` | Reorder photos | 200 |
| GET | `/api/cats/{catId}/photos` | List cat photos | 200 |

## Package Structure

Following domain-first vertical slices:
```
com.catspell.api.cat/
├── controller/
│   ├── CatProfileController.kt
│   └── CatPhotoController.kt
├── service/
│   ├── CatProfileService.kt
│   └── CatPhotoService.kt
└── model/
    ├── CatProfile.kt          (entity)
    ├── CatPhoto.kt            (entity)
    ├── AgeUnit.kt             (enum)
    ├── CatProfileRepository.kt
    ├── CatPhotoRepository.kt
    ├── CatProfileDtos.kt      (request/response DTOs)
    └── CatPhotoDtos.kt        (request/response DTOs)
```

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| S3 orphans on cascade delete | MEDIUM | Service-level S3 cleanup before DB delete |
| N+1 queries loading cat photos | LOW | Use explicit `JOIN FETCH` or separate queries |
| Photo count drift (PENDING + ACTIVE) | LOW | Mirror PhotoService pattern: count both statuses |
| Missing ownership check on cat photos | HIGH | Chain: photoId → catId → userId must match authenticated user |

## Validation Architecture

### Required Validations
- Cat name: `@NotBlank`, `@Size(max = 100)`
- Age: `@NotNull`, `@Min(0)` (kittens can be 0 months)
- AgeUnit: `@NotNull`, must be valid enum value
- Breed: `@Size(max = 100)` (nullable)
- Bio: `@Size(max = 500)` (nullable)
- Cat count per user: max 5 (service-level)
- Photo content type: JPEG/PNG only
- Photo count per cat: max 10

## RESEARCH COMPLETE
