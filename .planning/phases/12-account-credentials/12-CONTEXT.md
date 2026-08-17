# Phase 12: Account Credentials - Context

**Gathered:** 2026-08-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Self-service credential changes for a **logged-in** user. Two capabilities:

1. **Change password** — supply the current password + a new password; on
   success the password is updated and **all** refresh-token sessions are
   revoked (fresh login everywhere).
2. **Change email** — supply the current password + a new email; the new
   address is stored as a **pending** change and a verification link is emailed
   **to the new address**. The account's active email only switches once that
   link is confirmed. The request is rejected up front if the new address
   already belongs to another account.

Reuses the Phase 10/11 machinery: `EmailSender` seam + backend-rendered email,
SHA-256-hashed single-use tokens (atomic `markUsed`, prior-token invalidation),
`@Value` env config, RFC 7807 problem responses, and the "sensitive action →
fresh login" convention.

**In scope:** two authenticated endpoints (change-password, change-email), one
public token-only endpoint (`confirm-email-change`), a new
`email_change_requests` table (V18), a new change-email renderer + `app.*-url`
deep-link config key, a new `INVALID_CURRENT_PASSWORD` 403 exception, a
"revoke all other sessions" behavior (implemented as revoke-all here), and
integration tests covering ACCT-01..05.

**Out of scope (own phases / deferred / v2):** password-strength rules beyond
the existing `@Size(min=8)`, 2FA/SMS, account deletion, a "revoke a *specific*
device/session" management surface, concrete transactional-email provider
selection/wiring (still deferred per `transactional-email-infra.md` — keep the
no-op/logging provider), any web page for the confirm link (deep-link into the
mobile app only).
</domain>

<decisions>
## Implementation Decisions

### Change password (ACCT-01, ACCT-02)
- **D-01:** Current-password verification reuses the existing
  `passwordEncoder.matches(raw, user.passwordHash)` idiom
  (`AuthService.login`, line 51). (ACCT-01)
