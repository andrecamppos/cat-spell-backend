---
title: Third-Party Age / Identity Verification
trigger_condition: When self-attested DOB is no longer sufficient — legal/compliance
  pressure, an under-18 incident, app-store or payment-provider requirements, or
  scaling into jurisdictions that mandate verified age for dating apps.
planted_date: 2026-09-24
---

# Third-Party Age / Identity Verification

## Idea

The launch age gate is **self-attested DOB** (collect date of birth at signup,
hard-block under-18, trust the user). This is the common MVP path but provides no
real assurance — a determined minor can lie.

Add a **third-party age/identity verification** step (e.g. Persona, Veriff, Yoti,
Stripe Identity) that performs document/liveness or database-backed age checks,
gating full account activation behind a verified-adult status.

## Why Deferred

- Self-attested DOB is enough to launch and validate the product.
- Vendor integration adds cost, signup friction, PII handling/retention burden,
  and a compliance surface that isn't justified pre-launch.

## When To Revisit

- Legal/regulatory requirement for verified age (jurisdiction-dependent).
- An under-18 access incident or trust-and-safety escalation.
- App store or payment provider requires it.
- Scale makes self-attestation an unacceptable liability.

## Notes

- Keep the age-gate seam abstracted so a vendor can slot in without reworking the
  signup flow — mirror the existing `EmailSender` / `PushProvider` provider pattern.
- Treat verification status as a distinct field/state (e.g. `age_verified`,
  `verified_at`, `method`), separate from the self-attested DOB.
- Mind PII: minimize what's stored, prefer vendor-hosted verification with a
  pass/fail + token returned rather than storing ID documents.
