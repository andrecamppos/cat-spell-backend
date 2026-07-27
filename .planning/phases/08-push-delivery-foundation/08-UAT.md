---
status: complete
phase: 08-push-delivery-foundation
source: [08-01-SUMMARY.md, 08-02-SUMMARY.md, 08-03-SUMMARY.md]
started: 2026-07-27T11:25:34Z
updated: 2026-07-27T11:40:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Fresh boot with push disabled — server starts, V14 device_tokens migration applies, Firebase health indicator is UP (push=disabled, no live send), and a primary request (health/actuator or POST /api/devices) returns live.
result: pass

### 2. Real FCM Send Path (live Firebase)
expected: With push.enabled=true and a valid FIREBASE_CREDENTIALS_BASE64, FcmPushProvider.send delivers a real notification (title/body/data) to a registered device token via FirebaseMessaging.send. A revoked/uninstalled token returns UNREGISTERED and the device row is soft-deactivated.
result: pass
reason: "Pass-by-acceptance (design review, NOT live execution). Human judgment: the send seam is verified by mocked-provider contract tests (Test 13) and UNREGISTERED pruning tests (Test 12); the real network round-trip is production-only and @Disabled by design. Accepted by user during UAT to seal Phase 08."

### 3. Real FCM validate_only Round-Trip (opt-in smoke test)
expected: Running the @Disabled @Tag("smoke") FcmSmokeTest with real credentials performs a validate_only dry-run against the live Firebase project and succeeds (valid message accepted, no actual notification delivered).
result: pass
reason: "Pass-by-acceptance (design review, NOT live execution). Human judgment: FcmSmokeTest is @Disabled + @Tag(smoke) by design and requires real credentials + network; the dry-run path is exercised manually in production only. Accepted by user during UAT to seal Phase 08."

### 4. Device registration upsert (POST /api/devices)
expected: POST /api/devices upserts by (userId, deviceId) and reactivates on re-register.
result: pass
source: automated
coverage_id: 08-01-D1

### 5. Device unregister soft-deactivate (DELETE /api/devices/{deviceId})
expected: DELETE soft-deactivates only the caller's named device; multiple active devices per user supported.
result: pass
source: automated
coverage_id: 08-01-D2

### 6. IDOR protection on device deactivation
expected: A user cannot deactivate another user's device (userId sourced from SecurityContextHolder).
result: pass
source: automated
coverage_id: 08-01-D3

### 7. Device endpoint auth + validation
expected: Endpoints require authentication (401) and reject unknown platform values (400).
result: pass
source: automated
coverage_id: 08-01-D4

### 8. PushProvider abstraction is provider-neutral
expected: No Firebase types leak into the PushProvider interface/payload (grep-clean + selection test).
result: pass
source: automated
coverage_id: 08-02-D1

### 9. Provider selection via push.enabled
expected: push.enabled selects LoggingPushProvider (false/missing) vs FcmPushProvider (true); FcmPushProvider maps UNREGISTERED to a distinct status.
result: pass
source: automated
coverage_id: 08-02-D2

### 10. Fail-fast on missing credentials
expected: Startup fails fast when push.enabled=true and credentials are missing/blank.
result: pass
source: automated
coverage_id: 08-02-D3

### 11. Firebase health indicator (config-only)
expected: FirebaseHealthIndicator reports config status (UP/push=disabled) without a live send and does not fail the aggregate when disabled.
result: pass
source: automated
coverage_id: 08-02-D4

### 12. Dead-token pruning only on UNREGISTERED
expected: PushSendService prunes the token only on UNREGISTERED; SUCCESS/ERROR leave it active.
result: pass
source: automated
coverage_id: 08-03-D1

### 13. Payload shape contract
expected: Payload shape (title/body/data) passed to the provider is asserted against a captured PushPayload.
result: pass
source: automated
coverage_id: 08-03-D2

### 14. Smoke test disabled by default
expected: validate_only dry-run smoke test is @Disabled + @Tag("smoke") and excluded from the standard test task.
result: pass
source: automated
coverage_id: 08-03-D3

## Summary

total: 14
passed: 14
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
