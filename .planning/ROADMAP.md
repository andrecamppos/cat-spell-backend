# Roadmap: Cat Spell Backend

## Milestones

- ✅ **v1.0 MVP Backend** — Phases 1-6 (shipped 2026-06-16)
- ✅ **v1.1 Mixed Discovery** — Phase 7 (shipped 2026-06-23)
- 🔜 **v2.0 Push Notifications** — Phase 8 (planned)

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
<summary>🔜 v2.0 Push Notifications (Phase 8) — PLANNED</summary>

- [ ] Phase 8: Push Notifications (FCM) — planned
  - FCM HTTP v1 provider behind a `send(token, payload)` abstraction (APNs-ready)
  - Device token registration + lifecycle (upsert by user_id/device_id, prune on UNREGISTERED)
  - Push on new match and new chat message
  - "Offline + inactive" send decision (STOMP presence + active-conversation tracking)
  - Collapse keys for chat unread; all-on preferences (no toggle in v1)
  - Contract tests with mocked FCM + `validate_only` dry-run smoke test

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
| 8. Push Notifications (FCM) | v2.0 | 0/? | 🔜 Planned | — |

---
*Roadmap created: 2025-06-09*
*Last updated: 2026-07-17 — added v2.0 Push Notifications (Phase 8) from /gsd-explore*
