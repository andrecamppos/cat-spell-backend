# Phase 8: Push Delivery Foundation - Context

**Gathered:** 2026-07-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the foundation for push notifications: device token registration/storage/lifecycle and the FCM delivery infrastructure behind a `PushProvider` abstraction, plus a Firebase health indicator and provider-mocked tests.

**In scope:** PUSH-01 (register token, upsert by `(user_id, device_id)`), PUSH-02 (unregister on logout, multi-device), PUSH-03 (deactivate tokens FCM reports `UNREGISTERED`), PUSH-09 (`PushProvider` abstraction delivering via FCM HTTP v1, APNs-addable later without call-site changes), PUSH-11 (Firebase/FCM health indicator + fail-fast on missing credentials), PUSH-12 (mocked-provider integration tests + `validate_only` dry-run smoke test).

**Out of scope (Phase 9 or later):** Notification *triggers* (match/message → push), the "offline + inactive" send decision, STOMP presence/active-conversation tracking, collapse keys, async event dispatch, per-type preferences/toggles, quiet hours, direct APNs, web push.

</domain>

<decisions>
## Implementation Decisions

### Device Token API Contract
- **D-01:** REST endpoints under `/api/devices`. `POST /api/devices` registers/upserts (body: `token`, `deviceId`, `platform`); `DELETE /api/devices/{deviceId}` unregisters. Follows the existing `@RestController` + `SecurityContextHolder` userId-extraction pattern.
- **D-02:** Upsert key is `(userId, deviceId)` — a client-supplied `deviceId` identifies the device; re-registering the same device updates the token.
- **D-03:** Capture a `platform` enum now for real APNs-readiness (PUSH-09), scoped to `ANDROID` / `IOS` only — web push is out of scope, so no `WEB` value.

### Token Lifecycle (Dead & Unregistered Handling)
- **D-04:** Soft-deactivate in both cases — logout-unregister (PUSH-02) and FCM `UNREGISTERED` (PUSH-03) set `active = false` + a `deactivatedAt` timestamp rather than hard-deleting. Preserves audit trail / churn visibility.
- **D-05:** Send/query paths filter `WHERE active = true`. Re-registering the same `(userId, deviceId)` re-activates the row via the upsert.

### Firebase Credentials & Local Dev
- **D-06:** Service-account JSON supplied as a **base64-encoded env var** (12-factor, matches existing `.env`/`S3Config` style). No secrets in the repo.
- **D-07:** A `push.enabled` flag gates behavior. When `true`: fail-fast at startup if credentials are missing/invalid (PUSH-11). When `false` (local default): a no-op **logging `PushProvider`** logs sends instead of calling FCM, so devs run with zero Firebase setup.
- **D-08:** Firebase health indicator reports configuration status, following the existing `HealthIndicator` pattern (S3/WebSocket/DB).

### FCM Client & Dry-Run Verification
- **D-09:** Use the **`firebase-admin` SDK** behind the `PushProvider` interface (handles HTTP v1 auth/token refresh, native `Message` + `dryRun`). All call sites depend only on the `PushProvider` abstraction.
- **D-10:** Mocked-provider contract tests run in normal CI (PUSH-12) — assert payload shape and token-pruning behavior against a mocked provider.
- **D-11:** The `validate_only` dry-run is a separate `@Tag("smoke")` test, **disabled by default**, run manually/opt-in when real Firebase credentials are present. Not wired into standard CI (no real creds secret in CI).

### Claude's Discretion
- Exact entity/table name, column types, and Flyway migration details.
- DTO/request-response field naming and validation annotations.
- `PushPayload` internal model shape (the abstraction's `send(token, payload)` contract) — provided it is provider-neutral so APNs can be added later.
- Package placement (suggested new `com.catspell.api.push` module mirroring existing domain modules).
- Whether the no-op provider is selected by Spring profile vs `@ConditionalOnProperty` on `push.enabled`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & Milestone Scope
- `.planning/REQUIREMENTS.md` — v2.0 requirement definitions; Phase 8 owns PUSH-01, 02, 03, 09, 11, 12. Traceability table maps each to its phase.
- `.planning/ROADMAP.md` §"Phase 8: Push Delivery Foundation" — goal and 4 success criteria.
- `.planning/PROJECT.md` §"Current Milestone" + "Key Decisions" — locked milestone-level decisions (FCM-only + "offline+inactive"; push preferences all-on, no toggle in v1).

### Existing Code Patterns To Follow
- `src/main/kotlin/com/catspell/api/common/health/WebSocketHealthIndicator.kt` — `HealthIndicator` pattern for the Firebase health indicator (PUSH-11).
- `src/main/kotlin/com/catspell/api/profile/config/S3Config.kt` — `@Configuration` + `@Value` external-client bean pattern for FirebaseApp/provider wiring.
- `src/main/kotlin/com/catspell/api/chat/controller/ConversationController.kt` — `@RestController` + `SecurityContextHolder` userId extraction for the devices controller.
- `src/main/kotlin/com/catspell/api/auth/model/User.kt` — JPA entity conventions (UUID id, `equals`/`hashCode`).
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` — `@Service` + repository + `@Transactional` service pattern.

_No external ADRs/specs exist for this phase — requirements are fully captured in REQUIREMENTS.md and the decisions above._

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`HealthIndicator` implementations** (`common/health/`): direct template for `FirebaseHealthIndicator`.
- **`S3Config`** external-client bean: template for a Firebase/`PushProvider` `@Configuration`, including `@Value` credential injection and an env-based wiring approach.
- **`ConversationController` + SecurityContext extraction**: template for the authenticated `DevicesController`.
- **`.env.example` / storage config style**: base64 credential env var slots in cleanly here.

### Established Patterns
- Domain modules under `com.catspell.api.<domain>` with `controller` / `service` / `model` / `config` subpackages — a new `push` (or `device`) module should mirror this.
- Flyway migrations for schema (no H2); Testcontainers-based integration tests (PostgreSQL + PostGIS + MinIO) — the device-token table needs a Flyway migration and the provider tests fit the existing integration-test harness.
- Spring `@Value` config injection with sensible defaults.

### Integration Points
- **Auth/logout flow** (`auth` module): unregister-on-logout (PUSH-02) — the `DELETE /api/devices/{deviceId}` call originates from the client on logout; no server-side coupling required beyond the endpoint.
- **Actuator health group**: Firebase indicator joins the existing S3/WebSocket/DB indicators.
- **`PushProvider` abstraction**: the seam Phase 9 will call into for match/message triggers — must stay provider-neutral.

</code_context>

<specifics>
## Specific Ideas

- Platform enum deliberately limited to `ANDROID`/`IOS` (no `WEB`) — future-proofs for APNs without modeling out-of-scope web push.
- No-op/logging provider is the local-dev default so the app boots and the full flow is exercisable without a Firebase project.
- `validate_only` smoke test kept out of the default CI run to avoid requiring a real Firebase service-account secret in CI.

</specifics>

<deferred>
## Deferred Ideas

- **Rate limiting on the token-register endpoint** — raised as a possible extra gray area; not pursued. Existing Bucket4j rate limiting could be extended later if abuse is observed. Not required for Phase 8.
- **Direct APNs integration** — deferred (seed `direct-apns-hardening`); the `platform` field and `PushProvider` abstraction leave room for it.
- **Notification triggers, offline+inactive send decision, STOMP presence, collapse keys, async dispatch** — Phase 9.
- **Per-type toggles / quiet hours / shared-store (Redis) presence** — future requirements per REQUIREMENTS.md.

</deferred>

---

*Phase: 8-Push Delivery Foundation*
*Context gathered: 2026-07-17*
