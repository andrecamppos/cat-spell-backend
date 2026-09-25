# Project Research Summary

**Project:** Cat Spell Backend
**Domain:** Dating-app backend — safety/moderation + gated access (v2.2)
**Researched:** 2026-09-24
**Confidence:** HIGH

## Executive Summary

Milestone v2.2 adds two clusters to an already-mature Kotlin/Spring Boot 4.0 + PostgreSQL/PostGIS backend: **safety & moderation** (block, unblock, unmatch, report) and **gated access** (self-attested 18+ age gate, invite-only signup with operator-issued codes + referral attribution, and a public waitlist that feeds operator-driven invites). Research strongly indicates that these are well-trodden, table-stakes backend features with established patterns — the risk is not novelty but *consistency of enforcement*.

The recommended approach is to add **almost no new dependencies** and instead reuse the seams this codebase already proved in v2.0–v2.1: the `EmailSender` abstraction (no-op logging default, no network sends in dev/CI), the Bucket4j `RateLimitFilter`, the SHA-256 hashed single-use expiring token model, enumeration-safe responses, `AFTER_COMMIT` async dispatch, and the three-place public-endpoint whitelist. New tables land as Flyway `V19+` (last shipped is `V18`).

The dominant risks are safety-correctness, not throughput: (1) blocking must be enforced **synchronously and on every surface** where one user can see or contact another (feed, profile detail, chat, match), (2) block and unmatch must have **distinct rediscovery semantics**, and (3) the age gate must be **server-side at account creation** — which surfaces a real schema decision, because `date_of_birth` currently lives on `user_profiles` (profile completion), not on the registration payload.

## Key Findings

### Recommended Stack

The stack is fixed by PROJECT.md constraints and needs no additions. Everything reuses in-repo seams; the only *optional* new asset is a static disposable-email-domain blocklist (a bundled resource file, not a dependency) if the waitlist needs it.

**Core technologies:**
- Kotlin / Spring Boot 4.0: standard controller→service→repository slices for each new domain
- PostgreSQL + PostGIS: relational blocks/invites/referrals/waitlist + `NOT EXISTS` read filters (same technique as swipe dedupe)
- Flyway `V19+`: append-only migrations for the new tables
- Bucket4j `RateLimitFilter` + `EmailSender` + hashed single-use token model: reused unchanged

### Expected Features

**Must have (table stakes):**
- Block / unblock / list — bidirectional enforcement, existing chat history retained but locked
- Unmatch — teardown without a rediscovery ban (distinct from block)
- Report a user — category enum + required details, persist + operator email, optional alsoBlock
- Age gate — self-attested DOB, hard-block under-18 at signup
- Invite-only gate — global on/off flag + operator-issued codes
- Waitlist — public join + double-opt-in confirmation + anti-abuse

**Should have (competitive):**
- Referral attribution (referrer → invitee) captured at invite consumption
- Operator waitlist→invite conversion (sends invite email)
- `AgeVerifier` seam so a vendor age check can drop in later

**Defer (v2.x+):** report triage/status workflow, member-generated invite quotas + rewards, third-party age/ID vendor, admin moderation panel.

### Architecture Approach

Three new packages — `moderation/` (block + unmatch + report), `invite/` (codes + referral), `waitlist/` (public capture + operator conversion) — plus targeted modifications to `auth/` (age + invite gate at register), `discovery/` (block-exclusion in the feed query), and `chat/`+`match/` (block/unmatch teardown + send guard).

**Major components:**
1. `BlockService` — owns the `blocks(blocker_id, blocked_id)` relationship and the bidirectional block predicate other services consult
2. `ReportService` — persists reports, notifies operator out-of-band, delegates to `BlockService` when alsoBlock
3. `InviteService` — SecureRandom codes hashed at rest, single-use consumption, referral recording
4. `WaitlistService` — enumeration-safe public capture, hashed-token double opt-in, operator convert-to-invite
5. `AgeVerifier` (seam) — local DOB math now, vendor-swappable later

### Critical Pitfalls

1. **Partial block enforcement** — add the bidirectional block check to *every* surface (feed both UNION branches, profile detail, chat send, match), not just the feed.
2. **Block vs unmatch conflated** — only `blocks` feeds the discovery `NOT EXISTS`; unmatch must NOT ban rediscovery.
3. **DOB collected too late** — `date_of_birth` is on `user_profiles` today; a true hard gate needs DOB at register time (schema/migration decision up front).
4. **Non-enumeration-safe / guessable invites & waitlist** — SecureRandom + hashed + single-use + generic errors; `202`-parity + throttle + double-opt-in for waitlist.
5. **Report send blocking the request** — persist first, notify via `AFTER_COMMIT`/async so a mail outage never loses or delays a report.

