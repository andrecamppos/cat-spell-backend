---
phase: 16-invite-only-access-referral
plan: 03
subsystem: api
tags: [invite, admin, shared-secret, constant-time, permitall, security]

requires:
  - phase: 16-invite-only-access-referral
    provides: InviteService.create, AdminAuthException + 401 mapping, issuance DTOs (16-02)
provides:
  - POST /api/admin/invites operator issuance endpoint (constant-time X-Admin-Token, deny-by-default)
  - SecurityConfig permitAll whitelist for /api/admin/invites
affects: [waitlist-phase-17]

tech-stack:
  added: []
  patterns:
    - "Constant-time shared-secret comparison via MessageDigest.isEqual (first in-repo use)"
    - "permitAll route guarded solely by an in-controller shared-secret check (deny-by-default when unconfigured)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt
    - src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt

key-decisions:
  - "Operator issuance is an HTTP endpoint (not a startup seed / out-of-band SQL) to give Phase 17 a programmatic path (D-01)"
  - "X-Admin-Token compared with MessageDigest.isEqual; blank configured token denies everything (D-03, Pitfall 4)"

patterns-established:
  - "Admin-only surface protected by a constant-time shared-secret header rather than a role/JWT"

requirements-completed: [INV-02]

coverage:
  - id: D1
    description: "POST /api/admin/invites with a correct X-Admin-Token returns 201 with the raw code once and stores it hashed"
    requirement: "INV-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt#correct token issues 201 with a code stored hashed"
        status: pass
    human_judgment: false
  - id: D2
    description: "Missing/wrong token returns generic 401 and mints nothing; constant-time compare"
    requirement: "INV-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt (wrong token / missing token tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Deny-by-default: a blank app.invite.admin-token rejects every request on the permitAll route"
    requirement: "INV-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt#InviteAdminEndpointIntegrationTestDenyByDefault"
        status: pass
    human_judgment: false
  - id: D4
    description: "Unknown referrerUserId → 400; real referrer and bootstrap (no referrer) → 201"
    requirement: "INV-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt (referrer validation tests)"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-10-01
status: complete
---

# Phase 16 Plan 03: Admin Invite Issuance Endpoint Summary

**POST /api/admin/invites — a permitAll route gated solely by a constant-time (MessageDigest.isEqual) deny-by-default X-Admin-Token shared-secret check that calls InviteService.create and returns the raw code once.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 2
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments
- `InviteAdminController` exposes `POST /api/admin/invites`, validates the `X-Admin-Token` in constant time, and returns 201 + raw code once
- Deny-by-default: a blank `app.invite.admin-token` rejects every request even though the route is permitAll
- `SecurityConfig` whitelists `/api/admin/invites` so the header check is the entire access boundary (no JWT/role)
- Integration tests prove 201 issuance (code stored hashed), generic 401 for missing/wrong token, deny-by-default, and 400/201 referrer validation

## Task Commits

1. **Task 1: InviteAdminController + SecurityConfig whitelist** - `91a4d1b` (feat)
2. **Task 2: Admin endpoint integration test** - `236ec4f` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt` - the admin issuance endpoint + guard
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` - permitAll /api/admin/invites
- `src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt` - issuance + shared-secret + deny-by-default + referrer validation

## Decisions Made
- HTTP issuance endpoint over startup-seed/out-of-band SQL (D-01) to give Phase 17 a reusable programmatic path.
- Constant-time compare with `MessageDigest.isEqual`; no guessable default token (D-03).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. (Harmless `HHH000478` create-drop shutdown warnings in test logs, as elsewhere.)

## User Setup Required
None in code. Operators must set the `INVITE_ADMIN_TOKEN` env var (and `INVITE_ENABLED=true`) to actually issue/enforce invites in a deployment — the deny-by-default means an unset token keeps the endpoint closed.

## Next Phase Readiness
- Issuance surface complete. Ready for 16-04 (compose the invite gate into the register flow), the last plan in the phase.

---
*Phase: 16-invite-only-access-referral*
*Completed: 2026-10-01*
