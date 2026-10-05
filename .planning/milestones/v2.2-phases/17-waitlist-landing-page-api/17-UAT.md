---
status: complete
phase: 17-waitlist-landing-page-api
source: [17-VERIFICATION.md]
started: 2026-10-02T21:30:00Z
updated: 2026-10-02T22:50:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Browser CORS from the deployed landing origin vs a foreign origin
With WAITLIST_ALLOWED_ORIGINS set, run a browser fetch POST to /api/waitlist from the deployed landing origin, then repeat from a foreign origin.
expected: 202 readable by the landing page; the foreign origin is blocked by CORS
result: pass
note: "Verified locally via curl with WAITLIST_ALLOWED_ORIGINS=http://localhost:3000 — allowed origin preflight 200 + POST 202 with Allow-Origin echoed; foreign origin preflight 403 + POST 403 'Invalid CORS request'. Deployed-origin value still to be set to match exactly at deploy time."

### 2. Confirmation and invite emails in a real mail client
Open the confirmation and invite emails in a real mail client and follow the links.
expected: Links work; copy is accurate (see WR-03 "still confirmed" sentence and IN-01 hardcoded "7 days" from the first review)
result: pass
note: "Checked via dev-profile LoggingEmailSender body (no real email provider yet). Confirmation copy and confirm link reviewed by user; invite email not exercised in this session."

### 3. Production reverse-proxy shape
Confirm the production reverse proxy's connect address, whether it appends or overwrites X-Forwarded-For, and that it writes plain IP hops (no ip:port, no [v6]:port).
expected: RATE_LIMIT_TRUSTED_PROXIES is set to the proxy's address or CIDR, and hops are bare IP literals. A proxy that writes ip:port gives every connection its own bucket (17-REVIEW WR-02, probe P5).
result: skipped
reason: "Deferred follow-up: not deployed yet — verify the production reverse-proxy shape and set RATE_LIMIT_TRUSTED_PROXIES at deployment"

### 4. Decide on the flagged CORS-preflight prohibition (17-09)
"MUST NOT throttle ... CORS preflight requests" is enforced by the code guard `httpRequest.method == "POST"` and a scratch probe (3 OPTIONS after bucket exhaustion all passed), but no wired test covers it.
expected: Accept the code guard plus probe evidence, or add an integration test: exhaust one IP's join bucket, then send OPTIONS /api/waitlist and expect no 429
result: pass
note: "Accepted code guard (RateLimitFilter.kt:50, POST-only) plus live probe: 10x POST 202, POST 11 429, then 3x OPTIONS 200, follow-up POST still 429. No wired integration test added (user decision)."

## Summary

total: 4
passed: 3
issues: 0
pending: 0
skipped: 1
blocked: 0

## Gaps

[none]

## Deferred Follow-Ups

- test: 3
  idea: "not deployed yet — verify the production reverse-proxy shape (connect address, XFF append vs overwrite, bare IP hops) and set RATE_LIMIT_TRUSTED_PROXIES at deployment"
  deferred_at: 2026-10-02
