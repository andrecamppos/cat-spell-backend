---
phase: 17
review: 17-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "The CIDR mask branch for prefixes that are not a multiple of 8 has no test"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "The chosen hop is used as the bucket key exactly as written, so `ip:port` and other non-literal hops fail open"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "Dot-segment spellings are kept out only by Spring Security's StrictHttpFirewall, not by the filter"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "`isIpLiteral` is public and named as a literal check, but it is only a shape pre-filter"
  - id: IN-03
    severity: info
    disposition: deferred
    title: "Entries in IPv4-mapped CIDR form fail startup with a misleading message, and zone-scoped peers are never trusted"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "The configuration docs omit the new variable from the places operators check first"
  - id: IN-05
    severity: info
    disposition: fixed
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
    disposition: fixed
    title: "A double-submit or re-join kills the earlier link, and the email copy tells the user a dead link means \"you're confirmed\""
  - id: WR-04
    severity: warning
    disposition: fixed
    title: "The normalizer can merge distinct mailboxes, and a PENDING re-join overwrites the delivery address, so a waitlist spot can be hijacked"
  - id: WR-05
    severity: warning
    disposition: fixed
    title: "The invite email is sent before the invite row is flushed, and a row lock is held across external I/O"
  - id: WR-06
    severity: warning
    disposition: fixed
    title: "Invite delivery failures are swallowed with no cause and no log"
  - id: WR-07
    severity: warning
    disposition: fixed
    title: "The per-IP bucket is shared with `/api/auth/*`, and 429s are unreadable cross-origin"
  - id: WR-08
    severity: warning
    disposition: fixed
    title: "The admin boundary depends on each handler calling the guard and has no brute-force throttling, yet it now exposes the full waitlist PII"
  - id: WR-09
    severity: warning
    disposition: fixed
    title: "Trusted-proxy matching is exact-string, the `::1` default never matches, and a mismatch silently collapses all clients into one bucket"
  - id: IN-06
    severity: info
    disposition: fixed
    title: "The JWT skip list matches the broad prefix `/api/waitlist`"
  - id: IN-07
    severity: info
    disposition: fixed
    title: "A misconfigured redirect URL fails only at runtime, after the token was already claimed"
  - id: WR-10
    severity: warning
    disposition: fixed
    title: "Bucket maps keyed by attacker-chosen values grow without bound (first-review WR-01, re-added)"
  - id: WR-11
    severity: warning
    disposition: fixed
    title: "The per-email limit let anyone send about 72 confirmation emails per day to a victim (first-review WR-02, re-added)"
  - id: IN-08
    severity: info
    disposition: fixed
    title: "The confirmation copy hardcoded the 7-day expiry while the TTL is configurable (first-review IN-01)"
  - id: IN-09
    severity: info
    disposition: fixed
    title: "Admin type-conversion errors returned 400 before the 401 the KDoc promised (first-review IN-02)"
  - id: IN-10
    severity: info
    disposition: deferred
    title: "The operator list cannot page past the first 500 rows (first-review IN-03)"
  - id: IN-11
    severity: info
    disposition: fixed
    title: "Unused repository lookup by normalized email (first-review IN-04)"
  - id: IN-12
    severity: info
    disposition: fixed
    title: "Link building assumed the configured URL has no query string (first-review IN-05)"
  - id: AUD-01
    severity: info
    disposition: fixed
    title: "Admin endpoints were permitAll but not in the JWT skip list, so a stale Bearer could 401 the operator (v2.2 audit, Unchecked item)"
open: 0
total: 26
recorded: 2026-10-04T16:23:59.000Z
---

# Phase 17: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | Phase 18: 18-03 |
| WR-02 | warning | fixed | Phase 18: 18-03 |
| IN-01 | info | fixed | Phase 18: 18-07 |
| IN-02 | info | fixed | Phase 18: 18-03 |
| IN-03 | info | deferred | deferred: out of scope per 18-CONTEXT D-02 (fails safe; clearer IPv4-mapped CIDR message and zone-scoped peers left for later) |
| IN-04 | info | fixed | Phase 18: 18-11 |
| IN-05 | info | fixed | Phase 18: 18-03 |
| CR-01 | critical | fixed | 17-09 (re-review 2026-10-02: RESOLVED) |
| CR-02 | critical | fixed | 17-09 (re-review 2026-10-02: RESOLVED) |
| WR-03 | warning | fixed | Phase 18: 18-04, 18-06 |
| WR-04 | warning | fixed | Phase 18: 18-06 |
| WR-05 | warning | fixed | Phase 18: 18-08 |
| WR-06 | warning | fixed | Phase 18: 18-08 |
| WR-07 | warning | fixed | Phase 18: 18-07, 18-09, 18-11 |
| WR-08 | warning | fixed | Phase 18: 18-05, 18-07, 18-10 |
| WR-09 | warning | fixed | 17-09 (re-review 2026-10-02: RESOLVED) |
| IN-06 | info | fixed | Phase 18: 18-10 |
| IN-07 | info | fixed | Phase 18: 18-04 |
| WR-10 | warning | fixed | Phase 18: 18-02, 18-06, 18-07 |
| WR-11 | warning | fixed | Phase 18: 18-06 |
| IN-08 | info | fixed | Phase 18: 18-04 |
| IN-09 | info | fixed | Phase 18: 18-10 |
| IN-10 | info | deferred | deferred: out of scope per 18-CONTEXT D-02 (operator list pagination past 500 rows) |
| IN-11 | info | fixed | Phase 18: 18-06 |
| IN-12 | info | fixed | Phase 18: 18-04 |
| AUD-01 | info | fixed | Phase 18: 18-10 |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
