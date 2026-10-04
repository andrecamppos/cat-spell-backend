---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
verified: 2026-10-04T17:00:00Z
status: human_needed
score: 68/69 must-haves verified
covered_files:
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-01-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-01-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-02-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-02-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-03-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-03-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-04-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-04-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-05-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-05-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-06-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-06-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-07-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-07-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-08-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-08-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-09-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-09-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-10-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-10-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-11-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-11-SUMMARY.md"
  - "build.gradle.kts"
  - "docs/CONFIGURATION.md"
  - "src/main/kotlin/com/catspell/api/auth/service/EmailChangeService.kt"
  - "src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt"
  - "src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt"
  - "src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt"
  - "src/main/kotlin/com/catspell/api/chat/service/ChatService.kt"
  - "src/main/kotlin/com/catspell/api/common/config/SecurityConfig.kt"
  - "src/main/kotlin/com/catspell/api/common/config/WaitlistCorsPolicy.kt"
  - "src/main/kotlin/com/catspell/api/common/exception/Exceptions.kt"
  - "src/main/kotlin/com/catspell/api/common/ratelimit/RateLimitBuckets.kt"
  - "src/main/kotlin/com/catspell/api/common/security/AdminTokenFilter.kt"
  - "src/main/kotlin/com/catspell/api/common/security/AdminTokenGuard.kt"
  - "src/main/kotlin/com/catspell/api/common/security/JwtAuthenticationFilter.kt"
  - "src/main/kotlin/com/catspell/api/common/security/RateLimitFilter.kt"
  - "src/main/kotlin/com/catspell/api/common/security/RequestPaths.kt"
  - "src/main/kotlin/com/catspell/api/common/security/TrustedProxyMatcher.kt"
  - "src/main/kotlin/com/catspell/api/email/service/WaitlistConfirmEmailRenderer.kt"
  - "src/main/kotlin/com/catspell/api/email/service/WaitlistInviteEmailRenderer.kt"
  - "src/main/kotlin/com/catspell/api/invite/controller/InviteAdminController.kt"
  - "src/main/kotlin/com/catspell/api/invite/service/InviteService.kt"
  - "src/main/kotlin/com/catspell/api/moderation/service/ReportService.kt"
  - "src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistAdminController.kt"
  - "src/main/kotlin/com/catspell/api/waitlist/controller/WaitlistController.kt"
  - "src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntry.kt"
  - "src/main/kotlin/com/catspell/api/waitlist/model/WaitlistEntryRepository.kt"
  - "src/main/kotlin/com/catspell/api/waitlist/service/WaitlistEmailNormalizer.kt"
  - "src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt"
  - "src/main/resources/application.yml"
  - "src/test/kotlin/com/catspell/api/TestAdminToken.kt"
  - "src/test/kotlin/com/catspell/api/common/AdminTokenGuardStartupTest.kt"
  - "src/test/kotlin/com/catspell/api/common/RateLimitBucketsTest.kt"
  - "src/test/kotlin/com/catspell/api/common/RateLimitBypassIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/common/RateLimitFilterWarnTest.kt"
  - "src/test/kotlin/com/catspell/api/common/RateLimitTrustedProxyIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/common/RequestPathsTest.kt"
  - "src/test/kotlin/com/catspell/api/common/TrustedProxyMatcherTest.kt"
  - "src/test/kotlin/com/catspell/api/common/WaitlistCorsPolicyTest.kt"
  - "src/test/kotlin/com/catspell/api/email/WaitlistConfirmEmailRendererTest.kt"
  - "src/test/kotlin/com/catspell/api/email/WaitlistInviteEmailRendererTest.kt"
  - "src/test/kotlin/com/catspell/api/invite/InviteAdminEndpointIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/invite/InviteGateIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/invite/InviteServiceTest.kt"
  - "src/test/kotlin/com/catspell/api/moderation/BlockEnforcementIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistAdminIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistConfirmIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistControllerUrlTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistConvertIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistCors429IntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistEnumerationSafetyIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistJoinIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailConcurrencyIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistPerEmailLimitIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistRateLimitIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/waitlist/WaitlistServiceConvertTest.kt"
  - "src/test/resources/application.yml"
