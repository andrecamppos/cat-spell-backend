---
phase: 14
slug: report-a-user
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-09-28
---

# Phase 14 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| client (JWT + JSON body) → ReportController | Untrusted request body + bearer token cross here; reporter id is derived from the token, never the body. | reportedUserId, category, free-text details, alsoBlock, bearer JWT |
| ReportController → ReportService | Authenticated reporterId (principal) + validated request cross into the invariant-owning service. | reporterId (UUID), validated ReportRequest |
| application code → PostgreSQL (reports) | DB-level CHECK constraints backstop the service-layer invariants (self-report, category domain). | report rows (reporter/reported ids, category, details) |
| ReportService → BlockService (nested tx) | alsoBlock composition; must remain atomic with the report write. | reporterId, reportedId |
| ReportService → ApplicationEventPublisher | Event carries reporter identity toward the operator path only (never toward the reported user). | ReportCreatedEvent (ids + strings) |
| ApplicationEvent (AFTER_COMMIT) → ReportNotificationListener → EmailSender → provider | Committed report data crosses onto an async thread; operator email leaves the app to a possibly-slow/failing provider. | operator email (ids + strings, operator address) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-14-01 | Tampering | `details` free-text field | medium | mitigate | `@field:NotBlank` + `@field:Size(max=1000)` on `ReportRequest` → RFC 7807 400; DB `details VARCHAR(1000)` backstop (V21). | closed |
| T-14-02 | Tampering | `category` field | medium | mitigate | Enum-typed `ReportCategory` → unknown/null fails Jackson → 400; DB `chk_reports_category` CHECK backstop (V21). | closed |
| T-14-03 | Elevation/Abuse | self-report (reporter==reported) | high | mitigate | `SelfReportException` → 400 (ReportService first guard) + `chk_reports_no_self` CHECK defence-in-depth (V21). | closed |
| T-14-04 | Information Disclosure | reporter identity toward reported user (retaliation, D-11) | high | mitigate | `ReportResponse` exposes only `reportId`; no field carries reporter identity toward the target; event is operator-bound only. | closed |
| T-14-05 | Repudiation / evidence loss | duplicate-report dedupe | medium | mitigate | No `UNIQUE(reporter_id, reported_id)` in V21 — every report persists (D-04), preserving the full evidence trail. | closed |
| T-14-06 | Tampering | migration history | high | mitigate | Append-only V21; V1–V20 unmodified (verified: last 8 commits touched only V21). Flyway checksum integrity preserved. | closed |
| T-14-10 | Elevation of Privilege | self-report (reporterId==reportedId) | high | mitigate | `SelfReportException` → 400 as the first guard, before any write (ReportService.report line 52). | closed |
| T-14-11 | Denial of Service | per-reporter report flooding | high | mitigate | Per-reporter Bucket4j token bucket keyed by reporter userId → 429 over cap (real 429, not silent). | closed |
| T-14-12 | Spoofing / target-forgery | reporting a non-existent userId | medium | mitigate | Explicit `userRepository.existsById` → `ResourceNotFoundException` 404 (not a late FK-violation 500). | closed |
| T-14-13 | Information Disclosure | reporter identity to reported user (retaliation, D-11) | high | mitigate | No event/side effect toward the reported user; reporter id only enters `ReportCreatedEvent` (operator-bound). | closed |
| T-14-14 | Denial of Service / data loss | mail send coupled to report tx | high | mitigate | No inline `EmailSender.send` in the tx; only `publishEvent` (delivered AFTER_COMMIT by the listener). | closed |
| T-14-15 | Tampering / integrity | partial commit of report vs block | medium | mitigate | `BlockService.block` joins the outer `@Transactional` (propagation REQUIRED) → atomic commit. | closed |
| T-14-16 | Abuse / scope-creep | auto-suspend/auto-action on report count | medium | accept | Explicitly out of scope (MOD2-02 anti-feature); service performs no thresholded action — documented prohibition. | closed |
| T-14-20 | Denial of Service / data loss | mail send coupled to persistence | high | mitigate | `@Async` `@TransactionalEventListener(AFTER_COMMIT)`; report committed before send; send failure swallowed + logged (never lost/delayed/rolled back). | closed |
| T-14-21 | Information Disclosure | operator email leaks to reported user | high | mitigate | Recipient is `app.report.operator-email` only; reported user is never addressed; reporter identity confined to the operator email (D-11). | closed |
| T-14-22 | Information Disclosure | lazy JPA entity across async boundary | medium | mitigate | Event carries only IDs + precomputed strings; renderer builds body from event fields, no entity deref. | closed |
| T-14-23 | Repudiation | silent loss of notification | low | accept | Failures logged (`log.warn`) for operator triage; delivery is best-effort by design (D-02). | closed |
| T-14-24 | Tampering | operator-email misconfiguration | low | mitigate | `application.yml` resolves `${REPORT_OPERATOR_EMAIL:ops@catspell.example}` — safe default, internal/operator address, not user-supplied. | closed |
| T-14-30 | Spoofing | reporter identity forgery via body | high | mitigate | Reporter id from `SecurityContextHolder` principal (JWT), never from request body (ReportController.extractUserId). | closed |
| T-14-31 | Elevation / unauth access | unauthenticated report | high | mitigate | `/api/reports` under `anyRequest().authenticated()` (SecurityConfig) → 401/403 without a valid JWT. | closed |
| T-14-32 | Tampering | oversized/blank details, bad category | medium | mitigate | `@Valid @RequestBody` → 400 (Bean Validation + enum deserialization) before the service. | closed |
| T-14-33 | Elevation/Abuse | self-report via endpoint | high | mitigate | Service `SelfReportException` surfaces as 400 (title "Bad Request") via GlobalExceptionHandler. | closed |
| T-14-34 | Denial of Service | report flooding via endpoint | high | mitigate | Per-reporter 429 surfaces through the service; asserted at the HTTP layer. | closed |
| T-14-35 | Information Disclosure | response reveals reporter to target | high | mitigate | 201 body is only `{ reportId }`; no reported-user-facing artifact (D-11). | closed |
| T-14-01-SC | Tampering | npm/pip/cargo installs | low | accept | No package installs in this phase (no build.gradle.kts change); supply-chain checkpoint not applicable. | closed |
| T-14-02-SC | Tampering | npm/pip/cargo installs | low | accept | No package installs; Bucket4j already on classpath. | closed |
| T-14-03-SC | Tampering | npm/pip/cargo installs | low | accept | No package installs; MockK/EmailSender seam already present. | closed |
| T-14-04-SC | Tampering | npm/pip/cargo installs | low | accept | No package installs in this phase. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-14-01 | T-14-16 | Auto-suspend/auto-action on report count is an explicit anti-feature (MOD2-02); moderation stays human-in-the-loop. | phase-14 plan | 2026-09-28 |
| AR-14-02 | T-14-23 | Operator notification delivery is best-effort by design (D-02); failures are logged for triage rather than retried/persisted. | phase-14 plan | 2026-09-28 |
| AR-14-03 | T-14-01-SC / T-14-02-SC / T-14-03-SC / T-14-04-SC | No package installs occurred in Phase 14; supply-chain checkpoint not applicable. | phase-14 plan | 2026-09-28 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-28 | 27 | 27 | 0 | gsd-secure-phase (State B, L1 grep-depth, short-circuit) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-28
