---
phase: 17-waitlist-landing-page-api
plan: 04
subsystem: api
tags: [waitlist, admin, shared-secret, constant-time, spring-security, mockmvc, testcontainers]

# Dependency graph
requires:
  - phase: 16-invite-only-access-referral
    provides: "InviteAdminController X-Admin-Token check (deny-by-default, MessageDigest.isEqual), AdminAuthException → generic 401"
  - phase: 17-waitlist-landing-page-api
    provides: "17-01 WaitlistEntry/WaitlistStatus/WaitlistDtos + V24 idx_waitlist_entries_status_confirmed_at; 17-02 WaitlistService.confirm; 17-03 SecurityConfig CORS + permitAll layout"
provides:
  - "common.security.AdminTokenGuard.require(provided: String?) — the single shared operator-endpoint guard"
  - "InviteAdminController delegating to AdminTokenGuard (behavior unchanged)"
  - "GET /api/admin/waitlist?status=&limit= → List<WaitlistEntryResponse>, CONFIRMED-by-default, ordered by confirmedAt asc"
  - "WaitlistEntryRepository.findByStatusOrderByConfirmedAtAscCreatedAtAsc(status, pageable)"
  - "WaitlistService.listByStatus(status, limit) with case-insensitive status and 1..500 limit"
  - "SecurityConfig permitAll for /api/admin/waitlist and /api/admin/waitlist/** (covers 17-05 convert route)"
affects: [17-05, phase-17-verification, gsd-secure-phase]

# Actuals (#2632) — chars/4 over the realized diff (3 new files + edits to 5 existing files)
actuals:
  tokens: 3550
  tasks: 2
  commits: 0
plan_head_before: 6ae4f42581082686024784d210267d5ca2c05edc
plan_head_after: 6ae4f42581082686024784d210267d5ca2c05edc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared @Component guard for permitAll operator routes, called as the first statement of every admin handler"
    - "Spring Data derived query + PageRequest.of(0, limit) for a bounded, ordered operator list"
    - "Mutation check (guard moved after the service call) to prove the auth-before-validation test is load-bearing"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt
    - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistDtos.kt
    - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt

key-decisions:
  - "AdminTokenGuard keeps the app.invite.admin-token key, so invite and waitlist admin share one secret (D-09)"
  - "Out-of-range limit (0, 501) is rejected with 400 rather than clamped; status accepts pending/confirmed/invited case-insensitively, default confirmed"
  - "Deny-by-default test class pins app.invite.admin-token= explicitly, because test application.yml reads INVITE_ADMIN_TOKEN from the environment"

patterns-established:
  - "Operator endpoint: permitAll matcher in SecurityConfig + adminTokenGuard.require(token) as first handler statement"

requirements-completed: [WAIT-04]

coverage:
  - id: D1
    description: "Shared constant-time, deny-by-default AdminTokenGuard; invite issuance behavior unchanged"
    requirement: "WAIT-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt (5 cases + 2 DenyByDefault cases)"
        status: pass
    human_judgment: false
  - id: D2
    description: "GET /api/admin/waitlist lists CONFIRMED entries by default, ordered by confirmedAt asc, no token hash/expiry/normalized key; case-insensitive status; 400 on bad status/limit"
    requirement: "WAIT-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#WaitlistAdminIntegrationTest (11 cases)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Missing/wrong token → generic 401 before parameter validation; blank configured token → 401 for every request"
    requirement: "WAIT-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#wrong token with an invalid status is 401 not 400"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt#WaitlistAdminDenyByDefaultIntegrationTest (2 cases)"
        status: pass
    human_judgment: false

# Metrics
duration: 17min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 04: Shared Admin Token Guard + Waitlist Admin List Summary

**One shared, constant-time, deny-by-default `AdminTokenGuard` now protects both invite issuance and the new `GET /api/admin/waitlist`. The list returns confirmed entries by default, ordered by `confirmed_at` and capped by `limit` (1 to 500), and never includes token material.**