covered_digest: "v2:sha256:363b06e1307347f85cfdf45c9e57f92d986cc874836ab25d7642094b4fe81c42"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Decide the D-04 rematch residual (18-REVIEW WR-01). Sequence: A sends B messages while matched (live-pushed, so delivered stays false) -> A blocks or unmatches B -> unblock + mutual re-like (same match row reactivated) -> only then B reconnects."
    expected: "Either (a) accept as within scope: the push happens for a conversation that is active again, whose full history is visible to B, so no hidden content leaks and the roadmap goal's literal wording holds; record an override and correct the ChatService.deliverUnreadMessages KDoc, which claims suppressed messages 'can't resurface'. Or (b) treat it as a gap: amend D-05 to allow one set-based UPDATE on the MatchService.createMatch reactivation branch (mark the match's undelivered messages delivered), add a no-reconnect-between test, and fix the KDoc."
    why_human: "D-04 promises messages 'can't resurface if the pair later rematches', but D-05 confines the fix to the reconnect path, and with that constraint the no-reconnect-between sequence cannot be closed. The two locked decisions conflict here. RESEARCH Pitfall 6 recorded it as an accepted limitation, but the user's own discussion choice was made for the 'can't resurface after a rematch' guarantee. Only the developer can resolve which decision wins."
---

# Phase 18: Address tech debt (post-block redelivery + waitlist review warnings) Verification Report

**Phase Goal:** Close the v2.2 audit tech debt without adding a capability. WebSocket reconnect never pushes previews for blocked or ended conversations (W1). Every open Phase 17 review warning, plus the chosen cheap info items and the stale-Bearer audit item, is fixed. Every finding has a recorded disposition.
**Verified:** 2026-10-04T17:00:00Z
**Status:** human_needed
**Re-verification:** No (initial verification)

Context: stage-only repository. All phase-18 work is staged on `phase/18-address-tech-debt-post-block-redelivery-waitlist-review-warn`, and HEAD is still 605ddb4. I checked the working tree and `git diff --cached`, not commits.

## Goal Achievement

ROADMAP Phase 18 has no `success_criteria` array. The truths below are the five goal clauses plus the 63 PLAN `must_haves.truths` (11 plans), and one derived truth for D-04's rematch promise.

### Goal-level truths

| # | Goal clause | Status | Evidence |
|---|-------------|--------|----------|
| G1 | Reconnect never pushes previews for blocked or ended conversations (W1) | VERIFIED | `ChatService.deliverUnreadMessages` (ChatService.kt:272-309) calls `conversationRepository.findHiddenConversationIdsForUser(userId)` once (ended match OR a block row either way, ConversationRepository.kt:19-29). It partitions the undelivered messages, marks the hidden ones `delivered = true` with no push, and pushes only the visible ones. A broker-channel `ChannelInterceptor` test harness proves zero `/user/<id>/queue/notifications` sends for: a block by the sender, a block by the recipient, an unmatch, a block row alone with the match active, and a rematch after a suppressed reconnect. A third-party conversation is still pushed exactly once. My re-run: BlockEnforcementIntegrationTest 12/12 passed. |
| G2 | Every open Phase 17 review warning fixed | VERIFIED | Current review WR-01/WR-02 (18-03). First review WR-03..WR-08 (18-04..18-10). The dropped warnings were re-added as WR-10 (Caffeine-backed `RateLimitBuckets` used by all 8 bucket stores, with no bucket `ConcurrentHashMap` left in src/main) and WR-11 (cooldown + 3 per 24 h). Details per plan below. |
| G3 | Chosen cheap info items fixed | VERIFIED | Current IN-01 (`RequestPaths.normalized`), IN-02 (`hasIpLiteralShape` private, `canonicalize` public), IN-04 (docs env table), IN-05 (one-shot `AtomicBoolean` WARN). First review IN-01→IN-08 (rendered TTL), IN-02→IN-09 (central filter, so 401 comes before 400), IN-04→IN-11 (`findByNormalizedEmail` removed, `findStoredEmail` added), IN-05→IN-12 (`UriComponentsBuilder`), IN-06 (exact JWT skip list), IN-07 (`URI.create` at construction). |
| G4 | Stale-Bearer audit item fixed | VERIFIED | `JwtAuthenticationFilter.shouldNotFilter` skips `/api/admin` and `/api/admin/` on the normalized path. `AdminTokenFilter` is registered on `/api/admin/*` at `HIGHEST_PRECEDENCE + 10`. Test `correct token with a stale Bearer header still lists entries` passed in my re-run (WaitlistAdminIntegrationTest 16/16). |
| G5 | Every finding has a recorded disposition | VERIFIED | `.planning/phases/17-waitlist-landing-page-api/17-REVIEW-DISPOSITION.md`: 26 rows, `open: 0`, 24 `fixed` (each Phase 18 row cites its plan), 2 `deferred` (current IN-03, first-review IN-03→IN-10) with the reason in the Source cell. I cross-checked against both reviews. First review (`git show aa09317`): CR-01, CR-02, WR-01..WR-09, IN-01..IN-07, all mapped (WR-01→WR-10, WR-02→WR-11, IN-01..IN-05→IN-08..IN-12). Current review: WR-01, WR-02, IN-01..IN-05, all present. The audit item is AUD-01. No finding is missing. |

