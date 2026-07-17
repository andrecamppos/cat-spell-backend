---
title: Push Notifications Design (Matches + Chat)
date: 2026-07-17
context: Captured during /gsd-explore session. Design decisions for adding push
  notifications for new matches and new chat messages. Previously parked as v2 in
  PROJECT.md Out of Scope ("requires mobile app integration").
---

# Push Notifications Design

## Goal

Deliver push notifications for **two events**, both first-class:

- **New match** — celebrate the mutual match moment.
- **New chat message** — re-engage the recipient when they're away.

Push complements the existing WebSocket STOMP chat (Phase 5); it reaches users when
the socket is **not** connected (app backgrounded/closed). WebSocket + offline
message delivery on reconnect remains the reliable source of truth. Push is a
best-effort re-engagement nudge, never a delivery guarantee.

## Send Decision: "Offline + Inactive"

Send a push only when the recipient is:

- **Offline** — no active WebSocket connection, OR
- **Online but inactive** — connected but not currently viewing that conversation.

Suppress the push when the user is actively viewing the relevant conversation to
avoid double-notifying (buzz + in-app message for the same event).

**Backend implications:**

- Track live **presence** — is this user's STOMP session connected right now?
- Track **active conversation** — client sends "now viewing conversation X" over
  STOMP; server holds this per-session. Clear on blur/disconnect.

## Delivery Provider: FCM-only (v1)

- Single integration: **FCM HTTP v1** (OAuth2 service account), which relays to
  both Android (native) and iOS (via APNs bridge).
- Fastest path, matches the project's MVP-first pattern.
- Build behind a **provider abstraction** — one internal `send(token, payload)`
  interface routing by platform — so direct APNs can be added later without
  touching call sites.
- Tradeoff accepted: FCM's iOS bridge can silently fail if APNs entitlements are
  misconfigured; direct APNs is deferred (see seed `direct-apns-hardening`).

## Token Lifecycle

- Registration endpoint(s): mobile client (separate repo) posts its FCM token.
- Store keyed by `(user_id, device_id)` — **always upsert, never insert** (handles
  reinstalls and token rotation cleanly).
- Suggested fields: `user_id`, `device_id`, `token`, `platform`, `app_version`,
  `last_seen`, `is_active`.
- Prune on delivery errors: FCM `UNREGISTERED` / APNs `410 Gone` → mark
  `is_active = false` immediately.
- Use **collapse keys** (FCM `collapse_key`) for chat so an offline device receives
  only the latest unread-count push, not a stack of individual buzzes.

## Preferences

- **v1: all-on, no toggle.** Everyone with a registered token + granted OS
  permission gets match + message pushes.
- OS-level notification permission is the user's off switch — no backend
  preferences table needed yet. Per-type toggles / quiet hours can come later.

## Verification (mobile app is a separate repo)

The backend is independently testable despite no in-repo mobile client:

- **Contract tests:** mock the FCM client (e.g. WireMock) to verify request shape,
  the send-decision logic (offline + inactive), payload structure, and
  `UNREGISTERED` pruning behavior.
- **Real smoke test:** FCM HTTP v1 `validate_only` (dry-run) flag confirms auth +
  payload validity against real FCM with no device required.
- Only the final "banner actually appears on device" step waits for the mobile app.

## Security

- Store FCM service account credentials in a secret manager — never in the client.
- Sends originate from the backend only (authenticated, auditable), never from the
  mobile app directly.

## Open Questions / Future

- Direct APNs integration for iOS reliability hardening (see seed).
- Notification preferences (per-type toggles, quiet hours) if users request control.
- Delivery metrics / observability (success rate, token churn) for production.
