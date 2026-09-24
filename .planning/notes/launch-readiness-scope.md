---
title: Launch Readiness Scope & Decisions
date: 2026-09-24
context: Captured during /gsd-explore session. Scopes a "public launch readiness"
  effort bundling five features — age verification, invite-only access, report
  user, block user, and a landing page. Intended as input for /gsd-new-milestone.
  Block/report already exist in PROJECT.md Active requirements; age gate,
  invite-only, and the waitlist/invite-request API are new.
---

# Launch Readiness Scope & Decisions

## Goal

Get Cat Spell ready to open to the public. The five requested features are not
independent — together they form one connected **access + safety layer** on top
of the existing signup/auth flow (email + password + email verification):

- Gate **who** gets in (invite-only) and **how** they arrive (landing page → waitlist).
- Verify users are **adults** (age gate) as they sign up.
- Give users tools to handle **bad actors** (report, block).

This reads as a new milestone (candidate: **v2.2 Launch Readiness**), not loose phases.

## Features & Decisions

### 1. Landing page → waitlist/invite-request API
- The landing **page** itself lives in a **separate frontend repo** (this repo is
  backend-only by constraint).
- In this repo, "landing page" means the **API the page calls**: capture email /
  invite requests into a waitlist.

### 2. Invite-only access (waitlist + referrals)
- **Two-phase model:**
  - **Bootstrap:** people request access via the landing page → waitlist. Admitted
    users receive an invite to sign up.
  - **Growth:** once in, existing members can **refer** friends (invite quota per
    member + referral attribution).
- This **reshapes the existing signup flow** — signup must now be gated by a valid
  invite/referral, layered on top of email verification.

### 3. Age verification (self-attested DOB)
- Collect **date of birth at signup**; **hard-block under-18** (dating app).
- **No third-party ID/KYC vendor** at launch — trust self-attestation.
- Leave room for a stronger vendor-based check later (see seed:
  `third-party-age-verification`).

### 4. Block user
- Already in Active requirements ("Block/report/unmatch safety features").
- Behavior: **unmatch + hide from discovery and chat** in both directions.

### 5. Report user
- Already in Active requirements.
- No admin moderation panel yet (deliberately deferred — see PROJECT.md Out of Scope).
- Decision: **persist reports to a table + notify the owner** (email/log) to act
  manually for now. Review tooling / admin panel is a later milestone.

## Dependencies & Sequencing

1. **Waitlist/invite API** feeds **invite issuance**.
2. **Invite-only** reshapes the **signup flow** (which already has email verification).
3. **Age gate** slots into that same signup path.
4. **Block/report** are independent of the access work and can proceed in parallel.

## Open / Deferred

- Stronger age verification (KYC vendor) — deferred; see seed.
- Admin moderation panel to review reports — deferred to a later milestone.
- "Report implies block" UX coupling — not decided; reporting and blocking are
  currently separate actions.

## Next Step

Run `/gsd-new-milestone` to formally scope this (e.g. v2.2 Launch Readiness) and
decompose into phases using this note as context.
