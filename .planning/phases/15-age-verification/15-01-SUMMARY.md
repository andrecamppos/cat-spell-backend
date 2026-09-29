---
phase: 15-age-verification
plan: 01
subsystem: auth
tags: [kotlin, spring-boot, age-verification, problem-detail, rfc7807, junit5]

# Dependency graph
requires:
  - phase: 14 (prior identity/auth work)
    provides: EmailNotVerifiedException 403+code handler pattern reused for the 422 mapping
provides:
  - AgeVerifier seam interface (single-method requireAdult)
  - LocalAgeVerifier default @Component with configurable minimum age
  - UnderMinimumAgeException mapped to HTTP 422 + code=UNDER_MINIMUM_AGE
  - app.age.minimum-age config key (default 18)
  - Boundary unit tests for the age math
affects: [15-02 register age gate, discovery DOB repoint]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Narrow seam interface + default @Component (mirrors EmailSender/PushProvider) so a vendor age verifier can drop in without call-site changes (D-03)"
    - "Domain exception → RFC 7807 ProblemDetail with machine code property (mirrors EMAIL_NOT_VERIFIED)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/age/AgeVerifier.kt
    - src/main/kotlin/com/catspell/api/age/LocalAgeVerifier.kt
    - src/test/kotlin/com/catspell/api/age/LocalAgeVerifierTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt
    - src/main/resources/application.yml

key-decisions:
  - "Under-18 block surfaces as 422 UNPROCESSABLE_ENTITY + code=UNDER_MINIMUM_AGE, NOT a 400 Bean Validation error (D-08)"
  - "Age math lives only in LocalAgeVerifier; no @ConditionalOnProperty since the interface is the swap point (D-07)"
  - "Single global minimum via app.age.minimum-age (default 18); per-jurisdiction routing deferred"

patterns-established:
  - "AgeVerifier seam: call sites depend only on the interface"
  - "422 ProblemDetail carries no PII — only generic detail + machine code"

requirements-completed: [AGE-02]

coverage:
  - id: D1
    description: "LocalAgeVerifier enforces the minimum age via Period-based math (exactly-18 passes, 17y364d throws)"
    requirement: "AGE-02"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/age/LocalAgeVerifierTest.kt"
        status: pass
    human_judgment: false
  - id: D2
    description: "UnderMinimumAgeException maps to HTTP 422 + code=UNDER_MINIMUM_AGE with no PII in the body"
    requirement: "AGE-02"
    verification:
      - kind: automated
        ref: "grep UNPROCESSABLE_ENTITY + code=UNDER_MINIMUM_AGE in GlobalExceptionHandler.kt; ./gradlew compileKotlin"
        status: pass
    human_judgment: false
  - id: D3
    description: "app.age.minimum-age config key resolves (default 18)"
    requirement: "AGE-02"
    verification:
      - kind: automated
        ref: "application.yml app.age.minimum-age present; consumed via @Value in LocalAgeVerifier"
        status: pass
    human_judgment: false

# Metrics
duration: 12 min
completed: 2026-09-29
status: complete
---

# Phase 15 Plan 01: AgeVerifier Seam & Under-18 Error Contract Summary

**AgeVerifier seam with a Period-based LocalAgeVerifier default impl, plus an UnderMinimumAgeException → HTTP 422 `code=UNDER_MINIMUM_AGE` mapping and an `app.age.minimum-age` config key (default 18).**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-09-29T09:29:15Z
- **Tasks:** 2
- **Files modified:** 6 (3 created, 3 modified)

## Accomplishments
- Introduced the `AgeVerifier` interface (single `requireAdult(LocalDate)` method) as the future vendor swap point (D-03).
- Added `LocalAgeVerifier` `@Component` computing age via `Period.between(...).years` against the configured minimum (D-07).
- Mapped `UnderMinimumAgeException` to a 422 RFC 7807 ProblemDetail carrying `code=UNDER_MINIMUM_AGE` and no PII (D-08/D-09).
- Added the `app.age.minimum-age` config key (default 18) bound via `@Value`, matching the project's `app.*` convention.
- Boundary unit tests cover exactly-18 (pass), 18y+1d (pass), 17y364d (throw), and clearly-under (throw).

## Task Commits

1. **Task 1: UnderMinimumAgeException + 422 handler + app.age.minimum-age** - `0f9c11c` (feat)
2. **Task 2: AgeVerifier seam + LocalAgeVerifier + boundary tests** - `00aacd7` (feat)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/age/AgeVerifier.kt` - seam interface
- `src/main/kotlin/com/catspell/api/age/LocalAgeVerifier.kt` - default `@Component` impl reading `app.age.minimum-age`
- `src/test/kotlin/com/catspell/api/age/LocalAgeVerifierTest.kt` - boundary math unit tests
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` - added `UnderMinimumAgeException`
- `src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt` - added `handleUnderMinimumAge` (422 + code)
- `src/main/resources/application.yml` - added `app.age.minimum-age` (default 18)

## Decisions Made
- Followed plan as specified. 422 (not 400), single-rule location, and no `@ConditionalOnProperty` all per plan decisions D-07/D-08.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. (A KDoc comment initially mentioned `@ConditionalOnProperty`, which tripped the "no @ConditionalOnProperty" grep check; reworded before commit so the artifact truly contains none.)

## User Setup Required
None - no external service configuration required. `AGE_MINIMUM_AGE` env var is optional (defaults to 18).

## Next Phase Readiness
- Seam, exception, handler, and config are green and independently verified.
- 15-02 can now wire `AgeVerifier.requireAdult` into `AuthService.register`, relocate DOB to `users`, and repoint DOB readers.

---
*Phase: 15-age-verification*
*Completed: 2026-09-29*
