---
phase: 14
slug: report-a-user
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-25
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL/PostGIS) + MockMvc |
| **Config file** | `build.gradle.kts` (test task; Testcontainers self-provisions containers) |
| **Quick run command** | `./gradlew test --tests "*Report*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~depends on Testcontainers startup (~30–90s cold) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*Report*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| _(populated during planning/execution — see RESEARCH.md §Validation Architecture for the 12 requirement→test mappings)_ | — | — | MOD-06/07/08 | — | — | integration | `./gradlew test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `ReportEndpointIntegrationTest.kt` — endpoint + validation + status codes (MOD-06, MOD-08)
- [ ] `ReportServiceIntegrationTest.kt` — persistence, self-report, 404, rate-limit, alsoBlock delegation (MOD-06, MOD-08)
- [ ] `ReportNotificationIntegrationTest.kt` — AFTER_COMMIT async operator email + email-failure survives the report (MOD-07)

*Reuses the existing `BlockEndpointIntegrationTest` Testcontainers harness; no new framework install required.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| _(none expected — all behaviors have automated verification via Testcontainers + mocked EmailSender)_ | — | — | — |

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
