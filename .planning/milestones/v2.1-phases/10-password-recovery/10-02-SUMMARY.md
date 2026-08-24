---
phase: 10-password-recovery
plan: 02
subsystem: auth
tags: [jpa, hibernate, flyway, postgres, sha-256, password-reset]

# Dependency graph
requires:
  - phase: 01-auth (RefreshToken/V2 pattern)
    provides: RefreshToken entity + repository + V2 migration used as the exact template
provides:
  - PasswordResetToken JPA entity (hashed, single-use, expiring reset-token store)
  - PasswordResetTokenRepository with indexed findByTokenHash redemption lookup
  - Flyway V15 migration creating password_reset_tokens table + indexes
affects: [10-password-recovery Plan 03 (reset service), Plan 04 (integration test)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hashed single-use token store mirroring RefreshToken (SHA-256 token_hash, not BCrypt)"
    - "Entity/migration column parity for Hibernate validate (prod) + create-drop (tests)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/auth/model/PasswordResetToken.kt
    - src/main/kotlin/com/catspell/api/auth/model/PasswordResetTokenRepository.kt
    - src/main/resources/db/migration/V15__create_password_reset_tokens_table.sql
  modified: []

key-decisions:
  - "Stored only the SHA-256 token_hash column (no raw-token column) so a DB leak cannot yield live tokens (RECOV-05, T-10-04)."
  - "Used a nullable used_at Instant as the single-use marker, replacing RefreshToken's revoked/replaced_by fields."
  - "token_hash is UNIQUE + non-null with a dedicated unique index for atomic single-lookup redemption."

patterns-established:
  - "Pattern 1: Reset-token entity mirrors RefreshToken (UUID PK, @ManyToOne user, timestamps, equals/hashCode by id)."
  - "Pattern 2: V15 DDL column names/types match entity @Column mappings exactly to avoid schema drift (Pitfall #4)."

requirements-completed: [RECOV-05]

coverage:
  - id: D1
    description: "PasswordResetToken entity + repository storing only a SHA-256 token_hash with indexed findByTokenHash lookup"
    requirement: "RECOV-05"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin"
        status: pass
    human_judgment: false
  - id: D2
    description: "V15 Flyway migration creating password_reset_tokens with unique token_hash index, matching the entity schema"
    requirement: "RECOV-05"
    verification:
      - kind: integration
        ref: "./gradlew compileTestKotlin (schema exercised end-to-end by PasswordResetIntegrationTest in Plan 04 under Testcontainers)"
        status: pass
    human_judgment: false

# Metrics
duration: 6min
completed: 2026-08-08
status: complete
---

# Phase 10 Plan 02: Reset-Token Store Summary

**Durable hashed single-use password-reset token store: PasswordResetToken entity + repository + Flyway V15 migration, mirroring the proven RefreshToken pattern.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-08-08
- **Completed:** 2026-08-08
- **Tasks:** 2 completed
- **Files modified:** 3 created

## Accomplishments
- Created the `PasswordResetToken` JPA entity that persists ONLY the SHA-256 `token_hash` (no raw token), with a nullable `used_at` single-use marker and an `expires_at` expiry.
- Added `PasswordResetTokenRepository` exposing `findByTokenHash` (indexed redemption lookup) and `findAllByUserAndUsedAtIsNull` (invalidate prior unused tokens).
- Authored Flyway `V15__create_password_reset_tokens_table.sql` with a UNIQUE `token_hash` index and a `user_id` FK to `users(id) ON DELETE CASCADE`, matching the entity schema exactly.

## Task Commits

Each task was committed atomically:

1. **Task 1: Create the PasswordResetToken entity + repository** - `1dea950` (feat)
2. **Task 2: Create the V15 Flyway migration** - `db79b32` (feat)

**Plan metadata:** docs commit for SUMMARY/STATE/ROADMAP/REQUIREMENTS.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/auth/model/PasswordResetToken.kt` - Hashed single-use reset-token JPA entity.
- `src/main/kotlin/com/catspell/api/auth/model/PasswordResetTokenRepository.kt` - Spring Data repository with `findByTokenHash`.
- `src/main/resources/db/migration/V15__create_password_reset_tokens_table.sql` - Flyway migration creating the table + indexes.

## Decisions Made
- Stored only the SHA-256 hash (no raw-token column) per RECOV-05 / RESEARCH divergence (SHA-256 deterministic, not BCrypt) — enables single unique-index lookup.
- `used_at` (nullable Instant) is the single-use marker, replacing RefreshToken's `revoked`/`replaced_by`.

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None. (`compileKotlin` initially reported UP-TO-DATE from a prior build cache; a `--rerun-tasks` compile confirmed the new classes compile cleanly. Pre-existing `!!` warnings in `DiscoveryService.kt` are unrelated to this plan.)

## User Setup Required
None - no external service configuration required. Flyway auto-runs V15 on app boot.

## Next Phase Readiness
- Plan 03's `PasswordResetService` can now persist an issued token's hash and redeem it atomically via `findByTokenHash`.
- Plan 04's integration test will exercise the schema end-to-end under Testcontainers.

---
*Phase: 10-password-recovery*
*Completed: 2026-08-08*

## Self-Check: PASSED

- All 3 source files + SUMMARY exist on disk.
- Commits `1dea950` (entity+repo) and `db79b32` (V15 migration) present in git log.
- `./gradlew compileKotlin` and `./gradlew compileTestKotlin` both BUILD SUCCESSFUL.
