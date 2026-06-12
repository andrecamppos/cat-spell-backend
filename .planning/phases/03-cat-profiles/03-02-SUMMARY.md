---
phase: 03-cat-profiles
plan: 02
subsystem: api
tags: [cat-photos, s3, presigned-urls, thumbnails, cascade-deletion, ownership-chain]

requires:
  - phase: 03-01-cat-profile-crud
    provides: CatProfile entity, CatProfileService, CatProfileController, CatProfileRepository
  - phase: 02-02-photo-management
    provides: StorageService, S3Config, presigned URL upload pattern, Thumbnailator, MinIO Testcontainer
provides:
  - Cat photo upload via presigned URLs (POST /api/cats/{catId}/photos/upload-url)
  - Cat photo confirm with server-side 200x200 thumbnail generation
  - Cat photo delete, reorder, and list endpoints
  - Cascade S3 cleanup on cat profile deletion (no orphaned S3 objects)
affects: [04-discovery-matching]

tech-stack:
  added: []
  patterns: [cat-scoped S3 key pattern cats/{catId}/{uuid}.ext, ownership chain validation photoId→catId→userId]

key-files:
  created:
    - src/main/resources/db/migration/V7__create_cat_photos.sql
    - src/main/kotlin/com/catspell/api/cat/model/CatPhoto.kt
    - src/main/kotlin/com/catspell/api/cat/model/CatPhotoRepository.kt
    - src/main/kotlin/com/catspell/api/cat/model/CatPhotoDtos.kt
    - src/main/kotlin/com/catspell/api/cat/service/CatPhotoService.kt
    - src/main/kotlin/com/catspell/api/cat/controller/CatPhotoController.kt
    - src/test/kotlin/com/catspell/api/cat/CatPhotoIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/cat/CatCascadeDeleteIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/cat/service/CatProfileService.kt
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt

key-decisions:
  - "S3 key pattern: cats/{catId}/{uuid}.ext for originals, thumbnails/cats/{catId}/{photoId}.jpg for thumbnails"
  - "10-photo-per-cat limit (PENDING + ACTIVE counted) — higher than user photos (6) since cats are the product"
  - "Ownership chain validation: all photo ops verify userId→catId ownership before proceeding"
  - "CatPhotoLimitExceededException returns 400 Bad Request — client-correctable error"
  - "Cascade S3 cleanup: CatProfileService.deleteCatProfile fetches all photos and deletes S3 objects before DB cascade"
  - "DB cascade (ON DELETE CASCADE) handles cat_photos rows; service handles S3 object cleanup"

patterns-established:
  - "Cat photo endpoints nested under cat: /api/cats/{catId}/photos/*"
  - "Ownership chain: verifyCatOwnership helper reused by all CatPhotoService methods"
  - "Cascade deletion: service-level S3 cleanup + DB-level row cascade"
  - "Cat photo tests: create user → create cat → upload+confirm photo → test operation"

requirements-completed: [CAT-02]

duration: ~20min
completed: 2026-06-12
---

# Plan 03-02 Summary: Cat Photo Management + Cascade Deletion

**Cat photo presigned URL upload flow, thumbnails, ownership chain validation, and cascade S3 cleanup on cat deletion**

## Performance

- **Tasks:** 10/10 complete
- **Files created:** 8
- **Files modified:** 3

## Accomplishments
- V7 Flyway migration: cat_photos table with FK to cat_profiles (ON DELETE CASCADE)
- CatPhoto JPA entity mirroring UserPhoto with ManyToOne LAZY to CatProfile
- CatPhotoService: presigned URL upload, confirm + 200x200 thumbnail, delete, reorder, list
- CatPhotoController: REST endpoints at /api/cats/{catId}/photos
- 10-photo-per-cat limit enforced (PENDING + ACTIVE counted)
- Ownership chain validation on all photo operations (userId→catId→photoId)
- CatProfileService updated: cascade S3 cleanup deletes originals + thumbnails before cat profile deletion
- CatPhotoLimitExceededException mapped to 400 in GlobalExceptionHandler
- 11 photo integration tests: upload URL, invalid content type, 10-photo limit, confirm, confirm 404, list, delete, delete other user, reorder, reorder bad IDs, other user's cat
- 3 cascade deletion tests: delete cat with photos (DB + S3), list excludes deleted, multi-photo S3 cleanup

## Task Commits

1. **Tasks 1–10: Full cat photo management + cascade deletion** — `5996399`

## Deviations from Plan

- All 10 tasks committed as a single atomic unit — the executor built the complete photo+cascade system as one coherent change
- Plan specified CAT-02 as the sole requirement; commit message also references CAT-06, CAT-07, CAT-08 which were implicit sub-requirements for cascade deletion and photo limits

## Issues Encountered
- None — pattern replication from Phase 2 PhotoService was straightforward

## Next Phase Readiness
- Complete cat identity system (profile + photos) ready for Phase 4 discovery feed
- Cat photos with thumbnails available for swipe card rendering
- S3 key patterns (cats/{catId}/) separate from user photos for clean storage organization

---
*Phase: 03-cat-profiles*
*Completed: 2026-06-12*
