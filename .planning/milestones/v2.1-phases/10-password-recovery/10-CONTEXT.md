# Phase 10: Password Recovery - Context

**Gathered:** 2026-08-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Self-service password recovery: a `POST /forgot-password` → emailed single-use
tokenized reset link → `POST /reset-password` flow. Ships the reusable
transactional-email foundation (an `EmailSender` abstraction + a no-op/logging
provider) that Phases 11–12 will reuse.

**In scope:** forgot/reset endpoints, hashed single-use reset token with short
TTL, enumeration-safe responses, rate limiting, revoke-all-sessions on reset,
the `EmailSender` seam + logging/no-op provider, backend-rendered email body.

**Out of scope (own phases / deferred):** email verification on signup (Phase 11),
logged-in credential changes (Phase 12), concrete provider selection/wiring
(deferred per seed), any web reset page, 2FA/SMS recovery (v2 requirements).
</domain>

<decisions>
## Implementation Decisions

### Reset link & email content
- **D-01:** The reset link base URL is **environment-configured** (e.g.
  `app.reset-password-url`); the backend appends the raw token. It is a
  universal/deep link into the mobile app — **no web reset page** is built
  (there is no web frontend, and the mobile app lives in a separate repo).
- **D-02:** The email body is **rendered by the backend** (simple HTML/text
  template owned in this repo), not a provider-hosted template. Keeps content
  provider-agnostic so the concrete provider stays swappable.

### Provider scope this phase
- **D-03:** Phase 10 ships **only the `EmailSender` abstraction + a
  no-op/logging provider** (satisfies EMAIL-01/EMAIL-02 for local dev + tests).
  Concrete provider (SendGrid/Postmark/Resend/SES) selection is **deferred** per
  `transactional-email-infra.md` — the first consumer builds the seam, later
  features reuse it.

### Rate limiting
- **D-04:** **Reuse the existing per-IP `RateLimitFilter`** (already covers
  `/api/auth/*`, so `/forgot-password` is IP-limited for free) **and add a
  lightweight per-email Bucket4j guard** in the service layer, because
  email-bombing targets a specific address across many IPs. Reuses the Bucket4j
  library — no new rate-limiting infrastructure (matches REQUIREMENTS "Out of
  Scope").

### Reset response & session behavior
- **D-05:** `POST /forgot-password` returns **202 Accepted** with a generic
  enumeration-safe body regardless of whether the email is registered.
- **D-06:** Reset token **TTL = 30 minutes**.
- **D-07:** `POST /reset-password` returns **200 with no tokens** — a successful
  reset **revokes all refresh tokens** (via existing `revokeAllUserTokens`) and
  forces a fresh login (no auto-login).

### Claude's Discretion
- Token entity/table name, column layout, and the exact hashing helper (mirror
  the password-hashing / refresh-token patterns already in `auth`).
- Per-email bucket capacity/refill values and the dedicated forgot-password
  IP capacity (tune to sane anti-abuse defaults).
- Package/file placement for `EmailSender` (mirror `push/service` layout).
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Design & requirements
- `.planning/notes/password-recovery-design.md` — full design: delivery
  mechanism, secure token model, enumeration protection, rate-limiting intent,
  proposed API surface (`POST /api/auth/forgot-password`, `POST
  /api/auth/reset-password`).
- `.planning/seeds/transactional-email-infra.md` — rationale for the reusable
  `EmailSender` abstraction and deferring the concrete provider.
- `.planning/REQUIREMENTS.md` — EMAIL-01, EMAIL-02, RECOV-01..07 (the locked
  requirements for this phase) and the "Out of Scope" reuse-Bucket4j note.

### Reusable code (see code_context below for line refs)
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt`
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt`
- `src/main/kotlin/com/catspell/api/auth/model/User.kt`,
  `UserRepository.kt`, `RefreshTokenRepository`
- `src/main/kotlin/com/catspell/api/push/service/PushProvider.kt`
  (+ `FcmPushProvider.kt`) and `src/test/kotlin/com/catspell/api/push/FcmSmokeTest.kt`
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt`
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AuthService.revokeAllUserTokens(user)`** (currently `private`,
  `AuthService.kt:101-105`): call on successful reset (D-07). Planner should
  decide whether to expose it or add a dedicated reset method on `AuthService`.
- **`RefreshTokenRepository.findAllByUserAndRevokedFalse`**: backs the
  revoke-all behavior.
- **`UserRepository.findByEmail` / `existsByEmail`** (`UserRepository.kt`): look
  up the account for the (enumeration-safe) forgot flow.
- **`PushProvider` interface** (`push/service/PushProvider.kt`): the exact
  abstraction shape to mirror for `EmailSender` (interface + payload/result data
  classes + a concrete + a test double).
- **`FcmSmokeTest`**: the test-double / no-real-network testing pattern to mirror
  for email (EMAIL-02).
- **`PasswordEncoder`** (already injected in `AuthService`): reuse for hashing
  the reset token, same principle as `passwordHash`.

### Established Patterns
- Endpoints live under `AuthController` (`@RequestMapping("/api/auth")`,
  `@SecurityRequirements` for public routes) — add `/forgot-password` and
  `/reset-password` here.
- RFC 7807 problem responses + Bucket4j rate limiting are already the house
  style (see `RateLimitFilter` 429 body).
- Provider config/secret via `@Value` env injection (see `AuthService`
  `jwt.*`, `RateLimitFilter` `rate-limit.capacity`); `.env.example` holds keys.

### Integration Points
- `RateLimitFilter.AUTH_PATHS` (`RateLimitFilter.kt:24-28`): `/forgot-password`
  is already covered by the `/api/auth/*` URL pattern; the dedicated per-email
  guard is a new service-layer addition, not a filter change.
- New reset-token JPA entity + repository alongside `auth/model`.
- New `EmailSender` seam consumed by the forgot-password service path.
</code_context>

<specifics>
## Specific Ideas

- Proposed API surface is fixed by the design note:
  - `POST /api/auth/forgot-password` — body `{ email }`; always generic 202.
  - `POST /api/auth/reset-password` — body `{ token, newPassword }`; validates
    (exists, not expired, not used), updates `passwordHash`, marks token used,
    revokes refresh tokens, returns 200 (no session).
</specifics>

<deferred>
## Deferred Ideas

- **Concrete transactional email provider selection + wiring** — deferred to
  when it's actually needed (per seed). Phase 10 ships only the abstraction +
  no-op/logging provider.
- Email verification (Phase 11) and logged-in credential changes (Phase 12)
  reuse this phase's email infrastructure — not in scope here.
</deferred>

---

*Phase: 10-password-recovery*
*Context gathered: 2026-08-07*
