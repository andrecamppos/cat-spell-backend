# Phase 17: Waitlist / Landing-Page API - Context

**Gathered:** 2026-10-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Capture launch demand via a **public, unauthenticated, double-opt-in waitlist** that the operator can convert into invites — the backing API for the separate-repo landing page. A public `POST /api/waitlist` accepts an email and persists a `PENDING` entry + a **hashed single-use, time-limited confirmation token**, then emails a confirm link via the existing `EmailSender` seam, always returning an **identical enumeration-safe `202`** for new vs duplicate emails. Clicking the emailed link (`GET /api/waitlist/confirm?token=...`) marks the entry `CONFIRMED` via an atomic single-use claim. The endpoint is throttled with **per-IP + per-email Bucket4j limits** (reusing `RateLimitFilter`) over a **normalized email**. The operator, behind the existing **`X-Admin-Token` shared secret**, can list confirmed entries and convert one into an invite (`InviteService.create`) which is emailed to the address. Introduces a `waitlist_entries` table via Flyway **V24** and a new `waitlist/` domain package.

**In scope:** public `POST /api/waitlist` join, enumeration-safe `202` (WAIT-01); double opt-in via SHA-256 hashed, single-use, 7-day-TTL confirm token + `GET /api/waitlist/confirm` (WAIT-02); per-IP + per-email rate limiting with email normalization (WAIT-03); operator convert-to-invite + confirmed-list endpoints behind `X-Admin-Token`, emailing the invite (WAIT-04); V24 `waitlist_entries` table; `waitlist/` package.

**Out of scope (own phases / deferred):** the landing-page frontend (separate repo); disposable-domain filtering (WAIT-03 "optional" — deferred this phase); capturing optional profile info beyond email at join (deferred — email only); waitlist position / referral leaderboard (WAIT2-01, v2 requirement); referral attribution from waitlist conversions (converted invites are organic/bootstrap-style, no referrer); re-convert of an already-invited entry (rejected, not idempotent re-send).

</domain>

<decisions>
## Implementation Decisions

### Confirmation link response (WAIT-02)
- **D-01:** `GET /api/waitlist/confirm?token=...` responds with a **`302` redirect to a configurable web landing-page URL** — a success URL on a valid single-use claim and a separate error URL on an invalid/expired/already-used token. The landing page is a separate **web** repo and the link is clicked in a browser, so a redirect (not JSON, not a `catspell://` deep link) is the right fit. Model the config keys on the existing `app.*-url` convention (e.g. `app.waitlist.confirm-success-url` / `app.waitlist.confirm-error-url`); exact key names are Claude's discretion.
- **D-02:** The `catspell://` deep-link style used by `verify-email`/`confirm-email-change` is **explicitly rejected** here — waitlist clicks happen on web before the user has the app.

### Join / dedupe / re-join behavior (WAIT-01, WAIT-03)
- **D-03:** **Email normalization = trim + lowercase + strip the `+suffix`** from the local part (all domains) for the **dedupe/unique key and the per-email rate-limit bucket key**. This closes the most common per-email-limit bypass (Pitfall 5, `a+tag@`) without provider-specific dot rules. Full gmail-style dot-stripping was rejected as risky (merges distinct addresses at providers where dots are significant). Storing the raw-submitted address alongside the normalized key is Claude's discretion (existing register/login store verbatim; the normalized form is the dedupe/bucket key).
- **D-04:** **Re-join is state-dependent but always returns the identical enumeration-safe `202`:** an **unconfirmed (`PENDING`) re-join invalidates the prior token and sends a fresh confirm email** (mirrors `EmailVerificationService.issueAndSend` — invalidate prior unused tokens, mint + send one new token); an **already-`CONFIRMED` (or `INVITED`) re-join is a silent no-op** (no resend). The per-email rate-limit bucket bounds resend abuse. Callers cannot distinguish new / pending / confirmed.
- **D-05:** **Email only at join this phase** — no optional name / referral-source fields captured. WAIT-01's "optional info" is deferred; keeps the table + DTO minimal.

