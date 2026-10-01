# Phase 16: Invite-Only Access & Referral - Research

**Researched:** 2026-09-29
**Domain:** Gated account creation (invite-only signup gate + referral attribution) in a Kotlin/Spring Boot monolith
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Operator issuance mechanism (INV-02)**
- **D-01:** The operator issues codes through a **shared-secret-protected admin HTTP endpoint** (e.g. `POST /api/admin/invites`). Chosen over bootstrap-seed-on-startup and manual/out-of-band SQL because it is the most consistent with existing config-driven patterns, gives a real, integration-testable API surface, and provides the programmatic `InviteService.create(...)` path that Phase 17 (waitlist → invite conversion) will reuse.
- **D-02:** **One code per issuance call** (single-code request → single code returned). No batch/count parameter this phase — keeps the contract minimal; the operator loops if they need several.
- **D-03:** Because there is **no admin JWT user** in the app (`SecurityConfig` only distinguishes public vs. authenticated, and there are no roles), the admin route is **whitelisted `permitAll` in `SecurityConfig`** (mirroring the `/api/auth/*` public-endpoint whitelist) and guarded by a **static shared secret** validated in the controller/service — an `X-Admin-Token` header matched against an `app.invite.admin-token` config key (mirrors the `app.report.operator-email` config style). A missing/wrong secret returns a **generic 401/403 RFC 7807** body. A dedicated Spring Security filter + `ADMIN` authority was rejected to avoid introducing a role concept the app doesn't otherwise have.

**Code lifecycle & attributes (INV-02, INV-04)**
- **D-04:** Invite codes have **no expiry** this phase. Single-use plus operator-controlled issuance volume bounds abuse; a TTL (`expires_at` + `app.invite.ttl-days`) is deferred. (If added later, expired codes must return the same generic error as consumed/invalid.)
- **D-05:** **Revocation is deferred** — no revoke endpoint/status this phase. Single-use + controlled issuance limits the blast radius of a leaked code.
- **D-06:** The **raw code is returned exactly once** in the issuance response. Codes are **stored hashed** (SHA-256 at rest, reusing the v2.1 hashed-token discipline), so the operator cannot retrieve a raw code after creation — it must be captured from the create response.
- **D-07:** Codes are **single-use** (INV-04). Consumption is enforced with an **atomic compare-and-set claim** (conditional update matching zero rows on a second attempt), mirroring the v2.1 single-use token pattern — no read-check-write race. Exact code format/length/entropy (URL-safe SecureRandom) is Claude's discretion; must be high-entropy and non-sequential (research Pitfall 4).

**Register flow & error contract (INV-03, INV-04)**
- **D-08:** The invite check composes **after** the age gate in `AuthService.register` — the existing register order (`ageVerifier.requireAdult(...)` first, then the duplicate-email check, then `userRepository.save(...)`) already leaves the slot. Order: age gate → invite validate → (existing duplicate-email check + user save) → invite consume + referral record. Invite validation/consumption happen **before** the account row is committed / paired atomically so a failed consume never yields an orphan account. Add an `inviteCode` field to `RegisterRequest` (`AuthDtos.kt`), optional at the DTO level (required only when gated, enforced in service).
- **D-09:** **When the gate is OFF (`app.invite.enabled=false`), the `inviteCode` field is ignored entirely** — not read, not consumed, no referral recorded. "Public" means the invite path does not run. (This is why referral-while-public is out of scope.)
- **D-10:** **Invalid, already-consumed, AND missing-when-gated invite codes all return an identical generic `403`** with the same RFC 7807 body and a single generic machine-readable `code` (e.g. `INVITE_REQUIRED` / `invalid-or-expired-invite`) — maximum enumeration safety (research Pitfall 4: invalid vs consumed must be indistinguishable). `403` = "not allowed to register." Exact exception class name, `code` string, and 7807 title/detail copy are Claude's discretion; model the exception + `GlobalExceptionHandler` mapping on the existing `EmailNotVerifiedException` (403) / under-age (422) precedents.

**Referral attribution model (INV-05)**
- **D-11:** The invite carries an **optional, nullable `referrer_user_id`** accepted at issuance. **Operator bootstrap codes leave it null.** At consumption, a **`referrals(referrer_id → invitee_id)` row is written ONLY when the invite has a non-null referrer.** This satisfies INV-05 where attribution exists, keeps bootstrap codes clean (no meaningless null-referrer rows), and future-proofs member-generated invites (deferred). Chosen over "always write a referrals row (nullable referrer)" and over deferring the referrer concept entirely.
- **D-12:** When a `referrer_user_id` is supplied at issuance, its **existence is validated** — issuance is rejected (`400`) if it doesn't match a real user, preventing dangling attribution. Guard against self-referral defensively (referrer ≠ invitee) even though the invitee is a brand-new account at consumption. `referrals` row captures at least `(referrer_id, invitee_id, invite_id, created_at)`; exact columns/indexes are Claude's discretion.

### Claude's Discretion
- Exact invite code format/length/entropy (URL-safe SecureRandom), and the `invites` / `referrals` table column shapes, types, and indexes (subject to: code hashed at rest, single-use claim, nullable `referrer_user_id`).
- The invite exception class name, generic `code` string, and RFC 7807 title/detail copy for the 403 (D-10), and the generic 401/403 shape for a bad admin secret (D-03).
- Admin endpoint path/DTO naming (`/api/admin/invites` suggested), the `InviteService` method signatures (`create`/`validate`/`consume`), and package placement (`com.catspell.api.invite` per ARCHITECTURE.md).
- Exact transaction boundary that keeps invite consumption + user creation atomic (no orphan account on consume failure), following existing `@Transactional` service conventions.
- Whether `app.invite.admin-token` has a dev default vs. is required (fail-fast) — follow existing `app.*` config conventions.

