---
phase: 18
review: 18-REVIEW.md
titles: json
findings:
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "Reconnect racing a rematch commit can still push stale pre-hide previews (the two suppression points read in the wrong order)"
  - id: IN-07
    severity: info
    disposition: open
    title: "Bare `@Modifying` bulk UPDATE leaves already-managed `Message` entities stale in the persistence context"
  - id: IN-08
    severity: info
    disposition: open
    title: "Integration helper asserts the sweep result after the symptom assertions, which weakens failure diagnostics"
  - id: WR-01
    severity: warning
    disposition: fixed
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
open: 8
total: 10
recorded: 2026-10-05T11:00:18.731Z
---

# Phase 18: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-02 | warning | fixed | 18-REVIEW-FIX.md |
| IN-07 | info | open | - |
| IN-08 | info | open | - |
| WR-01 | warning | fixed | 18-12 (G-18-1): markAllDeliveredForMatch sweep on createMatch reactivation; confirmed resolved by the 2026-10-05 re-review (not in the current review) |
| IN-01 | info | open | - (not in the current review) |
| IN-02 | info | open | - (not in the current review) |
| IN-03 | info | open | - (not in the current review) |
| IN-04 | info | open | - (not in the current review) |
| IN-05 | info | open | - (not in the current review) |
| IN-06 | info | open | - (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
