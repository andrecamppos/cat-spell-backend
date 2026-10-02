---
phase: 17
review: 17-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "The CIDR mask branch for prefixes that are not a multiple of 8 has no test"
  - id: WR-02
    severity: warning
    disposition: open
    title: "The chosen hop is used as the bucket key exactly as written, so `ip:port` and other non-literal hops fail open"
  - id: IN-01
    severity: info
    disposition: open
    title: "Dot-segment spellings are kept out only by Spring Security's StrictHttpFirewall, not by the filter"
  - id: IN-02
    severity: info
    disposition: open
    title: "`isIpLiteral` is public and named as a literal check, but it is only a shape pre-filter"
  - id: IN-03
    severity: info
    disposition: open
    title: "Entries in IPv4-mapped CIDR form fail startup with a misleading message, and zone-scoped peers are never trusted"
  - id: IN-04
    severity: info
    disposition: open
    title: "The configuration docs omit the new variable from the places operators check first"
  - id: IN-05
    severity: info
    disposition: open
    title: "A trusted-proxy misconfiguration is still silent at runtime (residual of WR-09)"
  - id: CR-01
    severity: critical
    disposition: fixed
    title: "Behind a trusted proxy, the per-IP key is still the client-controlled leftmost `X-Forwarded-For` entry (residual of the prior CR-01)"
  - id: CR-02
    severity: critical
    disposition: fixed
    title: "The rate-limit filter matches on the raw `requestURI`, so percent-encoding the path skips the limit entirely"
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
    disposition: fixed
    title: "Trusted-proxy matching is exact-string, the `::1` default never matches, and a mismatch silently collapses all clients into one bucket"
  - id: IN-06
    severity: info
    disposition: open
    title: "The JWT skip list matches the broad prefix `/api/waitlist`"
  - id: IN-07
    severity: info
    disposition: open
    title: "A misconfigured redirect URL fails only at runtime, after the token was already claimed"
open: 15
total: 18
recorded: 2026-10-02T20:47:17.976Z
---

# Phase 17: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-02 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |
| CR-01 | critical | fixed | 17-09 (re-review 2026-10-02: RESOLVED) |
| CR-02 | critical | fixed | 17-09 (re-review 2026-10-02: RESOLVED) |
| WR-03 | warning | open | - (not in the current review) |
| WR-04 | warning | open | - (not in the current review) |
| WR-05 | warning | open | - (not in the current review) |
| WR-06 | warning | open | - (not in the current review) |
| WR-07 | warning | open | - (not in the current review) |
| WR-08 | warning | open | - (not in the current review) |
| WR-09 | warning | fixed | 17-09 (re-review 2026-10-02: RESOLVED) |
| IN-06 | info | open | - (not in the current review) |
| IN-07 | info | open | - (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
