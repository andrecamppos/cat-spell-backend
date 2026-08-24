---
phase: 10
slug: password-recovery
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-08-08
---

# Phase 10 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| config → renderer | Reset-link base URL originates from `app.reset-password-url` config only, never an HTTP request header. | Trusted deep-link base URL |
| app → email provider seam | Messages leave the app via `EmailSender`; the raw reset token rides in the message body. | Raw reset token (secret) |
| app → logs | Log sink is lower-trust; recipient and token must not appear in cleartext. | Recipient email, token |
| DB at rest | A DB dump must not yield usable live reset tokens. | SHA-256 token hash only |
| app → DB | Token lookups cross into persistence; parameterized, by hash only. | token_hash (hex) |
| client → forgot flow | Untrusted email input; response must not reveal account existence. | Email address |
| client → reset flow | Untrusted raw token + new password; only a valid/unused/unexpired token may mutate state. | Raw token, new password |
| filter tier → controller | Public routes bypass JWT auth yet remain rate-limited. | HTTP request |
| API → client (response) | Response must not carry session tokens (reset) or existence signals (forgot). | HTTP response body/status |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-10-01 | Information Disclosure | LoggingEmailSender logs | high | mitigate | `LoggingEmailSender.kt:14,18-25` logs `maskEmail(to)` + subject only; raw token/body never logged. Verified by `EmailSenderContractTest`. | closed |
| T-10-02 | Tampering | Reset-link base URL (host-header/link poisoning) | high | mitigate | `PasswordResetEmailRenderer.kt:8,12` builds link from `@Value("app.reset-password-url")`; no `HttpServletRequest`/host-header read. | closed |
| T-10-03 | Spoofing/Info Disclosure | Real network send from default provider | high | mitigate | `LoggingEmailSender.kt:7-9` `@ConditionalOnProperty(..., matchIfMissing=true)` no-op default; concrete provider deferred (D-03). `EmailSenderSelectionTest` proves default. | closed |
| T-10-04 | Information Disclosure | Token theft via DB leak | high | mitigate | `PasswordResetService.kt:75,90-94` stores only SHA-256 `token_hash`; V15 has no raw-token column. | closed |
| T-10-05 | Tampering | Token replay / collision | high | mitigate | `V15:4,11` `UNIQUE token_hash` + unique index; nullable `used_at` single-use marker; atomic consume in `AuthService`. | closed |
| T-10-06 | Tampering | SQL injection on token lookup | medium | mitigate | `PasswordResetTokenRepository.kt:11` Spring Data derived `findByTokenHash` (parameterized); `markUsed` uses `@Param` JPQL. No raw SQL. | closed |
| T-10-07 | Denial of Service | Schema drift (entity vs V15) blocks prod boot | high | mitigate | `PasswordResetToken.kt:18-28` `@Column` names map 1:1 to `V15` columns; exercised end-to-end by `PasswordResetIntegrationTest` under Testcontainers. | closed |
| T-10-08 | Information Disclosure | Account enumeration via forgot-password | high | mitigate | `PasswordResetService.kt:49-82` returns Unit on every branch (rate-limited/absent/issued); no exception/429/distinct return (D-05). | closed |
| T-10-09 | Spoofing/Tampering | Reset-token brute force | high | mitigate | `PasswordResetService.kt:32,84-88` 32-byte (256-bit) `SecureRandom` token; 30-min TTL (line 76); per-IP + per-email rate limiting. | closed |
| T-10-10 | Tampering | Reset-token replay / double-use | high | mitigate | `AuthService.kt:93-108` `@Transactional`; atomic conditional `markUsed(... WHERE usedAt IS NULL)`; 0 rows → 401. Used/expired rejected. | closed |
| T-10-11 | Elevation of Privilege | Session persistence after compromise | high | mitigate | `AuthService.kt:115,135-139` `revokeAllUserTokens(user)` on successful reset (RECOV-06/D-07). | closed |
| T-10-12 | Elevation of Privilege | Cross-account reset via client-supplied identity | high | mitigate | `AuthService.kt:110` user derived from `resetToken.user` (DB FK); client supplies only the opaque token. | closed |
| T-10-13 | Denial of Service | Email bombing / provider-cost abuse | high | mitigate | `RateLimitFilter.kt:28` per-IP `AUTH_PATHS` entry + `PasswordResetService.kt:34-56` per-email Bucket4j guard (RECOV-07/D-04). | closed |
| T-10-14 | Information Disclosure | Timing side-channel reveals existence | medium | accept | ASVS L1 hard gate is the identical response (mitigated). Uniform timing is defense-in-depth, noted not enforced (RESEARCH Pitfall #3). Below block threshold. | closed |
| T-10-15 | Elevation of Privilege | Endpoint omitted from a whitelist | high | mitigate | `SecurityConfig.kt:28` permitAll + `JwtAuthenticationFilter.kt:21-22` shouldNotFilter + `RateLimitFilter.kt:28` AUTH_PATHS — all three tiers updated (Pitfall #1). | closed |
| T-10-16 | Information Disclosure | Enumeration via distinct forgot response | high | mitigate | Controller returns unconditional fixed 202 body; `PasswordResetIntegrationTest` RECOV-04 asserts byte-identical status+body for registered vs unregistered. | closed |
| T-10-17 | Elevation of Privilege | Auto-login / token leak on reset response | high | mitigate | `AuthController.kt` reset returns 200 `Void` (`ok().build()`); RECOV-03 asserts empty body + no tokens (D-07). | closed |
| T-10-18 | Denial of Service | Unlimited forgot-password (missed AUTH_PATHS) | high | mitigate | `RateLimitIntegrationTest` per-IP 429 boundary test proves AUTH_PATHS membership (RECOV-07). | closed |
| T-10-19 | Spoofing | Real network email during CI reveals infra / flakes | medium | mitigate | `EmailSender` stubbed via MockK `@Primary` bean in tests; no network send. | closed |
| T-10-20 | Input Validation | Malformed email / short password | medium | mitigate | `AuthDtos.kt:33-38` `@Email` + `@Size(min=8)` DTO validation → 400 via GlobalExceptionHandler. | closed |
| T-10-SC | Tampering | Package installs (supply chain) | high | accept | No new dependencies added this phase (RESEARCH: all libs already on classpath); no install to audit. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-10-01 | T-10-14 | Timing side-channel on forgot-password. ASVS L1 requirement (identical response) is satisfied; uniform-timing hardening is defense-in-depth, noted but not enforced this phase. | Phase 10 plan (D-05 / RESEARCH Pitfall #3) | 2026-08-08 |
| AR-10-02 | T-10-SC | No new third-party dependencies were introduced this phase; all libraries were already on the classpath, so there is no new install to audit. | Phase 10 plans 01–04 | 2026-08-08 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-08 | 21 | 21 | 0 | gsd-secure-phase (L1 grep verification) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-08
