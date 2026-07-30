---
phase: 8
slug: push-delivery-foundation
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-07-27
---

# Phase 8 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Client → `/api/devices` | Authenticated REST surface for device-token registration/unregistration | FCM registration token, deviceId, platform (per-user, JWT-authenticated) |
| Backend → Firebase Cloud Messaging | Outbound push delivery via `firebase-admin` SDK | Service-account credential (send rights), notification payload, device token |
| Env/config → Application | Firebase service-account credential injection at startup | base64 service-account JSON (secret) |
| `/actuator/health` → Operator | Actuator health exposure of Firebase configuration status | Coarse push status (enabled/disabled) only |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-08-01 | Elevation of Privilege (IDOR / BOLA) | `DeviceController` / `DeviceTokenService` | high | mitigate | userId sourced from `SecurityContextHolder` (never request body); DELETE scoped to `findByUserIdAndDeviceId(callerUserId, deviceId)`. Verified: `DeviceTokenIntegrationTest#IDOR user B cannot deactivate user A device` | closed |
| T-08-02 | Information Disclosure (data at rest) | `device_tokens` table | low | accept | FCM tokens are delivery-routing identifiers, not credentials; per-user scope with `ON DELETE CASCADE`. No encryption required at L1 | closed |
| T-08-03 | Information Disclosure (credential exposure) | `FirebaseConfig` / `.env.example` | high | mitigate | Service-account JSON only via base64 env var `FIREBASE_CREDENTIALS_BASE64`; `.env.example` ships empty slot; no literal in source; credential never logged (`GoogleCredentials.fromStream`, no `log` of value) | closed |
| T-08-04 | Information Disclosure (health/info) | `FirebaseHealthIndicator` / actuator | low | mitigate | `application.yml` sets `show-details`/`show-components: when-authorized`; indicator exposes only coarse `push=enabled/disabled`, never the credential | closed |
| T-08-05 | Denial of Service (silent misconfiguration) | `FirebaseConfig` | medium | mitigate | Fail-fast at startup: `check(credentialsBase64.isNotBlank())` aborts context init on missing credential when `push.enabled=true` | closed |
| T-08-06 | Denial of Service (erroneous mass-deactivation) | `PushSendService` | medium | mitigate | Deactivates ONLY on `PushSendStatus.UNREGISTERED`; `SUCCESS`/`ERROR` leave token active; no batch path. Verified: `TokenPruningTest` (ERROR/SUCCESS stay active, UNREGISTERED prunes) | closed |
| T-08-07 | Information Disclosure (smoke test creds in CI) | `FcmSmokeTest` | low | mitigate | `@Tag("smoke")` + `@Disabled`; reads creds from env only when opted in; default `test` task never runs it | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-08-01 | T-08-02 | FCM registration tokens stored in plaintext. They are delivery-routing identifiers (not access credentials), scoped per user with `ON DELETE CASCADE`. Plaintext storage is acceptable at ASVS L1; no encryption-at-rest required for this data class. | Phase 8 threat model (plan 08-01) | 2026-07-27 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-07-27 | 7 | 7 | 0 | gsd-secure-phase (L1 grep-depth, State B from artifacts) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-07-27
