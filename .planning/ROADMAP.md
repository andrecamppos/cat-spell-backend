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

- [ ] Phase 10: Password Recovery — email-based forgot/reset-password flow

  Introduces transactional email infrastructure (new dependency), a hashed
  single-use reset token with short TTL, enumeration-safe responses, and rate
  limiting on the forgot-password endpoint. Design captured in
  `.planning/notes/password-recovery-design.md`; email capability seed in
  `.planning/seeds/transactional-email-infra.md`.
  Requirements: EMAIL-01, EMAIL-02, RECOV-01..07.

- [ ] Phase 11: Email Verification — prove ownership of the signup email

  Verification email on registration, hard-gate login until verified, resend
  flow (rate-limited), and a migration that grandfathers existing accounts as
  verified so no current user is locked out. Reuses the email infrastructure
  from Phase 10. Requirements: VERIFY-01..05.

- [ ] Phase 12: Account Credentials — self-service credential changes while logged in

  Change password (current password + revoke other sessions) and change email
  (current password + verify the new address before it takes effect, reject if
  already in use). Reuses email verification from Phase 11.
  Requirements: ACCT-01..05.

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
| 10. Password Recovery | v2.1 | 0/? | 🔭 Planned | — |
| 11. Email Verification | v2.1 | 0/? | 🔭 Planned | — |
| 12. Account Credentials | v2.1 | 0/? | 🔭 Planned | — |

---
*Roadmap created: 2025-06-09*
*Last updated: 2026-08-07 — Added planned Phases 11 (Email Verification) and 12 (Account Credentials) to complete v2.1 roadmap*
