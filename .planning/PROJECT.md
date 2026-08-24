# Cat Spell Backend

## What This Is

The backend API for Cat Spell — a dating app for cat lovers and cat owners. It powers a "see the cat before the owner" experience where users browse cat profiles in the swipe screen and can optionally tap through to the owner's profile. The backend serves a mobile app (built in a separate repo) via REST and WebSocket APIs, handling user/cat profiles, swipe-based discovery with geolocation filtering, mutual match detection, real-time chat, and production-ready API hardening.

## Core Value

Cat-preferred discovery — users with cats show cat-first (fall for the cat, then meet the person). Users without cats appear as human cards. The app is for all cat lovers, not just cat owners. The reveal mechanic works for cat cards; human cards show the person directly.

## Current State

**Shipped:** v2.1 Account Recovery & Email Verification (2026-08-24) — reusable provider-abstracted `EmailSender` infrastructure, password recovery (enumeration-safe forgot/reset with hashed single-use tokens, rate limiting, session revocation), email verification on signup (hard-gate login until verified, resend, grandfather migration), and self-service change-password / change-email (verify the new address before it takes effect).

**Previously shipped:** v2.0 Push Notifications (2026-07-30), v1.1 Mixed Discovery (2026-06-23), v1.0 MVP Backend (2026-06-16).

**Next milestone:** Not yet defined — start with `/gsd-new-milestone`. Likely candidates from Active requirements: safety & moderation (block/report/unmatch), compatibility scoring, chat UX (typing/read receipts), notification preferences, or direct APNs hardening.

## Requirements

### Validated

