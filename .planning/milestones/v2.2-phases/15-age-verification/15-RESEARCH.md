# Phase 15: Age Verification - Research

**Researched:** 2026-09-28
**Domain:** Server-side age gating at signup (Spring Boot / Kotlin / JPA / Flyway) + DOB data migration
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** The 18+ gate fires at `POST /api/auth/register` — a true hard gate. `AgeVerifier.requireAdult(dateOfBirth)` runs **before** the `users` row is created, so no under-18 account ever exists.
- **D-02:** Add a **`dateOfBirth`** field to `RegisterRequest` (`AuthDtos.kt`), required. Register composition order: age check first (cheap, always on), leaving room for Phase 16's invite check after.
- **D-03:** Introduce an **`AgeVerifier` seam** (interface + default local implementation doing DOB math against an 18-year minimum). Call sites depend only on the seam. Mirror `EmailSender` / `PushProvider`. Interface shape, package placement, method signature, and minimum-age constant-vs-`@Value` are **Claude's discretion**.
- **D-04:** DOB is stored on the **`users` table** and is the **single source of truth**. The existing `user_profiles.date_of_birth` column is **dropped** (V22).
- **D-05:** Update the discovery age filter (`SwipeRepository` JOINs), `DiscoveryService` (age computation), and `ProfileService` to read DOB from `users`. SQL/JPA rewrite is discretion; the age-filter *behavior* must not change.
- **D-06:** DOB is **immutable after signup**. Remove `dateOfBirth` from `CreateProfileRequest` and `UpdateProfileRequest` (and the profile-creation write path). Set once at register, never editable via profile edit.
- **D-07:** The old `ProfileService.validateAge` is **removed** — superseded by the register-time `AgeVerifier` gate. The `<18` guard lives in exactly one place.
- **D-08:** An under-18 register attempt returns **`422 Unprocessable Entity`** with an RFC 7807 body.
- **D-09:** The response carries a **distinct machine-readable `code`** via `problem.setProperty("code", ...)`, mirroring `EMAIL_NOT_VERIFIED`. Add a dedicated exception + `GlobalExceptionHandler` mapping. Exact code string and exception class name are discretion.
- **D-10:** V22 adds `users.date_of_birth` as **nullable**, then **backfills** from `user_profiles.date_of_birth` for every account with a completed profile, then drops the profile column.
- **D-11:** Accounts with **no DOB** are **grandfathered** — column stays nullable, they remain usable, no gate applied. Only new signups pass the age gate. No forced DOB-collection flow (deferred).
- **D-12:** Backfill DOBs **as-is with no special under-18 handling**. The migration does not audit/flag/suspend.

### Claude's Discretion
- `AgeVerifier` interface/method shape, package placement, throwing-vs-result contract, minimum-age constant vs. config key.
- Exact under-18 exception class name and `code` string, RFC 7807 title/detail copy.
- The `SwipeRepository` / `DiscoveryService` / `ProfileService` rewrite to source DOB from `users` (behavior-preserving), and the `users.date_of_birth` column type/index.
- Register-payload DOB validation beyond the age gate (future dates / absurd ages), clock/timezone basis for the 18th-birthday boundary.
- Whether `RegisterRequest.dateOfBirth` validation is Bean Validation vs. service-layer — but the *hard 18+ block* must be the `AgeVerifier`/422 path (D-08/D-09), not a generic 400.

### Deferred Ideas (OUT OF SCOPE)
- Third-party age-estimation / ID-verification vendor (Yoti, Veriff, Persona, Onfido, Apple Declared Age Range). The `AgeVerifier` seam is the drop-in point.
- Forcing grandfathered no-DOB accounts to supply a DOB later (backfill-collection flow).
- Auditing / flagging / suspending pre-existing under-18 accounts during migration (declined, D-12).
- Configurable minimum age per market / jurisdiction routing.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AGE-01 | A self-attested date of birth is collected at signup | Add `dateOfBirth: LocalDate` to `RegisterRequest`; persist on `User`; V22 moves DOB storage to `users` as the single source of truth (Standard Stack + Migration Pattern). |
| AGE-02 | Signup hard-blocked server-side for under-18; `AgeVerifier` seam keeps a vendor check swappable | `AgeVerifier` seam (interface + `@Component` default) mirroring `EmailSender`/`PushProvider`; `requireAdult` called before `userRepository.save`; under-18 → 422 + distinct `code` (Architecture Pattern 1 + 2). |
| AGE-03 | Existing accounts handled via migration (backfill / grandfather), no lockout | V22 add-nullable + backfill-from-profile + drop-profile-column, grandfather NULL-DOB rows (Migration Pattern mirrors V17). |
</phase_requirements>