### Deferred Ideas (OUT OF SCOPE)
- **Member-generated invites, per-member quotas, and referral rewards** — explicitly out of scope; only operator-issued codes this phase. The nullable `referrer_user_id` (D-11) is the forward-compatible hook.
- **Invite code expiry / TTL** (`expires_at` + `app.invite.ttl-days`) — deferred (D-04); no expiry this phase.
- **Operator revocation of unused codes** — deferred (D-05); single-use + controlled issuance limits leak impact.
- **Waitlist → invite conversion + emailing the code/link to the invitee** — Phase 17 (reuses `InviteService.create` and `EmailSender`).
- **Referral tracking while the gate is OFF (public mode)** — declined (D-09); public mode ignores codes entirely.
- **A general admin role / RBAC system** — declined this phase (D-03); a static shared-secret guard is sufficient for operator-only issuance.
- **Batch code issuance** — declined (D-02); one code per call.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INV-01 | A global config gate enforces invite-required signup and can be flipped off to go fully public | `@Value("\${app.invite.enabled:false}")` boolean read in `AuthService.register`; when false the entire invite path is skipped (D-09). Mirrors the `push.enabled` / `email.enabled` toggles and `app.age.minimum-age` `@Value` convention. See Architecture Pattern 1 + Code Examples. |
| INV-02 | The operator can issue invite codes to bootstrap the first users | Shared-secret admin endpoint `POST /api/admin/invites` → `InviteService.create(referrerUserId?)` returns the raw code once (D-01/D-02/D-06). Whitelisted `permitAll` in `SecurityConfig` + constant-time `X-Admin-Token` check. See Architecture Pattern 3. |
| INV-03 | When the gate is on, signup requires a valid, unconsumed invite code | `InviteService.validate(code)` composed after `ageVerifier.requireAdult(...)` in `register`; a missing/invalid/consumed code throws the generic 403 (D-08/D-10). See Architecture Pattern 1 + Pitfall 1. |
| INV-04 | Codes are high-entropy, stored hashed, and single-use; invalid vs consumed codes return an identical generic error | Reuse the in-repo `SecureRandom` 32-byte + URL-safe Base64 generator (`EmailVerificationService.generateRawToken`), SHA-256 hex hash at rest (`hashToken`), and the atomic conditional-UPDATE single-use claim (`PasswordResetTokenRepository.markUsed`). One exception → one 403 body. See Don't Hand-Roll + Pitfall 2/3. |
| INV-05 | Referral attribution (referrer → invitee) is recorded when an invite is consumed | `referrals` table + `InviteService.consume(invite, newUser)` inserts a row only when `invite.referrerUserId != null` (D-11); issuance validates the referrer exists (D-12). See Architecture Pattern 2 + Data Flow. |
</phase_requirements>

## Summary

This phase adds an **invite-only signup gate** and **referral attribution** to an existing Kotlin 2.4 / Spring Boot 4.0.6 monolith. It is a pure additive integration on top of infrastructure the codebase already has: the Phase 15 register-time ordered-guard composition (age gate first), the v2.1 hashed single-use token discipline (SecureRandom generation, SHA-256-at-rest, atomic compare-and-set consumption), the `@Value`-backed `app.*` config-key convention, the RFC 7807 exception-mapping pattern, and the three-place-whitelist / `permitAll` public-endpoint pattern. **No new external dependencies are required** — code generation and hashing use JDK primitives (`java.security.SecureRandom`, `java.security.MessageDigest`, `java.util.Base64`) already in use in `EmailVerificationService`/`PasswordResetService`.

The work splits into five seams: (1) a global `app.invite.enabled` boolean gate in `AuthService.register` (INV-01); (2) a new `com.catspell.api.invite` domain package (controller/service/model) mirroring the `moderation/` layout, with `InviteService.create/validate/consume` (INV-02/03/05); (3) a Flyway **V23** migration creating `invites` (hashed code, single-use state, nullable `referrer_user_id`) + `referrals` (referrer→invitee→invite→timestamp); (4) a new invite exception → generic 403 mapping in `GlobalExceptionHandler` that makes invalid/consumed/missing codes indistinguishable (INV-04); and (5) a shared-secret admin issuance endpoint whitelisted in `SecurityConfig` and guarded by a constant-time `X-Admin-Token` comparison (INV-02).

The three highest-risk areas are all covered by locked decisions and existing patterns: **enumeration safety** (one exception, one 403 body for all failure modes — Pitfall 1), **single-use atomicity** (the conditional-UPDATE claim, not read-check-write — Pitfall 2), and **no orphan account on consume failure** (wrap validate→save→consume in one `@Transactional` boundary so a lost concurrent claim rolls back the user insert — Pitfall 3). One security refinement to add beyond the in-repo precedent: the admin shared-secret must use a **constant-time comparison** and **deny-by-default when unconfigured**, since a `permitAll` route means the header check is the only barrier.

**Primary recommendation:** Build a new `invite/` package reusing the exact `SecureRandom`+Base64 generator and SHA-256 `hashToken` helper from `EmailVerificationService`, and the atomic `markUsed`-style conditional UPDATE from `PasswordResetTokenRepository`; compose `InviteService.validate`/`consume` into a `@Transactional` `register` after the age gate; issue via a `permitAll` `POST /api/admin/invites` guarded by a constant-time `X-Admin-Token` check; map all invite failures to one generic 403.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Global invite gate flag (`app.invite.enabled`) | API / Backend (config) | — | Server-side gate must be authoritative; a client flag is trivially bypassed. Read via `@Value` in the service. |
| Invite code generation (high-entropy, URL-safe) | API / Backend (service) | — | Cryptographic randomness must be server-side (`SecureRandom`); raw code returned once, never stored raw. |
| Invite code storage (hashed) + single-use state | Database / Storage | API / Backend | Hashed value + `consumed_at` claim live in Postgres; atomic claim enforced by a DB conditional UPDATE under a row lock. |
| Register-time validate + consume | API / Backend (service) | Database / Storage | Composed into `AuthService.register`; atomicity guaranteed by a single `@Transactional` boundary + conditional UPDATE. |
| Operator code issuance | API / Backend (controller+service) | Database / Storage | Shared-secret-guarded HTTP endpoint; no admin identity tier exists, so auth is a header check in the controller. |
| Admin shared-secret authentication | API / Backend (controller) | — | `permitAll` route means the constant-time header comparison is the access-control boundary (ASVS V4). |
| Referral attribution | Database / Storage | API / Backend | `referrals` row written at consumption; foreign keys to `users` + `invites` enforce integrity. |
| Enumeration-safe error contract | API / Backend (exception handler) | — | One exception → one 403 RFC 7807 body for invalid/consumed/missing (ASVS V7 / info-leak prevention). |

## Project Constraints (from AGENTS.md / .windsurf/rules)

Treat these with the same authority as locked decisions:

- **Containers: Podman, not Docker.** Use `podman compose ...` for local services (`docker-compose.yml`: Postgres 16 + PostGIS 3.4 on 5432, MinIO on 9002/9001). `[VERIFIED: AGENTS.md]`
- **Stack (actual, verified in `build.gradle.kts`):** Kotlin **2.4.0**, Spring Boot **4.0.6**, JVM/toolchain **17**, PostgreSQL 16 + PostGIS 3.4, Flyway (`flyway-core` + `flyway-database-postgresql` + `spring-boot-flyway`). `[VERIFIED: build.gradle.kts]` (Note: `.windsurf/rules` STACK section still lists Kotlin 2.0.x / Spring Boot 3.3.x — that block is **stale**; AGENTS.md and `build.gradle.kts` are authoritative.)
- **Custom config keys are bound via `@Value` / `@ConditionalOnProperty` only — NO `@ConfigurationProperties`, no config-processor.** The Spring VSCode "Unknown property" warning on `app.invite.*` is cosmetic and expected. `[VERIFIED: AGENTS.md + application.yml + LocalAgeVerifier.kt]`
- **Flyway migrations are append-only.** Next version is **V23**; never edit V1–V22. `[VERIFIED: db/migration listing]`
- **Testing:** `./gradlew test`; integration tests spin up their own Postgres/MinIO via Testcontainers (`BaseIntegrationTest`). `[VERIFIED: AGENTS.md + BaseIntegrationTest.kt]`
- **GSD workflow enforcement:** repo edits should flow through a GSD command; this is a research-only artifact. `[CITED: .windsurf/rules]`
- No project `skills/` directories with a `SKILL.md` exist (`.windsurf/skills/` and `.agents/skills/` contain GSD command skills, not project code skills). `[VERIFIED: filesystem]`

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java.security.SecureRandom` (JDK) | JVM 17 | CSPRNG for high-entropy invite codes | Already the in-repo generator for all tokens (`EmailVerificationService`, `PasswordResetService`, `EmailChangeService`). No dependency. `[VERIFIED: in-repo]` |
| `java.util.Base64.getUrlEncoder().withoutPadding()` (JDK) | JVM 17 | URL-safe, non-sequential code encoding | Exact in-repo pattern; 32 random bytes → 43-char URL-safe string (~256 bits entropy). `[VERIFIED: in-repo]` |
| `java.security.MessageDigest` SHA-256 (JDK) | JVM 17 | Hash codes at rest (D-06) | Exact in-repo `hashToken` helper; hex-encoded via `HexFormat`. `[VERIFIED: in-repo]` |
| Spring Data JPA / Hibernate | Spring Boot 4.0.6 | `invites`/`referrals` entities + repositories, atomic `@Modifying @Query` claim | The single-use claim pattern (`markUsed`) is already proven in `PasswordResetTokenRepository`. `[VERIFIED: in-repo]` |
| Spring Security | Spring Boot 4.0.6 | `permitAll` whitelist for the admin route | Existing three-place/`permitAll` pattern in `SecurityConfig`. `[VERIFIED: in-repo]` |
| Bean Validation (Jakarta) | Spring Boot 4.0.6 | `RegisterRequest.inviteCode` / admin DTO validation | Existing `@field:*` annotations on `AuthDtos`. `[VERIFIED: in-repo]` |
| Flyway | (managed by Spring Boot 4.0.6) | V23 DDL migration | Append-only migration convention. `[VERIFIED: in-repo]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `MessageDigest.isEqual(...)` (JDK) | JVM 17 | Constant-time comparison of the admin shared secret | Compare the `X-Admin-Token` header against `app.invite.admin-token` without a timing side channel (ASVS V2/V6). `[VERIFIED: JDK]` |
| JUnit 5 (Jupiter) + Spring Boot Test + MockMvc | Spring Boot 4.0.6 | Integration tests | Existing test style (`@SpringBootTest @AutoConfigureMockMvc`, `BaseIntegrationTest`). `[VERIFIED: in-repo]` |
| Testcontainers (postgresql, junit-jupiter) | 1.20.6 | Real Postgres for migration + repository tests | `BaseIntegrationTest` companion container `postgis/postgis:16-3.4-alpine`. `[VERIFIED: build.gradle.kts]` |
| MockK | 1.13.11 | Unit-level mocking if needed | Kotlin-native mocking already in use. `[VERIFIED: build.gradle.kts]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `SecureRandom` + Base64 URL | A human-readable code (e.g. `XXXX-XXXX` Crockford Base32) | Nicer to type but lower entropy per char and needs custom alphabet handling; codes are machine-passed here (returned once, pasted), so URL-safe Base64 matches the existing token pattern with zero new code. Discretion (D-07) — recommend Base64 URL for consistency. |
| Atomic conditional UPDATE claim | `SELECT ... FOR UPDATE` then update | Both are correct; the conditional UPDATE (`WHERE consumed_at IS NULL`) is the established in-repo idiom (`markUsed`) and needs no explicit lock management. |
| `permitAll` + header secret | Dedicated Spring Security filter + `ADMIN` authority | Rejected by D-03 — introduces a role concept the app lacks. |
| `@ConfigurationProperties` for `app.invite.*` | `@Value` per key | Forbidden by AGENTS.md; use `@Value`. |

**Installation:** None. No new Gradle dependencies. All primitives are JDK or already-present Spring Boot starters.

**Version verification:** Versions confirmed against `build.gradle.kts` (Kotlin 2.4.0, Spring Boot 4.0.6, JVM 17, Testcontainers 1.20.6, MockK 1.13.11). `[VERIFIED: build.gradle.kts]`

## Package Legitimacy Audit

> **Not applicable.** This phase installs **no external packages.** All functionality is built from JDK primitives (`java.security.SecureRandom`, `java.security.MessageDigest`, `java.util.Base64`, `java.util.HexFormat`) and libraries already present in `build.gradle.kts` (Spring Boot 4.0.6 starters, Flyway, Testcontainers, MockK). No registry lookup or slopsquatting check is required.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                    ADMIN / OPERATOR PATH (INV-02)                REGISTER PATH (INV-01/03/04/05)
                    ─────────────────────────────                ───────────────────────────────

  POST /api/admin/invites                                     POST /api/auth/register
  Header: X-Admin-Token          RegisterRequest { email, password, dateOfBirth, inviteCode? }
        │                                                             │
        ▼                                                             ▼
  InviteController ──(constant-time compare)──►  reject           AuthController ──► AuthService.register  @Transactional
        │  X-Admin-Token vs app.invite.admin-token  │ (bad → generic 401/403)      │
        │  (deny-by-default if unconfigured)        │                              ▼
        ▼                                                          1. ageVerifier.requireAdult(dob)      (422 under-18)
  InviteService.create(referrerUserId?)                                            │
        │  ├─ D-12: if referrerUserId != null → assert user exists (else 400)      ▼
        │  ├─ generate raw code (SecureRandom + Base64url)          app.invite.enabled? ──false──► skip invite path
        │  ├─ hash (SHA-256) → store Invite(codeHash, referrerUserId, consumed=false)  │             (inviteCode ignored, D-09)
        │  └─ return RAW code ONCE (D-06)                                          true│
        ▼                                                                             ▼
  201 { code: "<raw, shown once>" }                              2. InviteService.validate(inviteCode)
                                                                      hash(code) → lookup by codeHash
                                                                      missing / not found / consumed
                                                                          └──► ONE generic InviteRequiredException → 403
                                                                                  (invalid == consumed == missing, D-10)
                                                                             │ valid + unconsumed
                                                                             ▼
                                                                 3. existsByEmail? → DuplicateEmailException (409)
                                                                 4. userRepository.save(newUser)
                                                                 5. InviteService.consume(invite, newUser):
                                                                      atomic UPDATE ... WHERE consumed_at IS NULL
                                                                          └─ 0 rows (raced) ──► generic 403 (tx rolls back → no orphan)
                                                                      if invite.referrerUserId != null:
                                                                          insert referrals(referrer, invitee, invite, now)  (INV-05, D-11)
                                                                 6. emailVerificationService.issueAndSend(newUser)
                                                                             │
                                                                             ▼
                                                                        201 Created
                              ┌────────────────────────────────────────────────────┐
                              │  PostgreSQL (V23):  invites   referrals              │
                              └────────────────────────────────────────────────────┘
```