### Anti-abuse (WAIT-03)
- **D-06:** **Disposable-domain filtering is deferred** (WAIT-03 lists it as optional). Double opt-in (bots that can't confirm never reach `CONFIRMED`) + per-IP/per-email Bucket4j throttling are sufficient anti-abuse for launch; a config-driven blocklist can be added later if abuse appears.
- **D-07:** Reuse the existing **`RateLimitFilter`** (Bucket4j, per-IP) and add a **per-email bucket** in the service layer (mirroring `EmailVerificationService.emailBucket`) keyed on the normalized email (D-03). Add the public `POST /api/waitlist` path to the filter's throttled-path set. Exact capacities/refill windows are Claude's discretion, following existing `rate-limit.*` / `app.resend-verification.*` conventions.

### Confirmation token (WAIT-02)
- **D-08:** The confirm token reuses the **v2.1 hashed single-use token model** — SHA-256 at rest, raw token embedded only in the outbound email link (never persisted/logged), atomic single-use claim on confirm (conditional update, 0 rows → treated as invalid/expired → redirect to error URL). **TTL = 7 days** (lower-security than password reset/verify; a long window maximizes confirmation rate for a launch list).

### Operator conversion (WAIT-04)
- **D-09:** The operator converts via **`POST /api/admin/waitlist/{id}/invite`** (entry identified by its **DB id**, not email — keeps the raw email out of the request path) **plus a `GET /api/admin/waitlist?status=confirmed`** list endpoint so the operator can see who to convert. Both sit behind the **existing `X-Admin-Token` shared-secret guard** (reuse the exact deny-by-default, constant-time `MessageDigest.isEqual` pattern from `InviteAdminController.requireValidAdminToken`; same `app.invite.admin-token` key). No admin role/RBAC introduced.
- **D-10:** Conversion is only valid on a **`CONFIRMED`** entry: it calls **`InviteService.create(referrerUserId = null)`** (organic — no referral attribution), emails the invite code/link via `EmailSender` (new renderer, mirror the existing `*EmailRenderer` + `app.*-url` pattern), and **marks the entry `INVITED`**. A **second convert of an already-`INVITED` entry is rejected** (409/400 — not an idempotent re-send), and converting a non-`CONFIRMED` entry is likewise rejected. Exact exception/status + whether the convert response returns the raw invite code is Claude's discretion (note: `InviteService.create` returns the raw code once).

### Claude's Discretion
- Exact `waitlist_entries` column shapes/types/indexes (subject to: unique on normalized-email dedupe key, hashed confirm token, single-use claim, status enum `PENDING`/`CONFIRMED`/`INVITED`), and the status state machine representation.
- Config key names for the confirm success/error URLs and any `app.waitlist.*` keys (confirm TTL, rate-limit capacities), following existing `app.*` / `@Value` conventions (no `@ConfigurationProperties`, per AGENTS.md).
- Service method signatures (`WaitlistService.join` / `confirm` / `convertToInvite` or similar), DTO/package naming (`com.catspell.api.waitlist`), and the exact transaction boundaries (confirm claim; convert + mark-INVITED + invite-create atomicity).
- Whether to store the raw-submitted email in addition to the normalized key.
- Exception class names + RFC 7807 copy for admin-auth failures (reuse `AdminAuthException`), bad/expired confirm token (redirect, no 7807 body needed), and convert-state violations.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/ROADMAP.md` §"Phase 17: Waitlist / Landing-Page API" (~lines 152-165) — goal, 4 success criteria, note ("V19+" — actual next migration is **V24**)
- `.planning/REQUIREMENTS.md` — WAIT-01, WAIT-02, WAIT-03, WAIT-04 (the requirements this phase closes); §"Out of Scope" (landing-page frontend, new rate-limit infra); §"v2 Requirements" WAIT2-01 (position/leaderboard deferred)

### Milestone research (v2.2)
- `.planning/research/ARCHITECTURE.md` §"Pattern 3: Hashed single-use token reuse (waitlist confirm + invite accept)" (~lines 115-119); §"Waitlist → invite flow" data-flow diagram (~lines 137-147) — the exact join → confirm → operator-convert sequence; §"Component Responsibilities" / §"Recommended Project Structure" `waitlist/` package
- `.planning/research/PITFALLS.md` §"Pitfall 5: Waitlist public endpoint leaks membership or gets flooded" (~lines 85-100) — enumeration-safe `202`, per-IP + per-email Bucket4j throttle, email normalization, double opt-in, optional disposable-domain drop (drives D-01/D-03/D-04/D-06/D-07)
- `.planning/research/FEATURES.md` — "Waitlist join + double opt-in" (P1) + "Operator waitlist→invite conversion" (P2) rows (~lines 48-49, 103-104); dependency chain waitlist → conversion → invite → referral (~lines 44-52)
- `.planning/research/SUMMARY.md` / `.planning/research/STACK.md` — v2.2 overview; waitlist double-opt-in + hashed-single-use reuse

### Prior phase context (patterns this phase reuses)
- `.planning/phases/16-invite-only-access-referral/16-CONTEXT.md` — the `InviteService.create(referrerUserId?)` path (returns raw code once) and the `X-Admin-Token` shared-secret admin pattern this phase reuses for conversion
- `.planning/PROJECT.md` §"Key Decisions" — "Hashed single-use expiring tokens for all email flows" (Phases 10-12), "Provider-abstracted `EmailSender` seam", "Admin issuance behind a shared-secret `X-Admin-Token`" (Phase 16)

### In-repo code (read before modifying)
- `src/main/kotlin/com/catspell/api/invite/service/InviteService.kt` — `create(referrerUserId: UUID?): String` returns the raw code once (reused by convert, D-10); the SHA-256 `hashToken` + atomic `markConsumed` claim patterns to mirror for the confirm token (D-08)
- `src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt` — `requireValidAdminToken` deny-by-default constant-time `MessageDigest.isEqual` guard + `@Value("\${app.invite.admin-token:}")`; mirror for the waitlist admin endpoints (D-09)
- `src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt` — `issueAndSend` (invalidate prior unused tokens → mint SHA-256 hashed token → `EmailSender.send`) is the template for join/re-send (D-04); `emailBucket` per-email Bucket4j pattern (D-07); `generateRawToken`/`hashToken` helpers (D-08)
- `src/main/kotlin/com/catspell/api/email/service/EmailSender.kt` + `EmailVerificationEmailRenderer.kt` — the `EmailMessage`/`EmailSender` seam and `@Value("\${app.*-url}")` renderer pattern to mirror for the confirm + invite emails (D-01, D-10)
- `src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt` — `AUTH_PATHS` set + per-IP bucket + `/api/auth/*` registration; add `POST /api/waitlist` to the throttled set (D-07)
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` — public-endpoint `permitAll` whitelist (lines ~28-33); add `/api/waitlist`, `/api/waitlist/confirm`, and `/api/admin/waitlist/**` (D-09)
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` + `GlobalExceptionHandler.kt` — reuse `AdminAuthException`; add any waitlist convert-state exception mapping
- `src/main/resources/application.yml` §`app:` (lines ~38-48) — where `app.waitlist.*` keys are added; `email.enabled` (line 36) gates real sends
- `src/main/resources/db/migration/V23__create_invites_and_referrals.sql` — latest migration; the new migration is **V24**, never edit V1–V23
- `src/main/kotlin/com/catspell/api/invite/` — reference layout for a new domain package (controller/service/model); mirror for the new `waitlist/` package

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`InviteService.create(referrerUserId?): String`** — already issues a hashed-at-rest invite and returns the raw code once; conversion calls it with `null` (organic, D-10).
- **`InviteAdminController.requireValidAdminToken`** — deny-by-default, constant-time `X-Admin-Token` guard; the entire access boundary for the waitlist admin endpoints (D-09).
- **`EmailVerificationService.issueAndSend` + `emailBucket`** — the invalidate-prior → mint-hashed-token → send flow (D-04) and the per-email Bucket4j pattern (D-07) to copy for waitlist join/resend.
- **`EmailSender` seam + `*EmailRenderer` + `app.*-url` config** — for the confirm-link and invite emails (D-01, D-10); no network sends in dev/CI (`email.enabled=false`).
- **`RateLimitFilter`** — per-IP Bucket4j; add `POST /api/waitlist` to its throttled set (D-07).
- **`SecurityConfig` permitAll whitelist** — the pattern for exposing the public + admin waitlist routes while the app stays JWT-only elsewhere (D-09).
- **`invite/` domain package** — layout template for the new `waitlist/` package.

### Established Patterns
- Package-per-domain; Flyway append-only migrations (next is **V24**); JPA entities + Spring Data repositories; SHA-256 hashed single-use tokens with atomic conditional-update claim; enumeration-safe generic responses; per-IP (`RateLimitFilter`) + per-email (service `ConcurrentHashMap<String, Bucket>`) throttling; `@Value` custom `app.*` config keys (no `@ConfigurationProperties`, per AGENTS.md — "Unknown property" IDE warning is cosmetic); RFC 7807 errors; `@Transactional` service methods.
- Shared-secret `X-Admin-Token` admin surface on a `permitAll` route (no admin role) — established in Phase 16, reused here.

### Integration Points
- Public `POST /api/waitlist` → `WaitlistService.join(email)` → persist/upsert `PENDING` + hashed confirm token → `EmailSender(confirm link)`; `RateLimitFilter` (per-IP) + per-email bucket in front.
- `GET /api/waitlist/confirm?token=...` → `WaitlistService.confirm(token)` atomic single-use claim → `302` to success/error URL (D-01/D-08).
- `GET /api/admin/waitlist?status=confirmed` + `POST /api/admin/waitlist/{id}/invite` (X-Admin-Token) → `WaitlistService.convertToInvite(id)` → `InviteService.create(null)` + `EmailSender(invite)` + mark `INVITED` (D-09/D-10).
- `SecurityConfig` → whitelist the public + admin waitlist routes.
- Flyway **V24** → create `waitlist_entries` (normalized-email unique key, hashed confirm token, single-use/expiry state, status enum).

</code_context>

<specifics>
## Specific Ideas

- The public endpoint must be **truly enumeration-safe**: new / pending-duplicate / confirmed-duplicate all return the identical `202` body (D-04).
- Confirm links must land on **web** (redirect to configurable landing-page URLs), not a mobile deep link (D-01).
- Normalization must close the **`+suffix` bypass** on the dedupe + per-email-limit key, but not over-normalize with provider-specific dot rules (D-03).
- Converted invites are **organic/bootstrap-style with no referrer** — conversion must not fabricate referral attribution (D-10).
- Reuse, don't rebuild: the invite issuance, admin guard, hashed-token flow, email seam, and rate-limit infra all already exist.

</specifics>

<deferred>
## Deferred Ideas

- **Disposable-domain filtering** — deferred (D-06); double opt-in + rate limiting suffice for launch. Add a config-driven blocklist later if abuse appears.
- **Optional join fields (name / referral source)** — deferred (D-05); email only this phase.
- **Waitlist position / referral leaderboard** — v2 requirement WAIT2-01; out of scope.
- **Referral attribution from waitlist conversions** — declined (D-10); converted invites carry no referrer.
- **Idempotent re-convert / re-send of an already-invited entry** — declined (D-10); a second convert is rejected.
- **Full gmail-style dot normalization** — declined (D-03); risks merging distinct addresses at other providers.
- **Landing-page frontend** — separate repo; this phase builds only the backing API.

None else — discussion stayed within phase scope.

</deferred>

---

*Phase: 17-waitlist-landing-page-api*
*Context gathered: 2026-10-01*
