# Phase 12: Account Credentials - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-17
**Phase:** 12-account-credentials
**Areas discussed:** Session revocation scope, Pending-email storage & confirm flow, New-email-taken & enumeration, Wrong-password & rate limiting

---

## Session revocation scope

| Option | Description | Selected |
|--------|-------------|----------|
| Revoke ALL (re-login everywhere) | Reuse `revokeAllUserTokens` + the reset-password "fresh login" pattern. Simplest, consistent with Phases 10/11. Current device also logged out. | ✓ |
| Keep current session alive | Client passes its current refresh token; revoke all EXCEPT that one. Honors ACCT-02 "other" literally; needs a new revoke-others helper + endpoint takes the refresh token. | |

**User's choice:** Revoke ALL (re-login everywhere)
**Notes:** The access-token principal carries only the userId, so the server can't identify the caller's own refresh token. Revoke-all is stricter than ACCT-02's "other" wording but matches the established sensitive-action → fresh-login convention.

---

## Pending-email storage & confirm flow

| Option | Description | Selected |
|--------|-------------|----------|
| New table + new endpoint | Dedicated `email_change_requests` table (user_id, new_email, token_hash, expires_at, used_at) + new `POST /api/auth/confirm-email-change`. Cleanest separation; renderer targets the NEW address. | ✓ |
| Extend verification token table | Add a `new_email` column to `email_verification_tokens` and reuse `/verify-email`. Less new code, overloads signup-verify semantics. | |
| pending_email column on users | Store pending email on the users row + new confirm endpoint. Simplest schema, pollutes the core users table. | |

**User's choice:** New table + new endpoint
**Notes:** Follow-up locked that `confirm-email-change` is public/token-only (works logged-out / other device), and that a successful confirm swaps `users.email`, stamps `email_verified_at`, and revokes all sessions.

---

## New-email-taken & enumeration

| Option | Description | Selected |
|--------|-------------|----------|
| Immediate 409 Conflict | Reuse `DuplicateEmailException`. Clear feedback for the logged-in user; low enumeration risk (authenticated + rate-limited). | ✓ |
| Enumeration-safe generic | Always return generic response, silently send nothing if taken. Hides existence but gives no feedback on why the change fails. | |

**User's choice:** Immediate 409 Conflict
**Notes:** For a logged-in user changing their own email, clear feedback outweighs the bounded enumeration risk.

---

## Wrong-password & rate limiting

| Option | Description | Selected |
|--------|-------------|----------|
| Distinct 403 + code | New exception → 403 with `INVALID_CURRENT_PASSWORD`, mirroring Phase 11's `EMAIL_NOT_VERIFIED`. Reads better than 401 on an authenticated request. | ✓ |
| Reuse 401 InvalidCredentials | Reuse the existing login exception (401). Least new code, but semantically odd on an authenticated request. | |
| 400 Bad Request | Treat as a validation-style failure. Loses credential-mismatch semantics. | |

**User's choice:** Distinct 403 + code (`INVALID_CURRENT_PASSWORD`)
**Notes:** Applies to both change-password and change-email. Rate limiting left to Claude's discretion — mirror the existing per-IP `AUTH_PATHS` + per-target-email Bucket4j guard.

---

## Claude's Discretion

- Exact endpoint names/paths for the two authenticated routes (suggested `/api/auth/change-password`, `/api/auth/change-email`).
- Rate limiting on the new routes (per-IP `AUTH_PATHS` + per-target-email Bucket4j guard on change-email; capacities/refill + config-key names).
- Change-email confirmation token TTL and the `app.confirm-email-change-url` deep-link key + renderer copy.
- Whether the change-email request requires the account's current email to already be verified.
- Flyway V18 split/DDL details for `email_change_requests`.
- Whether to expose/rename the currently-private `revokeAllUserTokens` / `createRefreshToken` or add dedicated methods.

## Deferred Ideas

- Revoke-a-specific-device / session-management surface (the "keep current session" alternative) — its own phase.
- Stronger password-strength rules beyond `@Size(min=8)`.
- Concrete transactional-email provider selection + wiring (still deferred per seed).
- 2FA/SMS, account deletion — v2 requirements.
