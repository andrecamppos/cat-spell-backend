---
gsd_state_version: "1.0"
milestone: v2.2
current_phase: 18
current_phase_name: "Address tech debt: post-block redelivery + waitlist review warnings"
status: completed
stopped_at: Phase 18 complete — all phases complete
last_updated: "2026-10-05T10:41:20.139Z"
last_activity: 2026-10-05
last_activity_desc: "Phase 18 complete — gap G-18-1 closed by 18-12, re-verified 75/75"
state_head: add8a2f1bf5a5ea3d693310c9bd5492308b7c74c
progress:
  total_phases: 6
  completed_phases: 6
  total_plans: 35
  completed_plans: 35
milestone_name: Safety, Moderation & Gated Access
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-05)

**Core value:** Cat-preferred discovery — cat cards for cat owners, human cards for cat lovers without cats.
**Current focus:** Phase 18 complete — milestone v2.2 (Safety, Moderation & Gated Access) ready to close

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

Last session: 2026-10-05T10:45:00Z
Stopped at: Phase 18 complete (gap closure 18-12 verified) — all v2.2 phases complete, ready to close milestone
Resume file: None

---
*Last updated: 2026-10-05 after Phase 18 (Address tech debt: post-block redelivery + waitlist review warnings)*

## Current Position

Phase: 18 (Address tech debt: post-block redelivery + waitlist review warnings) — COMPLETE
Plan: 12 of 12
Status: Phase complete — verified (75/75, G-18-1 closed by 18-12)
Last activity: 2026-10-05 — Phase 18 complete (gap closure 18-12 executed and re-verified)

## Operator Next Steps

- `/gsd-complete-milestone v2.2` to archive milestone v2.2 (Safety, Moderation & Gated Access)

## Accumulated Context

### Roadmap Evolution

