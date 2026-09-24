# Architecture Research

**Domain:** Dating-app backend — safety/moderation + gated access (integrating into existing Kotlin/Spring Boot monolith)
**Researched:** 2026-09-24
**Confidence:** HIGH

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                          HTTP / Controller layer                       │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌──────────┐ │
│  │ Block/    │ │ Report    │ │ Invite    │ │ Waitlist  │ │ Auth      │ │
│  │ Unmatch   │ │           │ │ (admin +  │ │ (PUBLIC)  │ │ (signup   │ │
│  │           │ │           │ │  consume) │ │           │ │  gate)    │ │
│  └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └────┬─────┘ │
├────────┼─────────────┼─────────────┼─────────────┼────────────┼───────┤
│                          Service layer                                 │
│  BlockService   ReportService   InviteService   WaitlistService        │
│       │              │  └─(alsoBlock)─┘   │            │               │
│       │              └──> EmailSender <───┴────────────┘  AgeVerifier  │
│       │  (operator notify)        (confirm/invite emails)   (seam)     │
├────────┼───────────────────────────────────────────────────────────────┤
│                    Cross-cutting (existing seams)                      │
│   RateLimitFilter (Bucket4j)   Hashed single-use token model           │
├────────────────────────────────────────────────────────────────────────┤
│                          PostgreSQL + PostGIS                          │
│  blocks   reports   invites   referrals   waitlist_entries             │
│  (+ read-path filters on: swipes/discovery feed, chat, match)          │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `BlockService` | Create/remove block, expose block list, provide the bidirectional block predicate used by other services | `blocks(blocker_id, blocked_id)` table, PK on the pair, reverse index |
| `ReportService` | Persist reports, notify operator, optionally delegate to `BlockService` | `reports` table + `EmailSender` operator notification |
| `InviteService` | Generate operator codes, validate + consume at signup, record referral | `invites` + `referrals`; SecureRandom code, hashed at rest |
| `WaitlistService` | Public capture, double-opt-in confirm, operator convert-to-invite | `waitlist_entries` + hashed confirm token + `EmailSender` |
| `AgeVerifier` (seam) | Decide if a DOB/user passes the 18+ gate | Default: local DOB math; vendor impl swappable later |
| Existing read paths | Enforce block filtering | `DiscoveryService`/`SwipeRepository`, `ChatService`, `MatchService` |

## Recommended Project Structure

```
src/main/kotlin/com/catspell/api/
├── moderation/              # NEW — block, unmatch, report
│   ├── controller/          # BlockController, ReportController
│   ├── service/             # BlockService, ReportService
│   └── model/               # Block, Report entities + repositories
├── invite/                  # NEW — invite codes + referral attribution
│   ├── controller/          # InviteAdminController (operator), consumed in auth signup
│   ├── service/             # InviteService
│   └── model/               # Invite, Referral entities + repositories
├── waitlist/                # NEW — public capture + operator conversion
│   ├── controller/          # WaitlistController (public), WaitlistAdminController
│   ├── service/             # WaitlistService
│   └── model/               # WaitlistEntry + repository
├── auth/                    # MODIFIED — DOB/age gate + invite gate at register
├── discovery/               # MODIFIED — block-exclusion in feed query
├── chat/ + match/           # MODIFIED — block/unmatch teardown + send guard
├── email/                   # REUSED — EmailSender seam
└── common/security/         # REUSED — RateLimitFilter (add public endpoint keys)

src/main/resources/db/migration/   # V19+ (last shipped: V18)
```

### Structure Rationale

- **`moderation/` groups block + unmatch + report:** they share the block relationship and teardown logic; keeping them together avoids a circular dependency between chat and a standalone block module.
- **`invite/` separate from `auth/`:** invite lifecycle (create/consume/attribute) is its own concern; `auth` only *calls* `InviteService.consume()` during registration.
- **`waitlist/` separate + public:** it has an unauthenticated surface, so it needs its own controller and explicit public-endpoint whitelisting (three-place whitelist pattern already established in v2.1).

## Architectural Patterns

### Pattern 1: Bidirectional block filter on read paths

**What:** A single `blocks` table with the pair as PK; every discovery/chat read excludes rows where a block exists in *either* direction.
**When to use:** Everywhere a user could see or contact another user.
**Trade-offs:** Adds a `NOT EXISTS` per read path (cheap, indexed); must be applied consistently or a surface leaks.

**Example:**
```sql
-- Added to BOTH branches of SwipeRepository.findDiscoveryFeed, alongside the
-- existing "NOT EXISTS (SELECT 1 FROM swipes ...)" exclusion:
AND NOT EXISTS (
  SELECT 1 FROM blocks b
  WHERE (b.blocker_id = :requesterId AND b.blocked_id = candidate.user_id)
     OR (b.blocker_id = candidate.user_id AND b.blocked_id = :requesterId)
)
```

### Pattern 2: Signup gate composition (age + invite)

**What:** Registration runs ordered guards — cheap first (DOB/age), then invite validation — before creating the account.
**When to use:** All account creation while the invite gate is ON.
**Trade-offs:** Age gate always on; invite gate behind `app.invite.enabled` so it flips to public without code changes.

