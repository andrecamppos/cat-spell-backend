# Phase 9: Notification Triggers & Smart Delivery - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-28
**Phase:** 9-Notification Triggers & Smart Delivery
**Areas discussed:** Message push content & privacy, Match push send logic, Active-conversation signaling, Collapsed message content

---

## Message push content & privacy

| Option | Description | Selected |
|--------|-------------|----------|
| Show preview text | title `Sarah` / body `want to grab a coffee?` — reuses the existing 100-char preview. Best re-engagement; content appears on lock screen. | ✓ |
| Generic, no content | `New message from Sarah`, no message text. Max lock-screen privacy, weaker re-engagement. | |
| No name either | `You have a new message`. Highest privacy, weakest re-engagement. | |

**User's choice:** Show preview text
**Notes:** Consistent with the in-app `ChatNotification` preview already built in `ChatService`; lock-screen exposure accepted.

---

## Match push send logic

| Option | Description | Selected |
|--------|-------------|----------|
| Suppress for whoever is active in-app | Presence-based: skip push for any matched user with a live STOMP session; push offline users. | ✓ |
| Push both unconditionally | Literal PUSH-04 — always push both regardless of presence. Double-notifies the active swiper. | |
| Suppress only the swiper | Swiper never pushed; matchee always pushed even if online. | |

**User's choice:** Suppress for whoever is active in-app
**Notes:** A match has no conversation yet, so suppression is presence-only (online = suppress). The swiper on the match screen is suppressed; offline matchee is pushed.

---

## Active-conversation signaling

| Option | Description | Selected |
|--------|-------------|----------|
| Infer from STOMP subscription | Treat SUBSCRIBE to `/topic/chat/{id}` as "viewing"; clear on unsubscribe/disconnect. No new client contract. | ✓ |
| Explicit "now viewing" signal | Dedicated STOMP SEND on focus/blur. More precise, new mobile contract required. | |
| Hybrid — subscription now, explicit later | Subscription-based now; add explicit signal in a future phase if needed. | |

**User's choice:** Infer from STOMP subscription
**Notes:** Reuses the existing chat topic; server tracks active conversation per session via subscribe/unsubscribe listeners.

---

## Collapsed message content

| Option | Description | Selected |
|--------|-------------|----------|
| Latest message preview | Show most recent message; no extra query; matches iMessage/WhatsApp collapse. | ✓ |
| Unread-count summary | `3 new messages`; requires a per-conversation count query at send time. | |
| Latest preview + count | `Sarah (3 new)` + text; richest but needs the count query. | |

**User's choice:** Latest message preview
**Notes:** Collapse key = `conversationId` (PUSH-06). Consistent with the single-message push; no count query.

---

## Claude's Discretion

- Event class names/shapes and package placement (suggest `push.event` + listener in the `push` module).
- Async executor config; recommended `@Async @TransactionalEventListener(AFTER_COMMIT)`.
- Match push copy (title/body wording) and cat/user display fields, provided `data` carries `matchId`.
- Deep-link `data` keys (match → `matchId`; message → `conversationId`, `messageId`, `senderId`).
- Whether orchestration lives in a new `PushNotificationService` or folds into `PushSendService`.

## Deferred Ideas

- Explicit focus/blur "now viewing" signal (Area 3 alternative).
- Unread-count / aggregate collapsed payloads (Area 4 alternative).
- Redis shared-store presence for horizontal scaling.
- Per-type toggles, quiet hours, direct APNs, rich media, delivery analytics.