## Summary

This is a **schema/data-migration + guard-insertion phase**, not a new-dependency phase. Everything needed already exists in the codebase: the `EmailSender`/`PushProvider` seam pattern (interface + `@Component` default impl selected by config), the `EmailNotVerifiedException` → RFC 7807 `ProblemDetail` + distinct-`code` mapping, the V17 grandfather-backfill migration pattern, and the discovery age filter that already computes ages from a `date_of_birth` column. The work is to (1) collect a self-attested DOB at register, (2) hard-block under-18 **before** the account row is written behind an `AgeVerifier` seam, (3) move DOB from `user_profiles` to `users` as the single source of truth via a Flyway **V22** migration that backfills and grandfathers, and (4) repoint every DOB reader (discovery SQL, `DiscoveryService`, `ProfileService`) at `users`.

The single highest-leverage decision — *where DOB is collected* (PITFALLS Pitfall 3) — is already locked by CONTEXT (D-01/D-04: collect at register, store on `users`). The research below is therefore prescriptive about **how** to implement each locked decision idiomatically, not **whether** to.

The one non-obvious risk is **test-surface breakage**: DOB is currently supplied through the profile-creation JSON in ~12 integration test files, and the discovery age-range tests rely on per-user DOBs seeded at profile time. After this phase, DOB must be supplied at **register** time and the profile column is gone — so register test helpers must thread DOB through, or those suites go red. This is the largest hidden cost and must be planned explicitly.

**Primary recommendation:** Add `AgeVerifier` as a domain seam (`com.catspell.api.age`), call `requireAdult(dateOfBirth)` at the very top of `AuthService.register` (before the duplicate-email check, per D-02/Pattern 2), map its under-18 exception to `422 UNDER_MINIMUM_AGE`, add a nullable `users.date_of_birth` in V22 with a profile backfill + column drop, and repoint the discovery filter with a `JOIN users` (behavior-preserving), updating existing register test helpers in the same phase.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Collect self-attested DOB (AGE-01) | API / Backend (`RegisterRequest` + `AuthController`) | — | DOB enters through the register endpoint payload; validation belongs at the API/service boundary. |
| Hard 18+ gate (AGE-02) | API / Backend (`AgeVerifier` seam invoked in `AuthService.register`) | — | Must be server-side and evaluated before persistence — never client tier (PITFALLS anti-pattern "age gate in mobile client only"). |
| DOB persistence + single source of truth (AGE-01/AGE-03) | Database / Storage (`users.date_of_birth`, V22 migration) | API (`User` entity) | Persistence and the backfill/grandfather are DB-tier concerns; the entity maps it. |
| Discovery age filter reads DOB (D-05) | Database / Storage (native SQL in `SwipeRepository`) | API (`DiscoveryService` age math) | The filter runs in SQL; age display math runs in the service reading the entity. |
| Under-18 error contract (AGE-02) | API / Backend (`GlobalExceptionHandler`) | — | RFC 7807 mapping is an API-tier cross-cut, mirroring existing exception handlers. |

## Standard Stack

### Core

No new external libraries. This phase uses only what the project already depends on.

| Library / Facility | Version | Purpose | Why Standard |
|--------------------|---------|---------|--------------|
| `java.time.LocalDate` / `java.time.Period` | JDK 17 | DOB type + 18-year age computation | Already used by `ProfileService.validateAge` (`Period.between(dob, now).years`) and `DiscoveryService`; the exact rule to lift into `AgeVerifier`. `[VERIFIED: codebase grep]` |
| Jakarta Bean Validation (`jakarta.validation.constraints`) | Spring Boot 4.0.6 managed | `@NotNull` / `@Past` on `RegisterRequest.dateOfBirth` | Already the DTO validation convention (`@Email`, `@Size` on `RegisterRequest`). `[VERIFIED: codebase grep]` |
| Spring `ProblemDetail` (RFC 7807) | Spring Boot 4.0.6 managed | Under-18 422 body + `code` property | Every handler in `GlobalExceptionHandler` uses `ProblemDetail.forStatusAndDetail(...)` + `setProperty("code", ...)`. `[VERIFIED: codebase grep]` |
| Spring `@Component` + `@ConditionalOnProperty` | Spring Boot 4.0.6 managed | Seam default-impl selection | `LoggingEmailSender` / `LoggingPushProvider` are chosen this way; `AgeVerifier` mirrors it (though a single default impl needs no condition). `[VERIFIED: codebase grep]` |
| Flyway migration (append-only) | Spring Boot 4.0.6 managed | V22 schema + data migration | V1–V21 exist; next is **V22**. `[VERIFIED: codebase — latest is V21__create_reports_table.sql]` |
| `@Value` custom `app.*` key | Spring | Optional `app.age.minimum-age:18` | AGENTS.md documents `@Value`-bound `app.*` keys (no `@ConfigurationProperties`); `app.report.operator-email` etc. already exist in `application.yml`. `[VERIFIED: application.yml + AGENTS.md]` |

