# Phase 16: Invite-Only Access & Referral - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-29
**Phase:** 16-invite-only-access-referral
**Areas discussed:** Operator issuance mechanism, Code lifecycle & attributes, Register flow & error contract, Referral attribution model

---

## Operator issuance mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Shared-secret admin endpoint | POST endpoint guarded by a static secret header (X-Admin-Token) matched against `app.invite.admin-token`; mirrors config style; testable; gives an API surface Phase 17 reuses | ✓ |
| Bootstrap seed on startup | ApplicationRunner generates N codes at boot and logs them; zero HTTP surface but no re-issue without restart | |
| Manual / out-of-band only | No endpoint; operator inserts hashed codes via SQL/CLI; least code, awkward, untestable | |

**User's choice:** Shared-secret admin endpoint.

| Option | Description | Selected |
|--------|-------------|----------|
| Single code per call | One request → one code returned; simplest contract | ✓ |
| Batch (count param) | One request with a count → N codes returned | |

**User's choice:** Single code per call.

| Option | Description | Selected |
|--------|-------------|----------|
| Public route + secret check | Whitelist route in SecurityConfig (permitAll), validate X-Admin-Token in controller/service; generic 401/403 on failure | ✓ |
| Dedicated security filter | Spring filter granting an ADMIN authority; more "correct" but introduces a role concept the app lacks | |
| You decide | Follow existing conventions | |

**User's choice:** Public route + secret check.
**Notes:** No admin role/JWT user exists in the app today; SecurityConfig only distinguishes public vs. authenticated.

---

## Code lifecycle & attributes

| Option | Description | Selected |
|--------|-------------|----------|
| No expiry (MVP) | Codes valid until consumed; single-use bounds abuse | ✓ |
| Configurable TTL | Codes expire after `app.invite.ttl-days`; expired == consumed error | |

**User's choice:** No expiry (MVP).

| Option | Description | Selected |
|--------|-------------|----------|
| Defer revocation | No revoke endpoint now; single-use + controlled issuance limits blast radius | ✓ |
| Include revoke now | Add a revoke action to kill a leaked/mis-sent code before use | |

**User's choice:** Defer revocation.
**Notes:** Locked implicitly — single-use (INV-04); raw code returned once on creation, stored hashed at rest (SecureRandom high-entropy).

---

## Register flow & error contract

| Option | Description | Selected |
|--------|-------------|----------|
| Ignore the code entirely | When gate OFF, inviteCode not read/consumed, no referral | ✓ |
| Consume + attribute if present | Even when gate off, a valid code is consumed + referral recorded | |

**User's choice:** Ignore the code entirely.

| Option | Description | Selected |
|--------|-------------|----------|
| 403 + generic code, all identical | Invalid, consumed, and missing-when-gated all return same generic 403 body | ✓ |
| 422 like the age gate | Mirror the Phase 15 under-age gate: 422 + distinct code | |
| Distinct missing vs invalid | Missing → 400 Bean-Validation; invalid/consumed → generic 403/422 | |

**User's choice:** 403 + generic code, all identical.
**Notes:** Composition order (age → invite) carried forward from Phase 15; not re-asked.

---

## Referral attribution model

| Option | Description | Selected |
|--------|-------------|----------|
| Optional referrer_user_id on invite | Admin create accepts optional referrer; bootstrap = null; referrals row written at consumption only when referrer present | ✓ |
| referrals row always, referrer nullable | Always write a row at consumption, referrer_id nullable | |
| Defer referrer entirely | No referrer concept this phase | |

**User's choice:** Optional referrer_user_id on invite.

| Option | Description | Selected |
|--------|-------------|----------|
| Validate referrer exists | Reject issuance (400) if referrer_user_id doesn't match a real user | ✓ |
| Store as-is | Trust the operator; no existence check | |

**User's choice:** Validate referrer exists.

---

## Claude's Discretion

- Exact invite code format/length/entropy (URL-safe SecureRandom); `invites` / `referrals` table column shapes, types, indexes.
- Invite exception class name, generic `code` string, and RFC 7807 copy for the 403; the generic 401/403 shape for a bad admin secret.
- Admin endpoint path/DTO naming; `InviteService` method signatures; `com.catspell.api.invite` package placement.
- Transaction boundary keeping invite consumption + user creation atomic (no orphan account).
- Whether `app.invite.admin-token` has a dev default vs. is required (fail-fast).

## Deferred Ideas

- Member-generated invites, per-member quotas, referral rewards (nullable referrer_user_id is the forward hook).
- Invite code expiry/TTL; operator revocation of unused codes.
- Waitlist → invite conversion + emailing the code (Phase 17).
- Referral tracking while gate OFF; a general admin role/RBAC; batch code issuance.
