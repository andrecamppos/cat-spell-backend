---
phase: 16
slug: invite-only-access-referral
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-10-01
---

# Phase 16 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| application → PostgreSQL (DDL) | The V23 schema defines the durable integrity guarantees (uniqueness, single-use target column, FK integrity) the service relies on. | Invite code hashes, referral attribution rows |
| JPA entity ↔ Flyway schema | Production runs `ddl-auto=validate`; an entity/schema mismatch fails startup. | Schema metadata |
| caller → InviteService | Untrusted invite codes and referrer ids enter `validate`/`create`; the service hashes, looks up, and claims. | Raw invite code, referrer UUID |
| InviteService → PostgreSQL (claim) | The atomic conditional UPDATE claim is where single-use is truly enforced, under a DB row lock. | code_hash, consumed_at |
| InviteService → GlobalExceptionHandler | All failure modes converge on one exception → one 403 body (no enumeration channel). | Generic error body (`INVITE_REQUIRED`) |
| operator/client → POST /api/admin/invites | A `permitAll` route: the `X-Admin-Token` header check is the ONLY access-control barrier (no JWT/role). | Admin shared-secret token, referrer UUID |
| client → POST /api/auth/register | Untrusted `inviteCode` crosses here; the server-side gate (`app.invite.enabled`) decides whether it is required and validated. | Invite code, registration payload |
| AuthService.register → InviteService + DB | `validate`/`consume` + user save must be atomic (one transaction) to avoid orphan accounts or double-consumed codes. | User row, invite claim, referral row |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-16-01 | Tampering | invites.code_hash | high | mitigate | V23 stores SHA-256 hex only (`code_hash VARCHAR(64) NOT NULL UNIQUE`); raw code has no column — a table leak yields no usable codes (D-06). | closed |
| T-16-02 | Tampering/Elevation | invites.consumed_at | high | mitigate | `consumed_at` is the single-use claim target; `InviteRepository.markConsumed` conditional UPDATE `WHERE consumedAt IS NULL` (atomic, closes double-consume race, D-07). | closed |
| T-16-03 | Tampering | referrals attribution | medium | mitigate | `chk_referrals_no_self CHECK (referrer_id <> invitee_id)` + FKs to users/invites + `uq_referrals_invitee UNIQUE(invitee_id)` in V23 (D-12). | closed |
| T-16-04 | Information Disclosure | invites.code_hash lookup | low | accept | `code_hash UNIQUE` index gives a constant lookup path; enumeration-safe generic error enforced in service/handler tier (see T-16-06/T-16-17). | closed |
| T-16-05 | Spoofing | InviteService.create code generation | high | mitigate | `SecureRandom` 32 bytes → URL-safe Base64 (~256-bit, non-sequential); no custom/guessable alphabet (INV-04). | closed |
| T-16-06 | Information Disclosure | InviteService.validate failure modes | high | mitigate | Null/blank/not-found/consumed all throw the SAME `InviteRequiredException` → one 403 body + `code=INVITE_REQUIRED` (D-10, INV-04). | closed |
| T-16-07 | Tampering/Elevation | InviteService.consume single-use | high | mitigate | Atomic `markConsumed(...) WHERE consumedAt IS NULL`; 0 rows → generic 403; no read-check-write race (Pitfall 2). | closed |
| T-16-08 | Tampering | referral attribution | high | mitigate | `create` validates referrer existence → 400 (D-12); `consume` writes a referral only for a non-null, non-self referrer (D-11); DB `chk_referrals_no_self` + FKs backstop. | closed |
| T-16-09 | Information Disclosure | raw code exposure | high | mitigate | Raw code stored only as SHA-256 hash; returned once from `create`; never logged (D-06). | closed |
| T-16-10 | Repudiation | timing on validate (hash-miss vs consumed) | low | accept | Residual micro-timing difference between a hash miss and a consumed-row hit; identical response body removes the primary enumeration channel — accepted at ASVS L1. | closed |
| T-16-11 | Elevation of Privilege | permitAll admin route unconfigured | high | mitigate | Deny-by-default: `requireValidAdminToken` rejects when `app.invite.admin-token` is blank; no guessable default ships (Pitfall 4, D-03). | closed |
| T-16-12 | Information Disclosure | X-Admin-Token comparison | high | mitigate | Constant-time `MessageDigest.isEqual` compare; never `==`/`String.equals` (timing side channel). | closed |
| T-16-13 | Spoofing | bad/missing admin token response | medium | mitigate | Generic 401 (`AdminAuthException`, title "Unauthorized", no `code` hint) — caller cannot distinguish missing vs wrong token (D-03). | closed |
| T-16-14 | Tampering | referrerUserId at issuance | medium | mitigate | `InviteService.create` rejects an unknown non-null referrer with 400; prevents dangling attribution (D-12). | closed |
| T-16-15 | Denial of Service | unbounded issuance by a leaked token | low | accept | Single-use + operator-controlled volume bounds abuse; rate-limiting/revocation deferred (D-05) — accepted at ASVS L1 for an operator-only surface. | closed |
| T-16-16 | Elevation of Privilege | gate flag authority | high | mitigate | Gate read server-side via `@Value("\${app.invite.enabled}")` in `AuthService`; no client flag is trusted (INV-01, D-09). | closed |
| T-16-17 | Information Disclosure | gated register failure modes | high | mitigate | Missing/invalid/consumed codes all raise `InviteRequiredException` → one identical 403 body + `INVITE_REQUIRED` (INV-04, D-10). | closed |
| T-16-18 | Tampering (integrity) | orphan account on raced consume | high | mitigate | `@Transactional register` wraps validate→save→consume; a thrown 403 from consume rolls back the user insert (Pitfall 3). | closed |
| T-16-19 | Tampering | reused/consumed code at register | high | mitigate | consume's atomic `markConsumed` claim rejects a second use with the generic 403; the tx rolls back (INV-04, D-07). | closed |
| T-16-20 | Denial of Service | fail-open gate on misconfiguration | medium | accept | Default is `false` (public) by design (D-09); operator sets `INVITE_ENABLED=true` to gate — an explicit config choice. Accepted at ASVS L1. | closed |
| T-16-SC | Tampering | dependency installs (supply chain) | low | accept | No new Gradle dependencies this phase (RESEARCH Package Legitimacy Audit: none — all JDK/Spring Boot primitives). No install task, no supply-chain surface added. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-16-01 | T-16-04 | Constant-time hash lookup path is acceptable; enumeration safety is enforced at the service/handler tier via one generic error. | phase threat model (plan 16-01) | 2026-10-01 |
| AR-16-02 | T-16-10 | Residual micro-timing between a hash miss and a consumed-row hit; identical response body removes the primary enumeration channel — within ASVS L1 scope. | phase threat model (plan 16-02) | 2026-10-01 |
| AR-16-03 | T-16-15 | Single-use codes + operator-controlled issuance volume bound abuse; rate-limiting/revocation deferred (D-05) for an operator-only surface at ASVS L1. | phase threat model (plan 16-03) | 2026-10-01 |
| AR-16-04 | T-16-20 | Invite gate defaults to `false` (public) by design (D-09); gating is an explicit operator config choice (`INVITE_ENABLED=true`). Accepted at ASVS L1. | phase threat model (plan 16-04) | 2026-10-01 |
| AR-16-05 | T-16-SC | No new dependencies introduced this phase (all JDK/Spring Boot primitives); no supply-chain install surface. | phase threat model (all plans) | 2026-10-01 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-01 | 21 | 21 | 0 | gsd-secure-phase (L1, from PLAN threat models) |

