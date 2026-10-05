# Phase 14: Report a User - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-25
**Phase:** 14-report-a-user
**Areas discussed:** Operator notification routing, Duplicate & abuse handling, Evidence captured in report, Who can be reported + validation

---

## Operator notification routing

| Option | Description | Selected |
|--------|-------------|----------|
| New `app.report.operator-email` key | Dedicated config key holds a single operator address; email sent via existing EmailSender + configured from-address; dedicated report renderer builds the body | ✓ |
| Comma-separated recipient list | Dedicated key supporting multiple operator addresses, all notified per report | |
| Reuse existing email from-address | Send to whatever email.* is already configured with; no new config key | |

**User's choice:** New `app.report.operator-email` key
**Notes:** Single operator address for now; email dispatched out-of-band (AFTER_COMMIT + async) so mail failures never block/roll back the report.

---

## Duplicate & abuse handling

| Option | Description | Selected |
|--------|-------------|----------|
| Persist all + rate-limit reporter | Every submission persisted (full evidence trail); per-reporter Bucket4j cap prevents flooding; over cap → 429 | ✓ |
| Dedupe one open per pair | Only one active report per (reporter, reported) pair; repeat is idempotent no-op | |
| Persist all, no limit | Every report persisted and emailed with no throttle | |

**User's choice:** Persist all + rate-limit reporter
**Notes:** Repeat reports carry signal for the operator; only flooding is throttled via existing Bucket4j approach.

---

## Evidence captured in report

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal | reporterId, reportedId, category, details, createdAt only; operator uses Phase 13 retained locked chat history | ✓ |
| Optional conversationId ref | Client may pass a conversationId stored on the report | |
| Snapshot recent messages | Copy last N messages into the report at submission time | |

**User's choice:** Minimal
**Notes:** Report row stays lean; evidence context comes from the already-retained locked chat history from Phase 13.

---

## Who can be reported + validation

| Option | Description | Selected |
|--------|-------------|----------|
| Anyone by userId; 1-1000 chars | Report any existing user by id (mirrors block D-10); unknown target → 404; details non-blank, max 1000 chars | ✓ |
| Anyone by userId; 1-2000 chars | Same reachability, larger 2000-char details cap | |
| Only matched/contacted users | Restrict reporting to matched/messaged users | |

**User's choice:** Anyone by userId; 1-1000 chars
**Notes:** Enables preemptive reporting from a discovery/profile card, mirroring block reachability.

---

## Claude's Discretion

- Exact `reports` table schema/columns/indexes, category storage form, and entity/repository shape under `moderation/model/`.
- Endpoint URL/verb, request/response DTOs, and success status code (follow existing controller/RFC 7807 conventions).
- Rate-limit capacity/refill defaults + config-key names (follow `app.forgot-password.*` `@Value` pattern).
- Domain-event data class + async listener naming/placement (mirror `push/event/`).
- Report email subject/body copy and renderer placement under `email/`.

## Deferred Ideas

- Report triage/status workflow (reviewed/actioned/dismissed) — v2.x (MOD2-01).
- Automated abuse scoring / auto-suspend — v2.x, explicit anti-feature (MOD2-02).
- Admin moderation panel — v2.x (MOD2-03).
