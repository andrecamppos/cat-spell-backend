---
gsd_state_version: "1.0"
milestone: v2.2
current_phase: 17
status: completed
stopped_at: Phase 17 complete — all phases complete
last_updated: "2026-10-02T21:35:37.865Z"
last_activity: 2026-10-02
last_activity_desc: Phase 17 complete
state_head: c6cf055f3dd91a5dfaae6c041fc11fa7ffa0d076
progress:
  total_phases: 5
  completed_phases: 5
  total_plans: 23
  completed_plans: 23
milestone_name: Safety, Moderation & Gated Access
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-02)

**Core value:** Cat-preferred discovery — cat cards for cat owners, human cards for cat lovers without cats.
**Current focus:** Milestone v2.2 complete — ready to close (`/gsd-complete-milestone v2.2`)

## Milestone v1.0 — MVP Backend

**Status:** All phases complete
See `.planning/milestones/v1.0-ROADMAP.md` for archived phase details.

## Milestone v1.1 — Mixed Discovery

**Status:** ✅ Milestone complete (shipped 2026-06-23)
See `.planning/milestones/v1.1-ROADMAP.md` for archived phase details.

**Stats:** 1 phase, 2 plans, 180 tests, 8,880 LOC Kotlin

## Milestone v2.0 — Push Notifications

**Status:** ✅ Milestone complete (shipped 2026-07-30)
See `.planning/milestones/v2.0-ROADMAP.md` for archived phase details.

**Stats:** 2 phases (8-9), 6 plans, 221 tests, 10,608 LOC Kotlin

## Milestone v2.1 — Account Recovery & Email Verification

**Status:** ✅ Milestone complete (shipped 2026-08-24)
See `.planning/milestones/v2.1-ROADMAP.md` for archived phase details.

**Stats:** 3 phases (10-12), 14 plans, 260 tests, 12,774 LOC Kotlin

## Session Continuity

Last session: 2026-10-02T22:55:00Z
Stopped at: Phase 17 complete (UAT 3 passed, 1 deferred) — all v2.2 phases complete, milestone ready to close
Resume file: None

---
*Last updated: 2026-10-02 after Phase 17 (Waitlist / Landing-Page API)*

## Current Position

Phase: 17
Plan: Not started
Status: Phase complete — verified, validated, secured, UAT complete (17-UAT.md)
Last activity: 2026-10-02 — Phase 17 complete

## Operator Next Steps

- `/gsd-complete-milestone v2.2` to archive milestone v2.2 (Safety, Moderation & Gated Access)

## Accumulated Context

### Roadmap Evolution

- 2026-09-24: v2.2 roadmap created — Phases 13 (Blocking & Unmatch), 14 (Report a User), 15 (Age Verification), 16 (Invite-Only Access & Referral), 17 (Waitlist / Landing-Page API). All 20 v2.2 requirements mapped. Research-driven ordering: block first (report + read-path enforcement depend on it), invite before waitlist (waitlist converts into invites).
- 2026-08-07: v2.1 roadmap completed — added Phase 11 (Email Verification) and Phase 12 (Account Credentials) alongside existing Phase 10 (Password Recovery). All 19 v2.1 requirements mapped to phases (email infra bundled into Phase 10 per seed guidance).

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 17 P01 | 16 min | 2 tasks | 14 files |
| Phase 17 P02 | 8 min | 2 tasks | 7 files |
| Phase 17 P03 | 21 min | 3 tasks | 6 files |
| Phase 17 P06 | 17 min | 2 tasks | 2 files |
| Phase 17 P04 | 17 min | 2 tasks | 8 files |
| Phase 17 P05 | 15 min | 2 tasks | 8 files |
| Phase 17 P07 | 15 min | 2 tasks | 3 files |
| Phase 17 P08 | 5 min | 2 tasks | 2 files |
| Phase 17 P09 | 105 min | 3 tasks | 6 files |

