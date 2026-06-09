# Pitfalls Research

**Domain:** Dating app backend (Kotlin / Spring Boot)
**Researched:** 2025-06-09
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: Discovery Feed N+1 Query Explosion

**What goes wrong:**
Discovery feed loads 20 cat profiles, each triggering separate queries for photos, traits, and owner existence check. Response times balloon from 50ms to 2+ seconds.

**Why it happens:**
JPA lazy loading defaults. Developers build entity relationships and forget that each `.photos` access triggers a query.

**How to avoid:**
Use `@EntityGraph` or JPQL `JOIN FETCH` for feed queries. Write a dedicated feed projection DTO that fetches everything in 1-2 queries.

**Warning signs:**
Slow feed endpoint (>200ms), Hibernate query count per request >5 for feed.

**Phase to address:**
Discovery/matching phase — when building the feed endpoint.

---

### Pitfall 2: JWT Secret/Key Management

**What goes wrong:**
JWT signing key hardcoded in `application.yml` or committed to git. Token generation uses weak HMAC with short keys.

**Why it happens:**
Quick setup tutorials use inline secrets. Developers copy-paste without securing.

**How to avoid:**
Use RSA key pair for JWT signing. Load keys from environment variables or external secrets manager. Never commit keys.

**Warning signs:**
`jwt.secret=mysecretkey` in config files. HMAC-SHA256 with <256-bit key.

**Phase to address:**
Authentication phase — during JWT setup.

---

### Pitfall 3: Geospatial Query Without Proper Indexing

**What goes wrong:**
Distance filtering queries scan entire user table for every feed request. Works with 100 users, breaks at 10k.

**Why it happens:**
PostGIS `ST_DWithin` works without spatial index but uses sequential scan. Developers don't notice until data grows.

**How to avoid:**
Create GiST index on the geometry/geography column in the first migration that adds location. Use `geography` type (not `geometry`) for earth-distance accuracy.

**Warning signs:**
`EXPLAIN ANALYZE` shows Seq Scan on user table for distance queries.

**Phase to address:**
Profile/geolocation phase — when adding location fields.

---

### Pitfall 4: WebSocket Authentication Gap

**What goes wrong:**
WebSocket connections accept any client, or JWT validation happens only on HTTP upgrade but not on subsequent STOMP frames. Users can impersonate others in chat.

**Why it happens:**
Spring WebSocket security is configured differently from REST security. `SecurityFilterChain` doesn't apply to WebSocket messages by default.

**How to avoid:**
Validate JWT in `HandshakeInterceptor` during CONNECT. Set `Principal` on the session. Use Spring Security's `@MessageMapping` security annotations or `ChannelInterceptor` to verify identity on every message.

**Warning signs:**
Chat works without auth token. Users can send messages as other users.

**Phase to address:**
Chat phase — during WebSocket setup.

---

### Pitfall 5: Match Race Condition (Double Match)

**What goes wrong:**
Two users like each other simultaneously. Both requests detect "other user liked me" and both create a Match record, resulting in duplicate matches.

**Why it happens:**
No database-level uniqueness constraint or optimistic locking on match creation.

**How to avoid:**
Add unique constraint on match pair (user_a_id, user_b_id) with ordered IDs (smaller ID always first). Use `INSERT ... ON CONFLICT DO NOTHING` for atomic match creation.

**Warning signs:**
Duplicate match entries in database. User sees same match twice.

**Phase to address:**
Discovery/matching phase — when implementing like/match flow.

---

### Pitfall 6: Kotlin Entity Gotchas with Hibernate

**What goes wrong:**
Kotlin data classes used for JPA entities cause issues: no no-arg constructor, broken equals/hashCode with proxy objects, immutable properties vs. Hibernate dirty checking.

**Why it happens:**
Kotlin idioms (data classes, val properties) conflict with Hibernate's proxy/reflection requirements.

**How to avoid:**
Use regular Kotlin classes (not data classes) for entities. Use `kotlin-jpa` compiler plugin for no-arg constructors. Use `var` for mutable fields. Override `equals`/`hashCode` based on ID only.

**Warning signs:**
`InstantiationException` on entity load. Dirty checking not detecting changes. Broken collections.

