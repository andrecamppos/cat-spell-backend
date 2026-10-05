---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
verified: 2026-10-05T10:39:00Z
status: passed
score: 75/75 must-haves verified
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
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-12-PLAN.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-12-SUMMARY.md"
  - ".planning/phases/18-address-tech-debt-post-block-redelivery-waitlist-review-warn/18-CONTEXT.md"
  - "build.gradle.kts"
  - "docs/CONFIGURATION.md"
  - "src/main/kotlin/com/catspell/api/auth/service/EmailChangeService.kt"
  - "src/main/kotlin/com/catspell/api/auth/service/EmailVerificationService.kt"
  - "src/main/kotlin/com/catspell/api/auth/service/PasswordResetService.kt"
  - "src/main/kotlin/com/catspell/api/chat/model/ConversationRepository.kt"
  - "src/main/kotlin/com/catspell/api/chat/model/MessageRepository.kt"
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
  - "src/main/kotlin/com/catspell/api/match/service/MatchService.kt"
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
  - "src/test/kotlin/com/catspell/api/match/MatchServiceTest.kt"
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
covered_digest: "v2:sha256:b312007ef1a0e6877ce7a7c6f33f4bf6c3b031c66812b0c80c432f311877b1ef"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 68/69
  gaps_closed:
    - "G-18-1 / D-04: messages left undelivered when a conversation was hidden (block or unmatch) no longer resurface as /queue/notifications previews after a rematch, whether or not the recipient reconnected in between (plan 18-12; resolves the prior human_verification item by code, not by override)"
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "18-REVIEW WR-02: a reconnect that overlaps a rematch commit can still push pre-hide previews. deliverUnreadMessages reads the undelivered rows (ChatService.kt:290) before the hidden set (ChatService.kt:295). Under PostgreSQL READ COMMITTED, a rematch (endedAt = null plus the sweep) that commits between those two statements leaves the first read stale (rows still delivered = false) while the second read sees the match as active. The @Async WebSocketSessionListener makes the overlap possible."
    category: other
    reason: "Does not undermine the goal. At the moment of that push the match is already reactivated and committed, so no preview is pushed for a blocked or ended conversation (W1 holds), and B can read the full history anyway. G-18-1 as written (the sequential no-reconnect-between sequence) is closed and test-proven. It is a millisecond concurrency window of the kind D-06 already accepts for send-vs-block. It does make D-04's 'can't resurface' promise, and the KDoc sentence 'Only the two together keep...', slightly stronger than the code. Resolve it by giving WR-02 a disposition in 18-REVIEW-DISPOSITION.md before ship: fix it (read the hidden set before the undelivered rows, a two-line reorder; the reviewer shows that every commit ordering is then safe) or mark it deferred with a reason."
    evidence_status: "none provided (no test reproduces the interleaving; reasoning from code confirmed by me)"
  - finding: "18-REVIEW IN-07 / IN-08 (info): bare @Modifying leaves managed Message entities stale for any future caller that holds them in the same transaction (no current caller does); the integration helper asserts the sweep result after the symptom assertions, so a regression's first failure message points at the reconnect path."
    category: other
    reason: "Code-quality items, no behavior impact today. Still 'open' in 18-REVIEW-DISPOSITION.md, together with IN-01..IN-06. Give them dispositions before ship."
    evidence_status: "none provided"
---

# Phase 18: Address tech debt (post-block redelivery + waitlist review warnings) Verification Report

**Phase Goal:** Close the v2.2 audit tech debt without adding a capability. WebSocket reconnect never pushes previews for blocked or ended conversations (W1). Every open Phase 17 review warning, plus the chosen cheap info items and the stale-Bearer audit item, is fixed. Every finding has a recorded disposition.
**Verified:** 2026-10-05T10:39:00Z
**Status:** passed
**Re-verification:** Yes. This pass follows gap closure for UAT gap G-18-1 (plan 18-12).

Context: stage-only repository. HEAD is `add8a2f`. All 18-12 changes are staged and not committed. I read the working tree and `git diff --cached`. Nothing is unstaged under `src/`. The staged source delta is limited to MessageRepository.kt, MatchService.kt, ChatService.kt (KDoc only), MatchServiceTest.kt and BlockEnforcementIntegrationTest.kt.

