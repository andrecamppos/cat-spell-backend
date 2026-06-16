# Phase 1: Foundation & Auth - Context

**Gathered:** 2025-06-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver a runnable Spring Boot application with PostgreSQL, Flyway migrations, and complete JWT authentication (register, login, refresh). This is the foundation that every subsequent phase builds on.

</domain>

<decisions>
## Implementation Decisions

### Package Structure
- **D-01:** Domain-first vertical slices — each business domain (auth, profile, discovery, chat) owns its controller/service/model sub-packages
- **D-02:** Base package is `com.catspell.api` — domain packages are `com.catspell.api.auth`, `com.catspell.api.profile`, etc.
- **D-03:** Cross-cutting concerns live in `com.catspell.api.common.*` — sub-packages for config, security, exception handling
- **D-04:** Minimal sub-packages within each domain: `controller/`, `service/`, `model/` (entities and DTOs together)

### Token Lifecycle
- **D-05:** JWT access token expires after 1 hour
- **D-06:** Rotating refresh tokens stored in database — each refresh issues a new token and invalidates the old one (detects token theft via reuse)
- **D-07:** Refresh token expires after 30 days of inactivity
- **D-08:** Multi-device sessions allowed — each device gets its own refresh token, all stay active simultaneously

### Registration Flow
- **D-09:** Registration endpoint returns tokens immediately (auto-login) — `POST /auth/register` → 201 with access + refresh tokens
- **D-10:** No email verification for v1 — users start immediately after registration
- **D-11:** Registration requires email + password only — display name and profile details come in Phase 2
- **D-12:** Password minimum 8 characters, no complexity rules (follows NIST guidelines favoring length over complexity)

### API Error Format
- **D-13:** RFC 7807 Problem Details format — uses Spring Boot 3.3 built-in `ProblemDetail` class
- **D-14:** Validation errors include field-level detail via `violations` array: `[{field, message}]` — mobile app can highlight specific form fields
- **D-15:** Safe production defaults — validation errors show field details; auth errors are vague ("Invalid credentials", never reveal if email exists); 500s show generic message with full stack logged server-side

### Claude's Discretion
No areas deferred to Claude's discretion — all decisions made by user.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md` — Core value, constraints (Kotlin + Spring Boot, PostgreSQL, JWT auth), out-of-scope items
- `.planning/REQUIREMENTS.md` — AUTH-01, AUTH-02, AUTH-03 requirement definitions and traceability
- `.planning/ROADMAP.md` §Phase 1 — Success criteria for this phase

### Stack Research
- `.planning/research/STACK.md` — Recommended versions, dependencies, setup instructions, Kotlin entity gotchas
- `.planning/research/SUMMARY.md` — Architecture approach, critical pitfalls (Kotlin entity gotchas, N+1 queries, WebSocket auth)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — greenfield project, no existing code

### Established Patterns
- None — this phase establishes the foundational patterns for all subsequent phases

### Integration Points
- None — this is the first phase, creating the initial project structure

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Stack research recommends:
- jjwt 0.12.x for JWT creation/validation
- Spring Security for auth filters
- Flyway for database migrations
- Podman with PostgreSQL for local dev (`podman compose`)
- Don't use Kotlin data classes for JPA entities (use kotlin-jpa plugin)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 1-Foundation & Auth*
*Context gathered: 2025-06-09*
