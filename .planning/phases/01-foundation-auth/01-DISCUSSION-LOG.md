# Phase 1: Foundation & Auth - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2025-06-09
**Phase:** 1-Foundation & Auth
**Areas discussed:** Package structure, Token lifecycle, Registration flow, API error format

---

## Package Structure

### Q1: How should Kotlin packages be organized?

| Option | Description | Selected |
|--------|-------------|----------|
| Domain-first vertical slices | com.catspell.auth.controller/service/repository, each domain owns its full vertical | ✓ |
| Layer-first flat | com.catspell.controller.*, com.catspell.service.*, traditional Spring Boot layout | |
| Hybrid | Domain packages for business logic + shared package for cross-cutting concerns | |

**User's choice:** Domain-first vertical slices
**Notes:** Aligns with monolith-by-domain architecture from research

### Q2: What base package name?

| Option | Description | Selected |
|--------|-------------|----------|
| com.catspell | Short, matches the app name | |
| com.catspell.api | Adds 'api' layer since this is a backend-only project | ✓ |
| io.catspell | Modern convention (io prefix) | |

**User's choice:** com.catspell.api

### Q3: Where do cross-cutting concerns live?

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated common package | com.catspell.api.common.config, .security, .exception — explicit shared layer | ✓ |
| Top-level config package | com.catspell.api.config, .security — flat alongside domain packages | |
| You decide | Let Claude pick based on Spring Boot conventions | |

**User's choice:** Dedicated common package

### Q4: How granular should sub-packages be within each domain?

| Option | Description | Selected |
|--------|-------------|----------|
| Full sub-packages | controller/, service/, repository/, dto/, entity/ — clear separation | |
| Minimal sub-packages | controller/, service/, model/ (entities + DTOs together) | ✓ |
| Flat within domain | All classes in domain package directly | |

**User's choice:** Minimal sub-packages

---

## Token Lifecycle

### Q1: How long should the JWT access token live?

| Option | Description | Selected |
|--------|-------------|----------|
| 15 minutes | Short-lived, more secure, frequent refreshes | |
| 1 hour | Good balance for a dating app, fewer refresh calls | ✓ |
| You decide | Let Claude pick based on best practices | |

**User's choice:** 1 hour

### Q2: Refresh token strategy?

| Option | Description | Selected |
|--------|-------------|----------|
| Rotating refresh tokens (DB-stored) | Each refresh issues new token, old invalidated, detects theft | ✓ |
| Long-lived static refresh token (DB-stored) | Single token per session, simpler, no theft detection | |
| Stateless refresh token (no DB) | Signed JWT with long expiry, can't be revoked until expiry | |

**User's choice:** Rotating refresh tokens (DB-stored)

### Q3: Multi-device sessions?

| Option | Description | Selected |
|--------|-------------|----------|
| Allow multiple sessions | Each device gets own refresh token, all stay logged in | ✓ |
| Single active session | New login invalidates all previous tokens | |

**User's choice:** Allow multiple sessions

### Q4: Refresh token expiry?

| Option | Description | Selected |
|--------|-------------|----------|
| 30 days | Standard for mobile apps, month of inactivity | ✓ |
| 7 days | More conservative, weekly re-login if inactive | |
| 90 days | Very long session, risk of forgotten compromised devices | |

**User's choice:** 30 days

---

## Registration Flow

### Q1: Should register auto-return tokens?

| Option | Description | Selected |
|--------|-------------|----------|
| Register returns tokens | POST /auth/register → 201 with tokens, user immediately logged in | ✓ |
| Register then login separately | POST /auth/register → 201 (no tokens), must call login next | |
| You decide | Let Claude pick based on conventions | |

**User's choice:** Register returns tokens

### Q2: Email verification for v1?

| Option | Description | Selected |
|--------|-------------|----------|
| Skip for v1 | No verification, users start immediately | ✓ |
| Verify before use | Must verify email before using app | |
| Verify later (soft) | Can start but reminded to verify | |

**User's choice:** Skip for v1

### Q3: Registration fields?

| Option | Description | Selected |
|--------|-------------|----------|
| Email + password only | Minimal registration, profile creation separate | ✓ |
| Email + password + display name | Collect display name at registration | |

**User's choice:** Email + password only

### Q4: Password requirements?

| Option | Description | Selected |
|--------|-------------|----------|
| Minimum 8 characters only | NIST-aligned, length over complexity | ✓ |
| 8+ chars with complexity | Require uppercase, lowercase, number | |
| You decide | Let Claude follow security best practices | |

**User's choice:** Minimum 8 characters only

---

## API Error Format

### Q1: Error response format?

| Option | Description | Selected |
|--------|-------------|----------|
| RFC 7807 Problem Details | Standard format with Spring Boot 3.3 built-in support | ✓ |
| Custom error envelope | Own format with full control over shape | |
| You decide | Let Claude pick based on conventions | |

**User's choice:** RFC 7807 Problem Details

### Q2: Validation error detail?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, field-level errors | violations array with field + message | ✓ |
| Generic message only | Just 'Validation failed', no field breakdown | |
| You decide | Let Claude pick best approach | |

**User's choice:** Yes, field-level errors

### Q3: Error detail in production?

| Option | Description | Selected |
|--------|-------------|----------|
| Safe defaults | Vague auth errors, field-level validation, generic 500s | ✓ |
| Verbose always | Full details in all environments | |
| You decide | Let Claude apply security best practices | |

**User's choice:** Safe defaults

---

## Claude's Discretion

No areas deferred to Claude's discretion.

## Deferred Ideas

None — discussion stayed within phase scope.
