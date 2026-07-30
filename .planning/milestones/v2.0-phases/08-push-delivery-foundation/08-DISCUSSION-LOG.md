# Phase 8: Push Delivery Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-17
**Phase:** 8-Push Delivery Foundation
**Areas discussed:** Device token API contract, Dead & unregistered token handling, Firebase credentials & local dev, FCM client & dry-run verification

---

## Device Token API Contract

| Option | Description | Selected |
|--------|-------------|----------|
| REST /api/devices + platform | POST /api/devices (token, deviceId, platform) upsert by (userId, deviceId); DELETE /api/devices/{deviceId}. Capture platform enum now for APNs-readiness. | ✓ |
| REST /api/devices, no platform yet | Same endpoints but omit platform until APNs is built (YAGNI). | |
| Nest under /api/push/tokens | POST/DELETE /api/push/tokens namespace with platform. | |

**User's choice:** REST /api/devices + platform (recommended)
**Notes:** Platform enum scoped to ANDROID/IOS only — web push is out of scope, so no WEB value.

---

## Dead & Unregistered Token Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Soft-deactivate both | active=false + deactivatedAt for both logout and UNREGISTERED; sends filter active=true; re-register reactivates. | ✓ |
| Hard-delete both | DELETE row on logout and UNREGISTERED. Simplest storage, loses history. | |
| Mixed | Hard-delete on logout, soft-deactivate on UNREGISTERED. | |

**User's choice:** Soft-deactivate both (recommended)
**Notes:** Preserves audit trail / churn visibility; re-registration re-activates via upsert.

---

## Firebase Credentials & Local Dev

| Option | Description | Selected |
|--------|-------------|----------|
| Env base64 JSON + no-op local provider | Base64 env var creds; push.enabled flag: fail-fast when on, no-op logging provider when off (local default). | ✓ |
| File path + no-op local provider | Service-account JSON file path + same push.enabled flag / no-op provider. | |
| Always require real creds | No no-op provider; every env needs real Firebase; fail-fast always. | |

**User's choice:** Env base64 JSON + no-op local provider (recommended)
**Notes:** 12-factor, matches existing .env/S3 config style; devs run with zero Firebase setup.

---

## FCM Client & Dry-Run Verification

| Option | Description | Selected |
|--------|-------------|----------|
| Firebase Admin SDK + tagged dry-run test | firebase-admin behind PushProvider; mocked contract tests in CI; validate_only as @Tag("smoke") disabled by default. | ✓ |
| Raw HTTP v1 client + tagged dry-run test | Thin WebClient/RestClient + Google auth lib; no firebase-admin dependency. | |
| Admin SDK + dry-run in CI (creds-gated) | Admin SDK with validate_only wired into CI conditionally on a creds secret. | |

**User's choice:** Firebase Admin SDK + tagged dry-run test (recommended)
**Notes:** Admin SDK handles HTTP v1 auth/token refresh + native dryRun; smoke test kept out of default CI to avoid needing a real Firebase secret.

---

## Claude's Discretion

- Entity/table name, column types, Flyway migration details.
- DTO/request-response field naming and validation annotations.
- Provider-neutral `PushPayload` model shape for `send(token, payload)`.
- Package placement (suggested `com.catspell.api.push`).
- No-op provider selection mechanism (Spring profile vs `@ConditionalOnProperty`).

## Deferred Ideas

- Rate limiting on the token-register endpoint (extend Bucket4j later if abuse seen).
- Direct APNs integration (seed `direct-apns-hardening`).
- Notification triggers, offline+inactive send, STOMP presence, collapse keys, async dispatch — Phase 9.
- Per-type toggles / quiet hours / Redis-shared presence — future requirements.