### Plan must-have truths

| Plan | Truths | Status | Key evidence (code read + tests) |
|------|--------|--------|----------------------------------|
| 18-01 W1 | 6 | 6 VERIFIED | The set-based query plus the partition (above). `MatchService`, `BlockService` and the migrations have an empty staged diff. Tests A-G present and passing. |
| 18-02 WR-10 helper | 6 | 6 VERIFIED | RateLimitBuckets.kt: Caffeine `maximumSize` + `expireAfterAccess(window)`, no `expireAfterWrite` anywhere in src/main, `require` guards in `init`, atomic `cache.get`. build.gradle.kts:53 has `implementation("com.github.ben-manes.caffeine:caffeine")` with no version and no bucket4j-caffeine. ReportService, PasswordResetService, EmailVerificationService and EmailChangeService use `RateLimitBuckets(...)` with `rate-limit.max-tracked-keys:100000`. RateLimitBucketsTest (fake Ticker) has 6 tests. |
| 18-03 XFF/CIDR/WARN | 7 | 7 VERIFIED | `TrustedProxyMatcher.canonicalize` strips `[...]` and `:port`, shape-checks the host, then re-renders it from the parsed bytes. `RateLimitFilter.resolveClientIp` canonicalizes every hop and returns `remoteAddr` on null. The WARN is guarded by `AtomicBoolean.compareAndSet` and names the peer and both setting names. TrustedProxyMatcherTest (10 tests, including the `172.16.0.0/12` and `/41` edges) and RateLimitBypassIntegrationTest (13 tests) passed in my re-run. The three shared-context `@TestPropertySource` arrays are byte-identical (same md5). |
| 18-04 copy/links/URLs | 5 | 5 VERIFIED | The confirm renderer renders the TTL (days/hours) and the cooldown, says "Only the link in the most recent email from us works", and no longer has "still confirmed" text. Both renderers use `UriComponentsBuilder`. `WaitlistController` builds `successUri`/`errorUri` with `URI.create` at construction and picks between them per request. WaitlistConfirmEmailRendererTest, WaitlistControllerUrlTest. |
| 18-05 admin token length | 4 | 4 VERIFIED | `AdminTokenGuard.init` `check(blank OR length >= 32)` uses a fixed message that never contains the token. `MessageDigest.isEqual` is kept. `TEST_ADMIN_TOKEN` (34 characters) is used in 33 places. AdminTokenGuardStartupTest (`ApplicationContextRunner`) covers blank, 31, 32. |
| 18-06 cooldown/pin/cap | 7 | 7 VERIFIED | `rotatePendingToken`: `AND (e.confirmTokenHash IS NULL OR e.updatedAt <= :resendCutoff)`, and `email` is never in the SET clause. `findStoredEmail` feeds the event. `per-email-refill-hours:24`. V24 migration not in the diff. WaitlistConfirmIntegrationTest (13 tests, including `INTERVAL '16 minutes'` backdating) passed in my re-run. |
| 18-07 three families + path | 6 | 6 VERIFIED | `bucketFamilyFor` covers exact `POST /api/waitlist`, admin prefix (any method) and AUTH_PATHS, all on `RequestPaths.normalized`. URL patterns are exactly `/api/auth/*`, `/api/waitlist`, `/api/admin/*`. All three families are `RateLimitBuckets`. The no-arg constructor still works because every parameter has a default. RequestPathsTest covers `/api/auth/./login`. |
| 18-08 convert I/O | 5 | 5 VERIFIED | `InviteService.create` calls `saveAndFlush`. `convertToInvite` calls `create(null)`, then render, then `sendBounded` (a private `ThreadPoolExecutor`, not a bean). The timeout, rejection, ExecutionException (unwrapped) and interrupt paths each throw `WaitlistInviteDeliveryException(cause = ...)` and log one fixed WARN with no address. The non-SUCCESS branch logs a WARN and throws. WaitlistServiceConvertTest (5 tests: verifyOrder, timeout, cause, non-SUCCESS, renderer passthrough) passed in my re-run. |
| 18-09 CORS 429 | 4 | 4 VERIFIED | `WaitlistCorsPolicy.configuration` is shared by `SecurityConfig.corsConfigurationSource` and the `RateLimitFilter` 429 branch (`checkOrigin`, no `DefaultCorsProcessor`, `Vary: Origin` always, ACAO and Expose-Headers only for allowed origins, join family only). WaitlistCors429IntegrationTest has 5 tests. |
| 18-10 admin boundary | 7 | 7 VERIFIED | `AdminTokenFilter` (not a `@Component`; registered only via `FilterRegistrationBean`) calls `adminTokenGuard.require`. The JWT skip list is exact for `/api/waitlist` and `/api/waitlist/confirm`, plus the `/api/admin` prefix. `SecurityConfig` has `/api/admin/**` permitAll. Handler `require` calls are kept as defense in depth. Tests for unmapped path 401/404, `limit=abc` 401 and stale Bearer all passed in my re-run. |
| 18-11 docs/keys/disposition | 6 | 6 VERIFIED | application.yml declares all 7 keys with the same defaults as `@Value`. docs/CONFIGURATION.md lists every env var (the grep counts are non-zero for each), the Production row names the three settings, line 285 lists Caffeine, and the 32-character rule is documented. Disposition file as in G5. Full-suite gate: 511/511 per orchestrator evidence (Gradle `:test UP-TO-DATE` on an unfiltered re-run). |

