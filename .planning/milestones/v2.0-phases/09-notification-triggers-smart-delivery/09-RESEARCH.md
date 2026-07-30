# Phase 9: Notification Triggers & Smart Delivery - Research

**Researched:** 2026-07-28
**Domain:** Spring Boot event-driven async orchestration + STOMP session presence + FCM smart delivery
**Confidence:** HIGH (codebase-grounded; framework specifics ASSUMED from training, not web-verified this session)

## Summary

Phase 9 wires two existing domain seams — `MatchService.createMatch` and `ChatService.sendMessage` — to the Phase 8 push delivery stack (`PushSendService.send(token, payload)`), gated by an "offline + inactive" send decision and dispatched asynchronously so a slow/failing FCM call never blocks message persistence or the request thread. No new external dependencies are required: `firebase-admin:9.9.0`, `@EnableAsync`, Spring WebSocket/STOMP, MockK, and Testcontainers are all already present `[VERIFIED: codebase]`.

The core technical work is three integration patterns, all of which already have working analogs in this repo: (1) publish Spring application events from the two `@Transactional` service methods and consume them with `@Async @TransactionalEventListener(phase = AFTER_COMMIT)` so sends fire only after DB commit; (2) an in-memory, single-instance presence + active-conversation registry driven by STOMP `SessionSubscribe/SessionUnsubscribe/SessionDisconnect` events (extending the existing `WebSocketSessionListener` pattern); (3) a per-conversation FCM collapse key, added provider-neutrally by extending `PushPayload` with an optional `collapseKey` and setting `AndroidConfig.collapse_key` / APNs `apns-collapse-id` in `FcmPushProvider`.

**Primary recommendation:** Add a `push.event` package (events + one `@Async @TransactionalEventListener` orchestrator) and a `push.presence` registry; keep all send-decision + fan-out logic in a new `PushNotificationService` so `chat`/`match` depend only on `ApplicationEventPublisher`, never on the `push` module. Extend `PushPayload` (not `FcmPushProvider` call sites) with `collapseKey` to preserve the provider-neutral contract.

## User Constraints

> Copied from `09-CONTEXT.md` `<decisions>` — these are LOCKED. Plan THESE, not alternatives.

