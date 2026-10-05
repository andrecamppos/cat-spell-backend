---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 04
subsystem: waitlist
tags: [kotlin, spring-boot, email, uricomponentsbuilder, waitlist, double-opt-in]

requires:
  - phase: 17-waitlist-landing-page-api
    provides: WaitlistConfirmEmailRenderer, WaitlistInviteEmailRenderer, WaitlistController confirm redirect
provides:
  - Truthful confirmation-email copy (newest link only, re-join for a fresh link, cooldown rendered from config)
  - TTL rendered from app.waitlist.confirm-token-ttl-hours (days when a multiple of 24, else hours)
  - Confirm and invite links built with UriComponentsBuilder (query-string-safe)
  - WaitlistController redirect URIs parsed once at construction (fail-fast on malformed config)
affects: [18-06 resend cooldown, 18-11 application.yml declaration of app.waitlist.resend-cooldown-minutes]

actuals:
  tokens: 4316
  tasks: 2
  commits: 0

tech-stack:
  added: []
  patterns:
    - "Config-driven email copy: durations rendered from the same @Value keys/defaults the service uses"
    - "Parse operator URLs in the constructor so misconfiguration fails bean creation, not a request"

key-files:
  created:
    - src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt
    - src/test/kotlin/com/catspell/api/email/WaitlistInviteEmailRendererTest.kt
    - src/test/kotlin/com/catspell/api/waitlist/WaitlistControllerUrlTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt
    - src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt
    - src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt

key-decisions:
  - "Confirm-email renderer binds app.waitlist.confirm-token-ttl-hours:168 and app.waitlist.resend-cooldown-minutes:15 only to render copy; the cooldown itself stays 18-06's job"
  - "TTL formatter: whole days when hours is a positive multiple of 24, otherwise hours; singular for 1 (1 day / 1 hour / 1 minute)"
  - "A cooldown of 0 or less drops the 'we send at most one new link every N minutes' clause rather than rendering 0 minutes"
  - "WaitlistController keeps one error URL (D-09); the success/error URIs are parsed at construction, the redirect contract (302, no-referrer, no-store) is unchanged"

patterns-established:
  - "Link building: UriComponentsBuilder.fromUriString(base).queryParam(name, value).build().toUriString() for any configured base URL"

requirements-completed: [WAIT-02, WAIT-04]

coverage:
  - id: D1
    description: "Confirmation email no longer claims a used link means the spot is confirmed; says only the newest link works and re-joining sends a fresh one subject to the cooldown"
    requirement: WAIT-02
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt#default config renders the 7 day expiry, the newest-link rule and the cooldown in both bodies"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt#a zero cooldown still says to join again but renders no minute count"
        status: pass
    human_judgment: false
  - id: D2
    description: "Expiry rendered from app.waitlist.confirm-token-ttl-hours (168 -> 7 days, 24 -> 1 day, 36 -> 36 hours, 1 -> 1 hour)"
    requirement: WAIT-02
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt#a 24 hour TTL renders as 1 day / a TTL that is not a whole number of days renders in hours / a 1 hour TTL renders in the singular"
        status: pass
    human_judgment: false
  - id: D3
    description: "Confirm and invite links built with UriComponentsBuilder; existing query strings get & appended; default links unchanged"
    requirement: WAIT-04
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt#a confirm URL that already has a query gets the token appended with an ampersand"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/email/WaitlistInviteEmailRendererTest.kt#an invite URL that already has a query gets the code appended with an ampersand"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt (16 tests, catspell://register?code=<code>)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Malformed confirm-success-url / confirm-error-url throws IllegalArgumentException at bean construction; redirect behavior unchanged"
    requirement: WAIT-02
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistControllerUrlTest.kt (4 tests)"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt (11 tests, token regex, ?token= link, Location headers)"
        status: pass
    human_judgment: false

duration: 9min
completed: 2026-10-03
status: complete
---

# Phase 18 Plan 04: Honest confirm-email copy, config-driven TTL, safe link building and fail-fast redirect URLs Summary

