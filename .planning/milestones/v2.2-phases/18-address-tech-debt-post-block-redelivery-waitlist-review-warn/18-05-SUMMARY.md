---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 05
subsystem: security
tags: [admin-token, startup-validation, spring-boot, application-context-runner, testcontainers]

requires:
  - phase: 17
    provides: AdminTokenGuard shared X-Admin-Token check for invite issuance and waitlist admin routes
provides:
  - "AdminTokenGuard fails application startup when app.invite.admin-token is non-blank and shorter than 32 characters (D-13, WR-08 minimum-length half)"
  - "AdminTokenGuard.MIN_ADMIN_TOKEN_LENGTH = 32 (companion const val)"
  - "Shared 34-character test constant com.catspell.api.TEST_ADMIN_TOKEN used by every admin-token test context"
  - "AdminTokenGuardStartupTest: ApplicationContextRunner proof of the startup rule (no Testcontainers)"
affects: [18-07, 18-10, 18-11]

actuals:
  tokens: 4926
  tasks: 2
  commits: 0
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40

tech-stack:
  added: []
  patterns:
    - "Startup validation of a secret in the bean's init block via check() with a fixed, never-interpolated message"
    - "One top-level test const (TEST_ADMIN_TOKEN) imported into @TestPropertySource string templates"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/TestAdminToken.kt
    - src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt

key-decisions:
  - "Phase 18-05 (D-13, WR-08): AdminTokenGuard fails startup in its init block when app.invite.admin-token is non-blank and under MIN_ADMIN_TOKEN_LENGTH (32); blank still starts and denies every operator request; the failure message is fixed text and never contains the token"
  - "Phase 18-05: every admin-token test context uses the shared 34-character com.catspell.api.TEST_ADMIN_TOKEN; the blank-token deny-by-default classes keep app.invite.admin-token= and now send TEST_ADMIN_TOKEN as their plausible header"

patterns-established:
  - "Secret-length startup checks are proven with ApplicationContextRunner (hasFailed + root-cause message assertion that the value is absent)"

requirements-completed: [INV-02, WAIT-04]

