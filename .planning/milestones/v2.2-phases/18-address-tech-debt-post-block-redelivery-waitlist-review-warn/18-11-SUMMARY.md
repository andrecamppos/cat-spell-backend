---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
plan: 11
subsystem: docs
tags: [configuration, rate-limit, waitlist, admin-token, review-disposition, phase-gate]

# Dependency graph
requires:
  - phase: 18-02
    provides: "rate-limit.max-tracked-keys on RateLimitBuckets"
  - phase: 18-03
    provides: "hop canonicalization and the one-shot untrusted-XFF WARN"
  - phase: 18-05
    provides: "32-character admin-token startup rule"
  - phase: 18-06
    provides: "resend cooldown, 3-per-24h per-email bucket, pinned address"
  - phase: 18-07
    provides: "rate-limit.waitlist-capacity, rate-limit.admin-capacity, normalized path matching"
  - phase: 18-08
    provides: "app.waitlist.invite-send-timeout-ms and the late-delivery residual"
  - phase: 18-09
    provides: "CORS-readable waitlist 429"
  - phase: 18-10
    provides: "AdminTokenFilter, JWT skip of /api/admin, AUD-01 RED/GREEN evidence"
provides:
  - "Every Phase 18 config key declared in application.yml with env overrides and @Value-identical defaults"
  - "docs/CONFIGURATION.md covering every rate-limit, invite and waitlist env var, plus new Operator Endpoints and Waitlist sections"
  - "17-REVIEW-DISPOSITION.md with an outcome for all 26 findings (24 fixed, 2 deferred, 0 open)"
  - "Phase gate: full suite green (511 tests, 0 failures)"
affects: [phase-18-verification, v2.2-milestone-audit, operators]

# Actuals (#2632): chars/4 over the realized staged diff of this plan's three files
actuals:
  tokens: 5000
  tasks: 2
  commits: 0

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Config key declared in application.yml as ${ENV:default} with the same default as its @Value binding, and documented in the CONFIGURATION.md env table"

key-files:
  created: []
  modified:
    - src/main/resources/application.yml
    - docs/CONFIGURATION.md
    - .planning/phases/17-waitlist-landing-page-api/17-REVIEW-DISPOSITION.md

key-decisions:
  - "Phase 18-11: application.yml now declares rate-limit.capacity/waitlist-capacity/admin-capacity/max-tracked-keys and app.waitlist.resend-cooldown-minutes/invite-send-timeout-ms; WAITLIST_PER_EMAIL_REFILL_HOURS default raised 1 -> 24 to match the 18-06 code default"
  - "Phase 18-11: CONFIGURATION.md tells operators to rotate any non-blank INVITE_ADMIN_TOKEN shorter than 32 characters before deploying, and that the admin throttle (5/min, every request) requires batch scripts to honor Retry-After"
  - "Phase 18-11: Phase 17 disposition record closed at 26 findings (24 fixed, IN-03 and IN-10 deferred per 18-CONTEXT D-02, 0 open); first-review findings dropped by ID reuse re-added as WR-10, WR-11, IN-08..IN-12; audit stale-Bearer item recorded as AUD-01"

patterns-established:
  - "Disposition bookkeeping: a finding dropped by ID reuse is re-added under a fresh ID above both reviews' maxima, with its origin in the title"

requirements-completed: [MOD-02, MOD-03, WAIT-01, WAIT-02, WAIT-03, WAIT-04, INV-02]

