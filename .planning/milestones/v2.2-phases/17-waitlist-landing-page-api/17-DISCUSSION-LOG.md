# Phase 17: Waitlist / Landing-Page API - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-01
**Phase:** 17-waitlist-landing-page-api
**Areas discussed:** Confirm-link response, Dedupe & re-join behavior, Operator conversion flow, Anti-abuse & captured fields

---

## Confirm-link response

| Option | Description | Selected |
|--------|-------------|----------|
| 302 redirect to web URL | Redirect to a configurable landing-page success URL (+ error URL for bad/expired token). Mirrors app.*-url config, web-style. | ✓ |
| JSON 200/4xx | Return a plain JSON body; landing page calls the API via fetch. | |
| catspell:// deep link | Reuse the mobile deep-link style (verify-email). | |

**User's choice:** 302 redirect to web URL
**Notes:** Landing page is a separate web repo; the confirm link is clicked in a browser, not the app.

---

## Dedupe & re-join behavior

### Re-join behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Resend if pending, no-op if confirmed | Unconfirmed re-join invalidates prior token + sends fresh confirm email; already-confirmed does nothing. | ✓ |
| Always silent no-op | Never resend; first join is the only confirm email. | |
| Always resend | Resend on every join regardless of state. | |

**User's choice:** Resend if pending, no-op if confirmed (enumeration-safe identical 202 either way)

### Email normalization

| Option | Description | Selected |
|--------|-------------|----------|
| Trim + lowercase only | Consistent with existing register/login/bucket conventions. | |
| Also strip +suffix (all domains) | Lowercase, trim, drop everything after '+' in the local part for the dedupe key. | ✓ |
| Full gmail normalization | Strip +suffix for all + remove dots for gmail/googlemail. | |

**User's choice:** Also strip +suffix (all domains)
**Notes:** Closes the most common per-email-limit bypass (Pitfall 5) without risky provider-specific dot rules.

---

## Operator conversion flow

### Convert endpoint shape

| Option | Description | Selected |
|--------|-------------|----------|
| By id + list endpoint | POST /api/admin/waitlist/{id}/invite + GET /api/admin/waitlist?status=confirmed, both behind X-Admin-Token. | ✓ |
| By id, no list | Only the convert endpoint; ids obtained out-of-band. | |
| By email, no list | POST /api/admin/waitlist/invite {email}. | |

**User's choice:** By id + list endpoint
**Notes:** Operator needs to see the confirmed list; id keeps the raw email out of the request path.

### Entry state after conversion

| Option | Description | Selected |
|--------|-------------|----------|
| Mark INVITED, reject re-convert | status=INVITED on success; a second convert returns 409/400. | ✓ |
| Mark INVITED, re-convert resends | Re-converting issues a fresh invite + re-emails. | |
| You decide | Leave to planning. | |

**User's choice:** Mark INVITED, reject re-convert

---

## Anti-abuse & captured fields

| Option | Description | Selected |
|--------|-------------|----------|
| Email only, defer disposable filter | Capture email only; skip disposable-domain filtering (double opt-in + rate limiting suffice). | ✓ |
| Email + disposable filter | Add a config-driven disposable-domain blocklist now. | |
| Capture extra fields too | Also store optional info (name / referral source). | |

**User's choice:** Email only, defer disposable filter

---

## Confirmation token TTL

| Option | Description | Selected |
|--------|-------------|----------|
| 7 days | Long window maximizes confirmations for a low-risk launch list. | ✓ |
| 24 hours | Matches existing email-verification TTL. | |
| 72 hours | Middle ground. | |

**User's choice:** 7 days

---

## Claude's Discretion

- Exact `waitlist_entries` columns/types/indexes and status state-machine representation (subject to the locked constraints).
- `app.waitlist.*` config key names (confirm success/error URLs, TTL, rate-limit capacities).
- Service method signatures, DTO/package naming (`com.catspell.api.waitlist`), transaction boundaries.
- Whether to store the raw-submitted email alongside the normalized dedupe key.
- Exception class names / RFC 7807 copy for convert-state violations (reuse `AdminAuthException` for admin-auth).
- Whether the convert response returns the raw invite code.

## Deferred Ideas

- Disposable-domain filtering (optional per WAIT-03) — deferred.
- Optional join fields (name / referral source) — deferred; email only.
- Waitlist position / referral leaderboard (WAIT2-01) — v2.
- Referral attribution from waitlist conversions — declined (organic, no referrer).
- Idempotent re-convert / re-send of an already-invited entry — declined.
- Full gmail-style dot normalization — declined.
- Landing-page frontend — separate repo.
