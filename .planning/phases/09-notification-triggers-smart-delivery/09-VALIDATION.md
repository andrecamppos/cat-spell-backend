---
phase: 9
slug: notification-triggers-smart-delivery
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-07-28
validated: 2026-07-29
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

> Reconciled 2026-07-29 against executed artifacts: during TDD the planned `FcmPushProviderTest`/`SendDecisionTest`/`PushEventTest` were folded into fewer real files (`PushNotificationServiceTest`, `PushProviderContractTest`, `PushTriggerIntegrationTest`). Behavior coverage is unchanged; file names updated below.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | PUSH-08 | — | Presence/active-conversation state isolated per session | unit | `./gradlew test --tests "*PresenceRegistryTest*"` | ✅ | ✅ green |
| 09-01-02 | 01 | 1 | PUSH-08 | — | State clears fully on disconnect (no stale presence) | unit | `./gradlew test --tests "*PresenceRegistryTest*"` | ✅ | ✅ green |
| 09-01-03 | 01 | 1 | PUSH-08 | — | Multi-session presence: online while ≥1 session live | unit | `./gradlew test --tests "*PresenceRegistryTest*"` | ✅ | ✅ green |
| 09-02-01 | 02 | 2 | PUSH-06 | — | Collapse key set per-conversation; provider-neutral | unit | `./gradlew test --tests "*PushNotificationServiceTest*" --tests "*PushProviderContractTest*"` | ✅ | ✅ green |
| 09-02-02 | 02 | 2 | PUSH-04, PUSH-05, PUSH-07 | T-9-01 | Push targets only recipient / matched users; decision matrix correct | unit | `./gradlew test --tests "*PushNotificationServiceTest*"` | ✅ | ✅ green |
| 09-02-03 | 02 | 2 | PUSH-04, PUSH-05, PUSH-06, PUSH-07 | T-9-01 | Payload deep-link keys present; no self-notify | unit | `./gradlew test --tests "*PushNotificationServiceTest*"` | ✅ | ✅ green |
| 09-03-01 | 03 | 3 | PUSH-10 | — | Events carry IDs/preview only (no lazy entities) — proven behaviorally: async AFTER_COMMIT dispatch on a closed persistence context would throw if events held lazy entities | integration | `./gradlew test --tests "*PushTriggerIntegrationTest*"` | ✅ | ✅ green |
| 09-03-02 | 03 | 3 | PUSH-10 | T-9-02 | Listener async + AFTER_COMMIT; never blocks/rolls back write | integration | `./gradlew test --tests "*PushTriggerIntegrationTest*"` | ✅ | ✅ green |
| 09-03-03 | 03 | 3 | PUSH-04, PUSH-05, PUSH-10 | T-9-08 | Events published from createMatch/sendMessage inside tx; duplicate match publishes once | integration | `./gradlew test --tests "*PushTriggerIntegrationTest*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `PresenceRegistryTest.kt` — PUSH-08 (online/offline, multi-session, subscribe/unsubscribe, per-session isolation, disconnect cleanup)
- [x] `PushNotificationServiceTest.kt` — PUSH-04/05/06/07 (match fan-out + presence suppression, message send-decision matrix, collapseKey, fan-out, zero-token safety), MockK
- [x] `PushProviderContractTest.kt` — PUSH-06 (payload shape reaches provider)
- [x] `PushTriggerIntegrationTest.kt` — PUSH-04/10 (message + match trigger → AFTER_COMMIT → async send, non-blocking, duplicate-match single dispatch), Testcontainers + `BaseIntegrationTest`

*No framework install needed — JUnit 5 + MockK + Testcontainers already present.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Banner actually renders on a physical device | PUSH-04/05 | Requires the mobile app (separate repo) + real FCM token | Deferred to mobile integration; backend verified via mocked provider + FCM `validate_only` smoke (Phase 8) |

*All backend-observable behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 15s (unit)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-07-29

---

## Validation Audit 2026-07-29

| Metric | Count |
|--------|-------|
| Gaps found | 2 |
| Resolved | 2 |
| Escalated | 0 |

Gaps were on the match trigger path (only the message path was previously tested). Added to `PushTriggerIntegrationTest`:
- `match creation dispatches notifyMatch asynchronously after the transaction commits` — PUSH-04 async AFTER_COMMIT dispatch.
- `duplicate match creation dispatches only one match notification` — T-9-08 duplicate-suppression (second `createMatch` returns existing, publishes nothing).

Full `PushTriggerIntegrationTest` run: 4 tests, 0 failures. All other Phase 9 requirements were already covered by existing green tests (file names reconciled above).
