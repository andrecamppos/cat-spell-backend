---
title: Password Recovery Design
date: 2026-08-07
context: Captured during /gsd-explore session. Design decisions for adding a
  forgot-password / reset-password flow to the existing JWT auth system
  (AuthService, User entity). No email-sending capability exists in the backend
  yet, so this feature introduces a transactional email dependency.
---

# Password Recovery Design

## Goal

Let a user who has forgotten their password regain access to their account
without support intervention, via a self-service email-based reset flow.

## Delivery mechanism

**Email a tokenized reset link** (e.g. `/reset-password?token=...`). Chosen over
email OTP codes and push notifications:

- Push was rejected because it only reaches a device where the user is *already
  logged in* — useless for someone locked out.
- Email link is the classic, well-understood flow and works for any user.

## Email infrastructure (new dependency)

The backend currently has **no transactional email capability**. This feature
requires introducing one.

- Use a **managed transactional email provider** (e.g. SendGrid, Postmark,
  Resend, or AWS SES) rather than raw SMTP, for deliverability and simplicity.
- Wrap sending behind an application-level abstraction (e.g. `EmailSender`
  interface) so the concrete provider can be swapped and so tests can stub it.
- See seed: `transactional-email-infra.md` — the email capability is broader than
  password recovery (verification emails, etc.).

## Reset token model (secure defaults)

- **High-entropy random token** (not a JWT, not guessable).
- **Stored hashed** in the DB (same principle as password hashing) — the raw
  token only ever lives in the emailed link.
- **Single-use**: invalidated immediately after a successful reset.
- **Short TTL**: ~15–30 minutes.
- On **successful reset**, revoke **all** of the user's refresh tokens
  (`revokeAllUserTokens` already exists in `AuthService`) so any hijacked
  sessions are logged out.

## Enumeration protection

The `POST /forgot-password` endpoint **always returns the same generic response**
("if an account exists, we've sent an email") regardless of whether the email is
registered. This prevents attackers from using the endpoint to discover which
emails have accounts.

## Abuse / rate limiting

The forgot-password endpoint triggers outbound email and is a classic abuse
vector (email bombing, provider cost). It **must be rate-limited** — per-email
and/or per-IP. Confirm whether existing infrastructure provides a rate-limiting
mechanism to reuse, or whether one needs to be added.

## Proposed API surface

- `POST /api/auth/forgot-password` — body `{ email }`; always generic 200/202.
- `POST /api/auth/reset-password` — body `{ token, newPassword }`; validates
  token (exists, not expired, not used), updates `passwordHash`, marks token
  used, revokes refresh tokens.

## Open questions

- Which transactional provider? (deferred — abstraction first)
- Does a rate-limiting mechanism already exist to reuse, or build new?
- Email template/branding ownership (backend-rendered vs. provider template).
