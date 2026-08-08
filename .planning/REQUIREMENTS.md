# Requirements: Cat Spell Backend

**Defined:** 2026-08-07
**Milestone:** v2.1 Account Recovery & Email Verification
**Core Value:** Cat-preferred discovery — cat cards for cat owners, human cards for cat lovers without cats.

## v1 Requirements

Requirements for milestone v2.1. Each maps to a roadmap phase.

### Email Infrastructure

- [x] **EMAIL-01**: System sends transactional email through a provider-abstracted `EmailSender` seam (concrete provider swappable, stubbed in tests)
- [x] **EMAIL-02**: Email sending is environment-configurable — provider API key via env, with a no-op/logging provider used for local dev and integration tests (no real network sends in tests)

### Password Recovery

- [ ] **RECOV-01**: User can request a password reset by submitting their email address
- [x] **RECOV-02**: User receives an email containing a single-use, time-limited reset link
- [ ] **RECOV-03**: User can set a new password by submitting a valid reset token and new password
- [x] **RECOV-04**: The forgot-password endpoint returns an identical generic response whether or not the email is registered (no account enumeration)
- [x] **RECOV-05**: Reset tokens are stored hashed, are single-use (invalidated after use), and expire after a short TTL (~15–30 min); used or expired tokens are rejected
- [x] **RECOV-06**: On a successful password reset, all of the user's active sessions (refresh tokens) are revoked
- [x] **RECOV-07**: The forgot-password endpoint is rate-limited (per-email and/or per-IP) to prevent email-bombing abuse

### Email Verification

- [ ] **VERIFY-01**: User receives a verification email upon registration
- [ ] **VERIFY-02**: User can verify their email address by submitting the emailed verification token
- [ ] **VERIFY-03**: A user whose email is not verified cannot log in until they verify (hard gate)
- [ ] **VERIFY-04**: User can request the verification email be resent (rate-limited)
- [ ] **VERIFY-05**: Existing accounts are grandfathered as verified via data migration so no current user is locked out on rollout

### Account Credentials

- [ ] **ACCT-01**: A logged-in user can change their password by supplying their current password and a new password
- [ ] **ACCT-02**: On a successful password change, all of the user's other active sessions (refresh tokens) are revoked
- [ ] **ACCT-03**: A logged-in user can initiate an email change by supplying their current password and the new email
- [ ] **ACCT-04**: A newly requested email address must be verified before it becomes the account's active email
- [ ] **ACCT-05**: An email change is rejected if the new address already belongs to another account

## v2 Requirements

Deferred to a future release. Tracked but not in the current roadmap.

### Account Security

- **SEC2-01**: Multi-factor authentication (TOTP/authenticator)
- **SEC2-02**: Recovery via SMS / phone number
- **SEC2-03**: Account deletion and personal-data export (GDPR)

## Out of Scope

Explicitly excluded from v2.1. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| SMS / 2FA-based recovery | Email recovery is sufficient for this milestone; MFA is a separate future effort |
| Security questions | Weak security posture; email link is the modern standard |
| Account deletion / data export | Distinct compliance concern, not part of recovery |
| OAuth / social login | Already out of scope project-wide (email+password by design) |
| New rate-limiting infrastructure | Reuse existing Bucket4j rate limiting rather than build new infra |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| EMAIL-01 | Phase 10 | Complete |
| EMAIL-02 | Phase 10 | Complete |
| RECOV-01 | Phase 10 | Pending |
| RECOV-02 | Phase 10 | Complete |
| RECOV-03 | Phase 10 | Pending |
| RECOV-04 | Phase 10 | Complete |
| RECOV-05 | Phase 10 | Complete |
| RECOV-06 | Phase 10 | Complete |
| RECOV-07 | Phase 10 | Complete |
| VERIFY-01 | Phase 11 | Pending |
| VERIFY-02 | Phase 11 | Pending |
| VERIFY-03 | Phase 11 | Pending |
| VERIFY-04 | Phase 11 | Pending |
| VERIFY-05 | Phase 11 | Pending |
| ACCT-01 | Phase 12 | Pending |
| ACCT-02 | Phase 12 | Pending |
| ACCT-03 | Phase 12 | Pending |
| ACCT-04 | Phase 12 | Pending |
| ACCT-05 | Phase 12 | Pending |

**Coverage:**

- v1 requirements: 19 total
- Mapped to phases: 19 ✓
- Unmapped: 0

---
*Requirements defined: 2026-08-07*
*Last updated: 2026-08-07 — mapped all 19 requirements to Phases 10-12 (roadmap completed)*