## Goal Achievement

ROADMAP Phase 18 has no `success_criteria` array. The truths are the five goal clauses, the 63 PLAN truths from 18-01..18-11, and the 7 truths from 18-12. The prior derived truth D1 (the D-04 rematch promise) restates 18-12 truths 1-2, so I fold it into them.

### Goal-level truths

| # | Goal clause | Status | Evidence |
|---|-------------|--------|----------|
| G1 | Reconnect never pushes previews for blocked or ended conversations (W1) | VERIFIED | Reconnect path unchanged since the prior pass. The ChatService staged diff has no non-comment line. It still calls `findHiddenConversationIdsForUser` once, partitions, suppresses and pushes. 18-12 adds a second suppression point: `MatchService.createMatch` reactivation sweep. BlockEnforcementIntegrationTest has 14 tests, 0 failures (12 prior plus H/I). |
| G2 | Every open Phase 17 review warning fixed | VERIFIED | Regression check: the symbols from 18-02..18-10 are present (RateLimitBuckets `expireAfterAccess`, `canonicalize`, `MIN_ADMIN_TOKEN_LENGTH`, `resendCutoff`, `checkOrigin`, `adminTokenGuard.require`, `saveAndFlush`). No files outside the 18-12 set are in the staged source diff. Full suite is green (below). |
| G3 | Chosen cheap info items fixed | VERIFIED | Unchanged since the prior pass. Files untouched by 18-12. Full suite green. |
| G4 | Stale-Bearer audit item fixed | VERIFIED | Unchanged. WaitlistAdminIntegrationTest is in the green 514-test run. |
| G5 | Every finding has a recorded disposition | VERIFIED | `17-REVIEW-DISPOSITION.md` still has `open: 0` (26 rows). The goal's finding set is the Phase 17 reviews plus the audit item. The Phase 18 review ledger (`18-REVIEW-DISPOSITION.md`) records WR-01 as `fixed`, citing 18-12. Its other 9 rows are recorded as `open`. Those are post-phase findings outside the goal's set (see Advisory). |

### 18-12 must-have truths (gap closure G-18-1)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Block path, no reconnect between: every pre-block message (both directions) is delivered at rematch, and B's and A's first reconnects push 0 and return 0 | VERIFIED | `assertNoStalePreviewWhenRematchPrecedesReconnect` (BlockEnforcementIntegrationTest.kt:410-453, tests at :456 and :466). It sends 2 A→B messages and 1 B→A reply, asserts all start `delivered = false`, then runs block + unblock, then `matchPair` (real `POST /api/discovery/swipe` ×2). It asserts the same match row with `endedAt == null`, captures `delivered` via JDBC right after the rematch, then calls `deliverUnreadMessages` for B and A. The broker-captured notifications for each must be 0, both return values must be 0, and the sweep snapshot must be `[true, true, true]`. Test `pre-block messages do not resurface when the pair rematches before the recipient reconnects` passed. Discrimination: without line MatchService.kt:50, B's reconnect would push 2 and A's would push 1. The asserts would fail (the summary records the mutation check, and my code reading agrees). |
| 2 | Same on the unmatch path | VERIFIED | Same helper with `matchService.unmatch(a.id, b.id)` as the hide step. Test `pre-unmatch messages do not resurface when the pair rematches before the recipient reconnects` passed. |
| 3 | No over-suppression: a post-rematch message is pushed live once, starts undelivered, and is pushed once on the next reconnect (returns 1) | VERIFIED | Control block in the same helper (lines 442-452), run for both paths. It passed. |
| 4 | D-05 as amended: one set-based `@Modifying` UPDATE, called only in the reactivation branch and before `MatchCreatedEvent`, never for a new or still-active match. `endMatch` and BlockService unchanged | VERIFIED | MessageRepository.kt:47-49 is `UPDATE Message m SET m.delivered = true WHERE m.conversation.match.id = :matchId AND m.delivered = false`. It is one statement with no entity load. The only call site is MatchService.kt:50, inside `if (existing.endedAt != null)`, after `save` and before `publishEvent`. `grep` shows `endedAt = null` only at MatchService.kt:47, so it is the only reactivation point. Both production callers (`DiscoveryService.swipe` at :249 and :296) are `@Transactional`. MatchServiceTest: the `verifyOrder { markAllDeliveredForMatch(id); publishEvent(...) }` test, plus `exactly = 0` checks on the new-match, already-active and race-fallback tests. 5/5 passed. `endMatch` (MatchService.kt:77-88) and BlockService have no staged diff. |
| 5 | ChatService: KDoc only, code byte-identical, both suppression points named | VERIFIED | Every added or removed line in `git diff --cached -U0` of ChatService.kt is a KDoc line (my non-comment filter returned nothing). The KDoc names "Suppression point 1 (here)" and "Suppression point 2 (`MatchService.createMatch`, reactivation branch)" and links `[MessageRepository.markAllDeliveredForMatch]`. |
| 6 | 18-CONTEXT.md D-05 keeps its sentence and gains a dated amendment naming G-18-1 / 18-12 and stating that `endMatch` is unchanged | VERIFIED | The staged diff adds one indented "**Amended 2026-10-05 (UAT G-18-1, plan 18-12):**" bullet under D-05. The original D-05 line is unmodified. The bullet says "Block/unmatch teardown in `MatchService.endMatch` stays unchanged, and there is still no per-message loop." |
| 7 | No regression: the 12 prior BlockEnforcement tests, the match, chat and moderation packages, and PushTriggerIntegrationTest pass | VERIFIED | `build/test-results/test`: 81 classes, 514 tests, 0 failures, 0 errors, 1 skipped (FcmSmokeTest, a pre-existing opt-in smoke test). The result XMLs (10:22Z) are newer than every 18-12 source mtime (latest 10:12Z), so they reflect this tree. The ChatIntegrationTest flake did not occur. |