**The waitlist confirmation email now tells users only the newest link works and that re-joining sends a fresh one (at most once per configured cooldown), renders its expiry from `app.waitlist.confirm-token-ttl-hours`, both email links are built with UriComponentsBuilder, and WaitlistController parses its redirect URLs once at startup.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-10-03T21:49:09Z
- **Completed:** 2026-10-03T21:58:32Z
- **Tasks:** 2
- **Files modified:** 6 (3 source, 3 new test files)

## Accomplishments

- WR-03 (copy half) / D-09: the false "If the page says this link was already used, your spot is still confirmed" sentence is gone from both bodies.
- IN-08 (first-review IN-01): the hardcoded "7 days" is replaced by a TTL rendered from config.
- IN-12 (first-review IN-05): the confirm and invite links use `UriComponentsBuilder`, so a configured URL with a query (`https://x.example/c?a=b`) gets `&token=` / `&code=` appended. The default links are byte-identical to before.
- IN-07: `WaitlistController` builds `successUri` / `errorUri` in the constructor. A malformed value fails bean creation (startup) instead of returning an error after a user's token was claimed.

## Final copy (verbatim, default config: TTL 168 h, cooldown 15 min)

HTML body:
- "Tap the link below to confirm your spot. It works once and expires in 7 days."
- "Only the link in the most recent email from us works. If you need a new link, join the waitlist again; we send at most one new link every 15 minutes."

Text body:
- "Open this link to confirm your spot (it works once and expires in 7 days):"
- "Only the link in the most recent email from us works. If you need a new link, join the waitlist again; we send at most one new link every 15 minutes."

With a cooldown of 0 or less the sentence ends after the first clause: "Only the link in the most recent email from us works. If you need a new link, join the waitlist again." A cooldown of 1 renders "every 1 minute.". TTL rendering: 168 → `7 days`, 24 → `1 day`, 36 → `36 hours`, 1 → `1 hour`.

Subject (unchanged): "Confirm your spot on the Cat Spell waitlist".

## Files Created/Modified

- `src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt` - new `confirmTokenTtlHours` / `resendCooldownMinutes` constructor bindings, UriComponentsBuilder link, private `newestLinkNotice()` / `formatTtl()` / `plural()` helpers, KDoc describing the D-09 copy contract
- `src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt` - invite link via UriComponentsBuilder (nothing else changed)
- `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt` - URL constructor params are no longer properties; `private val successUri` / `errorUri` parsed at construction; `confirm` selects `if (waitlistService.confirm(token)) successUri else errorUri`; KDoc extended (IN-07)
- `src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt` - 8 plain JUnit 5 cases (copy, TTL formats, cooldown 0 and 1, query-string URL, recipient/subject)
- `src/test/kotlin/com/catspell/api/email/WaitlistInviteEmailRendererTest.kt` - 3 cases (default deep link, query-string URL, raw code on its own line)
- `src/test/kotlin/com/catspell/api/waitlist/WaitlistControllerUrlTest.kt` - 4 mockk cases (malformed success URL, malformed error URL, success redirect + headers, error redirect + headers)

## Decisions Made

- The renderer reads `app.waitlist.resend-cooldown-minutes` (default 15) only to render copy. 18-06 adds the same key with the same default to WaitlistService, and 18-11 declares it in application.yml.
- The TTL formatter uses days only when hours is a positive multiple of 24, otherwise hours, with the singular for 1.
- The `@Value` keys and defaults on WaitlistController are unchanged, and there is still one error URL (D-09).

## Deviations from Plan

None. The plan was executed as written. One addition to the test list: a `1 minute` singular case in WaitlistConfirmEmailRendererTest, which the plan's action step 4 implies ("Use `minute` for N == 1") but its behavior list does not name.

## TDD Evidence

