---
phase: 16-invite-only-access-referral
plan: 04
subsystem: auth
tags: [invite, register, gate, transactional, enumeration-safety, referral]

requires:
  - phase: 16-invite-only-access-referral
    provides: InviteService.validate/consume, app.invite.enabled config key (16-01/16-02)
provides:
  - RegisterRequest.inviteCode optional field
  - AuthService.register composed invite gate (@Transactional, age-gate-first ordering)
  - End-to-end gate-on / public-mode / enumeration-safety behavior at POST /api/auth/register
affects: [waitlist-phase-17]

tech-stack:
  added: []
  patterns:
    - "Server-authoritative feature gate read via @Value('${app.invite.enabled}') — never trusts client input"
    - "@Transactional register wrapping validate→save→consume to prevent orphan accounts on a raced claim"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/invite/InvitePublicModeIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/invite/InviteEnumerationSafetyIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt
    - src/main/kotlin/com/catspell/api/auth/service/AuthService.kt

key-decisions:
  - "inviteCode is optional at the DTO layer (no @NotBlank); requiredness is enforced in the service only when gated (D-08, Pitfall 5)"
  - "Age gate stays FIRST; invite validate runs next (gated), consume runs after save, all in one transaction (D-08)"
  - "Gate state comes solely from app.invite.enabled (default false = public); no client flag is trusted (INV-01)"

patterns-established:
  - "Register-time ordered-guard composition extended with the invite gate"

requirements-completed: [INV-01, INV-03, INV-04]

coverage:
  - id: D1
    description: "Public mode (gate off) returns 201 for missing/empty/garbage inviteCode and consumes nothing"
    requirement: "INV-01"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InvitePublicModeIntegrationTest.kt (3 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Gated register requires a valid unconsumed code: valid → 201 + consumed; missing → 403 INVITE_REQUIRED, no users row"
    requirement: "INV-03"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt (2 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Invalid, consumed, and missing gated codes return a byte-identical 403/title/INVITE_REQUIRED; a rejected register leaves no orphan account"
    requirement: "INV-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteEnumerationSafetyIntegrationTest.kt (2 tests)"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-01
status: complete
---

# Phase 16 Plan 04: Register-Flow Invite Gate Composition Summary

**AuthService.register becomes @Transactional and composes the invite gate (age-gate-first → gated validate → save → consume), with an optional RegisterRequest.inviteCode, proven end-to-end for public mode, gated enforcement, enumeration safety, and no-orphan-account.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 3
- **Files modified:** 5 (3 created, 2 modified)

## Accomplishments
- `RegisterRequest.inviteCode: String? = null` added with no DTO-layer validation (requiredness enforced in the service only when gated)
- `AuthService.register` is now `@Transactional`; order is age gate → (if enabled) `inviteService.validate` → duplicate-email → save → `inviteService.consume` → email verification
- Public mode (gate off): register ignores any/absent inviteCode and returns 201 — the invite path never runs
- Gated mode: valid code → 201 + consumed; missing/invalid/consumed → single generic 403 `INVITE_REQUIRED`; a rejected register writes no orphan users row (transaction rollback)
- Three integration suites prove public-mode bypass, gated enforcement, and enumeration safety / no-orphan-account

## Task Commits

1. **Task 1: Optional inviteCode on RegisterRequest** - `032d310` (feat)
2. **Task 2: Compose invite gate into AuthService.register** - `f78f066` (feat)
3. **Task 3: Gate/public-mode/enumeration-safety tests** - `55c7644` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt` - inviteCode field
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` - @Transactional + composed gate
- `src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt` - INV-03 gated enforcement
- `src/test/kotlin/com/catspell/api/invite/InvitePublicModeIntegrationTest.kt` - INV-01 public-mode bypass
- `src/test/kotlin/com/catspell/api/invite/InviteEnumerationSafetyIntegrationTest.kt` - INV-04 indistinguishable failures + no orphan

## Decisions Made
- inviteCode optional at DTO layer; conditional requiredness in the service (D-08).
- Age gate remains the first guard; the whole flow is one transaction (D-08, Pitfall 3).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None affecting results. Test teardown logs show `HHH000478` / Hikari connection-refused warnings at JVM shutdown — the Testcontainers DB is torn down while create-drop contexts run cleanup DDL; cosmetic, build is green.

## User Setup Required
None in code. To actually gate signups a deployment sets `INVITE_ENABLED=true` (and issues codes via `INVITE_ADMIN_TOKEN`). Default is public (`false`).

## Next Phase Readiness
- Phase 16 feature-complete: invite-only gate (INV-01), issuance (INV-02), gated signup (INV-03), enumeration-safe single-use (INV-04), and referral attribution (INV-05) are all wired and tested.
- Phase 17 (Waitlist) can reuse `InviteService.create` to convert waitlist entries into invites.

---
*Phase: 16-invite-only-access-referral*
*Completed: 2026-10-01*
