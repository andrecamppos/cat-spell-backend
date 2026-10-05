---
phase: 17-waitlist-landing-page-api
plan: 01
subsystem: api
tags: [waitlist, flyway, jpa, postgres, on-conflict, bucket4j, spring-security, sha-256]

# Dependency graph
requires:
  - phase: 16-invite-only-access-referral
    provides: hashed single-use token helpers (InviteService), conditional-UPDATE repository style, InviteMigrationTest validate harness
  - phase: 11-email-verification
    provides: per-email Bucket4j bucket pattern (EmailVerificationService), GenericMessageResponse 202 shape
provides:
  - V24 waitlist_entries table (unique normalized_email, unique hashed confirm token, status CHECK)
  - com.catspell.api.waitlist model/service/controller package (WaitlistEntry, WaitlistStatus, WaitlistEntryRepository, JoinWaitlistRequest, WaitlistEmailNormalizer, WaitlistService.join, WaitlistController)
  - public POST /api/waitlist returning one constant 202 body (WAITLIST_JOIN_MESSAGE)
  - SecurityConfig permitAll for POST /api/waitlist and GET /api/waitlist/confirm
  - full app.waitlist.* config block (8 keys) in both application.yml files
affects: [17-02, 17-03, 17-04, 17-05, 17-06]

# Actuals (#2632) — chars/4 over the staged diff
actuals:
  tokens: 7234
  tasks: 2
  commits: 0
plan_head_before: 6ae4f42581082686024784d210267d5ca2c05edc
plan_head_after: 6ae4f42581082686024784d210267d5ca2c05edc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Native INSERT ... ON CONFLICT (normalized_email) DO NOTHING as the race guard (no DataIntegrityViolationException catch inside @Transactional)"
    - "Conditional JPQL UPDATE with enum status guard (rotatePendingToken) returning Int"
    - "Request DTO trims the submitted value before bean validation (plain class, not data class)"

key-files:
  created:
    - src/main/resources/db/migration/V24__create_waitlist_entries.sql
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistStatus.kt
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntry.kt
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt
    - src/main/kotlin/com/catspell/api/waitlist/model/WaitlistDtos.kt
    - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt
    - src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt
    - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistEmailNormalizerTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt
    - src/main/resources/application.yml
    - src/test/resources/application.yml

key-decisions:
  - "JoinWaitlistRequest trims the email before bean validation so a space-padded address is accepted rather than rejected with 400 by @Email (required by the D-03 must-have truth)"
  - "rotatePendingToken result is kept in a local `rotated` (suppressed unused) so plan 17-02 can gate the confirmation-email event on it"

patterns-established:
  - "Waitlist identity = D-03 normalized email; the same key drives the UNIQUE constraint and the per-email bucket"
  - "Every public waitlist branch (new, pending, confirmed, invited, throttled) returns the identical 202 body"

requirements-completed: [WAIT-01, WAIT-02, WAIT-03]