## Implications for Roadmap

Based on research, a suggested phase structure (continuing numbering from Phase 12 → **Phase 13+**):

### Phase 13: Blocking & Unmatch
**Rationale:** Highest safety value; establishes the block relationship + predicate that report and all read paths depend on.
**Delivers:** `blocks` table, block/unblock/list endpoints, unmatch endpoint, bidirectional enforcement across discovery/profile/chat/match.
**Addresses:** Block, Unblock, Unmatch (FEATURES table stakes).
**Avoids:** Partial-enforcement and block-vs-unmatch pitfalls (#1, #2).

### Phase 14: Report a User
**Rationale:** Depends on `BlockService` (for optional alsoBlock) and the `EmailSender` seam.
**Delivers:** `reports` table, report endpoint (category + details + alsoBlock), out-of-band operator notification.
**Uses:** `EmailSender`, `AFTER_COMMIT` async dispatch.
**Avoids:** Report-blocks-request pitfall (#6).

### Phase 15: Age Verification (18+)
**Rationale:** Independent gate; must resolve DOB-placement schema decision.
**Delivers:** `AgeVerifier` seam, server-side under-18 hard-block at signup, DOB collection/migration decision.
**Avoids:** DOB-too-late pitfall (#3).

### Phase 16: Invite-Only Access + Referral Attribution
**Rationale:** Gates signup; introduces invite store consumed by `auth`.
**Delivers:** `invites` + `referrals` tables, global `app.invite.enabled` gate, operator-issued codes, referral linkage at consumption.
**Avoids:** Guessable/reusable-code + enumeration pitfalls (#4).

### Phase 17: Waitlist / Landing-Page API
**Rationale:** Public entry point that feeds Phase 16's invite funnel; naturally last as it depends on invites existing.
**Delivers:** public join endpoint (`202`, enumeration-safe), double-opt-in confirm (hashed token), anti-abuse (Bucket4j + email normalization + optional disposable filter), operator convert-to-invite.
**Avoids:** Waitlist enumeration/flood pitfall (#5).

### Phase Ordering Rationale

- Block-first because report (alsoBlock) and read-path enforcement all depend on the block relationship.
- Age gate is independent and can slot anywhere, but ahead of invite/waitlist keeps all "who can create an account" logic converging in `auth` in one stretch.
- Invite before waitlist because waitlist conversion produces invites — the waitlist has nothing to convert into until the invite store exists.

### Research Flags

Phases likely needing deeper research/design during planning:
- **Phase 15 (Age):** the DOB-placement/migration decision (register vs profile) needs an explicit call; existing accounts need a backfill/grandfather plan.

Phases with standard patterns (skip deep research-phase):
- **Phases 13, 14, 16, 17:** well-documented CRUD + relational filters + reuse of existing token/email/rate-limit seams.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | No additions; reuse of proven in-repo seams |
| Features | HIGH | Table-stakes features with clear competitor precedent |
| Architecture | HIGH | Grounded in actual repo structure and read paths |
| Pitfalls | HIGH | Safety-enforcement + enumeration risks are well understood |

**Overall confidence:** HIGH

### Gaps to Address

- **DOB placement for the hard age gate:** resolve during Phase 15 planning (collect at register + migrate, or gate at profile completion with a documented weaker guarantee).
- **Operator identity/routing for report + invite emails:** which address(es) receive operator notifications — confirm during Phase 14/17 planning.
- **Disposable-email blocking scope:** decide in Phase 17 whether to bundle a blocklist or rely on double-opt-in + throttle alone.

## Sources

### Primary (HIGH confidence)
- In-repo code: `DiscoveryService`, `SwipeRepository.findDiscoveryFeed`, `PasswordResetService`/`PasswordResetToken`, `EmailSender`/`LoggingEmailSender`, `RateLimitFilter`, `db/migration/V18` — actual patterns to reuse
- Apple Developer "Age assurance", Hinge "Age Checks", CA AB-1043 — age-gate legal landscape
- PROJECT.md / MILESTONES.md — validated capabilities, constraints, prior decisions

### Secondary (MEDIUM confidence)
- techinterview.org LLDs (User Blocking, Invitation, Referral) — schema + code-generation + fraud patterns
- hld.handbook.academy, vibeengines.com, chiraghasija.cc — block/unmatch/report system-design expectations
- waitlister.me, getqueueup.com, getlaunchlist.com, Taldres/laravel-waitlist — waitlist double-opt-in + anti-abuse defaults

---
*Research completed: 2026-09-24*
*Ready for roadmap: yes*