Changes staged, not committed (user's no-auto-commit rule).

## Performance

- **Duration:** 17 min
- **Started:** 2026-10-02T10:02:32Z
- **Completed:** 2026-10-02T10:19:44Z
- **Tasks:** 2
- **Files modified:** 8 (3 created, 5 modified)

## Accomplishments
- The `X-Admin-Token` check moved verbatim into `common.security.AdminTokenGuard`. `InviteAdminController` now calls `adminTokenGuard.require(token)`, so there is no second copy of this security boundary that could drift.
- Added `GET /api/admin/waitlist`, which lists CONFIRMED entries by default, oldest confirmation first. `status` (pending/confirmed/invited) is case-insensitive, `limit` defaults to 100 and allows 1 to 500, and anything else returns 400.
- The guard runs before any parameter parsing or query. A wrong token combined with `status=bogus` returns 401, not 400, and a blank configured token denies every request.
- `/api/admin/waitlist` and `/api/admin/waitlist/**` are permitAll in SecurityConfig, which also whitelists 17-05's convert route ahead of time.

## Staged Files (per task)

| Task | Files staged | Verification |
|------|--------------|--------------|
| 1: Shared X-Admin-Token guard | `common/security/AdminTokenGuard.kt` (new), `invite/controller/InviteAdminController.kt` | `./gradlew test --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest*"`: 7/7 pass (5 + 2 deny-by-default) |
| 2: GET /api/admin/waitlist | `waitlist/model/WaitlistEntryRepository.kt`, `waitlist/service/WaitlistService.kt`, `waitlist/model/WaitlistDtos.kt`, `waitlist/controller/WaitlistAdminController.kt` (new), `common/config/SecurityConfig.kt`, `test/.../waitlist/WaitlistAdminIntegrationTest.kt` (new) | `./gradlew test --tests "com.catspell.api.waitlist.WaitlistAdmin*" --tests "com.catspell.api.invite.InviteAdminEndpointIntegrationTest*"`: 20/20 pass (11 + 2 + 5 + 2) |

**Plan-level verification:** `./gradlew compileKotlin -q` exit 0. The full `./gradlew test` (which includes `com.catspell.api.waitlist.*` and `com.catspell.api.invite.*`) finished BUILD SUCCESSFUL in 12m 32s: 393 tests, 0 failures, 0 errors, 1 skipped. The skip is the pre-existing `FcmSmokeTest`.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt`: shared guard for operator endpoints (`@Value app.invite.admin-token`, `MessageDigest.isEqual`)
- `src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt`: delegates to the guard; private copy and unused imports removed
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt`: derived `findByStatusOrderByConfirmedAtAscCreatedAtAsc(status, pageable)`
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistDtos.kt`: `WaitlistEntryResponse(id, email, status, createdAt, confirmedAt, invitedAt)`
- `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt`: `listByStatus(status, limit)`, read-only transaction
- `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt`: `GET /api/admin/waitlist`, with the guard as its first statement
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt`: permitAll for the admin waitlist prefix
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt`: `WaitlistAdminIntegrationTest` (11 cases) and `WaitlistAdminDenyByDefaultIntegrationTest` (2 cases)

## Decisions Made
- The guard keeps reading `app.invite.admin-token` (D-09): one operator secret, no admin role or RBAC.
- An out-of-range `limit` returns 400 instead of being clamped, so a caller never silently gets fewer rows than it asked for (this was the plan's recorded discretion choice).
- The deny-by-default test class sets `app.invite.admin-token=` explicitly instead of leaving the property out. The test `application.yml` resolves `${INVITE_ADMIN_TOKEN:}`, so a developer with that env var exported would otherwise get a false failure. This matches `InviteAdminEndpointIntegrationTestDenyByDefault`.

## TDD Notes
`workflow.tdd_mode` is false, so the RED gate was not enforced.
- **Task 1** is a pure refactor. The existing `InviteAdminEndpointIntegrationTest` (unchanged) was the behavior contract and stayed green.
- **Task 2:** the tests were written alongside the implementation, not RED-first. To show the tests are load-bearing, I ran a mutation check: I temporarily moved `adminTokenGuard.require(token)` after `waitlistService.listByStatus(...)`, and `wrong token with an invalid status is 401 not 400` failed (exit 1). The controller was then restored byte-for-byte from a backup.

## Deviations from Plan

None. The plan was executed as written. The explicit blank-token property in the deny-by-default class falls within the plan's "no admin-token property — blank" intent and makes it hold regardless of the environment.

## Issues Encountered
None.

## User Setup Required
None. No external service configuration is required. Operators already set `INVITE_ADMIN_TOKEN` for Phase 16, and the same secret now also unlocks the waitlist list.

## Next Phase Readiness
- 17-05 can add `POST /api/admin/waitlist/{id}/invite` to `WaitlistAdminController`, reusing `adminTokenGuard`. SecurityConfig already whitelists `/api/admin/waitlist/**`.
- No blockers.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*

## Self-Check: PASSED

- All 8 key files and this SUMMARY exist on disk and are staged with no unstaged residue (verified via `git diff --cached --name-only` + `git diff --quiet`).
- No commits expected (stage-only rule); HEAD unchanged at 6ae4f42.
- Task acceptance criteria re-run: all PASS. Full suite: 393 tests, 0 failures.