### Derived truth (D-04 promise)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| D1 | A message left undelivered before a block or unmatch can never be pushed after a rematch (D-04: "it can't resurface if the pair later rematches") | UNCERTAIN (human decision) | Only conditionally true. `sendMessage` (ChatService.kt:74-110) pushes live but never sets `delivered`. `MatchService.createMatch` reactivation (MatchService.kt:41-46) clears `endedAt` without touching messages. So if B does not reconnect between the block/unmatch and the rematch, `findHiddenConversationIdsForUser` no longer returns the conversation on B's next reconnect, and the pre-block messages are pushed. All three rematch tests put a suppressed reconnect in between, so none of them covers this path. The ChatService KDoc (lines 263-267) states the guarantee without that condition. |

**Score:** 68/69 truths verified (0 present-but-behavior-unverified; 1 uncertain, routed to human decision)

### WR-01 classification (orchestrator question, answered plainly)

- **Against the goal's literal wording: within scope as achieved.** Every reconnect push goes to a conversation that is currently visible: not ended, no block row either way. At the moment WR-01's push happens, the pair has rematched, the conversation is active, and B can read the full history anyway (`getMessages` has no time filter). No preview is ever pushed for a blocked or ended conversation, and no hidden content leaks.
- **Against D-04: not fully achieved.** D-04's purpose clause ("can't resurface if the pair later rematches") holds only when a reconnect happens while the conversation is hidden. Because live-pushed messages are never marked delivered, this is the common case, not a rare one. The plan's own must-have was written with the intermediate reconnect as a condition, so the plans pass while D-04's promise is weaker than stated.
- **Not a deferred item.** No later milestone phase exists in ROADMAP.md (Phase 18 is last).
- **Not a FAILED gap either.** D-05 ("the fix lives only on the reconnect path") rules out the reviewer's fix on the reactivation path. Under the locked decisions as written, this sequence cannot be closed, and RESEARCH Pitfall 6 and 18-01-SUMMARY "Known Limitations" record it openly. The two locked decisions conflict here, so I escalate it to the developer instead of failing the phase. Either outcome needs a follow-up edit: an override plus a KDoc correction, or a D-05 amendment plus a small fix and test.