**Example:**
```kotlin
fun register(req: RegisterRequest): ... {
    ageVerifier.requireAdult(req.dateOfBirth)              // 400/422 under-18
    if (inviteProps.enabled) {
        val invite = inviteService.validate(req.inviteCode) // 400 invalid/consumed
        // ... create user ...
        inviteService.consume(invite, newUser)              // records referral
    }
}
```
> Note: `date_of_birth` currently lives on `user_profiles`, not on the `users`/register payload. This milestone must decide whether to collect DOB at register time (recommended for a hard gate) or gate at profile completion. Flagged in PITFALLS.

### Pattern 3: Hashed single-use token reuse (waitlist confirm + invite accept)

**What:** Reuse the v2.1 token model — SHA-256 at rest, atomic single-use claim, short TTL — for waitlist confirmation and invite links.
**When to use:** Any emailed link that must be single-use and expiring.
**Trade-offs:** None material; it is the proven in-repo pattern (`PasswordResetToken`, email-verification/email-change tokens).

## Data Flow

### Report-with-block flow

```
[User taps Report]
    ↓
ReportController → ReportService.create(reporter, target, category, details, alsoBlock)
    ↓                        ↓ (if alsoBlock)
persist report          BlockService.block(reporter, target)
    ↓
EmailSender.send(operator notification)  [async / AFTER_COMMIT, never blocks the request]
    ↓
201 Created (generic body)
```

### Waitlist → invite flow

```
[Public join]  POST /api/waitlist {email}
    ↓ (RateLimitFilter: per-IP + per-email)
WaitlistService.join → persist PENDING + hashed confirm token → EmailSender(confirm link)
    ↓  202 Accepted (enumeration-safe, identical body)
[Confirm]  GET /api/waitlist/confirm?token=... → mark CONFIRMED (single-use claim)
    ↓
[Operator]  POST /api/admin/waitlist/{id}/invite → InviteService.create → EmailSender(invite)
```

### Block enforcement fan-out

```
BlockService.block(A, B)
    ├─ insert blocks(A,B)               (idempotent — PK on pair / put-if-absent)
    ├─ tear down existing match(A,B)    (mark ended)
    └─ lock conversation(A,B)           (retain history, no new messages)
Read paths (discovery, chat send, match): consult block predicate synchronously
```

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0–1k users (launch) | Single Postgres instance; `NOT EXISTS` block filters are fine; in-memory Bucket4j sufficient |
| 1k–100k users | Index `blocks(blocked_id, blocker_id)` for reverse lookups; consider a per-user blocked-set cache **only** if measured, and only invalidated synchronously on block/unblock |
| 100k+ users | Move rate-limit counters to a shared store (Redis) if multi-instance — same caveat already documented for push presence registry |

### Scaling Priorities

1. **First bottleneck:** discovery feed query cost with block filter — mitigate with the reverse-direction index; the feed already does heavier PostGIS work.
2. **Second bottleneck:** waitlist write bursts at launch — Bucket4j per-IP throttle + `202` fast path absorb this.

## Anti-Patterns

### Anti-Pattern 1: Standalone block module that chat/discovery import circularly

**What people do:** Put block logic in a leaf module that also depends on chat/match.
**Why it's wrong:** Creates dependency cycles; block must be *consulted by* read paths, not depend on them.
**Do this instead:** Expose a thin block predicate/query from `moderation`; read paths call it (or inline the SQL `NOT EXISTS`).

### Anti-Pattern 2: Eventually-consistent / cached block checks

**What people do:** Cache "is A blocked by B" for performance.
**Why it's wrong:** A stale "not blocked" window is a safety failure (blocked user still sees/messages victim).
**Do this instead:** Strongly-consistent synchronous DB check; cache only with synchronous invalidation on block/unblock.

### Anti-Pattern 3: Deleting rows on unmatch/block

**What people do:** Hard-delete matches/messages.
**Why it's wrong:** Destroys report evidence and breaks the immutable-message-history invariant.
**Do this instead:** Soft state transitions (match `ended`, conversation locked, messages retained).

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Email provider (via `EmailSender`) | Existing seam; no-op default | Operator report notify, waitlist confirm, invite email |
| Age-verification vendor (future) | `AgeVerifier` seam | Not integrated this milestone; keep the interface so it can drop in |
| Landing-page frontend (separate repo) | Calls the public waitlist API | This repo owns only the API contract; frontend is out of scope |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `auth` ↔ `invite` | Direct service call at register | `auth` calls `InviteService.validate/consume`; no reverse dependency |
| `moderation` ↔ `discovery`/`chat`/`match` | Block predicate consulted on read/send | Prefer inline SQL `NOT EXISTS` in queries + a service method for imperative checks |
| `report` ↔ `block` | `ReportService` delegates to `BlockService` when `alsoBlock` | Compose, don't duplicate the relationship write |
| public waitlist ↔ security | Explicit public-endpoint whitelist | Follow the established three-place whitelist pattern |

## Sources

- vibeengines.com, hld.handbook.academy, techinterview.org (User Blocking LLD) — block enforcement + strong consistency (MEDIUM–HIGH)
- techinterview.org Invitation/Referral LLDs — invite/referral table + attribution flow (MEDIUM)
- In-repo: `DiscoveryService`, `SwipeRepository.findDiscoveryFeed`, `PasswordResetService`, `EmailSender`, `RateLimitFilter`, `db/migration/V18` (HIGH)

---
*Architecture research for: dating-app backend — safety + gated access*
*Researched: 2026-09-24*
