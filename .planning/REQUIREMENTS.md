# Requirements: Cat Spell Backend

**Defined:** 2026-09-24
**Milestone:** v2.2 Safety, Moderation & Gated Access
**Core Value:** Cat-preferred discovery — cat cards for cat owners, human cards for cat lovers without cats.

## v1 Requirements

Requirements for milestone v2.2. Each maps to a roadmap phase.

### Moderation & Safety

- [x] **MOD-01**: A user can block another user
- [x] **MOD-02**: A block is enforced bidirectionally on every surface — discovery feed, profile/owner detail, chat send, and match lookup (neither party sees or can contact the other)
- [ ] **MOD-03**: Blocking a matched user retains existing conversation history but locks it (no new messages either way; no rediscovery)
- [ ] **MOD-04**: A user can view their block list and unblock a user; unblocking re-enables rediscovery
- [x] **MOD-05**: A user can unmatch another user, ending the match/conversation without banning rediscovery (the other user can reappear in the feed)
- [ ] **MOD-06**: A user can report another user with a fixed category (harassment, spam, fake profile, inappropriate content, other) plus a required free-text details field
- [ ] **MOD-07**: A report is persisted and the operator is notified out-of-band (async / AFTER_COMMIT) — a slow or failing email never blocks or rolls back the report
- [ ] **MOD-08**: The reporter can optionally block the reported user in the same action ("also block" flag)

### Age Verification

- [ ] **AGE-01**: A self-attested date of birth is collected at signup
- [ ] **AGE-02**: Signup is hard-blocked server-side for anyone under 18 (not client-only); an `AgeVerifier` seam keeps a future vendor check swappable
- [ ] **AGE-03**: Existing accounts are handled via migration (DOB backfill / grandfather) so no current user is broken on rollout

### Invite-Only Access & Referral

- [ ] **INV-01**: A global config gate enforces invite-required signup and can be flipped off to go fully public
- [ ] **INV-02**: The operator can issue invite codes to bootstrap the first users
- [ ] **INV-03**: When the gate is on, signup requires a valid, unconsumed invite code
- [ ] **INV-04**: Invite codes are high-entropy, stored hashed, and single-use; invalid vs consumed codes return an identical generic error (no enumeration)
- [ ] **INV-05**: Referral attribution (referrer → invitee) is recorded when an invite is consumed

### Waitlist / Landing-Page API

- [ ] **WAIT-01**: A public unauthenticated endpoint accepts a waitlist join (email + optional info) and returns an identical enumeration-safe response for new vs duplicate emails
- [ ] **WAIT-02**: Waitlist join uses double opt-in — a hashed single-use, time-limited confirmation token emailed to the address; only confirmed entries count
- [ ] **WAIT-03**: The public endpoint is protected by per-IP + per-email rate limiting with email normalization (Bucket4j reuse), with optional disposable-domain filtering
- [ ] **WAIT-04**: The operator can convert a confirmed waitlist entry into an invite, which emails the invite code/link

## v2 Requirements

Deferred to a future release. Tracked but not in the current roadmap.

### Moderation

- **MOD2-01**: Report triage/status workflow (reviewed / actioned / dismissed) with an audit trail
- **MOD2-02**: Automated content/abuse scoring or threshold-based auto-actioning
- **MOD2-03**: Admin moderation panel (already scoped project-wide as post-block/report)

### Access & Growth

- **INV2-01**: Member-generated invites with per-member quotas
- **INV2-02**: Referral rewards / incentives built on the attribution recorded in v2.2
- **WAIT2-01**: Waitlist position / referral leaderboard surfacing

### Age Assurance

- **AGE2-01**: Third-party age/ID verification vendor for regulated markets (EU DSA, UK OSA, AU minimum-age law)

## Out of Scope

Explicitly excluded from v2.2. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Third-party age/ID verification vendor | Cost + PII + moderation burden before launch; self-attested DOB + `AgeVerifier` seam is sufficient for the MVP gate |
| Auto-suspend after N reports | Weaponizable brigading + false positives with no appeal path; operator actions reports manually for now |
| Member-generated invite quotas | This milestone is operator-issued codes only; attribution is captured so quotas/rewards can be added later |
| Admin moderation panel | Already out of scope project-wide until block/report exists (this milestone builds that foundation) |
| Landing-page frontend | Separate repo; this repo builds only the backing waitlist API |
| New rate-limiting infrastructure | Reuse existing Bucket4j `RateLimitFilter` rather than build new infra |
| Hard-delete on block/unmatch | Destroys report evidence + breaks immutable message-history invariant; use soft state |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| MOD-01 | Phase 13 | Complete |
| MOD-02 | Phase 13 | Complete |
| MOD-03 | Phase 13 | Pending |
| MOD-04 | Phase 13 | Pending |
| MOD-05 | Phase 13 | Complete |
| MOD-06 | Phase 14 | Pending |
| MOD-07 | Phase 14 | Pending |
| MOD-08 | Phase 14 | Pending |
| AGE-01 | Phase 15 | Pending |
| AGE-02 | Phase 15 | Pending |
| AGE-03 | Phase 15 | Pending |
| INV-01 | Phase 16 | Pending |
| INV-02 | Phase 16 | Pending |
| INV-03 | Phase 16 | Pending |
| INV-04 | Phase 16 | Pending |
| INV-05 | Phase 16 | Pending |
| WAIT-01 | Phase 17 | Pending |
| WAIT-02 | Phase 17 | Pending |
| WAIT-03 | Phase 17 | Pending |
| WAIT-04 | Phase 17 | Pending |

**Coverage:**

- v1 requirements: 20 total
- Mapped to phases: 20 ✓
- Unmapped: 0

---
*Requirements defined: 2026-09-24*
*Last updated: 2026-09-24 — mapped all 20 requirements to Phases 13-17 (roadmap created)*
