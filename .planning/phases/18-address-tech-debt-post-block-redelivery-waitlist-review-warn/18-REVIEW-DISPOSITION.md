---
phase: 18
review: 18-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Undelivered pre-block or pre-unmatch messages still resurface if the rematch comes before the recipient's next reconnect"
  - id: IN-01
    severity: info
    disposition: open
    title: "Per-IP limits (including the D-13 admin throttle) key on the full IPv6 address"
  - id: IN-02
    severity: info
    disposition: open
    title: "The \"never a wildcard\" CORS claim isn't enforced, and the 429 grant echoes `*`"
  - id: IN-03
    severity: info
    disposition: open
    title: "The confirmation email copy doesn't mention the daily cap"
  - id: IN-04
    severity: info
    disposition: open
    title: "An interrupt-ignoring provider can permanently exhaust the 2-thread invite send pool"
  - id: IN-05
    severity: info
    disposition: open
    title: "The first inserted address wins, so a variant can squat a slot"
  - id: IN-06
    severity: info
    disposition: open
    title: "The confirm and invite URLs are still parsed only when an email is rendered"
open: 7
total: 7
recorded: 2026-10-04T16:50:02.043Z
---

# Phase 18: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |
| IN-06 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
