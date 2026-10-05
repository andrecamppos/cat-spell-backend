---
phase: 16-invite-only-access-referral
verified: 2026-10-01T11:00:00Z
status: passed
score: 7/7 must-haves verified
behavior_unverified: 0
---

# Phase 16: Invite-Only Access & Referral Verification Report

**Phase Goal:** Gate account creation behind invite-only access using operator-issued codes, with referral attribution recorded at consumption and a global flag to open signup when going public.
**Verified:** 2026-10-01T11:00:00Z
**Status:** passed

## Goal Achievement

### Observable Truths

| # | Truth (from ROADMAP success criteria + plan must_haves) | Status | Evidence |
|---|----------------------------------------------------------|--------|----------|
| 1 | Global `app.invite.enabled` gate enforces invite-required signup and flips off to go public | ✓ VERIFIED | `AuthService.register` reads `@Value("${app.invite.enabled:false}")`; `InvitePublicModeIntegrationTest` (enabled=false → 201 for any/absent code), `InviteGateIntegrationTest` (enabled=true → required) |
| 2 | Operator can issue invite codes to bootstrap first users | ✓ VERIFIED | `POST /api/admin/invites` → `InviteService.create`; `InviteAdminEndpointIntegrationTest` asserts 201 + raw code once |
| 3 | When gate is on, signup requires a valid, unconsumed code | ✓ VERIFIED | `InviteGateIntegrationTest`: valid code → 201 + consumed; missing → 403 INVITE_REQUIRED, no users row |
| 4 | Codes are high-entropy, hashed-at-rest, single-use; invalid == consumed == missing (no enumeration) | ✓ VERIFIED | `InviteSingleUseIntegrationTest` (SHA-256 stored, concurrent one-winner), `InviteEnumerationSafetyIntegrationTest` (identical 403/title/INVITE_REQUIRED) |
| 5 | Referral attribution (referrer → invitee) recorded on consumption; bootstrap/self write none | ✓ VERIFIED | `ReferralAttributionIntegrationTest` (1 row for referrer-bearing, 0 for null-referrer, 0 for self) |
| 6 | Admin route is permitAll + guarded only by a constant-time, deny-by-default X-Admin-Token | ✓ VERIFIED | `SecurityConfig` permitAll `/api/admin/invites`; controller uses `MessageDigest.isEqual`; `InviteAdminEndpointIntegrationTestDenyByDefault` (blank token → 401) |
| 7 | A failed/raced consume rolls back the whole register tx — no orphan account | ✓ VERIFIED | `register` is `@Transactional`; `InviteEnumerationSafetyIntegrationTest` asserts userCount==0 on a rejected gated register |

**Score:** 7/7 truths verified (0 present, behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `db/migration/V23__create_invites_and_referrals.sql` | invites + referrals DDL | ✓ EXISTS + SUBSTANTIVE | code_hash UNIQUE, nullable referrer, consumed_at, chk_referrals_no_self, uq_referrals_invitee |
| `invite/model/Invite.kt`, `Referral.kt` | JPA entities | ✓ EXISTS + SUBSTANTIVE | Validated under ddl-auto=validate in InviteMigrationTest |
| `invite/model/InviteRepository.kt` | findByCodeHash + atomic markConsumed | ✓ EXISTS + SUBSTANTIVE | `@Modifying @Query ... WHERE i.consumedAt IS NULL` returning Int |
| `invite/model/ReferralRepository.kt` | JpaRepository<Referral,UUID> | ✓ EXISTS + SUBSTANTIVE | — |
| `invite/service/InviteService.kt` | create/validate/consume | ✓ EXISTS + SUBSTANTIVE | SecureRandom+SHA-256, generic exception, atomic claim, referrer-conditional referral |
| `invite/controller/InviteAdminController.kt` | POST /api/admin/invites | ✓ EXISTS + SUBSTANTIVE | constant-time deny-by-default guard |
| `invite/model/IssueInviteRequest.kt` / `IssueInviteResponse.kt` | issuance DTOs | ✓ EXISTS + SUBSTANTIVE | — |
| 7 invite integration test classes | behavioral proof | ✓ EXISTS + SUBSTANTIVE | all green in full suite |

**Artifacts:** 8/8 verified

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| AuthService.register | InviteService.validate | gated `if (inviteEnabled)` | ✓ WIRED | runs after age gate, only when enabled |
| AuthService.register | InviteService.consume | `invite?.let { ... }` after save | ✓ WIRED | inside @Transactional boundary |
| InviteAdminController | InviteService.create | after constant-time token guard | ✓ WIRED | returns IssueInviteResponse(code) 201 |
| SecurityConfig | /api/admin/invites | permitAll matcher | ✓ WIRED | header check is the sole barrier |
| InviteService / controller | GlobalExceptionHandler | InviteRequiredException→403, AdminAuthException→401 | ✓ WIRED | single INVITE_REQUIRED body |

**Wiring:** 5/5 connections verified

## Requirements Coverage

| Requirement | Status | Blocking Issue |
|-------------|--------|----------------|
| INV-01: global config gate, flippable to public | ✓ SATISFIED | - |
| INV-02: operator can issue codes | ✓ SATISFIED | - |
| INV-03: gated signup requires valid unconsumed code | ✓ SATISFIED | - |
| INV-04: high-entropy, hashed, single-use, non-enumerable | ✓ SATISFIED | - |
| INV-05: referral attribution recorded on consumption | ✓ SATISFIED | - |

**Coverage:** 5/5 requirements satisfied

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | none | — | No TODOs, stubs, placeholders, or raw-code logging in the invite domain |

**Anti-patterns:** 0 found (0 blockers, 0 warnings)

## Human Verification Required

None — all seven observable truths are exercised by automated integration tests (327 tests pass, 0 failures, 1 pre-existing skip).

## Gaps Summary

**No gaps found.** Phase goal achieved. Ready to proceed.

## Verification Metadata

**Verification approach:** Goal-backward (derived from phase goal + plan must_haves)
**Must-haves source:** 16-01..16-04 PLAN.md frontmatter + ROADMAP.md success criteria
**Automated checks:** full suite `./gradlew test` → 327 passed, 0 failed, 1 skipped
**Note:** A test-infra fix was required during execution — the new per-`@TestPropertySource` contexts exhausted the shared Testcontainers Postgres connection limit; capped HikariCP (`maximum-pool-size=4`, `minimum-idle=1`) in `src/test/resources/application.yml`.
**Human checks required:** 0
**Total verification time:** ~10 min (dominated by full Testcontainers suite)

---
*Verified: 2026-10-01T11:00:00Z*
*Verifier: orchestrator (inline — Agent subagent primitive unavailable in this runtime)*