**Phase to address:**
Foundation phase — when setting up entities.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Embedded STOMP broker | No external dependency for chat | Single-instance only, no horizontal scaling | MVP with <5k users |
| Synchronous email sending | Simpler code | Blocks auth flow for 1-3 seconds | Never — always use @Async |
| Single DB for everything | Simple deployment | Chat messages grow fast, pollutes main DB | MVP, split chat DB at scale |
| No image validation | Faster upload flow | Malicious files, wrong formats | Never — validate mime type and size |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| S3 presigned URLs | Generating download URLs with long expiry | Short-lived presigned URLs (15 min) for uploads, serve via CDN/proxy for downloads |
| PostGIS | Using `geometry` type with SRID 4326 | Use `geography` type for accurate earth-distance calculations |
| Spring Security + WebSocket | Applying HTTP security config to WS | Separate `WebSocketSecurityConfig` with `@EnableWebSocketSecurity` |
| Flyway + Hibernate | Letting Hibernate `ddl-auto` create tables alongside Flyway | Set `ddl-auto=validate` — Flyway owns schema, Hibernate validates |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| No pagination on feed | Slow response, high memory | Always paginate, default 20 items | 100+ eligible profiles |
| Loading all messages for chat | Chat opening takes seconds | Paginate messages, load latest 50 | 500+ messages in conversation |
| Matching score computed on every request | Feed latency increases linearly | Cache scores, recompute on profile change | 1k+ active users |
| No connection pooling tuning | Connection exhaustion under load | Configure HikariCP pool size (10-20 for starter) | 50+ concurrent requests |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Location stored at full GPS precision | Stalking risk — user location pinpointed | Round coordinates to ~1km precision, never expose exact coords |
| Chat messages unencrypted in DB | Data breach exposes private conversations | Encrypt message content at rest, or use application-level encryption |
| No rate limiting on auth endpoints | Brute force attacks, credential stuffing | Rate limit login/register to 5/min per IP |
| Photo URLs guessable | Unauthorized access to private photos | Use random UUIDs in S3 keys, short-lived presigned URLs |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Showing same profiles repeatedly | Frustrating, feels broken | Track "seen" profiles, exclude from future feeds |
| Empty feed for new users | Feels like dead app | Show profiles from wider radius, or all profiles initially |
| No "undo" on swipe | Accidental pass on great match | Defer to v2 — focus on core flow first |
| Matching on inactive accounts | Matches that never respond | Track last active, deprioritize inactive (>30 days) |

## "Looks Done But Isn't" Checklist

- [ ] **Auth:** Often missing refresh token rotation — verify expired refresh tokens can't be reused
- [ ] **Photo upload:** Often missing file size limits — verify max upload size is enforced (e.g., 10MB)
- [ ] **Chat:** Often missing message ordering guarantee — verify messages display in send order, not arrival order
- [ ] **Feed:** Often missing "already seen" tracking — verify passed profiles don't reappear
- [ ] **Geolocation:** Often missing null handling — verify feed works for users who haven't set location
- [ ] **Matching:** Often missing edge case for self-match — verify user can't match with themselves

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| N+1 queries | LOW | Add `@EntityGraph` or `JOIN FETCH` to repository methods |
| JWT key in git | HIGH | Rotate keys, invalidate all tokens, force re-login |
| No spatial index | LOW | Add GiST index migration, no downtime on small data |
| Duplicate matches | MEDIUM | Add unique constraint + migration to deduplicate existing data |
| Kotlin entity issues | MEDIUM | Refactor entities from data class to regular class, update equals/hashCode |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Kotlin entity gotchas | Foundation/setup | Entities use regular classes, kotlin-jpa plugin configured |
| JWT key management | Authentication | Keys loaded from env, not in git |
| Geospatial indexing | Profile/geolocation | GiST index in migration, EXPLAIN shows Index Scan |
| N+1 feed queries | Discovery feed | Feed endpoint uses ≤3 queries for 20 items |
| WebSocket auth gap | Chat | Chat rejects unauthenticated STOMP frames |
| Match race condition | Matching | Unique constraint on match pairs, concurrent test passes |

## Sources

- Spring Boot + Kotlin official best practices
- Hibernate/JPA with Kotlin gotchas (JetBrains docs)
- PostGIS documentation and performance guides
- Dating app engineering blog posts (security and scale lessons)

---
*Pitfalls research for: dating app backend (Kotlin/Spring Boot)*
*Researched: 2025-06-09*