coverage:
  - id: D1
    description: "Every Phase 18 key is declared in application.yml with @Value-identical defaults and the file parses as YAML"
    verification:
      - kind: other
        ref: "ruby -ryaml application.yml key/default check (Task 1 verify) -> yaml ok"
        status: pass
    human_judgment: false
  - id: D2
    description: "CONFIGURATION.md lists every rate-limit, invite and waitlist env var and has Operator Endpoints and Waitlist sections; the rate-limit snippet matches application.yml"
    verification:
      - kind: other
        ref: "Task 1 docs grep verify -> docs ok; grep -qxF line-by-line snippet match against application.yml (12/12)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Operator-facing prose is accurate and readable (rotation note, throttle trade-off, late-delivery residual)"
    verification: []
    human_judgment: true
    rationale: "Documentation clarity for operators is a reading judgment; the greps only prove presence"
  - id: D4
    description: "17-REVIEW-DISPOSITION.md records an outcome for all 26 findings, none open"
    verification:
      - kind: other
        ref: "Task 2 disposition verify -> disposition ok; YAML frontmatter parse: 26 findings, 24 fixed, 2 deferred, 0 open"
        status: pass
    human_judgment: false
  - id: D5
    description: "Phase gate: the full test suite passes"
    verification:
      - kind: integration
        ref: "./gradlew test --rerun -Pkotlin.compiler.execution.strategy=in-process -> BUILD SUCCESSFUL in 14m 39s, 80 classes, 511 tests, 0 failures, 0 errors, 1 skipped"
        status: pass
    human_judgment: false

# Metrics
duration: 16 min
completed: 2026-10-04
status: complete
---

# Phase 18 Plan 11: Config declarations, operator docs and the Phase 17 disposition record Summary

**application.yml now declares the four rate-limit keys, the waitlist resend cooldown and invite send timeout, and the 24 h per-email refill. CONFIGURATION.md documents every rate-limit, invite and waitlist env var, the three per-IP bucket families, hop canonicalization, the one-shot WARN, the CORS-readable 429, the 32-character admin-token startup rule and the late-delivery residual. The Phase 17 disposition record closes at 26 findings, 0 open. The full suite passed: 511 tests, 0 failures.**

## Performance

- **Duration:** 16 min
- **Started:** 2026-10-04T16:20:31Z
- **Completed:** 2026-10-04T16:36:28Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- **application.yml (Task 1).** Under `rate-limit:` it adds `capacity` (RATE_LIMIT_CAPACITY, 10), `waitlist-capacity` (RATE_LIMIT_WAITLIST_CAPACITY, 10), `admin-capacity` (RATE_LIMIT_ADMIN_CAPACITY, 5) and `max-tracked-keys` (RATE_LIMIT_MAX_TRACKED_KEYS, 100000). Under `app.waitlist:` it adds `resend-cooldown-minutes` (WAITLIST_RESEND_COOLDOWN_MINUTES, 15) and `invite-send-timeout-ms` (WAITLIST_INVITE_SEND_TIMEOUT_MS, 10000), and the refill default goes from 1 to 24. The admin-token comment now states the blank / 32-character rule. I checked every default against the staged `@Value` bindings in RateLimitFilter, WaitlistService, WaitlistConfirmEmailRenderer, ReportService, the three auth services and AdminTokenGuard. All are identical.
- **docs/CONFIGURATION.md (Task 1).**
  - The env table has 10 new rows.
  - The Rate Limiting snippet is the exact `rate-limit:` block from application.yml. The section was rewritten to cover the three families, bounded memory, normalized path matching, hop canonicalization with fail-safe fallback to the peer, the one-shot WARN, the CORS-readable 429 and the server-to-server requirement. The `ip:port` unsupported-shape bullet is removed.
  - New `### Operator Endpoints` section: blank / under 32 characters fails startup / rotate before deploying, one filter on every `/api/admin` path, Authorization ignored there, the throttle and Retry-After guidance.
  - New `### Waitlist` section: cooldown, 3 per 24 h, pinned address, rendered copy, invite send timeout, late-delivery residual, and the provider I/O requirement.
  - Production row, a Caffeine dependency row, and five Business Limits rows.
- **17-REVIEW-DISPOSITION.md (Task 2).**
  - WR-01..WR-08, IN-01, IN-02 and IN-04..IN-07 are `fixed`, each with its Phase 18 plans in the Source cell. Before marking a row fixed, I checked that every plan it cites has a SUMMARY with `status: complete` and Self-Check PASSED.
  - IN-03 is `deferred` (D-02).
  - New rows WR-10, WR-11, IN-08, IN-09, IN-11, IN-12 and AUD-01 are `fixed`. IN-10 is `deferred`.
  - CR-01, CR-02 and WR-09 are unchanged.
  - Frontmatter: `open: 0`, `total: 26`, new `recorded:` timestamp. The footer is byte-identical.