### Plan must-have truths, 18-01..18-11 (regression check)

| Plan | Truths | Status | Regression evidence |
|------|--------|--------|---------------------|
| 18-01 W1 | 6 | 6 VERIFIED | The ChatService code path is unchanged (diff is KDoc only). `findHiddenConversationIdsForUser` is unchanged (no diff for ConversationRepository.kt). Tests A-G are among the 14 passing. The prohibition "no change to MatchService.endMatch" still holds: 18-12 touches only `createMatch` and the constructor. |
| 18-02 WR-10 | 6 | 6 VERIFIED | Files untouched. Tests green. |
| 18-03 XFF/CIDR/WARN | 7 | 7 VERIFIED | Files untouched. Tests green. |
| 18-04 copy/links/URLs | 5 | 5 VERIFIED | Files untouched. Tests green. |
| 18-05 admin token length | 4 | 4 VERIFIED | Files untouched. Tests green. |
| 18-06 cooldown/pin/cap | 7 | 7 VERIFIED | Files untouched. Tests green. |
| 18-07 three families + path | 6 | 6 VERIFIED | Files untouched. Tests green. |
| 18-08 convert I/O | 5 | 5 VERIFIED | Files untouched. Tests green. |
| 18-09 CORS 429 | 4 | 4 VERIFIED | Files untouched. Tests green. |
| 18-10 admin boundary | 7 | 7 VERIFIED | Files untouched. Tests green. |
| 18-11 docs/keys/disposition | 6 | 6 VERIFIED | Files untouched. `17-REVIEW-DISPOSITION.md` `open: 0`. |

The detailed per-truth evidence for 18-01..18-11 is unchanged from the 2026-10-04 pass (git `fcf9dd0`).

**Score:** 75/75 truths verified (0 present-but-behavior-unverified, 0 uncertain, 0 overrides).

### Resolution of the prior human-verification item (D-04 / WR-01)

The 2026-10-04 pass left D1 UNCERTAIN because D-04 and D-05 conflicted. The user chose (b) at UAT. 18-12 amends D-05 and closes the sequence in code. Pre-hide messages are swept to `delivered = true` in the same transaction that clears `endedAt`, so after the rematch commits, a later reconnect has nothing stale left to push. The no-reconnect-between path, which the old tests missed, now has its own tests for both hide paths. So the item is resolved by code, not by an override.

### WR-02 judgment (reconnect racing a rematch commit)