### Supporting

None required.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| DOB on `users` (D-04) | Keep DOB on `user_profiles`, gate at profile completion | Rejected by D-01/PITFALLS Pitfall 3 — a profile-completion gate lets an under-18 `users` row exist first (weaker guarantee). |
| `AgeVerifier.requireAdult(dob)` throwing an exception | Result type (`AgeCheckResult`) inspected by caller | Throwing matches the in-repo guard style (`EmailNotVerifiedException` thrown in `AuthService.login`); a result type adds a branch at the single call site with no benefit. **Recommend: throwing.** |
| `app.age.minimum-age:18` `@Value` key | Hard-coded `18` constant in the default impl | Either is acceptable (discretion). A `@Value` key with default `18` costs nothing and matches the `app.*` convention; **recommend the config key** so a market override needs no recompile (still single global value — jurisdiction routing is deferred). |

**Installation:** None — no packages added. (Package Legitimacy Audit N/A.)

## Package Legitimacy Audit

**N/A — this phase installs no external packages.** All facilities are JDK or already-managed Spring Boot dependencies. No `[SLOP]`/`[SUS]` risk surface.

## Architecture Patterns

### System Architecture Diagram

```
POST /api/auth/register  { email, password, dateOfBirth }
        │
        ▼
AuthController.register  (@Valid → 400 on @NotNull/@Past failure)
        │
        ▼
AuthService.register
        │  (1) ageVerifier.requireAdult(dateOfBirth)   ── under-18 ─▶ UnderMinimumAgeException
        │        (before ANY user row is written; D-01/D-02)              │
        │  (2) existsByEmail → DuplicateEmailException (409)              ▼
        │  (3) User(email, passwordHash, dateOfBirth) ─▶ userRepository   GlobalExceptionHandler
        │  (4) emailVerificationService.issueAndSend(...)                 → 422 + code=UNDER_MINIMUM_AGE (RFC 7807)
        ▼
users row created WITH date_of_birth (single source of truth, D-04)
        │
        ▼ (later, read paths — D-05)
Discovery feed SQL:  JOIN users u ON u.id = up.user_id → filter on u.date_of_birth
DiscoveryService:    age = Period.between(user.dateOfBirth, today)  (owner/user profile age display)
ProfileService:      NO DOB read/write (validateAge removed; DTO fields removed; D-06/D-07)

V22 migration (one-time, D-10/D-11):
  ALTER users ADD date_of_birth DATE NULL
  UPDATE users u SET date_of_birth = up.date_of_birth FROM user_profiles up WHERE up.user_id = u.id AND u.date_of_birth IS NULL
  ALTER user_profiles DROP COLUMN date_of_birth
  (NULL-DOB users left grandfathered; no gate)
```

### Recommended Project Structure

```
src/main/kotlin/com/catspell/api/
├── age/                              # NEW domain package for the seam
│   ├── AgeVerifier.kt                # interface: fun requireAdult(dateOfBirth: LocalDate)
│   └── LocalAgeVerifier.kt          # @Component default impl (Period-based, min age 18)
├── auth/
│   ├── model/AuthDtos.kt            # + dateOfBirth on RegisterRequest
│   ├── model/User.kt                # + var dateOfBirth: LocalDate? (nullable, grandfather)
│   └── service/AuthService.kt       # requireAdult(...) before save; persist DOB
├── common/exception/
│   ├── Exceptions.kt                # + UnderMinimumAgeException
│   └── GlobalExceptionHandler.kt    # + 422 + code mapping
├── profile/
│   ├── model/UserProfile.kt         # − dateOfBirth
│   ├── model/ProfileDtos.kt         # − dateOfBirth (Create/Update/Response)
│   └── service/ProfileService.kt    # − validateAge, − DOB writes
└── discovery/
    ├── model/SwipeRepository.kt     # native SQL: JOIN users, read u.date_of_birth
    └── service/DiscoveryService.kt  # age math from users.date_of_birth
src/main/resources/db/migration/
└── V22__move_date_of_birth_to_users.sql   # add nullable + backfill + drop
```