### Recommended Project Structure

Mirror the `moderation/` domain package layout (`[VERIFIED: in-repo]`):

```
src/main/kotlin/com/catspell/api/invite/
├── controller/
│   └── InviteAdminController.kt      # POST /api/admin/invites (shared-secret guarded)
├── service/
│   └── InviteService.kt             # create() / validate() / consume()
└── model/
    ├── Invite.kt                    # @Entity → invites
    ├── InviteRepository.kt          # findByCodeHash + @Modifying markConsumed
    ├── Referral.kt                  # @Entity → referrals
    ├── ReferralRepository.kt
    ├── IssueInviteRequest.kt        # { referrerUserId: UUID? }
    └── IssueInviteResponse.kt       # { code: String }  (raw, once)

src/main/resources/db/migration/
└── V23__create_invites_and_referrals.sql

MODIFIED:
├── auth/model/AuthDtos.kt                       # add inviteCode?: String to RegisterRequest
├── auth/service/AuthService.kt                  # inject InviteService + @Value enabled; @Transactional register
├── common/config/SecurityConfig.kt             # permitAll /api/admin/invites
├── common/exception/Exceptions.kt              # InviteRequiredException (or similar)
├── common/exception/GlobalExceptionHandler.kt  # → 403 + generic code
└── resources/application.yml                    # app.invite.enabled + app.invite.admin-token
```

### Pattern 1: Global config gate composed into ordered register guards (INV-01/INV-03)

**What:** Read `app.invite.enabled` via `@Value`; when true, run `validate` after the age gate and `consume` after `save`, all in one transaction. When false, the invite path never runs (D-09).
**When to use:** All account creation.
**Example:**
```kotlin
// AuthService — mirrors LocalAgeVerifier's @Value config style and the existing register order.
@Service
class AuthService(
    private val ageVerifier: AgeVerifier,
    private val inviteService: InviteService,
    // ...existing deps...
    @Value("\${app.invite.enabled:false}") private val inviteEnabled: Boolean,
) {
    @Transactional  // NEW: makes validate → save → consume atomic (no orphan account, Pitfall 3)
    fun register(request: RegisterRequest) {
        ageVerifier.requireAdult(request.dateOfBirth)              // 1. age gate first (Phase 15)

        val invite = if (inviteEnabled)                            // 2. invite gate (skipped when public, D-09)
            inviteService.validate(request.inviteCode)             //    throws generic 403 on missing/invalid/consumed
        else null

        if (userRepository.existsByEmail(request.email)) throw DuplicateEmailException()

        val savedUser = userRepository.save(User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)!!,
            dateOfBirth = request.dateOfBirth,
        ))

        invite?.let { inviteService.consume(it, savedUser) }       // 3. atomic claim + referral (INV-05)

        emailVerificationService.issueAndSend(savedUser)
    }
}
```
> Note: `register` is currently **not** `@Transactional`. Adding it is required for consume/save atomicity. The `emailVerificationService.issueAndSend` send is a no-op by default (`email.enabled=false`), so it does not create a network dependency inside the transaction; it persists a verification token in the same tx (fine) and calls the no-op sender.

### Pattern 2: Referral attribution written only when a referrer exists (INV-05, D-11)

**What:** `consume` performs the atomic single-use claim, then writes a `referrals` row **only if** the invite carries a non-null `referrer_user_id`. Bootstrap codes (null referrer) produce no referral row.
**When to use:** At invite consumption during register.
**Example:**
```kotlin
@Transactional  // participates in register's transaction
fun consume(invite: Invite, invitee: User) {
    // Atomic single-use claim: exactly one concurrent caller matches consumed_at IS NULL.
    if (inviteRepository.markConsumed(invite.id!!, invitee.id!!, Instant.now()) == 0) {
        throw InviteRequiredException()   // raced/already consumed → SAME generic 403 (D-10); tx rolls back
    }
    val referrerId = invite.referrerUserId ?: return          // bootstrap code → no referral row (D-11)
    if (referrerId == invitee.id) return                      // defensive self-referral guard (D-12)
    referralRepository.save(Referral(
        referrerId = referrerId, inviteeId = invitee.id!!, inviteId = invite.id!!,
    ))
}
```

### Pattern 3: Shared-secret admin endpoint via `permitAll` + constant-time header check (INV-02, D-03)

