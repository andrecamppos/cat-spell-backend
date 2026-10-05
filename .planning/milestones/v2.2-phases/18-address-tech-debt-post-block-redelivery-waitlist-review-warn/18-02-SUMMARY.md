---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 02
subsystem: api
tags: [rate-limit, bucket4j, caffeine, dos, wr-10]

requires:
  - phase: 17-waitlist-landing-page-api
    provides: per-key Bucket4j throttles (per-email, per-reporter, per-IP) that this plan bounds
provides:
  - "Shared bounded, access-expiring per-key Bucket4j store: com.catspell.api.common.ratelimit.RateLimitBuckets (Caffeine maximumSize + expireAfterAccess = refill window)"
  - "ReportService per-reporter throttle and the PasswordReset / EmailVerification / EmailChange per-email throttles migrated onto it"
  - "Config key rate-limit.max-tracked-keys (default 100000) read by all four migrated services"
affects: [18-06 WaitlistService migration, 18-07 RateLimitFilter migration, 18-11 application.yml + docs for rate-limit.max-tracked-keys]

actuals:
  tokens: 4700
  tasks: 2
  commits: 0
plan_head_before: 605ddb4d49266e805436c16b18bfc78746c46e40
plan_head_after: 605ddb4d49266e805436c16b18bfc78746c46e40

tech-stack:
  added: ["com.github.ben-manes.caffeine:caffeine 3.2.3 (Spring Boot 4.0.6 BOM-managed, no explicit version)"]
  patterns:
    - "Per-key throttle stores go through RateLimitBuckets(capacity, window, maxKeys) — never a raw ConcurrentHashMap<String, Bucket>"
    - "Cache-time tests inject a fake Caffeine Ticker and a same-thread Executor so expiry and size eviction are deterministic"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/common/ratelimit/RateLimitBuckets.kt
    - src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt
  modified:
    - build.gradle.kts
    - src/main/kotlin/com/catspell/api/moderation/service/ReportService.kt
    - src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt
    - src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt
    - src/main/kotlin/com/catspell/api/auth/service/EmailChangeService.kt

key-decisions:
  - "WR-10: per-key bucket stores use Caffeine expireAfterAccess equal to the refill window (lossless, since an idle key has refilled by then), never expireAfterWrite (which would reset an active attacker's bucket mid-window)"
  - "RateLimitBuckets rejects capacity <= 0, a zero/negative window and maxKeys <= 0 at construction, so a bad config value fails startup"
  - "ReportService was migrated in this plan beyond D-03's named list (RESEARCH recommendation); WaitlistService and RateLimitFilter stay on ConcurrentHashMap until 18-06 / 18-07"

patterns-established:
  - "Shared rate-limit helper: new per-key throttles construct RateLimitBuckets with @Value(\"\\${rate-limit.max-tracked-keys:100000}\")"

requirements-completed: [WAIT-03, MOD-06]

