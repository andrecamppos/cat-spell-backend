---
phase: 10-password-recovery
plan: 01
subsystem: api
tags: [email, spring-boot, kotlin, password-reset, provider-pattern]

# Dependency graph
requires:
  - phase: push-notifications
    provides: "PushProvider/LoggingPushProvider provider-seam pattern mirrored for email"
provides:
  - "EmailSender seam (interface + EmailMessage/EmailResult value objects + EmailSendStatus enum)"
  - "LoggingEmailSender no-op provider selected by default (email.enabled=false / matchIfMissing)"
  - "PasswordResetEmailRenderer producing in-repo HTML+text bodies with an env-configured deep link"
  - "email.enabled and app.reset-password-url config keys in application.yml + .env.example"
  - "EMAIL-01 contract test and EMAIL-02 provider-selection test"
affects: [password-recovery, forgot-password, transactional-email]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "EmailSender provider seam mirroring push/service (interface + value objects in one file)"
    - "No-op logging provider gated on ConditionalOnProperty with matchIfMissing=true as default"
    - "Backend-rendered email bodies via plain Kotlin string templates (no template engine)"

key-files:
  created:
    - src/main/kotlin/com/catspell/api/email/service/EmailSender.kt
    - src/main/kotlin/com/catspell/api/email/service/LoggingEmailSender.kt
    - src/main/kotlin/com/catspell/api/email/service/PasswordResetEmailRenderer.kt
    - src/test/kotlin/com/catspell/api/email/EmailSenderSelectionTest.kt
    - src/test/kotlin/com/catspell/api/email/EmailSenderContractTest.kt
  modified:
    - src/main/resources/application.yml
    - .env.example

key-decisions:
  - "No-op LoggingEmailSender is the default bean via matchIfMissing=true; no concrete network provider built (D-03 deferred)."
  - "Reset deep-link base comes only from app.reset-password-url config, never from an HTTP request header (host-header safe, D-01)."
  - "Email body rendered in-repo with Kotlin string templates (HTML + plain text); no Thymeleaf/template-engine dependency (D-02)."
  - "Recipient email masked in logs; raw reset token never logged."

patterns-established:
  - "Pattern 1: Message-delivery provider seam (interface + value objects + no-op default) mirrored from push/service."
  - "Pattern 2: In-repo string-template email rendering returning an EmailMessage value object."

requirements-completed: [EMAIL-01, EMAIL-02, RECOV-02]

coverage:
  - id: D1
    description: "LoggingEmailSender is the default (sole) EmailSender bean when email.enabled is unset (no network sender in dev/CI)."
    requirement: "EMAIL-02"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/email/EmailSenderSelectionTest.kt#logging sender selected when email disabled or missing"
        status: pass
    human_judgment: false
  - id: D2
    description: "EmailSender.send contract returns EmailResult SUCCESS without throwing or network I/O, including concurrent calls."
    requirement: "EMAIL-01"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/email/EmailSenderContractTest.kt#send returns SUCCESS without throwing"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/catspell/api/email/EmailSenderContractTest.kt#concurrent sends each return SUCCESS"
        status: pass
    human_judgment: false
  - id: D3
    description: "PasswordResetEmailRenderer builds HTML+text bodies with an env-configured deep link (host-header safe)."
    requirement: "RECOV-02"
    verification:
      - kind: unit
        ref: "./gradlew compileKotlin (source assertion: resetPasswordUrl used in link, no HttpServletRequest reference)"
        status: pass
    human_judgment: false

# Metrics
duration: 10min
completed: 2026-08-08
status: complete
---

# Phase 10 Plan 01: Email Seam Summary

**Reusable transactional-email seam with a no-op logging default and backend-rendered password-reset email, mirroring the existing push provider pattern — no network dependency in dev/CI.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-08-08T10:20:00Z
- **Completed:** 2026-08-08T10:30:16Z
- **Tasks:** 3 completed
- **Files modified:** 7 (5 created, 2 modified)

## Accomplishments
- Shipped the `EmailSender` seam (interface + `EmailMessage`/`EmailResult` value objects + `EmailSendStatus` enum) mirroring `push/service/PushProvider`.
- `LoggingEmailSender` is the default bean via `@ConditionalOnProperty(email.enabled, matchIfMissing=true)`; it masks the recipient, never logs the token, and does no network I/O.
- `PasswordResetEmailRenderer` renders HTML + plain-text bodies in-repo and assembles the deep link from the env-configured `app.reset-password-url` (never from a request header).
- Added `email.enabled` / `app.reset-password-url` config keys to `application.yml` and `EMAIL_ENABLED` / `RESET_PASSWORD_URL` to `.env.example`.
- EMAIL-01 (seam contract) and EMAIL-02 (no-op default, no network) proven by green tests.

## Task Commits

Each task was committed atomically:

1. **Task 1: EmailSender seam + no-op LoggingEmailSender** - `28e5780` (feat)
2. **Task 2: PasswordResetEmailRenderer + config keys** - `7eb944f` (feat)
3. **Task 3: EMAIL-01 contract + EMAIL-02 selection tests** - `271b638` (test)

**Plan metadata:** docs commit (see below)

## Files Created/Modified
- `src/main/kotlin/com/catspell/api/email/service/EmailSender.kt` - EmailSender interface + EmailMessage/EmailResult value objects + EmailSendStatus enum
- `src/main/kotlin/com/catspell/api/email/service/LoggingEmailSender.kt` - No-op logging sender, default bean, masks recipient
- `src/main/kotlin/com/catspell/api/email/service/PasswordResetEmailRenderer.kt` - Renders HTML+text reset email with env-configured deep link
- `src/main/resources/application.yml` - Added `email.enabled` and `app.reset-password-url` keys
- `.env.example` - Added `EMAIL_ENABLED` and `RESET_PASSWORD_URL` with comments
- `src/test/kotlin/com/catspell/api/email/EmailSenderSelectionTest.kt` - EMAIL-02 default-bean proof (ApplicationContextRunner)
- `src/test/kotlin/com/catspell/api/email/EmailSenderContractTest.kt` - EMAIL-01 contract + concurrency proof

## Decisions Made
- Followed plan and PATTERNS mapping exactly. No-op sender default via `matchIfMissing=true`; concrete provider deferred (D-03).
- Email bodies rendered with plain Kotlin string templates; no template-engine dependency (D-02).
- `maskEmail` helper masks the local part and preserves the domain for log readability while avoiding cleartext disclosure.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required. A concrete email provider (and its credentials) is deferred to a later phase (D-03).

## Next Phase Readiness
- The `EmailSender` seam and `PasswordResetEmailRenderer` are ready for the forgot-password flow (Plans 03/04) to send reset emails without a real network dependency.
- No blockers.

---
*Phase: 10-password-recovery*
*Completed: 2026-08-08*

## Self-Check: PASSED

All created files exist on disk and all recorded task commits (28e5780, 7eb944f, 271b638) are present in git log.