- 2026-09-24: v2.2 roadmap created — Phases 13 (Blocking & Unmatch), 14 (Report a User), 15 (Age Verification), 16 (Invite-Only Access & Referral), 17 (Waitlist / Landing-Page API). All 20 v2.2 requirements mapped. Research-driven ordering: block first (report + read-path enforcement depend on it), invite before waitlist (waitlist converts into invites).
- 2026-08-07: v2.1 roadmap completed — added Phase 11 (Email Verification) and Phase 12 (Account Credentials) alongside existing Phase 10 (Password Recovery). All 19 v2.1 requirements mapped to phases (email infra bundled into Phase 10 per seed guidance).
- 2026-10-03: Phase 18 added — Address tech debt: post-block redelivery + waitlist review warnings

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
| Phase 18 P01 | 43 min | 2 tasks | 3 files |
| Phase 18 P02 | 22 min | 2 tasks | 7 files |
| Phase 18 P03 | 25 min | 3 tasks | 7 files |
| Phase 18 P04 | 9 min | 2 tasks | 6 files |
| Phase 18 P05 | 11 min | 2 tasks | 7 files |
| Phase 18 P06 | 22 min | 2 tasks | 8 files |
| Phase 18 P07 | 30 min | 2 tasks | 7 files |
| Phase 18 P08 | 21 min | 2 tasks | 6 files |
| Phase 18 P09 | 18 min | 2 tasks | 5 files |
| Phase 18 P10 | 19 min | 2 tasks | 8 files |
| Phase 18 P11 | 16 min | 2 tasks | 3 files |
| Phase 18 P12 | 18 min | 3 tasks | 6 files |

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
- [Phase 18]: Phase 18-01 W1: reconnect redelivery resolves hidden conversations (ended match or block either way) with one JPQL query, ConversationRepository.findHiddenConversationIdsForUser; no native fallback needed
- [Phase 18]: Phase 18-01: ChatService.deliverUnreadMessages marks hidden-conversation messages delivered without pushing and returns the pushed (visible) count
- [Phase 18]: Phase 18-02 (WR-10): per-key Bucket4j stores go through one shared helper, RateLimitBuckets (Caffeine maximumSize = rate-limit.max-tracked-keys, expireAfterAccess = refill window, never expireAfterWrite); ReportService and the PasswordReset/EmailVerification/EmailChange services migrated
- [Phase 18]: Phase 18-02: RateLimitBuckets rejects capacity <= 0, a non-positive window and maxKeys <= 0 at construction, so a bad config value fails startup
- [Phase 18]: Phase 18-03 (D-15, WR-02): every X-Forwarded-For hop is canonicalized (brackets and :port stripped, re-rendered from parsed bytes) by TrustedProxyMatcher.canonicalize before both the trust check and key selection; an unparseable hop makes the key the peer's remoteAddr (fail safe)
- [Phase 18]: Phase 18-03: a bare IPv6 address followed by a port without brackets is treated as an address, never as address plus port
- [Phase 18]: Phase 18-03 (IN-02): the shape check is private (hasIpLiteralShape); public entry points are matches (strict, rejects brackets) and canonicalize
- [Phase 18]: Phase 18-03 (D-17, IN-05): the first untrusted peer that sends X-Forwarded-For logs one AtomicBoolean-guarded WARN naming the peer, rate-limit.trusted-proxies and RATE_LIMIT_TRUSTED_PROXIES; header values are never logged
- [Phase 18]: Phase 18-03: the three shared rate-limit test classes trust 198.51.100.0/24 in a byte-identical @TestPropertySource array; tests needing a dedicated trusted peer use 198.51.100.x
- [Phase 18]: Phase 18-04: Confirm-email renderer binds app.waitlist.confirm-token-ttl-hours:168 and app.waitlist.resend-cooldown-minutes:15 only to render copy; the cooldown itself is 18-06
- [Phase 18]: Phase 18-04: TTL copy renders whole days when hours is a positive multiple of 24, else hours; cooldown <= 0 drops the cooldown clause
- [Phase 18]: Phase 18-04: WaitlistController parses confirm success/error URLs once at construction (fail-fast); one error URL kept, redirect contract unchanged
- [Phase 18]: Phase 18-05 (D-13, WR-08): AdminTokenGuard fails startup in its init block when app.invite.admin-token is non-blank and under MIN_ADMIN_TOKEN_LENGTH (32); blank still starts and denies every operator request; the failure message is fixed text and never contains the token
- [Phase 18]: Phase 18-05: every admin-token test context uses the shared 34-character com.catspell.api.TEST_ADMIN_TOKEN; blank-token deny-by-default classes keep app.invite.admin-token= and send TEST_ADMIN_TOKEN as their plausible header
- [Phase 18]: 18-06: a PENDING waitlist re-join inside the resend cooldown (app.waitlist.resend-cooldown-minutes, default 15) matches 0 rows in the rotate UPDATE and sends nothing; outside it rotates and mails once (D-07, WR-03, WR-11)
- [Phase 18]: 18-06: the per-email bucket check stays first, so a no-op re-join inside the cooldown still spends a token (Open Question 2); the bucket default is 3 per 24 h on RateLimitBuckets
- [Phase 18]: 18-06: waitlist_entries.email is pinned at first insert; rotatePendingToken never writes it and the confirmation event uses findStoredEmail (D-08, WR-04)
- [Phase 18]: Phase 18-07 (D-14, WR-07a): POST /api/waitlist has its own per-IP bucket family (rate-limit.waitlist-capacity, default 10/min), separate from the /api/auth family (rate-limit.capacity)
- [Phase 18]: Phase 18-07 (D-13, WR-08): /api/admin and /api/admin/** have a strict per-IP family (rate-limit.admin-capacity, default 5/min) counting every request, any method, ahead of the X-Admin-Token check (Open Question 1: successes count; operators honor Retry-After or raise RATE_LIMIT_ADMIN_CAPACITY)
- [Phase 18]: Phase 18-07 (WR-10): RateLimitFilter's three families are RateLimitBuckets stores bounded by rate-limit.max-tracked-keys (default 100000); no unbounded map remains
- [Phase 18]: Phase 18-07 (D-16, IN-01): the limiter matches on RequestPaths.normalized (servletPath + pathInfo, semicolons stripped, dot/empty segments resolved), the one path source reused by the JWT skip list in 18-10
- [Phase 18]: Phase 18-08 (D-10, WR-05): the waitlist invite send runs on a private ThreadPoolExecutor field (2 daemon threads, queue 10), never a Spring bean, because an Executor bean would replace Boot's applicationTaskExecutor for every @Async listener
- [Phase 18]: Phase 18-08 (D-10): the invite send stays synchronous inside the convert transaction, bounded by app.waitlist.invite-send-timeout-ms (default 10000, > 0); InviteService.create uses saveAndFlush so the row is flushed before any send
- [Phase 18]: Phase 18-08 (WR-06): every delivery failure throws WaitlistInviteDeliveryException with its cause chained and logs one fixed-text WARN (exception type only, never recipient/errorDetail/code); renderer exceptions propagate unchanged as 500
- [Phase 18]: Phase 18-08 residual (T-18-29, accepted): a provider ignoring interrupts may deliver a rolled-back code after timeout; redemption gets the generic 403 and the operator retry sends a valid code (documented by 18-11)
- [Phase 18]: Phase 18-09 (D-14, WR-07c): the waitlist join CORS policy lives in one WaitlistCorsPolicy bean read by both SecurityConfig (CORS registration for /api/waitlist) and RateLimitFilterConfig; only it reads app.waitlist.allowed-origins
- [Phase 18]: Phase 18-09 (D-14): only a WAITLIST_JOIN 429 gets CORS headers - always Vary: Origin, plus ACAO (request's spelling) and Expose-Headers (Retry-After, X-RateLimit-Remaining, X-RateLimit-Reset) when checkOrigin allows it; no credentials, no wildcard, no CORS processor
- [Phase 18]: Phase 18-10 (D-11, WR-08): the operator boundary is one servlet filter, AdminTokenFilter, on /api/admin/* at HIGHEST_PRECEDENCE + 10 (after RateLimitFilter, before Spring Security). It reuses AdminTokenGuard.require and answers 401 Not authorized before MVC, so unmapped admin paths are 401 (not 404) and malformed parameters are 401 (not 400) without a token; per-handler require() calls stay as defense in depth
- [Phase 18]: Phase 18-10 (D-12, AUD-01): the JWT filter skips /api/admin and /api/admin/*, so a stale Bearer no longer 401s an operator with the right X-Admin-Token (RED: 401 Invalid or expired token)
- [Phase 18]: Phase 18-10 (IN-06): the JWT filter's waitlist skip is exact (/api/waitlist, /api/waitlist/confirm) on RequestPaths.normalized, so the skip list also applies under MockMvc
- [Phase 18]: Phase 18-11: application.yml declares rate-limit.capacity / waitlist-capacity / admin-capacity / max-tracked-keys and app.waitlist.resend-cooldown-minutes / invite-send-timeout-ms with @Value-identical defaults; WAITLIST_PER_EMAIL_REFILL_HOURS default is now 24 (matches 18-06)
- [Phase 18]: Phase 18-11: CONFIGURATION.md tells operators to rotate any non-blank INVITE_ADMIN_TOKEN under 32 characters before deploying, and that batch convert scripts must honor Retry-After on the 5/min admin throttle (or raise RATE_LIMIT_ADMIN_CAPACITY)
- [Phase 18]: Phase 18-11: the Phase 17 disposition record closes at 26 findings (24 fixed, IN-03 and IN-10 deferred per D-02, 0 open); first-review findings lost to ID reuse are re-added as WR-10, WR-11, IN-08..IN-12, and the audit stale-Bearer item is AUD-01
- [Phase 18]: G-18-1: MatchService.createMatch reactivation branch sweeps the conversation's undelivered messages via one MessageRepository.markAllDeliveredForMatch UPDATE before MatchCreatedEvent (D-05 amended; endMatch unchanged; JPQL path form shipped)

### Blockers/Concerns

- ⚠️ [Phase 17] Before launch: confirm the production reverse-proxy shape (connect address, `X-Forwarded-For` append vs overwrite, bare-IP hops) and set `RATE_LIMIT_TRUSTED_PROXIES` + `WAITLIST_ALLOWED_ORIGINS` to match exactly (deferred UAT test 3)
