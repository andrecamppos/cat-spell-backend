---
phase: 4
slug: discovery-matching
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2025-06-15
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `src/test/resources/application-test.yml` |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.discovery.*" --tests "com.catspell.api.match.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~37 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.catspell.api.discovery.*" --tests "com.catspell.api.match.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 37 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | DISC-01 | — | Cat-first feed data, complete profile + photo only | integration | `./gradlew test --tests "*.DiscoveryIntegrationTest"` | ✅ | ✅ green |
| 04-01-02 | 01 | 1 | DISC-01 | T4 | Own cats excluded from feed | integration | `./gradlew test --tests "*.DiscoveryIntegrationTest"` | ✅ | ✅ green |
| 04-01-03 | 01 | 2 | DISC-04 | T3 | Distance filtering; distance as rounded integer km | integration | `./gradlew test --tests "*.DiscoveryIntegrationTest"` | ✅ | ✅ green |
| 04-01-04 | 01 | 2 | DISC-05 | — | Swiped cats excluded from feed | integration | `./gradlew test --tests "*.DiscoveryIntegrationTest"` | ✅ | ✅ green |
| 04-01-05 | 01 | 2 | D-13 | — | Bidirectional gender + age preferences | integration | `./gradlew test --tests "*.DiscoveryIntegrationTest"` | ✅ | ✅ green |
| 04-01-06 | 01 | 2 | D-14 | — | 400 when requester has no location | integration | `./gradlew test --tests "*.DiscoveryIntegrationTest"` | ✅ | ✅ green |
| 04-01-07 | 01 | 3 | DISC-03 | T2 | LIKE/PASS swipes; self-swipe blocked; duplicate 409 | integration | `./gradlew test --tests "*.SwipeMatchIntegrationTest"` | ✅ | ✅ green |
| 04-01-08 | 01 | 3 | DISC-06 | T5 | Mutual match detection; LIKE+PASS no match | integration | `./gradlew test --tests "*.SwipeMatchIntegrationTest"` | ✅ | ✅ green |
| 04-01-09 | 01 | 3 | D-12 | T5 | Match idempotency — one match per user pair | integration | `./gradlew test --tests "*.SwipeMatchIntegrationTest"` | ✅ | ✅ green |
| 04-01-10 | 01 | — | D-02/D-03 | — | Cursor-based pagination returns different pages | integration | `./gradlew test --tests "*.DiscoveryIntegrationTest"` | ✅ | ✅ green |
| 04-02-01 | 02 | 1 | DISC-02 | — | Owner profile: name, bio, age, gender, photos, cats | integration | `./gradlew test --tests "*.OwnerProfileIntegrationTest"` | ✅ | ✅ green |
| 04-02-02 | 02 | 1 | DISC-02 | — | Non-existent cat 404; auth 401; any-user access | integration | `./gradlew test --tests "*.OwnerProfileIntegrationTest"` | ✅ | ✅ green |
| 04-02-03 | 02 | 2 | DISC-07 | — | Match list: empty, both-sides visible, user info, cat info | integration | `./gradlew test --tests "*.MatchIntegrationTest"` | ✅ | ✅ green |
| 04-02-04 | 02 | 2 | DISC-07 | — | Match list sorted by matchedAt descending | integration | `./gradlew test --tests "*.MatchIntegrationTest"` | ✅ | ✅ green |
| 04-02-05 | 02 | 2 | DISC-07 | — | Multiple matches; other-user resolution from both sides | integration | `./gradlew test --tests "*.MatchIntegrationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 37s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2025-06-15