If you accept it, add to the frontmatter:

```yaml
overrides:
  - must_have: "A message left undelivered before a block or unmatch can never be pushed after a rematch"
    reason: "Rematch-before-reconnect pushes only for an active, fully visible conversation (no hidden content); D-05 confines the fix to the reconnect path; recorded as RESEARCH Pitfall 6 / 18-01 Known Limitations. ChatService KDoc to be corrected."
    accepted_by: "<name>"
    accepted_at: "<ISO timestamp>"
```

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| chat/model/ConversationRepository.kt `findHiddenConversationIdsForUser` | VERIFIED | JPQL with ended-or-EXISTS-block, used by ChatService |
| chat/service/ChatService.kt | VERIFIED | partition, suppress, push |
| common/ratelimit/RateLimitBuckets.kt | VERIFIED | 8 call sites |
| common/security/TrustedProxyMatcher.kt `canonicalize` | VERIFIED | used by RateLimitFilter |
| common/security/RateLimitFilter.kt | VERIFIED | 3 families, AtomicBoolean, CORS 429 |
| common/security/RequestPaths.kt | VERIFIED | used by RateLimitFilter and JwtAuthenticationFilter |
| common/security/AdminTokenFilter.kt | VERIFIED | `FilterRegistrationBean` on `/api/admin/*`, order HIGHEST_PRECEDENCE + 10 |
| common/security/AdminTokenGuard.kt `MIN_ADMIN_TOKEN_LENGTH` | VERIFIED | startup check |
| common/config/WaitlistCorsPolicy.kt | VERIFIED | injected into SecurityConfig and RateLimitFilterConfig |
| email/service/WaitlistConfirmEmailRenderer.kt, WaitlistInviteEmailRenderer.kt | VERIFIED | UriComponentsBuilder, rendered TTL and cooldown |
| waitlist/controller/WaitlistController.kt | VERIFIED | successUri/errorUri parsed at construction |
| waitlist/model/WaitlistEntryRepository.kt | VERIFIED | `resendCutoff`, `findStoredEmail` |
| waitlist/service/WaitlistService.kt | VERIFIED | cooldown, pinned address, bounded send |
| invite/service/InviteService.kt | VERIFIED | saveAndFlush |
| common/exception/Exceptions.kt | VERIFIED | `cause: Throwable? = null` |
| src/main/resources/application.yml, docs/CONFIGURATION.md | VERIFIED | all keys declared and documented |
| 17-REVIEW-DISPOSITION.md | VERIFIED | 26/26 recorded, 0 open |
| Tests (17 new or extended classes) | VERIFIED | listed in covered_files |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| ChatService.deliverUnreadMessages | ConversationRepository.findHiddenConversationIdsForUser | one call per reconnect | WIRED |
| BlockEnforcementIntegrationTest | brokerChannel | `addInterceptor(captureInterceptor)` | WIRED |
| ReportService, PasswordReset, EmailVerification, EmailChange, WaitlistService, RateLimitFilter | RateLimitBuckets | `RateLimitBuckets(` | WIRED |
| RateLimitFilter.resolveClientIp | TrustedProxyMatcher.canonicalize | each hop; null returns remoteAddr | WIRED |
| RateLimitFilter.doFilter, JwtAuthenticationFilter.shouldNotFilter | RequestPaths.normalized | path decisions | WIRED |
| WaitlistConfirmEmailRenderer | `confirm-token-ttl-hours:168` / `resend-cooldown-minutes:15` | `@Value` | WIRED |
| WaitlistController.confirm | successUri / errorUri | `if (...) successUri else errorUri` | WIRED |
| WaitlistService.join | rotatePendingToken + findStoredEmail | event with stored email | WIRED |
| WaitlistService.convertToInvite | InviteService.create(null) → saveAndFlush | before the send | WIRED |
| WaitlistService | GlobalExceptionHandler (502) | `WaitlistInviteDeliveryException(cause = ...)` | WIRED |
| SecurityConfig / RateLimitFilterConfig | WaitlistCorsPolicy | `waitlistCorsPolicy.configuration`, `checkOrigin` | WIRED |
| AdminTokenFilter | AdminTokenGuard.require | single constant-time check | WIRED |
| TestAdminToken | 4 admin-token test classes | `TEST_ADMIN_TOKEN` | WIRED |
| application.yml | `@Value` bindings | same keys and defaults (`max-tracked-keys`, etc.) | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| deliverUnreadMessages pushes | undelivered messages minus hidden ids | `messageRepository` derived query + JPQL hidden-id query | yes (DB) | FLOWING |
| Confirmation email recipient | stored email | `findStoredEmail` scalar JPQL | yes (DB) | FLOWING |
| Rate-limit key | canonical hop or remoteAddr | request headers + socket | yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| W1 suppression, rematch, block-row-only, visible push still once | `./gradlew test --tests ...BlockEnforcementIntegrationTest` (plus the 5 classes below, one run) | 12 tests, 0 failures | PASS |
| Stale Bearer, central filter, 401 before 400/404 | WaitlistAdminIntegrationTest | 16 tests, 0 failures | PASS |
| Cooldown, pinned address | WaitlistConfirmIntegrationTest | 13 tests, 0 failures | PASS |
| XFF canonicalization, fail-safe, normalized spellings | RateLimitBypassIntegrationTest | 13 tests, 0 failures | PASS |
| CIDR edges, canonicalize table | TrustedProxyMatcherTest | 10 tests, 0 failures | PASS |
| Flush-before-send, timeout, cause chain, address-free WARN | WaitlistServiceConvertTest | 5 tests, 0 failures | PASS |
| Full suite | orchestrator evidence (18-11 run; `:test UP-TO-DATE` re-run) | 80 classes / 511 tests, 0 failures | PASS (not re-run here) |