## Decisions

- [Phase 17]: JoinWaitlistRequest trims email before bean validation so space-padded addresses are accepted (D-03)
- [Phase 17]: WaitlistService.join keeps rotatePendingToken result in local 'rotated' for 17-02 event gating
- [Phase 17]: D-08 applied literally: any 0-row confirm claim (incl. scanner-spent token) redirects to the error URL; hash kept on row for a possible later success variant
- [Phase 17]: Waitlist confirmation event published only when rotatePendingToken returned 1 (CONFIRMED/INVITED re-joins send no mail, D-04)
- [Phase 17]: Per-IP waitlist throttle is an exact POST /api/waitlist match plus the /api/waitlist URL registration; confirm links and CORS preflights are never throttled
- [Phase 17]: CORS maps only /api/waitlist (explicit origins, POST, Content-Type, no credentials); blank app.waitlist.allowed-origins registers nothing
- [Phase 17]: Waitlist migration proof runs on its own private DB (waitlist_migration_test), never shared with InviteMigrationTest
- [Phase 17]: Terminal-state (CONFIRMED/INVITED) re-join no-op proven by back-dating updated_at before the re-join
- [Phase 17]: AdminTokenGuard is the single shared X-Admin-Token check (app.invite.admin-token) for invite issuance and waitlist admin routes; called first in every admin handler
- [Phase 17]: GET /api/admin/waitlist rejects out-of-range limit (1..500) with 400 instead of clamping; status is case-insensitive, default confirmed
- [Phase 17]: Waitlist convert returns the raw invite code in the 201 and runs claim + organic invite + synchronous email in one transaction; delivery failure → 502 WAITLIST_INVITE_DELIVERY_FAILED with full rollback
- [Phase 17]: No idempotent re-send: converting a PENDING or already-INVITED waitlist entry is 409 WAITLIST_ENTRY_NOT_CONVERTIBLE (single-winner markInvited conditional UPDATE)
- [Phase 17]: RateLimitFilter trusts X-Forwarded-For only when request.remoteAddr is in rate-limit.trusted-proxies (exact match, default 127.0.0.1,::1; env RATE_LIMIT_TRUSTED_PROXIES); untrusted peers are keyed on their socket address (T-17-30 replaces accepted T-17-17)
- [Phase 17]: Rate-limit tests sharing a cached context pin a unique 203.0.113.x remoteAddr per test and assert requests 1-2 are not 429 before asserting request 3 is 429
- [Phase 17]: Per-email concurrency proof holds the email count at exactly 3 with Awaitility during(500ms), so a late extra send cannot pass a momentary match
- [Phase 17]: D-05 email-only storage is enforced by an exact literal 10-column set read from information_schema.columns in WaitlistMigrationTest
- [Phase 17]: RateLimitFilter matches on UrlPathHelper.defaultInstance.getPathWithinApplication (decoded path), not the raw requestURI; the waitlist join stays an exact POST + /api/waitlist match (T-17-32, CR-02)
- [Phase 17]: A trusted peer is keyed on the rightmost X-Forwarded-For hop that is not a trusted proxy, read across every header line (Tomcat RemoteIpValve semantics); supersedes the 17-07 leftmost-hop rule for multi-hop chains (T-17-33, CR-01)
- [Phase 17]: Trusted proxies are matched by TrustedProxyMatcher (JDK-only exact/CIDR, same-family, strict IP literals only, never DNS); an invalid rate-limit.trusted-proxies entry fails startup; key declared in application.yml (WR-09)

### Blockers/Concerns

- ⚠️ [Phase 17] Before launch: confirm the production reverse-proxy shape (connect address, `X-Forwarded-For` append vs overwrite, bare-IP hops) and set `RATE_LIMIT_TRUSTED_PROXIES` + `WAITLIST_ALLOWED_ORIGINS` to match exactly (deferred UAT test 3)
