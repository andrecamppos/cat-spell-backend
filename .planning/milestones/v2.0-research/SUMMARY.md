# Project Research Summary

**Project:** Cat Spell Backend
**Milestone:** v2.0 Push Notifications
**Researched:** 2026-07-17
**Confidence:** HIGH

## Executive Summary

v2.0 adds push notifications for new matches and new chat messages to the existing
Kotlin + Spring Boot backend. Push complements the shipped WebSocket STOMP chat
(Phase 5): it reaches users when the app is backgrounded/closed and the socket is
disconnected. WebSocket + offline-message-on-reconnect remains the reliable source of
truth; push is a best-effort re-engagement nudge.

The recommended approach is **FCM-only** via the official `firebase-admin` SDK (FCM
HTTP v1, OAuth2 service-account auth), wired into the monolith through an
**event-driven** notification domain. Match/chat code publishes Spring
`ApplicationEvent`s; an `@Async` listener applies an "offline + inactive"
send-decision, builds the payload, and delivers via a `PushProvider` abstraction that
leaves room for direct APNs later. Device tokens live in Postgres (new Flyway
migration), upserted by `(user_id, device_id)` and pruned on `UNREGISTERED`.

The main risks are token lifecycle hygiene, double-notifying active users (needs
reliable STOMP presence + active-conversation tracking), blocking the send path on FCM
I/O, and in-memory presence not surviving horizontal scaling. All are well understood
and preventable. The feature is backend-verifiable without the mobile app via a mocked
provider plus FCM `validate_only` dry-run.

## Key Findings

### Recommended Stack

`com.google.firebase:firebase-admin` (9.x). Provides `FirebaseMessaging.send()`,
`send(message, dryRun=true)` for validate-only, and `sendEachForMulticast()` for
multi-device. OAuth2 handled automatically via ADC (`GOOGLE_APPLICATION_CREDENTIALS`).
No new datastore, no broker, no Redis for v2.0.

### Expected Features

- **Table stakes:** device token register/unregister + upsert + prune; push on match;
  push on message with per-conversation collapse key.
- **Differentiator:** "offline + inactive" send-decision (STOMP presence +
  active-conversation tracking) to avoid double-notifying.
- **Deferred:** per-type toggles/quiet hours, direct APNs, rich media, campaign/topic
  pushes, analytics dashboard.

### Architecture Approach

Event-driven notification domain in the existing monolith. New components:
`DeviceToken` entity/repo + migration, `DeviceTokenController`, `PushProvider`
interface + `FcmPushProvider`, `NotificationService`, `PresenceRegistry`,
`FirebaseConfig`. Match/chat domains publish events; async listener decides + sends.

### Critical Pitfalls

1. Insert-only token storage → always upsert by `(user_id, device_id)`.
2. Never pruning invalid tokens → deactivate on `UNREGISTERED`.
3. Treating push as guaranteed → WebSocket stays source of truth.
4. Double-notifying active users → offline+inactive decision, clear presence on disconnect.
5. Blocking send path on FCM I/O → `@Async` off domain events.
6. In-memory presence breaks under horizontal scaling → document; Redis before scale-out.
7. Missing collapse keys → set `collapse_key = conversationId`.
8. Credential leakage/misconfig → env/secret manager + startup health check.

## Implications for Roadmap

Single phase (**Phase 8: Push Notifications**) is appropriate — one cohesive feature
domain with a clear internal build order:

1. DeviceToken entity/migration + register/unregister endpoints.
2. FirebaseConfig + PushProvider abstraction + FcmPushProvider (+ dry-run smoke test).
3. STOMP presence + active-conversation tracking.
4. Event publication from match + chat domains → NotificationService wiring.
5. Integration tests (mocked provider) + validate_only dry-run.

### Research Flags

- **Presence tracking** — Spring STOMP session-event wiring warrants care during planning.
- Everything else follows well-documented FCM + Spring patterns.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | firebase-admin is the official, well-documented SDK |
| Features | HIGH | Push patterns for messaging apps are well established |
| Architecture | HIGH | Event-driven fits the existing domain monolith |
| Pitfalls | HIGH | Token lifecycle + double-notify are well-known |

**Overall confidence:** HIGH

### Gaps to Address

- Horizontal-scaling presence (Redis) — out of scope for v2.0 but must be documented.
- iOS delivery reliability via FCM's APNs bridge — acceptable for v2.0; direct APNs is
  a planted seed for later.

## Sources

### Primary (HIGH)
- Firebase Admin SDK — send / dryRun / multicast (firebase.google.com/docs/cloud-messaging)
- FCM HTTP v1 auth & service accounts (firebase.google.com/docs/cloud-messaging/send/v1-api)
- Spring STOMP session event model

### Secondary (MEDIUM)
- APNs vs FCM developer guides; token lifecycle & collapse-key best practices
- Exploration research pass (2026-07-17)

---
*Research completed: 2026-07-17*
*Ready for roadmap: yes*
