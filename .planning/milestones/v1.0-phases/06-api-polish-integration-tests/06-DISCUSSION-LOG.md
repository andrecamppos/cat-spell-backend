# Phase 6: API Polish & Integration Tests - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-15
**Phase:** 6-API Polish & Integration Tests
**Areas discussed:** OpenAPI docs setup, Rate limiting approach, Health check depth, Test coverage strategy

---

## OpenAPI Docs Setup

### Swagger UI exposure

| Option | Description | Selected |
|--------|-------------|----------|
| Swagger UI + spec | Expose interactive Swagger UI at /swagger-ui.html plus the raw OpenAPI spec at /v3/api-docs | |
| Spec only | Generate the OpenAPI spec file (JSON/YAML) but don't serve the Swagger UI | ✓ |
| You decide | Let Claude pick the best approach for a mobile-backend API | |

**User's choice:** Spec only
**Notes:** Clients can import the spec into Postman/Insomnia. Lighter footprint.

### Endpoint grouping

| Option | Description | Selected |
|--------|-------------|----------|
| Tag per domain | One tag per domain slice (9 tags matching package structure) | |
| Coarser groups | Fewer tags: Auth, User, Cats, Discovery & Matching, Chat | ✓ |
| You decide | Let Claude pick a grouping | |

**User's choice:** Coarser groups
**Notes:** Simpler for mobile devs consuming the API.

### Documentation detail

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal annotations | Auto-generated from DTOs/Jakarta validation, @Operation only where unclear | ✓ |
| Full annotations | @Operation with summary+description, @ApiResponse per status, @Schema examples | |
| You decide | Let Claude balance quality vs effort | |

**User's choice:** Minimal annotations
**Notes:** Least annotation effort, auto-generation handles most cases.

### Auth scheme in spec

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, global Bearer scheme | Global SecurityScheme(type=HTTP, scheme=bearer, bearerFormat=JWT) | ✓ |
| No auth in spec | Skip auth documentation — mobile team already knows | |
| You decide | Let Claude decide | |

**User's choice:** Yes, global Bearer scheme
**Notes:** Standard practice for JWT APIs. Auth endpoints excluded from security requirement.

---

## Rate Limiting Approach

### Endpoint scope

| Option | Description | Selected |
|--------|-------------|----------|
| Auth only | Rate limit register, login, refresh to prevent brute-force | ✓ |
| Auth + discovery | Auth endpoints plus discovery feed (heaviest query) | |
| Global + stricter auth | Light global limit + stricter auth limits | |

**User's choice:** Auth only
**Notes:** Other endpoints already behind JWT auth.

### Rate key

| Option | Description | Selected |
|--------|-------------|----------|
| By IP address | Standard for unauthenticated endpoints, X-Forwarded-For support | ✓ |
| By IP + email combo | Per IP AND per target email for login | |
| You decide | Let Claude pick | |

**User's choice:** By IP address
**Notes:** Simple and effective for brute-force prevention.

### Implementation

| Option | Description | Selected |
|--------|-------------|----------|
| Bucket4j + Spring filter | Lightweight token-bucket library, servlet Filter, in-memory ConcurrentHashMap | ✓ |
| Custom filter (no library) | Hand-rolled token bucket, zero dependencies | |
| You decide | Let Claude pick simplest approach | |

**User's choice:** Bucket4j + Spring filter
**Notes:** Sufficient for single-instance v1.

### Limits & headers

| Option | Description | Selected |
|--------|-------------|----------|
| 10 req/min + headers | 10 req/min per IP, X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After | ✓ |
| 10 req/min, no headers | Same limit, just 429 without rate limit headers | |
| You decide | Let Claude pick | |

**User's choice:** 10 req/min + headers
**Notes:** Helps mobile client implement backoff.

---

## Health Check Depth

### Indicator depth

| Option | Description | Selected |
|--------|-------------|----------|
| Default actuator only | Auto-configured DB health + /actuator/health and /actuator/info | |
| Custom indicators | Custom HealthIndicators for S3/MinIO and WebSocket broker | ✓ |
| You decide | Let Claude determine | |

**User's choice:** Custom indicators
**Notes:** More visibility into system state beyond auto-configured DB check.

### Info endpoint

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, build info | build-info plugin + optional git-commit-id-plugin | |
| No info endpoint | Skip /actuator/info, health check is sufficient | ✓ |
| You decide | Let Claude decide | |

**User's choice:** No info endpoint
**Notes:** Keep attack surface minimal.

### Detail visibility

| Option | Description | Selected |
|--------|-------------|----------|
| Details always shown | show-details=always, exposes internals | |
| Details when authenticated | show-details=when-authorized, anonymous gets UP/DOWN only | ✓ |
| Aggregate only | show-details=never, most secure | |

**User's choice:** Details when authenticated
**Notes:** Best of both worlds — security + debuggability.

---

## Test Coverage Strategy

### Main goal

| Option | Description | Selected |
|--------|-------------|----------|
| Audit & fill gaps | Systematic audit per endpoint, fill missing happy/error/edge cases | ✓ |
| New test for new code | Focus on Phase 6 code only (OpenAPI, rate limiting, health) | |
| Cross-domain journeys | Full user journey tests spanning multiple domains | |

**User's choice:** Audit & fill gaps
**Notes:** Endpoint-by-endpoint gap analysis across all domains.

### Phase 6 code tests

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, test Phase 6 code too | Integration tests for rate limiting, health, OpenAPI spec | ✓ |
| Gap-fill only | Focus purely on existing domain tests | |
| You decide | Let Claude determine | |

**User's choice:** Yes, test Phase 6 code too
**Notes:** Covers both gap-filling AND new code.

### Validation tightening

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, fix validation gaps | Audit DTOs for missing Jakarta validation annotations | |
| Test gaps only | Only add missing tests, don't modify production code | ✓ |
| You decide | Let Claude assess | |

**User's choice:** Test gaps only
**Notes:** Existing Jakarta validation annotations are sufficient from prior phases.

---

## Claude's Discretion

No areas deferred to Claude's discretion — all decisions made by user.

## Deferred Ideas

None — discussion stayed within phase scope.
