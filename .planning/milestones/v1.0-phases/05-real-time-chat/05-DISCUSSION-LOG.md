# Phase 5: Real-Time Chat - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-15
**Phase:** 05-real-time-chat
**Areas discussed:** Conversation model, Offline & missed messages, WebSocket authentication, Message ordering & history

---

## Conversation Model

| Option | Description | Selected |
|--------|-------------|----------|
| Match IS the conversation | No separate table. Messages FK to match_id directly. Simpler schema, but no place for per-user metadata | |
| Separate conversations table | New `conversations` table with FK to match. Stores per-user metadata (last_read_at, muted, etc) | ✓ |
| You decide | Let Claude pick | |

**User's choice:** Separate conversations table
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Single row per conversation | One row per match. Shared columns like `last_message_at`. No per-user state in v1 | |
| Two rows per conversation | One row per participant (conversation_participants table). Each row tracks last_read_at, muted status | ✓ |
| You decide | Let Claude pick | |

**User's choice:** Two rows per conversation (conversation_participants)
**Notes:** Ready for v2 read receipts

| Option | Description | Selected |
|--------|-------------|----------|
| On match creation | Conversation + 2 participant rows created automatically when mutual match forms | |
| On first message | Conversation created lazily when one user sends the first message | ✓ |
| You decide | Let Claude pick | |

**User's choice:** On first message — lazy creation
**Notes:** Avoids empty conversations cluttering the list

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal | Other user's display name + first photo, last message preview, unread count | |
| With cats | Same as minimal, plus the other user's cats (names + first photo) | ✓ |
| You decide | Let Claude pick | |

**User's choice:** With cats — consistent with Phase 4 match list
**Notes:** None

---

## Offline & Missed Messages

| Option | Description | Selected |
|--------|-------------|----------|
| REST-only pull | Client fetches message history via REST on app open. Simple — no server-side queuing | |
| Queue + deliver on reconnect | Server queues unsent messages and pushes them via WebSocket when user reconnects | ✓ |
| You decide | Let Claude pick | |

**User's choice:** Queue + deliver on reconnect
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| In-memory (session scoped) | Messages queued in server-side map. Lost on server restart — client falls back to REST history | |
| Database delivery status | Messages table has a `delivered` boolean per recipient. Survives restarts | ✓ |
| You decide | Let Claude pick | |

**User's choice:** Database delivery status
**Notes:** More robust, survives server restarts

| Option | Description | Selected |
|--------|-------------|----------|
| Active conversation only | WebSocket delivers messages only for the conversation the user is currently viewing | |
| All conversations | WebSocket pushes a lightweight notification for any new message across all conversations | ✓ |
| You decide | Let Claude pick | |

**User's choice:** All conversations — real-time badge updates
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| REST includes unread counts | Conversation list REST endpoint returns unread count per conversation | ✓ |
| WebSocket-only unread | No unread count in REST. Client tracks unread locally from WebSocket events | |
| You decide | Let Claude pick | |

**User's choice:** REST includes unread counts
**Notes:** Computed from last_read_at vs message timestamps

---

## WebSocket Authentication

| Option | Description | Selected |
|--------|-------------|----------|
| STOMP CONNECT header | Client sends JWT in STOMP CONNECT frame's `Authorization` header. Server intercepts via ChannelInterceptor | ✓ |
| Handshake query param | JWT passed as `?token=xxx` during WebSocket HTTP upgrade. Token visible in URL/logs | |
| You decide | Let Claude pick | |

**User's choice:** STOMP CONNECT header — most Spring-idiomatic
**Notes:** Reuses existing JwtService

| Option | Description | Selected |
|--------|-------------|----------|
| Disconnect immediately | Server checks token expiry on each message. Expired → disconnect with error frame | |
| Grace period + disconnect | Allow 30s grace window after expiry for token refresh | |
| Session outlives token | Once authenticated via CONNECT, session stays alive until closed. Expiry checked on reconnect | ✓ |
| You decide | Let Claude pick | |

**User's choice:** Session outlives token — simplest for v1
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Validate every message | On each incoming message, verify sender is a participant of target conversation | |
| Validate on SUBSCRIBE only | Check membership when user subscribes to conversation topic. Subscribed topics are trusted | ✓ |
| You decide | Let Claude pick | |

**User's choice:** Validate on SUBSCRIBE only — lighter per-message overhead
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| WebSocket only | Native WebSocket required. No SockJS fallback. Modern mobile apps support WebSocket natively | ✓ |
| SockJS fallback | Enable SockJS as fallback (long-polling, streaming). Broader compatibility but adds complexity | |
| You decide | Let Claude pick | |

**User's choice:** WebSocket only — mobile-only client
**Notes:** None

---

## Message Ordering & History

| Option | Description | Selected |
|--------|-------------|----------|
| Timestamp only | Order by server-assigned `created_at`. Simple. Sufficient for 1:1 chat | ✓ |
| Sequence numbers | Auto-increment sequence per conversation. Strict ordering. More complex | |
| You decide | Let Claude pick | |

**User's choice:** Timestamp only
**Notes:** Server is single ordering authority for 1:1 chat

| Option | Description | Selected |
|--------|-------------|----------|
| Cursor-based, newest first | Default returns newest messages. Client scrolls up to load older pages | ✓ |
| Cursor-based, oldest first | Default returns oldest messages. Client scrolls down to load newer | |
| You decide | Let Claude pick | |

**User's choice:** Cursor-based, newest first — consistent with Phase 4
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| 30 messages | Good balance — fills mobile screen with scroll room. Light payload | ✓ |
| 50 messages | More context per load. Fewer pagination calls for active conversations | |
| You decide | Let Claude pick | |

**User's choice:** 30 messages per page
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| 1000 characters | Keeps messages concise. Similar to dating app conventions | ✓ |
| 2000 characters | More room for longer messages. Closer to general messaging apps | |
| You decide | Let Claude pick | |

**User's choice:** 1000 characters max
**Notes:** Dating app convention (Hinge, Bumble style)

---

## Claude's Discretion

No areas deferred to Claude's discretion — all decisions made by user.

## Deferred Ideas

None — discussion stayed within phase scope.
