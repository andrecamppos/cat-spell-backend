---
status: partial
phase: 02-user-profiles-photos
source: 02-01-SUMMARY.md, 02-02-SUMMARY.md
started: 2025-06-11T20:53:00Z
updated: 2025-06-11T22:05:00Z
---

## Current Test

[testing paused — 11 items outstanding]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running server/containers. Run `./gradlew test` from clean state. All 54 tests pass — no compilation errors, no migration failures, Testcontainers boots PostgreSQL+PostGIS and MinIO.
result: pass

### 2. Create Profile
expected: POST /api/profile with displayName, bio, dateOfBirth, gender, genderPreference returns 201 with the created profile including all fields and an auto-generated ID.
result: [pending]

### 3. Get Own Profile
expected: GET /api/profile returns the authenticated user's profile with all stored fields (displayName, bio, dateOfBirth, gender, genderPreference, location coordinates).
result: [pending]

### 4. Update Profile (Partial)
expected: PUT /api/profile with only some fields (e.g., just bio) updates only those fields. Other fields remain unchanged. Returns 200 with the full updated profile.
result: [pending]

### 5. GPS Location Storage
expected: Create or update profile with latitude/longitude coordinates. Profile response includes the stored GPS location. PostGIS POINT is used internally.
result: [pending]

### 6. Request Photo Upload URL
expected: POST /api/profile/photos/upload-url returns a presigned S3 URL that the client can use to upload directly to S3/MinIO. Includes the photo key/ID for confirmation.
result: [pending]

### 7. Confirm Photo & Thumbnail
expected: After uploading to S3, POST confirm endpoint transitions photo status from PENDING to ACTIVE and generates a 200x200 JPEG thumbnail in S3.
result: [pending]

### 8. List Photos
expected: GET photos endpoint returns the user's photo list with URLs for both original and thumbnail images, ordered by display order.
result: [pending]

### 9. Delete Photo
expected: DELETE a photo removes both original and thumbnail from S3. Remaining photos are automatically reordered to fill the gap.
result: [pending]

### 10. Reorder Photos
expected: PUT reorder endpoint accepts new display order for photos. Subsequent list reflects the updated order.
result: [pending]

### 11. Profile Completeness
expected: GET /api/profile/completeness returns a list of missing fields and a `complete` boolean. A profile with displayName, bio, gender, genderPreference, location, and at least 1 ACTIVE photo returns `complete: true`.
result: [pending]

### 12. Max 6 Photos Enforced
expected: Requesting a 7th upload URL when the user already has 6 photos (ACTIVE + PENDING) returns an error response (e.g., 400/409) with a meaningful message.
result: [pending]

## Summary

total: 12
passed: 1
issues: 0
pending: 11
skipped: 0
blocked: 0

## Gaps

[none yet]
