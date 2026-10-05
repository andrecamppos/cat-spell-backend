---
phase: "18"
slug: "address-tech-debt-post-block-redelivery-waitlist-review-warn"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-05"
---

# Phase 18 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| server → recipient WebSocket session | Server decides which previews a reconnecting user receives (18-01) | Message previews (user content) |
| internet → per-key throttles | Callers choose the email, reporter or IP key (18-02) | Attacker-controlled bucket keys |
| build → Maven Central | A new library artifact enters the build (18-02) | Caffeine 3.2.3 (BOM-managed) |
| client / proxy → X-Forwarded-For | Hops left of the trusted chain are client-written text (18-03) | Client IP claims |
| server → user inbox | Email copy and links guide double opt-in (18-04) | Confirm tokens, invite codes |
| operator config → redirect Location | Configured URLs become the 302 Location (18-04) | Redirect targets |
| operator config → operator endpoints | One shared secret unlocks waitlist PII and invite minting (18-05) | Admin token |
| internet → POST /api/waitlist | Anyone can submit any address, including `+suffix` variants (18-06, 18-09) | Email addresses |
| internet → throttled endpoints, /api/admin/** | Path spelling, request volume and secret guesses are attacker-controlled (18-07, 18-10) | Request paths, admin token guesses |
| API → email provider | External I/O inside a transaction holding a row lock (18-08) | Invite codes, recipient addresses |
| server → logs | Operator logs must not collect subscriber PII (18-08) | Log lines |
| browser (landing origin) → POST /api/waitlist | CORS headers decide what the page may read (18-09) | 429 + rate-limit headers |
| browser credentials → operator routes | A stale Bearer may ride along on operator requests (18-10) | JWT |
| operator → deployment config | Operators set trust, origin and secret values (18-11) | Config / secrets |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-18-01 | Information disclosure | ChatService.deliverUnreadMessages | high | mitigate | `ConversationRepository.findHiddenConversationIdsForUser` (one set-based query, ended match or block either way); `ChatService.kt:283` partitions out hidden conversations before `/queue/notifications`; `BlockEnforcementIntegrationTest` | closed |
| T-18-02 | Repudiation | messages.delivered flag | low | accept | See Accepted Risks AR-18-01 | closed |
| T-18-03 | Denial of service | reconnect path | low | accept | See Accepted Risks AR-18-02 | closed |
| T-18-04 | Denial of service | per-key bucket stores (WR-10) | high | mitigate | `RateLimitBuckets.kt:41` `.maximumSize(maxKeys)`; `RateLimitBucketsTest` "the store never holds more than maxKeys entries" (1,000 keys, maxKeys 10) | closed |
| T-18-05 | Tampering | eviction under key churn (W-TinyLFU) | medium | accept | See Accepted Risks AR-18-03 | closed |
| T-18-06 | Tampering | expiry policy | high | mitigate | `RateLimitBuckets.kt:42` `.expireAfterAccess(window)`; zero `expireAfterWrite` in `src/main`; test "a key accessed within the window is kept" | closed |
| T-18-07 | Spoofing | RateLimitFilter.resolveClientIp (WR-02) | high | mitigate | `RateLimitFilter.kt:172` `TrustedProxyMatcher.canonicalize(hop)` before trust check; rotating-port IPv4/IPv6 tests in `RateLimitBypassIntegrationTest` | closed |
| T-18-08 | Denial of service | unparseable hop as bucket key | high | mitigate | `RateLimitFilter.kt:172` `?: return remoteAddr`; test "a malformed rightmost hop … falls back to the peer bucket" | closed |
| T-18-09 | Tampering | CIDR non-octet mask (WR-01) | medium | mitigate | `TrustedProxyMatcherTest.kt:104-112` edge tests for 172.16.0.0/12 and 2001:db8:ab00::/41 | closed |
| T-18-10 | Repudiation | silent trusted-proxy misconfiguration (IN-05) | low | mitigate | `RateLimitFilter.kt:61,158` AtomicBoolean-guarded single WARN; `RateLimitFilterWarnTest` (3 tests) | closed |
| T-18-11 | Spoofing | confirm email copy (WR-03) | medium | mitigate | "still confirmed" absent from `src/main`; `WaitlistConfirmEmailRenderer.newestLinkNotice()` | closed |
| T-18-12 | Tampering | link construction (IN-12) | low | mitigate | `UriComponentsBuilder` in both email renderers; "already has a query gets … appended with an ampersand" tests | closed |
| T-18-13 | Denial of service | redirect URL parsing (IN-07) | low | mitigate | `WaitlistController.kt:32-33` `URI.create` at construction; `WaitlistControllerUrlTest` malformed-URL tests | closed |
| T-18-14 | Spoofing | AdminTokenGuard weak secret (WR-08) | high | mitigate | `AdminTokenGuard` `init { check(isBlank() \|\| length >= 32) }`; `AdminTokenGuardStartupTest` 31/32-char cases | closed |
| T-18-15 | Information disclosure | startup error message | medium | mitigate | Fixed, uninterpolated `check` message; test asserts `root.message` `doesNotContain(shortToken)` | closed |
| T-18-16 | Denial of service | deploy availability | low | accept | See Accepted Risks AR-18-04 | closed |
| T-18-17 | Denial of service | email bombing via re-joins (WR-11) | high | mitigate | `rotatePendingToken` cooldown predicate `updatedAt <= :resendCutoff` (15 min default); per-email bucket 3/24 h; "re-join inside the cooldown sends no second email" | closed |
| T-18-18 | Spoofing | waitlist-spot hijack via `+suffix` (WR-04) | high | mitigate | `rotatePendingToken` UPDATE sets only hash/expiry/updatedAt (never email); test "a variant re-join outside the cooldown mails the first stored address" | closed |
| T-18-19 | Tampering | concurrent re-joins minting several tokens (WR-03) | medium | mitigate | Cooldown evaluated in the single conditional UPDATE under the row lock; `WaitlistPerEmailConcurrencyIntegrationTest` (20 threads → one email) | closed |
| T-18-20 | Denial of service | attacker spends victim's per-email tokens | low | accept | See Accepted Risks AR-18-05 | closed |
| T-18-21 | Denial of service | shared join/auth bucket (WR-07a) | medium | mitigate | `RateLimitFilter` `BucketFamily { AUTH, WAITLIST_JOIN, ADMIN }` with separate stores; test "the waitlist join and login draw from separate per-IP buckets" | closed |
| T-18-22 | Spoofing | admin secret brute force (WR-08) | high | mitigate | ADMIN family on `/api/admin` + `/api/admin/*`; filter at `HIGHEST_PRECEDENCE`, AdminTokenFilter at `+10`; test "operator routes are throttled per IP before the token check" | closed |
| T-18-23 | Tampering | path-spelling bypass (IN-01) | medium | mitigate | `RequestPaths.normalized` (servletPath+pathInfo, `;` stripped, dot/empty segments resolved); dot-segment login/join tests | closed |
| T-18-24 | Denial of service | operator batch conversion hitting admin throttle | low | accept | See Accepted Risks AR-18-06 | closed |
| T-18-25 | Repudiation | invite code mailed but never stored (WR-05) | medium | mitigate | `InviteService.kt:42` `saveAndFlush`; `WaitlistServiceConvertTest` `verifyOrder` | closed |
| T-18-26 | Denial of service | hung provider holding lock + connection (WR-05) | medium | mitigate | `WaitlistService` `app.waitlist.invite-send-timeout-ms` + `future.cancel(true)`; test "http convert with a hung sender times out with 502" | closed |
| T-18-27 | Repudiation | silent delivery failures (WR-06) | low | mitigate | One fixed WARN per failure branch (`WaitlistService.kt:213-244`); cause chained into `WaitlistInviteDeliveryException` | closed |
| T-18-28 | Information disclosure | PII / errorDetail in logs | medium | mitigate | Fixed log text, `errorDetail` never read in `src/main/.../waitlist`; `WaitlistServiceConvertTest:154-155` asserts no recipient / errorDetail in logs | closed |
| T-18-29 | Tampering | late completion of timed-out send | low | accept | See Accepted Risks AR-18-07 | closed |
| T-18-30 | Denial of service | @Async listeners rerouted to invite pool | high | mitigate | Private `ThreadPoolExecutor` field; zero `ThreadPoolTaskExecutor` in `src/main`; zero `@Bean` in `WaitlistService` | closed |
| T-18-31 | Denial of service | opaque 429 to landing page (WR-07c) | low | mitigate | `RateLimitFilter.addJoinCorsHeaders` (ACAO + Expose-Headers); `WaitlistCors429IntegrationTest` | closed |
| T-18-32 | Information disclosure | CORS grant to foreign origins / auth routes | medium | mitigate | `checkOrigin` on explicit-origin config, join family only; foreign/absent-origin and login tests in `WaitlistCors429IntegrationTest` | closed |
| T-18-33 | Tampering | two CORS policies drifting | low | mitigate | Single `WaitlistCorsPolicy` bean used by `SecurityConfig` and `RateLimitFilterConfig`; `SecurityConfig` no longer reads `app.waitlist.allowed-origins` | closed |
| T-18-34 | Elevation of privilege | future /api/admin route without handler guard | high | mitigate | `AdminTokenFilter` on `/api/admin/*` calls `AdminTokenGuard.require`; test "an unmapped admin path without a token is 401 not 404" | closed |
| T-18-35 | Denial of service | stale Bearer 401s operator (AUD-01) | medium | mitigate | `JwtAuthenticationFilter` skips `/api/admin` and `/api/admin/` on `RequestPaths.normalized`; test "correct token with a stale Bearer header still lists entries" | closed |
| T-18-36 | Information disclosure | 400 type-conversion before auth (IN-09) | low | mitigate | Filter runs before MVC; test "a malformed limit without a token is 401 not 400" | closed |
| T-18-37 | Tampering | broad waitlist JWT skip (IN-06) | low | mitigate | Exact `path ==` matches in `JwtAuthenticationFilter`; `/api/waitlistX` direct-filter test in `WaitlistEnumerationSafetyIntegrationTest` | closed |
| T-18-38 | Elevation of privilege | /api/admin/** permitAll | medium | mitigate | `AdminTokenFilter` runs before Spring Security on same prefix; handler `require()` retained (`WaitlistAdminController:37,52`, `InviteAdminController:34`) | closed |
| T-18-39 | Spoofing | undocumented trust/secret settings (IN-04) | medium | mitigate | `docs/CONFIGURATION.md` env table + Production row names `RATE_LIMIT_TRUSTED_PROXIES`, `INVITE_ADMIN_TOKEN`, `WAITLIST_ALLOWED_ORIGINS` | closed |
| T-18-40 | Repudiation | findings leaving the record silently | low | mitigate | `17-REVIEW-DISPOSITION.md` carries the AUD-01 row (`fixed`, 18-10) and no open rows | closed |
| T-18-SC (18-02) | Tampering | Maven install of Caffeine | high | mitigate | `build.gradle.kts` adds only `com.github.ben-manes.caffeine:caffeine` with no explicit version (BOM-managed, D-18 user-approved) | closed |
| T-18-SC (18-01, 18-03…18-11) | Tampering | npm/pip/cargo installs | high | accept | See Accepted Risks AR-18-08 | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-18-01 | T-18-02 | D-04: suppressed messages are marked delivered so they cannot resurface; the flag no longer distinguishes "pushed" from "suppressed". Message rows are kept as evidence (Phase 13 D-07) | Plan-time disposition (18-01) | 2026-10-05 |
| AR-18-02 | T-18-03 | Exactly one indexed query added per reconnect (D-05 forbids per-conversation lookups); no unbounded work | Plan-time disposition (18-01) | 2026-10-05 |
| AR-18-03 | T-18-05 | A flood of fresh keys can still evict and reset a throttled key; limits are per instance (distributed buckets deferred); W-TinyLFU admission favors frequently hit keys | Plan-time disposition (18-02) | 2026-10-05 |
| AR-18-04 | T-18-16 | D-13 (deliberate): a short admin token blocks startup until rotated; documented in docs/CONFIGURATION.md | Plan-time disposition (18-05) | 2026-10-05 |
| AR-18-05 | T-18-20 | A no-op re-join still consumes a per-email token; mail always goes to the pinned owner address and the cap is 3/24 h, so the attacker gains nothing | Plan-time disposition (18-06) | 2026-10-05 |
| AR-18-06 | T-18-24 | D-13 counts every admin request; capacity is configurable (`RATE_LIMIT_ADMIN_CAPACITY`) and 429s carry Retry-After; documented | Plan-time disposition (18-07) | 2026-10-05 |
| AR-18-07 | T-18-29 | A provider ignoring interrupts may deliver a rolled-back code; redeeming it fails with the generic invite-required 403 and an operator retry sends a valid code; documented | Plan-time disposition (18-08) | 2026-10-05 |
| AR-18-08 | T-18-SC (18-01, 18-03…18-11) | These plans install no packages | Plan-time disposition | 2026-10-05 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-05 | 42 | 42 | 0 | /gsd-secure-phase (orchestrator, ASVS L1 grep-depth; auditor skipped per short-circuit rule) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-05
