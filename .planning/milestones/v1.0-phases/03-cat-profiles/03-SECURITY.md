---
phase: 3
slug: cat-profiles
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-12
---

# Phase 3 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Retroactive-STRIDE mode — register built from implementation (no plan-time threat model).

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Client → CatProfileController | HTTP REST, JWT Bearer auth | Cat profile CRUD payloads (name, age, breed, bio) |
| Client → CatPhotoController | HTTP REST, JWT Bearer auth | Photo upload requests, presigned URL responses |
| Controller → Service | userId extracted from JWT principal | UUID userId, request DTOs |
| Service → PostgreSQL | JPA/Hibernate parameterized queries | Cat profile and photo entities |
| Service → S3/MinIO | StorageService (presigned URLs, object ops) | Image files, thumbnails, S3 keys |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-03-S1 | Spoofing | CatProfileController, CatPhotoController | mitigate | `extractUserId()` from JWT `SecurityContextHolder`; all endpoints require auth (`anyRequest().authenticated()`); JWT validated by `JwtAuthenticationFilter` | closed |
| T-03-T1 | Tampering | CatProfileService | mitigate | `findByIdAndUserId(catId, userId)` for get/update/delete; returns 404 for unauthorized access (no resource existence leakage) | closed |
| T-03-T2 | Tampering | CatPhotoService | mitigate | `verifyCatOwnership(userId, catId)` on all photo ops; ownership chain userId→catId→photoId enforced | closed |
| T-03-T3 | Tampering | CatProfile entity, CatProfileDtos | mitigate | JPA parameterized queries (Spring Data naming convention, no raw SQL); Jakarta `@Size` constraints; VARCHAR lengths at DB level | closed |
| T-03-R1 | Repudiation | CatProfileService, CatPhotoService | accept | `createdAt`/`updatedAt` timestamps on entities; Spring logging; full audit logging out of scope for MVP | closed |
| T-03-I1 | Info Disclosure | CatPhotoService, CatPhotoDtos | mitigate | S3 keys use `cats/{catId}/{randomUUID}.ext` (unpredictable); access requires presigned URLs | closed |
| T-03-I2 | Info Disclosure | GlobalExceptionHandler | mitigate | Structured `ProblemDetail` responses; generic 500 handler logs server-side, returns generic message to client | closed |
| T-03-I3 | Info Disclosure | CatProfileService | mitigate | All reads scoped to userId from JWT; `findByIdAndUserId` for single cat, `findByUserId` for listing | closed |
| T-03-D1 | Denial of Service | CatProfileService | mitigate | `MAX_CATS_PER_USER = 5` enforced via `countByUserId >= 5` check before create | closed |
| T-03-D2 | Denial of Service | CatPhotoService | mitigate | `MAX_PHOTOS = 10` enforced via `countByCatProfileIdAndStatus` (PENDING + ACTIVE) | closed |
| T-03-D3 | Denial of Service | CatPhotoService | mitigate | `MAX_FILE_SIZE_BYTES = 10_485_760L` (10MB) in presigned URL; content type restricted to JPEG/PNG | closed |
| T-03-D4 | Denial of Service | CatPhotoService.generateThumbnail | accept | Thumbnailator with fixed 200×200 output; 10MB file limit + content type restriction provide reasonable protection; residual decompression bomb risk accepted for MVP | closed |
| T-03-E1 | Elevation of Privilege | CatProfileController, CatPhotoController | mitigate | UUID type-safe identifiers (not sequential); ownership validation at service layer; `@PathVariable UUID` rejects non-UUID values | closed |
| T-03-E2 | Elevation of Privilege | SecurityConfig | mitigate | CSRF disabled but irrelevant — stateless JWT Bearer auth (no cookies, no session); API-only backend | closed |
| T-03-E3 | Elevation of Privilege | CatProfileDtos, CatPhotoDtos | mitigate | Jakarta validation (`@NotBlank`, `@NotNull`, `@Min`, `@Size`); `@Valid` on controller params; 400 on validation failures | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-03-01 | T-03-R1 | Full audit logging is out of scope for MVP; timestamp fields on entities provide basic auditability | gsd-security-auditor | 2026-06-12 |
| AR-03-02 | T-03-D4 | Decompression bomb risk mitigated by 10MB upload limit and content type restriction; Thumbnailator streaming resize limits memory impact; residual risk accepted for MVP maturity | gsd-security-auditor | 2026-06-12 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-12 | 15 | 15 | 0 | gsd-security-auditor (retroactive-STRIDE) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-12
