# Phase 9: Notification Triggers & Smart Delivery - Context

**Gathered:** 2026-07-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire match and message domain events to push notifications through an "offline + inactive" send decision, dispatched asynchronously so a send never blocks message persistence or the request path. Builds on Phase 8's `PushProvider` / `PushSendService` delivery foundation.

**In scope:** PUSH-04 (both users pushed on mutual match, deep-link payload), PUSH-05 (recipient pushed on new message, sender + conversation deep-link), PUSH-06 (per-conversation collapse key), PUSH-07 (send only when recipient is offline OR online-but-not-viewing that conversation), PUSH-08 (STOMP presence + active-conversation tracking per session, cleared on disconnect), PUSH-10 (FCM sends run async off Spring domain events, never blocking persistence).

**Out of scope (future):** Per-type toggles (match on/off, message on/off), quiet hours / DND, direct APNs, web push, rich media payloads, shared-store (Redis) presence for horizontal scaling, delivery analytics/observability dashboards.

</domain>

<decisions>
## Implementation Decisions

### Message Push Content & Privacy
- **D-01:** Message pushes carry the **latest message preview** — title = sender display name, body = the existing 100-char content preview (the same `preview` already built in `ChatService`/`ChatNotification`). Chosen for re-engagement, consistent with dating-app norms; lock-screen content exposure is accepted.

