---
phase: 11
slug: email-verification
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-08-13
---

# Phase 11 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| DB at rest | A database dump must not yield usable live verification tokens. | Hashed verification tokens (SHA-256) |
| migration → existing data | The rollout migration (V17) must not lock out any pre-existing account. | `users.email_verified_at` backfill |
| app → email transport | The raw verification token leaves the system only inside the rendered email body. | Raw single-use token |
| config → runtime | The deep-link base URL is operator-supplied env config. | `app.verify-email-url` |
| client → HTTP API | Untrusted register/verify/resend bodies reach the service; responses must not leak account state. | Email, raw token |
| service → DB | Token lookups and single-use claims cross into persistence; must be atomic + parameterized. | Token hash, user_id |
| auth decision | The login gate decides session issuance based on verified state. | JWT access + refresh tokens |
| test harness → app | Tests exercise the real gate and real migrations; must not bypass security to pass. | `email_verified_at` writes |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-11-01 | Information Disclosure | Verification-token theft via DB leak | high | mitigate | Only SHA-256 `token_hash` persisted; entity has no raw-token column (`EmailVerificationToken.kt`, `hashToken`). | closed |
| T-11-02 | Tampering | Verification-token replay / reuse | high | mitigate | `UNIQUE` index on `token_hash` + nullable `used_at` + atomic `markUsed` conditional UPDATE with `usedAt IS NULL` guard (`EmailVerificationTokenRepository.kt`). | closed |
| T-11-03 | Denial of Service | Grandfather migration locks out users | high | mitigate | V17 backfill `UPDATE ... WHERE email_verified_at IS NULL` grandfathers every pre-existing row. | closed |
| T-11-04 | Denial of Service | Schema drift (entity vs V16/V17) blocks prod boot | high | mitigate | Column parity: entity `@Column` names/types match V16 & `User.emailVerifiedAt` matches V17 (ddl-auto validate). | closed |
| T-11-05 | Tampering | SQL injection on token lookup | medium | mitigate | Spring Data derived `findByTokenHash` + parameterized `@Query markUsed`; no string-built SQL. | closed |
| T-11-06 | Information Disclosure | Raw token leaked to logs/URLs | high | mitigate | Token generated via `SecureRandom`, embedded only in email link; verify endpoint takes it in JSON body, never logged (`EmailVerificationService.issueAndSend`, `EmailVerificationEmailRenderer`). | closed |
| T-11-07 | Spoofing | Real network email during CI reveals infra / flakes | medium | mitigate | Renderer only builds an `EmailMessage`; delivery goes through the existing `EmailSender` seam (stubbed in tests). | closed |
| T-11-08 | Tampering | Wrong/attacker-controlled `verify-email-url` misdirects links | low | accept | Operator-supplied env config, outside app trust boundary. | closed |
| T-11-09 | Information Disclosure | Account/verification-status enumeration via resend | high | mitigate | `resend` returns normally for unknown / verified / rate-limited emails — no distinct status, body, 429, or exception (`EmailVerificationService.resend`). | closed |
| T-11-10 | Information Disclosure | Enumeration via login gate ordering | high | mitigate | Verified gate evaluated AFTER the password check; unknown email + wrong password both stay generic 401 (`AuthService.login`). | closed |
| T-11-11 | Tampering | Verification-token replay/reuse (double verify) | high | mitigate | Atomic `markUsed` conditional UPDATE; second concurrent/repeat claim matches zero rows → `InvalidTokenException` (`AuthService.verifyEmail`). | closed |
| T-11-12 | Elevation of Privilege | Session minted from a link token (verify auto-login) | high | mitigate | `verifyEmail` returns no tokens and never calls `revokeAllUserTokens`; user logs in fresh (`AuthService.verifyEmail`). | closed |
| T-11-13 | Denial of Service | Email-bombing a specific address via resend | high | mitigate | Per-email Bucket4j guard (`EmailVerificationService`) + per-IP `AUTH_PATHS` layer (`RateLimitFilter`), two layers. | closed |
| T-11-14 | Spoofing | IDOR: token→user binding bypass | medium | mitigate | Token row carries `user_id` FK; `verifyEmail` resolves user from the claimed token only, never client input. | closed |
| T-11-15 | Information Disclosure | Timing side-channel on resend (user lookup only when bucket allows) | medium | accept | Generic 202 contract + per-email cap bound the surface; constant-time hardening deferred (low residual). | closed |
| T-11-16 | Elevation of Privilege | Auto-login / token leak on register or verify response | high | mitigate | `register` returns 201 `GenericMessageResponse`, `verify` returns 200 `Void` — no tokens (`AuthController`). | closed |
| T-11-17 | Information Disclosure | Enumeration via distinct resend response | high | mitigate | Identical 202 body for unknown / verified / unverified (`AuthController.resendVerification`). | closed |
| T-11-18 | Tampering | Verify accepts empty/expired/reused token | high | mitigate | Empty → 400 (Bean Validation), expired → 401, reused → 401 (`AuthService.verifyEmail` + `GlobalExceptionHandler`). | closed |
| T-11-19 | Spoofing | Real network email during CI reveals infra / flakes | medium | mitigate | `EmailSender` stubbed via MockK `@Primary` bean; no network in tests. | closed |
| T-11-20 | Information Disclosure | Raw token echoed to logs/URL | medium | mitigate | Token submitted in JSON body only; tests parse it from the email body, never a server URL/log. | closed |
| T-11-21 | Denial of Service | Grandfather migration locks out legitimate users | high | mitigate | `GrandfatherMigrationTest` proves a NULL-verified legacy user is backfilled and can log in (VERIFY-05). | closed |
| T-11-22 | Denial of Service | resend-verification email-bombing (missed AUTH_PATHS) | high | mitigate | Per-IP 429 boundary test proves `AUTH_PATHS` membership includes `/api/auth/resend-verification` (VERIFY-04). | closed |
| T-11-23 | Elevation of Privilege | Tests bypass the login gate, hiding a real auth regression | high | mitigate | Tests promote users via a real `email_verified_at` column write, never a gate-off flag. | closed |
| T-11-24 | Spoofing | Real network email during CI | medium | mitigate | `EmailSender` stubbed via MockK `@Primary` where needed; no network. | closed |
| T-11-SC | Tampering | npm/pip/cargo/gradle dependency installs | high | accept | No new dependencies introduced this phase — nothing to audit. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-11-01 | T-11-08 | The `app.verify-email-url` deep-link base is operator-supplied env config with a safe `catspell://` default; misconfiguration is outside the app trust boundary. | phase plan (11-02) | 2026-08-13 |
| R-11-02 | T-11-15 | The generic 202 resend contract plus per-email rate cap bound the enumeration surface; constant-time user-lookup hardening is deferred as low residual risk. | phase plan (11-03) | 2026-08-13 |
| R-11-03 | T-11-SC | No new dependencies were added in Phase 11, so there is no new supply-chain surface to audit. | phase plan (11-01..05) | 2026-08-13 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-13 | 24 | 24 | 0 | gsd-secure-phase (grep-depth L1, plan-time register) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-13