My targeted run: `BUILD SUCCESSFUL in 2m 14s`, 69 tests, 0 failures (result XMLs timestamped 2026-10-04T16:52-16:53Z).

### Probe Execution

No probes declared in the PLANs or SUMMARYs, and no `scripts/*/tests/probe-*.sh` exist. SKIPPED.

### Prohibitions (all `verification: test`)

| Prohibition | Enforcement evidence | Disposition |
|-------------|----------------------|-------------|
| 18-01: no change to MatchService.endMatch / BlockService | Staged diff for `match/` and `BlockService.kt` is empty | verified (observed) |
| 18-01: no deleting or hard-modifying message content | Only `delivered` is set; `messageRepository.existsById` asserted in test A | verified (test) |
| 18-02: no pinned Caffeine version, no extra artifact | build.gradle.kts has one unversioned line and no bucket4j-caffeine | verified (observed) |
| 18-02: no expireAfterWrite | No match in src/main; the RateLimitBucketsTest keep-within-window test | verified (test) |
| 18-03: no DNS lookup from header text | `hasIpLiteralShape` runs before `parseLiteral` in every path | verified (code + TrustedProxyMatcherTest) |
| 18-03: shared @TestPropertySource arrays identical | md5 is identical across the 3 classes | verified (observed) |
| 18-05: token never in logs or messages | Fixed `check` message; 31-character startup test asserts the token is absent | verified (test) |
| 18-06: V24 migration not edited | Not in the staged diff | verified (observed) |
| 18-06: no logging of email or token | WaitlistService logs contain no address or token | verified (observed) |
| 18-07: confirm GET and preflight not throttled | Exact `POST` + `/api/waitlist` match | verified (code + tests) |
| 18-07: no-arg RateLimitFilter() still compiles | All parameters have defaults; RateLimitIntegrationTest in the 511 | verified (test) |
| 18-08: no Executor/TaskExecutor bean | Private `ThreadPoolExecutor` field; no `@Bean` returns an Executor in src/main | verified (observed) |
| 18-08: send stays in the convert transaction | `sendBounded` is awaited inside the `@Transactional convertToInvite`; the rollback test is in WaitlistConvertIntegrationTest | verified (test) |
| 18-09: no CORS processor in the 429 path | No `DefaultCorsProcessor` in src/main; foreign-origin 429 test | verified (test) |
| 18-09: no wildcard or foreign-origin ACAO | Foreign and no-Origin tests pass. Caveat: an operator-configured `*` would be echoed (18-REVIEW IN-02, pre-existing, open there) | verified (test) |
| 18-10: AdminTokenFilter not a @Component | The class has no annotation; only a `@Bean FilterRegistrationBean` | verified (observed) |
| 18-10: no second token comparison | The filter calls `adminTokenGuard.require` | verified (observed) |
| 18-11: no Kotlin source change in 18-11 | 18-11 SUMMARY staged files are yml, md and the disposition only | verified (observed) |

