# Roadmap: Cat Spell Backend

## Milestones

- ✅ **v1.0 MVP Backend** — Phases 1-6 (shipped 2026-06-16)
- 🔲 **v1.1 Mixed Discovery** — Phase 7

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

### Phase 7: Mixed Discovery Feed

**Goal:** Transform discovery from cat-only to a mixed feed — users with cats show cat cards (cat-first preserved), users without cats show human cards. Cat ownership becomes optional; the app is open to all cat lovers, not just cat owners.
**Milestone:** v1.1
**Depends on:** Phase 4, Phase 6
**Requirements:** DISC-08, DISC-09, DISC-10, DISC-11, DISC-12
**Success Criteria:**

1. Users without cats appear in the discovery feed as human cards (user profile info + photos)
2. Users with cats still appear as cat cards (cat-first reveal preserved)
3. Feed items include a `type` discriminator (`CAT` or `HUMAN`) so clients render the right card
4. Swipe endpoint accepts either `catId` (cat card) or `targetUserId` (human card), not both
5. Mutual match detection works for both cat-based and direct user-based swipes
6. Previously seen profiles (via either path) are excluded from future feeds
7. Owner profile endpoint still works for cat cards; human cards link directly to user profile

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation & Auth | v1.0 | 3/3 | ✅ Complete | 2025-06-09 |
| 2. User Profiles & Photos | v1.0 | 2/2 | ✅ Complete | 2026-06-11 |
| 3. Cat Profiles | v1.0 | 2/2 | ✅ Complete | 2026-06-12 |
| 4. Discovery & Matching | v1.0 | 2/2 | ✅ Complete | 2026-06-15 |
| 5. Real-Time Chat | v1.0 | 2/2 | ✅ Complete | 2026-06-15 |
| 6. API Polish & Integration Tests | v1.0 | 2/2 | ✅ Complete | 2026-06-16 |
| 7. Mixed Discovery Feed | v1.1 | 2/2 | Complete    | 2026-06-23 |

---
*Roadmap created: 2025-06-09*
*Last updated: 2026-06-22 after Phase 7 planning*
