---
phase: 02
slug: user-profiles-photos
status: verified
threats_open: 0
asvs_level: 1
created: 2025-06-11
---

# Phase 02 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Client → API | Untrusted profile data, GPS coordinates, and file metadata from mobile app | PII (name, DOB, gender, location), file metadata (contentType, fileName) |
| Client → S3 | Direct file upload via presigned URL | Binary image data (JPEG/PNG, up to unbounded size — see T-02-09 accepted risk) |
| API → Database | Service-layer validated data persisted to PostgreSQL | Profile fields, photo metadata, PostGIS geometry |
| API → S3 | Server downloads uploaded files for thumbnail generation | Untrusted binary image content |
| S3 → API | Untrusted file content from S3 processed for thumbnails | Raw image bytes (processed by Thumbnailator/ImageIO) |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-02-01 | Spoofing | /api/profile | mitigate | JWT auth required (`anyRequest().authenticated()`); userId extracted from `SecurityContextHolder` token, never request body | closed |
| T-02-02 | Tampering | Profile data | mitigate | Jakarta validation: `@Size(max=100)` displayName, `@Size(max=1000)` bio, `@Min(18)/@Max(99)` age range, `@DecimalMin/@DecimalMax` coords; `validateAge()` + `validateAgeRange()` in ProfileService | closed |
| T-02-03 | Information Disclosure | GPS coordinates | mitigate | Raw coords returned only to profile owner via JWT-scoped `getProfile()`; no endpoint to view other users' coordinates in Phase 2 | closed |
| T-02-04 | Information Disclosure | User enumeration | accept | Profile endpoints only access own data via JWT userId; no lookup endpoint exists | closed |
| T-02-05 | Denial of Service | Profile creation | accept | No rate limiting in Phase 2; deferred to Phase 6. One-profile-per-user constraint limits abuse | closed |
| T-02-06 | Elevation of Privilege | Cross-user access | mitigate | userId always from JWT principal; `findByUserId()` enforces ownership at service layer | closed |
| T-02-07 | Spoofing | Photo ownership | mitigate | `findByIdAndUserId(photoId, userId)` on confirm/delete; reorder loads photos by JWT userId | closed |
| T-02-08 | Tampering | File upload type | mitigate | `ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png")` server-side check; extension derived from validated contentType, not user input | closed |
| T-02-09 | Tampering | File size | accept | Presigned PUT URL `maxSizeBytes` param unused — AWS SDK presigned PUT does not support content-length-range natively. Accepted: 6-photo limit + JWT auth bounds total exposure. Enhancement logged for future (POST policy or server-side HEAD check before download) | closed |
| T-02-10 | Tampering | Malicious image | accept | Thumbnailator processes via ImageIO; OOM bounded by photo limit (max 6). No execution of embedded content. Residual risk accepted for v1 | closed |
| T-02-11 | Information Disclosure | S3 keys | mitigate | UUID-based S3 paths (`photos/{userId}/{UUID}.ext`); presigned URLs expire in 15 min; bucket is private (no public access) | closed |
| T-02-12 | Denial of Service | Photo upload spam | mitigate | `MAX_PHOTOS = 6` (ACTIVE + PENDING counted); JWT required; 15-min presigned URL expiry | closed |
| T-02-13 | Denial of Service | Thumbnail generation | mitigate | Synchronous single-request processing; max 6 photos per user constrains total generation surface | closed |
| T-02-14 | Elevation of Privilege | Cross-user photo access | mitigate | `findByIdAndUserId()` on all mutating ops (confirm, delete); list/reorder filter by JWT userId; non-owned → 404 | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-02-04 | Profile endpoints only access own data via JWT; no user enumeration vector in Phase 2 | user | 2025-06-11 |
| AR-02-02 | T-02-05 | Rate limiting deferred to Phase 6; one-profile-per-user constraint limits abuse surface | user | 2025-06-11 |
| AR-02-03 | T-02-09 | AWS SDK presigned PUT URLs do not support content-length-range conditions; 6-photo limit + JWT auth bounds total exposure; enhancement candidate: add server-side HEAD check on S3 object size before downloading in confirmUpload(), or switch to POST-based upload policy | user | 2025-06-11 |
| AR-02-04 | T-02-10 | Thumbnailator/ImageIO handles common formats safely; 10MB intent (unenforced, see AR-02-03) + 6-photo limit bounds OOM risk; no execution of embedded content | user | 2025-06-11 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2025-06-11 | 14 | 14 | 0 | gsd-security-auditor (inline) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2025-06-11
