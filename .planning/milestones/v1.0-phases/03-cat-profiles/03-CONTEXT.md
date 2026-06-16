# Phase 3: Cat Profiles - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the cat profile system — users can create, edit, and delete cat profiles with name, age, breed, bio, and photos. Each user can have up to 5 cats. Cat photos use the same presigned URL upload flow as user photos via a separate CatPhotoService. Each cat appears as an independent entry in the Phase 4 discovery feed. Full cascade deletion from user → cats → cat photos.

</domain>

<decisions>
## Implementation Decisions

### Cat Data Model
- **D-01:** Age stored as number + unit enum (YEARS / MONTHS) — supports both kittens ("4 months") and adults ("3 years"). Cats' exact birthdays are often unknown
- **D-02:** Breed is free-text and optional (nullable) — many owners don't know the breed or have a mix
- **D-03:** Bio/description is optional (nullable), max 500 characters
- **D-04:** Name is required, max 100 characters (same limit as UserProfile.displayName)

### Cat Photo Rules
- **D-05:** Maximum 10 photos per cat (higher than user's 6 — cats are the hero content of the app)
- **D-06:** Minimum 1 photo required before a cat appears in discovery feed
- **D-07:** Separate CatPhotoService mirroring the existing PhotoService pattern — same presigned URL → upload → confirm → thumbnail flow, but with cat-specific S3 paths (`cats/{catId}/...`)
- **D-08:** File constraints identical to user photos: JPEG/PNG only, 10MB max, 200×200 thumbnails

### Multi-Cat & Discovery
- **D-09:** Maximum 5 cats per user — covers multi-cat households without flooding the feed
- **D-10:** Each cat appears as a separate card in the Phase 4 discovery feed — users swipe on individual cats, not per-user
- **D-11:** No cat completeness endpoint — validate required fields (name, age) on create. Photo minimum for discovery enforced by the discovery query in Phase 4

### Deletion Behavior
- **D-12:** Full cascade delete when cat profile is deleted — remove all cat_photos DB rows and delete S3 objects (originals + thumbnails). No orphans
- **D-13:** User account deletion cascades to all cats and cat photos — set up JPA cascade and DB-level ON DELETE CASCADE constraints now

### Claude's Discretion
No areas deferred to Claude's discretion — all decisions made by user.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md` — Core value (cat-first discovery), constraints (Kotlin + Spring Boot, PostgreSQL, S3-compatible), out-of-scope items
- `.planning/REQUIREMENTS.md` — CAT-01 through CAT-05 requirement definitions and traceability
- `.planning/ROADMAP.md` §Phase 3 — Success criteria for this phase

### Prior Phase Context
- `.planning/phases/01-foundation-auth/01-CONTEXT.md` — Package structure decisions (D-01–D-04), token lifecycle, error format (RFC 7807). Phase 3 MUST follow the same domain-first vertical slice pattern (`com.catspell.api.cat.*`)
- `.planning/phases/02-user-profiles-photos/02-CONTEXT.md` — Photo upload decisions (D-05–D-09), profile completeness pattern, PostGIS setup. CatPhotoService mirrors the PhotoService pattern established here

### Stack Research
- `.planning/research/STACK.md` — Recommended versions, dependencies, Kotlin entity gotchas
- `.planning/research/SUMMARY.md` — Architecture approach, critical pitfalls (Kotlin entity gotchas, N+1 queries)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `User` entity (`com.catspell.api.auth.model.User`) — UUID PK, FK target for cat profiles (one-to-many)
- `UserPhoto` entity (`com.catspell.api.profile.model.UserPhoto`) — Reference pattern for CatPhoto entity (s3Key, thumbnailS3Key, displayOrder, status, contentType, fileSizeBytes)
- `PhotoService` (`com.catspell.api.profile.service.PhotoService`) — Reference implementation for CatPhotoService: presigned URL generation, upload confirmation, thumbnail generation, reorder, delete
- `StorageService` (`com.catspell.api.profile.service.StorageService`) — Shared S3 service for presigned URLs, object get/put/delete. Reuse directly from cat photo service
- `GlobalExceptionHandler` — RFC 7807 error handling ready. Cat validation errors use the same `violations` array pattern
- `ResourceNotFoundException`, `PhotoLimitExceededException`, `InvalidPhotoTypeException` — Reusable exception classes

### Established Patterns
- Domain-first vertical slices: `com.catspell.api.{domain}.controller/service/model/`
- JPA entities as classes (not data classes) with `equals`/`hashCode` overrides (kotlin-jpa plugin)
- DTOs as Kotlin data classes with Jakarta validation annotations
- Flyway versioned migrations: `V{N}__description.sql`
- Photo upload flow: request presigned URL → client uploads → confirm → server generates thumbnail → status PENDING → ACTIVE
- `extractUserId()` pattern in controllers via `SecurityContextHolder`

### Integration Points
- `SecurityConfig.securityFilterChain` — add permit rules for new cat profile/photo endpoints
- Flyway migrations — next available migration number for `cat_profiles` and `cat_photos` tables
- `StorageService` — inject into new CatPhotoService for S3 operations
- S3 key prefix — `cats/{catId}/` for cat photos, `thumbnails/cats/{catId}/` for thumbnails

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond decisions captured above. Implementation should mirror the Phase 2 profile + photo pattern:
- Separate `cat_profiles` table with FK to `users`, and `cat_photos` table with FK to `cat_profiles`
- CatProfile entity: name (required), age (int), ageUnit (enum YEARS/MONTHS), breed (nullable), bio (nullable), FK to User, timestamps
- CatPhoto entity mirrors UserPhoto structure with FK to CatProfile instead of User
- API endpoints under `/api/cats` for CRUD and `/api/cats/{catId}/photos` for photo management

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 3-Cat Profiles*
*Context gathered: 2026-06-12*
