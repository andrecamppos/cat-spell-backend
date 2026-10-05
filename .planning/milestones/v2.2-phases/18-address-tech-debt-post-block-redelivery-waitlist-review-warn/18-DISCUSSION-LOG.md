# Phase 18: Address tech debt: post-block redelivery + waitlist review warnings - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-03
**Phase:** 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
**Areas discussed:** Scope cut, W1 message fate, Re-join & hijack, Admin & limiter

---

## Scope cut

| Option | Description | Selected |
|--------|-------------|----------|
| All open warnings | WR-03..08, current WR-01/WR-02, the two dropped warnings re-added | ✓ |
| Original WR-03..08 only | Only the six named in the audit list | |
| User-facing four only | WR-03, WR-04, WR-06, WR-08 | |

| Option (info items + stale Bearer) | Description | Selected |
|--------|-------------|----------|
| Cheap ones + Bearer | Stale-Bearer fix, exact JWT skip list, startup-parsed redirect URLs, docs table, TTL in copy, rename isIpLiteral; defer pagination, IPv4-mapped CIDR, zone peers | ✓ |
| All info items | Every IN item from both reviews | |
| Bearer only, defer IN | Only the stale-Bearer item | |

| Option (eviction scope) | Description | Selected |
|--------|-------------|----------|
| Yes, all of them | Shared helper for the 4 services + RateLimitFilter | ✓ |
| Waitlist + RateLimitFilter only | Strictly the audit finding | |

| Option (current IN-01 + IN-05) | Description | Selected |
|--------|-------------|----------|
| Include both | servletPath key; one-shot XFF WARN | ✓ |
| Defer both | | |

| Option (W2/W3/deploy check) | Description | Selected |
|--------|-------------|----------|
| Keep them out | Deferred | ✓ |
| Pull W2 in | Add a set-DOB path | |

**User's choice:** Every recommended option.

---

## W1 message fate

| Option | Description | Selected |
|--------|-------------|----------|
| Skip + mark delivered | Never previewed; consumed so it can't resurface after a rematch | ✓ |
| Skip, leave undelivered | Previews arrive after a rematch | |

| Option (teardown) | Description | Selected |
|--------|-------------|----------|
| Filter on reconnect only | Single change in deliverUnreadMessages, set-based | ✓ |
| Both: teardown + filter | endMatch also bulk-marks delivered | |

| Option (race guard) | Description | Selected |
|--------|-------------|----------|
| No, out of scope | The send path already guards | ✓ |
| Yes, re-check at push | | |

---

## Re-join & hijack

| Option (throttle) | Description | Selected |
|--------|-------------|----------|
| Cooldown + daily cap | 15-min silent no-op cooldown + 3/24h bucket | ✓ |
| Cooldown only | | |
| Daily cap only | | |

| Option (hijack) | Description | Selected |
|--------|-------------|----------|
| Pin first address | A re-join never changes the stored email; keep D-03 normalization | ✓ |
| Stop stripping +suffix | Reverses Phase 17 D-03 | |

| Option (email copy) | Description | Selected |
|--------|-------------|----------|
| "Use the newest email" | Drop the false sentence; render the TTL; keep one error URL | ✓ |
| Add reason to error URL | Changes the landing-page contract | |

| Option (convert I/O) | Description | Selected |
|--------|-------------|----------|
| Flush, send, roll back | saveAndFlush, synchronous send with timeout, 502 on failure, log the cause | ✓ |
| Commit, then send after commit | Needs a resend path | |

---

## Admin & limiter

| Option (guard) | Description | Selected |
|--------|-------------|----------|
| Central filter on /api/admin/** | Reuses AdminTokenGuard; adds /api/admin to the JWT skip list | ✓ |
| Spring Security ROLE_OPERATOR | New role concept | |

| Option (token) | Description | Selected |
|--------|-------------|----------|
| Throttle + 32-char minimum | Per-IP admin bucket; fail startup on a short non-blank token | ✓ |
| Throttle only | WARN on a short token | |

| Option (join bucket) | Description | Selected |
|--------|-------------|----------|
| Separate bucket + CORS 429 | Own bucket; CORS headers on 429; document server-to-server mode | ✓ |
| Separate bucket only | | |

| Option (eviction) | Description | Selected |
|--------|-------------|----------|
| Add Caffeine | New dependency, Boot-managed version | ✓ |
| Hand-rolled, no new dep | Size cap + scheduled sweep | |

---

## Claude's Discretion

- Config key names and defaults (cooldown, caps, admin/join capacities, Caffeine size, send timeout)
- Filter vs interceptor for the central admin guard, and filter ordering
- How the send timeout is applied
- Whether to include the first-review IN-04 (unused repo method) and IN-05 (UriComponentsBuilder) one-liners
- Plan/wave grouping and disposition bookkeeping format (new IDs WR-10/WR-11 suggested)

## Deferred Ideas

- W2 set-DOB path; W3 invite-to-email binding; production proxy check at deploy; invite email checked in a real client
- First-review IN-03 pagination; current-review IN-03 IPv4-mapped CIDR + zone-scoped peers
- Distributed bucket4j ProxyManager for multi-instance deployments