### Pattern 1: Provider-abstracted seam (interface + default `@Component`)

**What:** A narrow interface with a single default `@Component` implementation; call sites depend only on the interface so a vendor implementation drops in later without touching them (D-03).
**When to use:** Any capability with a "swap the provider later" requirement — exactly the `AgeVerifier` case.
**Example (mirrors `EmailSender`/`PushProvider`):**
```kotlin
// Source: in-repo EmailSender.kt / PushProvider.kt
package com.catspell.api.age

import java.time.LocalDate

interface AgeVerifier {
    /** Throws UnderMinimumAgeException when the DOB implies an age below the configured minimum. */
    fun requireAdult(dateOfBirth: LocalDate)
}

@org.springframework.stereotype.Component
class LocalAgeVerifier(
    @org.springframework.beans.factory.annotation.Value("\${app.age.minimum-age:18}")
    private val minimumAge: Int
) : AgeVerifier {
    override fun requireAdult(dateOfBirth: LocalDate) {
        val age = java.time.Period.between(dateOfBirth, LocalDate.now()).years
        if (age < minimumAge) throw UnderMinimumAgeException()
    }
}
```
> Note: unlike `EmailSender` (two impls chosen by `@ConditionalOnProperty`), `AgeVerifier` has only one impl now, so **no** `@ConditionalOnProperty` is needed — a plain `@Component`. The interface *is* the swap point.

### Pattern 2: Register-time ordered guard, evaluated before persistence

**What:** Registration runs cheap always-on guards first (age), then persists. The age check throws before `userRepository.save`, so no under-18 row is ever created.
**When to use:** The register hard-gate (D-01/D-02). Matches milestone ARCHITECTURE Pattern 2, which explicitly leaves room for Phase 16's invite check *after* the age check.
**Example:**
```kotlin
// Source: .planning/research/ARCHITECTURE.md §"Pattern 2" + in-repo AuthService.register
fun register(request: RegisterRequest) {
    ageVerifier.requireAdult(request.dateOfBirth)      // (1) cheap, always on → 422 under-18
    if (userRepository.existsByEmail(request.email)) { // (2) existing check
        throw DuplicateEmailException()
    }
    val user = User(
        email = request.email,
        passwordHash = passwordEncoder.encode(request.password)!!,
        dateOfBirth = request.dateOfBirth              // (3) persist DOB on users
    )
    val savedUser = userRepository.save(user)
    emailVerificationService.issueAndSend(savedUser)
}
```

### Pattern 3: RFC 7807 exception with distinct machine `code`

**What:** A domain exception mapped in `GlobalExceptionHandler` to a specific status + a `code` property the mobile app routes on.
**When to use:** The under-18 response (D-08/D-09), modelled exactly on `EmailNotVerifiedException` → 403 `EMAIL_NOT_VERIFIED`.
**Example:**
```kotlin
// Source: in-repo GlobalExceptionHandler.handleEmailNotVerified
@ExceptionHandler(UnderMinimumAgeException::class)
fun handleUnderMinimumAge(ex: UnderMinimumAgeException): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "You must be at least 18 to sign up"
    )
    problem.title = "Unprocessable Entity"
    problem.setProperty("code", "UNDER_MINIMUM_AGE")
    return problem
}
```

### Pattern 4: Add-nullable → backfill → drop migration (grandfather-safe)

**What:** A single Flyway migration that adds the new column nullable, backfills from the old location, then drops the old column, leaving rows that have no source value as NULL (grandfathered).
**When to use:** V22 (D-10/D-11). Mirrors V17's `UPDATE ... WHERE ... IS NULL` grandfather pattern.
**Example:**
```sql
-- Source: mirrors V17__add_email_verified_at_to_users.sql grandfather backfill
ALTER TABLE users ADD COLUMN date_of_birth DATE;

-- Backfill from completed profiles; idempotent via the IS NULL guard (no-op on empty/rerun).
UPDATE users u
SET date_of_birth = up.date_of_birth
FROM user_profiles up
WHERE up.user_id = u.id
  AND u.date_of_birth IS NULL;

-- DOB now lives on users as the single source of truth (D-04).
ALTER TABLE user_profiles DROP COLUMN date_of_birth;
```

