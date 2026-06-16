# Phase 2: User Profiles & Photos - Context

**Gathered:** 2025-06-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver user profile management with photo uploads to S3-compatible storage and GPS location storage. Users can create and edit their profile (display name, bio, DOB, gender, dating preferences), upload/delete/reorder photos via S3 presigned URLs with server-side thumbnail generation, and set/update their GPS location. Profile completeness gates discovery feed access.

</domain>

<decisions>
## Implementation Decisions

### Dating Preferences
- **D-01:** Preference fields: gender preference, age range (min/max), and max distance radius — all three required
- **D-02:** Gender model: binary (Male / Female) with preference options (Men / Women / Everyone)
- **D-03:** No default values for preferences — user must explicitly set all preferences during onboarding before profile is discoverable
- **D-04:** Age range bounds: 18–99 (enforced by backend validation)

### Photo Rules
- **D-05:** Maximum 6 photos per user profile
- **D-06:** Minimum 1 photo required before profile appears in discovery feed
- **D-07:** Photos are ordered — display order stored, first photo is the primary/thumbnail photo. Users can reorder via the API
- **D-08:** Upload validation: JPEG/PNG only, max 10MB per photo. Presigned URL constraints enforced
- **D-09:** Server-side thumbnail generation on upload (e.g., 200×200 for list views) — not just raw storage

### Profile Completeness
- **D-10:** Full profile required before appearing in discovery: display name, bio, date of birth, gender, at least 1 photo, all preferences set, and GPS location
- **D-11:** Store date of birth (not age) — age calculated dynamically. Enables accurate age-range filtering in Phase 4
- **D-12:** Bio max length: 1000 characters
- **D-13:** Backend exposes a profile completeness endpoint — returns which fields are missing and a boolean `isComplete`. Mobile app uses this to gate discovery access and show onboarding progress

### Location
- **D-14:** Location updated on every app open — mobile app sends coordinates automatically, backend stores the latest
- **D-15:** Store location as PostGIS geometry column (POINT) — install PostGIS extension in this phase. Enables native ST_DWithin/ST_Distance queries in Phase 4
- **D-16:** Other users see relative distance only (e.g., "5 km away") — never expose raw coordinates. Privacy-first approach

### Claude's Discretion
No areas deferred to Claude's discretion — all decisions made by user.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md` — Core value (cat-first discovery), constraints (Kotlin + Spring Boot, PostgreSQL, S3-compatible), out-of-scope items
- `.planning/REQUIREMENTS.md` — PROF-01 through PROF-05 requirement definitions and traceability
- `.planning/ROADMAP.md` §Phase 2 — Success criteria for this phase

### Prior Phase Context
- `.planning/phases/01-foundation-auth/01-CONTEXT.md` — Package structure decisions (D-01–D-04), token lifecycle, error format (RFC 7807). Phase 2 MUST follow the same domain-first vertical slice pattern (`com.catspell.api.profile.*`)

### Stack Research
- `.planning/research/STACK.md` — Recommended versions, dependencies, Kotlin entity gotchas
- `.planning/research/SUMMARY.md` — Architecture approach, critical pitfalls (Kotlin entity gotchas, N+1 queries)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `User` entity (`com.catspell.api.auth.model.User`) — UUID PK, email, passwordHash, timestamps. Profile data references this user via FK
- `SecurityConfig` — Stateless JWT auth already configured. New profile/photo endpoints need permit rules added
- `GlobalExceptionHandler` — RFC 7807 error handling ready. Profile validation errors will use the same `violations` array pattern
- `JwtAuthenticationFilter` + `JwtService` — Authentication infrastructure for all new protected endpoints

### Established Patterns
- Domain-first vertical slices: `com.catspell.api.{domain}.controller/service/model/`
- JPA entities as classes (not data classes) with `equals`/`hashCode` overrides (kotlin-jpa plugin)
- DTOs as Kotlin data classes with Jakarta validation annotations
- Flyway versioned migrations: `V{N}__description.sql` (next is V3)
- `application.yml` with env var fallbacks for config

### Integration Points
- `SecurityConfig.securityFilterChain` — add permit rules for new profile/photo endpoints
- Flyway migrations — V3+ for user_profiles, user_photos tables, PostGIS extension
- `application.yml` — add S3/MinIO configuration properties
- Podman compose — add MinIO container for local dev S3-compatible storage

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond decisions captured above. Stack research recommends:
- AWS SDK for Kotlin (or Spring Cloud AWS) for S3 presigned URL generation
- MinIO container in Podman compose for local dev S3 parity
- PostGIS extension enabled via Flyway migration (`CREATE EXTENSION IF NOT EXISTS postgis`)
- Separate `user_profiles` table (not adding columns to `users`) to keep auth and profile concerns separated

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 2-User Profiles & Photos*
*Context gathered: 2025-06-11*