None are flagged unverified. Each one has deterministic evidence.

### Requirements Coverage

| Requirement | Source Plans | Status | Evidence |
|-------------|-------------|--------|----------|
| MOD-02 | 18-01, 18-11 | SATISFIED (hardened) | Bidirectional block predicate now also covers reconnect redelivery |
| MOD-03 | 18-01, 18-11 | SATISFIED (hardened) | Locked or ended conversation history kept; no reconnect previews while hidden |
| MOD-06 | 18-02 | SATISFIED (no regression) | Per-reporter cap on the bounded store |
| INV-02 | 18-05, 18-10, 18-11 | SATISFIED (hardened) | Central admin filter, token length rule, admin throttle |
| WAIT-01 | 18-06, 18-07, 18-09, 18-10, 18-11 | SATISFIED | Identical 202, separate join bucket, CORS-readable 429, exact JWT skip |
| WAIT-02 | 18-04, 18-06, 18-11 | SATISFIED | Truthful copy, cooldown, single-use token unchanged |
| WAIT-03 | 18-02, 18-03, 18-06, 18-07, 18-09, 18-11 | SATISFIED | Bounded buckets, XFF canonicalization, 3 per 24 h |
| WAIT-04 | 18-04..18-08, 18-10, 18-11 | SATISFIED | Flush/send/rollback, bounded send, central guard |

All IDs exist in REQUIREMENTS.md. REQUIREMENTS.md maps no IDs to Phase 18 (no new IDs, by design), so none are orphaned.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all modified files) | - | TBD/FIXME/XXX | none found | - |
| (all modified files) | - | TODO/HACK/PLACEHOLDER | none found | - |
| docs/CONFIGURATION.md | 256 | `<!-- VERIFY: production deployment platform ... -->` | Info | Pre-existing at HEAD 605ddb4; not introduced by this phase |
| chat/service/ChatService.kt | 263-267 | KDoc states the D-04 rematch guarantee without its condition | Warning | Misleading to maintainers; tied to the human decision above |

18-REVIEW IN-01..IN-06 (info) are still `open` in 18-REVIEW-DISPOSITION.md. They are code-quality items found after the phase, outside the goal's finding set (which is the Phase 17 findings). I don't count them against the goal, but someone should give them dispositions before ship.

### Human Verification Required

#### 1. D-04 rematch residual (18-REVIEW WR-01)

**Test:** A messages B while matched (B online, receives it live). A blocks B (or unmatches). They unblock and mutually re-like (same match row reactivated). B reconnects only now.
**Expected:** Decide one: (a) accept, because the push is for an active, fully visible conversation (record the override above and fix the ChatService KDoc); or (b) amend D-05 and add one set-based "mark delivered" UPDATE on the reactivation branch of `MatchService.createMatch`, a no-reconnect-between test, and the KDoc fix.
**Why human:** D-04 and D-05 conflict for this sequence. The roadmap goal's literal wording is met, and D-04's stated purpose is not fully met.

### Gaps Summary

There are no blocking gaps. Every Phase 17 warning, every chosen info item, and the stale-Bearer audit item are implemented and wired, with passing tests behind them. The Phase 17 disposition record is complete (26/26, 0 open). The one open item is the D-04 rematch residual (18-REVIEW WR-01). It doesn't violate the goal's literal clause, because no preview is ever pushed for a conversation that is blocked or ended at push time. But it leaves D-04's "can't resurface after a rematch" promise conditional, and the ChatService KDoc overstates it. Because closing it needs a change to locked decision D-05, it goes to the developer, not into an automatic gap-closure plan.

---

_Verified: 2026-10-04T17:00:00Z_
_Verifier: Claude (gsd-verifier)_
