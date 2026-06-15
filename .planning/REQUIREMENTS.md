# Requirements: Cat Spell Backend

**Defined:** 2025-06-09
**Core Value:** Cat-first discovery — users fall for the cat first, then meet the person.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Authentication

- [x] **AUTH-01**: User can register with email and password — Phase 1 ✓
- [x] **AUTH-02**: User can log in and receive JWT access token — Phase 1 ✓
- [x] **AUTH-03**: User can refresh expired access token using refresh token — Phase 1 ✓

### User Profiles

- [ ] **PROF-01**: User can create profile with display name, bio, and preferences
- [ ] **PROF-02**: User can edit their own profile
- [ ] **PROF-03**: User can upload profile photos to S3-compatible storage
- [ ] **PROF-04**: User can delete their own profile photos
- [ ] **PROF-05**: User can set and update GPS location coordinates

### Cat Profiles

- [x] **CAT-01**: User can create a cat profile with name, age, and breed
- [x] **CAT-02**: User can upload photos for their cat profile
- [x] **CAT-03**: User can edit their cat's profile
- [x] **CAT-04**: User can delete a cat profile
- [x] **CAT-05**: User can have multiple cat profiles linked to their account

### Discovery & Matching

- [x] **DISC-01**: User can browse a discovery feed showing cat profiles (cat-first reveal)
- [x] **DISC-02**: User can view a cat's owner profile by tapping into the cat detail view
- [x] **DISC-03**: User can like or pass on a cat profile in the feed
- [x] **DISC-04**: Discovery feed filters by configurable distance radius using GPS geolocation
- [x] **DISC-05**: Feed excludes previously seen (liked or passed) profiles
- [x] **DISC-06**: Mutual match is detected when both users like each other's cats
- [x] **DISC-07**: User can view their list of matches

### Chat

- [ ] **CHAT-01**: Matched users can send and receive text messages in real time via WebSocket
- [ ] **CHAT-02**: User can view message history for a conversation
- [ ] **CHAT-03**: User can view list of conversations (one per match)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Authentication

- **AUTH-04**: User can reset password via email link
- **AUTH-05**: User can sign in with OAuth (Google, Apple)

### Cat Profiles

- **CAT-06**: Cat profile includes personality traits (playful, shy, cuddly, etc.)
- **CAT-07**: User can designate a primary/featured cat for the swipe feed
- **CAT-08**: Multi-cat household management with featured cat selection

### Matching

- **MATCH-01**: Cat compatibility scoring based on temperament, energy level, indoor/outdoor
- **MATCH-02**: Lifestyle signal scoring from cat ownership patterns (count, breeds, care style)
- **MATCH-03**: Combined match score weighting cat compatibility and human preferences

### Chat

- **CHAT-04**: Typing indicators shown to the other user in real time
- **CHAT-05**: Read receipts shown when messages are seen

### Safety & Moderation

- **SAFE-01**: User can block another user
- **SAFE-02**: User can report another user
- **SAFE-03**: User can unmatch from an existing match
- **SAFE-04**: Admin moderation panel for reviewing reports

### Notifications

- **NOTF-01**: Push notifications for new matches
- **NOTF-02**: Push notifications for new messages

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Mobile app | Separate project/repo |
| Video profiles | High storage/bandwidth cost, moderation burden |
| Chat media sharing | Adds complexity, text-only for v1 |
| Cat playdate scheduling | Scope creep beyond dating domain |
| AI-generated cat descriptions | Removes personal touch, feels generic |
| Payment/subscription features | Premature before community established |
| Real-time chat with media | Text-only v1, media sharing deferred |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | ✅ Validated |
| AUTH-02 | Phase 1 | ✅ Validated |
| AUTH-03 | Phase 1 | ✅ Validated |
| PROF-01 | Phase 2 | Pending |
| PROF-02 | Phase 2 | Pending |
| PROF-03 | Phase 2 | Pending |
| PROF-04 | Phase 2 | Pending |
| PROF-05 | Phase 2 | Pending |
| CAT-01 | Phase 3 | ✅ Validated |
| CAT-02 | Phase 3 | ✅ Validated |
| CAT-03 | Phase 3 | ✅ Validated |
| CAT-04 | Phase 3 | ✅ Validated |
| CAT-05 | Phase 3 | ✅ Validated |
| DISC-01 | Phase 4 | Complete |
| DISC-02 | Phase 4 | Complete |
| DISC-03 | Phase 4 | Complete |
| DISC-04 | Phase 4 | Complete |
| DISC-05 | Phase 4 | Complete |
| DISC-06 | Phase 4 | Complete |
| DISC-07 | Phase 4 | Complete |
| CHAT-01 | Phase 5 | Pending |
| CHAT-02 | Phase 5 | Pending |
| CHAT-03 | Phase 5 | Pending |

**Coverage:**

- v1 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0 ✓

---
*Requirements defined: 2025-06-09*
*Last updated: 2026-06-15 after Phase 3 completion sync*
