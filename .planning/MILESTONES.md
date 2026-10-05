# Milestones

## v2.2 Safety, Moderation & Gated Access (Shipped: 2026-10-05)

**Delivered:** User safety (block, unmatch, report), an 18+ signup gate, invite-only signup with referral attribution, and a double-opt-in waitlist that operators convert into invites, followed by a hardening phase that closed the milestone audit's tech debt.

**Phases completed:** 6 phases (13-18), 35 plans, 85 tasks
**Stats:** 139 commits, 21,328 LOC Kotlin, 511 tests passing (79 test files); 283 files changed (+34,664 / −291)
**Timeline:** 2026-09-24 → 2026-10-05
**Requirements:** 20/20 v2.2 requirements complete (MOD-01→08, AGE-01→03, INV-01→05, WAIT-01→04)
**Closeout:** override_closeout
**Known verification overrides:** 1 newly acknowledged, 0 carried forward from a prior close (see STATE.md Deferred Items). In addition, Phase 17's VERIFICATION.md (passed 59/59) is stale because Phase 18 intentionally changed Phase 17 files to close its review warnings; Phase 18 verified those changes at 75/75. The user accepted this override on 2026-10-05. The milestone audit (2026-10-03, `tech_debt`) predates Phase 18.

**Key accomplishments:**

- Blocking and unmatch: a bidirectional block predicate (V19 `blocks` and V20 soft-state matches) enforced across the discovery feed, profile detail, swipes, chat send/open and conversation lists, with pretend-not-exist 404s. Unmatch ends the conversation without banning rediscovery (Phase 13).
- Report a user: category plus details, persisted in V21 `reports`, optional `alsoBlock` in the same transaction, and the operator is notified by an `@Async` AFTER_COMMIT email, so a send failure never loses a report (Phase 14).
- Server-side 18+ gate: DOB moved to `users` (V22, existing DOBs grandfathered, no lockout), checked at register behind a swappable `AgeVerifier` seam that returns 422 `UNDER_MINIMUM_AGE` (Phase 15).
- Invite-only access: SecureRandom codes stored as SHA-256 hashes and claimed single-use with an atomic conditional UPDATE, one generic 403 for invalid and consumed codes, referral attribution on consumption, an operator issuance endpoint, and a global `app.invite.enabled` flag (Phase 16).
- Waitlist API for the landing page: an enumeration-safe identical 202 join, hashed time-limited double opt-in, per-IP and per-email throttles, config-driven CORS, and operator list and convert-to-invite endpoints that roll back if delivery fails (Phase 17).
- Audit tech debt closed (Phase 18): reconnect and rematch never resurface previews from hidden conversations; rate-limit stores are bounded (Caffeine `RateLimitBuckets`); X-Forwarded-For hops are canonicalized against a CIDR-aware trusted-proxy matcher; join, auth and admin have separate per-IP buckets; a central `AdminTokenFilter` with a 32-character minimum token; a waitlist resend cooldown with the address pinned at first insert; and a 26-finding disposition record with 0 open.

**Known tech debt carried forward:**

- Before launch, confirm the production reverse-proxy shape and set `RATE_LIMIT_TRUSTED_PROXIES` and `WAITLIST_ALLOWED_ORIGINS` to match (deferred Phase 17 UAT test 3).
- Pre-V22 accounts with no DOB stay out of discovery, and no endpoint lets them set a DOB later (W2, AGE-03; not a regression).
- Waitlist-converted invites are bearer codes that aren't bound to the waitlist email (W3, low; follows from INV-04).
- Phase 17 IN-03 and IN-10 were deferred under D-02. Accepted residual T-18-29: a provider that ignores interrupts may deliver a rolled-back code after the send timeout.

---

## v2.1 Account Recovery & Email Verification (Shipped: 2026-08-24)

**Phases completed:** 3 phases (10-12), 14 plans, 36 tasks
**Stats:** 73 commits, 12,774 LOC Kotlin, 260 test methods (36 test files)
**Timeline:** 2026-08-08 → 2026-08-19
**Requirements:** 19/19 v2.1 requirements complete (EMAIL-01/02, RECOV-01→07, VERIFY-01→05, ACCT-01→05)

**Key accomplishments:**

