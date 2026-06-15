---
phase: 5
slug: real-time-chat
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 05-01-01 | 01 | 1 | CHAT-01 | — | JWT validated on STOMP CONNECT | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ❌ W0 | ⬜ pending |
| 05-01-02 | 01 | 1 | CHAT-01 | — | Match validated before conversation | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ❌ W0 | ⬜ pending |
| 05-01-03 | 01 | 1 | CHAT-02 | — | Messages persisted and paginated | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 2 | CHAT-03 | — | Conversation list with unread count | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 2 | CHAT-01 | — | Offline messages delivered on reconnect | integration | `./gradlew test --tests "com.catspell.api.chat.*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/catspell/api/chat/ChatIntegrationTest.kt` — stubs for CHAT-01, CHAT-02, CHAT-03
- [ ] Existing `BaseIntegrationTest` covers shared fixtures (Testcontainers PostgreSQL + MinIO)
- [ ] `spring-boot-starter-websocket` test dependency for WebSocketStompClient

*Existing infrastructure (Testcontainers, BaseIntegrationTest) covers most requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real-time delivery latency < 500ms | CHAT-01 | Timing-sensitive, flaky in CI | Connect two WebSocket clients, send message, measure receive time |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
