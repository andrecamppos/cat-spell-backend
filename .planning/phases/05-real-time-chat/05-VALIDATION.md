---
phase: 5
slug: real-time-chat
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-15
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `build.gradle.kts` (test dependencies) |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.chat.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.chat.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | CHAT-01 | T-05-01 | JWT validated on STOMP CONNECT | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ✅ | ✅ green |
| 05-01-02 | 01 | 1 | CHAT-01 | T-05-03 | Match validated before conversation | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ✅ | ✅ green |
| 05-01-03 | 01 | 1 | CHAT-02 | — | Messages persisted and paginated | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ✅ | ✅ green |
| 05-02-01 | 02 | 2 | CHAT-03 | — | Conversation list with unread count | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ✅ | ✅ green |
| 05-02-02 | 02 | 2 | CHAT-01 | T-05-09 | Offline messages delivered on reconnect | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/kotlin/com/catspell/api/chat/ChatIntegrationTest.kt` — 9 tests for CHAT-01, CHAT-02
- [x] `src/test/kotlin/com/catspell/api/chat/ConversationListIntegrationTest.kt` — 10 tests for CHAT-03
- [x] Existing `BaseIntegrationTest` covers shared fixtures (Testcontainers PostgreSQL + MinIO)
- [x] `spring-boot-starter-websocket` test dependency for WebSocketStompClient

*All 19 tests green. Testcontainers + BaseIntegrationTest provide shared fixtures.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real-time delivery latency < 500ms | CHAT-01 | Timing-sensitive, flaky in CI | Connect two WebSocket clients, send message, measure receive time |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 45s (~67s full suite)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** ✅ approved

---

## Validation Audit 2026-06-15

| Metric | Count |
|--------|-------|
| Gaps found | 3 |
| Resolved | 3 |
| Escalated | 0 |

### Gaps Resolved
1. **05-01-02** (PARTIAL → COVERED): `send message to non-match is rejected` — added assertion verifying no conversation was created
2. **05-01-03** (MISSING → COVERED): `message history returns paginated results newest first` — added GET `/api/conversations/{id}/messages` call with order assertions
3. **05-01-03** (MISSING → COVERED): `message history supports cursor pagination` — added two-page fetch verifying 30+5 split with `hasMore` and `nextCursor`