- ✓ Email/password authentication with JWT tokens — v1.0 (Phase 1)
- ✓ Refresh token rotation with theft detection — v1.0 (Phase 1)
- ✓ RFC 7807 error handling — v1.0 (Phase 1)
- ✓ User profiles (bio, photos, preferences) — v1.0 (Phase 2)
- ✓ GPS geolocation storage and distance-based filtering with configurable radius — v1.0 (Phase 2)
- ✓ S3-compatible photo upload and storage with presigned URLs — v1.0 (Phase 2)
- ✓ Cat profiles (name, age, breed, photos) with multi-cat support — v1.0 (Phase 3)
- ✓ Cat-first swipe feed (serves cat profiles, owner profile accessible from detail view) — v1.0 (Phase 4)
- ✓ Mutual match detection (both users like each other's cats) — v1.0 (Phase 4)
- ✓ Real-time WebSocket chat with STOMP for matched users — v1.0 (Phase 5)
- ✓ Conversation list with unread counts and mark-read — v1.0 (Phase 5)
- ✓ Offline message delivery on WebSocket reconnect — v1.0 (Phase 5)
- ✓ OpenAPI/Swagger documentation auto-generated — v1.0 (Phase 6)
- ✓ Rate limiting on authentication endpoints — v1.0 (Phase 6)
- ✓ Health indicators (S3, WebSocket, DB) — v1.0 (Phase 6)
- ✓ 163 integration tests across all domains — v1.0 (Phase 6)
- ✓ Mixed discovery feed — cat cards for users with cats, human cards for users without — v1.1 (Phase 7)
- ✓ Swipe supports both cat profiles and user profiles — v1.1 (Phase 7)
- ✓ 180 integration tests across all domains — v1.1 (Phase 7)
- ✓ Device token registration + lifecycle — authenticated endpoint, upsert by `(user_id, device_id)`, multi-device, prune on FCM `UNREGISTERED` — v2.0 (Phase 8)
- ✓ Push notification on new match with deep-link payload — v2.0 (Phase 9)
- ✓ Push notification on new chat message with sender + conversation deep-link and per-conversation collapse key — v2.0 (Phase 9)
- ✓ "Offline + inactive" send decision — suppress push when recipient is viewing that conversation (STOMP presence + active-conversation tracking) — v2.0 (Phase 9)
- ✓ FCM HTTP v1 delivery behind a `PushProvider` abstraction (APNs-ready) with fail-fast config + Firebase health indicator — v2.0 (Phase 8)
- ✓ Async off-thread FCM sends (`AFTER_COMMIT` domain events) that never block or roll back persistence — v2.0 (Phase 9)
- ✓ Backend-verifiable delivery — mocked-provider contract tests + `validate_only` dry-run smoke test — v2.0 (Phase 8)
- ✓ Transactional email infrastructure — provider-abstracted `EmailSender` seam with default no-op/logging provider (no network sends in dev/tests) — v2.1 (Phase 10)
- ✓ Password recovery — enumeration-safe forgot/reset via emailed link, hashed single-use token with short TTL, per-email/per-IP rate limiting, revokes all refresh tokens on reset — v2.1 (Phase 10)
- ✓ Email verification on signup — hard-gate login until verified (403 EMAIL_NOT_VERIFIED), enumeration-safe resend, migration grandfathers existing accounts — v2.1 (Phase 11)
- ✓ Change password while logged in — requires current password (403 INVALID_CURRENT_PASSWORD on mismatch), revokes all sessions, mints no tokens — v2.1 (Phase 12)
- ✓ Change email while logged in — requires current password, confirm the new address via emailed single-use token before it becomes active, 409 if already in use, revokes all sessions on confirm — v2.1 (Phase 12)

### Active
- [ ] Cat compatibility scoring (temperament, energy, indoor/outdoor)
- [ ] Lifestyle signal scoring from cat ownership patterns
- [ ] Primary/featured cat designation for swipe feed
- [ ] Typing indicators and read receipts in chat
- [ ] Block/report/unmatch safety features
- [ ] Per-type notification toggles + quiet hours (deferred from v2.0)
- [ ] Direct APNs integration for iOS delivery reliability (deferred from v2.0)

### Out of Scope

- Mobile app — separate project/repo
- Admin moderation panel — v2 (after block/report is built)
- OAuth/social login — v2 (email+password sufficient for MVP)
- Chat media sharing — v2 (text-only proven sufficient)
- Payment/subscription features — v2 (premature before community)
- Video profiles — high storage/bandwidth cost, moderation burden
- AI-generated cat descriptions — removes personal touch

## Context

- **Current state:** v2.1 shipped (2026-08-24). 12,774 LOC Kotlin, 260 test methods across 36 test files. Four milestones complete (v1.0, v1.1, v2.0, v2.1).
- **Tech stack:** Kotlin + Spring Boot 4.0, PostgreSQL + PostGIS, S3 (MinIO local), WebSocket STOMP, Flyway, Testcontainers
- **Domain:** Niche dating app targeting cat lovers/owners
- **Architecture:** Backend-only REST + WebSocket API. Mobile app is a separate project.
- **Reveal mechanic:** Cat cards: Two stages — Stage 1: Cat profile shown in swipe screen. Stage 2: Owner profile accessible by tapping into cat detail view. Human cards: User profile shown directly.
- **Multi-cat:** Users can register up to 5 cats. Cat owners appear as cat cards in discovery (one card per owner, first-created cat). Users without cats appear as human cards.
- **Chat:** WebSocket STOMP messaging with lazy conversation creation, offline delivery, and unread tracking. Unlocked after mutual match.
- **Push:** FCM push for matches and messages behind a `PushProvider` abstraction (APNs-ready), "offline + inactive" send decision via STOMP presence, async `AFTER_COMMIT` dispatch, dead-token pruning.
- **Account/email:** Provider-abstracted `EmailSender` seam (no-op logging default, no network sends in dev/CI). Password recovery, email verification (hard-gate login), and self-service change-password/change-email all use hashed single-use expiring tokens, enumeration-safe responses, Bucket4j rate limiting, and session revocation.
- **Testing:** Full Testcontainers-based integration tests (PostgreSQL + PostGIS + MinIO). No H2.
- **Next focus:** Safety & moderation, compatibility scoring, direct APNs hardening, or mobile app integration.

## Constraints

- **Tech stack:** Kotlin + Spring Boot — non-negotiable
- **Database:** PostgreSQL + PostGIS — non-negotiable
- **Photo storage:** S3-compatible (AWS S3 or MinIO for local dev)
- **Scope:** Backend API only — no frontend, no mobile app code in this repo
- **Auth:** Email + password with JWT for v1 (no OAuth)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin + Spring Boot 4.0 | Owner preference, strong ecosystem for backend APIs | ✓ Good — productive, mature ecosystem |
| PostgreSQL + PostGIS | Relational model fits user/cat/match; PostGIS for geolocation | ✓ Good — native distance queries performant |
| Cat-first swipe (two-stage reveal) | Core differentiator — playful and curiosity-driven | ✓ Good — clean API separation enforced |
| 5-cat limit per user | Prevents abuse while supporting multi-cat households | ✓ Good — reasonable constraint |
| WebSocket STOMP over polling | Real-time feel expected in modern dating apps | ✓ Good — Spring Boot native support |
| Lazy conversation creation | Conversation created on first message, not on match | ✓ Good — avoids empty conversation clutter |
| S3 presigned URLs | Client uploads directly to S3, backend never handles file bytes | ✓ Good — scalable, MinIO local dev parity |
| Testcontainers over H2 | Real PostgreSQL + PostGIS in tests, no dialect mismatches | ✓ Good — caught real bugs H2 would miss |
| Bucket4j rate limiting | Lightweight, no Redis dependency for MVP | ✓ Good — simple ConcurrentHashMap sufficient |
| Optional cat ownership | App is for all cat lovers, not just owners — widens user base | ✓ Good — v1.1 (Phase 7) |
| Mixed discovery feed | Cat cards for cat owners, human cards for catless users | ✓ Good — v1.1 (Phase 7) |
| Defer moderation to v2 | Focus v1 on core matching/chat loop | — Pending (needed before public launch) |
| Push notifications: FCM-only + "offline+inactive" send | Fastest path (one integration, relays to iOS+APNs); abstraction leaves room for direct APNs; suppress push when user is in the conversation | ✓ Good — shipped v2.0 (Phases 8-9); `PushProvider` abstraction kept call sites APNs-agnostic |
| Push preferences: all-on, no toggle in v1 | OS-level permission is the off switch; avoids premature preferences model | ✓ Good — v2.0; per-type toggles + quiet hours deferred to a later milestone |
| Async AFTER_COMMIT push dispatch | Domain-event listeners run off-thread after commit so a slow/failing FCM call never blocks or rolls back message persistence | ✓ Good — v2.0 (Phase 9), verified persistence is never blocked |
| In-memory single-instance presence registry | ConcurrentHashMap-backed STOMP presence/active-conversation is sufficient for single-instance; Redis-backed shared store deferred until horizontal scaling | ✓ Good — v2.0 (Phase 9); scaling caveat documented as future work |
| iOS client uses the FCM SDK (client-owned FCM token) | Backend delivers only via FCM (`FirebaseMessaging.send(setToken(...))`), which requires an FCM registration token, not a raw APNs token; iOS app adds Firebase Messaging SDK and registers its FCM token via `POST /api/devices`. Keeps the shipped contract unchanged; direct APNs stays deferred | — Decided 2026-07-30 (cross-repo); FCM payload shapes documented in `docs/openapi.yaml` (`x-push-notifications`) |
| Provider-abstracted `EmailSender` seam (no-op logging default) | Mirrors the proven `PushProvider` pattern; concrete provider swappable, no real network sends in dev/CI | ✓ Good — v2.1 (Phase 10); reused unchanged across recovery, verification, and email-change |
| Hashed single-use expiring tokens for all email flows | SHA-256 at rest, atomic single-use claim, short TTL (reset 30 min, verify 24 h); reused/expired tokens rejected | ✓ Good — v2.1 (Phases 10-12); consistent, low-risk token model |
| Enumeration-safe responses + reuse of Bucket4j rate limiting | Generic responses on forgot/resend, per-email + per-IP throttling on existing infra (no new dependency) | ✓ Good — v2.1 (Phases 10-11) |
| Hard-gate login until email verified + grandfather migration | Unverified users get `403 EMAIL_NOT_VERIFIED`; V17 backfill marks existing accounts verified so no current user is locked out on rollout | ✓ Good — v2.1 (Phase 11) |
| Confirm-before-swap for email changes (separate `email_change_requests` table) | New address is verified via emailed single-use token before it becomes active; 409 if already in use; revoke all sessions on confirm | ✓ Good — v2.1 (Phase 12) |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-24 after v2.1 milestone*
