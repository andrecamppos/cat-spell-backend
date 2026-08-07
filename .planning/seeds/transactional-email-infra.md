---
title: Transactional Email Infrastructure
trigger_condition: When password recovery is planned, or when any feature needs
  to send email to users (email verification, security alerts, digest/notification
  emails, account changes).
planted_date: 2026-08-07
---

# Transactional Email Infrastructure

## Idea

The backend has **no email-sending capability** today. Multiple features will
need one — password recovery is the first concrete driver, but this is broader:

- Password reset links (immediate need)
- Email address verification on registration
- Security/account-change notifications
- Potential digest or re-engagement emails (complements existing push infra)

Rather than bolt email onto the password-recovery feature only, stand up a
**reusable email capability**:

- A single `EmailSender` abstraction in the app so callers don't depend on a
  specific provider.
- A **managed transactional provider** behind it (SendGrid / Postmark / Resend /
  AWS SES) — chosen for deliverability over raw SMTP.
- A test double (no real network in tests), mirroring how push delivery is
  tested (see `FcmSmokeTest`).
- Config/secret management for the provider API key via env (see `.env.example`).

## Why a seed (not a phase yet)

The concrete provider choice is deferred. Whichever feature ships first
(password recovery likely) should build the abstraction; later email features
reuse it. Revisit provider selection at that point.

## Related

- Note: `password-recovery-design.md` — first consumer of this infrastructure.