coverage:
  - id: D1
    description: "RateLimitBuckets: same key gives the same bucket, capacity enforced, idle key evicted after one window and recreated full, accessed key kept (no mid-window reset), size bounded by maxKeys, invalid arguments rejected"
    requirement: WAIT-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt (6 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Per-reporter report throttle runs on RateLimitBuckets: over-limit report is 429 while a different reporter is unaffected"
    requirement: MOD-06
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/moderation/ReportServiceIntegrationTest.kt#exceeding per-reporter cap throws 429 while a different reporter is unaffected"
        status: pass
    human_judgment: false
  - id: D3
    description: "Password-reset, resend-verification and change-email per-email throttles migrated with unchanged behavior"
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.catspell.api.auth.* (PasswordResetIntegrationTest 12, EmailVerificationIntegrationTest 7, AccountCredentialsIntegrationTest 6, all auth classes 0 failures)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Caffeine added BOM-managed with no explicit version and no other artifact"
    verification:
      - kind: other
        ref: "./gradlew dependencyInsight --dependency caffeine --configuration runtimeClasspath → com.github.ben-manes.caffeine:caffeine:3.2.3 (selected by rule)"
        status: pass
    human_judgment: false

duration: 22min
completed: 2026-10-03
status: complete
---

# Phase 18 Plan 02: Bounded per-key rate-limit buckets (WR-10) Summary

**One shared, bounded store for per-key Bucket4j buckets, `RateLimitBuckets`, backed by Caffeine (`maximumSize` plus `expireAfterAccess` set to the refill window). ReportService's per-reporter throttle and the three auth services' per-email throttles now use it, so attacker-chosen keys can no longer grow memory without bound.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-10-03T20:52:55Z
- **Completed:** 2026-10-03T21:15:47Z
- **Tasks:** 2 (Task 1 was a tracer)
- **Files modified:** 7 (2 created, 5 modified)

## Accomplishments
- Added `RateLimitBuckets(capacity, window, maxKeys, ticker, executor)`. `bucketFor(key)` creates each bucket atomically through `Cache.get`. Arguments are checked at construction. The KDoc explains why access-based expiry is lossless, why write-based expiry would fail open, and that limits are per instance.
- Added a deterministic unit test (fake `Ticker`, same-thread `Executor`) with 6 cases. They cover identity, capacity, idle eviction followed by a fresh full bucket, no reset of an accessed key, the 1,000-keys-at-maxKeys-10 bound, and argument rejection.
- Migrated four services: ReportService (per-reporter), PasswordResetService, EmailVerificationService and EmailChangeService (per-email). Each reads `rate-limit.max-tracked-keys:100000`. Their existing capacity and refill keys and defaults are unchanged.
- Added Caffeine as `implementation("com.github.ben-manes.caffeine:caffeine")` with no version. The Boot BOM resolves it to 3.2.3.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
|------|--------------|
| Task 1 (RED) | build.gradle.kts, src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt, src/main/kotlin/com/catspell/api/common/ratelimit/RateLimitBuckets.kt (RED stub) |
| Task 1 (GREEN) | build.gradle.kts, src/main/kotlin/com/catspell/api/common/ratelimit/RateLimitBuckets.kt, src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt, src/main/kotlin/com/catspell/api/moderation/service/ReportService.kt |
| Task 2 | src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt, src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt, src/main/kotlin/com/catspell/api/auth/service/EmailChangeService.kt |
| Plan metadata | .planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-02-SUMMARY.md, .planning/STATE.md, .planning/ROADMAP.md, .planning/REQUIREMENTS.md (whichever the state tools changed) |

## TDD Evidence

- **RED:** The test was written first. It ran against a stub `RateLimitBuckets` with the final signature that just lifts the old unbounded `ConcurrentHashMap` pattern (no bound, no expiry, no validation).
  - Command: `./gradlew test --tests "com.catspell.api.common.RateLimitBucketsTest"`. Exit was non-zero, with 6 tests and 3 failed, each on its own assertion:
    - `a key idle for one full window is evicted and comes back as a fresh full bucket`: "the idle key must be evicted after one full window ==> expected: <0> but was: <1>"
    - `the store never holds more than maxKeys entries after maintenance`: "estimatedSize 1000 must be at most maxKeys 10 ==> expected: <true> but was: <false>"
    - `construction rejects a non-positive capacity, window or maxKeys`: "Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown."
  - The 3 tests that keep existing behavior (identity, capacity, accessed key kept) passed against the old pattern, as expected.
  - `gsd-tools check tdd-red-evidence` on the Surefire XML returned `RED_EVIDENCE_OK` (target_test_failed).
- **GREEN:** The stub was replaced with the Caffeine-backed implementation, and the same command passed (tests="6" failures="0" errors="0"). The Task 1 verify (`RateLimitBucketsTest` + `ReportServiceIntegrationTest`) also passed with 6 + 7 tests and 0 failures. The tracer gate re-ran it with `--rerun` and got 6 + 7, 0 failures.
- **REFACTOR:** None needed.

## Verification

- Task 1 `<verify>`: passed. RateLimitBucketsTest 6/6 and ReportServiceIntegrationTest 7/7, including `exceeding per-reporter cap throws 429 while a different reporter is unaffected`.
- Tracer feedback gate: the run is interactive in `end-of-phase` mode and the verify is automated only. The verify was re-run and passed: "Tracer verified end-to-end — expanding".
- Task 2 `<verify>`: passed. PasswordResetIntegrationTest 12, EmailVerificationIntegrationTest 7, AccountCredentialsIntegrationTest 6 and RateLimitBucketsTest 6, all with 0 failures.
- Plan `<verification>` 1: `./gradlew test --tests RateLimitBucketsTest --tests ReportServiceIntegrationTest --tests "com.catspell.api.auth.*"` passed. It covered 11 classes and 80 tests with 0 failures and 0 errors.
- Plan `<verification>` 2: `grep -rn "ConcurrentHashMap<String, Bucket>" src/main/kotlin` lists only WaitlistService.kt:47 and RateLimitFilter.kt:26 (handled by 18-06 / 18-07).
- Acceptance greps all pass:
  - The caffeine line appears once in build.gradle.kts.
  - `expireAfterAccess` appears 1 time and `expireAfterWrite` 0 times.
  - ConcurrentHashMap appears 0 times in each of the 4 migrated services.
  - `RateLimitBuckets(` appears once in each of the 3 auth services.
  - `rate-limit.max-tracked-keys:100000` appears once in each of the 4 services.
  - The three `per-email-capacity:3` keys are unchanged.
  - RateLimitBucketsTest has 6 `@Test` methods.
- The compiled classes for the 4 services reference `common/ratelimit/RateLimitBuckets`. This was checked because the IDE also builds into `build/`.

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/common/ratelimit/RateLimitBuckets.kt`: the shared bounded, access-expiring per-key bucket store (new package `com.catspell.api.common.ratelimit`)
- `src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt`: deterministic helper semantics
- `build.gradle.kts`: BOM-managed Caffeine dependency
- `src/main/kotlin/com/catspell/api/moderation/service/ReportService.kt`: per-reporter buckets moved to the helper, plus the `maxTrackedKeys` constructor parameter
- `src/main/kotlin/com/catspell/api/auth/service/{PasswordResetService,EmailVerificationService,EmailChangeService}.kt`: per-email buckets moved to the helper, plus the `maxTrackedKeys` constructor parameter; `emailBucket(normalizedEmail)` call sites unchanged

## Decisions Made
- Access-based expiry equal to the refill window, never write-based (T-18-06).
- Construction-time `require` checks, so a bad `rate-limit.max-tracked-keys` or capacity value fails startup.
- ReportService was included beyond D-03's named list, following the RESEARCH recommendation that every service using the pattern be migrated. WaitlistService and RateLimitFilter are migrated in 18-06 and 18-07, which already rewrite those files.
- `rate-limit.max-tracked-keys` is read only through the `@Value` default here. 18-11 declares it in application.yml and documents it.

## Deviations from Plan

None. The plan was executed as written.

The RED step used a compiling stub (the old unbounded pattern behind the final signature) instead of relying on a compile failure. The plan allows "fails to compile or fails", and tdd.md classifies a compile error as INVALID_RED, so the stub gives a valid RED on assertions instead.

## Issues Encountered
- **IDE build interference:** The build-file change triggered the VS Code Gradle/Java extension to rebuild into the shared `build/` directory while my Gradle runs were in progress. Two runs failed for environment reasons, one with a `NoSuchFileException` on a class file and one with a `ClassNotFoundException`, plus one spurious `compileKotlin FAILED` that a standalone `compileKotlin` showed to be clean. Re-running after the IDE settled fixed all of them. No code changes were made for this.
- **Known Testcontainers flake:** The first Task 2 run failed every integration test with `ContainerLaunchException: Container startup failed for image postgis/postgis:16-3.4-alpine` (the qemu readiness flake). I restarted the Podman machine and re-ran, and the run passed. Testcontainers and image configuration were not changed.

## Known Stubs

None. The RED stub was replaced by the GREEN implementation before staging Task 1.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness
- 18-06 (WaitlistService) and 18-07 (RateLimitFilter) can construct `RateLimitBuckets` the same way.
- 18-11 must declare `rate-limit.max-tracked-keys` in application.yml and document it.
- Residual risk T-18-05 is accepted: a flood of fresh keys can still evict a throttled key. Limits are per instance, and distributed buckets are deferred.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-03*

## Self-Check: PASSED

- (a) All 8 paths this plan changed (7 code/build files plus this SUMMARY) appear in `git diff --cached --name-only`.
- (b) `git rev-parse HEAD` = 605ddb4d49266e805436c16b18bfc78746c46e40 (unchanged; no commits).
- (c) `git diff --name-only` (unstaged) is empty, so none of this plan's files have unstaged changes.
