# Architecture Research — Push Notifications (v2.0)

**Milestone:** v2.0 Push Notifications
**Researched:** 2026-07-17
**Scope:** How push integrates with the existing domain-oriented Spring Boot monolith
(auth, profile, discovery, chat). Existing architecture NOT re-researched.

## Integration Approach: Event-Driven

Keep push **decoupled** from the match and chat domains via Spring
`ApplicationEventPublisher`. Match/chat code publishes a domain event; a new
notification domain listens and decides whether/what to send. This avoids coupling
core flows to FCM and keeps sends async/off the request path.

```
DiscoveryService.detectMutualMatch() ──publish──▶ MatchCreatedEvent
ChatService.persistMessage()          ──publish──▶ MessageDeliveredEvent
                                                        │
                                        @Async @EventListener
                                                        ▼
                                          NotificationService
                                   (send-decision → build payload → FCM)
```

## New Components (notification domain)

1. **DeviceToken entity + repository** — `(id, user_id, device_id, token, platform,
   app_version, is_active, last_seen, created_at)`. Unique on `(user_id, device_id)`.
   New Flyway migration.
2. **DeviceTokenController** — `POST /notifications/devices` (register/upsert),
   `DELETE /notifications/devices/{deviceId}` (unregister on logout).
3. **PushProvider abstraction** — `interface PushProvider { send(token, payload): Result }`.
   `FcmPushProvider` implements it via `FirebaseMessaging`. Interface leaves room for a
   future `ApnsPushProvider` with zero call-site changes.
4. **NotificationService** — orchestrates: resolve recipient's active tokens →
   apply send-decision → build payload (collapse key for chat) → call provider →
   prune tokens on `UNREGISTERED`.
5. **PresenceRegistry** — tracks live STOMP sessions per user + active conversation.
   Populated from STOMP lifecycle events.
6. **FirebaseConfig** — initializes `FirebaseApp`/`FirebaseMessaging` bean from ADC.

## Presence & Active-Conversation Tracking

- **Presence:** subscribe to Spring's `SessionConnectedEvent` /
  `SessionDisconnectEvent` (STOMP) to maintain a `userId → sessionCount` map.
- **Active conversation:** client sends a STOMP message (e.g. `/app/conversations/{id}/active`
  and `/inactive`) on open/close; server records `sessionId → activeConversationId`.
  Clear on disconnect.
- **Scope note:** in-memory map is fine for a single-instance monolith. If the app
  later scales horizontally, presence needs a shared store (Redis) — flag for future.

## Data Flow (message push)

1. Recipient's client is backgrounded (STOMP disconnected) OR viewing another screen.
2. Sender posts a message → `ChatService` persists → publishes `MessageDeliveredEvent`.
3. `@Async` listener → `NotificationService.onMessage()`:
   - Query `PresenceRegistry`: is recipient connected AND viewing this conversation?
     If yes → **suppress**.
   - Else → load active device tokens → build `Message` with `collapse_key = conversationId`
     → `provider.send()`.
   - On `UNREGISTERED` result → deactivate token.

## Suggested Build Order (within Phase 8)

1. DeviceToken entity/migration + register/unregister endpoints (foundation).
2. FirebaseConfig + PushProvider abstraction + FcmPushProvider (with dry-run smoke test).
3. Presence + active-conversation tracking over STOMP.
4. Event publication from match + chat domains → NotificationService wiring.
5. Integration tests (mocked FCM) + validate_only dry-run.

## Integration Points (existing code to touch)

- **Chat domain:** publish `MessageDeliveredEvent` after message persistence (Phase 5 code).
- **Discovery domain:** publish `MatchCreatedEvent` in mutual-match detection (Phase 4/7 code).
- **WebSocket config:** add STOMP session + active-conversation listeners.
- **Security:** device endpoints require the existing JWT auth.

## Sources

- Firebase Admin SDK send/dryRun (HIGH)
- Spring STOMP session event model (Spring docs, HIGH)
- Existing project ARCHITECTURE (v1.0 archived research)
