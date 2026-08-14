# Phase 11: Email Verification - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-11
**Phase:** 11-email-verification
**Areas discussed:** Register/verify token behavior, Login-gate error contract, Resend flow semantics, Token model & verify endpoint shape

---

## Register/verify token behavior

### Q1 — What should POST /register return under a hard gate?

| Option | Description | Selected |
|--------|-------------|----------|
| 201, no tokens | Creates unverified user, sends verification email, returns 201 + generic "check your email"; no tokens. Consistent with the gate; mirrors Phase 10 no-token reset. | ✓ |
| Keep returning tokens | Register still auto-logs-in — defeats the hard gate for new users. | |
| Tokens but marked unverified | Return tokens, reject unverified at downstream endpoints — much larger blast radius. | |

**User's choice:** 201, no tokens
**Notes:** Keeps the hard gate meaningful — an unverified user must not hold a session.

### Q2 — What should the verify endpoint return?

| Option | Description | Selected |
|--------|-------------|----------|
| 200, no tokens — then log in | Marks verified, returns 200, user logs in normally. Mirrors Phase 10 reset-password. | ✓ |
| Auto-login — return tokens | Verify mints access+refresh tokens. Smoother UX but larger security surface; diverges from reset pattern. | |

**User's choice:** 200, no tokens — then log in
**Notes:** One consistent "sensitive action → fresh login" pattern across reset and verify.

---

## Login-gate error contract

### Q1 — Response when an unverified user submits CORRECT credentials at login?

| Option | Description | Selected |
|--------|-------------|----------|
| 403 + EMAIL_NOT_VERIFIED code | Gate evaluated after password check; distinct 403 RFC-7807 problem so the app can route to resend. Wrong password stays generic 401. | ✓ |
| Generic 401 always | Zero enumeration signal but no hint for the legitimate user. | |
| 403 before password check | Leaks which emails are registered-but-unverified to anyone. | |

**User's choice:** 403 + EMAIL_NOT_VERIFIED code
**Notes:** Password-first ordering keeps the enumeration surface minimal while giving good UX.

---

## Resend flow semantics

### Q1 — Enumeration safety of POST /resend-verification?

| Option | Description | Selected |
|--------|-------------|----------|
| Enumeration-safe generic 202 | Always generic 202 regardless of unknown/verified/rate-limited. Mirrors forgot-password. | ✓ |
| Distinct responses | Friendlier for the app but reintroduces the enumeration signal Phase 10 avoided. | |

**User's choice:** Enumeration-safe generic 202

### Q2 — Rate limiting for resend?

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror forgot-password | Add to RateLimitFilter AUTH_PATHS (per-IP) + per-email Bucket4j guard; silent skip on exhaustion. | ✓ |
| Per-email only | Service-layer guard only; no per-IP protection. | |

**User's choice:** Mirror forgot-password
**Notes:** Two-layer approach, reuses existing Bucket4j — no new infra.

---

## Token model & verify endpoint shape

### Q1 — How to store verified state on users?

| Option | Description | Selected |
|--------|-------------|----------|
| verified_at timestamp (nullable) | Nullable email_verified_at TIMESTAMPTZ; audit-friendly, matches existing timestamp style. | ✓ |
| email_verified boolean | Simpler but loses the "when" and diverges from timestamp style. | |

**User's choice:** verified_at timestamp (nullable)

### Q2 — HTTP shape of the verify endpoint?

| Option | Description | Selected |
|--------|-------------|----------|
| POST with token in body | POST /api/auth/verify-email { token }; consistent with reset-password, keeps token out of URLs/logs. | ✓ |
| GET with token in query | Link hits API directly, but no web frontend and tokens land in logs. | |

**User's choice:** POST with token in body

### Q3 — Verification token TTL?

| Option | Description | Selected |
|--------|-------------|----------|
| 24 hours | Less time-sensitive than reset; reduces expired-link friction; env-configurable. | ✓ |
| 30 minutes (match reset) | Tighter window, more expired-link friction for signup. | |
| You decide | Planner picks a bounded default in the 1h–48h range. | |

**User's choice:** 24 hours

---

## Claude's Discretion

- Exact Flyway version numbers/ordering (token table + `users` column + grandfather backfill).
- Grandfather backfill value (`NOW()` vs each row's `created_at`).
- Package/file placement (mirror Phase 10 layout).
- Per-email/per-IP resend bucket capacity/refill defaults.
- Whether the register verification-email send is synchronous or off-thread.
- The `app.verify-email-url` config key name and email renderer copy.

## Deferred Ideas

- Change email / change password while logged in (ACCT-01..05) — Phase 12.
- Concrete transactional-email provider selection + wiring — still deferred per `transactional-email-infra.md`.