### Anti-Patterns to Avoid
- **Age gate in the mobile client only** (PITFALLS): the block MUST be server-side and before persistence. A client gate is bypassable.
- **Gating at profile completion instead of register** (PITFALLS Pitfall 3): permits an under-18 `users` row to exist. Rejected by D-01.
- **Leaving DOB writable via `UpdateProfileRequest`**: reintroduces post-signup age tampering (violates D-06). Remove the field, don't just ignore it.
- **Two age checks (profile + register)**: D-07 requires exactly one. Deleting `ProfileService.validateAge` is mandatory, not optional cleanup.
- **Making `users.date_of_birth` NOT NULL in V22**: would fail for grandfathered no-DOB accounts and lock them out (violates D-11). Keep nullable.
- **Backfilling with an under-18 audit/suspend step**: out of scope (D-12).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Age computation | Custom day/month arithmetic | `java.time.Period.between(dob, LocalDate.now()).years` | Handles leap years / month boundaries correctly; already the in-repo rule. |
| RFC 7807 error body | Hand-rolled JSON error map | Spring `ProblemDetail` + `setProperty("code", …)` | Consistent with every existing handler; the app already routes on `code`. |
| Provider abstraction | Ad-hoc `if (vendorEnabled)` branches | Interface seam + `@Component` | Matches `EmailSender`/`PushProvider`; keeps call sites clean for the deferred vendor. |
| DOB backfill | App-code loop over users | A single SQL `UPDATE ... FROM ... WHERE IS NULL` in Flyway | Set-based, atomic, idempotent; mirrors V17. |

**Key insight:** Every primitive this phase needs already has a blessed in-repo form. The risk is *inconsistency* (a second age rule, a bespoke error shape), not missing tooling.

## Runtime State Inventory

> Included because this is a **data-migration** phase (DOB relocation).

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `user_profiles.date_of_birth` (NOT NULL) holds every completed profile's DOB. This is the migration source. Discovery SQL and `DiscoveryService`/`ProfileService` read it. | **Data migration** (V22 backfill to `users.date_of_birth`) **+ code edits** (repoint all readers to `users`; drop the column). |
| Live service config | None — no external service stores DOB. `app.*` config keys live in `application.yml` (in git). Optional new `app.age.minimum-age` is code/config only. | Code edit only (add optional key). |
| OS-registered state | None — verified: no scheduler/daemon references DOB. | None. |
| Secrets/env vars | None — verified: no secret/env var references DOB. | None. |
| Build artifacts | None — verified: no generated artifact carries DOB (JPA schema is runtime, Flyway-managed). | None. |

**The canonical question:** *After every file is updated, what runtime systems still have the old DOB location?* → Only the Postgres `user_profiles.date_of_birth` column, which V22 backfills-then-drops. Test databases are ephemeral (Testcontainers, truncated per test) so they replay V22 from scratch — no stale state.

## Common Pitfalls

### Pitfall 1: Test suite goes red because DOB moved from profile to register
**What goes wrong:** ~12 integration test files supply DOB through the profile-creation JSON (`"dateOfBirth" to "2000-01-15"`), and discovery age-range tests seed **distinct** per-user DOBs at profile time. After this phase, `CreateProfileRequest` has no `dateOfBirth`, `user_profiles.date_of_birth` is gone, and DOB must arrive at **register**. Register helpers currently pass only `email`/`password`.
**Why it happens:** The DOB collection point moves upstream (profile → register), but the test fixtures still set it downstream.
**How to avoid:** In the same phase, thread `dateOfBirth` through the register test helpers (`registerUser`, `registerAndGetToken`, `setupCompleteUser`, etc.), and drop the now-ignored `dateOfBirth` from profile-creation bodies. The discovery age-range test (`feed respects bidirectional age range`) must seed each user's DOB at register. **This is the single biggest task-count driver — plan it as its own task, not an afterthought.**
**Warning signs:** `./gradlew test` compiles but discovery/age tests fail with empty feeds or unexpected 422s; register calls returning 400 (missing DOB `@NotNull`).