**Task 1 (tracer, tdd="true")**
- Before RED, the two new constructor parameters were added to the renderer with no behavior change. That let the test compile, so RED fails on assertions and not on a missing constructor.
- RED: `./gradlew test --tests "com.catspell.api.email.WaitlistConfirmEmailRendererTest"` exited non-zero with `8 tests completed, 7 failed`. Each failure was an `org.opentest4j.AssertionFailedError` in a target case: 7-day/most-recent/15-minutes copy (line 31), 1 day (41), 36 hours (46), 1 hour (51), 1 minute (57), zero cooldown (65), and `?a=b&token=` (75). The recipient/subject case passed as expected, since that behavior is unchanged.
- GREEN: `./gradlew test --tests "com.catspell.api.email.WaitlistConfirmEmailRendererTest" --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest"` gave BUILD SUCCESSFUL. WaitlistConfirmEmailRendererTest had 8 tests, 0 failures. WaitlistConfirmIntegrationTest had 11 tests, 0 failures.
- Tracer gate: the run is interactive, `human_verify_mode` is end-of-phase, and `<verify>` is automated only. The tracer verify was re-run and passed, so the plan moved on to Task 2 with no checkpoint.
- REFACTOR: none needed.

**Task 2 (auto, tdd="true")**
- RED: `./gradlew test --tests "com.catspell.api.email.WaitlistInviteEmailRendererTest" --tests "com.catspell.api.waitlist.WaitlistControllerUrlTest"` exited non-zero with `7 tests completed, 3 failed`. All 3 were `AssertionFailedError`s: `an invite URL that already has a query gets the code appended with an ampersand` (line 27), `a malformed success URL fails construction`, and `a malformed error URL fails construction`. The two construction cases failed because `assertThrows<IllegalArgumentException>` saw nothing thrown. The 4 unchanged-behavior cases passed.
- GREEN: `./gradlew test --tests "com.catspell.api.email.*" --tests "com.catspell.api.waitlist.WaitlistControllerUrlTest" --tests "com.catspell.api.waitlist.WaitlistConfirmIntegrationTest" --tests "com.catspell.api.waitlist.WaitlistConvertIntegrationTest"` gave BUILD SUCCESSFUL with 46 tests and 0 failures: EmailSenderContractTest 2, EmailSenderSelectionTest 2, WaitlistConfirmEmailRendererTest 8, WaitlistInviteEmailRendererTest 3, WaitlistConfirmIntegrationTest 11, WaitlistControllerUrlTest 4, WaitlistConvertIntegrationTest 16.
- REFACTOR: none needed.

`gsd-tools check tdd-red-evidence` was not run. The RED runs' JUnit XML reports were overwritten by the later GREEN runs before a record could be saved. The evidence above was taken from the RED runs' console output.

## Issues Encountered

- The VS Code Java/Gradle extension was rebuilding into the shared `build/` directory while Gradle ran, which caused three spurious failures:
  - Kotlin incremental-cache corruption (`Storage ... is already registered`).
  - A `FileNotFoundException` for a main class that was deleted in the middle of the compile.
  - One run that reported "No tests found" for WaitlistConfirmIntegrationTest.
- None of these came from code changes. Waiting for the IDE build to settle and re-running fixed all three. No configuration was changed.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 18-06 can add the cooldown with `app.waitlist.resend-cooldown-minutes` (default 15). The copy already renders that value.
- 18-11 still needs to declare `app.waitlist.resend-cooldown-minutes` in application.yml. Until then, the default of 15 applies everywhere.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
| ---- | ------------ |
| 1 | `src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt`, `src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt` |
| 2 | `src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt`, `src/test/kotlin/com/catspell/api/email/WaitlistInviteEmailRendererTest.kt`, `src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt`, `src/test/kotlin/com/catspell/api/waitlist/WaitlistControllerUrlTest.kt` |
| Plan metadata | `.planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-04-SUMMARY.md`, `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md` (whichever changed) |

## Self-Check: PASSED

- (a) All 6 code paths and this SUMMARY are in `git diff --cached --name-only`.
- (b) `git rev-parse HEAD` = `605ddb4d49266e805436c16b18bfc78746c46e40`.
- (c) `git diff --name-only` (unstaged) lists none of this plan's files.
- Every acceptance grep passed:
  - Task 1: `still confirmed` 0, `expires in 7 days` 0, `token=$rawToken` 0, `UriComponentsBuilder` 3, `confirm-token-ttl-hours:168` 1.
  - Task 2: `code=$rawCode` 0, `URI.create(target)` 0, `private val successUri` 1, 4 `@Test` methods in WaitlistControllerUrlTest, all passing.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-03*
