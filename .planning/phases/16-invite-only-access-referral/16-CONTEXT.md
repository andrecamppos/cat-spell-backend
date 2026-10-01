# Phase 16: Invite-Only Access & Referral - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Gate account creation behind **invite-only access** using **operator-issued codes**, controlled by a **global `app.invite.enabled` flag** that can be flipped off to go fully public. When the gate is ON, `POST /api/auth/register` requires a **valid, unconsumed, single-use invite code**; codes are **high-entropy (SecureRandom), stored hashed at rest**, and **invalid/consumed/missing-when-gated all return an identical generic error**. The operator issues codes through a **shared-secret-protected admin endpoint** (there is no admin role/JWT user in the app today). **Referral attribution** (referrer → invitee) is recorded at consumption when the invite carries an (optional) referrer. Introduces `invites` + `referrals` tables via Flyway **V23**.

**In scope:** `app.invite.enabled` global config gate (INV-01); operator code issuance via a shared-secret admin endpoint, one code per call (INV-02); register-time invite validation + single-use consumption composed **after** the age gate (INV-03); high-entropy SecureRandom codes hashed at rest, single-use, generic enumeration-safe error for invalid/consumed/missing (INV-04); `invites` table (nullable `referrer_user_id`) + `referrals` table, referral row written at consumption only when a referrer exists (INV-05); V23 migration.

**Out of scope (own phases / deferred):** member-generated invites, per-member quotas, and referral rewards (explicitly deferred — operator-issued codes only this phase); waitlist → invite conversion and emailing the code to the invitee (Phase 17); invite code expiry/TTL (deferred — no expiry this phase); operator revocation of unused codes (deferred); a general admin role / RBAC system (only a shared-secret guard this phase); referral tracking while the gate is OFF (public mode ignores codes entirely).

</domain>

<decisions>
## Implementation Decisions

### Operator issuance mechanism (INV-02)
- **D-01:** The operator issues codes through a **shared-secret-protected admin HTTP endpoint** (e.g. `POST /api/admin/invites`). Chosen over bootstrap-seed-on-startup and manual/out-of-band SQL because it is the most consistent with existing config-driven patterns, gives a real, integration-testable API surface, and provides the programmatic `InviteService.create(...)` path that Phase 17 (waitlist → invite conversion) will reuse.
- **D-02:** **One code per issuance call** (single-code request → single code returned). No batch/count parameter this phase — keeps the contract minimal; the operator loops if they need several.
- **D-03:** Because there is **no admin JWT user** in the app (`SecurityConfig` only distinguishes public vs. authenticated, and there are no roles), the admin route is **whitelisted `permitAll` in `SecurityConfig`** (mirroring the `/api/auth/*` public-endpoint whitelist) and guarded by a **static shared secret** validated in the controller/service — an `X-Admin-Token` header matched against an `app.invite.admin-token` config key (mirrors the `app.report.operator-email` config style). A missing/wrong secret returns a **generic 401/403 RFC 7807** body. A dedicated Spring Security filter + `ADMIN` authority was rejected to avoid introducing a role concept the app doesn't otherwise have.

### Code lifecycle & attributes (INV-02, INV-04)
- **D-04:** Invite codes have **no expiry** this phase. Single-use plus operator-controlled issuance volume bounds abuse; a TTL (`expires_at` + `app.invite.ttl-days`) is deferred. (If added later, expired codes must return the same generic error as consumed/invalid.)
- **D-05:** **Revocation is deferred** — no revoke endpoint/status this phase. Single-use + controlled issuance limits the blast radius of a leaked code.
- **D-06:** The **raw code is returned exactly once** in the issuance response. Codes are **stored hashed** (SHA-256 at rest, reusing the v2.1 hashed-token discipline), so the operator cannot retrieve a raw code after creation — it must be captured from the create response.
- **D-07:** Codes are **single-use** (INV-04). Consumption is enforced with an **atomic compare-and-set claim** (conditional update matching zero rows on a second attempt), mirroring the v2.1 single-use token pattern — no read-check-write race. Exact code format/length/entropy (URL-safe SecureRandom) is Claude's discretion; must be high-entropy and non-sequential (research Pitfall 4).

### Register flow & error contract (INV-03, INV-04)
- **D-08:** The invite check composes **after** the age gate in `AuthService.register` — the existing register order (`ageVerifier.requireAdult(...)` first, then the duplicate-email check, then `userRepository.save(...)`) already leaves the slot. Order: age gate → invite validate → (existing duplicate-email check + user save) → invite consume + referral record. Invite validation/consumption happen **before** the account row is committed / paired atomically so a failed consume never yields an orphan account. Add an `inviteCode` field to `RegisterRequest` (`AuthDtos.kt`), optional at the DTO level (required only when gated, enforced in service).
- **D-09:** **When the gate is OFF (`app.invite.enabled=false`), the `inviteCode` field is ignored entirely** — not read, not consumed, no referral recorded. "Public" means the invite path does not run. (This is why referral-while-public is out of scope.)
- **D-10:** **Invalid, already-consumed, AND missing-when-gated invite codes all return an identical generic `403`** with the same RFC 7807 body and a single generic machine-readable `code` (e.g. `INVITE_REQUIRED` / `invalid-or-expired-invite`) — maximum enumeration safety (research Pitfall 4: invalid vs consumed must be indistinguishable). `403` = "not allowed to register." Exact exception class name, `code` string, and 7807 title/detail copy are Claude's discretion; model the exception + `GlobalExceptionHandler` mapping on the existing `EmailNotVerifiedException` (403) / under-age (422) precedents.

