---
phase: 08-push-delivery-foundation
plan: 01
subsystem: api
tags: [push, device-tokens, jpa, flyway, postgres, spring-security]

requires:
  - phase: 01-foundation
    provides: User entity, JWT auth, SecurityContextHolder principal pattern
provides:
  - device_tokens table (V14) with UNIQUE (user_id, device_id) upsert key
  - DeviceToken JPA entity + Platform enum (ANDROID/IOS)
  - DeviceTokenRepository (findByUserIdAndDeviceId, findAllByUserIdAndActiveTrue, findByToken)
  - DeviceTokenService (register upsert/reactivate, unregister soft-deactivate, deactivateToken)
  - Authenticated DeviceController (POST /api/devices, DELETE /api/devices/{deviceId})
affects: [push-delivery, push-triggers, phase-09]

tech-stack:
  added: []
  patterns:
    - "com.catspell.api.push module (model/service/controller) mirroring existing domain modules"
    - "SecurityContextHolder-derived userId (never from request body) for object-level authz"
    - "Soft-deactivate (active=false + deactivatedAt) instead of hard-delete for tokens"

key-files:
  created:
    - src/main/resources/db/migration/V14__create_device_tokens_table.sql
    - src/main/kotlin/com/catspell/api/push/model/DeviceToken.kt
    - src/main/kotlin/com/catspell/api/push/model/DeviceTokenRepository.kt
    - src/main/kotlin/com/catspell/api/push/model/PushDtos.kt
    - src/main/kotlin/com/catspell/api/push/service/DeviceTokenService.kt
    - src/main/kotlin/com/catspell/api/push/controller/DeviceController.kt
    - src/test/kotlin/com/catspell/api/push/DeviceTokenIntegrationTest.kt
  modified: []

key-decisions:
  - "D-01: device API under /api/devices (POST register/upsert, DELETE /{deviceId} unregister)"
  - "D-02: upsert key (userId, deviceId); client-supplied deviceId identifies the device"
  - "D-03: platform enum ANDROID/IOS only (no WEB)"
  - "D-04: soft-deactivate on unregister (active=false + deactivatedAt), no hard-delete"
  - "D-05: active-row filtering; re-register reactivates via upsert"
  - "POST returns 204 No Content for upsert idempotency"

patterns-established:
  - "Push module package layout: com.catspell.api.push.{model,service,controller}"
  - "Object-level authz: DELETE scoped to (callerUserId, deviceId) prevents IDOR"

requirements-completed: [PUSH-01, PUSH-02]

coverage:
  - id: D1
    description: "POST /api/devices upserts by (userId, deviceId) and reactivates on re-register"
    requirement: "PUSH-01"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/DeviceTokenIntegrationTest.kt#register creates active row"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/DeviceTokenIntegrationTest.kt#re-register same device updates token and reactivates"
        status: pass
    human_judgment: false
  - id: D2
    description: "DELETE /api/devices/{deviceId} soft-deactivates only the caller's named device; multiple active devices per user supported"
    requirement: "PUSH-02"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/DeviceTokenIntegrationTest.kt#unregister soft-deactivates only named device"
        status: pass
    human_judgment: false
  - id: D3
    description: "IDOR protection — a user cannot deactivate another user's device (userId from SecurityContextHolder)"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/DeviceTokenIntegrationTest.kt#IDOR user B cannot deactivate user A device"
        status: pass
    human_judgment: false
  - id: D4
    description: "Endpoints require authentication (401) and reject unknown platform values (400)"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/DeviceTokenIntegrationTest.kt#unauthenticated request rejected"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/catspell/api/push/DeviceTokenIntegrationTest.kt#unknown platform value rejected"
        status: pass
    human_judgment: false

duration: ~20 min
completed: 2026-07-17
status: complete
---

# Phase 8 Plan 1: Device Token Persistence & Registration API Summary

**Authenticated device-token registration API with `(userId, deviceId)` upsert, soft-deactivation, multi-device support, and IDOR-safe object-level authz backed by a Flyway V14 `device_tokens` table.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-17
- **Tasks:** 5
- **Files modified:** 7 created

## Accomplishments
- Flyway `V14__create_device_tokens_table.sql` with UNIQUE `(user_id, device_id)`, soft-deactivate columns, and a partial index on active rows
- `DeviceToken` JPA entity + `Platform` enum (ANDROID/IOS), matching the migration exactly (ddl-auto validate)
- `DeviceTokenService` with transactional register (upsert + reactivate), unregister (soft-deactivate), and `deactivateToken` (used by plan 03)
- Authenticated `DeviceController` sourcing userId from `SecurityContextHolder` (never the request body)
- 6 integration tests: create, reactivate-on-re-register, multi-device soft-deactivate, IDOR isolation, 401 unauthenticated, 400 unknown platform

## Task Commits

1. **Task 1: V14 device_tokens migration** - `69b4df5` (feat)
2. **Task 2: DeviceToken entity, Platform enum, repository** - `a24800a` (feat)
3. **Task 3: RegisterDeviceRequest DTO and DeviceTokenService** - `849e53a` (feat)
4. **Task 4: authenticated DeviceController** - `7d53e92` (feat)
5. **Task 5: integration tests** - `a565361` (test)

## Files Created/Modified
- `src/main/resources/db/migration/V14__create_device_tokens_table.sql` - device_tokens schema
- `src/main/kotlin/com/catspell/api/push/model/DeviceToken.kt` - entity + Platform enum
- `src/main/kotlin/com/catspell/api/push/model/DeviceTokenRepository.kt` - JPA repository
- `src/main/kotlin/com/catspell/api/push/model/PushDtos.kt` - RegisterDeviceRequest DTO
- `src/main/kotlin/com/catspell/api/push/service/DeviceTokenService.kt` - transactional service
- `src/main/kotlin/com/catspell/api/push/controller/DeviceController.kt` - REST controller
- `src/test/kotlin/com/catspell/api/push/DeviceTokenIntegrationTest.kt` - integration tests

## Decisions Made
None beyond the plan's D-01..D-05 (followed as specified). POST returns 204 for upsert idempotency (documented in plan).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- IDE-reported Kotlin lint errors ("kotlin.Unit incompatible version", "Unresolved reference: javaClass/mapOf/to") were spurious: the IDE's bundled analyzer is Kotlin 2.1.0 while the project builds with Kotlin 2.4.0. The real `./gradlew compileKotlin` and `./gradlew test` both succeeded. No action needed.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `DeviceTokenService.deactivateToken(token)` is ready for plan 03's UNREGISTERED prune path
- `DeviceTokenRepository.findByToken` in place for plan 03
- Ready for plan 08-02 (PushProvider abstraction)

---
*Phase: 08-push-delivery-foundation*
*Completed: 2026-07-17*