### Pitfall 2: Under-18 returns 400 instead of 422
**What goes wrong:** If the 18+ check is expressed as a Bean Validation annotation or a generic `IllegalArgumentException`, it surfaces as a `400` (indistinguishable from a malformed field), and the app can't route to the tailored under-18 screen.
**Why it happens:** `GlobalExceptionHandler` already maps `IllegalArgumentException` → 400 and `MethodArgumentNotValidException` → 400.
**How to avoid:** The hard 18+ block MUST be the `AgeVerifier` → `UnderMinimumAgeException` → **422 + `code`** path (D-08/D-09). Basic sanity validation (`@NotNull`, `@Past` future-date reject) may be 400 — that's fine and separate. Do **not** annotate a `@Min`-style age constraint on the DTO.
**Warning signs:** Under-18 register returns 400 with a `violations` array instead of 422 with `code=UNDER_MINIMUM_AGE`.

### Pitfall 3: Discovery age filter breaks when `user_profiles.date_of_birth` is dropped
**What goes wrong:** The native discovery query (`SwipeRepository.findDiscoveryFeed`) reads `up.date_of_birth` and `requester.date_of_birth` and gates on `up.date_of_birth IS NOT NULL`. Dropping the column without repointing the query is a runtime SQL error.
**Why it happens:** The column is referenced in raw SQL (compiler can't catch it).
**How to avoid:** Add `JOIN users u ON u.id = up.user_id` (and a requester `JOIN users ru ON ru.id = :requesterId`), replace `up.date_of_birth` → `u.date_of_birth`, `requester.date_of_birth` → `ru.date_of_birth`, and `up.date_of_birth IS NOT NULL` → `u.date_of_birth IS NOT NULL`. Behavior is preserved: completed profiles are backfilled, so their `users.date_of_birth` is set; grandfathered NULL-DOB rows never had a completed profile anyway. Also update `DiscoveryService.getOwnerProfile`/`getUserProfile`, which compute `age` from `profile.dateOfBirth` — source from the `User` (inject/read via the already-present `userRepository`).
**Warning signs:** `column up.date_of_birth does not exist` at feed time; `UserProfile.dateOfBirth` unresolved reference at compile.

### Pitfall 4: `ProfileResponse` still exposes DOB after the field is removed
**What goes wrong:** `ProfileResponse` has a non-null `dateOfBirth`, and `ProfileService.toResponse` maps `profile.dateOfBirth`. Removing the entity field without updating the DTO + mapper is a compile error; leaving DOB on the response contradicts CONTEXT's canonical-refs note ("remove … from `ProfileResponse` mapping").
**Why it happens:** DOB is referenced in three DTOs (`Create`/`Update`/`Response`) plus the mapper.
**How to avoid:** Remove `dateOfBirth` from all three profile DTOs and from `toResponse`. If a client still needs to display DOB/age, it comes from the user record, not the profile (out of scope to add a new endpoint this phase).
**Warning signs:** Unresolved `profile.dateOfBirth` in `toResponse`; OpenAPI snapshot test diffs on the profile schema.

## Code Examples

### 18+ math lifted into the seam (behavior-identical to the removed `validateAge`)
```kotlin
// Source: in-repo ProfileService.validateAge (the exact rule to move, then delete)
val age = java.time.Period.between(dateOfBirth, java.time.LocalDate.now()).years
if (age < minimumAge) throw UnderMinimumAgeException()
```

### Under-18 exception (mirrors EmailNotVerifiedException)
```kotlin
// Source: in-repo Exceptions.kt
class UnderMinimumAgeException(
    message: String = "You must be at least 18 years old to sign up"
) : RuntimeException(message)
```

### RegisterRequest with DOB (mirrors existing @Email/@Size style)
```kotlin
// Source: in-repo AuthDtos.kt
data class RegisterRequest(
    @field:Email(message = "must be a valid email address")
    val email: String,

    @field:Size(min = 8, message = "must be at least 8 characters")
    val password: String,

    @field:NotNull
    @field:Past(message = "date of birth must be in the past")
    val dateOfBirth: LocalDate
)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| DOB on `user_profiles`, 18+ check at profile creation (`ProfileService.validateAge` → 400) | DOB on `users`, hard 18+ gate at register behind `AgeVerifier` (→ 422 + `code`) | This phase (v2.2) | No under-18 `users` row can exist; one age rule; vendor-swappable seam. |
| Self-attested DOB only | Self-attested DOB behind a seam, vendor drop-in deferred | This phase | Regulatory vendor (EU DSA / UK OSA / AU) can be added later without call-site changes (AGE2-01). |

**Deprecated/outdated:**
- `ProfileService.validateAge` — removed (D-07); superseded by `AgeVerifier.requireAdult`.
- `user_profiles.date_of_birth` column and `UserProfile.dateOfBirth` field — removed (D-04/D-06).
- `dateOfBirth` on `CreateProfileRequest` / `UpdateProfileRequest` / `ProfileResponse` — removed (D-06).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Removing `dateOfBirth` from `ProfileResponse` is intended (CONTEXT canonical-refs says "remove from … `ProfileResponse` mapping"). | Pitfall 4 / Structure | If a client depends on the profile response carrying DOB, that field disappears. Mitigation: DOB is immutable and known at signup; the mobile app already has it. Flag for UAT. |
| A2 | Register composition puts the age check **before** the duplicate-email check (D-02 "age check first"). | Pattern 2 | An under-18 registering with an already-taken email gets 422 (age) rather than 409 (duplicate). This is the intended precedence (age is the hard gate). |
| A3 | No dedicated index on `users.date_of_birth` is needed (the discovery filter uses `EXTRACT(YEAR FROM AGE(...))`, which the existing `user_profiles.date_of_birth` had no index for either). | Standard Stack / discretion | Marginal query-plan difference; behavior unchanged. Add an index later only if discovery latency regresses. |
| A4 | Clock/timezone basis for the 18th-birthday boundary is server `LocalDate.now()` (same as the removed `validateAge` and discovery `CURRENT_DATE`). | Discretion | Off-by-a-day at midnight across timezones for edge birthdays; matches existing behavior, so no regression. |

## Open Questions

1. **Should `ProfileResponse` keep `dateOfBirth` (read-only, sourced from `users`) or drop it entirely?**
   - What we know: CONTEXT canonical-refs lists `ProfileResponse` under D-06 removals; D-06's text names only the request DTOs.
   - What's unclear: whether the mobile profile screen reads DOB from the profile response.
   - Recommendation: **Drop it** (follow the canonical-refs note). If UAT shows the app needs it, re-add as a read-only field sourced from `users` in a follow-up — cheap and non-blocking.

2. **`app.age.minimum-age` config key vs. hard-coded `18`?**
   - What we know: Both satisfy D-03 (discretion).
   - Recommendation: **Config key with default `18`** — matches `app.*` convention, zero cost, avoids a recompile for a market override (jurisdiction routing still deferred).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL + PostGIS | V22 migration + discovery SQL | ✓ | 16 + 3.4 (compose + Testcontainers `postgis/postgis:16-3.4-alpine`) | — |
| Flyway | V22 migration | ✓ | Spring Boot 4.0.6 managed; runs on startup | — |
| JDK / Kotlin | All code | ✓ | JVM 17 / Kotlin 2.4 | — |
| Podman (local compose) | Local run only | ✓ (per AGENTS.md) | — | Testcontainers for CI/tests |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None — all facilities present.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (`@SpringBootTest` + `@AutoConfigureMockMvc`) + Testcontainers (Postgres/PostGIS + MinIO) |
| Config file | `build.gradle.kts` (test deps); `BaseIntegrationTest.kt` (container + DB-truncate harness) |
| Quick run command | `./gradlew test --tests "com.catspell.api.auth.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AGE-01 | Register accepts + persists `dateOfBirth` on `users` | integration | `./gradlew test --tests "com.catspell.api.auth.AgeGateIntegrationTest"` | ❌ Wave 0 |
| AGE-02 | Under-18 register → 422 + `code=UNDER_MINIMUM_AGE`; no `users` row created; 18+ succeeds | integration | `./gradlew test --tests "com.catspell.api.auth.AgeGateIntegrationTest"` | ❌ Wave 0 |
| AGE-02 | `AgeVerifier.requireAdult` boundary math (17y364d fails, exactly-18 passes) | unit/integration | `./gradlew test --tests "com.catspell.api.age.*"` | ❌ Wave 0 |
| AGE-03 | V22 backfills DOB from completed profiles; NULL-DOB rows grandfathered (usable, not gated) | integration (migration) | `./gradlew test --tests "com.catspell.api.auth.DobMigrationTest"` | ❌ Wave 0 |
| D-05 | Discovery age filter still excludes out-of-range ages after DOB sourced from `users` | integration | `./gradlew test --tests "com.catspell.api.discovery.DiscoveryIntegrationTest"` | ✅ (exists; must be updated to seed DOB at register) |
| D-06 | Profile update cannot change DOB (field absent); profile create ignores DOB | integration | `./gradlew test --tests "com.catspell.api.profile.ProfileIntegrationTest"` | ✅ (exists; update to drop DOB coupling) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.catspell.api.auth.*"` (or the module touched)
- **Per wave merge:** `./gradlew test`
- **Phase gate:** full `./gradlew test` green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/catspell/api/auth/AgeGateIntegrationTest.kt` — covers AGE-01, AGE-02 (register accepts DOB, under-18 → 422 + code + no row, 18+ succeeds).
- [ ] `src/test/kotlin/com/catspell/api/age/LocalAgeVerifierTest.kt` — boundary math (exactly 18 passes, 17y364d fails, future DOB rejected at DTO).
- [ ] `src/test/kotlin/com/catspell/api/auth/DobMigrationTest.kt` — V22 backfill + grandfather (mirrors `GrandfatherMigrationTest`).
- [ ] Update existing register test helpers across ~12 files to supply `dateOfBirth` at register; drop DOB from profile-creation bodies (Pitfall 1).

## Security Domain

> `security_enforcement: true`, ASVS L1, block on `high` (config.json). Each PLAN.md must carry a `<threat_model>` block.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Register/login auth unchanged; DOB is not a credential. |
| V3 Session Management | no | No session behavior change. |
| V4 Access Control | yes | DOB immutability (D-06) — removing `dateOfBirth` from `UpdateProfileRequest` prevents a user tampering with their own age post-signup (business-logic access control). |
| V5 Input Validation | yes | `@NotNull` + `@Past` on `RegisterRequest.dateOfBirth`; `AgeVerifier` for the 18+ business rule (server-side, not client). |
| V6 Cryptography | no | No secrets/crypto introduced. |
| V7 Errors & Logging | yes | RFC 7807 error carries a `code` but no PII; do not log full DOB at info level. |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Under-18 bypasses gate via client-only enforcement | Elevation of Privilege / Spoofing | Server-side `AgeVerifier.requireAdult` before persistence (D-01) — never trust the client. |
| Under-18 self-attests a false DOB | Spoofing | Accepted residual risk for the MVP (self-attested); `AgeVerifier` seam is the drop-in point for a vendor check (deferred AGE2-01). Documented, not mitigated this phase. |
| User edits DOB after signup to change age semantics | Tampering | DOB immutable — removed from `UpdateProfileRequest` and the profile write path (D-06). |
| Migration data-integrity (wrong/lost DOBs, lockout) | Tampering / DoS | Idempotent `UPDATE ... WHERE IS NULL` backfill mirroring V17; keep column nullable so no completed account is locked out; covered by `DobMigrationTest`. |
| Under-18 error leaks account-existence | Information Disclosure | Age check runs before the duplicate-email check (D-02) and returns a generic under-18 message with no account detail. |

## Sources

### Primary (HIGH confidence)
- In-repo code (grep + read): `AuthService.kt`, `AuthDtos.kt`, `User.kt`, `UserProfile.kt`, `ProfileService.kt`, `ProfileDtos.kt`, `Exceptions.kt`, `GlobalExceptionHandler.kt`, `EmailSender.kt`/`LoggingEmailSender.kt`, `PushProvider.kt`, `SwipeRepository.kt`, `DiscoveryService.kt`, `ProfileCompleteness.kt`, `V17__…sql`, `V21__…sql`, `application.yml`, `BaseIntegrationTest.kt`, `GrandfatherMigrationTest.kt`, `AuthIntegrationTest.kt` — the authoritative source for every pattern lifted here.
- `.planning/research/ARCHITECTURE.md` §"Pattern 2: Signup gate composition (age + invite)" — register-time ordered guards + `AgeVerifier` seam + DOB-placement note.
- `.planning/research/PITFALLS.md` §"Pitfall 3: DOB collected too late for a hard 18+ gate" — the central decision (resolved by D-01/D-04).
- `AGENTS.md` — Podman, `@Value`-bound `app.*` keys, Flyway/Testcontainers workflow.

### Secondary (MEDIUM confidence)
- `.planning/research/STACK.md` / `SUMMARY.md` — self-attested-DOB-vs-vendor trade-off and the "keep an `AgeVerifier` seam" recommendation.

### Tertiary (LOW confidence)
- None — all claims grounded in the codebase or milestone research; no external web lookups were required (no new packages).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; every facility verified in-repo.
- Architecture: HIGH — patterns lifted verbatim from existing seams/handlers/migrations and milestone ARCHITECTURE.
- Pitfalls: HIGH — test-surface impact and SQL-column coupling confirmed by grep across `src/test` and `SwipeRepository`.

**Research date:** 2026-09-28
**Valid until:** 2026-10-28 (stable — internal patterns, no fast-moving external deps)
