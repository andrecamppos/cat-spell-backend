---
phase: 12
slug: account-credentials
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-08-19
---

# Phase 12 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| app → PostgreSQL | Migration DDL and JPA persistence cross into durable storage | Pending email-change requests (new_email, token hash) |
| app → email transport (EmailSender seam) | Rendered message content leaves the app toward a recipient inbox | Confirm deep-link carrying the raw single-use token |
| authenticated client → service/API | userId comes from the JWT-validated principal; currentPassword / newEmail are untrusted input | Credentials, target email address |
| public token → confirm path | confirm-email-change is reachable unauthenticated; the single-use token is the sole proof of ownership | Raw confirmation token |
| test harness → live app + Postgres (Testcontainers) | Tests exercise the real filter chain, controllers, services, and Flyway-migrated schema | Full end-to-end credential-change behavior |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-12-01-01 | Tampering | EmailChangeRequestRepository.markUsed | high | mitigate | Atomic conditional `UPDATE ... WHERE t.id = :id AND t.usedAt IS NULL` — verified in `EmailChangeRequestRepository.kt`; double-confirm applies at most once (no read-check-write race). | closed |
| T-12-01-02 | Information Disclosure | email_change_requests.token_hash | medium | mitigate | Only the SHA-256 hex hash is stored (`hashToken`, never the raw token); column `token_hash` is UNIQUE. Verified in `EmailChangeService.kt` + `V18` migration. | closed |
| T-12-01-03 | Denial of Service | orphaned rows on user delete | low | mitigate | FK `user_id ... REFERENCES users(id) ON DELETE CASCADE` in `V18__create_email_change_requests_table.sql`. | closed |
| T-12-02-01 | Information Disclosure | EmailChangeEmailRenderer recipient | high | mitigate | `render(recipientEmail, rawToken)` sets `to = recipientEmail`; no `user.email` reference. Service calls `render(newEmail, ...)`. Verified in `EmailChangeEmailRenderer.kt` + `EmailChangeService.kt`. | closed |
| T-12-02-02 | Spoofing | 403 vs 401 semantics on authenticated request | medium | mitigate | Distinct 403 `INVALID_CURRENT_PASSWORD` handler avoids a 401 that could confuse token-refresh. Verified in `GlobalExceptionHandler.kt`. | closed |
| T-12-02-03 | Information Disclosure | ProblemDetail body | low | accept | Detail is a generic "Current password is incorrect"; no account data echoed. See Accepted Risks Log. | closed |
| T-12-03-01 | Elevation of Privilege | changePassword / requestChange | high | mitigate | `passwordEncoder.matches` re-verifies current password BEFORE any mutation; mismatch → 403, no state change. Verified in `AuthService.changePassword` + `EmailChangeService.requestChange`. | closed |
| T-12-03-02 | Tampering | confirmEmailChange token claim | high | mitigate | Atomic `markUsed` claim; 0 rows → `InvalidTokenException`, email swaps at most once under concurrent confirm. Verified in `AuthService.confirmEmailChange`. | closed |
| T-12-03-03 | Spoofing / account takeover | email swap on confirm | high | mitigate | Identity change calls `revokeAllUserTokens` and mints no tokens, forcing fresh login. Verified in `AuthService.confirmEmailChange` + `changePassword`. | closed |
| T-12-03-04 | Denial of Service | inbox bombing a victim address | medium | mitigate | Per-target-new_email Bucket4j guard (`emailBucket`, keyed on normalized email) → 429 on exhaustion. Verified in `EmailChangeService.kt`. | closed |
| T-12-03-05 | Information Disclosure | 409 on taken email | low | accept | D-06 accepts the 409 on an authenticated, rate-limited caller. See Accepted Risks Log. | closed |
| T-12-04-01 | Elevation of Privilege | endpoint auth posture | high | mitigate | Only `confirm-email-change` is whitelisted (SecurityConfig permitAll + JwtAuthenticationFilter.shouldNotFilter); change-password / change-email keep JWT auth (no `@SecurityRequirements`). Verified across the three security files + `AuthController`. | closed |
| T-12-04-02 | Spoofing | extractUserId principal | high | mitigate | `extractUserId()` reads the JWT-validated `SecurityContextHolder` principal, never the request body. Verified in `AuthController.kt`. | closed |
| T-12-04-03 | Denial of Service | change-email request flooding | medium | mitigate | `change-email` added to `RateLimitFilter.AUTH_PATHS` for per-IP throttling (plus per-target-email guard in 12-03). Verified in `RateLimitFilter.kt`. | closed |
| T-12-04-04 | Tampering | @Valid DTO bounds | low | mitigate | `@field:Size(min = 8)` and `@field:Email` reject malformed input at the controller boundary. Verified in `AuthDtos.kt`. | closed |
| T-12-05-01 | Repudiation | untested revoke-all / no-token invariants | high | mitigate | Integration tests assert refresh-token rejection after change-password and confirm-email-change, and that responses carry no tokens. Verified in `AccountCredentialsIntegrationTest.kt`. | closed |
| T-12-05-02 | Tampering | double-confirm race | medium | mitigate | Reused-token test asserts the second confirm is rejected and the email is not swapped twice. Verified in `AccountCredentialsIntegrationTest.kt`. | closed |
| T-12-05-03 | Information Disclosure | confirm email target | medium | mitigate | Test asserts the confirm message `to` is the NEW address, not the current account email. Verified in `AccountCredentialsIntegrationTest.kt`. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-12-01 | T-12-02-03 | ProblemDetail carries only a generic "Current password is incorrect"; no account data echoed. Broader account-existence enumeration is a project-wide canon concern, not minted in this phase. | Plan 12-02 (author) | 2026-08-19 |
| AR-12-02 | T-12-03-05 | D-06 explicitly accepts a real 409 on a taken new_email for the authenticated, rate-limited change-email flow (unlike the enumeration-safe recovery flows). Broader enumeration is canon. | Plan 12-03 (author) | 2026-08-19 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-19 | 18 | 18 | 0 | gsd-secure-phase (L1 grep-depth, ASVS 1) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-19
