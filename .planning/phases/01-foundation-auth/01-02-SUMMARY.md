---
plan: 01-02
phase: 01-foundation-auth
status: complete
started: 2025-06-09T15:50:00Z
completed: 2025-06-09T16:05:00Z
---

# Plan 01-02 Summary: Refresh Token Rotation

## What Was Built

Rotating refresh tokens stored in PostgreSQL with theft detection and multi-device support. Completes the full JWT token lifecycle: register → login → access → refresh → new access + new refresh.

## Key Files Created/Modified

- `src/main/resources/db/migration/V2__create_refresh_tokens_table.sql` — Refresh tokens table with user FK, revoked flag, replacedBy tracking
- `src/main/kotlin/com/catspell/api/auth/model/RefreshToken.kt` — JPA entity
- `src/main/kotlin/com/catspell/api/auth/model/RefreshTokenRepository.kt` — JPA repository
- `src/main/kotlin/com/catspell/api/auth/model/AuthDtos.kt` — Added `RefreshRequest` DTO
- `src/main/kotlin/com/catspell/api/auth/service/AuthService.kt` — Added `refreshToken()`, `createRefreshToken()`, `revokeAllUserTokens()`
- `src/main/kotlin/com/catspell/api/auth/controller/AuthController.kt` — Added `POST /api/auth/refresh`
- `src/test/kotlin/com/catspell/api/auth/RefreshTokenIntegrationTest.kt` — 8 integration tests

## Decisions Implemented

- **D-06**: Rotating refresh tokens, theft detection via reuse
- **D-07**: 30-day refresh token expiry
- **D-08**: Multi-device sessions with independent refresh tokens

## Deviations

- **noRollbackFor on refreshToken()**: `@Transactional(noRollbackFor = [ResponseStatusException::class])` needed because theft detection must commit the revocation of all tokens before throwing the exception.

## Self-Check: PASSED

- [x] V2 migration creates refresh_tokens table
- [x] Register returns refresh token (not empty string)
- [x] Login returns refresh token
- [x] POST /api/auth/refresh returns new token pair
- [x] Old refresh token rejected after rotation
- [x] Reuse of revoked token revokes all user tokens (theft detection)
- [x] Expired tokens rejected
- [x] Multi-device sessions work independently
- [x] All 18 tests pass