- Reusable transactional-email infrastructure — provider-abstracted `EmailSender` seam with a no-op logging default (no network sends in dev/CI), backend-rendered email bodies, mirroring the existing push-provider pattern (Phase 10).
- Password recovery — enumeration-safe forgot/reset flow with SHA-256 hashed single-use 30-min tokens, per-email + per-IP Bucket4j rate limiting, and full session revocation on reset, wired through all three security tiers (Phase 10).
- Email verification on signup — hashed single-use 24h tokens, a hard `EMAIL_NOT_VERIFIED` 403 login gate, enumeration-safe resend, and a V17 migration grandfathering all existing accounts as verified (Phase 11).
- Account credential self-service — change-password (requires current password, revokes all other sessions) and change-email (requires current password, confirm the new address before it takes effect, 409 if already in use) (Phase 12).
- Consistent security posture across all flows — distinct `403 INVALID_CURRENT_PASSWORD` ProblemDetail, atomic single-use token claims, and confirm-only email swaps, with three-place public-endpoint whitelisting (Phases 10-12).
- Full Testcontainers integration coverage — recovery, verification, and credential-change suites against real Postgres + a mocked EmailSender; entire suite migrated to the no-token register + login-gate contract (`./gradlew test` green).

---

## v2.0 Push Notifications (Shipped: 2026-07-30)

**Phases completed:** 2 phases, 6 plans, 20 tasks
**Stats:** 65 commits, 10,608 LOC Kotlin, 221 test methods
**Timeline:** 2026-06-26 → 2026-07-29
**Requirements:** 12/12 v2.0 requirements complete (PUSH-01 → PUSH-12)

**Key accomplishments:**

- Authenticated device-token registration API with `(userId, deviceId)` upsert, soft-deactivation, multi-device support, and IDOR-safe object-level authz backed by a Flyway V14 `device_tokens` table.
- Provider-neutral `PushProvider` abstraction with `push.enabled`-gated selection between a no-op `LoggingPushProvider` and a firebase-admin `FcmPushProvider`, fail-fast credential wiring, and an actuator Firebase health indicator.
- `PushSendService` send seam that soft-deactivates FCM `UNREGISTERED` tokens (only), with mocked-provider contract tests for payload shape + pruning branches and a disabled-by-default validate_only smoke test.
- In-memory single-instance `PresenceRegistry` (ConcurrentHashMap-backed) plus a `StompPresenceListener` that tracks live STOMP sessions and `/topic/chat/{id}` subscriptions to drive the Phase 9 "offline + inactive" send decision.
- `PushNotificationService` holding match presence-suppression fan-out and the message "offline + inactive" send decision, plus a provider-neutral `collapseKey` mapped to FCM `AndroidConfig.collapse_key` and APNs `apns-collapse-id`.
- `@Async @TransactionalEventListener(AFTER_COMMIT)` push pipeline: `MatchService`/`ChatService` publish ID-only domain events that `PushNotificationListener` consumes off-thread after commit and delegates to `PushNotificationService`, so a slow/failing FCM call never blocks or rolls back persistence (PUSH-10).

---

## v1.0 MVP Backend (Shipped: 2026-06-16)

**Phases completed:** 6 phases, 13 plans, 50 tasks
**Stats:** 122 commits, 8,433 LOC Kotlin, 163 integration tests
**Timeline:** 8 days (2026-06-08 → 2026-06-16)

**Key accomplishments:**

- JWT authentication with refresh token rotation and theft detection (Phase 1)
- User profiles with S3 photo management, PostGIS geolocation, and Testcontainers test infra (Phase 2)
- Cat profile system with multi-cat support, photos, and cascade deletion (Phase 3)
- Cat-first discovery feed with PostGIS distance filtering, swipe actions, and mutual match detection (Phase 4)
- Real-time WebSocket chat with STOMP, offline delivery, mark-read, and conversation management (Phase 5)
- API hardening — OpenAPI docs, rate limiting, health indicators, 163 integration tests (Phase 6)

## v1.1 Mixed Discovery (Shipped: 2026-06-23)

**Phases completed:** 1 phase, 2 plans, 15 new tests (180 total)
**Stats:** 16 commits, 8,880 LOC Kotlin, 180 integration tests
**Timeline:** 2 days (2026-06-22 → 2026-06-23)

**Key accomplishments:**

- Mixed discovery feed with UNION ALL query — cat cards for cat owners, human cards for catless users (Phase 7)
- Schema migration: nullable cat_id with partial unique indexes for polymorphic swipe deduplication (Phase 7)
- Human card detail endpoint and cross-type mutual match detection (Phase 7)
- 15 new integration tests covering all mixed feed scenarios (Phase 7)

**Design change:** Users no longer need a cat to use the app. Discovery shows cat cards for users with cats (cat-first preserved) and human cards for users without cats.

---
