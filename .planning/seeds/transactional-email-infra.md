---
title: Transactional Email Infrastructure
trigger_condition: When real email delivery is needed in a running/production
  environment (currently the ONLY EmailSender is a no-op logger). Concretely
  deferred by Phase 11 UAT — see 11-UAT.md Deferred Follow-Ups / 11-VERIFICATION.md
  Acknowledged Gaps.
planted_date: 2026-08-07
updated: 2026-08-13
status: abstraction-built — real provider still pending
---

# Transactional Email Infrastructure

## Idea

The `EmailSender` abstraction now EXISTS (built during Phases 10/11), but there
is still **no real email transport**. The only implementation is the no-op
`LoggingEmailSender` (`email.enabled=false` default), which just logs a masked
recipient + subject and sends nothing. Setting `email.enabled=true` currently
leaves no `EmailSender` bean, so the app would fail to start — there is no
real-sender branch yet.

Consumers already wired to the seam:
- Password reset links (Phase 10)
- Email address verification on registration (Phase 11)
- Future: security/account-change notifications, digest/re-engagement emails

## What remains (the actual follow-up)

- Implement a real `EmailSender` behind the existing interface
  (`src/main/kotlin/com/catspell/api/email/service/EmailSender.kt`), selected via
  `@ConditionalOnProperty(email.enabled=true)` alongside the existing
  `LoggingEmailSender` (`havingValue=false, matchIfMissing=true`).
- Choose a **managed transactional provider** (SendGrid / Postmark / Resend /
  AWS SES) — deliverability over raw SMTP.
- Config/secret management for the provider API key via env (see `.env.example`).
- Keep the test double (no real network in tests), mirroring push (`FcmSmokeTest`).
- Once shipped, re-run `/gsd-verify-work 11` to close UAT Test 2 (real email +
  deep link) and revisit the OPT-OUT rows in `11-COVERAGE.md`.

## Why a seed (not a phase yet)

The concrete provider choice is still deferred. The abstraction + renderers are
done; a future phase implements one real provider and every existing consumer
reuses it unchanged. Revisit provider selection at that point.

## Related

- Note: `password-recovery-design.md` — first consumer of this infrastructure.
- Phase 11 deferral: `.planning/phases/11-email-verification/11-UAT.md`
  (Deferred Follow-Ups) and `11-VERIFICATION.md` (Acknowledged Gaps);
  `11-COVERAGE.md` (external email providers OPT-OUT).