- **D-02:** On a **wrong current password**, return a distinct **403** with a
  machine-readable code **`INVALID_CURRENT_PASSWORD`** (new exception, mirrors
  Phase 11's `EmailNotVerifiedException` / `EMAIL_NOT_VERIFIED`). Chosen over
  reusing `InvalidCredentialsException` (401) because the caller is already
  authenticated — a 401 on an authenticated request is semantically odd and can
  confuse the app's token-refresh logic. Applies to **both** change-password and
  change-email (they both require the current password).
- **D-03:** On successful password change, update `passwordHash` +
  `updatedAt` and **revoke ALL refresh-token sessions** (reuse the
  `resetPassword` "fresh login" path / `revokeAllUserTokens`). ACCT-02 says
  "other" sessions, but the access-token principal carries only the userId — the
  server cannot identify the caller's own refresh token, so we revoke all
  (stricter, simplest, consistent with Phase 10). The endpoint does **not** mint
  new tokens; the user logs in again. (ACCT-02)

### Change email — storage & confirm flow (ACCT-03, ACCT-04)
- **D-04:** The pending new address lives in a **new dedicated
  `email_change_requests` table** (V18) — NOT on `users` and NOT bolted onto
  `email_verification_tokens`. Columns mirror the existing token tables:
  `user_id` FK (`ON DELETE CASCADE`), `new_email`, `token_hash` (unique,
  SHA-256), `expires_at`, `used_at`, `created_at`. Keeps signup-verification
  semantics untouched and the core `users` table clean.
- **D-05:** Change-email is a **two-step** flow. Step 1 (authenticated):
  validate current password (D-01/D-02), reject if taken (D-06), create an
  `email_change_request`, and email a confirmation link **to the new address**
  (new renderer targeting the request's `new_email`, not `user.email`). Step 2
  (public): confirm the token. The account email is **only** switched on
  confirmation. (ACCT-03, ACCT-04)
- **D-06:** If the requested new email **already belongs to another account**,
  reject **immediately with 409 Conflict** (reuse `DuplicateEmailException`).
  Chosen over an enumeration-safe generic response: the caller is authenticated
  and rate-limited (low enumeration risk), and a legitimate user needs to know
  why the change won't proceed. (ACCT-05)
- **D-07:** **`POST /api/auth/confirm-email-change` with `{ token }`** is
  **public / token-only** — whitelisted like `/verify-email` and
  `/reset-password` (SecurityConfig `permitAll` + `JwtAuthenticationFilter.
  shouldNotFilter`). The single-use token proves ownership, so the link works
  even when opened logged-out or on a different device. Mirrors the Phase 10/11
  deep-link model.
- **D-08:** On successful confirm: atomically claim the token (`markUsed`),
  **swap `users.email` to `new_email`**, stamp **`email_verified_at`** (the new
  address is now proven), and **revoke ALL refresh-token sessions** — an identity
  change is treated as a sensitive action requiring fresh login (account-takeover
  safety), consistent with D-03. Returns **200, no tokens**. (ACCT-04)

### Claude's Discretion
- Exact endpoint names/paths for the two authenticated routes — suggested
  `POST /api/auth/change-password` and `POST /api/auth/change-email` on the
  existing `AuthController` (no `@SecurityRequirements`, resolve the user via the
  `extractUserId()` `SecurityContextHolder` convention).
- Rate limiting on the new routes: mirror the existing two-layer pattern — add
  the change-email request path to per-IP `RateLimitFilter.AUTH_PATHS` and add a
  **per-target-email** Bucket4j guard on the *new* address to prevent bombing a
  victim's inbox; per-IP already covers `/api/auth/*`. Bucket capacities/refill
  and env-config key names follow the `app.forgot-password.*` /
  `app.resend-verification.*` style.
- Change-email confirmation token TTL (env-configurable; the 24h verify-token
  TTL is a reasonable analog) and the `app.confirm-email-change-url` deep-link
  config key name + renderer copy.
- Whether the change-email *request* additionally requires the account's current
  email to already be verified (not required by ACCT; lean "no" unless it
  simplifies).
- Flyway split/DDL details for V18 (`email_change_requests` table).
- Whether to expose/rename `revokeAllUserTokens` / `createRefreshToken` (both
  currently `private` in `AuthService`) or add dedicated change-credential
  methods — mirror how `resetPassword`/`verifyEmail` were factored.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & prior-phase context
- `.planning/REQUIREMENTS.md` — ACCT-01..05 (locked requirements for this
  phase) and the "Out of Scope" reuse-Bucket4j / no-new-rate-limit-infra note.
- `.planning/phases/11-email-verification/11-CONTEXT.md` — verification token
  model, atomic `markUsed`, prior-token invalidation, enumeration-safe patterns,
  the distinct-403-with-machine-code convention (`EMAIL_NOT_VERIFIED`), and the
  three-place security whitelist rule. Explicitly notes ACCT change-email reuses
  this phase's verification + `email_verified_at` machinery.
- `.planning/phases/10-password-recovery/10-CONTEXT.md` — `EmailSender` seam,
  backend-rendered email, hashed single-use token, enumeration-safe responses,
  two-layer rate limiting, and the "reset → revoke all sessions → fresh login"
  pattern that D-03/D-08 mirror.
- `.planning/notes/password-recovery-design.md` — secure token model,
  enumeration protection, rate-limiting intent, and the API-surface style.
- `.planning/seeds/transactional-email-infra.md` — rationale for the reusable
  `EmailSender` abstraction and why the concrete provider stays deferred.

### Reusable code (see code_context below)
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` — `login`
  (current-password check, line 51), `resetPassword` (revoke-all + fresh-login
  template, 99-122), `verifyEmail` (atomic `markUsed` + stamp, 124-146),
  `hashToken` (148-152), `revokeAllUserTokens` (private, 165-169),
  `createRefreshToken` (private, 154-163).
- `src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt` —
  `issueAndSend` (51-69), per-email Bucket4j guard (35-43), `generateRawToken`
  / `hashToken` — the closest template for a change-email service.
- `src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt` —
  enumeration-safe flow, per-email bucket, prior-token invalidation.
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` — where
  the three new endpoints are added; `/me` (85-91) shows the
  SecurityContext-principal-as-userId pattern.
- `src/main/kotlin/com/catspell/api/auth/model/EmailVerificationToken.kt` +
  `EmailVerificationTokenRepository.kt` — entity/repo shape to mirror for
  `email_change_requests` (add a `new_email` column; keep `findByTokenHash`,
  `findAllByUserAndUsedAtIsNull`, atomic `markUsed`).
- `src/main/kotlin/com/catspell/api/auth/model/User.kt` — `email` (unique),
  `passwordHash`, `emailVerifiedAt`, `updatedAt`.
- `src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt` — DTO/validation
  style (`@field:Email`, `@field:Size(min=8)`); add change-credential DTOs here.
- `src/main/kotlin/com/catspell/api/email/service/EmailSender.kt`,
  `EmailVerificationEmailRenderer.kt`, `PasswordResetEmailRenderer.kt` — seam +
  renderer pattern; the new renderer must target the request's `new_email`.
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` (line 28
  `permitAll` list), `.../common/security/JwtAuthenticationFilter.kt`
  (`shouldNotFilter`, 16-26), `.../common/security/RateLimitFilter.kt`
  (`AUTH_PATHS`, 24-30) — the three whitelist/rate-limit locations; only
  `confirm-email-change` is public.
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` +
  `GlobalExceptionHandler.kt` — `DuplicateEmailException` (409, reuse for D-06),
  `InvalidTokenException` (401), `EmailNotVerifiedException` (403 pattern to
  mirror for `INVALID_CURRENT_PASSWORD`).
- `src/main/resources/db/migration/V16__create_email_verification_tokens_table.sql`
  — migration template for the new `email_change_requests` table (V18 is next).
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AuthService.resetPassword`** is the near-exact template for
  change-password: verify credential → update `passwordHash` + `updatedAt` →
  `revokeAllUserTokens` → return no tokens. Change-password differs only in
  verifying the *current password* (D-01) instead of a reset token.
- **`EmailVerificationService.issueAndSend`** is the template for the
  change-email issue path (mint raw token, SHA-256 hash, prior-token
  invalidation, render + send) — but the renderer must target the pending
  `new_email`, not `user.email`, and the token is stored in the new
  `email_change_requests` table.
- **`EmailVerificationTokenRepository.markUsed`** (`@Modifying @Query ...
  WHERE id = :id AND usedAt IS NULL`) — the atomic single-use claim pattern to
  copy for confirm-email-change (prevents double-apply).
- **`UserRepository.existsByEmail` / `findByEmail`** — the taken-email check
  for D-06.
- **`extractUserId()` convention** (`SecurityContextHolder...principal as
  String` → `UUID.fromString`) used across all authenticated controllers — the
  two authenticated endpoints resolve the caller this way.
- **Per-email Bucket4j guard** (`ConcurrentHashMap<String, Bucket>`) from
  PasswordReset/EmailVerification services — reuse for the change-email target
  address.

### Established Patterns
- Distinct sensitive-error responses use a custom exception → RFC 7807
  `ProblemDetail` with a machine-readable code (e.g. `EMAIL_NOT_VERIFIED`);
  add `INVALID_CURRENT_PASSWORD` (403) the same way (D-02).
- Public `/api/auth/*` endpoints must be whitelisted in **three** places:
  `SecurityConfig` `permitAll`, `JwtAuthenticationFilter.shouldNotFilter`, and
  (for IP rate-limiting) `RateLimitFilter.AUTH_PATHS`. Only
  `confirm-email-change` is public (D-07); the two change endpoints stay
  authenticated and must NOT be whitelisted.
- Config via `@Value` env injection with sane defaults (no
  `@ConfigurationProperties`). Deep-link base URLs use `app.*-url` keys with a
  `catspell://...` default (see `app.verify-email-url`).
- Flyway migrations are sequential; **next version is V18**. Token tables use
  `token_hash VARCHAR(255) NOT NULL UNIQUE`, FK `ON DELETE CASCADE`, plus
  `idx_*_user_id` and unique `idx_*_token_hash`.
- DTO validation uses Kotlin `@field:` targets (`@field:Email`,
  `@field:Size(min = 8, message = "must be at least 8 characters")`).

### Integration Points
- New `email_change_requests` table (V18), FK to `users`.
- `users.email` is swapped and `email_verified_at` re-stamped on confirm (D-08);
  `users.password_hash` + `updated_at` updated on password change (D-03).
- Both success paths call the revoke-all-sessions behavior (`revokeAllUserTokens`
  is currently `private` — planner decides how to expose/factor it).
- One new public row in the security whitelist / `shouldNotFilter` sets
  (`confirm-email-change`); optional AUTH_PATHS addition for change-email.
</code_context>

<specifics>
## Specific Ideas

Proposed API surface (mirrors Phase 10/11 conventions):
- `POST /api/auth/change-password` — **authenticated**; body
  `{ currentPassword, newPassword }`; validates current password (403
  `INVALID_CURRENT_PASSWORD` on mismatch), updates hash, revokes all sessions;
  returns 200, no tokens.
- `POST /api/auth/change-email` — **authenticated**; body
  `{ currentPassword, newEmail }`; validates current password, rejects taken
  address with 409, creates an `email_change_request`, emails a confirm link to
  the new address; returns a generic 202-style acknowledgement.
- `POST /api/auth/confirm-email-change` — **public**; body `{ token }`;
  validates (exists, not expired, not used), swaps `users.email`, stamps
  `email_verified_at`, revokes all sessions; returns 200, no tokens.
</specifics>

<deferred>
## Deferred Ideas

- **Revoke-a-specific-device / session-management surface** — the "keep current
  session, revoke others" UX (client passes its refresh token) was considered
  and rejected for this phase in favor of revoke-all; a proper session/device
  management feature would be its own phase.
- **Stronger password-strength rules** — kept at the existing `@Size(min=8)`;
  complexity/regex/breach-check rules are a separate concern.
- **Concrete transactional-email provider selection + wiring** — still deferred
  per `transactional-email-infra.md`; keep the no-op/logging provider.
- **2FA/SMS, account deletion** — v2 requirements, not in this phase.

Discussion stayed within phase scope.
</deferred>

---

*Phase: 12-account-credentials*
*Context gathered: 2026-08-17*
