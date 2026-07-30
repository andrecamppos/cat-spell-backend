# Milestones

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
