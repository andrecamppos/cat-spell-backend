---
phase: 9
slug: notification-triggers-smart-delivery
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-28
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + MockK 1.13.11 + Spring Boot Test / Testcontainers 1.20.6 |
| **Config file** | `build.gradle.kts` (`useJUnitPlatform()`); `BaseIntegrationTest` for container-backed tests |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.push.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~15s quick (unit) / ~2–4 min full (Testcontainers) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.push.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~15 seconds (unit) / ~4 min (full suite gate)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | PUSH-08 | — | Presence/active-conversation state isolated per session | unit | `./gradlew test --tests "*PresenceRegistryTest*"` | ❌ W0 | ⬜ pending |
| 09-01-02 | 01 | 1 | PUSH-08 | — | State clears fully on disconnect (no stale presence) | unit | `./gradlew test --tests "*PresenceRegistryTest*"` | ❌ W0 | ⬜ pending |
| 09-01-03 | 01 | 1 | PUSH-08 | — | Multi-session presence: online while ≥1 session live | unit | `./gradlew test --tests "*PresenceRegistryTest*"` | ❌ W0 | ⬜ pending |
| 09-02-01 | 02 | 2 | PUSH-06 | — | Collapse key set per-conversation; provider-neutral | unit | `./gradlew test --tests "*FcmPushProviderTest*"` | ❌ W0 | ⬜ pending |
| 09-02-02 | 02 | 2 | PUSH-04, PUSH-05, PUSH-07 | T-9-01 | Push targets only recipient / matched users; decision matrix correct | unit | `./gradlew test --tests "*PushNotificationServiceTest*"` | ❌ W0 | ⬜ pending |
| 09-02-03 | 02 | 2 | PUSH-04, PUSH-05, PUSH-06, PUSH-07 | T-9-01 | Payload deep-link keys present; no self-notify | unit | `./gradlew test --tests "*SendDecisionTest*"` | ❌ W0 | ⬜ pending |
| 09-03-01 | 03 | 3 | PUSH-10 | — | Events carry IDs/preview only (no lazy entities) | unit | `./gradlew test --tests "*PushEventTest*"` | ❌ W0 | ⬜ pending |
| 09-03-02 | 03 | 3 | PUSH-10 | T-9-02 | Listener async + AFTER_COMMIT; never blocks/rolls back write | integration | `./gradlew test --tests "*PushTriggerIntegrationTest*"` | ❌ W0 | ⬜ pending |
| 09-03-03 | 03 | 3 | PUSH-04, PUSH-05, PUSH-10 | — | Events published from createMatch/sendMessage inside tx | integration | `./gradlew test --tests "*PushTriggerIntegrationTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `PresenceRegistryTest.kt` — stubs for PUSH-08 (subscribe/unsubscribe/disconnect, multi-session)
- [ ] `PushNotificationServiceTest.kt` / `SendDecisionTest.kt` — stubs for PUSH-04/05/06/07 (decision matrix, payload, no self-notify), MockK
- [ ] `FcmPushProviderTest.kt` — stub for PUSH-06 (collapse key on built message)
- [ ] `PushTriggerIntegrationTest.kt` — stub for PUSH-10 (trigger → AFTER_COMMIT → send, non-blocking), Testcontainers + `BaseIntegrationTest`

*No framework install needed — JUnit 5 + MockK + Testcontainers already present.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Banner actually renders on a physical device | PUSH-04/05 | Requires the mobile app (separate repo) + real FCM token | Deferred to mobile integration; backend verified via mocked provider + FCM `validate_only` smoke (Phase 8) |

*All backend-observable behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s (unit)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
