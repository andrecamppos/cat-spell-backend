# Phase 10: Password Recovery - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-07
**Phase:** 10-password-recovery
**Areas discussed:** Reset link + email content, Provider scope this phase, Rate-limiting approach, Reset response + session

---

## Reset link + email content

| Option | Description | Selected |
|--------|-------------|----------|
| Env-configured link + backend template | Reset URL base from env; backend appends raw token and renders a simple HTML/text body. Provider-agnostic, universal/deep link into mobile app. | ✓ |
| Env-configured link + provider template | Backend passes token to a provider-hosted template. Couples content to a specific provider. | |
| Hosted web reset page | Link points to a backend/web-served reset page. Requires a web page that doesn't exist. | |

**User's choice:** Env-configured link + backend template
**Notes:** No web frontend exists; mobile app is a separate repo, so the link is a universal/deep link and the body is owned in this repo to keep the provider swappable.

---

## Provider scope this phase

| Option | Description | Selected |
|--------|-------------|----------|
| Abstraction + no-op/logging only | Ship the `EmailSender` seam + logging/no-op provider (satisfies EMAIL-02). Defer concrete provider. | ✓ |
| Abstraction + one real provider | Also wire a concrete provider now; requires picking one + managing its API key. | |

**User's choice:** Abstraction + no-op/logging only
**Notes:** Matches the transactional-email seed — first consumer builds the abstraction, concrete provider deferred so Phase 10 ships without a provider account/secret.

---

## Rate-limiting approach

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse per-IP filter + per-email guard | Existing `RateLimitFilter` covers `/api/auth/*` per-IP; add a lightweight per-email Bucket4j guard in the service. | ✓ |
| Reuse per-IP filter as-is only | forgot-password is already covered per-IP; add nothing. Leaves a single address bomb-able from many IPs. | |

**User's choice:** Reuse per-IP filter + per-email guard
**Notes:** REQUIREMENTS "Out of Scope" mandates reusing existing Bucket4j rather than new infra; per-email guard reuses the library, not new infrastructure.

---

## Reset response + session

| Option | Description | Selected |
|--------|-------------|----------|
| 30 min TTL, 202, force re-login | forgot-password 202 generic; reset-password 200 with no tokens (all refresh tokens revoked). | ✓ |
| 15 min TTL, 200, force re-login | Tighter TTL, plain 200 responses; still forces fresh login. | |
| 30 min TTL, 202, auto-login | reset-password returns a fresh session so user is logged in immediately. | |

**User's choice:** 30 min TTL, 202, force re-login
**Notes:** Conservative — since all sessions are revoked on reset, forcing a fresh login is consistent; 30 min balances usability and security.

## Claude's Discretion

- Reset-token entity/table/column layout and hashing helper (mirror existing password/refresh-token patterns).
- Per-email bucket capacity/refill values and dedicated forgot-password IP capacity.
- Package/file placement for `EmailSender` (mirror `push/service`).

## Deferred Ideas

- Concrete transactional email provider selection + wiring — deferred per the seed.
- Email verification (Phase 11) and logged-in credential changes (Phase 12) reuse this email infrastructure — separate phases.
