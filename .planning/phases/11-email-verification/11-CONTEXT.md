# Phase 11: Email Verification - Context

**Gathered:** 2026-08-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Prove ownership of the signup email. On registration the backend creates the
account as **unverified** and emails a single-use tokenized verification link;
login is **hard-gated** until the email is verified; a rate-limited resend flow
reissues the link; and a data migration **grandfathers all existing accounts as
verified** so no current user is locked out on rollout (VERIFY-01..05).

**In scope:** verification email on register, `POST /api/auth/verify-email`,
hard login gate for unverified users, `POST /api/auth/resend-verification`
(enumeration-safe + rate-limited), new `email_verification_tokens` table,
`email_verified_at` column on `users`, grandfather migration, security-whitelist
+ rate-limit wiring for the new public endpoints. Reuses the Phase 10
`EmailSender` seam + backend-rendered email pattern.

**Out of scope (own phases / deferred):** logged-in credential changes — change
password / change email (Phase 12), concrete transactional-email provider
selection/wiring (still deferred per `transactional-email-infra.md`), any web
verification page (no web frontend — deep link into the mobile app), 2FA/SMS
(v2 requirements).
</domain>

<decisions>
## Implementation Decisions

### Register & verify token behavior
- **D-01:** `POST /api/auth/register` **no longer auto-logs-in**. It creates the
  unverified user, sends the verification email, and returns **201 with no
  tokens** (generic "check your email" body). This is a breaking change to the
  register contract — a hard gate means a freshly-registered user must not hold
  a session. (VERIFY-01, VERIFY-03)
- **D-02:** `POST /api/auth/verify-email` marks the email verified and returns
  **200 with no tokens** — the user then logs in normally. Mirrors Phase 10
  `reset-password` exactly: one consistent "sensitive action → fresh login"
  pattern; the verify endpoint never mints sessions from a link-token.
  (VERIFY-02)

### Login hard-gate error contract
- **D-03:** The gate is evaluated **after** the password check. Flow in
  `AuthService.login`: unknown email → generic `401 InvalidCredentials`; wrong
  password → generic `401 InvalidCredentials`; **correct password + unverified**
  → distinct **`403` RFC-7807 problem with a machine-readable code
  `EMAIL_NOT_VERIFIED`** so the mobile app can route to the resend screen. An
  attacker without the password learns nothing (minimal enumeration surface).
  (VERIFY-03)

