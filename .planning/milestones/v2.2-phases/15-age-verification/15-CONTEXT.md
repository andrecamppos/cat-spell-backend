# Phase 15: Age Verification - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver a **server-side 18+ hard gate at signup** via a **self-attested date of birth** collected on the `POST /api/auth/register` payload. The gate is enforced **before the account row is created**, behind an **`AgeVerifier` seam** (local DOB math now, vendor-swappable later without touching call sites). DOB becomes a first-class attribute of the account (`users` table), set once at signup and **immutable thereafter**. A Flyway **V22** migration moves DOB onto `users`, backfills it from existing completed profiles, and grandfathers accounts that have no DOB so nobody is locked out on rollout.

**In scope:** `dateOfBirth` field on `RegisterRequest`; `AgeVerifier` seam + default local-DOB implementation; register-time hard-block (`422` + distinct code) evaluated before user creation; `date_of_birth` column on `users`; V22 migration (add column nullable + backfill from `user_profiles` + drop `user_profiles.date_of_birth`); reconciling the discovery age filter and `ProfileService` to read DOB from `users`; removing DOB from the profile create/update DTOs (immutable after signup); removing the now-superseded `ProfileService.validateAge`.

**Out of scope (own phases / deferred):** third-party age-estimation / ID-verification vendor integration (the `AgeVerifier` seam exists precisely so it can drop in later — AGE-01/02 explicitly defer the vendor); invite-only gate (Phase 16, which composes onto the same register flow); waitlist (Phase 17); forcing grandfathered no-DOB accounts to supply a DOB later (a separate backfill-collection flow — deferred); configurable minimum age per market / legal jurisdiction handling.

</domain>

<decisions>
## Implementation Decisions

### Gate point & DOB collection (AGE-01, AGE-02)
- **D-01:** The 18+ gate fires **at `POST /api/auth/register`** — a **true hard gate**. `AgeVerifier.requireAdult(dateOfBirth)` runs **before** the `users` row is created, so no under-18 account ever exists in the database. This resolves research Pitfall 3 ("DOB collected too late for a hard 18+ gate") with the recommended option, not the weaker profile-completion gate.
- **D-02:** Add a **`dateOfBirth`** field to `RegisterRequest` (`AuthDtos.kt`). It is required (self-attested DOB collected at signup). Register composition order: age check first (cheap, always on), matching research Architecture Pattern 2 — this leaves room for Phase 16 to insert the invite check after the age check without reordering.

### AgeVerifier seam (AGE-02)
- **D-03:** Introduce an **`AgeVerifier` seam** (interface + default local implementation doing DOB math against an 18-year minimum). Call sites depend only on the seam, so a vendor implementation can drop in later with no call-site changes. Mirror the established in-repo seam pattern (`EmailSender`, `PushProvider`). Interface shape, package placement, method signature (e.g. `requireAdult(dob)` throwing vs. a result type), and whether the minimum age is a `@Value`-backed config key (e.g. `app.age.minimum-age:18`) vs. a constant are **Claude's discretion** — follow existing seam + config conventions.

### DOB storage & single source of truth (AGE-01)
- **D-04:** DOB is stored on the **`users` table** and is the **single source of truth**. The existing **`user_profiles.date_of_birth` column is dropped** (V22). Because DOB now arrives at register — before any `user_profiles` row exists — it cannot live only on the profile.
- **D-05:** Update the **discovery age filter** (`SwipeRepository` JOINs that read `up.date_of_birth`) and **`DiscoveryService`** (age computation) and **`ProfileService`** to read DOB from `users` instead of `user_profiles`. Exact SQL/JPA rewrite is Claude's discretion; the age-filter *behavior* must not change.
- **D-06:** DOB is **immutable after signup**. Remove `dateOfBirth` from **both** `CreateProfileRequest` and `UpdateProfileRequest` (and from the profile-creation write path). Set once at register, never editable via profile edit — prevents post-signup age tampering.
- **D-07:** The old **`ProfileService.validateAge`** (which threw a generic `IllegalArgumentException` → 400 at profile creation) is **removed** — it is fully superseded by the register-time `AgeVerifier` gate. The `<18` guard now lives in exactly one place.

