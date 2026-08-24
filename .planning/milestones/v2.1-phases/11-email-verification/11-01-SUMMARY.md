---
phase: 11-email-verification
plan: 01
subsystem: database
tags: [jpa, postgres, flyway, hibernate, email-verification, single-use-token]

# Dependency graph
requires:
  - phase: 10-password-recovery
    provides: PasswordResetToken entity/repository + V15 migration used as the exact structural template
provides:
  - EmailVerificationToken JPA entity (hashed, single-use) mapping email_verification_tokens
  - EmailVerificationTokenRepository with findByTokenHash + atomic markUsed single-use guard
  - users.email_verified_at nullable column + User.emailVerifiedAt field
  - Flyway V16 (token table) and V17 (email_verified_at + grandfather backfill)
affects: [11-03 EmailVerificationService, 11-03 login hard-gate, 11-04 HTTP surface]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hashed single-use token store mirroring PasswordResetToken (store SHA-256 hash only, atomic conditional UPDATE claim)"
    - "Grandfather backfill via set-based WHERE ... IS NULL UPDATE (idempotent, empty-table safe)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/auth/model/EmailVerificationToken.kt
    - src/main/kotlin/com/catspell/api/auth/model/EmailVerificationTokenRepository.kt
    - src/main/resources/db/migration/V16__create_email_verification_tokens_table.sql
    - src/main/resources/db/migration/V17__add_email_verified_at_to_users.sql
  modified:
    - src/main/kotlin/com/catspell/api/auth/model/User.kt

key-decisions:
  - "Backfill email_verified_at = created_at for pre-existing rows (D-09 discretion) so no current account is locked out (VERIFY-05)"
  - "Store only the SHA-256 token_hash; no raw-token column persisted (T-11-01)"

patterns-established:
  - "Single-use token redemption via @Modifying markUsed with `usedAt IS NULL` guard closing the read-check-write race"

requirements-completed: [VERIFY-02, VERIFY-05]

coverage:
  - id: D1
    description: "EmailVerificationToken entity + repository store only the SHA-256 hash and expose findByTokenHash + atomic single-use markUsed (VERIFY-02)"
    requirement: "VERIFY-02"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin"
        status: pass
    human_judgment: true
    rationale: "Compilation proves the entity/repository shape; end-to-end persist/lookup/single-use behavior is exercised under Testcontainers by Plan 04/05 integration tests, which do not exist yet."
  - id: D2
    description: "V16 creates email_verification_tokens (unique token_hash) and V17 adds nullable email_verified_at + grandfathers existing rows (VERIFY-05)"
    requirement: "VERIFY-05"
    verification:
      - kind: other
        ref: "./gradlew compileTestKotlin"
        status: pass
    human_judgment: true
    rationale: "Migrations only apply on a live Flyway/Testcontainers boot; the grandfather backfill and no-lockout behavior are asserted by Plan 04/05 integration tests still to be written."

# Metrics
duration: 6min
completed: 2026-08-12
status: complete
---

# Phase 11 Plan 01: Verification-token store + email_verified_at Summary

**Hashed single-use email-verification token store (entity + repository + Flyway V16) plus a grandfathered nullable `users.email_verified_at` column (V17), mirroring Phase 10's PasswordResetToken layer.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-08-12T21:08:09Z
- **Completed:** 2026-08-12T21:14:23Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- `EmailVerificationToken` entity + `EmailVerificationTokenRepository` storing only the SHA-256 hash, with `findByTokenHash`, `findAllByUserAndUsedAtIsNull`, and an atomic `markUsed` conditional UPDATE (`usedAt IS NULL` guard) closing the single-use race (VERIFY-02).
- Flyway `V16` creates `email_verification_tokens` with a unique `token_hash`, a `user_id` FK `ON DELETE CASCADE`, and matching indexes — column names/types parity with the entity so Hibernate `ddl-auto validate` agrees.
- Flyway `V17` adds the nullable `email_verified_at TIMESTAMPTZ` and grandfathers every pre-existing row (`WHERE email_verified_at IS NULL`) so no current account is locked out (VERIFY-05); `User.emailVerifiedAt` maps it.

## Task Commits

1. **Task 1: EmailVerificationToken entity + repository** - `67940ab` (feat)
2. **Task 2: V16 + V17 migrations + User.emailVerifiedAt** - `1fd400a` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/model/EmailVerificationToken.kt` - Hashed single-use verification-token JPA entity
- `src/main/kotlin/com/catspell/api/auth/model/EmailVerificationTokenRepository.kt` - Repository with findByTokenHash + atomic markUsed
- `src/main/kotlin/com/catspell/api/auth/model/User.kt` - Added nullable `emailVerifiedAt`
- `src/main/resources/db/migration/V16__create_email_verification_tokens_table.sql` - Token table + indexes
- `src/main/resources/db/migration/V17__add_email_verified_at_to_users.sql` - Add column + grandfather backfill

## Decisions Made
- Grandfather backfill value = `created_at` (D-09 discretion) — deterministic, set-based, idempotent.
- Persist only the SHA-256 hash; no raw-token column (T-11-01).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. (A transient Kotlin incremental-compilation cache error surfaced during `compileKotlin`/`compileTestKotlin` but the build recovered and reported `BUILD SUCCESSFUL` in both cases.)

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- DB layer ready: Plan 03's `EmailVerificationService` can persist token hashes and claim them atomically; the login gate has `email_verified_at` to read.
- Schema parity verified via compilation; full runtime migration behavior is exercised by Plan 04/05 integration tests.

## Self-Check: PASSED

---
*Phase: 11-email-verification*
*Completed: 2026-08-12*