**What:** Whitelist the route `permitAll` (no auth filter passes), then compare `X-Admin-Token` to `app.invite.admin-token` in constant time. **Deny by default** if the configured token is blank/unset.
**When to use:** The operator issuance endpoint.
**Example:**
```kotlin
// SecurityConfig — add alongside the existing permitAll list
it.requestMatchers("/api/admin/invites").permitAll()

// InviteAdminController
@RestController
@RequestMapping("/api/admin/invites")
class InviteAdminController(
    private val inviteService: InviteService,
    @Value("\${app.invite.admin-token:}") private val adminToken: String,
) {
    @PostMapping
    fun issue(
        @RequestHeader(value = "X-Admin-Token", required = false) token: String?,
        @Valid @RequestBody body: IssueInviteRequest,
    ): ResponseEntity<IssueInviteResponse> {
        requireValidAdminToken(token)                       // generic 401/403 on missing/wrong
        val rawCode = inviteService.create(body.referrerUserId)
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueInviteResponse(rawCode))
    }

    private fun requireValidAdminToken(provided: String?) {
        // Deny-by-default: an unset/blank server token means the endpoint is closed, not open.
        if (adminToken.isBlank() || provided == null ||
            !MessageDigest.isEqual(provided.toByteArray(), adminToken.toByteArray())) {
            throw AdminAuthException()                       // → generic 401 or 403 (D-03), no detail leak
        }
    }
}
```

### Pattern 4: Reuse the exact in-repo code-generation + hashing helpers (INV-04, D-06/D-07)

**What:** Copy the generator/hasher from `EmailVerificationService` verbatim into `InviteService`.
**Example:**
```kotlin
private val secureRandom = SecureRandom()

private fun generateRawCode(): String {
    val bytes = ByteArray(32); secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)   // ~256-bit, URL-safe, non-sequential
}
private fun hashCode(raw: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)))
```

### Anti-Patterns to Avoid
- **Distinct errors for invalid vs consumed vs missing codes** — leaks which codes exist/were used (Pitfall 1). One exception, one body.
- **Read-check-write single-use** (`if (invite.consumedAt == null) { save }`) — races let two accounts share one code (Pitfall 2). Use the conditional UPDATE.
- **Validating/consuming outside a transaction with `save`** — a lost concurrent claim after `save` orphans an account (Pitfall 3).
- **Storing raw invite codes** — a table leak = free accounts (research "Storing raw invite codes: Never"). Hash at rest.
- **`==`/`String.equals` for the admin secret** — timing side channel; use `MessageDigest.isEqual`.
- **A dev default for `app.invite.admin-token`** that ships to prod on a `permitAll` route — deny-by-default when blank instead.
- **Enforcing `inviteCode` as `@field:NotBlank` on the DTO** — it must be optional at the DTO layer (required only when gated, enforced in the service), else public mode (D-09) breaks.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| High-entropy code generation | Custom random string / `UUID.toString()` shortening | `SecureRandom` + `Base64.getUrlEncoder().withoutPadding()` (in-repo `generateRawToken`) | CSPRNG + proven encoding; non-sequential, URL-safe, ~256-bit. `[VERIFIED: EmailVerificationService.kt]` |
| Hash at rest | Custom hashing / plaintext compare | `MessageDigest` SHA-256 + `HexFormat` (in-repo `hashToken`) | Identical to the token discipline; deterministic lookup by hash. `[VERIFIED: AuthService.kt]` |
| Single-use enforcement | `SELECT` then `if (unused) UPDATE` | `@Modifying @Query("UPDATE ... WHERE id=:id AND consumed_at IS NULL")` returning `Int` | Closes the read-check-write race under a DB row lock (0 rows = already claimed). `[VERIFIED: PasswordResetTokenRepository.kt]` |
| Constant-time secret compare | `provided == adminToken` | `MessageDigest.isEqual(...)` | Avoids timing side channel on the only barrier protecting a `permitAll` route. `[VERIFIED: JDK]` |
| RFC 7807 error bodies | Custom JSON error shape | `ProblemDetail.forStatusAndDetail(...)` + `setProperty("code", ...)` | Matches every existing handler (`EmailNotVerifiedException`, `UnderMinimumAgeException`). `[VERIFIED: GlobalExceptionHandler.kt]` |
| Config gate | Hard-coded flag / new config framework | `@Value("\${app.invite.enabled:false}")` | Matches `push.enabled`, `email.enabled`, `app.age.minimum-age`. AGENTS.md forbids `@ConfigurationProperties`. `[VERIFIED: application.yml + LocalAgeVerifier.kt]` |
| Public route exposure | New security filter | Add to the `permitAll` list in `SecurityConfig` | Established pattern for `/api/auth/*`. `[VERIFIED: SecurityConfig.kt]` |

**Key insight:** Every mechanic this phase needs already exists in the codebase for email/password tokens. The correct implementation is *transcription of proven patterns into a new domain*, not novel design. Deviating (e.g. a bespoke code alphabet, a new error shape, a read-check-write claim) is where the documented pitfalls appear.

## Common Pitfalls

### Pitfall 1: Invite gate not enumeration-safe (invalid vs consumed distinguishable)
**What goes wrong:** Invalid, already-consumed, and missing-when-gated codes return different statuses/bodies/`code` strings, letting an attacker enumerate which codes exist or were used.
**Why it happens:** Returning granular errors for debugging; a separate "already used" exception.
**How to avoid:** A **single** `InviteRequiredException` thrown for all three cases (D-10), mapped to one `403` RFC 7807 body with one generic `code`. `validate()` throws it on null/blank code, on hash-not-found, and on already-consumed; `consume()` throws the same on a lost atomic claim.
**Warning signs:** Two exception classes for invite failures; different `code` values; a `404` for "unknown code" vs `403` for "used".

### Pitfall 2: Single-use enforced with a read-check-write race
**What goes wrong:** Two concurrent registrations both read the invite as unconsumed and both succeed → one code onboards multiple accounts.
**Why it happens:** `if (invite.consumedAt == null) { invite.consumedAt = now; save }`.
**How to avoid:** Atomic conditional UPDATE (`markConsumed(...) WHERE consumed_at IS NULL`) returning row count; 0 rows → reject with the generic 403. Exactly mirrors `PasswordResetTokenRepository.markUsed`.
**Warning signs:** A repository `save` on the invite instead of a `@Modifying @Query`; a test that fires two concurrent registers with one code and both return 201.