### Under-18 error contract (AGE-02)
- **D-08:** An under-18 register attempt returns **`422 Unprocessable Entity`** (payload is syntactically valid but semantically rejected by a business rule — distinct from Bean Validation `400`s) with an RFC 7807 body.
- **D-09:** The response carries a **distinct machine-readable `code`** (e.g. `UNDER_MINIMUM_AGE` / `AGE_REQUIREMENT_NOT_MET`) via `problem.setProperty("code", ...)`, mirroring how `EMAIL_NOT_VERIFIED` (403) drives the app's resend screen — so the mobile app can route to a tailored under-18 message. Add a dedicated exception + `GlobalExceptionHandler` mapping (alongside `EmailNotVerifiedException`). Exact code string and exception class name are Claude's discretion.

### Existing-account migration / rollout (AGE-03)
- **D-10:** V22 adds `users.date_of_birth` as **nullable**, then **backfills** it from `user_profiles.date_of_birth` for every account that has a completed profile, then drops the profile column.
- **D-11:** Accounts with **no DOB** (registered but never completed a profile) are **grandfathered** — the column stays nullable, they remain fully usable, and no gate is applied to them (mirrors the Phase 11 email-verification grandfather). **Only new signups** must pass the age gate. No forced DOB-collection flow for existing accounts (deferred).
- **D-12:** Backfill DOBs **as-is with no special under-18 handling**. Completed profiles already passed the old profile-creation 18+ check, so under-18 completed profiles are not expected to exist; the migration does not audit/flag/suspend. (The research risk of pre-existing under-18 rows is effectively theoretical here because the profile gate predates this phase.)

### Claude's Discretion
- `AgeVerifier` interface/method shape, package placement, throwing-vs-result contract, and minimum-age constant vs. config key.
- Exact under-18 exception class name and `code` string, and the RFC 7807 title/detail copy.
- The `SwipeRepository` / `DiscoveryService` / `ProfileService` rewrite to source DOB from `users` (behavior-preserving), and the `users.date_of_birth` column type/index.
- Register-payload DOB validation beyond the age gate (e.g. rejecting future dates or absurd ages), and clock/timezone basis for the 18th-birthday boundary — follow existing validation conventions.
- Whether `RegisterRequest.dateOfBirth` validation is Bean Validation vs. service-layer — the *hard 18+ block* itself must be the `AgeVerifier`/422 path (D-08/D-09), not a generic 400.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/ROADMAP.md` §"Phase 15: Age Verification" — goal, success criteria, DOB-placement note, V19+ migration note (actual next version is **V22**)
- `.planning/REQUIREMENTS.md` — AGE-01, AGE-02, AGE-03 (the requirements this phase closes)

### Milestone research (v2.2)
- `.planning/research/PITFALLS.md` §"Pitfall 3: DOB collected too late for a hard 18+ gate" (lines ~47-62) — the central decision, resolved here by D-01/D-04; also §"Age gate in mobile client only" anti-pattern and the risk table row on under-18 audit
- `.planning/research/ARCHITECTURE.md` §"Pattern 2: Signup gate composition (age + invite)" (lines ~96-113) — register-time ordered guards, `AgeVerifier` seam, and the explicit DOB-placement note; §"AgeVerifier (seam)" component row (~line 43); §"Future integration points" (`AgeVerifier` seam / vendor deferred, ~line 199)
- `.planning/research/STACK.md` — self-attested DOB vs. third-party vendor trade-off and the "keep an `AgeVerifier` seam" recommendation (~lines 51-63)
- `.planning/research/SUMMARY.md` — v2.2 overview; age-gate = server-side at account creation; `AgeVerifier` seam; DOB-collected-too-late risk (~lines 34-60)

### Prior phase context (patterns this phase reuses)
- `.planning/PROJECT.md` §"Key Decisions" — "Hard-gate login until email verified + grandfather migration" (Phase 11) and "Provider-abstracted `EmailSender` seam" (Phase 10) — the grandfather-migration and seam patterns mirrored here (D-03, D-11)

### In-repo code (read before modifying)
- `src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt` — `RegisterRequest`; add `dateOfBirth` (D-02)
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` — `POST /register` (returns 201); unchanged contract except the new field
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` — `register(...)`; insert `AgeVerifier.requireAdult(...)` before `userRepository.save(user)` (D-01); persist DOB on the new `User`
- `src/main/kotlin/com/catspell/api/auth/model/User.kt` — add `dateOfBirth` (nullable to accommodate grandfathered accounts, D-11)
- `src/main/kotlin/com/catspell/api/profile/model/UserProfile.kt` — remove `dateOfBirth` (D-04/D-06)
- `src/main/kotlin/com/catspell/api/profile/model/ProfileDtos.kt` — remove `dateOfBirth` from `CreateProfileRequest`/`UpdateProfileRequest`/`ProfileResponse` mapping (D-06)
- `src/main/kotlin/com/catspell/api/profile/service/ProfileService.kt` — remove `validateAge` and DOB writes (D-07)
- `src/main/kotlin/com/catspell/api/discovery/model/SwipeRepository.kt` — age-filter JOINs read `up.date_of_birth` (lines ~44-47, ~73-76); repoint to `users` (D-05)
- `src/main/kotlin/com/catspell/api/discovery/service/DiscoveryService.kt` — age computation from `profile.dateOfBirth` (lines ~121, ~160); source from `users` (D-05)
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` + `GlobalExceptionHandler.kt` — add the under-18 exception → **422 + `code`** mapping, modelled on `EmailNotVerifiedException` → 403 `EMAIL_NOT_VERIFIED` (lines ~67-73) (D-08/D-09)
- `src/main/kotlin/com/catspell/api/email/service/EmailSender.kt` / `src/main/kotlin/com/catspell/api/push/...PushProvider` — reference seam pattern to mirror for `AgeVerifier` (D-03)
- `src/main/resources/db/migration/V21__create_reports_table.sql` — latest migration; the new migration is **V22**, never edit V1–V21

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Existing 18+ math** in `ProfileService.validateAge` (`Period.between(dob, now).years < 18`) — the exact rule to lift into the `AgeVerifier` default implementation, then delete from `ProfileService`.
- **`EmailSender` / `PushProvider` seams** — proven interface + default-implementation + swappable-later pattern to copy for `AgeVerifier`.
- **`EmailNotVerifiedException` → 403 `EMAIL_NOT_VERIFIED`** mapping in `GlobalExceptionHandler` — exact template for the under-18 → 422 + distinct-`code` response (D-09).
- **Phase 11 grandfather migration** (V17 email-verified backfill) — template for the V22 backfill/grandfather of existing accounts (D-10/D-11).
- **`@Value`-backed config-key convention** (custom `app.*` keys, per AGENTS.md) — for an optional `app.age.minimum-age` key.

