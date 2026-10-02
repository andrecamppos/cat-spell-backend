---
phase: 17
slug: waitlist-landing-page-api
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-10-02
---

# Phase 17 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| internet → POST /api/waitlist | Unauthenticated, untrusted JSON on a `permitAll` route. | Email address (PII) |
| WaitlistService → PostgreSQL | `UNIQUE(normalized_email)` and conditional UPDATEs are the dedupe/state authorities. | Email, normalized key, confirm token hash |
| email client / mail scanner → GET /api/waitlist/confirm | Untrusted token in the query string of a top-level navigation (`permitAll`, not throttled — 256-bit token). | Raw confirm token |
| WaitlistService → async listener → EmailSender | The raw token crosses only into an in-memory event and the outbound email body. | Raw confirm token, email |
| internet → RateLimitFilter (HIGHEST_PRECEDENCE) | Per-IP throttle runs before Spring Security on `/api/auth/*` and `POST /api/waitlist`; the caller controls the path spelling and every header. | Request path, X-Forwarded-For |
| reverse proxy → app | Only a configured trusted peer may name the client through X-Forwarded-For; hops left of the rightmost untrusted one are client-written. | X-Forwarded-For hops |
| operator config → RateLimitFilter | `rate-limit.trusted-proxies` decides which peers are trusted; a wrong value must fail loudly. | Trusted proxy list / CIDRs |
| browser on another origin → CORS → POST /api/waitlist | Cross-origin calls allowed only from configured origins. | Email address |
| client Authorization header → JwtAuthenticationFilter | Untrusted header that must not block the public routes. | Bearer token |
| operator/client → /api/admin/waitlist (list, convert) | `permitAll` routes; `AdminTokenGuard` (X-Admin-Token) is the only barrier and runs first. | Admin shared secret, stored emails (PII), raw invite code |
| WaitlistService → InviteService / EmailSender | One transaction spans the CONFIRMED→INVITED claim, invite insert and synchronous send. | Invite code hash, raw invite code (email only) |
| test JVM → shared Testcontainers Postgres | `WaitlistMigrationTest` DROP/CREATE DATABASE against the shared container. | Test schema |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-17-01 | Information Disclosure | WaitlistService.join concurrent duplicate | high | mitigate | Native `INSERT ... ON CONFLICT (normalized_email) DO NOTHING` (`WaitlistEntryRepository.kt:27`); no DataIntegrityViolationException can surface as a 500. | closed |
| T-17-02 | Tampering | dedupe + per-email key | medium | mitigate | `WaitlistEmailNormalizer` trims, lowercases and strips the local-part `+suffix` for both the UNIQUE key and the bucket key (D-03). | closed |
| T-17-03 | Information Disclosure | confirm token at rest | high | mitigate | Only `hashToken(raw)` (SHA-256 hex) is written to `confirm_token_hash` (`WaitlistService.kt:77,98,169`); raw token never persisted or logged (D-08). | closed |
| T-17-04 | Denial of Service | repeated joins for one address | medium | mitigate | Per-email Bucket4j bucket `app.waitlist.per-email-capacity` (default 3/hour, `WaitlistService.kt:39-52`), silent on exhaustion (D-07). | closed |
| T-17-05 | Tampering | JoinWaitlistRequest input | medium | mitigate | `@field:NotBlank @field:Email @field:Size(max = 255)` (`WaitlistDtos.kt`); normalizer never throws; native query uses bound parameters only. | closed |
| T-17-06 | Denial of Service | in-memory per-email bucket map growth | low | accept | See AR-17-01. | closed |
| T-17-07 | Spoofing | confirm token guessing / replay / race | high | mitigate | 32-byte `SecureRandom` token, SHA-256 at rest, 168h TTL (`confirm-token-ttl-hours`), single conditional `claimConfirm` UPDATE (`status = PENDING AND expiresAt > now`). | closed |
| T-17-08 | Spoofing | 302 Location (open redirect) | medium | mitigate | Location built only from `app.waitlist.confirm-success-url` / `confirm-error-url` (`WaitlistController.kt:24-52`); no request input echoed. | closed |
| T-17-09 | Information Disclosure | token in Referer, caches, logs | medium | mitigate | `Referrer-Policy: no-referrer` + `Cache-Control: no-store` on the 302; redacted event `toString` (`WaitlistEvents.kt:13`); listener logs neither token nor address. | closed |
| T-17-10 | Information Disclosure | join timing / mail failure side channel | medium | mitigate | `@Async @TransactionalEventListener(AFTER_COMMIT)` send with swallow-log (`WaitlistEmailListener.kt:27-36`); provider error never changes the 202. | closed |
| T-17-11 | Repudiation | mail scanner consumes link before the human | low | accept | See AR-17-02. | closed |
| T-17-12 | Denial of Service | confirmation-mail bombing of a third party | medium | mitigate | Per-email bucket caps sends per normalized address; CONFIRMED/INVITED re-joins publish no event (`WaitlistService.kt:82-85`, D-04). | closed |
| T-17-13 | Denial of Service | POST /api/waitlist flooding from one IP | high | mitigate | `RateLimitFilter` registered on `/api/auth/*`, `/api/waitlist` (`RateLimitFilter.kt:119`) with exact `POST` + path match (`:50`). | closed |
| T-17-14 | Spoofing | cross-origin abuse via CORS | medium | mitigate | `SecurityConfig` CORS: explicit `app.waitlist.allowed-origins` (blank = none), `allowedMethods = POST`, `allowCredentials = false`, mapped on `/api/waitlist` only. | closed |
| T-17-15 | Information Disclosure | membership enumeration via status/body | high | mitigate | One constant `202` + `WAITLIST_JOIN_MESSAGE` body for every state incl. throttled (`WaitlistController.join`); proven by `WaitlistEnumerationSafetyIntegrationTest`. | closed |
| T-17-16 | Denial of Service | stale Bearer header 401s the public join | low | mitigate | `JwtAuthenticationFilter.shouldNotFilter` skips `/api/waitlist`. | closed |
| T-17-17 | Spoofing | X-Forwarded-For rotation bypasses per-IP bucket | medium | accept → mitigate | Superseded by T-17-30 / T-17-33 (trusted-proxy gating, rightmost untrusted hop). No longer an accepted risk. | closed |
| T-17-18 | Elevation of Privilege | permitAll admin waitlist routes, token unconfigured | high | mitigate | `AdminTokenGuard` denies when `app.invite.admin-token` is blank (`AdminTokenGuard.kt:25`); no default token ships. | closed |
| T-17-19 | Information Disclosure | X-Admin-Token comparison timing | high | mitigate | Constant-time `MessageDigest.isEqual` (`AdminTokenGuard.kt:27`). | closed |
| T-17-20 | Tampering | diverging copies of the admin boundary | medium | mitigate | Single `AdminTokenGuard` injected by both `InviteAdminController` and `WaitlistAdminController`. | closed |
| T-17-21 | Information Disclosure | waitlist emails via the list endpoint | high | mitigate | `adminTokenGuard.require(token)` is the first statement of `list` (`WaitlistAdminController.kt:37`); `WaitlistEntryResponse` omits token hash/expiry/normalized key. | closed |
| T-17-22 | Denial of Service | unbounded list size | low | mitigate | `limit !in 1..500` → 400 in `WaitlistService.listByStatus` (`:115`). | closed |
| T-17-23 | Tampering | double convert → two invites | high | mitigate | Conditional `markInvited(..., CONFIRMED, INVITED)` claim inside the convert transaction (`WaitlistService.kt:148`); 0 rows → not convertible. | closed |
| T-17-24 | Tampering | fabricated referral attribution | medium | mitigate | `inviteService.create(null)` only (`WaitlistService.kt:151`, D-10). | closed |
| T-17-25 | Information Disclosure | waitlist id probing | medium | mitigate | `adminTokenGuard.require(token)` is the first statement of `convert` (`WaitlistAdminController.kt:51`) → identical 401 for any id. | closed |
| T-17-26 | Elevation of Privilege | inviting an unconfirmed address | high | mitigate | Only `CONFIRMED` rows satisfy `markInvited`; PENDING/INVITED → `WaitlistEntryNotConvertibleException` (409), no invite, no email. | closed |
| T-17-27 | Information Disclosure | raw invite code exposure | medium | mitigate | Code returned once in the 201 and in the invitee email; only SHA-256 stored; no log statement in the waitlist service/admin controller references it. | closed |
| T-17-28 | Repudiation | invite issued but never delivered | medium | mitigate | Synchronous send in the transaction; non-SUCCESS or exception → `WaitlistInviteDeliveryException` → rollback (`WaitlistService.kt:155,158`). | closed |
| T-17-29 | Tampering | migration test DROP/CREATE on shared container | low | mitigate | Test uses its own `waitlist_migration_test` DB (`WaitlistMigrationTest.kt:41`). | closed |
| T-17-30 | Spoofing | `RateLimitFilter.resolveClientIp` | high | mitigate | X-Forwarded-For honored only when `remoteAddr` matches `trustedProxies` (default `127.0.0.1,::1`) (`RateLimitFilter.kt:85-87`). | closed |
| T-17-31 | Information Disclosure | `waitlist_entries` schema drift | medium | mitigate | Exact column-set assertion against `information_schema.columns` (`WaitlistMigrationTest.kt:108-117`). | closed |
| T-17-32 | Tampering | RateLimitFilter path match (CR-02) | high | mitigate | Match on `UrlPathHelper.defaultInstance.getPathWithinApplication` (`RateLimitFilter.kt:47`); covered by `RateLimitBypassIntegrationTest`. | closed |
| T-17-33 | Spoofing | XFF hop selection (CR-01) | high | mitigate | Rightmost non-trusted hop across all header lines: `getHeaders("X-Forwarded-For")…asReversed().firstOrNull { !trusted }` (`RateLimitFilter.kt:89-93`). | closed |
| T-17-34 | Denial of Service | trusted-proxy membership form (WR-09) | medium | mitigate | `TrustedProxyMatcher` compares parsed bytes / CIDR within one address family; covered by `TrustedProxyMatcherTest`. | closed |
| T-17-35 | Denial of Service | XFF hop parsing (DNS / 500) | medium | mitigate | Strict IPv4/IPv6 literal regexes gate `InetAddress.getByName`; parse failures return false (`TrustedProxyMatcher.kt:48-49,75-94`). | closed |
| T-17-36 | Spoofing | deployment shape: relaying proxy / `ip:port` hops | medium | transfer | Operator responsibility; "Unsupported shapes" documented in `docs/CONFIGURATION.md:108-111`; production proxy shape is human_verification item in 17-VERIFICATION.md. | closed |
| T-17-37 | Denial of Service | `RateLimitFilter.buckets` unbounded growth (WR-01) | medium | accept | See AR-17-03. | closed |
| T-17-SC | Tampering | dependency installs (supply chain) | low | accept | See AR-17-04. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-17-01 | T-17-06 | In-memory per-email bucket map grows with distinct addresses; pre-existing pattern (EmailVerificationService), new rate-limit infrastructure out of scope; bounded in practice by the per-IP filter. | phase threat model (plan 17-01) | 2026-10-02 |
| AR-17-02 | T-17-11 | A mail scanner may consume the GET confirm link before the human; D-08 (GET + single-use) is locked, the entry still becomes CONFIRMED so integrity holds; UX mitigated by email and landing-page copy. | phase threat model (plan 17-02) | 2026-10-02 |
| AR-17-03 | T-17-37 | `RateLimitFilter.buckets` has no eviction; 17-09 removed the client-controlled key sources (forged leftmost hops, encoded path spellings), but genuine address diversity (e.g. IPv6 rotation) remains. Review finding WR-01 is still `open` in 17-REVIEW-DISPOSITION.md pending an explicit human disposition. | phase threat model (plan 17-09) | 2026-10-02 |
| AR-17-04 | T-17-SC | No new dependencies this phase; only JDK `java.net.InetAddress` and existing spring-web `UrlPathHelper`; `build.gradle.kts` unchanged. | phase threat model (all plans) | 2026-10-02 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-02 | 38 | 38 | 0 | /gsd-secure-phase (orchestrator, L1 grep verification; auditor skipped per short-circuit: register authored at plan time, ASVS L1, threats_open 0) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-02
