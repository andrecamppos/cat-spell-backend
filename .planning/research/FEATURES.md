# Feature Research

**Domain:** Dating app backend (niche/cat-focused)
**Researched:** 2025-06-09
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Email/password registration & login | Every dating app has account creation | MEDIUM | JWT tokens, email verification, password hashing |
| User profiles with photos | Core identity in any dating app | MEDIUM | Multiple photos, bio, basic info |
| Swipe/browse discovery feed | Primary interaction loop | MEDIUM | Paginated feed with filtering, no repeats |
| Mutual matching | Two-way interest confirmation | LOW | Both users like → match created |
| Text chat after match | Communication channel for matches | HIGH | WebSocket real-time, message persistence, read receipts |
| Geolocation filtering | Users expect local matches | MEDIUM | PostGIS distance queries, configurable radius |
| Profile photo upload | Visual-first medium | MEDIUM | S3 presigned URLs, image validation |
| Session management | Stay logged in, token refresh | LOW | JWT access + refresh token pattern |
| Password reset | Account recovery | LOW | Email-based reset flow |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required generically, but core to Cat Spell's value.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Cat profiles (name, age, breed, personality traits) | Cat-first identity — the core differentiator | MEDIUM | Structured data + photos per cat |
| Multi-cat household with primary cat selection | Supports real cat owners (many have 2+ cats) | LOW | One-to-many user→cats, featured flag |
| Cat-first reveal (cat profile in swipe, owner behind tap) | Creates curiosity and emotional engagement | LOW | API design — feed returns cat data, owner data on separate endpoint |
| Cat compatibility scoring | "Would our cats get along?" matching signal | MEDIUM | Trait-based scoring (temperament, energy, indoor/outdoor) |
| Lifestyle compatibility from cat signals | Cat count/breeds/care = lifestyle proxy | MEDIUM | Weighted scoring algorithm |
| Combined match score (human prefs + cat compatibility) | Holistic matching unique to cat dating | HIGH | Multi-factor scoring, weighting, tuning |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| AI-generated cat personality descriptions | Fun, saves user effort | Inaccurate, feels generic, removes personal touch | Let owners write personality traits — authenticity matters |
| Super-likes / boost purchases (monetization) | Revenue potential | Breaks trust in a niche community, feels predatory | Defer monetization until community is established |
| Video profiles | Rich media engagement | High storage/bandwidth cost, moderation burden | Photo-only for v1, video in v2 with moderation |
| "Cat playdate" scheduling | Extends beyond dating into cat socialization | Scope creep, complex scheduling, liability | Keep focus on human dating; cat compatibility is the differentiator |

## Feature Dependencies

```
[Authentication]
    └──requires──> [Database + Migrations]

[User Profiles]
    └──requires──> [Authentication]
    └──requires──> [Photo Upload]

[Cat Profiles]
    └──requires──> [User Profiles]
    └──requires──> [Photo Upload]

[Discovery Feed]
    └──requires──> [Cat Profiles]
    └──requires──> [Geolocation]
    └──requires──> [Matching Algorithm]

[Matching Algorithm]
    └──requires──> [Cat Profiles] (cat compatibility)
    └──requires──> [User Profiles] (preferences)

[Mutual Match]
    └──requires──> [Discovery Feed] (swipe actions)

[Chat]
    └──requires──> [Mutual Match]
    └──requires──> [WebSocket infrastructure]
```

### Dependency Notes

- **Discovery Feed requires Cat Profiles:** Feed shows cat profiles; must have cat data first
- **Matching Algorithm requires Cat Profiles:** Cat compatibility scoring needs cat trait data
- **Chat requires Mutual Match:** Chat only unlocks after both users express interest
- **Cat Profiles require Photo Upload:** Cat photos are essential to the cat-first experience

## MVP Definition

### Launch With (v1)

Minimum viable product — what's needed to validate the concept.

- [ ] Email/password auth with JWT — baseline account management
- [ ] User profiles (bio, photos, preferences, location) — human identity
- [ ] Cat profiles (name, age, breed, photos, personality traits) — cat identity
- [ ] Multi-cat with primary selection — real-world cat ownership support
- [ ] Cat-first discovery feed — core differentiator
- [ ] Cat-influenced matching algorithm — core differentiator
- [ ] Geolocation filtering — distance-based discovery
- [ ] Mutual matching — two-way interest
- [ ] Real-time WebSocket chat — communication after match
- [ ] S3 photo upload — photo management

### Add After Validation (v1.x)

- [ ] Push notifications — when matched or messaged (add once chat validates)
- [ ] Block/report/unmatch — safety features (add before wider launch)
- [ ] Email notifications for matches/messages — re-engagement (add when retention data available)

### Future Consideration (v2+)

- [ ] OAuth social login — reduces signup friction (after initial growth)
- [ ] Admin moderation panel — content review (before scaling)
- [ ] Video profiles — richer media (after storage/bandwidth budget)
- [ ] Chat media sharing — share cat photos in chat (after chat validates)
- [ ] Premium features / monetization — only after community trust

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Cat profiles | HIGH | MEDIUM | P1 |
| Cat-first discovery feed | HIGH | MEDIUM | P1 |
| Cat compatibility matching | HIGH | HIGH | P1 |
| Real-time chat | HIGH | HIGH | P1 |
| Auth + user profiles | HIGH | MEDIUM | P1 |
| Geolocation filtering | MEDIUM | MEDIUM | P1 |
| Multi-cat households | MEDIUM | LOW | P1 |
| Photo upload (S3) | HIGH | MEDIUM | P1 |
| Block/report | MEDIUM | LOW | P2 |
| Push notifications | MEDIUM | MEDIUM | P2 |
| Admin panel | LOW | HIGH | P3 |
| Video profiles | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature | Tinder/Bumble | Tabby (cat dating) | Our Approach |
|---------|---------------|---------------------|--------------|
| Profile structure | Human-only profiles | Cat + human combined | Separate cat & human profiles, cat-first reveal |
| Matching | Location + preferences | Basic preferences | Cat compatibility + lifestyle signals + location |
| Discovery | Swipe on human photos | Swipe on combined | Swipe on cat profiles, owner details behind tap |
| Multi-pet | Not supported | Single pet | Multi-cat with primary selection |
| Chat | Full-featured + media | Basic text | Real-time WebSocket with typing/read receipts |

## Sources

- Dating app industry patterns (Tinder, Bumble, Hinge architecture public talks)
- Niche dating app case studies (pet-focused apps)
- Spring Boot WebSocket documentation
- PostGIS documentation for geospatial dating queries

---
*Feature research for: dating app backend (cat-focused)*
*Researched: 2025-06-09*
