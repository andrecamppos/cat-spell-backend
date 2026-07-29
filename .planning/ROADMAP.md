# Roadmap: Cat Spell Backend

## Milestones

- ✅ **v1.0 MVP Backend** — Phases 1-6 (shipped 2026-06-16)
- ✅ **v1.1 Mixed Discovery** — Phase 7 (shipped 2026-06-23)
- 🔜 **v2.0 Push Notifications** — Phases 8-9 (planned)

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

<details open>
<summary>🔜 v2.0 Push Notifications (Phases 8-9) — PLANNED</summary>

- [x] **Phase 8: Push Delivery Foundation** — planned (completed 2026-07-17)
  - Goal: A device token can be registered, stored, and used to deliver a validated FCM push; dead tokens are pruned.
  - Requirements: PUSH-01, PUSH-02, PUSH-03, PUSH-09, PUSH-11, PUSH-12
  - Success criteria:
    1. Authenticated client can register/unregister a device token (upsert by user+device, multi-device)
    2. `PushProvider` abstraction delivers via FCM HTTP v1; APNs addable later without call-site changes
    3. Tokens reported `UNREGISTERED` are deactivated
    4. Firebase health indicator reports status; `validate_only` dry-run + mocked-provider tests pass
- [ ] **Phase 9: Notification Triggers & Smart Delivery** — planned (3 plans, 3 waves)
  - Goal: Matches and messages trigger pushes through the "offline + inactive" decision, asynchronously.
  - Requirements: PUSH-04, PUSH-05, PUSH-06, PUSH-07, PUSH-08, PUSH-10
  - Success criteria:
    1. Mutual match notifies both users; new message notifies the recipient with deep-link payload
    2. Push suppressed when recipient is actively viewing that conversation (STOMP presence + active-conversation)
    3. Message pushes collapse per conversation
    4. Sends run async off domain events, never blocking message persistence
  - Plans:
    - [x] 09-01-PLAN.md — Presence & active-conversation registry (Wave 1, PUSH-08)
    - [x] 09-02-PLAN.md — Send decision, payloads & collapse key (Wave 2, PUSH-04/05/06/07)
    - [ ] 09-03-PLAN.md — Domain events & async AFTER_COMMIT dispatch (Wave 3, PUSH-10)

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
| 8. Push Delivery Foundation | v2.0 | 3/3 | Complete    | 2026-07-17 |
| 9. Notification Triggers & Smart Delivery | v2.0 | 2/3 | In Progress|  |

---
*Roadmap created: 2025-06-09*
*Last updated: 2026-07-17 — v2.0 Push Notifications roadmap (Phases 8-9) via /gsd-new-milestone*
