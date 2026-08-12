# Roadmap: Cat Spell Backend

## Milestones

- ✅ **v1.0 MVP Backend** — Phases 1-6 (shipped 2026-06-16)
- ✅ **v1.1 Mixed Discovery** — Phase 7 (shipped 2026-06-23)
- ✅ **v2.0 Push Notifications** — Phases 8-9 (shipped 2026-07-30)
- 🔭 **v2.1 Account Recovery & Email Verification** — Phases 10-12 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP Backend (Phases 1-6) — SHIPPED 2026-06-16</summary>

- [x] Phase 1: Foundation & Auth (3/3 plans) — completed 2025-06-09
- [x] Phase 2: User Profiles & Photos (2/2 plans) — completed 2026-06-11
- [x] Phase 3: Cat Profiles (2/2 plans) — completed 2026-06-12
- [x] Phase 4: Discovery & Matching (2/2 plans) — completed 2026-06-15
- [x] Phase 5: Real-Time Chat (2/2 plans) — completed 2026-06-15
- [x] Phase 6: API Polish & Integration Tests (2/2 plans) — completed 2026-06-16

</details>

<details>
<summary>✅ v1.1 Mixed Discovery (Phase 7) — SHIPPED 2026-06-23</summary>

- [x] Phase 7: Mixed Discovery Feed (2/2 plans) — completed 2026-06-23

</details>

<details>
<summary>✅ v2.0 Push Notifications (Phases 8-9) — SHIPPED 2026-07-30</summary>

- [x] Phase 8: Push Delivery Foundation (3/3 plans) — completed 2026-07-17
- [x] Phase 9: Notification Triggers & Smart Delivery (3/3 plans) — completed 2026-07-29

_Full phase details: `.planning/milestones/v2.0-ROADMAP.md`_

</details>

<details open>
<summary>🔭 v2.1 Account Recovery & Email Verification (Phases 10-12) — PLANNED</summary>

- [x] **Phase 10: Password Recovery** — email-based forgot/reset-password flow (completed 2026-08-08)
- [ ] **Phase 11: Email Verification** — prove ownership of the signup email
- [ ] **Phase 12: Account Credentials** — self-service credential changes while logged in

### Phase 10: Password Recovery

**Goal**: A user who forgot their password can regain access via an emailed single-use reset link, backed by a new reusable transactional-email abstraction.
**Depends on**: Phase 1 (Auth — `AuthService`, refresh tokens)
**Requirements**: EMAIL-01, EMAIL-02, RECOV-01, RECOV-02, RECOV-03, RECOV-04, RECOV-05, RECOV-06, RECOV-07
**Success Criteria** (what must be TRUE):

  1. A user can request a reset with their email and always receives an identical generic response (no account enumeration)
  2. A registered user receives an email containing a single-use, time-limited reset link
  3. Submitting a valid token + new password updates the password; reused or expired tokens are rejected
  4. A successful reset revokes all of the user's refresh tokens
  5. The forgot-password endpoint is rate-limited (per-IP and per-email) using existing Bucket4j infrastructure
  6. Email is sent through a provider-abstracted `EmailSender` seam, stubbed/logged in tests (no real network sends)

**Plans**: 4 plans
**Wave 1**

- [x] 10-01-PLAN.md — EmailSender seam + no-op logging provider + backend-rendered reset email + config (EMAIL-01, EMAIL-02) [Wave 1]
- [x] 10-02-PLAN.md — PasswordResetToken entity + repository + V15 Flyway migration (RECOV-05) [Wave 1]

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 10-03-PLAN.md — PasswordResetService + AuthService.resetPassword + three-whitelist security wiring (RECOV-02/04/05/06/07) [Wave 2]

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 10-04-PLAN.md — forgot/reset endpoints + DTOs + integration tests (RECOV-01/03/04/05/06/07) [Wave 3]

Introduces transactional email infrastructure (new dependency), a hashed single-use reset token with short TTL, enumeration-safe responses, and rate limiting on the forgot-password endpoint. Design captured in `.planning/notes/password-recovery-design.md`; email capability seed in `.planning/seeds/transactional-email-infra.md`.

### Phase 11: Email Verification

**Goal**: Prove ownership of the signup email — hard-gate login until verified, with a resend flow and a migration that grandfathers existing accounts.
**Depends on**: Phase 10 (reuses the email infrastructure)
**Requirements**: VERIFY-01, VERIFY-02, VERIFY-03, VERIFY-04, VERIFY-05
**Plans**: 5 plans

Verification email on registration, hard-gate login until verified, resend flow (rate-limited), and a migration that grandfathers existing accounts as verified so no current user is locked out.

**Wave 1**

- [x] 11-01-PLAN.md — EmailVerificationToken entity + repository + V16 token table + V17 email_verified_at column & grandfather backfill (VERIFY-02, VERIFY-05) [Wave 1]
- [x] 11-02-PLAN.md — EmailVerificationEmailRenderer + app.verify-email-url config, reusing the Phase 10 EmailSender seam (VERIFY-01) [Wave 1]

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 11-03-PLAN.md — EmailVerificationService (issue/resend) + AuthService.verifyEmail + login hard-gate + EmailNotVerifiedException(403 EMAIL_NOT_VERIFIED) + three-place security whitelist (VERIFY-01/02/03/04) [Wave 2]

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 11-04-PLAN.md — verify-email/resend-verification endpoints + DTOs + register no-token 201 contract + EmailVerificationIntegrationTest (VERIFY-01/02/03/04) [Wave 3]

**Wave 4** *(blocked on Wave 3 completion)*

- [ ] 11-05-PLAN.md — test-suite migration to the no-token register + login-gate contract + resend-verification per-IP rate-limit test + GrandfatherMigrationTest (VERIFY-03/04/05) [Wave 4]

### Phase 12: Account Credentials

**Goal**: Self-service credential changes while logged in — change password and change email (with verification of the new address).
**Depends on**: Phase 11 (reuses email verification)
**Requirements**: ACCT-01, ACCT-02, ACCT-03, ACCT-04, ACCT-05
**Plans**: TBD

Change password (current password + revoke other sessions) and change email (current password + verify the new address before it takes effect, reject if already in use).

</details>

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation & Auth | v1.0 | 3/3 | ✅ Complete | 2025-06-09 |
| 2. User Profiles & Photos | v1.0 | 2/2 | ✅ Complete | 2026-06-11 |
| 3. Cat Profiles | v1.0 | 2/2 | ✅ Complete | 2026-06-12 |
| 4. Discovery & Matching | v1.0 | 2/2 | ✅ Complete | 2026-06-15 |
| 5. Real-Time Chat | v1.0 | 2/2 | ✅ Complete | 2026-06-15 |
| 6. API Polish & Integration Tests | v1.0 | 2/2 | ✅ Complete | 2026-06-16 |
| 7. Mixed Discovery Feed | v1.1 | 2/2 | ✅ Complete | 2026-06-23 |
| 8. Push Delivery Foundation | v2.0 | 3/3 | ✅ Complete | 2026-07-17 |
| 9. Notification Triggers & Smart Delivery | v2.0 | 3/3 | ✅ Complete | 2026-07-29 |
| 10. Password Recovery | v2.1 | 4/4 | Complete    | 2026-08-08 |
| 11. Email Verification | v2.1 | 3/5 | In Progress|  |
| 12. Account Credentials | v2.1 | 0/? | 🔭 Planned | — |

---
*Roadmap created: 2025-06-09*
*Last updated: 2026-08-07 — Added planned Phases 11 (Email Verification) and 12 (Account Credentials) to complete v2.1 roadmap*
