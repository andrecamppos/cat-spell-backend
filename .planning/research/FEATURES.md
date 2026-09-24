# Feature Research

**Domain:** Dating-app backend — safety/moderation + gated access
**Researched:** 2026-09-24
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels unsafe or broken.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Block a user | Every social/dating app has it; safety baseline | MEDIUM | One-way action; enforce **bidirectionally** on discovery + chat. Existing convo history retained but locked (this milestone's chosen behavior) |
| Unblock / view block list | Users mis-tap or reconcile; need reversibility | LOW | List + unblock; unblock re-enables rediscovery |
| Unmatch | End a conversation without full block | LOW | Closes match/conversation; the other user *can* reappear in discovery (distinct from block). Messages preserved (immutable log) |
| Report a user | Required for launch, app-store policy, and legal safety posture | MEDIUM | Category enum + required details; persist + notify operator |
| Age gate (18+) | Legal requirement for dating apps | MEDIUM | Self-attested DOB, hard-block under-18 at signup |
| Invite-required signup (when gated) | Expected of a closed beta / invite-only launch | MEDIUM | Global on/off gate; operator-issued codes to bootstrap |
| Waitlist join | Expected entry point for a pre-launch/gated app | LOW–MEDIUM | Public endpoint + confirmation; feeds the invite funnel |

### Differentiators (Competitive Advantage)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Referral attribution (referrer → invitee) | Bootstraps trusted growth; who-invited-whom for future rewards | MEDIUM | Track linkage now even though member-generated quotas/rewards are out of scope |
| Optional block-on-report | One tap to both report and protect self | LOW | `alsoBlock` flag on the report request; composes report + block atomically |
| Operator waitlist→invite conversion | Controlled, curated onboarding of demand | MEDIUM | Convert a confirmed waitlist email into an invite that emails a code/link |
| `AgeVerifier` seam (vendor-ready) | Drop-in third-party age check later with no call-site churn | LOW (seam only) | Mirrors the `EmailSender`/`PushProvider` abstraction philosophy |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Auto-suspend after N reports | "Automate moderation" | Weaponizable brigading; false positives ban innocent users pre-launch with no appeal path | Persist + notify operator; manual action (this milestone's choice) |
| Hard-delete on block/unmatch | "Clean up data" | Destroys evidence needed for report follow-up + breaks chat history invariants | Soft state: retain messages, flip access flags |
| Third-party ID/age vendor now | "Be fully compliant" | Cost, PII storage, moderation burden before there are users | Self-attested DOB + `AgeVerifier` seam for later |
| Member-generated invite quotas | "Viral growth" | Abuse surface + reward accounting before PMF | Operator-issued codes only this milestone; attribution captured for later |
| Caching block checks | "Performance" | Stale "not blocked" = safety failure | Synchronous DB check on read paths |

## Feature Dependencies

```
Age gate (DOB at signup)
    └──precedes──> Invite-required signup (both gate account creation)

Waitlist join
    └──feeds──> Operator waitlist→invite conversion
                       └──produces──> Invite code
                                          └──consumed by──> Invite-required signup
                                                                 └──records──> Referral attribution

Block a user
    ├──enforced on──> Discovery feed (bidirectional NOT EXISTS filter)
    ├──enforced on──> Chat (no new messages; existing convo locked)
    └──composed by──> Report (optional alsoBlock flag)

Unmatch ──sibling-of──> Block (shares match/conversation teardown, but no rediscovery ban)
```

### Dependency Notes

- **Invite-required signup requires the invite store + gate flag:** codes must exist and be validatable before the gate can enforce.
- **Referral attribution requires invite consumption:** the referrer is derived from the consumed invite/code at registration time.
- **Operator conversion requires waitlist capture:** can't convert what wasn't captured; waitlist join must ship first (or same phase).
- **Report(alsoBlock) enhances Block:** report should reuse the block service rather than duplicate the relationship write.
- **Block conflicts with rediscovery:** a blocked pair must be filtered from the feed; unmatch must NOT filter (only block does).

## MVP Definition

### Launch With (v2.2)

- [ ] Block + unblock + list — core safety, bidirectional enforcement on discovery & chat
- [ ] Unmatch — teardown without rediscovery ban
- [ ] Report a user — category + details, persist + operator email, optional alsoBlock
- [ ] Age gate — self-attested DOB, hard-block under-18 at signup
- [ ] Invite-only gate — global flag, operator-issued codes, referral attribution
- [ ] Waitlist — public join + double-opt-in confirmation + anti-abuse; operator convert-to-invite

### Add After Validation (v2.x)

- [ ] Report triage/status workflow (reviewed/actioned/dismissed) once volume warrants
- [ ] Member-generated invites with per-member quotas + referral rewards
- [ ] Waitlist position / referral leaderboard surfacing

### Future Consideration (v3+)

- [ ] Third-party age/ID verification vendor (EU/UK/AU compliance)
- [ ] Admin moderation panel (PROJECT.md already scopes this as post-block/report)
- [ ] Automated content/abuse scoring

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Block / unblock / list | HIGH | MEDIUM | P1 |
| Report a user | HIGH | MEDIUM | P1 |
| Age gate (18+) | HIGH | MEDIUM | P1 |
| Unmatch | MEDIUM | LOW | P1 |
| Invite-only gate + operator codes | HIGH | MEDIUM | P1 |
| Referral attribution | MEDIUM | LOW–MEDIUM | P2 |
| Waitlist join + double opt-in | MEDIUM | MEDIUM | P1 |
| Operator waitlist→invite conversion | MEDIUM | MEDIUM | P2 |

**Priority key:** P1 = must have for this milestone · P2 = should have, same milestone if capacity · P3 = future

## Competitor Feature Analysis

| Feature | Tinder/Hinge/Bumble | Closed-beta apps (Raya, early Clubhouse) | Our Approach |
|---------|---------------------|------------------------------------------|--------------|
| Block | One-way, bidirectional hide, retain nothing visible | same | One-way, bidirectional hide, **retain convo history locked** |
| Report | Category enum + details, backed by moderation team | category enum | Category + details, persist + operator email (no panel yet) |
| Age gate | Self-attested DOB, escalating to photo/vendor in regulated regions | DOB | Self-attested DOB now, `AgeVerifier` seam for vendor later |
| Invite gate | N/A (open) | Invite/approval required | Global gate + operator-issued codes + referral attribution |
| Waitlist | N/A | Waitlist → curated invites | Public join + double opt-in → operator conversion |

## Sources

- HLD/system-design write-ups (hld.handbook.academy, vibeengines.com, chiraghasija.cc) — block/unmatch/report expectations (MEDIUM)
- Hinge Help Center, Apple age-assurance, CA AB-1043 — age-gate norms + legal drivers (HIGH)
- Waitlister/QueueUp/LaunchList docs — waitlist + double-opt-in + referral norms (MEDIUM)
- In-repo PROJECT.md Active requirements + existing discovery/match/chat behavior (HIGH)

---
*Feature research for: dating-app backend — safety + gated access*
*Researched: 2026-09-24*