- **D-01:** Message pushes carry the latest message preview — title = sender display name, body = existing 100-char `content.take(100)` preview (same as `ChatNotification.preview`). Lock-screen content exposure accepted.
- **D-02:** Match pushes use **presence-based suppression** — skip any matched user with a live STOMP session; push users with no live session. No conversation-viewing check for matches (a new match has no conversation yet).
- **D-03:** Backend infers "recipient viewing conversation X" from the client's STOMP subscription to `/topic/chat/{conversationId}`. No new client contract. Track via `SessionSubscribeEvent`/`SessionUnsubscribeEvent`; clear on `SessionDisconnectEvent`.
- **D-04:** Message push send decision ("offline + inactive"): send only when recipient has **no live STOMP session** OR (has a session but is **not subscribed** to that conversation's `/topic/chat/{id}`). Suppress when currently subscribed to that conversation.
- **D-05:** Collapsed message pushes carry the latest message preview (same as D-01) — no unread-count. Collapse key = `conversationId`.
- **D-06:** Presence + active-conversation state held **in-memory, single-instance** (`ConcurrentHashMap`). Redis deferred. State MUST clear on `SessionDisconnectEvent`.
- **D-07:** Triggers fire **Spring application events** published from the existing `@Transactional` service methods; a listener consumes them **asynchronously** (`@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)`) so FCM I/O runs off the request/persistence path.

**Claude's Discretion (from CONTEXT.md):** event class names/shapes + package placement; async executor config (reuse existing `@Async` default vs dedicated pool); match push copy wording; deep-link `data` keys (match → `matchId`; message → `conversationId`, `messageId`, `senderId`); whether send-decision lives in a new `PushNotificationService` or folds into `PushSendService`; fan-out via `DeviceTokenRepository.findAllByUserIdAndActiveTrue(userId)`.

**Deferred (ignore):** per-type toggles, quiet hours/DND, direct APNs, web push, rich media, Redis shared-store presence, delivery analytics.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Match/message trigger emission | API / Backend (`match`, `chat` services) | — | Events published from the transactional write path; only place that knows a match/message was persisted |
| Async send orchestration + send decision | API / Backend (`push` module) | — | Business logic; must not run on request thread (PUSH-10) |
| STOMP presence + active-conversation tracking | API / Backend (`push.presence`) | — | Server holds per-session state from STOMP lifecycle events (D-06) |
| FCM delivery + collapse key | API / Backend (`push` provider) → FCM | External (FCM HTTP v1) | Existing Phase 8 seam; collapse is an FCM message field |
| Presence signal source | Browser/Client (mobile app) | — | Client's existing `/topic/chat/{id}` subscription — no new contract (D-03) |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Context events (`ApplicationEventPublisher`, `@TransactionalEventListener`) | Spring Boot 4.0.6 | Decouple triggers from push side-effects, fire AFTER_COMMIT | Built-in; canonical Spring decoupling pattern `[VERIFIED: codebase uses @EventListener already]` |
| Spring `@Async` / `@EnableAsync` | Spring Boot 4.0.6 | Run FCM I/O off request/persistence thread | `@EnableAsync` already on `CatSpellApplication` `[VERIFIED: codebase]` |
| Spring WebSocket STOMP session events | Spring Boot 4.0.6 | `SessionSubscribe/Unsubscribe/Disconnect` for presence | Already consumed via `SessionConnectedEvent` `[VERIFIED: codebase]` |
| `com.google.firebase:firebase-admin` | 9.9.0 | FCM HTTP v1 send + `AndroidConfig`/`ApnsConfig` collapse | Already integrated in `FcmPushProvider` `[VERIFIED: codebase]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.util.concurrent.ConcurrentHashMap` | JDK 17 | In-memory presence registry (D-06) | Single-instance presence/active-conversation store |
| MockK | 1.13.11 | Mock `PushSendService`/`PushProvider` in unit tests | Send-decision matrix + payload assertions `[VERIFIED: codebase]` |
| Testcontainers (postgis) | 1.20.6 | Integration tests through the real persistence path | Trigger → AFTER_COMMIT → send assertions `[VERIFIED: codebase]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring events + `@TransactionalEventListener` | Direct call to push service inside `sendMessage` | Couples chat→push, runs on request thread, and a failed send could affect the transaction — violates PUSH-10 and D-07 |
| In-memory `ConcurrentHashMap` presence | Redis shared store | Redis explicitly deferred (D-06 / REQUIREMENTS Future) — out of scope |
| `@Async @TransactionalEventListener` | `@EventListener(fallbackExecution=true)` | Non-transactional fallback would fire even on rollback; AFTER_COMMIT is the correct guarantee |

**Installation:** None — no new dependencies. `[VERIFIED: codebase build.gradle.kts already includes firebase-admin, websocket, testcontainers, mockk]`

## Package Legitimacy Audit

Not applicable — this phase installs **no external packages**. All required libraries are already present in `build.gradle.kts` `[VERIFIED: codebase]`.

## Architecture Patterns

### System Architecture Diagram

```
  swipe (mutual)                     sendMessage
       │                                  │
       ▼                                  ▼
 MatchService.createMatch          ChatService.sendMessage
 (@Transactional)                  (@Transactional)
       │ publish                          │ publish
       ▼                                  ▼
 MatchCreatedEvent                  MessageSentEvent
       │                                  │
       └───────────► ApplicationEventPublisher ◄────────┘
                            │
                 [DB COMMIT succeeds]
                            │
                            ▼
        @Async @TransactionalEventListener(AFTER_COMMIT)
                 PushNotificationService
                    │                 │
      ┌─────────────┘                 └──────────────┐
      ▼                                               ▼
 MATCH: for each matched user                MESSAGE: recipient only
   presence.isOnline(user)?                    sendDecision(recipient, convId):
     online → suppress (D-02)                    online & subscribed(convId) → suppress (D-04)
     offline → push                              else → push (collapseKey=convId, D-05)
      │                                               │
      └───────────────► fan-out ◄────────────────────┘
        DeviceTokenRepository.findAllByUserIdAndActiveTrue(userId)
                            │ per token
                            ▼
                 PushSendService.send(token, payload)  ── prunes UNREGISTERED (Phase 8)
                            │
                            ▼
                     PushProvider (FCM HTTP v1 / Logging)

  STOMP lifecycle ──► PresenceRegistry (ConcurrentHashMap)
   SessionConnected/Subscribe/Unsubscribe/Disconnect
     • sessions per user (online = ≥1 session)
     • subscriptionId → destination map (resolve conversationId on unsubscribe)
     • clear all on disconnect (D-06)
```

### Recommended Project Structure
```
com/catspell/api/push/
├── event/
│   ├── MatchCreatedEvent.kt        # userIds of both matched users + matchId
│   ├── MessageSentEvent.kt         # recipientId, conversationId, messageId, senderId, senderName, preview
│   └── PushNotificationListener.kt # @Async @TransactionalEventListener(AFTER_COMMIT)
├── presence/
│   └── PresenceRegistry.kt         # ConcurrentHashMap state + STOMP event listeners
├── service/
│   ├── PushNotificationService.kt  # send decision + fan-out (calls PushSendService)
│   ├── PushSendService.kt          # (Phase 8 — unchanged)
│   └── PushProvider.kt             # PushPayload gains optional collapseKey
```

### Pattern 1: Publish-after-commit, consume-async
**What:** Publish a lightweight event from inside the `@Transactional` write method; consume it with `@Async @TransactionalEventListener(phase = AFTER_COMMIT)`. `[ASSUMED: Spring Framework behavior]`
**When to use:** Any side-effect (push, email) that must (a) only happen if the DB commit succeeds and (b) never block or roll back the write.
**Example:**
```kotlin
// Publisher — inside @Transactional MatchService.createMatch(...)
// (inject ApplicationEventPublisher)
eventPublisher.publishEvent(MatchCreatedEvent(matchId, user1Id, user2Id))

// Listener — new component in push.event
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onMatchCreated(event: MatchCreatedEvent) {
    pushNotificationService.notifyMatch(event)
}
```

### Pattern 2: In-memory STOMP presence registry
**What:** A `@Component` holding two `ConcurrentHashMap`s — `userId → set<sessionId>` (presence) and `sessionId → (subscriptionId → destination)` (active conversations) — updated from STOMP lifecycle events. `[ASSUMED: Spring STOMP event payloads]`
**When to use:** Single-instance "who is online / what are they viewing" without Redis.
**Example:**
```kotlin
@EventListener
fun onSubscribe(event: SessionSubscribeEvent) {
    val acc = StompHeaderAccessor.wrap(event.message)
    val userId = acc.user?.name ?: return
    val dest = acc.destination ?: return          // e.g. /topic/chat/{convId}
    registry.addSubscription(userId, acc.sessionId!!, acc.subscriptionId!!, dest)
}

@EventListener
fun onUnsubscribe(event: SessionUnsubscribeEvent) {
    val acc = StompHeaderAccessor.wrap(event.message)
    // NOTE: unsubscribe carries subscriptionId only, NOT destination —
    // resolve the destination from the map stored at subscribe time.
    registry.removeSubscription(acc.sessionId!!, acc.subscriptionId!!)
}

@EventListener
fun onDisconnect(event: SessionDisconnectEvent) {
    registry.removeSession(event.sessionId)       // clears presence + subscriptions (D-06)
}
```

### Pattern 3: Provider-neutral collapse key
**What:** Add `collapseKey: String? = null` to `PushPayload`; `FcmPushProvider.buildMessage` maps it to `AndroidConfig.collapse_key` and APNs `apns-collapse-id`. Call sites stay provider-neutral. `[ASSUMED: firebase-admin AndroidConfig API]`
**Example:**
```kotlin
Message.builder()
    .setToken(token)
    .setNotification(Notification.builder().setTitle(payload.title).setBody(payload.body).build())
    .putAllData(payload.data)
    .apply {
        payload.collapseKey?.let { key ->
            setAndroidConfig(AndroidConfig.builder().setCollapseKey(key).build())
            setApnsConfig(ApnsConfig.builder().putHeader("apns-collapse-id", key).build())
        }
    }
    .build()
```

### Anti-Patterns to Avoid
- **Calling `PushSendService` directly from `ChatService`/`MatchService`:** couples chat/match to push, runs on request thread, and a slow FCM call blocks message persistence — violates PUSH-10 + module boundary. Use events.
- **`@Async` without `AFTER_COMMIT`:** the push could fire for a message that later rolls back. Bind to the commit.
- **Passing JPA entities in events:** the async listener runs after the transaction closes → `LazyInitializationException`. Put only primitives/IDs/preview strings in the event `[ASSUMED: Hibernate lazy-loading]`.
- **Assuming `SessionUnsubscribeEvent` carries the destination:** it does not — only the subscription id. Store `subscriptionId → destination` at subscribe time.
- **Assuming one session per user:** a user may have multiple devices/sessions; presence = has ≥1 live session; only remove online status when the last session disconnects.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Fire side-effect after commit | Manual `try/finally` + thread | `@Async @TransactionalEventListener(AFTER_COMMIT)` | Handles commit binding + threading correctly |
| Background execution | `Thread { }` / `ExecutorService` by hand | Spring `@Async` (already enabled) | Managed pool, exception handling, testability |
| Per-user device fan-out | New query | `DeviceTokenRepository.findAllByUserIdAndActiveTrue(userId)` | Already exists `[VERIFIED: codebase]` |
| Dead-token pruning | Re-check in Phase 9 | `PushSendService.send` (prunes UNREGISTERED) | Already exists `[VERIFIED: codebase]` |
| Message preview | New truncation | `message.content.take(100)` (as `ChatNotification.preview`) | Already the pattern `[VERIFIED: codebase]` |

**Key insight:** Phase 9 is almost entirely wiring existing seams; the only genuinely new building block is the presence registry.

## Common Pitfalls

### Pitfall 1: Async listener + closed persistence context
**What goes wrong:** The `@Async` AFTER_COMMIT listener needs sender name / preview but the transaction is already closed → lazy load fails.
**Why it happens:** Async runs on another thread after commit; the JPA session is gone.
**How to avoid:** Capture everything the push needs (recipientId, conversationId, messageId, senderId, senderName, preview) into the event object *inside* the transaction. `ChatService.sendMessage` already computes `senderName` and the 100-char preview `[VERIFIED: codebase]` — reuse them.

### Pitfall 2: Match presence check races the WebSocket
**What goes wrong:** The swiper is on the match screen (online) and gets suppressed correctly, but an offline matchee's presence read must reflect true state.
**How to avoid:** Presence is read at send time (AFTER_COMMIT, async) from the registry; suppress only users with ≥1 live session (D-02).

### Pitfall 3: Self-notification
**What goes wrong:** Sender/ swiper receives their own message/match push.
**How to avoid:** Message push targets **recipient only** (`getOtherUserId`, already in `ChatService` `[VERIFIED: codebase]`). Match push iterates both users but presence-suppresses the active swiper (D-02).

### Pitfall 4: Testing async behavior
**What goes wrong:** Integration test asserts a send before the async listener runs → flaky.
**How to avoid:** Prefer unit tests of `PushNotificationService` (decision matrix, mocked `PushSendService`/`PresenceRegistry`) for the core logic; for integration, use Awaitility-style polling or make the executor synchronous in the test profile. Simplest: unit-test the decision + fan-out with MockK (matches Phase 8's `PushProviderContractTest` style `[VERIFIED: codebase]`).

## Code Examples

### Send decision (D-04) — the heart of the phase
```kotlin
// PushNotificationService
fun shouldSendMessagePush(recipientId: UUID, conversationId: UUID): Boolean {
    if (!presenceRegistry.isOnline(recipientId)) return true            // offline → send
    return !presenceRegistry.isViewingConversation(recipientId, conversationId) // online but not viewing → send
}
```

### Match fan-out (D-02)
```kotlin
fun notifyMatch(event: MatchCreatedEvent) {
    listOf(event.userId1, event.userId2)
        .filterNot { presenceRegistry.isOnline(it) }                    // suppress online users
        .forEach { userId -> pushToAllDevices(userId, buildMatchPayload(event)) }
}

private fun pushToAllDevices(userId: UUID, payload: PushPayload) {
    deviceTokenRepository.findAllByUserIdAndActiveTrue(userId)
        .forEach { pushSendService.send(it.token, payload) }            // Phase 8 prunes UNREGISTERED
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| FCM legacy HTTP API + `collapse_key` top-level | FCM HTTP v1 with `AndroidConfig.collapse_key` + APNs `apns-collapse-id` | FCM v1 (firebase-admin 9.x) | Collapse is per-platform config, set in `FcmPushProvider` `[ASSUMED]` |

**Deprecated/outdated:** FCM legacy server key API (do not use; project already on HTTP v1 via firebase-admin `[VERIFIED: codebase]`).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `@Async @TransactionalEventListener(AFTER_COMMIT)` runs the listener on a separate thread only after commit | Patterns / Pitfalls | If wrong, sends could block the request or fire pre-commit; mitigated by an integration test asserting non-blocking + commit-gated send |
| A2 | `SessionUnsubscribeEvent` exposes `subscriptionId` but not `destination` | Pattern 2 | If destination IS available, the subscriptionId→destination map is unnecessary (harmless over-engineering); verify against Spring `SimpMessageHeaderAccessor` at implementation |
| A3 | `AndroidConfig.setCollapseKey` + APNs `apns-collapse-id` are the firebase-admin 9.9.0 collapse APIs | Pattern 3 | If API differs, adjust `FcmPushProvider`; unit test asserts collapse key is set on the built message |
| A4 | Events carrying only IDs/strings avoid `LazyInitializationException` in the async listener | Pitfall 1 | Low — standard Hibernate behavior; enforced by event design |
| A5 | STOMP `destination` for a viewed conversation is exactly `/topic/chat/{conversationId}` | D-03/D-04 | Confirmed by `ChatService.convertAndSend("/topic/chat/${conversation.id}")` `[VERIFIED: codebase]` |

**Note:** A1–A3 are framework specifics from training knowledge, not web-verified this session. The planner should gate them behind acceptance-criteria tests (which it does) rather than treat them as locked fact.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL (postgis) via Testcontainers | Integration tests | ✓ | postgis 16-3.4 | — `[VERIFIED: codebase]` |
| firebase-admin | FCM send + collapse | ✓ | 9.9.0 | LoggingPushProvider for local (Phase 8) `[VERIFIED: codebase]` |
| Docker/Podman | Testcontainers | ✓ (build.gradle auto-detects podman socket) | — | — `[VERIFIED: codebase]` |

**Missing dependencies with no fallback:** None.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + MockK 1.13.11 + Spring Boot Test / Testcontainers 1.20.6 `[VERIFIED: codebase]` |
| Config file | `build.gradle.kts` (`useJUnitPlatform()`); `BaseIntegrationTest` for container-backed tests |
| Quick run command | `./gradlew test --tests "com.catspell.api.push.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PUSH-04 | Mutual match pushes both users; payload carries `matchId` | unit | `./gradlew test --tests "*PushNotificationServiceTest*"` | ❌ Wave 0 |
| PUSH-05 | New message pushes recipient; payload carries `conversationId`/`messageId`/`senderId` | unit | `./gradlew test --tests "*PushNotificationServiceTest*"` | ❌ Wave 0 |
| PUSH-06 | Message payload sets collapse key = `conversationId` | unit | `./gradlew test --tests "*FcmPushProvider*"`/`*PushNotificationServiceTest*` | ❌ Wave 0 |
| PUSH-07 | Send only when offline OR online-not-viewing; suppress when viewing | unit | `./gradlew test --tests "*SendDecisionTest*"` | ❌ Wave 0 |
| PUSH-08 | Presence + active-conversation tracked per session; cleared on disconnect | unit | `./gradlew test --tests "*PresenceRegistryTest*"` | ❌ Wave 0 |
| PUSH-10 | Sends run async AFTER_COMMIT; message persistence never blocked/rolled back | integration | `./gradlew test --tests "*PushTriggerIntegrationTest*"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.catspell.api.push.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `PresenceRegistryTest.kt` — covers PUSH-08 (subscribe/unsubscribe/disconnect state transitions)
- [ ] `PushNotificationServiceTest.kt` / `SendDecisionTest.kt` — covers PUSH-04/05/06/07 (decision matrix + payload/collapse assertions, MockK)
- [ ] `PushTriggerIntegrationTest.kt` — covers PUSH-10 (trigger → AFTER_COMMIT → send, non-blocking) — Testcontainers + `BaseIntegrationTest`

## Security Domain

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V4 Access Control | yes | Message push targets recipient only; match push only to the two matched users — no cross-user leakage |
| V5 Input Validation | partial | Event fields are internal (server-generated IDs/preview); no new external input surface in Phase 9 |
| V6 Cryptography | no | No new crypto; FCM credentials handled in Phase 8 |
| V8 Data Protection | yes | D-01 accepts message preview on lock screen — a documented, accepted privacy tradeoff, not a defect |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Push sent to wrong user (deep-link leaks conversation) | Information Disclosure | Fan-out strictly by `recipientId`/matched user IDs; unit-test that no push targets the sender |
| Sensitive content on lock screen | Information Disclosure | Accepted per D-01 (dating-app norm); revisit if per-type toggles ship |
| FCM I/O failure blocks persistence | Denial of Service | Async AFTER_COMMIT dispatch (PUSH-10) isolates the write path |

## Sources

### Primary (HIGH confidence)
- Codebase (`[VERIFIED]`): `PushSendService`, `PushProvider`/`PushPayload`, `FcmPushProvider`, `DeviceTokenRepository`, `ChatService.sendMessage`, `MatchService.createMatch`, `DiscoveryService.swipe`, `WebSocketSessionListener`, `WebSocketConfig`, `CatSpellApplication (@EnableAsync)`, `build.gradle.kts`, `PushProviderContractTest`, `BaseIntegrationTest`.
- `09-CONTEXT.md`, `REQUIREMENTS.md`, `notes/push-notifications-design.md`.

### Tertiary (LOW confidence — verify at implementation)
- Spring `@TransactionalEventListener`/`@Async` semantics (A1) — training knowledge; not web-fetched this session.
- Spring STOMP `SessionUnsubscribeEvent` header contents (A2) — training knowledge.
- firebase-admin 9.9.0 `AndroidConfig.setCollapseKey` / APNs `apns-collapse-id` API (A3) — training knowledge.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified present in the repo.
- Architecture: HIGH — every pattern has a working analog already in the codebase.
- Framework specifics (A1–A3): MEDIUM/LOW — gated behind acceptance-criteria tests in the plan.

**Research date:** 2026-07-28
**Valid until:** 2026-08-27 (stable stack; ~30 days)
