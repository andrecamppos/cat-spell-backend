# Phase 8: Push Delivery Foundation - Research

**Researched:** 2026-07-17
**Question answered:** "What do I need to know to PLAN this phase well?"

## Scope Recap

Phase 8 delivers device-token lifecycle (register/unregister/deactivate) plus the FCM
delivery seam behind a `PushProvider` abstraction, a Firebase health indicator, and
provider-mocked tests. Requirements owned: **PUSH-01, PUSH-02, PUSH-03, PUSH-09, PUSH-11,
PUSH-12**. Triggers, send-decision, presence, and async dispatch are Phase 9 — Phase 8 only
builds the plumbing that Phase 9 calls into.

---

## 1. FCM Delivery via `firebase-admin` (PUSH-09)

**Library:** `com.google.firebase:firebase-admin:9.9.0` (latest, released 2026-05-14; Apache-2.0).
The Admin SDK wraps FCM HTTP v1 and handles OAuth2 token minting + refresh internally from a
service-account credential — no manual JWT/token-refresh code needed. This directly satisfies
D-09 ("SDK handles HTTP v1 auth/token refresh").

**Initialization** (mirrors the `S3Config` `@Configuration` + `@Value` bean pattern):
```
FirebaseApp.initializeApp(
    FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ByteArrayInputStream(decodedJson)))
        .build()
)
FirebaseMessaging.getInstance()   // singleton messaging client
```

**Send API:**
- `FirebaseMessaging.send(message)` → real send, returns a message-ID string.
- `FirebaseMessaging.send(message, dryRun: Boolean)` → when `dryRun = true`, FCM runs all
  validations and emulates the send **without delivering** and **without a real creds guarantee
  for APNs tokens**. This is the `validate_only` path for PUSH-12's smoke test. Confirmed in the
  Admin SDK Javadoc: "The dryRun option is useful for determining whether an FCM registration
  has been deleted."

**Message construction** (provider-neutral payload maps into the native builder inside the FCM
implementation only — call sites never see `Message`):
```
Message.builder()
    .setToken(token)
    .setNotification(Notification.builder().setTitle(t).setBody(b).build())
    .putAllData(dataMap)          // deep-link / type metadata as String→String
    .build()
```

**Abstraction seam (D-09):** define a `PushProvider` interface with a single provider-neutral
method, e.g. `send(token: String, payload: PushPayload): PushResult`. `PushPayload` is an
internal model (title/body + `data: Map<String,String>`) with NO Firebase types, so APNs can be
added later without touching call sites. Phase 9 will call this seam from event handlers.

### Two implementations, selected by config (D-07)
- **`FcmPushProvider`** — real FCM via `FirebaseMessaging`. Active when `push.enabled=true`.
- **`LoggingPushProvider`** (no-op) — logs the send instead of calling FCM. Active when
  `push.enabled=false` (local-dev default), so the app boots and the whole flow is exercisable
  with zero Firebase setup.
