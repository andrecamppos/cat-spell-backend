# Roadmap: Cat Spell Backend

## Milestones

- ✅ **v1.0 MVP Backend** — Phases 1-6 (shipped 2026-06-16)
- ✅ **v1.1 Mixed Discovery** — Phase 7 (shipped 2026-06-23)
- ✅ **v2.0 Push Notifications** — Phases 8-9 (shipped 2026-07-30)
- ✅ **v2.1 Account Recovery & Email Verification** — Phases 10-12 (shipped 2026-08-24)
- 🔭 **v2.2 Safety, Moderation & Gated Access** — Phases 13-17 (in progress)

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

<details>
<summary>✅ v2.1 Account Recovery & Email Verification (Phases 10-12) — SHIPPED 2026-08-24</summary>

- [x] Phase 10: Password Recovery (4/4 plans) — completed 2026-08-08
- [x] Phase 11: Email Verification (5/5 plans) — completed 2026-08-12
- [x] Phase 12: Account Credentials (5/5 plans) — completed 2026-08-19

_Full phase details: `.planning/milestones/v2.1-ROADMAP.md`_

</details>

<details open>
<summary>🔭 v2.2 Safety, Moderation & Gated Access (Phases 13-17) — PLANNED</summary>

- [ ] **Phase 13: Blocking & Unmatch** — block/unblock/list + unmatch, enforced across all surfaces
- [ ] **Phase 14: Report a User** — report with category + details, persisted and operator-notified
- [ ] **Phase 15: Age Verification** — server-side 18+ hard gate at signup
- [ ] **Phase 16: Invite-Only Access & Referral** — gated signup via operator codes + referral attribution
- [ ] **Phase 17: Waitlist / Landing-Page API** — public waitlist with double opt-in → operator invites

### Phase 13: Blocking & Unmatch

**Goal**: Users can protect themselves by blocking (bidirectional, enforced everywhere) and unmatching, establishing the block relationship and predicate that report and all read paths reuse.
**Depends on**: Phase 4 (Discovery & Matching), Phase 5 (Chat)
**Requirements**: MOD-01, MOD-02, MOD-03, MOD-04, MOD-05
**Success Criteria** (what must be TRUE):

  1. Blocking a user hides both users from each other's discovery feed (both cat and human branches) and profile/owner-detail endpoints
  2. A blocked pair cannot open or send in chat; existing conversation history is retained but locked (no new messages either way)
  3. A user can view their block list and unblock; unblocking re-enables rediscovery
  4. Unmatch ends the match/conversation but does NOT ban rediscovery (the other user can reappear)
  5. Self-block is rejected; block enforcement is verified on feed, profile-detail, chat, and match lookup

Introduces a `blocks` table (PK on the pair + reverse index) and a bidirectional block predicate consulted synchronously on every read/send path. Reuses soft-state teardown (retain messages, lock conversation). New Flyway migration (V19+).

**Plans:** 4 plans across 3 waves

Plans:
**Wave 1**

- [x] 13-01-PLAN.md — Data foundation: V19 `blocks` table + V20 `matches` soft-state columns, Block entity/repository, feed block filter (both UNION branches), teardown primitives

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 13-02-PLAN.md — Service layer: SelfBlockException (400), MatchService endMatch/unmatch/reactivation, BlockService (block/unblock/list + bidirectional predicate)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 13-03-PLAN.md — Enforcement fan-out: profile-detail + swipe 404 guards, chat send/open guards + conversation hiding, BlockEnforcementIntegrationTest
- [x] 13-04-PLAN.md — HTTP surface: BlockController (block/unblock/list) + MatchController unmatch endpoint, BlockEndpointIntegrationTest

### Phase 14: Report a User

**Goal**: Users can report others with a structured reason; reports are persisted and the operator is notified out-of-band, with an optional block in the same action.
**Depends on**: Phase 13 (BlockService for alsoBlock), reuses the `EmailSender` seam
**Requirements**: MOD-06, MOD-07, MOD-08
**Success Criteria** (what must be TRUE):

  1. A user can report another user with a fixed category enum plus a required details field
  2. A report is persisted and the operator is notified out-of-band (async / AFTER_COMMIT); the report survives an email-send failure
  3. The `alsoBlock` flag blocks the reported user via the Phase 13 BlockService
  4. Self-report is rejected and the reporter's identity is never exposed to the reported user