### Referral attribution model (INV-05)
- **D-11:** The invite carries an **optional, nullable `referrer_user_id`** accepted at issuance. **Operator bootstrap codes leave it null.** At consumption, a **`referrals(referrer_id → invitee_id)` row is written ONLY when the invite has a non-null referrer.** This satisfies INV-05 where attribution exists, keeps bootstrap codes clean (no meaningless null-referrer rows), and future-proofs member-generated invites (deferred). Chosen over "always write a referrals row (nullable referrer)" and over deferring the referrer concept entirely.
- **D-12:** When a `referrer_user_id` is supplied at issuance, its **existence is validated** — issuance is rejected (`400`) if it doesn't match a real user, preventing dangling attribution. Guard against self-referral defensively (referrer ≠ invitee) even though the invitee is a brand-new account at consumption. `referrals` row captures at least `(referrer_id, invitee_id, invite_id, created_at)`; exact columns/indexes are Claude's discretion.

### Claude's Discretion
- Exact invite code format/length/entropy (URL-safe SecureRandom), and the `invites` / `referrals` table column shapes, types, and indexes (subject to: code hashed at rest, single-use claim, nullable `referrer_user_id`).
- The invite exception class name, generic `code` string, and RFC 7807 title/detail copy for the 403 (D-10), and the generic 401/403 shape for a bad admin secret (D-03).
- Admin endpoint path/DTO naming (`/api/admin/invites` suggested), the `InviteService` method signatures (`create`/`validate`/`consume`), and package placement (`com.catspell.api.invite` per ARCHITECTURE.md).
- Exact transaction boundary that keeps invite consumption + user creation atomic (no orphan account on consume failure), following existing `@Transactional` service conventions.
- Whether `app.invite.admin-token` has a dev default vs. is required (fail-fast) — follow existing `app.*` config conventions.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/ROADMAP.md` §"Phase 16: Invite-Only Access & Referral" — goal, 5 success criteria, table note ("V19+" — actual next version is **V23**)
- `.planning/REQUIREMENTS.md` — INV-01, INV-02, INV-03, INV-04, INV-05 (the requirements this phase closes)

### Milestone research (v2.2)
- `.planning/research/ARCHITECTURE.md` §"Pattern 2: Signup gate composition (age + invite)" (~lines 96-113) — ordered register guards, `app.invite.enabled` flag, `InviteService.validate/consume`; §"Component Responsibilities" `InviteService` row (~line 41); §"Recommended Project Structure" `invite/` package (~lines 54-57, 74); §"Internal Boundaries" `auth ↔ invite` (~line 206)
- `.planning/research/PITFALLS.md` §"Pitfall 4: Invite gate not enumeration-safe / codes guessable or reusable" (~lines 66-81) — SecureRandom + hashed + single-use atomic claim + generic error (drives D-06/D-07/D-10); §"Security Mistakes" (self-invite/enumeration rows, ~lines 155-158); §""Looks Done But Isn't" Checklist" invite rows (~lines 173-174, 177)
- `.planning/research/FEATURES.md` — invite-required signup + referral attribution rows (~lines 20, 27, 39, 64-67); anti-feature "member-generated invite quotas" deferral (~line 39)
- `.planning/research/STACK.md` / `.planning/research/SUMMARY.md` — v2.2 overview; invite gate + referral attribution scope and hashed-single-use reuse

### Prior phase context (patterns this phase reuses)
- `.planning/phases/15-age-verification/15-CONTEXT.md` — the register-time gate composition this phase extends (age gate first, invite second); the seam/config + enumeration-safe + distinct-`code` conventions to mirror
- `.planning/PROJECT.md` §"Key Decisions" — "Hashed single-use expiring tokens for all email flows" (Phases 10-12) and "Provider-abstracted `EmailSender` seam" — the hashed single-use claim discipline reused for codes (D-06/D-07)

### In-repo code (read before modifying)
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` — `register(request)` (returns Unit); insert invite validate after `ageVerifier.requireAdult(...)` (line ~39) and invite consume + referral record around/after `userRepository.save(user)` (line ~52), atomically (D-08)
- `src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt` — `RegisterRequest` (lines ~9-19, already has `dateOfBirth`); add optional `inviteCode` (D-08)
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` — `POST /api/auth/register` (201); contract unchanged except new field
- `src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt` — public-endpoint whitelist (lines ~27-34); add the admin-invite route to `permitAll` (D-03), guarded by the shared secret in-controller
- `src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt` + `GlobalExceptionHandler.kt` — add the invite exception → **403 + generic `code`** mapping, modelled on `EmailNotVerifiedException` → 403 `EMAIL_NOT_VERIFIED` (D-10)
- `src/main/kotlin/com/catspell/api/moderation/event/ReportNotificationListener.kt` (line ~23) + `src/main/resources/application.yml` (`app.report.operator-email`, lines ~42-45) — the `@Value`-backed `app.*` config-key convention to mirror for `app.invite.enabled` / `app.invite.admin-token`
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` `hashToken(...)` (lines ~205-209) — the exact SHA-256-at-rest helper pattern to reuse for hashing invite codes
- `src/main/resources/application.yml` §`app:` (lines ~38-45) — where `app.invite.*` keys are added
- `src/main/resources/db/migration/V22__move_date_of_birth_to_users.sql` — latest migration; the new migration is **V23**, never edit V1–V22
- `src/main/kotlin/com/catspell/api/moderation/` — reference for a new domain package layout (controller/service/model + event); mirror for the new `invite/` package (per ARCHITECTURE.md structure)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AuthService.register` composition slot** — age gate already runs first (Phase 15), the exact place to insert the invite check with no reordering (research Pattern 2, D-08).
- **`AuthService.hashToken` (SHA-256) + v2.1 atomic single-use claim** (`markUsed` conditional-update pattern in `resetPassword`/`verifyEmail`) — the exact discipline to reuse for hashing invite codes and enforcing single-use consumption (D-06/D-07).
- **`EmailNotVerifiedException` → 403 `EMAIL_NOT_VERIFIED`** and the Phase 15 under-age → 422 mappings in `GlobalExceptionHandler` — templates for the generic invite → 403 error (D-10).
- **`app.report.operator-email` / `app.age.minimum-age` `@Value` config keys** — the convention for `app.invite.enabled` and `app.invite.admin-token` (D-03).
- **`SecurityConfig` public-endpoint whitelist** — the three-place/`permitAll` pattern for exposing the admin-invite route while keeping the app JWT-only elsewhere (D-03).
- **`moderation/` domain package** (controller/service/model/event) — layout template for the new `invite/` package.

### Established Patterns
- Package-per-domain; Flyway append-only migrations (next is **V23**); JPA entities + Spring Data repositories; RFC 7807 error bodies with a machine-readable `code`; Bean Validation on request DTOs; `@Transactional` service methods; enumeration-safe generic responses; `@Value` custom `app.*` config keys (no `@ConfigurationProperties`, per AGENTS.md — the "Unknown property" IDE warning is cosmetic).
- Register is a **register-time ordered-guard** composition (age gate already there); the invite gate is the second guard, conditional on `app.invite.enabled`.

### Integration Points
- `AuthService.register` → `InviteService.validate(inviteCode)` (when gated) after the age gate; → `InviteService.consume(invite, newUser)` + referral record atomically with user creation.
- New admin endpoint (`InviteController` / admin) → `InviteService.create(referrerUserId?)` → returns the raw code once (D-06).
- `SecurityConfig` → whitelist the admin-invite route (D-03).
- `GlobalExceptionHandler` → new invite exception → 403 generic mapping (D-10).
- Flyway **V23** → create `invites` (hashed code, single-use state, nullable `referrer_user_id`) + `referrals` (referrer→invitee, invite ref, timestamp).

</code_context>

<specifics>
## Specific Ideas

- The gate must be a **true server-side gate**: flipping `app.invite.enabled=false` must fully open signup with no invite logic running; flipping it on must require a valid unconsumed code.
- Codes must be **indistinguishable on failure** — invalid, consumed, and missing-when-gated all return one identical generic 403.
- Codes are **hashed at rest** and the raw value is shown **once** on creation (no later retrieval).
- **Single-use** enforced by an atomic claim (no read-check-write race), reusing the v2.1 pattern.
- Referral attribution is **recorded at consumption**, and only when the invite actually has a referrer — operator bootstrap codes correctly produce no referral row.
- No admin role is introduced; the admin surface is a **shared-secret-guarded public route**.

</specifics>

<deferred>
## Deferred Ideas

- **Member-generated invites, per-member quotas, and referral rewards** — explicitly out of scope; only operator-issued codes this phase. The nullable `referrer_user_id` (D-11) is the forward-compatible hook.
- **Invite code expiry / TTL** (`expires_at` + `app.invite.ttl-days`) — deferred (D-04); no expiry this phase.
- **Operator revocation of unused codes** — deferred (D-05); single-use + controlled issuance limits leak impact.
- **Waitlist → invite conversion + emailing the code/link to the invitee** — Phase 17 (reuses `InviteService.create` and `EmailSender`).
- **Referral tracking while the gate is OFF (public mode)** — declined (D-09); public mode ignores codes entirely.
- **A general admin role / RBAC system** — declined this phase (D-03); a static shared-secret guard is sufficient for operator-only issuance.
- **Batch code issuance** — declined (D-02); one code per call.

None else — discussion stayed within phase scope.

</deferred>

---

*Phase: 16-invite-only-access-referral*
*Context gathered: 2026-09-29*
