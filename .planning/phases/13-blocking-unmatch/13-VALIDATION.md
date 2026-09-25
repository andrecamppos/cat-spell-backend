---
phase: 13
slug: blocking-unmatch
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-09-25
---

# Phase 13 — Validation Strategy

> Per-phase validation contract, reconstructed from phase artifacts (State B). All Phase 13 requirements (MOD-01…MOD-05) have automated integration coverage; the full suite is green.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16 + PostGIS 3.4, MinIO) |
| **Config file** | `build.gradle.kts` (Gradle test task); Testcontainers provisions ephemeral Postgres/MinIO per run |
| **Quick run command** | `./gradlew test --tests "com.catspell.api.moderation.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60s (moderation subset); full suite longer |

---

## Sampling Rate

- **After every task commit:** Run the moderation subset `./gradlew test --tests "com.catspell.api.moderation.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~60s (moderation subset)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 13-01 (D1/D4) | 01 | 1 | MOD-01 | — | `blocks` table idempotency UNIQUE + self-block CHECK + reverse index; Block entity/repo bidirectional `existsBlockBetween` | integration | `./gradlew test --tests "com.catspell.api.match.MatchIntegrationTest"` | ✅ | ✅ green |
| 13-01 (D2) | 01 | 1 | MOD-05 | — | `matches.ended_at`/`ended_reason` soft-state, active-only match list (no hard delete) | integration | `./gradlew test --tests "com.catspell.api.match.MatchIntegrationTest"` | ✅ | ✅ green |
| 13-01 (D3) | 01 | 1 | MOD-02 | — | Feed excludes blocked pairs bidirectionally in both UNION branches (blocks-only); `deleteSwipesBetween` re-opens rediscovery | integration | `./gradlew test --tests "com.catspell.api.discovery.DiscoveryIntegrationTest"` | ✅ | ✅ green |
| 13-02 (D1/D2) | 02 | 2 | MOD-01 | — | `SelfBlockException` → RFC 7807 400; block⊇unmatch teardown (reason=BLOCK, swipes cleared, rows retained); idempotent | integration | `./gradlew test --tests "com.catspell.api.moderation.BlockServiceIntegrationTest"` | ✅ | ✅ green |
| 13-02 (D3/D5) | 02 | 2 | MOD-04 | — | Directional (IDOR-scoped) unblock, no auto-rematch; `isBlockedEitherWay` bidirectional; block list minimal-identity newest-first | integration | `./gradlew test --tests "com.catspell.api.moderation.BlockServiceIntegrationTest"` | ✅ | ✅ green |
| 13-02 (D4) | 02 | 2 | MOD-05 | — | `unmatch` participant-scoped (404 no active match), writes no block row; `createMatch` reactivates ended matches | unit | `./gradlew test --tests "com.catspell.api.match.MatchServiceTest"` | ✅ | ✅ green |
| 13-03 (D1/D3) | 03 | 3 | MOD-02 | — | Profile-detail (cat-owner + user-profile), feed, and swipe/match-lookup pretend-not-exist 404 bidirectionally | integration | `./gradlew test --tests "com.catspell.api.moderation.BlockEnforcementIntegrationTest"` | ✅ | ✅ green |
| 13-03 (D2/D4) | 03 | 3 | MOD-03 | — | Chat send + open rejected (404) both directions for blocked/ended pairs; ended conversations hidden from both lists; messages retained | integration | `./gradlew test --tests "com.catspell.api.moderation.BlockEnforcementIntegrationTest"` | ✅ | ✅ green |
| 13-03 (D5) | 03 | 3 | MOD-03 | — | Regression: active conversations still listed | integration | `./gradlew test --tests "com.catspell.api.chat.ConversationListIntegrationTest"` | ✅ | ✅ green |
| 13-04 (D1/D2) | 04 | 4 | MOD-01 | — | `POST/DELETE /api/blocks/{targetUserId}` → 204 (idempotent), self → 400; `GET /api/blocks` minimal identity | integration | `./gradlew test --tests "com.catspell.api.moderation.BlockEndpointIntegrationTest"` | ✅ | ✅ green |
| 13-04 (D3) | 04 | 4 | MOD-04 | — | Block list IDOR-scoped to JWT principal (each caller sees only their own) | integration | `./gradlew test --tests "com.catspell.api.moderation.BlockEndpointIntegrationTest"` | ✅ | ✅ green |
| 13-04 (D4) | 04 | 4 | MOD-05 | — | `DELETE /api/matches/{targetUserId}` → 204 then 404 on repeat; bans no rediscovery | integration | `./gradlew test --tests "com.catspell.api.moderation.BlockEndpointIntegrationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure (JUnit 5 + Spring Boot Test + Testcontainers) covers all phase requirements. No Wave 0 framework install needed.

---

## Manual-Only Verifications

All phase behaviors have automated verification.

*Note:* Chat send is WebSocket-only (`@MessageMapping("/chat.send")`); the send-block guard is exercised by driving `ChatService.sendMessage` directly — the exact path the WS controller invokes — plus the REST message-open 404. This is fully automated (no manual step).

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — existing infra sufficient)
- [x] No watch-mode flags
- [x] Feedback latency < 60s (moderation subset)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-09-25

---

## Validation Audit 2026-09-25

| Metric | Count |
|--------|-------|
| Requirements audited | 5 (MOD-01…MOD-05) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Reconstructed from phase SUMMARY/VERIFICATION artifacts (no prior VALIDATION.md). All 5 requirements map to named, behavior-targeting integration tests. Moderation suite re-run green (`./gradlew test --tests "com.catspell.api.moderation.*"` → BUILD SUCCESSFUL, 18 tests: BlockServiceIntegrationTest 7, BlockEnforcementIntegrationTest 5, BlockEndpointIntegrationTest 6).
