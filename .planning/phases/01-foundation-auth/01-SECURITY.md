---
phase: 01
slug: foundation-auth
status: verified
threats_open: 0
asvs_level: 1
created: 2025-06-10
---

# Phase 01 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Client → API | Untrusted HTTP requests from mobile app cross into backend | Credentials (email, password), JWT tokens |
| API → Database | Authenticated service queries cross into data layer | User records, refresh tokens |
| JWT Token | Bearer token travels over network, stored on client device | userId, email claims |
| Client → /api/auth/refresh | Refresh token transmitted from client storage to server | Opaque UUID refresh token |
| Refresh token storage (client) | Token persisted on device, vulnerable to device compromise | Refresh token string |
| Error response → Client | Error details cross from server to untrusted client | Validation messages, status codes |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-01-01 | Spoofing | /api/auth/login | mitigate | BCrypt password hashing; vague "Invalid credentials" for both wrong password and non-existent email (D-15) | closed |
| T-01-02 | Tampering | JWT tokens | mitigate | HMAC-SHA512 signing with secret key; signature validation on every request via JwtAuthenticationFilter | closed |
| T-01-03 | Repudiation | Auth operations | accept | No audit logging for v1; accept risk at current scale | closed |
| T-01-04 | Information Disclosure | Error responses | mitigate | Safe production defaults (D-15): generic 500s with server-side logging, vague auth errors, field-level validation only | closed |
| T-01-05 | Denial of Service | /api/auth/register, /api/auth/login | accept | No rate limiting in Phase 1; deferred to Phase 6 hardening | closed |
| T-01-06 | Elevation of Privilege | JWT claims | mitigate | JWT contains only userId and email claims; no roles; token validated on every request | closed |
| T-01-07 | Information Disclosure | JWT secret | mitigate | Secret loaded from environment variable JWT_SECRET; .env in .gitignore; never committed to source | closed |
| T-01-SC | Tampering | Gradle dependencies | mitigate | All dependencies from Maven Central only; versions pinned in build.gradle.kts via Spring dependency management | closed |
| T-01-08 | Spoofing | /api/auth/refresh | mitigate | Token rotation: each refresh invalidates old token; theft detection revokes all user tokens on reuse of revoked token (D-06) | closed |
| T-01-09 | Tampering | refresh_tokens table | mitigate | Token is opaque UUID (not JWT), server-side validation; FK constraint with ON DELETE CASCADE ensures cleanup | closed |
| T-01-10 | Information Disclosure | Refresh token in response | accept | Token transmitted over HTTPS (enforced at deployment level); stored securely on client (mobile app responsibility) | closed |
| T-01-11 | Denial of Service | Token family revocation | accept | Theft detection revokes all user tokens; legitimate user must re-login; acceptable tradeoff for security | closed |
| T-01-12 | Information Disclosure | Error responses (login) | mitigate | Vague "Invalid credentials" for both wrong password and non-existent email — prevents user enumeration (D-15) | closed |
| T-01-13 | Information Disclosure | Error responses (500) | mitigate | Generic "An unexpected error occurred" with no stack trace in response; full stack logged server-side only (D-15) | closed |
| T-01-14 | Information Disclosure | Validation errors | accept | Field-level validation details intentionally exposed (D-14) — email format and password length are not sensitive | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-01-03 | No audit logging for v1; acceptable at current scale; revisit at growth | Plan author | 2025-06-09 |
| AR-02 | T-01-05 | No rate limiting in Phase 1; deferred to Phase 6 hardening | Plan author | 2025-06-09 |
| AR-03 | T-01-10 | HTTPS enforced at deployment level; client-side secure storage is mobile app responsibility | Plan author | 2025-06-09 |
| AR-04 | T-01-11 | Family revocation forces re-login; acceptable tradeoff for theft detection security | Plan author | 2025-06-09 |
| AR-05 | T-01-14 | Validation field names and constraint messages are not sensitive data | Plan author | 2025-06-09 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2025-06-10 | 15 | 15 | 0 | gsd-secure-phase |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2025-06-10