### Resend flow semantics
- **D-04:** `POST /api/auth/resend-verification` is **enumeration-safe** — it
  ALWAYS returns a generic **202** ("if an unverified account exists, a link has
  been sent") regardless of whether the email is unknown, already verified, or
  rate-limited. Mirrors the Phase 10 forgot-password contract (D-05 there).
  (VERIFY-04)
- **D-05:** Rate limiting mirrors forgot-password's **two layers**: add
  `/api/auth/resend-verification` to `RateLimitFilter.AUTH_PATHS` (per-IP) AND a
  per-email Bucket4j guard in the service layer. On per-email exhaustion,
  **silently skip the send and still return 202** (no 429, no existence signal).
  Reuses the existing Bucket4j library — no new rate-limiting infra.

### Token model & verify endpoint shape
- **D-06:** Verified state is stored as a **nullable `email_verified_at`
  TIMESTAMPTZ** column on `users` (NULL = unverified; a value = verified + when).
  Chosen over a boolean for audit value and to match the existing timestamp
  style (`used_at`, `expires_at`, `created_at`).
- **D-07:** New **`email_verification_tokens`** table mirrors
  `password_reset_tokens` (SHA-256 raw-token hash, Base64url raw token,
  `expires_at`, `used_at`, invalidate prior unused tokens on reissue). Verify
  endpoint is **`POST /api/auth/verify-email` with `{ token }` in the JSON
  body** — consistent with `reset-password`, keeps the raw token out of server
  logs/URLs, and fits the "app owns the deep link, extracts token, calls API"
  model (Phase 10 D-01). No GET-with-query-token variant (no web frontend).
- **D-08:** Verification-token **TTL = 24 hours** (env-configurable, e.g.
  `app.verify-token.ttl-hours:24`) — longer than the 30-min reset TTL because
  signup verification is less time-sensitive and users often check email later;
  resend issues a fresh token.

### Grandfather migration
- **D-09:** A Flyway migration (next number after V15 → **V17**; a V16 table
  migration is also needed, see below) backfills `email_verified_at` for all
  pre-existing `users` rows so nobody is locked out on rollout. Backfill value
  (e.g. `NOW()` vs `created_at`) is Claude's discretion. (VERIFY-05)

### Claude's Discretion
- Exact Flyway version numbers / ordering (new token table + `users` column +
  backfill; likely V16 = `email_verification_tokens` table, V17 = add column +
  grandfather backfill — planner decides how to split).
- Grandfather backfill value (`NOW()` vs each row's `created_at`).
- Package/file placement: mirror the Phase 10 layout — token entity/repo under
  `auth/model`, service under `auth/service` (e.g. `EmailVerificationService`),
  renderer under `email/service` (mirror `PasswordResetEmailRenderer`).
- Per-email/per-IP bucket capacity/refill defaults for resend (sane anti-abuse
  values, env-configurable like the forgot-password knobs).
- Whether the verification-email send in `register` stays synchronous (matches
  Phase 10; the no-op provider makes this a non-issue) or is dispatched
  off-thread.
- The new `app.verify-email-url` deep-link base config key name and the email
  renderer copy.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & prior-phase context
- `.planning/REQUIREMENTS.md` — VERIFY-01..05 (locked requirements for this
  phase) and the "Out of Scope" reuse-Bucket4j / no-new-rate-limit-infra note.
- `.planning/phases/10-password-recovery/10-CONTEXT.md` — Phase 10 decisions
  reused here (EmailSender seam, backend-rendered email, hashed single-use
  token, enumeration-safe generic responses, two-layer rate limiting).
- `.planning/notes/password-recovery-design.md` — secure token model,
  enumeration protection, rate-limiting intent, and the API-surface style this
  phase mirrors.
- `.planning/seeds/transactional-email-infra.md` — rationale for the reusable
  `EmailSender` abstraction and why the concrete provider stays deferred.

### Reusable code (see code_context below)
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` — `register`,
  `login`, `revokeAllUserTokens`, `createRefreshToken`.
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` — where
  `/verify-email` and `/resend-verification` are added.
- `src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt` —
  the closest template for the new verification service (token gen/hash,
  per-email Bucket4j guard, prior-token invalidation, enumeration-safe flow).
- `src/main/kotlin/com/catspell/api/auth/model/PasswordResetToken.kt` +
  `PasswordResetTokenRepository` — entity/repo shape to mirror.
- `src/main/kotlin/com/catspell/api/auth/model/User.kt` — add
  `email_verified_at`.
- `src/main/kotlin/com/catspell/api/email/service/EmailSender.kt` +
  `PasswordResetEmailRenderer.kt` — seam + renderer pattern to mirror.
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`
  (`AUTH_PATHS`), `JwtAuthenticationFilter.kt`, and
  `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` — the three
  places a new public `/api/auth/*` endpoint must be whitelisted.
- `src/main/resources/db/migration/V15__create_password_reset_tokens_table.sql`
  — migration template for the new token table.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`PasswordResetService`** is a near-exact template: `generateRawToken()`
  (32 random bytes, Base64url), `hashToken()` (SHA-256 hex), per-email
  `ConcurrentHashMap<String, Bucket>` guard, prior-unused-token invalidation,
  and the enumeration-safe "always return normally" flow. Clone its shape for
  an `EmailVerificationService`.
- **`PasswordResetToken` entity + repository** — mirror as
  `EmailVerificationToken` (`findAllByUserAndUsedAtIsNull`, `findByTokenHash`).
- **`PasswordResetEmailRenderer`** (`@Value app.reset-password-url`) — mirror as
  a verification renderer keyed on a new `app.verify-email-url`.
- **`AuthService.login`** — add the verified gate here after the
  `passwordEncoder.matches` check (D-03).
- **`AuthService.register`** — change to no-token 201 + trigger verification
  email (D-01).
- **`EmailSender`** seam + no-op/logging provider from Phase 10 — reuse as-is
  (no real network sends in dev/tests, EMAIL-02).

### Established Patterns
- Public auth endpoints must be whitelisted in **three** places:
  `SecurityConfig.requestMatchers(...).permitAll()`,
  `JwtAuthenticationFilter` public-path checks, and (for rate limiting)
  `RateLimitFilter.AUTH_PATHS`. `reset-password` currently appears in the first
  two but NOT `AUTH_PATHS`; `verify-email` follows the same shape, while
  `resend-verification` should also join `AUTH_PATHS` (D-05).
- RFC 7807 problem responses + custom exceptions in
  `com.catspell.api.common.exception` (e.g. `InvalidCredentialsException`,
  `InvalidTokenException`) — add an `EmailNotVerifiedException`-style 403 with
  the `EMAIL_NOT_VERIFIED` code (D-03).
- Config via `@Value` env injection with sane defaults (see the
  `app.forgot-password.*`, `app.reset-token.ttl-minutes` knobs) — no
  `@ConfigurationProperties`.
- Flyway migrations are sequential (`V1..V15`); next are V16/V17.

### Integration Points
- `users` table gains `email_verified_at` (V17 add-column + grandfather
  backfill).
- New `email_verification_tokens` table (V16), FK to `users` with
  `ON DELETE CASCADE`, unique index on `token_hash` (mirror V15).
- `login` gate reads the new column; `register` writes an unverified user +
  issues the first verification token.
- Two new rows in the security whitelist / rate-limit path sets.
</code_context>

<specifics>
## Specific Ideas

- Proposed API surface (mirrors Phase 10 conventions):
  - `POST /api/auth/register` — creates unverified user, sends verification
    email; returns **201, no tokens**, generic body.
  - `POST /api/auth/verify-email` — body `{ token }`; validates (exists, not
    expired, not used), sets `email_verified_at`, marks token used; returns
    **200, no tokens**.
  - `POST /api/auth/resend-verification` — body `{ email }`; always generic
    **202**; enumeration-safe + two-layer rate-limited.
  - `POST /api/auth/login` — unchanged happy path; adds a distinct
    **403 `EMAIL_NOT_VERIFIED`** for correct-password-but-unverified.
</specifics>

<deferred>
## Deferred Ideas

- **Change email / change password while logged in (ACCT-01..05)** — Phase 12;
  the change-email flow will reuse this phase's verification token +
  `email_verified_at` machinery.
- **Concrete transactional-email provider selection + wiring** — still deferred
  per `transactional-email-infra.md`; Phase 11 keeps using the no-op/logging
  provider.

None of the discussion strayed outside the phase scope.
</deferred>

---

*Phase: 11-email-verification*
*Context gathered: 2026-08-11*