### Security Audit 2026-10-01

| Metric | Count |
|--------|-------|
| Threats found | 21 |
| Closed | 21 |
| Open | 0 |

Register built from `<threat_model>` blocks in plans 16-01…16-04 (`register_authored_at_plan_time: true`). ASVS L1 → L1 grep/read-depth verification sufficient; auditor subagent not required per the secure-phase short-circuit rule. All 16 `mitigate` threats verified present in the implementation; 5 `accept` threats recorded in the Accepted Risks Log.

**Evidence (mitigate threats):**
- T-16-01/02/03 — `src/main/resources/db/migration/V23__create_invites_and_referrals.sql` (hash-only `code_hash`, `consumed_at`, `chk_referrals_no_self`, FKs, `uq_referrals_invitee`).
- T-16-02/07/19 — `InviteRepository.markConsumed` conditional UPDATE `WHERE i.consumedAt IS NULL`.
- T-16-05/06/08/09 — `InviteService` (`SecureRandom` 32B→URL-safe Base64, SHA-256 hash-only, identical `InviteRequiredException` for all validate failures, referrer/self guards in `consume`).
- T-16-11/12/13/14 — `InviteAdminController.requireValidAdminToken` (deny-by-default blank token, constant-time `MessageDigest.isEqual`, generic `AdminAuthException`).
- T-16-16/17/18 — `AuthService.register` (`@Transactional`, server-side `@Value("\${app.invite.enabled}")` gate, generic exception).
- Error contract — `GlobalExceptionHandler.handleInviteRequired` (403, `code=INVITE_REQUIRED`, no branching) and `handleAdminAuth` (401, no code hint); `SecurityConfig` `permitAll` `/api/admin/invites`.
- Config — `app.invite.enabled` defaults `false` and `app.invite.admin-token` defaults empty (deny-by-default) in both `application.yml` files.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-01
