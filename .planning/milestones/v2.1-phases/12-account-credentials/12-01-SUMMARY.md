---
phase: 12-account-credentials
plan: 01
subsystem: database
tags: [jpa, postgres, flyway, kotlin, tokens]

requires:
  - phase: 11-email-verification
    provides: EmailVerificationToken/Repository + V16 migration analog copied here
provides:
  - EmailChangeRequest JPA entity (with new_email column)
  - EmailChangeRequestRepository (findByTokenHash, findAllByUserAndUsedAtIsNull, atomic markUsed)
  - Flyway V18 migration creating email_change_requests table
affects: [12-03, 12-05, account-credentials]

tech-stack:
  added: []
  patterns:
    - "Atomic single-use token claim via conditional UPDATE (usedAt IS NULL guard)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/auth/model/EmailChangeRequest.kt
    - src/main/kotlin/com/catspell/api/auth/model/EmailChangeRequestRepository.kt
    - src/main/resources/db/migration/V18__create_email_change_requests_table.sql
  modified: []

key-decisions:
  - "Mirrored EmailVerificationToken exactly (D-04), adding only the new_email column to distinguish a pending change request from a verification token."

patterns-established:
  - "email_change_requests is a separate table from users and email_verification_tokens, carrying the pending new address plus a single-use hashed token."

requirements-completed: [ACCT-03, ACCT-04]

coverage:
  - id: D1
    description: "EmailChangeRequest entity + email_change_requests table persist a pending email change (user_id, new_email, token_hash, expires_at, used_at, created_at)."
    requirement: "ACCT-04"
    verification:
      - kind: integration
        ref: "com.catspell.api.CatSpellApplicationTests (Flyway applies V18 on boot)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Atomic single-use markUsed claim (UPDATE ... WHERE usedAt IS NULL) so concurrent double-confirm applies at most once."
    requirement: "ACCT-04"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin (JPQL + @Modifying compile-verified); behavioral proof deferred to 12-05"
        status: pass
    human_judgment: false

duration: 5min
completed: 2026-08-19
status: complete
---

# Phase 12 Plan 01: Email Change Request Persistence Summary

**EmailChangeRequest JPA entity + repository with atomic single-use markUsed, backed by Flyway V18 email_change_requests table (separate from users and email_verification_tokens).**

## Performance

- **Duration:** 5 min
- **Started:** 2026-08-19T13:57:20Z
- **Completed:** 2026-08-19T14:02:31Z
- **Tasks:** 3
- **Files modified:** 3 (created)

## Accomplishments
- `EmailChangeRequest` entity mirrors `EmailVerificationToken` with a new `new_email` column and identity-based equals/hashCode.
- `EmailChangeRequestRepository` exposes `findByTokenHash`, `findAllByUserAndUsedAtIsNull`, and the atomic `markUsed` conditional UPDATE (returns rows-updated).
- Flyway `V18__create_email_change_requests_table.sql` creates `email_change_requests` with an ON DELETE CASCADE FK to `users`, a unique `token_hash`, and both `idx_email_change_requests_*` indexes; verified to apply cleanly on Spring context boot.

## Task Commits

1. **Task 1: Create EmailChangeRequest JPA entity** - `478315b` (feat)
2. **Task 2: Create EmailChangeRequestRepository with atomic markUsed** - `1da6546` (feat)
3. **Task 3: Create Flyway V18 migration for email_change_requests** - `5578c44` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/model/EmailChangeRequest.kt` - Pending email-change entity with new_email + single-use token fields.
- `src/main/kotlin/com/catspell/api/auth/model/EmailChangeRequestRepository.kt` - Repository with lookup + prior-invalidation + atomic markUsed claim.
- `src/main/resources/db/migration/V18__create_email_change_requests_table.sql` - DDL for the email_change_requests table + indexes.

## Decisions Made
- Followed plan/D-04 exactly: copied the EmailVerificationToken machinery and added only the `new_email` column.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. The Hibernate `HHH000478: Unsuccessful: drop table ...` lines emitted during test-context shutdown are pre-existing benign shutdown-hook noise (they list every table, including pre-existing ones), not a migration failure — the boot test exited 0 with V18 applied.

## Next Phase Readiness
- Storage backbone ready for 12-03 (`EmailChangeService` + `AuthService.confirmEmailChange`) which consume this repository.

---
*Phase: 12-account-credentials*
*Completed: 2026-08-19*

## Self-Check: PASSED