I confirmed the race in code. `deliverUnreadMessages` reads undelivered rows (ChatService.kt:290) before the hidden set (:295). `WebSocketSessionListener.handleSessionConnected` is `@Async`, so a reconnect can overlap a rematch transaction. No isolation level is set anywhere in src/main, so PostgreSQL uses READ COMMITTED. My assessment:

- **It does not undermine the goal.** W1 says reconnect never pushes previews for blocked or ended conversations. In the WR-02 interleaving, the hidden-set read sees the match as already reactivated and committed. The push is for an active conversation whose full history B can already read. No hidden content leaks.
- **G-18-1 as specified is closed.** Its truth covers the sequential case, "whether or not the recipient reconnected between the block/unmatch and the rematch." Both sequential orderings are tested and pass. WR-02 is a third case: a reconnect concurrent with the rematch commit, in a window of milliseconds.
- **The project has precedent for accepting this kind of window.** D-06 accepts the send-vs-block push race on the same delivery surface.
- **It is still worth closing.** D-04's wording ("can't resurface") is unconditional, and the user chose (b) to make it so. The fix is cheap: swap the two reads. Under READ COMMITTED every commit ordering is then safe, as the reviewer shows. The KDoc sentence "Only the two together keep..." should then say the read order is load-bearing.

Classification: recorded as an advisory item, not blocking. It needs a disposition (`fixed` or `deferred` with reason) in 18-REVIEW-DISPOSITION.md before ship.

### Required Artifacts (18-12)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `chat/model/MessageRepository.kt` | `fun markAllDeliveredForMatch` | VERIFIED | `@Modifying @Query` bulk UPDATE, returns Int, KDoc present. Called from MatchService. |
| `match/service/MatchService.kt` | `messageRepository.markAllDeliveredForMatch(existing.id!!)` | VERIFIED | Line 50, reactivation branch only. Constructor gains `messageRepository` (Spring-injected). |
| `moderation/BlockEnforcementIntegrationTest.kt` | "rematches before the recipient reconnects" | VERIFIED | Two tests plus a shared helper. Both pass. |
| `match/MatchServiceTest.kt` | `markAllDeliveredForMatch` | VERIFIED | Ordering test plus 2 negative checks (`exactly = 0`). 5/5 pass. |
| `chat/service/ChatService.kt` | KDoc contains `markAllDeliveredForMatch` | VERIFIED | Line 273. |
| `18-CONTEXT.md` | contains `G-18-1` | VERIFIED | D-05 amendment bullet. |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| MatchService.createMatch (`endedAt != null`) | MessageRepository.markAllDeliveredForMatch | one call after `save`, before `publishEvent`, same transaction | WIRED (code line 50; verifyOrder test) |
| DiscoveryService.swipe (`@Transactional`) | MatchService.createMatch | mutual LIKE at :249 and :296; the tests reach it via `matchPair` → `POST /api/discovery/swipe` | WIRED |
| BlockEnforcementIntegrationTest | brokerChannel | existing `ChannelInterceptor` capture; `notificationsTo(` | WIRED |
| ChatService.deliverUnreadMessages | ConversationRepository.findHiddenConversationIdsForUser | one call per reconnect (unchanged) | WIRED |
| (prior links from 18-02..18-11) | - | unchanged files | WIRED (regression: symbols present, suite green) |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| Rematch sweep | `messages.delivered` for the match's conversation | JPQL bulk UPDATE on the DB | yes | FLOWING (asserted via JDBC `SELECT delivered`) |
| Reconnect pushes | undelivered minus hidden | derived query + JPQL hidden-id query | yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| G-18-1 block and unmatch paths, post-rematch control | orchestrator full run on this tree; result XML `TEST-...BlockEnforcementIntegrationTest.xml` | 14 tests, 0 failures (H and I present by name) | PASS |
| Sweep scoped to reactivation, before publish | `TEST-...match.MatchServiceTest.xml` | 5 tests, 0 failures | PASS |
| Full suite | orchestrator `./gradlew test` (16m 55s); I summed all 81 result XMLs | 514 tests, 0 failures, 0 errors, 1 skipped | PASS |

I did not re-run tests. The result XMLs are newer than every changed source file, and there are no unstaged source changes.

### Probe Execution

