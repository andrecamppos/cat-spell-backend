---
phase: 16-invite-only-access-referral
plan: 01
subsystem: database
tags: [flyway, postgres, jpa, invite, referral, hashed-token, single-use]

requires:
  - phase: 15-age-verification
    provides: register-time ordered-guard composition the invite gate slots into (16-04)
  - phase: 10-12-account-recovery
    provides: hashed single-use token discipline (SecureRandom + SHA-256 + atomic claim) reused by InviteService (16-02)
provides:
  - V23 migration creating invites (hashed code, single-use consumed_at, nullable referrer_user_id, no expiry/revoke) + referrals (referrer/invitee/invite FKs, no-self CHECK, unique invitee)
  - com.catspell.api.invite.model package — Invite + Referral entities, InviteRepository (findByCodeHash, atomic markConsumed), ReferralRepository
  - app.invite.enabled (default false) and app.invite.admin-token (default empty = deny-by-default) in both application.yml files
affects: [invite-service, invite-admin-endpoint, auth-register]

tech-stack:
  added: []
  patterns:
    - "Atomic single-use claim via conditional UPDATE (WHERE consumed_at IS NULL) returning Int — transcribed from PasswordResetTokenRepository.markUsed"
    - "SHA-256 hash-at-rest for invite codes (code_hash VARCHAR(64), raw code never persisted)"

key-files:
  created:
    - src/main/resources/db/migration/V23__create_invites_and_referrals.sql
    - src/main/kotlin/com/catspell/api/invite/model/Invite.kt
    - src/main/kotlin/com/catspell/api/invite/model/Referral.kt
    - src/main/kotlin/com/catspell/api/invite/model/InviteRepository.kt
    - src/main/kotlin/com/catspell/api/invite/model/ReferralRepository.kt
    - src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt
  modified:
    - src/main/resources/application.yml
    - src/test/resources/application.yml

key-decisions:
  - "referrer_user_id and consumed_by are raw nullable UUID columns (not @ManyToOne) — the service only needs the id (D-11)"
  - "No expiry/revocation columns in invites — deferred (D-04, D-05)"
  - "admin-token default is empty string (deny-by-default); no guessable dev default"

patterns-established:
  - "New com.catspell.api.invite domain package mirroring moderation/ layout"
  - "Atomic single-use claim repository method returning rows-updated Int"

requirements-completed: [INV-04, INV-05]

coverage:
  - id: D1
    description: "V23 creates invites + referrals with the expected columns, UNIQUE(code_hash), UNIQUE(invitee_id), and chk_referrals_no_self CHECK; entities validate against the schema under Flyway + ddl-auto=validate"
    requirement: "INV-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt (5 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "InviteRepository.markConsumed is the atomic single-use claim (conditional UPDATE WHERE consumed_at IS NULL); referrals attribution constraints enforced at the storage tier"
    requirement: "INV-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt#two referrals with the same invitee_id violate uq_referrals_invitee"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-10-01
status: complete
---

# Phase 16 Plan 01: Invite Persistence & Config Foundation Summary

**V23 Flyway migration + com.catspell.api.invite.model package (Invite/Referral entities, atomic single-use InviteRepository) + app.invite.* deny-by-default config keys, proven by a Flyway-enabled migration test.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 3
- **Files modified:** 8 (6 created, 2 modified)

## Accomplishments
- V23 migration: `invites` (code_hash UNIQUE hashed-at-rest, nullable referrer_user_id, single-use consumed_at, no expiry/revoke columns) and `referrals` (referrer/invitee/invite FKs, chk_referrals_no_self CHECK, uq_referrals_invitee UNIQUE)
- `Invite`/`Referral` JPA entities + `InviteRepository` (findByCodeHash + atomic `markConsumed` conditional UPDATE) + `ReferralRepository`
- `app.invite.enabled` (default false) and `app.invite.admin-token` (default empty = deny-by-default) added to both main and test application.yml
- `InviteMigrationTest` boots with Flyway enabled + ddl-auto=validate and proves column shape plus the UNIQUE(invitee_id) and no-self CHECK rejections

## Task Commits

1. **Task 1: V23 migration + app.invite.* config keys** - `15c950e` (feat)
2. **Task 2: Invite/Referral entities and repositories** - `6758502` (feat)
3. **Task 3: InviteMigrationTest** - `ea1f43e` (test)

## Files Created/Modified
- `src/main/resources/db/migration/V23__create_invites_and_referrals.sql` - invites + referrals DDL
- `src/main/kotlin/com/catspell/api/invite/model/Invite.kt` - invites entity (hashed code, single-use state)
- `src/main/kotlin/com/catspell/api/invite/model/Referral.kt` - referrals entity
- `src/main/kotlin/com/catspell/api/invite/model/InviteRepository.kt` - findByCodeHash + atomic markConsumed
- `src/main/kotlin/com/catspell/api/invite/model/ReferralRepository.kt` - JpaRepository<Referral, UUID>
- `src/main/resources/application.yml` / `src/test/resources/application.yml` - app.invite.* keys
- `src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt` - V23 schema proof

## Decisions Made
- `referrer_user_id`/`consumed_by` modeled as raw nullable `UUID` columns (not `@ManyToOne`) per D-11 — the service is id-only.
- Omitted expiry/revocation columns per D-04/D-05.
- `admin-token` defaults to empty string so an unconfigured deployment denies all admin issuance.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None during this plan. Note for downstream/verification: `InviteMigrationTest` enables Flyway while the default test profile uses `create-drop`. It passes in isolation; the first full-suite run must confirm the Flyway-enabled context does not clash with the shared Testcontainers DB used by create-drop contexts. (Tracked at phase verification.)

## User Setup Required
None - no external service configuration required. (Operators set `INVITE_ENABLED` / `INVITE_ADMIN_TOKEN` env vars only when turning the gate on; surfaced in later plans.)

## Next Phase Readiness
- Tables, entities, repositories, and config keys exist — ready for 16-02 (InviteService + error contract).
- `InviteRepository.markConsumed` is the atomic claim consumed by `InviteService.consume`.

---
*Phase: 16-invite-only-access-referral*
*Completed: 2026-10-01*