- **Phase gate.** `./gradlew test --rerun -Pkotlin.compiler.execution.strategy=in-process` gave BUILD SUCCESSFUL in 14m 39s: 80 classes, 511 tests, 0 failures, 0 errors. The 1 skip is FcmSmokeTest's real-Firebase dry run, which needs credentials and is a pre-existing skip. ChatIntegrationTest's known intermittent failure did not occur.

## Staged Files

Stage-only run (the user's no-commit rule for this repository). No commits were made. HEAD is still `605ddb4`.

| Task | Paths staged |
|------|--------------|
| Task 1: declare and document Phase 18 config | `src/main/resources/application.yml`, `docs/CONFIGURATION.md` |
| Task 2: disposition record + phase gate | `.planning/phases/17-waitlist-landing-page-api/17-REVIEW-DISPOSITION.md` |
| Plan metadata | `.planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-11-SUMMARY.md`, `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md` (whichever changed) |

## Files Created/Modified

- `src/main/resources/application.yml`: six new key declarations, refill default 24, extended admin-token comment.
- `docs/CONFIGURATION.md`: env table rows, rewritten Rate Limiting section, new Operator Endpoints and Waitlist sections, Production row, Caffeine row, Business Limits rows. The first-line marker is kept.
- `.planning/phases/17-waitlist-landing-page-api/17-REVIEW-DISPOSITION.md`: 26 findings, 24 fixed, 2 deferred, 0 open.

## Verification Evidence

- Task 1 YAML verify: `yaml ok`. The tracer gate (interactive, end-of-phase mode, automated-only verify) re-ran it after the docs edits and it passed again, so expansion continued with no checkpoint.
- Task 1 docs verify: `docs ok`. `grep -c "ip:port.*give each connection its own key"` gives 0. `head -1` is still `<!-- generated-by: gsd-doc-writer -->`. `git diff --stat -- src/main/kotlin` is empty. All 12 config lines in the doc snippets match application.yml exactly (`grep -qxF`).
- Task 2 verify: `disposition ok`. All 8 new IDs are present. A Ruby YAML parse of the frontmatter gives 26 findings: 24 fixed, 2 deferred, 0 open.
- Full suite: as above, green.

## Final Disposition Counts

| Disposition | Count | IDs |
|-------------|-------|-----|
| fixed | 24 | WR-01..WR-11, IN-01, IN-02, IN-04..IN-09, IN-11, IN-12, CR-01, CR-02, AUD-01 |
| deferred | 2 | IN-03, IN-10 |
| open | 0 | - |

## Decisions Made

- I documented the convert endpoint under its real route, `POST /api/admin/waitlist/{id}/invite`. The plan text says "convert", which is the operation's name, not its path.
- I described the misconfiguration WARN as "once per application instance". 18-03 guards it per filter instance, and there is one filter bean per process.
- The docs say `WAITLIST_RESEND_COOLDOWN_MINUTES=0` disables the cooldown. With 0, the rotate UPDATE's `updatedAt <= now` condition always holds, and the renderer drops the cooldown clause at 0 (18-04).

## Deviations from Plan

None. The plan was executed as written. No Kotlin source changed.

## Issues Encountered

None. The suite passed on its first run, with no container flake and no ChatIntegrationTest intermittent.

## Known Stubs

None.

## User Setup Required

None for this plan. Operators upgrading must rotate any non-blank `INVITE_ADMIN_TOKEN` shorter than 32 characters before deploying. CONFIGURATION.md documents this.

## Next Phase Readiness

All 11 Phase 18 plans have SUMMARYs. The phase is ready for orchestrator-owned verification and phase completion. The Phase 17 production-proxy blocker is still open in STATE: confirm the proxy shape and set `RATE_LIMIT_TRUSTED_PROXIES` and `WAITLIST_ALLOWED_ORIGINS`.

## Self-Check: PASSED

- (a) All three task files are in `git diff --cached --name-only`, and so is this SUMMARY once staged.
- (b) `git rev-parse HEAD` = `605ddb4d49266e805436c16b18bfc78746c46e40`.
- (c) `git diff --name-only` (unstaged) lists none of this plan's files. These checks were re-run after staging.

---
*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Completed: 2026-10-04*