Introduces a `reports` table and reuses the v2.0 AFTER_COMMIT async dispatch pattern so email never blocks or rolls back persistence. New Flyway migration (V19+).

### Phase 15: Age Verification

**Goal**: Enforce a server-side 18+ hard gate at signup via self-attested DOB, behind an `AgeVerifier` seam that keeps a future vendor check swappable.
**Depends on**: Phase 1 (Auth — registration flow)
**Requirements**: AGE-01, AGE-02, AGE-03
**Success Criteria** (what must be TRUE):

  1. A self-attested date of birth is collected at signup
  2. Registration is hard-blocked server-side for anyone under 18 (not client-only)
  3. Existing accounts are handled via migration with no lockout on rollout
  4. Age checking sits behind an `AgeVerifier` seam so a vendor implementation can drop in without call-site changes

Resolves the DOB-placement decision (DOB currently lives on `user_profiles`, not the register payload). New Flyway migration (V19+) for DOB collection/backfill.

### Phase 16: Invite-Only Access & Referral

**Goal**: Gate account creation behind invite-only access using operator-issued codes, with referral attribution recorded at consumption and a global flag to open signup when going public.
**Depends on**: Phase 1 (Auth — registration), Phase 15 (register-time gate composition)
**Requirements**: INV-01, INV-02, INV-03, INV-04, INV-05
**Success Criteria** (what must be TRUE):

  1. A global config gate (`app.invite.enabled`) enforces invite-required signup and can be flipped off to go fully public
  2. The operator can issue invite codes to bootstrap the first users
  3. When the gate is on, signup requires a valid, unconsumed invite code
  4. Codes are high-entropy, stored hashed, and single-use; invalid vs consumed codes return an identical generic error
  5. Referral attribution (referrer → invitee) is recorded when an invite is consumed

Introduces `invites` + `referrals` tables, SecureRandom code generation hashed at rest, and single-use consumption. New Flyway migration (V19+).

### Phase 17: Waitlist / Landing-Page API

**Goal**: Capture demand via a public, enumeration-safe, double-opt-in waitlist that the operator can convert into invites — the backing API for the separate-repo landing page.
**Depends on**: Phase 16 (converts confirmed entries into invites), reuses `EmailSender` + Bucket4j `RateLimitFilter`
**Requirements**: WAIT-01, WAIT-02, WAIT-03, WAIT-04
**Success Criteria** (what must be TRUE):

  1. A public unauthenticated endpoint accepts a join and returns an identical enumeration-safe response for new vs duplicate emails
  2. Double opt-in via a hashed single-use, time-limited confirmation token; only confirmed entries count
  3. The endpoint is protected by per-IP + per-email rate limiting with email normalization
  4. The operator can convert a confirmed entry into an invite, which emails the invite code/link

Introduces a `waitlist_entries` table, reuses the hashed single-use token model and the three-place public-endpoint whitelist pattern. New Flyway migration (V19+).

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
| 10. Password Recovery | v2.1 | 4/4 | ✅ Complete | 2026-08-08 |
| 11. Email Verification | v2.1 | 5/5 | ✅ Complete | 2026-08-12 |
| 12. Account Credentials | v2.1 | 5/5 | ✅ Complete | 2026-08-19 |
| 13. Blocking & Unmatch | v2.2 | 4/4 | In Progress|  |
| 14. Report a User | v2.2 | — | Not started | — |
| 15. Age Verification | v2.2 | — | Not started | — |
| 16. Invite-Only Access & Referral | v2.2 | — | Not started | — |
| 17. Waitlist / Landing-Page API | v2.2 | — | Not started | — |

---
*Roadmap created: 2025-06-09*
*Last updated: 2026-09-24 — v2.2 Safety, Moderation & Gated Access roadmap created (Phases 13-17)*