- **Selection mechanism (Claude's Discretion, D-44 note):** `@ConditionalOnProperty(name =
  "push.enabled", havingValue = "true")` on the FCM bean + `matchIfMissing`-style fallback bean
  for the logging provider is the cleaner fit than Spring profiles (keeps local/CI on the no-op
  without a profile flag). Recommended over profiles.

---

## 2. Dead-Token Handling (PUSH-03) — the key correctness detail

When a send targets a token that FCM has dropped, the Admin SDK throws
`FirebaseMessagingException`. Detect the dead-token case via:
```
ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED   // HTTP 404
```
`UNREGISTERED` = "App instance was unregistered from FCM ... the token is no longer valid."
On this specific code, the send path must **soft-deactivate** the token (D-04): set
`active = false` + `deactivatedAt = now()`, NOT hard-delete. Other error codes
(`INVALID_ARGUMENT`, `SENDER_ID_MISMATCH`, transient `UNAVAILABLE`/`INTERNAL`) must NOT
deactivate — only `UNREGISTERED` prunes.

**Phase-8 boundary note:** there is no trigger wiring in Phase 8, so the *token-pruning behavior*
is exercised via PUSH-12's mocked-provider tests (mock throws `UNREGISTERED` → assert row
deactivated) and the opt-in `validate_only` smoke test — not via a real match/message flow.
The pruning logic itself should live where the provider result is interpreted (a thin
send-orchestration/service method that Phase 9 will reuse), so keep it out of the raw
`PushProvider` implementation.

---

## 3. Device Token Persistence (PUSH-01, PUSH-02)

**Migration:** next Flyway version is **`V14__create_device_tokens_table.sql`** (current head is
V13). Follow the established SQL style (`gen_random_uuid()` PK, `UUID ... REFERENCES users(id)
ON DELETE CASCADE`, `TIMESTAMPTZ` timestamps, explicit indexes).

Suggested schema (column names = Claude's Discretion):
```sql
CREATE TABLE device_tokens (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id      VARCHAR(255) NOT NULL,
    token          TEXT NOT NULL,
    platform       VARCHAR(16) NOT NULL,          -- ANDROID | IOS (D-03)
    active         BOOLEAN NOT NULL DEFAULT TRUE,  -- (D-04/D-05)
    deactivated_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_tokens_user_device UNIQUE (user_id, device_id)  -- upsert key (D-02)
);
CREATE INDEX idx_device_tokens_user_active ON device_tokens(user_id) WHERE active;
```

**Upsert (D-02, D-05):** the unique `(user_id, device_id)` constraint is the upsert key.
Re-registering the same device updates `token` and **re-activates** the row (`active=true`,
`deactivated_at=null`). Cleanest in JPA: `repository.findByUserIdAndDeviceId(...)` then update,
else insert — inside a `@Transactional` service method (mirrors `AuthService`). Avoids native
`ON CONFLICT` and keeps entity semantics.

**Entity:** JPA entity mirroring `User.kt` conventions — `@Id @GeneratedValue(UUID)`, nullable
`var id`, `equals`/`hashCode` on id. `platform` as `@Enumerated(EnumType.STRING)`.

**Repository:** `JpaRepository<DeviceToken, UUID>` with derived queries:
`findByUserIdAndDeviceId`, `findAllByUserIdAndActiveTrue`, and a token lookup for the
UNREGISTERED prune path.

**Controller (D-01):** `@RestController @RequestMapping("/api/devices")`, userId extracted via
`SecurityContextHolder.getContext().authentication.principal as String` → `UUID.fromString(...)`
(identical to `ConversationController.extractUserId()`).
- `POST /api/devices` — body `{ token, deviceId, platform }`, upsert → `200`/`201`.
- `DELETE /api/devices/{deviceId}` — soft-deactivate that device for the caller → `204`.
- Multi-device (PUSH-02) falls out naturally: rows are keyed per `(userId, deviceId)`, so a
  user can have many active rows; delete only affects the named device.

**DTO validation:** `data class` request with `@field:NotBlank` on `token`/`deviceId` and a
`platform` enum bound as a String → invalid values surface via the existing
`GlobalExceptionHandler` (`MethodArgumentNotValid` → 400 ProblemDetail). An unknown platform
enum value deserializes to a Jackson error → handled as 400 by the existing readable-message
handler.

---

## 4. Credentials, Config & Fail-Fast (PUSH-11, D-06/D-07/D-08)

**Credential supply (D-06):** service-account JSON as a **base64-encoded env var** (matches the
`.env` / `S3_*` style). Add a slot to `.env.example` (e.g. `PUSH_ENABLED=false` and
`FIREBASE_CREDENTIALS_BASE64=`). In `application.yml` add a `push:` block:
```yaml
push:
  enabled: ${PUSH_ENABLED:false}
  firebase:
    credentials-base64: ${FIREBASE_CREDENTIALS_BASE64:}