coverage:
  - id: D1
    description: "Unauthenticated POST /api/waitlist returns 202 with WAITLIST_JOIN_MESSAGE and persists one PENDING row with a 64-hex SHA-256 confirm token hash and a non-null expiry"
    requirement: WAIT-01
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt#unauthenticated join returns 202 and persists one PENDING entry with a hashed confirm token"
        status: pass
    human_judgment: false
  - id: D2
    description: "V24 schema and WaitlistEntry entity agree under Flyway + ddl-auto=validate"
    requirement: WAIT-02
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt (5 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "WaitlistEmailNormalizer D-03 rules (trim, lowercase, last-@ split, +suffix strip unless leading, dots preserved, never throws)"
    requirement: WAIT-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistEmailNormalizerTest.kt (8 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "+suffix/case variants share one row and one bucket; dotted addresses stay distinct; the (capacity+1)th join returns a byte-identical 202 with hash and email unchanged; buckets are per address"
    requirement: WAIT-03
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt (3 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Concurrent joins for one normalized email draw from a single shared bucket so at most per-email-capacity tokens are minted per refill window"
    requirement: WAIT-03
    verification: []
    human_judgment: true
    rationale: "Backstop truth per the plan: only the last hash survives in the DB, so a multi-thread mint count is not observable from tests; relies on ConcurrentHashMap.computeIfAbsent + Bucket.tryConsume semantics (code review)"

# Metrics
duration: 16min
completed: 2026-10-02
status: complete
---

# Phase 17 Plan 01: Waitlist Join Tracer Summary

**Public `POST /api/waitlist` that deduplicates on a D-03 normalized email through a native `ON CONFLICT` upsert, rotates a SHA-256-hashed 7-day confirm token on PENDING entries only, throttles each normalized email with a silent Bucket4j bucket, and always returns one identical 202 body, all on the new V24 schema.**

Changes staged, not committed (user's no-auto-commit rule).

## Performance

- **Duration:** 16 min
- **Started:** 2026-10-02T08:47:08Z
- **Completed:** 2026-10-02T09:03:12Z
- **Tasks:** 2
- **Files modified:** 14 (11 created, 3 modified)

## Accomplishments
- V24 `waitlist_entries` migration with `uq_waitlist_entries_normalized_email`, `uq_waitlist_entries_confirm_token_hash`, `chk_waitlist_entries_status` and `idx_waitlist_entries_status_confirmed_at`. The table holds email only (D-05).
- `WaitlistEntryRepository.insertIfAbsent` (native `ON CONFLICT (normalized_email) DO NOTHING`) and `rotatePendingToken` (conditional UPDATE guarded by `e.status = :pending`). `findByNormalizedEmail` is also added.
- `WaitlistService.join`: normalize, then the per-email bucket check (before any DB call), then upsert, then token mint/hash/rotate. It never logs the token or the email.
- `WaitlistController` POST returns the constant `WAITLIST_JOIN_MESSAGE` 202. `SecurityConfig` whitelists `POST /api/waitlist` and `GET /api/waitlist/confirm`.
- The 8-key `app.waitlist.*` block is in both `application.yml` files, as the contract for plans 17-02/03/05.

## Task Outcomes (staged, not committed)

| Task | Files staged | Verification |
|------|--------------|--------------|
| 1. Tracer: POST /api/waitlist end-to-end (type=tracer) | V24__create_waitlist_entries.sql, WaitlistStatus.kt, WaitlistEntry.kt, WaitlistEntryRepository.kt, WaitlistDtos.kt, WaitlistEmailNormalizer.kt, WaitlistService.kt, WaitlistController.kt, SecurityConfig.kt, WaitlistJoinIntegrationTest.kt | `./gradlew test --tests WaitlistJoinIntegrationTest --tests InviteMigrationTest`: PASS (1 + 5 tests). All 6 acceptance criteria PASS. Tracer gate: interactive/end-of-phase/automated-only, re-verified, expanded. |
| 2. Normalization, per-email throttle, config (tdd=true) | WaitlistService.kt, WaitlistDtos.kt (deviation fix), application.yml (main + test), WaitlistEmailNormalizerTest.kt, WaitlistPerEmailLimitIntegrationTest.kt | RED first: 2 of 3 per-email tests failed (over-limit join rotated the token; padded email got 400). GREEN: `./gradlew test --tests WaitlistEmailNormalizerTest --tests WaitlistPerEmailLimitIntegrationTest`: PASS (8 + 3 tests). YAML grep check PASS. All 4 acceptance criteria PASS. |

Plan-level verification: `./gradlew compileKotlin -q` exit 0. The full `./gradlew test` run passed: 339 tests, 0 failures, 0 errors, 1 skipped (pre-existing `FcmSmokeTest`).

## TDD Notes (Task 2)
- **RED:** `WaitlistEmailNormalizerTest` passed against the Task 1 normalizer, as the plan expected. `WaitlistPerEmailLimitIntegrationTest` failed on its intended assertion: "e@ is over its limit" got a rotated hash. It also failed on an unexpected 400 for `" B+Tag@Example.COM "` (see deviation 1).
- **GREEN:** Added the per-email bucket plus the trim-before-validation DTO. All tests pass.
- **REFACTOR:** None needed.
- `workflow.tdd_mode=false`, so the RED/GREEN commit gate does not apply. Staging was used instead of commits.

## Files Created/Modified
- `src/main/resources/db/migration/V24__create_waitlist_entries.sql`: waitlist table, constraints, index.
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistStatus.kt`: PENDING/CONFIRMED/INVITED.
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntry.kt`: JPA entity matching V24, with `unique = true` on the normalized email and the hash.
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt`: native upsert, conditional rotate, lookup.
- `src/main/kotlin/com/catspell/api/waitlist/model/WaitlistDtos.kt`: `JoinWaitlistRequest` (trimmed, `@NotBlank @Email @Size(255)`).
- `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt`: D-03 normalizer object.
- `src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt`: join with per-email bucket and token rotation.
- `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt`: public POST, constant 202.
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt`: permitAll for the two public waitlist routes.
- `src/main/resources/application.yml`, `src/test/resources/application.yml`: `app.waitlist.*` block.
- `src/test/kotlin/com/catspell/api/waitlist/{WaitlistJoinIntegrationTest,WaitlistEmailNormalizerTest,WaitlistPerEmailLimitIntegrationTest}.kt`: tests.

## Decisions Made
- `JoinWaitlistRequest` trims the email in its constructor so `@Email`/`@Size` validate the trimmed value. Whitespace-only input still fails `@NotBlank` (400).
- `rotated` is kept as a local (with an unused warning suppressed) per the plan. Plan 17-02 gates the confirmation event on it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Space-padded email rejected with 400 before normalization**
- **Found during:** Task 2 (RED run of WaitlistPerEmailLimitIntegrationTest)
- **Issue:** Hibernate Validator's `@Email` rejects `" B+Tag@Example.COM "` because of the surrounding whitespace. The service-level trim never ran, which broke the must-have truth that this address dedupes with `b@example.com`.
- **Fix:** Changed `JoinWaitlistRequest` from a data class to a plain class whose `email` property is `email.trim()`, so bean validation sees the trimmed value.
- **Files modified:** src/main/kotlin/com/catspell/api/waitlist/model/WaitlistDtos.kt
- **Verification:** The boundary test now passes (202, one row). The tracer test and the full suite are still green.
- **Staged in:** Task 2 staging

**2. [Rule 2 - Missing critical] `@SecurityRequirements` on the public join endpoint**
- **Found during:** Task 1
- **Issue:** Public auth endpoints carry `@SecurityRequirements` so OpenAPI does not show a JWT requirement on them. The plan's controller spec did not include it.
- **Fix:** Added `@SecurityRequirements` to `WaitlistController.join`, mirroring `AuthController.resendVerification`.
- **Files modified:** src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt
- **Verification:** Compiles. The tracer test passes.

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both are small and needed for the stated truths and for consistency with the docs. No scope creep.

## Known Stubs
- `WaitlistService.join`: the `rotated` result is not used yet, and no confirmation email is sent. This is intentional: the event publish and email belong to plan 17-02. The token is already minted and hashed.
- `GET /api/waitlist/confirm` is whitelisted but has no handler yet, so it returns 404. The handler is plan 17-02's scope.

## Issues Encountered
- The full test suite takes about 10 minutes. It was run in the background and finished green.

## User Setup Required
None. No external service configuration is required. The new `WAITLIST_*` env vars all have local defaults.

## Next Phase Readiness
- Ready for 17-02 (confirm email + confirm endpoint), 17-03 (per-IP throttle/CORS) and 17-06 (contract tests), which build on this join slice and config block.
- Changes are staged but not committed. The user needs to review and commit.

---
*Phase: 17-waitlist-landing-page-api*
*Completed: 2026-10-02*

## Self-Check: PASSED
- All 14 plan files plus this SUMMARY exist on disk and show in `git diff --cached --name-only`. Staging was checked in place of commits, per the no-auto-commit override. HEAD is still 6ae4f42, so no commits were made.