### Established Patterns
- Package-per-domain (controller/service/model); Flyway append-only migrations (next is **V22**); JPA entities + Spring Data repositories; RFC 7807 error bodies; Bean Validation on request DTOs; `@Transactional` service methods.
- The **register hard-gate composition** already exists conceptually at *login* (`AuthService.login` throws `EmailNotVerifiedException` after the password check) — the age gate is the *register-time* analogue, evaluated before account creation.
- Custom config keys bound via `@Value` (no `@ConfigurationProperties`) — per AGENTS.md the Spring VSCode "Unknown property" warning is cosmetic.

### Integration Points
- `AuthService.register` → `AgeVerifier.requireAdult(dob)` before user creation; DOB persisted on `User`.
- Discovery age filter (`SwipeRepository` + `DiscoveryService`) → sources DOB from `users` post-migration.
- `GlobalExceptionHandler` → new under-18 exception mapping.
- Flyway V22 → add `users.date_of_birth`, backfill from `user_profiles`, drop `user_profiles.date_of_birth`.

</code_context>

<specifics>
## Specific Ideas

- The gate must be a **true hard block before account creation** — no under-18 `users` row is ever persisted (not a client-only or profile-completion gate).
- DOB is **captured once at signup and immutable** — no second write path via profile edit.
- The `<18` rule lives in **exactly one place** (the `AgeVerifier` seam) after this phase; the duplicate profile-time check is removed.
- Rollout must **lock out nobody**: existing completed profiles are backfilled; existing no-DOB accounts are grandfathered.
- The under-18 response should be **routable by the app** (distinct `code`), consistent with the `EMAIL_NOT_VERIFIED` precedent.

</specifics>

<deferred>
## Deferred Ideas

- **Third-party age-estimation / ID-verification vendor** (Yoti, Veriff, Persona, Onfido, Apple Declared Age Range, etc.) — explicitly deferred by AGE-01/02; the `AgeVerifier` seam is the drop-in point when a target market's law (EU DSA, UK OSA, AU minimum-age) requires it.
- **Forcing grandfathered no-DOB accounts to supply a DOB** (backfill-collection flow at next login/profile step) — considered under Area 3; deferred as arguably its own scope.
- **Auditing / flagging / suspending pre-existing under-18 accounts** during migration — declined (D-12); the profile-time gate predates this phase so such rows aren't expected.
- **Configurable minimum age per market / jurisdiction routing** — beyond a single global 18 constant/config key.

None else — discussion stayed within phase scope.

</deferred>

---

*Phase: 15-age-verification*
*Context gathered: 2026-09-28*
