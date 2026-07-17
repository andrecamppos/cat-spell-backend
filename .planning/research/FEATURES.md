# Features Research — Push Notifications (v2.0)

**Milestone:** v2.0 Push Notifications
**Researched:** 2026-07-17
**Scope:** How push notifications typically work for a dating app, focused on the
NEW capability. Existing match/chat features already shipped (v1.0/v1.1).

## Feature Categories

### Device Token Management (table stakes)

- **Register token:** authenticated endpoint where the mobile client posts its FCM
  token + device metadata. **Upsert** by `(user_id, device_id)` — never insert-only
  (handles reinstalls and token rotation cleanly).
- **Unregister token:** on logout the client removes its token; backend deletes the
  association.
- **Automatic pruning:** on send, FCM returns `UNREGISTERED` / `NotRegistered` for dead
  tokens (uninstall, expiry). Mark `is_active = false` immediately so we stop retrying.
- **Multi-device:** a user may have several active tokens; send to all.

### Match Notifications (table stakes for this milestone)

- Fire when a **mutual match** is detected (both users like each other). Both users
  are notified. Deep-link payload targets the new match/conversation.
- Celebratory, one-shot event — no collapsing needed.

### Message Notifications (table stakes)

- Fire when a chat message is persisted and the recipient should be alerted.
- **Collapse** per conversation (FCM `collapse_key`) so an offline device receives
  only the latest unread state, not a stack of buzzes.
- Payload carries sender + conversation ID for deep-linking and badge count.

### Send-Decision Logic (differentiator — the smart part)

- **"Offline + inactive":** send push only when the recipient is (a) offline (no live
  STOMP session) OR (b) online but not viewing that conversation.
- Suppress when the user is actively viewing the relevant conversation (they already
  see the in-app message) — avoids double-notification.
- Requires: live **presence** (is a STOMP session connected?) and **active-conversation**
  tracking (client announces "viewing conversation X" over STOMP).

## Deferred / Out of Scope (this milestone)

- **Per-type toggles / quiet hours** — v1 is all-on; OS permission is the off switch.
- **Direct APNs** — FCM relays to iOS for now (seed: direct-apns-hardening).
- **Rich media / images in push** — text + deep-link only.
- **Marketing/campaign pushes, topic broadcast** — transactional only for v2.0.
- **Delivery analytics dashboard** — basic logging only; full metrics later.

## Anti-features

- Do not treat push as guaranteed delivery — it is best-effort. WebSocket + offline
  message-on-reconnect (Phase 5) remains the source of truth.
- Do not send push for messages the user is actively reading.

## Sources

- Exploration research pass (2026-07-17): push delivery providers + token lifecycle
- APNs vs FCM developer guides; collapse-key / unread-badge patterns (MEDIUM/HIGH)
