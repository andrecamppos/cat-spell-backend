# Phase 15: Age Verification - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-28
**Phase:** 15-age-verification
**Areas discussed:** DOB placement & gate point, DOB storage & discovery filter, Existing-account migration, Under-18 error contract

---

## Areas selected for discussion

All four proposed gray areas were selected:

| Option | Description | Selected |
|--------|-------------|----------|
| DOB placement & gate point | Where the 18+ gate fires (register vs profile completion) | ✓ |
| DOB storage & discovery filter | Where DOB is stored; reconciling discovery SQL + ProfileService | ✓ |
| Existing-account migration | AGE-03 rollout / backfill / grandfather | ✓ |
| Under-18 error contract | Status code + machine-readable code | ✓ |

---

## DOB placement & gate point

| Option | Description | Selected |
|--------|-------------|----------|
| At /register (true hard gate) | Add dateOfBirth to RegisterRequest; AgeVerifier.requireAdult runs before the account row is created, so no under-18 account exists in the DB. Research-recommended. | ✓ |
| At profile completion (weaker) | Keep DOB on user_profiles; under-18 can create a users row but is blocked from completing a profile. Weaker guarantee. | |

**User's choice:** At /register (true hard gate)
**Notes:** Resolves research Pitfall 3 with the stronger option — no under-18 account ever persists.

---

## DOB storage & discovery filter

| Option | Description | Selected |
|--------|-------------|----------|
| Move to users, drop from profiles | Single source of truth on users; update SwipeRepository JOINs + DiscoveryService/ProfileService to read users.date_of_birth. | ✓ |
| Users canonical, keep profile copy | Authoritative DOB on users; keep a synced user_profiles copy so discovery SQL/profile code stay untouched. | |
| Users only, profile stops taking DOB | DOB on users only; remove from profile DTOs entirely. | (folded into follow-up) |

**Follow-up — DOB after signup:**

| Option | Description | Selected |
|--------|-------------|----------|
| Immutable after signup | Remove dateOfBirth from both create and update profile DTOs; set once at register, never editable. | ✓ |
| Editable via profile, re-gated | Keep DOB editable via profile update but re-run AgeVerifier on every change. | |

**User's choice:** Move to users + drop from profiles; DOB immutable after signup.
**Notes:** Single source of truth on `users`; old profile-time write path and DOB-on-profile column both removed. Prevents post-signup age tampering.

---

## Existing-account migration

| Option (no-DOB accounts) | Description | Selected |
|--------|-------------|----------|
| Grandfather (nullable, no gate) | Keep users.date_of_birth nullable; existing no-DOB accounts stay usable, only new signups gated. Mirrors Phase 11 email-verification grandfather. | ✓ |
| Force DOB collection later | Require these accounts to supply a DOB (re-gated) at next login/profile step. | |

**Follow-up — existing under-18 rows:**

| Option | Description | Selected |
|--------|-------------|----------|
| Backfill as-is, no special action | Trust the prior profile-creation 18+ check; just copy DOBs over. | ✓ |
| Audit & flag under-18 | Migration explicitly checks DOB < 18 and flags for the operator. | |

**User's choice:** Grandfather no-DOB accounts (nullable, no gate); backfill as-is with no special under-18 handling.
**Notes:** No lockout on rollout (AGE-03). Under-18 completed profiles not expected since the profile-time gate predates this phase.

---

## Under-18 error contract

| Option (status) | Description | Selected |
|--------|-------------|----------|
| 422 Unprocessable Entity | Payload valid but semantically rejected by the age rule; distinct from 400 validation errors. | ✓ |
| 400 Bad Request | Treat under-18 as a request validation failure. | |

| Option (error code) | Description | Selected |
|--------|-------------|----------|
| Distinct code | e.g. UNDER_MINIMUM_AGE in the RFC 7807 body so the app can route to a tailored message. | ✓ |
| Generic validation error | Standard validation shape, no dedicated code. | |

**User's choice:** 422 + distinct machine-readable code.
**Notes:** Mirrors the EMAIL_NOT_VERIFIED (403) precedent that drives the app's resend screen. Old ProfileService.validateAge (generic 400) is removed.

---

## Claude's Discretion

- `AgeVerifier` interface/method shape, package, throwing-vs-result contract, minimum-age constant vs. `app.age.minimum-age` config key.
- Under-18 exception class name + exact `code` string + RFC 7807 copy.
- The SwipeRepository/DiscoveryService/ProfileService rewrite to source DOB from `users` (behavior-preserving); `users.date_of_birth` column type/index.
- DOB validation beyond the age gate (future dates, absurd ages) and clock/timezone basis for the 18th-birthday boundary.

## Deferred Ideas

- Third-party age-estimation / ID-verification vendor (drop-in via the AgeVerifier seam when legally required).
- Forcing grandfathered no-DOB accounts to supply a DOB later.
- Auditing/flagging/suspending pre-existing under-18 accounts during migration.
- Configurable minimum age per market / jurisdiction routing.