### Pitfall 3: Orphan account when consume fails after user save
**What goes wrong:** `save(user)` commits, then `consume` fails (raced claim) — an account exists but the invite wasn't consumed, or vice versa.
**How to avoid:** Wrap `validate → existsByEmail → save → consume → issueAndSend` in one `@Transactional` on `register`; a thrown generic 403 from `consume` rolls back the user insert. `register` is currently non-transactional — **this annotation must be added.**
**Warning signs:** No `@Transactional` on `register`; a test where a raced consume leaves a `users` row behind.

### Pitfall 4: `permitAll` admin route left open (unconfigured or timing-leaky secret)
**What goes wrong:** A blank `app.invite.admin-token` with a `permitAll` route = anyone can mint invites; or `==` comparison leaks the secret via timing.
**How to avoid:** Deny-by-default when the configured token is blank; compare with `MessageDigest.isEqual`. Do not ship a guessable dev default to prod.
**Warning signs:** `@Value("\${app.invite.admin-token:changeme}")`; `provided == adminToken`.

### Pitfall 5: `inviteCode` made mandatory at the DTO layer breaks public mode
**What goes wrong:** `@field:NotBlank inviteCode` rejects every registration when the gate is OFF (D-09).
**How to avoid:** `inviteCode: String? = null` on `RegisterRequest`, no `@NotBlank`; requiredness enforced only inside `validate()` when `app.invite.enabled=true`.
**Warning signs:** Public-mode registration returns 400 for a missing code.

### Pitfall 6: Referral row written for bootstrap (null-referrer) codes
**What goes wrong:** A `referrals` row with a null/meaningless referrer for operator bootstrap codes.
**How to avoid:** `consume` writes a referral **only when** `invite.referrerUserId != null` (D-11). Enforce with a `NOT NULL` `referrer_id` on `referrals` + the service guard.
**Warning signs:** `referrals` rows equal in count to consumed invites even for operator codes.

### Pitfall 7: Dangling referrer attribution at issuance
**What goes wrong:** An invite issued with a `referrer_user_id` that doesn't exist → later a `referrals` FK insert fails, or attribution points nowhere.
**How to avoid:** Validate referrer existence at `create` (D-12) → `400` if unknown; FK `referrer_id REFERENCES users(id)` as a backstop.
**Warning signs:** No existence check in `create`; referral FK violations at consume time.

## Code Examples

### V23 migration (models `invites` + `referrals` on V19/V21 conventions)
```sql
-- Source: mirrors V21__create_reports_table.sql (gen_random_uuid, TIMESTAMPTZ, CHECK/FK, indexes)
-- src/main/resources/db/migration/V23__create_invites_and_referrals.sql

CREATE TABLE invites (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_hash        VARCHAR(64) NOT NULL UNIQUE,               -- SHA-256 hex (64 chars); raw code never stored
    referrer_user_id UUID REFERENCES users(id) ON DELETE SET NULL,  -- nullable (bootstrap codes), D-11
    consumed_at      TIMESTAMPTZ,                               -- NULL = unconsumed (single-use claim target)
    consumed_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_invites_referrer ON invites(referrer_user_id);
-- code_hash UNIQUE already indexes the register-time lookup path.

CREATE TABLE referrals (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invite_id     UUID NOT NULL REFERENCES invites(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_referrals_no_self CHECK (referrer_id <> invitee_id),  -- defensive (D-12)
    CONSTRAINT uq_referrals_invitee UNIQUE (invitee_id)                  -- one attribution per new account
);
CREATE INDEX idx_referrals_referrer ON referrals(referrer_id);
```

### Atomic single-use claim repository (mirrors `PasswordResetTokenRepository.markUsed`)
```kotlin
// Source: PasswordResetTokenRepository.kt (in-repo, VERIFIED)
interface InviteRepository : JpaRepository<Invite, UUID> {
    fun findByCodeHash(codeHash: String): Invite?

    @Modifying
    @Query("UPDATE Invite i SET i.consumedAt = :now, i.consumedBy = :inviteeId " +
           "WHERE i.id = :id AND i.consumedAt IS NULL")
    fun markConsumed(@Param("id") id: UUID, @Param("inviteeId") inviteeId: UUID,
                     @Param("now") now: Instant): Int   // 1 = claimed, 0 = already consumed
}
```

