# Pitfalls Research — Push Notifications (v2.0)

**Milestone:** v2.0 Push Notifications
**Researched:** 2026-07-17
**Scope:** Common mistakes when adding push to an existing Spring Boot dating backend.

## Critical Pitfalls

### 1. Insert-only token storage (duplicate/stale tokens)

- **Symptom:** duplicate rows per device, sends to dead tokens, wrong targeting.
- **Prevention:** always **upsert** by `(user_id, device_id)`. Enforce a unique
  constraint. Handle FCM `onTokenRefresh` by upserting the new token.

### 2. Never pruning invalid tokens

- **Symptom:** repeated failed sends, wasted calls, misleading metrics.
- **Prevention:** on send result `UNREGISTERED`/`NotRegistered` (FCM) — mark
  `is_active = false` immediately. Optionally deactivate tokens with `last_seen > 90d`.

### 3. Treating push delivery as guaranteed

- **Symptom:** assuming a sent push means the user saw it; missing messages when
  offline past TTL.
- **Prevention:** push is best-effort. Keep WebSocket + offline-message-on-reconnect
  (Phase 5) as the source of truth. Push is a nudge, not the delivery channel.

### 4. Double-notifying active users

- **Symptom:** user in an open conversation gets both the in-app message and a buzz.
- **Prevention:** the "offline + inactive" send-decision. Requires reliable presence +
  active-conversation state. Ensure state is **cleared on disconnect** (else a crashed
  client looks "active" forever and suppresses pushes it shouldn't).

### 5. Blocking the request/STOMP thread on FCM I/O

- **Symptom:** message send latency spikes because FCM HTTP call runs inline.
- **Prevention:** publish a domain event and handle sends with `@Async`. Never call
  `FirebaseMessaging.send()` on the message-persistence path synchronously.

### 6. In-memory presence breaks under horizontal scaling

- **Symptom:** works on one instance; wrong suppression decisions behind a load balancer
  (session on instance A, event handled on instance B).
- **Prevention:** acceptable for single-instance v2.0, but document the constraint.
  Migrate presence to a shared store (Redis) before scaling out. Flag explicitly.

### 7. Missing collapse keys for chat

- **Symptom:** an offline user returns to 30 stacked notifications from one chat.
- **Prevention:** set FCM `collapse_key` (= conversation ID) so only the latest is shown.

### 8. Credential leakage / misconfiguration

- **Symptom:** service-account JSON committed, or `GOOGLE_APPLICATION_CREDENTIALS`
  unset in prod → all sends fail.
- **Prevention:** gitignore the JSON, inject via env/secret manager, add a health
  indicator (reuse the project's health-indicator pattern from Phase 6) that checks
  Firebase init. Fail fast at startup if credentials are missing.

### 9. Testing gap — no mobile app in this repo

- **Symptom:** feature "untestable" because the mobile client is separate.
- **Prevention:** mock the `PushProvider` in integration tests to assert send-decision,
  payload shape, and pruning. Use FCM `dryRun`/`validate_only` for a real auth/payload
  smoke test. Only the on-device banner needs the app.

## Which pitfalls map to which build step

- Steps 1-2 (token store): #1, #2, #8
- Step 3 (presence): #4, #6
- Step 4 (event wiring): #3, #5, #7
- Step 5 (tests): #9

## Sources

- APNs vs FCM developer guides; token lifecycle best practices (HIGH/MEDIUM)
- Firebase Admin SDK docs — dryRun, credentials (HIGH)
- Exploration research pass 2026-07-17
