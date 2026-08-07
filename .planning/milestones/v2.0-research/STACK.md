# Stack Research — Push Notifications (v2.0)

**Milestone:** v2.0 Push Notifications
**Researched:** 2026-07-17
**Scope:** Additions needed to send push notifications from the existing
Kotlin + Spring Boot 4.0 backend. Existing stack (Postgres/PostGIS, S3, WebSocket
STOMP, Flyway, Testcontainers) is NOT re-researched.

## Recommended Additions

### firebase-admin (FCM HTTP v1)

- **Library:** `com.google.firebase:firebase-admin` (latest 9.x line).
- **Why:** Official server SDK. Wraps FCM HTTP v1, handles OAuth2 access-token
  minting/refresh automatically, exposes a clean `FirebaseMessaging` API. Avoids
  hand-rolling OAuth2 token refresh against `https://www.googleapis.com/auth/firebase.messaging`.
- **Key API surface:**
  - `FirebaseMessaging.getInstance().send(message)` — single send, returns message ID.
  - `FirebaseMessaging.getInstance().send(message, /* dryRun */ true)` — **validate_only**
    dry-run. Validates auth + payload + registration without delivering. This is our
    device-free smoke test (note: cannot validate APNs tokens, only FCM registrations).
  - `sendEachForMulticast(MulticastMessage)` — batch up to 500 tokens, 1:1 response
    mapping (useful if a user has multiple devices).
- **Message building:** `Message.builder().setToken(token).setNotification(...)
  .putData(...).setAndroidConfig(... collapseKey ...).setApnsConfig(...).build()`.

### Authentication / Credentials

- **Service account JSON** from Firebase console (Project Settings → Service accounts).
- **Provisioning:** set `GOOGLE_APPLICATION_CREDENTIALS` env var to the JSON path
  (ADC — Application Default Credentials). Strongly preferred over referencing the
  key in code. Fits the project's existing env-based config (`.env.example`).
- **Secret management:** never commit the JSON; inject via env/secret manager in
  prod (consistent with existing S3 credential handling).

### Spring wiring

- `@Configuration` bean that initializes `FirebaseApp` once at startup from ADC and
  exposes `FirebaseMessaging` as an injectable bean.
- No new datastore — device tokens live in Postgres (new Flyway migration + JPA entity).
- Reuse existing async infra (`@Async` / `ApplicationEventPublisher`) — no new broker.

## What NOT to add

- **No direct APNs integration** in v2.0 — FCM relays to iOS. Deferred (seed
  `direct-apns-hardening`).
- **No Redis / external queue** — send inline-async off Spring events; volume is low
  (dating app match/message rates). Revisit only if send throughput becomes a bottleneck.
- **No notification-preferences store** — all-on in v1; OS permission is the off switch.

## Version verification note

Confirm `firebase-admin` latest during planning (Context7 / Maven Central). Ensure
JVM/Kotlin compatibility with Spring Boot 4.0's baseline (Java 17+).

## Sources

- Firebase: Send a message using Admin SDK (HIGH) — firebase.google.com/docs/cloud-messaging/send/admin-sdk
- Firebase: FCM HTTP v1 auth / service accounts (HIGH) — firebase.google.com/docs/cloud-messaging/send/v1-api
- FirebaseMessaging Java API reference — dryRun semantics (HIGH)
