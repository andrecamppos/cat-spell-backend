---
phase: 15
slug: age-verification
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-09-29
---

# Phase 15 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| caller → `AgeVerifier.requireAdult` | Client-supplied `RegisterRequest.dateOfBirth` crosses into the single age-math rule at register time. | DOB (`LocalDate`) — PII |
| application code → HTTP response (ProblemDetail) | The under-18 rejection body is emitted toward the untrusted client; it must carry no PII and reveal no account existence. | 422 ProblemDetail (generic detail + machine `code`) |
| V22 migration → existing `users`/`user_profiles` data | One-time backfill relocates DOB to `users`; must not lock out or mutate accounts beyond the move. | DOB column relocation |
| discovery SQL → `users`/`user_profiles` JOIN | Age filter now reads DOB across a JOIN; the rewrite must preserve exact filter semantics. | DOB read for feed filtering |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-15-01 | Tampering | age computation in `LocalAgeVerifier.requireAdult` | high | mitigate | `Period.between`-based years (leap-year safe), single rule (D-07); boundary unit tests (exactly-18 pass, 17y364d throw). Verified in `LocalAgeVerifier.kt` + `LocalAgeVerifierTest.kt`. | closed |
| T-15-02 | Information Disclosure | 422 ProblemDetail body | medium | mitigate | `handleUnderMinimumAge` emits only a generic detail + `code=UNDER_MINIMUM_AGE`; no DOB/email/account detail. Verified in `GlobalExceptionHandler.kt`. | closed |
| T-15-03c | Tampering | wrong minimum via config (`app.age.minimum-age`) | low | accept | Defaults to 18; a deployment misconfiguring it is an operational concern, not a code defect. Below `block_on` (high) — non-blocking. | closed |
| T-15-03g | Elevation of Privilege | under-18 bypasses the gate (client-only enforcement) | high | mitigate | Server-side `ageVerifier.requireAdult(request.dateOfBirth)` is the first statement in `AuthService.register`, before duplicate-email check and before `userRepository.save` (D-01). `AgeGateIntegrationTest` asserts no `users` row on rejection. | closed |
| T-15-04 | Spoofing | applicant self-attests a false DOB | medium | accept | Self-attested DOB is the MVP contract; the `AgeVerifier` seam is the documented drop-in point for a vendor check (deferred AGE2-01). Residual risk recorded, not mitigated this phase. Below `block_on` (high) — non-blocking. | closed |
| T-15-05 | Tampering | user edits DOB after signup to alter age semantics | high | mitigate | DOB removed from `CreateProfileRequest`/`UpdateProfileRequest`/`UserProfile` and the profile write path; `ProfileService.validateAge` deleted — immutable after signup (D-06). Verified: no `dateOfBirth` in `profile/`. | closed |
| T-15-06 | Denial of Service / Tampering | V22 backfill loses DOBs or locks out accounts | high | mitigate | Column added NULLABLE (grandfather), idempotent `UPDATE ... WHERE date_of_birth IS NULL` backfill (mirrors V17), drop only after backfill; `DobMigrationTest` asserts grandfather usability + idempotence. Verified in `V22__move_date_of_birth_to_users.sql`. | closed |
| T-15-07 | Information Disclosure | under-18 error reveals account existence | medium | mitigate | Age check runs before the duplicate-email check (D-02); the 422 body carries only a generic message + `code`, no account detail. Verified: `requireAdult` precedes `existsByEmail` in `AuthService.register`. | closed |
| T-15-08 | Tampering | migration history integrity | high | mitigate | Append-only V22; V1–V21 never edited (Flyway checksum integrity). Verified via `git log --name-only`: only `V22__*.sql` added, no prior migration touched. | closed |
| T-15-SC | Tampering | npm/pip/cargo installs (supply chain) | n/a | accept | No package installs in this phase (no `build.gradle.kts` change); supply-chain checkpoint not applicable. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-15-01 | T-15-03c | Single global `app.age.minimum-age` (default 18); a deployment setting it wrong is an operational concern, not a code defect. Low severity, below block threshold. | Phase 15 plan (D-03) | 2026-09-29 |
| R-15-02 | T-15-04 | Self-attested DOB is the MVP contract; vendor age verification deferred to AGE2-01. The `AgeVerifier` interface is the documented seam for a future drop-in check. | Phase 15 plan (D-03) | 2026-09-29 |
| R-15-03 | T-15-SC | No dependency changes in this phase (no `build.gradle.kts` edit); supply-chain surface unchanged. | Phase 15 plan | 2026-09-29 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-29 | 10 | 10 | 0 | gsd-secure-phase (L1 grep-depth, register authored at plan time) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-29