coverage:
  - id: D1
    description: "A non-blank admin token under 32 characters stops application startup with a fixed message that omits the token; blank and 32+ characters start; require() keeps constant-time compare and deny-by-default"
    requirement: "INV-02"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt (4 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "All four admin-token integration suites (and their deny-by-default siblings) start and pass on the shared 34-character TEST_ADMIN_TOKEN"
    requirement: "WAIT-04"
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.catspell.api.waitlist.WaitlistConvertIntegrationTest --tests 'com.catspell.api.invite.*' --tests com.catspell.api.waitlist.WaitlistAdminIntegrationTest --tests com.catspell.api.common.AdminTokenGuardStartupTest (+ both waitlist DenyByDefault classes): 61 tests, 0 failures"
        status: pass
    human_judgment: false

duration: 11min
completed: 2026-10-03
status: complete
---

# Phase 18 Plan 05: Admin Token Minimum Length Summary

**AdminTokenGuard now refuses to start when `app.invite.admin-token` is non-blank and shorter than 32 characters. The failure message is fixed text that never contains the token. All admin-token test contexts now use one shared 34-character `TEST_ADMIN_TOKEN`.**

## Performance

- **Duration:** 11 min of active execution (2026-10-03T22:01:27Z to 22:12:26Z); the closeout was delayed by two machine-sleep interruptions
- **Started:** 2026-10-03T22:01:27Z
- **Completed:** 2026-10-04 (SUMMARY and state closeout)
- **Tasks:** 2
- **Files modified:** 7 (2 created, 5 modified)

## Accomplishments

- WR-08 minimum-length half (D-13): `AdminTokenGuard` has a companion `const val MIN_ADMIN_TOKEN_LENGTH = 32` and an `init` block: `check(adminToken.isBlank() || adminToken.length >= MIN_ADMIN_TOKEN_LENGTH)`, message `app.invite.admin-token must be blank (operator endpoints disabled) or at least 32 characters`. `require` is unchanged: constant-time `MessageDigest.isEqual`, deny-by-default when blank.
- `AdminTokenGuardStartupTest` has 4 tests using ApplicationContextRunner, with no Testcontainers and a run time of milliseconds:
  - a blank token starts the context;
  - a 31-character token fails, with an `IllegalStateException` root cause whose message contains `at least 32 characters` and does not contain the token;
  - a 32-character token starts the context;
  - a guard built directly accepts only the exact token and rejects `null`.
- The new shared constant `com.catspell.api.TEST_ADMIN_TOKEN` (`test-admin-secret-0123456789abcdef`, 34 characters) replaces the 17-character `test-admin-secret` in:
  - WaitlistAdminIntegrationTest
  - WaitlistConvertIntegrationTest
  - InviteAdminEndpointIntegrationTest
  - InviteGateIntegrationTest

  The old literal is gone from `src/test/kotlin`.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
| ---- | ------------ |
| Task 1 (RED) | src/test/kotlin/com/catspell/api/TestAdminToken.kt, src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt |
| Task 1 (GREEN) | src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt, src/test/kotlin/com/catspell/api/TestAdminToken.kt, src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt, src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt |
| Task 2 | src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt, src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt, src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt |
| Plan metadata | .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-05-SUMMARY.md, .planning/STATE.md, .planning/ROADMAP.md (and .planning/REQUIREMENTS.md if it changed) |

## TDD Evidence

**RED** (Task 1, before any change to `AdminTokenGuard.kt`):
- Command: `./gradlew test --tests "com.catspell.api.common.AdminTokenGuardStartupTest"`, which exited 1.
- Result: 4 tests, 3 passed, 1 failed. The target test `31-character admin token fails startup without revealing the token()` failed on its assertion: `Expecting: <Started application [AnnotationConfigApplicationContext ...]> to have failed`. The context started because there was no length check yet.
- `gsd-tools check tdd-red-evidence` on the persisted record, built from that run's JUnit XML, returned `RED_EVIDENCE_OK` (`target_test_failed`).
- An earlier attempt did not compile: a `Throwable?` type mismatch in the test, fixed with `requireNotNull`. That attempt was INVALID_RED and is not counted.
- The test files were staged at this point.

**GREEN:**
- Command: `./gradlew test --rerun -Pkotlin.compiler.execution.strategy=in-process --tests "com.catspell.api.common.AdminTokenGuardStartupTest" --tests "com.catspell.api.waitlist.WaitlistAdminIntegrationTest"`, which exited 0.
- Result: AdminTokenGuardStartupTest 4/4 and WaitlistAdminIntegrationTest 11/11, with 0 failures and 0 errors.
- The files were staged at this point.

**REFACTOR:** none needed.

**Task 2 and plan-level verification:**
- Command: `./gradlew test --rerun -Pkotlin.compiler.execution.strategy=in-process`, filtered to WaitlistConvertIntegrationTest, `com.catspell.api.invite.*`, WaitlistAdminIntegrationTest, WaitlistAdminDenyByDefaultIntegrationTest, WaitlistConvertDenyByDefaultIntegrationTest and AdminTokenGuardStartupTest. It exited 0 (BUILD SUCCESSFUL in 4m 29s).
- Result: 13 classes, 61 tests, 0 failures, 0 errors. This includes InviteAdminEndpointIntegrationTest 5/5, InviteGateIntegrationTest 2/2, WaitlistConvertIntegrationTest 16/16, and all three blank-token deny-by-default classes 2/2 each.

**Tracer gate:** this was an interactive run in end-of-phase mode with an automated-only `<verify>`. The verify was re-run and passed, so expansion continued with no checkpoint.

## Files Created/Modified

- `src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt`: adds the `init` startup check, the `MIN_ADMIN_TOKEN_LENGTH` companion constant, and a KDoc note on D-13.
- `src/test/kotlin/com/catspell/api/TestAdminToken.kt`: new file holding the top-level `const val TEST_ADMIN_TOKEN` (34 characters).
- `src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt`: new file with the 4 ApplicationContextRunner and direct-guard tests.
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt`: `ADMIN_SECRET` replaced by the imported `TEST_ADMIN_TOKEN`.
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt`: `CONVERT_ADMIN_SECRET` replaced by `TEST_ADMIN_TOKEN`.
- `src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt`: the `@TestPropertySource` and 3 header literals now use `TEST_ADMIN_TOKEN`.
- `src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt`: the `@TestPropertySource` token now uses `TEST_ADMIN_TOKEN`; the `app.invite.enabled=true` entry is unchanged.

## Decisions Made

- The startup check lives in the guard's `init` block (`check()`), so a short token fails at bean creation. The message is a fixed literal and the token is never interpolated (T-18-15).
- The two waitlist deny-by-default classes used the deleted private secret constant as their "plausible header". They now send `TEST_ADMIN_TOKEN`. Their `app.invite.admin-token=` config and their assertions are unchanged. `wrong-secret` and `anything` header values are kept as they were.

## Deviations from Plan

None in scope. The plan was executed as written.

Execution notes (not code deviations):
- The Kotlin daemon repeatedly failed with "Could not close incremental caches ... source-to-output.tab is already registered". This looks like the IDE sharing `build/`. Gradle runs used `-Pkotlin.compiler.execution.strategy=in-process` on the command line only, with no repository config change.
- One successful run's `build/test-results` directory vanished afterwards (again the IDE touching `build/`). It was re-run with `--rerun`, and the XML was copied straight into the scratchpad.

## Issues Encountered

- The environmental Kotlin daemon and `build/` interference above; resolved without touching project files.

## User Setup Required

None. D-13 is rated **costly**: any deployment whose `INVITE_ADMIN_TOKEN` is non-blank and under 32 characters will not start until the token is rotated. Plan 18-11 documents this in docs/CONFIGURATION.md.

## Next Phase Readiness

- Ready for 18-06. The per-IP admin throttle (other half of D-13) is 18-07. The central guard (D-11) and the stale-Bearer fix (D-12) are 18-10.

## Self-Check: PASSED

- (a) All 7 plan paths appear in `git diff --cached --name-only`: AdminTokenGuard.kt, TestAdminToken.kt, AdminTokenGuardStartupTest.kt, WaitlistAdminIntegrationTest.kt, WaitlistConvertIntegrationTest.kt, InviteAdminEndpointIntegrationTest.kt, InviteGateIntegrationTest.kt.
- (b) `git rev-parse HEAD` is 605ddb4d49266e805436c16b18bfc78746c46e40.
- (c) `git diff --name-only` (unstaged) lists none of this plan's files.
- Acceptance criteria:
  - `const val MIN_ADMIN_TOKEN_LENGTH = 32` appears exactly once (line 44).
  - TEST_ADMIN_TOKEN appears 11 times in WaitlistAdminIntegrationTest.
  - AdminTokenGuardStartupTest has 4 `@Test` methods, all passing.
  - `grep -rn 'test-admin-secret"' src/test/kotlin` finds nothing.
  - 3 of the 3 Task 2 files reference TEST_ADMIN_TOKEN.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-04*
