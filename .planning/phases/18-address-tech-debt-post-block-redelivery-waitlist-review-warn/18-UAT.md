---
status: testing
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
source: [18-VERIFICATION.md]
started: 2026-10-04T17:30:00Z
updated: 2026-10-04T17:30:00Z
---

## Current Test

number: 1
name: Decide the D-04 rematch residual (18-REVIEW WR-01)
expected: |
  Sequence under test: A sends B messages while they are matched. These are live-pushed, so `delivered` stays false. A then blocks or unmatches B. They unblock and re-like each other, which reactivates the same match row. Only after that does B reconnect.
  Choose one:
  (a) Accept as within scope. The push goes to a conversation that is active again, and B can already see its full history, so no hidden content leaks and the roadmap goal's literal wording holds. Record an override and correct the ChatService.deliverUnreadMessages KDoc, which claims suppressed messages "can't resurface".
  (b) Treat it as a gap. Amend D-05 to allow one set-based UPDATE on the MatchService.createMatch reactivation branch that marks the match's undelivered messages delivered. Add a test with no reconnect in between, and fix the KDoc.
awaiting: user response

## Tests

### 1. Decide the D-04 rematch residual (18-REVIEW WR-01)
expected: Either (a) accept as within scope (no hidden content leaks, the goal's literal wording holds), record an override and correct the deliverUnreadMessages KDoc; or (b) treat it as a gap: amend D-05, add a set-based mark-delivered UPDATE on the MatchService.createMatch reactivation branch, add a no-reconnect-between test, and fix the KDoc.
result: [pending]

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