```

**Fail-fast (PUSH-11):** when `push.enabled=true`, the Firebase config bean must throw at startup
if the base64 credential is missing/blank or fails to decode/parse (Google
`GoogleCredentials.fromStream` throws on malformed JSON) — an unhandled exception in a
`@Configuration` bean aborts context startup, which is the desired fail-fast. When
`push.enabled=false`, the FCM bean is never created, so no credential is needed.

**Health indicator (PUSH-11, D-08):** `FirebaseHealthIndicator : HealthIndicator` in
`common/health/` (same package as `S3HealthIndicator`/`WebSocketHealthIndicator`). Reports
**configuration status**, not a live send:
- `push.enabled=false` → `Health.up().withDetail("push", "disabled")` (or `status="disabled"` /
  UP with a detail — must not fail the aggregate when intentionally off).
- `push.enabled=true` + FirebaseApp initialized → `Health.up().withDetail("push","enabled")`.
- The component appears under `/actuator/health` (authorized view) exactly like `s3`/`webSocket`.
  Register bean as `@Component` named `firebaseHealthIndicator` → component key `firebase`.

**Do NOT** perform a real FCM round-trip in the health check (avoids per-scrape cost + external
dependency in the readiness signal). Config-status only, matching the phrase in ROADMAP success
criterion 4 ("reports status").

---

## 5. Testing Strategy (PUSH-12, D-10/D-11)

**Harness:** `@SpringBootTest @AutoConfigureMockMvc` extending `BaseIntegrationTest`
(Testcontainers PostGIS + MinIO already wired; `@DynamicPropertySource` sets datasource/S3).
Auth token obtained via the existing register-and-extract-JWT helper pattern
(`HealthEndpointIntegrationTest`).

**Mocked-provider contract tests (D-10, normal CI):**
- Register/unregister happy paths through `POST`/`DELETE /api/devices` (assert DB rows,
  active flags, multi-device, re-register re-activation, upsert-not-duplicate).
- **Payload shape** — mock the `PushProvider` (MockK is available: `io.mockk:mockk:1.13.11`),
  capture the `PushPayload`, assert title/body/data map contents.
- **Token pruning** — mock provider throws the UNREGISTERED-equivalent result/exception; assert
  the corresponding `device_tokens` row flips `active=false` + `deactivated_at` set.
- Override the `PushProvider` bean with a mock via `@MockkBean`/`@TestConfiguration` so no real
  Firebase is needed in CI (aligns with `push.enabled=false` default → logging provider replaced
  by a mock for assertions).

**`validate_only` dry-run smoke test (D-11):** a separate `@Tag("smoke")` test, **disabled by
default** (JUnit `@Disabled` or tag excluded from the default `test` task), run manually when a
real `FIREBASE_CREDENTIALS_BASE64` is present. It calls the FCM provider with `dryRun=true`
against a real (or throwaway) token to confirm real auth + payload validity. Must NOT run in
standard CI (no real creds secret). Add a Gradle tag-exclusion or rely on `@Disabled` — the
plan should pick one explicitly.

**Health test:** extend the health-endpoint assertions to expect `components.firebase` present
and `status` UP/disabled depending on `push.enabled` in the test profile.

---

## 6. Package Placement & Integration Points

- New module **`com.catspell.api.push`** with `controller` / `service` / `model` / `config`
  subpackages, mirroring existing domain modules (`auth`, `chat`, `profile`). (CONTEXT suggests
  `push` or `device`; `push` groups provider + tokens together and reads well for Phase 9.)
- **Health group:** `FirebaseHealthIndicator` auto-joins the actuator health group next to
  `db`/`s3`/`webSocket` (no config change needed — `@Component` HealthIndicators are
  auto-registered; `management.endpoints.web.exposure.include: health` already set).
- **Logout coupling (PUSH-02):** none server-side beyond the `DELETE` endpoint — the client
  calls it on logout. No change to `AuthService`.
- **`PushProvider` seam:** the class Phase 9 will inject to send match/message pushes — keep it
  provider-neutral and free of send-decision logic.

---

## 7. Risks & Landmines

- **Deactivate-on-wrong-error:** only `MessagingErrorCode.UNREGISTERED` may prune. Deactivating
  on transient errors (`UNAVAILABLE`/`INTERNAL`) would silently kill live devices. Test both
  branches.
- **FirebaseApp double-init:** `FirebaseApp.initializeApp()` throws if called twice with the
  default name. Guard with `FirebaseApp.getApps().isEmpty()` or a named app, and scope to a
  single bean created only when `push.enabled=true`.
- **Health check must not hard-fail when push disabled** — otherwise local/CI `/actuator/health`
  goes DOWN by design. Report UP/"disabled".
- **ddl-auto is `validate`** — the entity MUST match the Flyway migration exactly (column names,
  nullability, enum storage) or context startup fails. Migration is the source of truth.
- **Smoke test leaking into CI** — if tagged wrong it will fail CI for lack of creds. Verify the
  default `test` task excludes `@Tag("smoke")` or the test is `@Disabled`.
- **`platform` enum** limited to `ANDROID`/`IOS` (no `WEB`) per D-03 — don't add WEB.

---

## Validation Architecture

Signals to sample so verification proves the phase goal, not just "code compiles":

| Dimension | Signal | How sampled |
|-----------|--------|-------------|
| Token registration (PUSH-01) | Row upserted by `(user_id, device_id)`; re-register updates token + reactivates | Integration test: POST twice same deviceId → 1 row, token updated, active=true |
| Unregister / multi-device (PUSH-02) | DELETE deactivates only named device; other devices stay active | Integration test: 2 devices, DELETE one → that row active=false, other active=true |
| Dead-token prune (PUSH-03) | UNREGISTERED result → `active=false` + `deactivated_at` set; other errors do NOT prune | Mocked-provider test: mock throws UNREGISTERED → row deactivated; mock throws UNAVAILABLE → row unchanged |
| Provider abstraction (PUSH-09) | Call sites depend only on `PushProvider`; no Firebase types leak | Source assertion: `PushProvider` interface has no Firebase imports; FCM types confined to `FcmPushProvider` |
| Provider selection (D-07) | `push.enabled=false` → `LoggingPushProvider` bean; `true` → `FcmPushProvider` | Context test with property override asserts active bean type |
| Fail-fast (PUSH-11) | `push.enabled=true` + missing/blank creds → context startup fails | Test: start context with enabled+no creds → `ApplicationContextException`/bean init failure |
| Health status (PUSH-11) | `/actuator/health` (authorized) shows `components.firebase` with correct status | MockMvc test asserts `components.firebase.status` |
| Dry-run auth (PUSH-12) | `send(message, dryRun=true)` succeeds against real creds | `@Tag("smoke")` disabled-by-default test, manual/opt-in |
| Payload shape (PUSH-12) | Captured `PushPayload` has expected title/body/data | MockK captured-arg assertion |

**Backstop (insufficient without real creds):** the `validate_only` smoke test cannot run in CI,
so real end-to-end FCM auth remains a manually-verified backstop — the plan must mark it as such
rather than claiming automated coverage.

---

## Sources

- Firebase Admin Java SDK — `FirebaseMessaging.send(Message, boolean dryRun)` Javadoc
  (dryRun = validation-only, detects deleted registrations).
- `MessagingErrorCode.UNREGISTERED` reference (HTTP 404, token no longer valid → prune).
- FCM Error Codes guide (`registration-token-not-registered` → remove token, stop sending).
- Maven Central: `com.google.firebase:firebase-admin:9.9.0` (latest).
- Codebase: `S3Config`, `S3HealthIndicator`, `WebSocketHealthIndicator`, `ConversationController`,
  `AuthService`, `User`, `RefreshTokenRepository`, `BaseIntegrationTest`,
  `HealthEndpointIntegrationTest`, `GlobalExceptionHandler`, `application.yml`, `.env.example`,
  Flyway `V1`–`V13`.

## RESEARCH COMPLETE