No probes declared in the PLANs or SUMMARYs, and no `scripts/*/tests/probe-*.sh` exist. SKIPPED.

### Prohibitions

| Prohibition | Enforcement evidence | Disposition |
|-------------|----------------------|-------------|
| 18-12: MUST NOT change `endMatch`, BlockService, `sendMessage`, or the code of `deliverUnreadMessages` | Staged diff: MatchService hunks only touch the imports, the constructor and the `createMatch` reactivation branch. BlockService is not in the diff. Every changed ChatService line is a KDoc line. | verified (observed) |
| 18-12: MUST NOT change message content or delete rows | The UPDATE sets only `delivered`. The tests read the rows back by id after the sweep. | verified (test + code) |
| 18-12: MUST NOT mark delivered row by row | One `@Modifying` JPQL statement. MatchService loads no `Message` entities. | verified (observed) |
| 18-01..18-11 prohibitions | Files unchanged since the prior pass | verified (carried forward) |

### Requirements Coverage

| Requirement | Source Plans | Status | Evidence |
|-------------|-------------|--------|----------|
| MOD-02 | 18-01, 18-11, 18-12 | SATISFIED (hardened) | Block suppression now holds across a rematch whether or not a reconnect happened while hidden |
| MOD-03 | 18-01, 18-11, 18-12 | SATISFIED (hardened) | History retained (rows kept). No stale previews after an unmatch or block and a later rematch. |
| MOD-06 | 18-02 | SATISFIED (no regression) | Unchanged |
| INV-02 | 18-05, 18-10, 18-11 | SATISFIED | Unchanged |
| WAIT-01..WAIT-04 | 18-04..18-11 | SATISFIED | Unchanged |

All IDs exist in REQUIREMENTS.md (MOD-02 line 14, MOD-03 line 15). REQUIREMENTS.md maps no IDs to Phase 18 by design, so none are orphaned.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| 18-12 files (5 source/test) | - | TBD/FIXME/XXX/TODO/HACK | none found | - |
| chat/service/ChatService.kt | 290-295 | Undelivered read before the hidden-set read (18-REVIEW WR-02) | Warning (advisory) | Concurrency window only. See WR-02 judgment. |
| chat/service/ChatService.kt | 276 | KDoc "Only the two together keep..." doesn't mention that the read order matters | Info | Overstates slightly until WR-02 is fixed or deferred |
| chat/model/MessageRepository.kt | 47-49 | Bare `@Modifying` (IN-07) | Info | No current caller holds managed `Message` entities |

The prior pass's Warning (ChatService KDoc stating the D-04 guarantee without its condition) is fixed. The KDoc now names both suppression points.

### Advisory (New Scope, Unevidenced)

| # | Finding | Category | Why Advisory |
|---|---------|----------|--------------|
| 1 | 18-REVIEW WR-02: reconnect racing a rematch commit | other | No test reproduces it. The goal clause still holds at push time. It is a residual of the kind D-06 accepts. It needs a disposition (a cheap fix is available). |
| 2 | 18-REVIEW IN-07, IN-08 (and IN-01..IN-06 still `open`) | other | Code-quality info items outside the goal's finding set. They need dispositions before ship. |

### Human Verification Required

None. The prior item (D-04 rematch residual) is resolved by 18-12.

### Gaps Summary

There are no gaps. G-18-1 is closed in code. `MatchService.createMatch`'s reactivation branch runs one set-based UPDATE that marks the conversation's undelivered messages delivered, inside the swipe transaction and before `MatchCreatedEvent`. New integration tests prove the no-reconnect-between sequence for both block and unmatch, and a post-rematch delivery control shows nothing was over-suppressed. The unit tests prove branch scoping and ordering. The KDoc is corrected, and D-05 carries a dated amendment. All 18-01..18-11 work is unchanged, and the full suite is green on this tree (514/0/0, 1 skipped).

Not blocking: 18-REVIEW WR-02. It is a real but narrow concurrency window, and swapping two reads in `deliverUnreadMessages` would close it. Before ship, mark it `fixed` or `deferred` with a reason in 18-REVIEW-DISPOSITION.md, along with the other 8 `open` info rows.

---

_Verified: 2026-10-05T10:39:00Z_
_Verifier: Claude (gsd-verifier)_