### Match Push Send Logic
- **D-02:** Match pushes use **presence-based suppression**: skip the push for any matched user who has a live STOMP session (considered "active in-app" — they'll see the match live); push users with no live session. A new match has no conversation yet, so the message-level "viewing that conversation" check does not apply — presence alone drives it. In practice the swiper (who just triggered the match and is on the match screen) is suppressed; an offline matchee is pushed.

### Active-Conversation Signaling (Send Decision Input)
- **D-03:** The backend infers "recipient is viewing conversation X" from the client's **STOMP subscription to `/topic/chat/{conversationId}`**. No new client contract — reuses the existing chat topic. Track active conversation per session via `SessionSubscribeEvent` / `SessionUnsubscribeEvent`; clear on `SessionDisconnectEvent`.
- **D-04:** Message push send decision ("offline + inactive", PUSH-07): send only when the recipient has **no live STOMP session** OR (has a session but is **not subscribed** to that conversation's `/topic/chat/{id}`). Suppress when they are currently subscribed to that conversation.

### Collapsed Message Content
- **D-05:** Collapsed message pushes carry the **latest message preview** (same as D-01) — no unread-count summary, no aggregate count query. Collapse key = `conversationId` (PUSH-06). Matches iMessage/WhatsApp-style collapse; user taps in for full history.

### Presence & Active-Conversation Store
- **D-06:** Presence and active-conversation state are held **in-memory, single-instance** (e.g. `ConcurrentHashMap`) — Redis shared-store is explicitly deferred (REQUIREMENTS.md "Future Requirements"). State is per-session and MUST clear on `SessionDisconnectEvent` (PUSH-08).

### Async Dispatch
- **D-07:** Triggers fire **Spring application/domain events** (e.g. a match-created event and a message-sent event) published from the existing `@Transactional` service methods; a listener consumes them **asynchronously** so FCM I/O runs off the request/persistence path (PUSH-10). Recommended reliability semantics: `@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)` so a push only fires after the DB commit succeeds and a slow/failing FCM call can never roll back or block message persistence. Exact event class shapes and executor config are Claude's discretion.

### Claude's Discretion
- Event class names/shapes (`MatchCreatedEvent`, `MessageSentEvent` or similar) and package placement (suggest `com.catspell.api.push.event` + a listener/orchestrator in the `push` module so `chat`/`match` stay decoupled from push).
- Async executor configuration (thread pool vs `@Async` default) and whether to reuse the existing `@Async` setup that `WebSocketSessionListener` relies on.
- Match push notification **copy** (title/body wording) and which cat/user display fields to include — provided the deep-link `data` map carries `matchId`.
- Deep-link `data` payload keys, subject to: match → `matchId`; message → `conversationId`, `messageId`, `senderId`. Use `PushPayload.data` (Phase 8 contract).
- Whether the send-decision/orchestration logic lives in a new `PushNotificationService` or is folded into `PushSendService`.
- Fan-out: iterate a user's active tokens via the existing `DeviceTokenRepository.findAllByUserIdAndActiveTrue(userId)` and send per token through `PushSendService.send(...)` (which already prunes `UNREGISTERED`).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & Milestone Scope
- `.planning/REQUIREMENTS.md` — v2.0 requirement definitions; Phase 9 owns PUSH-04, 05, 06, 07, 08, 10. Traceability table maps each to its phase; "Future Requirements" section locks Redis/toggles/quiet-hours as out of scope.
- `.planning/ROADMAP.md` §"Phase 9: Notification Triggers & Smart Delivery" — goal and 4 success criteria.
- `.planning/PROJECT.md` §"Current Milestone" + "Key Decisions" — locked milestone-level decisions (FCM-only + "offline+inactive" send; push preferences all-on, no toggle in v1).

### Design Source (IMPORTANT — user's design doc)
- `.planning/notes/push-notifications-design.md` — original design decisions for the whole push feature: the "offline + inactive" send rule, collapse-key rationale, FCM-only provider abstraction, all-on preferences, and backend-verifiable testing approach. This drives the Phase 9 send logic.

### Phase 8 Foundation (delivery seam Phase 9 calls into)
- `.planning/phases/08-push-delivery-foundation/08-CONTEXT.md` — Phase 8 decisions; the `PushProvider` `send(token, payload)` contract, soft-deactivation lifecycle, and no-op/logging provider for local dev that Phase 9 sends flow through.

### Existing Code Patterns To Follow
- `src/main/kotlin/com/catspell/api/push/service/PushSendService.kt` — the `send(token, payload)` entry point; already prunes `UNREGISTERED`. Phase 9 orchestration calls this per active token.
- `src/main/kotlin/com/catspell/api/push/service/PushProvider.kt` — `PushPayload(title, body, data)`, `PushResult`, `PushSendStatus` (provider-neutral; keep it that way).
- `src/main/kotlin/com/catspell/api/push/model/DeviceTokenRepository.kt` — `findAllByUserIdAndActiveTrue(userId)` is the fan-out query for a user's devices.
- `src/main/kotlin/com/catspell/api/chat/service/ChatService.kt` §`sendMessage` (~L36-98) — message send seam; already builds the 100-char preview and fans out over STOMP (`convertAndSend` + `convertAndSendToUser`). Message trigger hooks in here.
- `src/main/kotlin/com/catspell/api/match/service/MatchService.kt` §`createMatch` — mutual-match creation seam (called from `DiscoveryService.swipe`). Match trigger hooks in here.
- `src/main/kotlin/com/catspell/api/chat/service/WebSocketSessionListener.kt` — existing `@Async @EventListener(SessionConnectedEvent)` pattern; template for the new presence/subscribe/unsubscribe/disconnect listeners.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`PushSendService.send(token, payload)`** — single send seam; already deactivates `UNREGISTERED` tokens. No pruning logic needed in Phase 9.
- **`DeviceTokenRepository.findAllByUserIdAndActiveTrue(userId)`** — ready-made fan-out to all a user's active devices.
- **`ChatService` message preview** — `content.take(100)` preview + `ChatNotification` already exist; reuse the same preview for the push body (D-01/D-05).
- **`PushPayload.data: Map<String,String>`** — deep-link payload slot already in the Phase 8 contract.
- **`WebSocketSessionListener`** — `@Async @EventListener` STOMP-event pattern to copy for presence tracking.

### Established Patterns
- Domain modules under `com.catspell.api.<domain>` with `service`/`model`/`config` subpackages — new event/listener/presence code should live in the `push` module (or a small `presence` sub-area) so `chat`/`match` don't depend on push.
- `@Transactional` service methods (`ChatService.sendMessage`, `MatchService.createMatch`) — publish domain events from here; consume with `@Async @TransactionalEventListener(AFTER_COMMIT)` (D-07).
- STOMP messaging via `SimpMessagingTemplate` and Spring `SessionConnectedEvent` listeners already wired — presence/active-conversation listeners extend this existing mechanism.
- Testcontainers integration tests + Phase 8's mocked-`PushProvider` contract-test harness — the send-decision matrix and payload/collapse-key assertions fit this harness (PUSH-12 style).

### Integration Points
- **`MatchService.createMatch`** → publish match-created event → presence-suppressed fan-out to both users (D-02).
- **`ChatService.sendMessage`** → publish message-sent event → offline+inactive decision for the recipient (D-03/D-04) → collapsed send (D-05).
- **STOMP session lifecycle** (`SessionSubscribe` / `SessionUnsubscribe` / `SessionDisconnect`) → in-memory presence + active-conversation store (D-06).
- **`PushSendService`** → the delivery seam every trigger routes through (unchanged from Phase 8).

</code_context>

<specifics>
## Specific Ideas

- Message push framing: **`{senderName}` / `{100-char preview}`** — deliberately mirrors the existing in-app `ChatNotification` so push and in-app feel consistent.
- Match suppression is **presence-only** (not conversation-based) because a match has no conversation to "view" yet — the online/offline signal is the whole decision.
- Active-conversation is inferred from the **existing `/topic/chat/{id}` subscription**, deliberately avoiding a new mobile-app contract for Phase 9.
- Collapsed pushes intentionally show the **latest message, not an unread count** — no per-send count query, consistent with the single-message push.

</specifics>

<deferred>
## Deferred Ideas

- **Explicit "now viewing" focus/blur signal** — considered for active-conversation precision (subscription can outlive the visible screen). Deferred; add only if double-notify complaints surface. (Area 3, option "hybrid/explicit".)
- **Unread-count / aggregate collapsed payloads** ("3 new messages") — considered and rejected for Phase 9 in favor of latest-message preview. Revisit if product wants inbox-style summaries.
- **Redis shared-store presence** for horizontal scaling — explicitly deferred (REQUIREMENTS.md); Phase 9 presence is single-instance in-memory.
- **Per-type toggles, quiet hours, direct APNs, rich media, delivery analytics** — future requirements per REQUIREMENTS.md and PROJECT.md.

None of the above are in Phase 9 scope — discussion stayed within the phase boundary.

</deferred>

---

*Phase: 9-Notification Triggers & Smart Delivery*
*Context gathered: 2026-07-28*
