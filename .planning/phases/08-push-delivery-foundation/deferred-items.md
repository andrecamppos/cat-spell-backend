# Deferred Items — Phase 8 (Push Delivery Foundation)

Out-of-scope discoveries found during execution. NOT fixed in Phase 8 (scope boundary:
pre-existing issues unrelated to the current task).

## Pre-existing flaky DiscoveryIntegrationTest failures (shared-DB test pollution)

**Discovered during:** Phase 8 regression gate (`./gradlew test` full suite).

**Symptom:** In the full suite, `DiscoveryIntegrationTest` intermittently fails:
- `human card appears in mixed feed for catless user` (line ~526)
- `feed shows cats from profiles without bio` (line ~385)

**Root cause (pre-existing, NOT caused by Phase 8):**
- All `@SpringBootTest` integration tests share ONE static `PostgreSQLContainer`
  (`BaseIntegrationTest` companion object) with no per-test cleanup / transactional
  rollback. Rows created by earlier tests persist into later tests.
- Several existing tests create complete-profile users at the same coordinates
  (NYC `40.7128,-74.0060`). The two failing assertions request the discovery feed
  WITHOUT a large `pageSize`, so once enough complete-profile users accumulate near
  those coords, the target card is pushed past the default page and the assertion fails.
- The failures are order-dependent: they reproduce (and vary: 2–3 failures) **with the
  push package excluded entirely**, and `DiscoveryIntegrationTest` passes in isolation.

**Evidence Phase 8 is not the cause:**
- `./gradlew test --tests "com.catspell.api.discovery.DiscoveryIntegrationTest"` → PASS (isolated).
- Full suite WITHOUT the `com.catspell.api.push` package → still FAILS (3 failed).
- Phase 8's own tests create users with NO `user_profiles` row and NO location, so they
  cannot appear in the discovery feed and cannot add to the NYC card overflow.
- The entire push suite passes; `FcmSmokeTest` is skipped as designed.

**Suggested fix (future work, own task):** Make integration tests isolate DB state —
e.g. per-class schema reset / truncation between tests, unique coordinates per test, or
assert against feeds requested with an explicit large `pageSize`. This is a test-harness
improvement spanning prior phases, not Phase 8 scope.
