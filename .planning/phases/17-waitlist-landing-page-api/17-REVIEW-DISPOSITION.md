---
phase: 17
review: 17-REVIEW.md
titles: json
findings:
  - id: CR-01
    severity: critical
    disposition: open
    title: "Behind a trusted proxy, the per-IP key is still the client-controlled leftmost `X-Forwarded-For` entry (residual of the prior CR-01)"
  - id: CR-02
    severity: critical
    disposition: open
    title: "The rate-limit filter matches on the raw `requestURI`, so percent-encoding the path skips the limit entirely"
  - id: WR-01
    severity: warning
    disposition: open
    title: "Bucket maps keyed by attacker-chosen values grow without bound (memory exhaustion)"
  - id: WR-02
    severity: warning
    disposition: open
    title: "The per-email limit still lets anyone send about 72 confirmation emails per day to a victim"
  - id: WR-03
    severity: warning
    disposition: open
    title: "A double-submit or re-join kills the earlier link, and the email copy tells the user a dead link means \"you're confirmed\""
  - id: WR-04
    severity: warning
    disposition: open
    title: "The normalizer can merge distinct mailboxes, and a PENDING re-join overwrites the delivery address, so a waitlist spot can be hijacked"
  - id: WR-05
    severity: warning
    disposition: open
    title: "The invite email is sent before the invite row is flushed, and a row lock is held across external I/O"
  - id: WR-06
    severity: warning
    disposition: open
    title: "Invite delivery failures are swallowed with no cause and no log"
  - id: WR-07
    severity: warning
    disposition: open
    title: "The per-IP bucket is shared with `/api/auth/*`, and 429s are unreadable cross-origin"
  - id: WR-08
    severity: warning
    disposition: open
    title: "The admin boundary depends on each handler calling the guard and has no brute-force throttling, yet it now exposes the full waitlist PII"
  - id: WR-09
    severity: warning
    disposition: open
    title: "Trusted-proxy matching is exact-string, the `::1` default never matches, and a mismatch silently collapses all clients into one bucket"
  - id: IN-01
    severity: info
    disposition: open
    title: "The email copy hardcodes \"expires in 7 days\" while the TTL is configurable"
  - id: IN-02
    severity: info
    disposition: open
    title: "The class KDoc says 401 always comes before parameter validation, but type-conversion errors return 400 first"
  - id: IN-03
    severity: info
    disposition: open
    title: "The operator list cannot page past the first 500 rows"
  - id: IN-04
    severity: info
    disposition: open
    title: "Unused repository method"
  - id: IN-05
    severity: info
    disposition: open
    title: "Link building assumes the configured URL has no query string"
  - id: IN-06
    severity: info
    disposition: open
    title: "The JWT skip list matches the broad prefix `/api/waitlist`"
  - id: IN-07
    severity: info
    disposition: open
    title: "A misconfigured redirect URL fails only at runtime, after the token was already claimed"
open: 18
total: 18
recorded: 2026-10-02T12:28:16.127Z
---

# Phase 17: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| CR-01 | critical | open | - |
| CR-02 | critical | open | - |
| WR-01 | warning | open | - |
| WR-02 | warning | open | - |
| WR-03 | warning | open | - |
| WR-04 | warning | open | - |
| WR-05 | warning | open | - |
| WR-06 | warning | open | - |
| WR-07 | warning | open | - |
| WR-08 | warning | open | - |
| WR-09 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |
| IN-06 | info | open | - |
| IN-07 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
