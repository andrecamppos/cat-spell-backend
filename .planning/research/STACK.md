# Stack Research

**Domain:** Dating-app backend — safety/moderation + gated access (age gate, invite-only, waitlist)
**Researched:** 2026-09-24
**Confidence:** HIGH

> Subsequent milestone (v2.2) on an existing Kotlin/Spring Boot 4.0 + PostgreSQL/PostGIS + Flyway + Bucket4j backend. Focus is on what the NEW features need. The core stack is fixed and non-negotiable (see PROJECT.md constraints); this milestone should add **almost nothing** and instead reuse proven in-repo seams.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin / Spring Boot | 4.0 (in repo) | HTTP + service + JPA layer | Already the stack; every new domain (block/report/invite/waitlist) is a standard controller→service→repository slice |
| PostgreSQL + PostGIS | 16 / 3.4 (in repo) | Relational store for blocks, reports, invites, referrals, waitlist | Blocks/invites are relational join data; partial unique indexes + `NOT EXISTS` filters (already used for swipe dedupe) fit exactly |
| Flyway | in repo | Schema migrations | New tables land as `V19+`; last shipped is `V18__create_email_change_requests_table.sql` |
| Bucket4j | in repo (`RateLimitFilter`) | Rate limiting waitlist join + report/block abuse | Already the enumeration-safe throttle used by forgot/reset/resend; reuse the per-IP + per-key pattern, no new dependency |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.security.SecureRandom` (JDK) | JDK 17 | Generate unguessable invite codes + waitlist confirmation tokens | Already implicitly used by the token model; no library needed |
| Existing `EmailSender` seam | in repo | Waitlist confirmation email, invite email, operator report notification | No-op logging default in dev/CI; concrete provider swappable — reuse unchanged |
| Existing hashed single-use token model | in repo | Waitlist double-opt-in token + invite acceptance token | SHA-256-at-rest, atomic single-use claim, TTL — the exact pattern from `PasswordResetToken` / email-verification tokens |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Testcontainers (Postgres + PostGIS) | Integration tests for every new domain | Existing convention — no H2; new suites follow the same base class |
| MockK / mocked `EmailSender` | Assert emails are triggered without network sends | Same approach used across recovery/verification suites |

## Installation

```bash
# No new runtime dependencies expected for v2.2.
# Everything reuses in-repo seams: EmailSender, RateLimitFilter (Bucket4j),
# the hashed single-use token pattern, and Flyway migrations.

# OPTIONAL (only if disposable-email blocking is chosen for the waitlist):
# a static disposable-domain blocklist can be vendored as a plain resource
# file (e.g. a bundled text list) rather than adding a dependency.
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Self-attested DOB age gate | Third-party age-estimation vendor (Yoti, Veriff, Persona, Onfido, FaceTec; Apple Declared Age Range API) | When legally required in a target market (EU DSA, UK OSA, AU Social Media Minimum Age Act) or when photo-based estimation is needed. **Deferred per existing seed** — keep an `AgeVerifier` seam so a vendor can drop in later without touching call sites |
| Bucket4j per-IP/per-email throttle on waitlist | reCAPTCHA v3 / proof-of-work challenge | If bot volume defeats IP throttling after launch; add as a second layer, not a replacement |
| Static disposable-domain blocklist | Live MX / SMTP verification service | If garbage signups become costly; MX lookup is a cheap middle ground before paid SMTP verification |
| SHA-256 hashed single-use invite/confirmation tokens | Signed JWT invite links | JWTs are stateless but can't be revoked or single-used without a store anyway; the existing hashed-token table already gives single-use + revocation |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| A new auth/permission framework for blocking | Blocking is a simple relational check, not an ACL system | A `blocks` table + `NOT EXISTS` filter on read paths (mirrors swipe exclusion) |
| Caching the block relationship | A stale "not blocked" answer is a safety failure, not a minor inconsistency | Synchronous, strongly-consistent DB check on every relevant read path |
| Storing raw invite codes / confirmation tokens | Leak of the table = account access | SHA-256 hash at rest, compare by hash (existing token pattern) |
| Third-party age vendor now | Cost + PII + moderation burden before launch; not required for MVP gate | Self-attested DOB hard-block behind an `AgeVerifier` seam |
| Member-generated invite quotas | Out of scope this milestone (operator-issued only) | Operator-issued codes + referral attribution only |

## Stack Patterns by Variant

**If invite-only is toggled ON (`app.invite.enabled=true`):**
- Registration requires a valid, unconsumed invite code
- Because the global gate is the launch switch; flip OFF to go fully public

**If waitlist double-opt-in is enabled:**
- Public join returns `202` (enumeration-safe), confirmation email carries a hashed single-use token with a TTL (48h is the common default)
- Because bots rarely complete the confirm step; it doubles as email-validity proof

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Flyway `V19+` | existing `V1..V18` chain | Append-only; never edit shipped migrations |
| Bucket4j `RateLimitFilter` | new public `/waitlist` + `/invite` endpoints | Add endpoint keys to the existing filter's config rather than a new filter |

## Sources

- techinterview.org — Invitation System / Referral System / User Blocking LLDs — schema + code-generation patterns (MEDIUM–HIGH)
- getqueueup.com, waitlister.me, getlaunchlist.com — waitlist anti-abuse + double-opt-in defaults (MEDIUM)
- Apple Developer "Age assurance", Hinge Help "Age Checks", CA AB-1043 — age-gating legal landscape (HIGH)
- In-repo: `RateLimitFilter.kt`, `EmailSender.kt`, `PasswordResetToken.kt`, `SwipeRepository.findDiscoveryFeed`, `db/migration/V18` (HIGH)

---
*Stack research for: dating-app backend — safety + gated access*
*Researched: 2026-09-24*