### Generic invite exception → 403 mapping (mirrors `EmailNotVerifiedException`)
```kotlin
// Exceptions.kt
class InviteRequiredException(message: String = "A valid invite is required to sign up")
    : RuntimeException(message)
class AdminAuthException(message: String = "Not authorized") : RuntimeException(message)

// GlobalExceptionHandler.kt — one body for invalid/consumed/missing (D-10)
@ExceptionHandler(InviteRequiredException::class)
fun handleInviteRequired(ex: InviteRequiredException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
        ex.message ?: "A valid invite is required to sign up")
    problem.title = "Forbidden"
    problem.setProperty("code", "INVITE_REQUIRED")   // single generic code
    return problem
}

@ExceptionHandler(AdminAuthException::class)
fun handleAdminAuth(ex: AdminAuthException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Not authorized")
    problem.title = "Unauthorized"   // generic — no hint about token correctness (D-03)
    return problem
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| DOB / gate logic added ad hoc in `register` | Ordered register guards with a reserved invite slot after the age gate | Phase 15 (D-01/D-02) | The exact insertion point already exists — no reordering. |
| Raw tokens/secrets compared/stored plainly | Hashed-single-use discipline (SecureRandom + SHA-256 + atomic claim) | Phases 10–12 (v2.1) | Directly reused for invite codes. |
| — | `permitAll` + in-controller shared-secret for operator-only surfaces | This phase (D-03) | First "admin" surface; deliberately avoids RBAC. |

**Deprecated/outdated:**
- `.windsurf/rules` STACK block (Kotlin 2.0.x / Spring Boot 3.3.x, Testcontainers 1.19.8) — **stale**; use `build.gradle.kts` (Kotlin 2.4.0 / Spring Boot 4.0.6 / Testcontainers 1.20.6).
- ROADMAP "V19+" note for this phase's migration — the actual next version is **V23** (V19–V22 already shipped).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 32 random bytes → URL-safe Base64 (~256-bit) is sufficient entropy for invite codes | Standard Stack / Pattern 4 | Very low — this exceeds guessing resistance and matches the in-repo token size; discretion (D-07) allows any high-entropy URL-safe format. |
| A2 | A `403` (not `401`) is the right status for the invite gate | Pattern 1 / Code Examples | Low — CONTEXT D-10 locks `403`; noted here as the chosen mapping. |
| A3 | Adding `@Transactional` to `register` is acceptable (email send is no-op by default) | Pattern 1 / Pitfall 3 | Low — `email.enabled=false` by default; if a real sender is later enabled, consider moving the send to AFTER_COMMIT (as the report notifier already does). Flagged for the planner. |
| A4 | `AdminAuthException` should map to `401` (vs `403`) | Code Examples / D-03 | Low — D-03 explicitly allows either; `401` (no/!valid credential) is the more conventional choice for a bad/missing token. Discretion. |
| A5 | `referrals` should have `UNIQUE(invitee_id)` (one attribution per new account) | Code Examples | Low — a new account consumes exactly one invite; the unique constraint is defensive. Column/index shape is Claude's discretion (D-12). |

**All other claims are `[VERIFIED: in-repo]` or `[CITED: CONTEXT.md / research/*]`.**

## Open Questions (RESOLVED)

1. **`app.invite.admin-token` default vs. fail-fast (D-03 discretion).**
   - What we know: Existing secrets like `jwt.secret` ship with a dev default; but this route is `permitAll`, so a default = an open mint endpoint.
   - What's unclear: Whether the operator wants a no-default (app fails/denies without the env var) or a clearly-dev-only default.
   - Recommendation: **Deny-by-default** — `@Value("\${app.invite.admin-token:}")` (empty), and reject all admin requests when blank. No usable default reaches prod. (Aligns with security intent; low friction — set the env var to enable issuance.)
   - **— RESOLVED: deny-by-default empty token.** The plans implement `@Value("\${app.invite.admin-token:}")` (empty default) in both application.yml files (16-01) and `InviteAdminController` denies all requests when the configured token is blank (16-03).

2. **Status for a bad/missing admin token: `401` vs `403` (D-03 discretion).**
   - Recommendation: `401 Unauthorized` (missing/invalid credential). Either satisfies D-03; keep the body generic.
   - **— RESOLVED: 401.** `AdminAuthException` maps to HTTP 401 (title 'Unauthorized', no code hint) in `GlobalExceptionHandler` (16-02), returned by the admin controller for a missing/wrong token (16-03).

3. **Human-friendly code vs. Base64url (D-07 discretion).**
   - Recommendation: Base64url for zero new code and consistency with existing tokens. Revisit only if Phase 17 needs codes typed by hand from an email (that phase emails a link, so likely unnecessary).
   - **— RESOLVED: Base64url.** `InviteService.create` reuses the in-repo SecureRandom 32-byte + `Base64.getUrlEncoder().withoutPadding()` generator (16-02).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK (SecureRandom, MessageDigest, Base64, HexFormat) | Code gen + hashing | ✓ | JVM 17 | — |
| PostgreSQL + PostGIS | V23 migration + repositories | ✓ (Podman compose + Testcontainers) | 16 / 3.4 | — |
| Flyway | V23 migration | ✓ | managed by Spring Boot 4.0.6 | — |
| Testcontainers | Integration tests | ✓ | 1.20.6 | — |
| Podman | Local Postgres/MinIO | ✓ (per AGENTS.md) | — | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

> No new external dependency is introduced. This is code + config + one migration.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test + MockMvc; MockK 1.13.11 for unit mocks |
| Config file | `build.gradle.kts` (`useJUnitPlatform()`, `TESTCONTAINERS_RYUK_DISABLED=true`); `src/test/resources/application.yml` |
| Base class | `com.catspell.api.BaseIntegrationTest` (Testcontainers `postgis/postgis:16-3.4-alpine` + MinIO; per-test TRUNCATE; `markEmailVerified` helper) |
| Quick run command | `./gradlew test --tests "com.catspell.api.invite.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INV-01 | Gate OFF (`app.invite.enabled=false`): register succeeds with missing/garbage `inviteCode`; no invite consumed, no referral row | integration (`@TestPropertySource`/`@DynamicPropertySource` overriding `app.invite.enabled`) | `./gradlew test --tests "*InvitePublicModeIntegrationTest"` | ❌ Wave 0 |
| INV-01/INV-03 | Gate ON: register with valid unconsumed code → 201; register with missing code → generic 403 | integration (MockMvc register) | `./gradlew test --tests "*InviteGateIntegrationTest"` | ❌ Wave 0 |
| INV-02 | `POST /api/admin/invites` with valid `X-Admin-Token` → 201 + raw code once; wrong/missing token → generic 401/403; valid `referrerUserId` accepted; unknown `referrerUserId` → 400 | integration (MockMvc admin) | `./gradlew test --tests "*InviteAdminEndpointIntegrationTest"` | ❌ Wave 0 |
| INV-04 | Code stored hashed (raw not in DB); reuse of a consumed code → identical generic 403 as an invalid code; concurrent double-register on one code → exactly one 201 (atomic claim) | integration (DB assert via `jdbcTemplate` + concurrency) | `./gradlew test --tests "*InviteSingleUseIntegrationTest"` | ❌ Wave 0 |
| INV-04 | Invalid vs consumed vs missing all return identical status + body + `code` | integration (MockMvc, compare responses) | `./gradlew test --tests "*InviteEnumerationSafetyIntegrationTest"` | ❌ Wave 0 |
| INV-05 | Consumption with a referrer writes `referrals(referrer,invitee,invite)`; bootstrap (null-referrer) consumption writes NO referral row | integration (DB assert) | `./gradlew test --tests "*ReferralAttributionIntegrationTest"` | ❌ Wave 0 |
| INV-04 (schema) | V23 creates `invites` + `referrals` with expected columns/constraints/indexes | integration (Testcontainers + Flyway on real Postgres; `jdbcTemplate` introspection) | `./gradlew test --tests "*InviteMigrationTest"` | ❌ Wave 0 |

> Note: the main `src/test/resources/application.yml` has `spring.flyway.enabled: false` and `ddl-auto: create-drop`. A **migration test** that must exercise V23 needs Flyway enabled for that test (e.g. a dedicated `@TestPropertySource(properties = ["spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate"])`), matching how `DobMigrationTest`/`GrandfatherMigrationTest` validate schema against the real container. Confirm the exact toggle when planning.

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.catspell.api.invite.*"` (+ the affected `auth` register tests)
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt` — INV-01/INV-03 gate ON
- [ ] `src/test/kotlin/com/catspell/api/invite/InvitePublicModeIntegrationTest.kt` — INV-01 gate OFF (needs `app.invite.enabled=false` override)
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt` — INV-02 issuance + shared-secret + referrer validation (needs `app.invite.admin-token` test value)
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteSingleUseIntegrationTest.kt` — INV-04 hashed-at-rest + atomic reuse rejection
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteEnumerationSafetyIntegrationTest.kt` — INV-04 invalid==consumed==missing
- [ ] `src/test/kotlin/com/catspell/api/invite/ReferralAttributionIntegrationTest.kt` — INV-05 referral row iff referrer present
- [ ] `src/test/kotlin/com/catspell/api/invite/InviteMigrationTest.kt` — V23 schema (Flyway-enabled variant)
- [ ] Test config: a way to set `app.invite.enabled` (both states) and `app.invite.admin-token` per test — via `@DynamicPropertySource`/`@TestPropertySource`; add `app.invite.*` defaults to `src/test/resources/application.yml` if a global default is desired.
- Framework install: **none** — JUnit 5 + Testcontainers + MockMvc already configured.

## Security Domain

`security_enforcement: true`, `security_asvs_level: 1` (from `config.json`).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Admin shared-secret is an auth factor: constant-time compare (`MessageDigest.isEqual`), deny-by-default when unconfigured, no default secret to prod. |
| V3 Session Management | no | Invite flow mints no session; register still issues no session (unchanged, VERIFY contract). |
| V4 Access Control | yes | `permitAll` admin route → the header check IS the access boundary; object integrity via FKs; issuance restricted to the operator secret. |
| V5 Input Validation | yes | `inviteCode` optional String (bounded length), `referrerUserId` a valid UUID, `X-Admin-Token` header; Bean Validation + service checks. |
| V6 Cryptography | yes | `SecureRandom` (CSPRNG) for codes; SHA-256 for hash-at-rest; constant-time comparison. Never hand-roll. |
| V7 Errors & Logging | yes | Generic 403 for all invite failures (no enumeration); generic 401/403 for bad admin token; never log the raw code or the admin token. |

### Known Threat Patterns for Kotlin/Spring Boot invite gate
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Invite code enumeration via differential errors | Information Disclosure | Single exception → one 403 body/`code` for invalid/consumed/missing (D-10). |
| Code guessing (low entropy / sequential) | Spoofing | `SecureRandom` 32 bytes, URL-safe Base64, non-sequential. |
| Code reuse (multi-account from one code) | Tampering / Elevation | Atomic conditional-UPDATE claim; 0 rows → reject. |
| Table leak → account access | Information Disclosure | SHA-256 hash at rest; raw code returned once, never stored/logged. |
| Open mint endpoint (unconfigured secret on `permitAll`) | Elevation of Privilege | Deny-by-default when `app.invite.admin-token` blank. |
| Timing attack on the admin secret | Information Disclosure | `MessageDigest.isEqual` constant-time compare. |
| Dangling / forged referral attribution | Tampering | Validate referrer existence at issuance (D-12); FK + self-referral CHECK. |
| Orphan account on raced consume | Tampering (integrity) | Single `@Transactional` around validate→save→consume. |
| SQL injection | Tampering | JPA / parameterized queries only (no string-built SQL). |

## Sources

### Primary (HIGH confidence) — in-repo, VERIFIED this session
- `auth/service/AuthService.kt` — `register` order + `hashToken` SHA-256 helper + atomic `markUsed` usage
- `auth/service/EmailVerificationService.kt` — `SecureRandom` 32-byte + `Base64.getUrlEncoder().withoutPadding()` generator; `hashToken`
- `auth/model/PasswordResetTokenRepository.kt` — atomic conditional-UPDATE single-use claim (`markUsed`)
- `auth/model/AuthDtos.kt`, `auth/controller/AuthController.kt` — register DTO/contract
- `common/config/SecurityConfig.kt` — `permitAll` whitelist pattern
- `common/exception/Exceptions.kt` + `GlobalExceptionHandler.kt` — RFC 7807 `ProblemDetail` + `code` mapping (`EmailNotVerifiedException` 403, `UnderMinimumAgeException` 422)
- `moderation/` package — new-domain layout template; `@Value` config + AFTER_COMMIT notifier
- `age/AgeVerifier.kt` + `LocalAgeVerifier.kt` — seam + `@Value` config-key convention
- `db/migration/V21__create_reports_table.sql`, `V22__move_date_of_birth_to_users.sql` — migration conventions; next is V23
- `BaseIntegrationTest.kt`, `AgeGateIntegrationTest.kt`, `DobMigrationTest.kt`, `src/test/resources/application.yml`, `build.gradle.kts` — test infra + versions
- `application.yml` — `app.*` config keys; `AGENTS.md`, `.windsurf/rules` — project constraints

### Secondary (MEDIUM confidence) — milestone research (v2.2)
- `.planning/research/ARCHITECTURE.md` §Pattern 2 (signup gate composition), §Component Responsibilities (`InviteService`), §Recommended Project Structure (`invite/`), §Internal Boundaries (`auth ↔ invite`)
- `.planning/research/PITFALLS.md` §Pitfall 4 (enumeration-safe/guessable/reusable), §Security Mistakes (self-invite/enumeration), §"Looks Done But Isn't"
- `.planning/REQUIREMENTS.md` (INV-01..INV-05), `.planning/ROADMAP.md` §Phase 16, `.planning/phases/15-age-verification/15-CONTEXT.md`

### Tertiary (LOW confidence)
- None. No web/Context7 lookup was needed: this is an internal integration built on JDK primitives + verified in-repo patterns, fully constrained by locked CONTEXT decisions.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; all primitives verified in `build.gradle.kts` and in-repo usage.
- Architecture: HIGH — every seam maps to an existing, read-and-verified in-repo pattern and a locked decision.
- Pitfalls: HIGH — grounded in PITFALLS.md §4 + the concrete in-repo atomic-claim / hashing implementations.

**Research date:** 2026-09-29
**Valid until:** 2026-10-29 (stable — internal integration; no fast-moving external deps)
