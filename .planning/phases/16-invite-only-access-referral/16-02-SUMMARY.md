---
phase: 16-invite-only-access-referral
plan: 02
subsystem: api
tags: [invite, referral, securerandom, sha-256, single-use, rfc7807, enumeration-safety]

requires:
  - phase: 16-invite-only-access-referral
    provides: Invite/Referral entities, InviteRepository.markConsumed atomic claim, config keys (16-01)
provides:
  - InviteService.create(referrerUserId) / validate(code) / consume(invite, invitee)
  - InviteRequiredException → 403 INVITE_REQUIRED (single generic body) and AdminAuthException → generic 401 mappings
  - IssueInviteRequest / IssueInviteResponse DTOs
affects: [invite-admin-endpoint, auth-register]

tech-stack:
  added: []
  patterns:
    - "SecureRandom 32-byte + Base64 url-no-padding code generation and SHA-256 hash-at-rest (transcribed from EmailVerificationService)"
    - "Single generic exception for all invite failure modes (enumeration safety, D-10)"
    - "Atomic conditional-UPDATE single-use claim wrapped in @Transactional (roll back account insert on lost race)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/invite/service/InviteService.kt
    - src/main/kotlin/com/catspell/api/invite/model/IssueInviteRequest.kt
    - src/main/kotlin/com/catspell/api/invite/model/IssueInviteResponse.kt
    - src/test/kotlin/com/catspell/api/invite/InviteSingleUseIntegrationTest.kt
    - src/test/kotlin/com/catspell/api/invite/ReferralAttributionIntegrationTest.kt
  modified:
    - src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt
    - src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt

key-decisions:
  - "Unknown non-null referrer rejected with IllegalArgumentException → existing 400 handler (D-12)"
  - "validate() throws the SAME InviteRequiredException for null/blank/not-found/consumed — no enumeration channel (D-10)"
  - "consume() writes a referral ONLY when referrerUserId is non-null and != invitee id (D-11/D-12)"

patterns-established:
  - "Invite domain service mirrors moderation/auth service scaffolding (@Service, @Transactional, constructor-injected repos)"

requirements-completed: [INV-02, INV-04, INV-05]

coverage:
  - id: D1
    description: "Invite codes are high-entropy, hashed-at-rest (code_hash == SHA-256(raw)), and the raw code is never persisted"
    requirement: "INV-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteSingleUseIntegrationTest.kt#invite code is stored only as its SHA-256 hash"
        status: pass
    human_judgment: false
  - id: D2
    description: "All validate() failure modes (null/blank/unknown/consumed) throw the single generic InviteRequiredException → 403 INVITE_REQUIRED"
    requirement: "INV-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteSingleUseIntegrationTest.kt#null blank unknown and consumed codes all throw the same generic exception"
        status: pass
    human_judgment: false
  - id: D3
    description: "Single-use is atomic: concurrent consumption of one code yields exactly one winner, one consumed invite"
    requirement: "INV-04"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/InviteSingleUseIntegrationTest.kt#concurrent consumption of one code yields exactly one winner"
        status: pass
    human_judgment: false
  - id: D4
    description: "Referral attribution written only for a real distinct referrer; bootstrap and self-referral write none"
    requirement: "INV-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/invite/ReferralAttributionIntegrationTest.kt (3 tests)"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-01
status: complete
---

# Phase 16 Plan 02: Invite Domain Service & Error Contract Summary

**InviteService (SecureRandom+SHA-256 hashed single-use codes, atomic conditional-UPDATE claim, referrer-conditional referral writes) plus the single generic INVITE_REQUIRED 403 and generic admin 401 RFC 7807 mappings.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 3
- **Files modified:** 7 (5 created, 2 modified)

## Accomplishments
- `InviteService.create` generates a ~256-bit URL-safe code, stores only its SHA-256 hash, returns the raw code once, and rejects an unknown non-null referrer with 400
- `InviteService.validate` throws one generic `InviteRequiredException` for null/blank/not-found/consumed (enumeration-safe), returns the Invite only when valid+unconsumed
- `InviteService.consume` performs the atomic `markConsumed` claim (0-row → generic 403, tx rolls back) and writes a referral row only for a real, non-self referrer
- `InviteRequiredException` → 403 `INVITE_REQUIRED` (single body) and `AdminAuthException` → generic 401 (no code hint) mapped in `GlobalExceptionHandler`
- Integration tests prove hashed-at-rest storage, indistinguishable failures, exactly-one concurrent winner, and referrer-conditional referral writes

## Task Commits

1. **Task 1: Invite exceptions + RFC 7807 mappings + DTOs** - `c8e1845` (feat)
2. **Task 2: InviteService create/validate/consume** - `1ce3d27` (feat)
3. **Task 3: Single-use + referral-attribution integration tests** - `da719e5` (test)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/invite/service/InviteService.kt` - the invite domain service
- `src/main/kotlin/com/catspell/api/invite/model/IssueInviteRequest.kt` / `IssueInviteResponse.kt` - issuance DTOs
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` - InviteRequiredException, AdminAuthException
- `src/main/kotlin/com/catspell/api/common/exception/GlobalExceptionHandler.kt` - handleInviteRequired (403), handleAdminAuth (401)
- `src/test/kotlin/com/catspell/api/invite/InviteSingleUseIntegrationTest.kt` - INV-04 single-use + enumeration safety
- `src/test/kotlin/com/catspell/api/invite/ReferralAttributionIntegrationTest.kt` - INV-05 referral attribution

## Decisions Made
- Unknown non-null referrer → `IllegalArgumentException` reusing the existing 400 handler (D-12).
- Generation/hash helpers transcribed verbatim from `EmailVerificationService` (no novel crypto).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. (Test logs show harmless `HHH000478` drop-constraint warnings at JVM shutdown from the default create-drop profile — pre-existing noise, not a failure.)

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `InviteService.create` is ready for the admin issuance endpoint (16-03).
- `InviteService.validate`/`consume` and the 403/401 mappings are ready for the register-flow composition (16-04).

---
*Phase: 16-invite-only-access-referral*
*Completed: 2026-10-01*
