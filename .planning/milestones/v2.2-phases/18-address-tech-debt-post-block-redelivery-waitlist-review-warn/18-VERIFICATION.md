---
phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn
verified: 2026-10-05T13:16:00Z
status: passed
score: 75/75 must-haves verified (includes 1 override)
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
  - "src/main/kotlin/com/catspell/api/chat/config/WebSocketConfig.kt"
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
  - "src/test/kotlin/com/catspell/api/chat/ChatIntegrationTest.kt"
  - "src/test/kotlin/com/catspell/api/chat/ChatServiceDeliverUnreadTest.kt"
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
covered_digest: "v2:sha256:e4dd0fb9ce106d475c5e4ebeb922ec9d25bb1bdb1dd1ede2ab11a9b5f78d7311"
behavior_unverified: 0
overrides_applied: 1
overrides:
  - must_have: "ChatService.deliverUnreadMessages: only its KDoc changes, and the code is byte-identical"
    reason: "Superseded by the 18-REVIEW WR-02 fix (18-REVIEW-FIX.md, 57c6a39). The hidden set is now read before the undelivered rows, so a rematch committing between the two reads cannot push a stale pre-hide preview. The constraint existed to keep 18-12 minimal. The reorder adds no write, leaves endMatch, BlockService and sendMessage untouched, and ChatServiceDeliverUnreadTest pins the new order."
    accepted_by: "andre.campos"
    accepted_at: "2026-10-05T13:13:46Z"
re_verification:
  previous_status: gaps_found
  previous_score: 74/75
  earlier_status: "passed 75/75 (2026-10-05T10:39Z, went stale)"
  trigger: "stale digest: covered source changed after the 2026-10-05T10:39Z pass, in commits 57c6a39, 5cc4776 and 8a4a95f"
  gaps_closed:
    - "18-12 truth 5 / prohibition-1 deliverUnreadMessages clause: PASSED (override), accepted by andre.campos 2026-10-05T13:13:46Z (the WR-02 reorder supersedes the byte-identical constraint)"
    - "18-REVIEW WR-02 (prior advisory 1): deliverUnreadMessages now resolves the hidden set before it reads the undelivered rows (57c6a39). ChatServiceDeliverUnreadTest pins the order with verifyOrder (8a4a95f)."
  gaps_remaining: []
  regressions:
    - "18-12 truth 5 ('ChatService.deliverUnreadMessages: only its KDoc changes, and the code is byte-identical') and the matching part of 18-12 prohibition 1 no longer hold literally (WR-02 reorder). This is intentional and resolved by the accepted override, so it is not a regression."
behavior_unverified_items: []
advisory:
  - finding: "18-12 prohibitions 1 and 3 are `verification: test`, but no test enforces them. 'MUST NOT change endMatch / BlockService / sendMessage' is enforced by diff observation only. 'MUST NOT mark delivered row by row' is enforced by code reading only. MatchServiceTest uses a relaxed MessageRepository mock and does not verify that `saveAll`/`find*` are never called from MatchService."
    category: other
    reason: "Per the test-tier fail-closed rule these are flagged, not green. I observed in code that both hold: `git diff add8a2f HEAD` shows no hunk in MatchService.endMatch, BlockService or ChatService.sendMessage, and MatchService loads no Message entities. The only exception is the deliverUnreadMessages clause of prohibition 1, which is the gap above. Resolve by accepting them as judgment-tier or adding a `verify(exactly = 0) { messageRepository.saveAll(any<List<Message>>()) }` check."
    evidence_status: "code observation only"
  - finding: "5cc4776 (outside plan scope): findOrCreateConversation now uses a native `INSERT ... ON CONFLICT (match_id) DO NOTHING` and writes participants only when it inserted (returns 1). setPreserveReceiveOrder(true) is set on the STOMP endpoint."
    category: other
    reason: "No phase-18 must-have regresses. The first-message path (`sendMessage(matchId=...)`) used by every BlockEnforcement G-18-1 test now goes through insertIfAbsent, and those 14 tests pass. A residual case that is out of the goal's scope: the loser transaction returns the conversation without participants only if the winner's transaction commits the row but its participant inserts fail. They are in the same transaction, so this cannot happen. The schema (V10) has DB defaults for id/created_at, and gen_random_uuid() is built in on PG16."
    evidence_status: "targeted run green (ChatIntegrationTest 10/10 incl. `concurrent first messages to a new match are all persisted`)"
  - finding: "WR-02 reorder side effect: if a rematch commits after the hidden read and a new post-rematch message commits before the undelivered read, that new message is suppressed on this reconnect (marked delivered, not pushed). It was already pushed live once."
    category: other
    reason: "Benign over-suppression in a millisecond window. No hidden content leaks, and 18-12 truth 3 (sequential case) holds and is tested. Recorded for completeness."
    evidence_status: "reasoning from code"
  - finding: "18-REVIEW IN-01..IN-08 are still `open` in 18-REVIEW-DISPOSITION.md (open: 8 of 10)."
    category: other
    reason: "Info-level post-phase findings outside the goal's finding set (Phase 17 reviews plus the audit item). 17-REVIEW-DISPOSITION.md is `open: 0`. Give them dispositions before ship."
    evidence_status: "none provided"
---

# Phase 18: Address tech debt (post-block redelivery + waitlist review warnings) Verification Report

**Phase Goal:** Close the v2.2 audit tech debt without adding a capability. WebSocket reconnect never pushes previews for blocked or ended conversations (W1). Every open Phase 17 review warning, plus the chosen cheap info items and the stale-Bearer audit item, is fixed. Every finding has a recorded disposition.
**Verified:** 2026-10-05T13:16:00Z
**Status:** passed (75/75, one must-have PASSED by an accepted override)
**Re-verification:** Yes. The 10:39Z pass (passed 75/75) went stale after commits 57c6a39, 5cc4776 and 8a4a95f. The 13:10Z pass found 74/75, because 18-12 truth 5 failed as written. andre.campos then accepted an override for it (2026-10-05T13:13:46Z), which this pass applies. Source is unchanged since the 13:10Z pass, so I did not re-run Gradle; the test evidence below comes from that run.

Context: HEAD is `5fae54f`. All phase code is committed. No source is unstaged or staged (only `18-UAT.md` and this report are staged). I read the committed tree and `git diff add8a2f HEAD -- src` (the 18-12 baseline). The source delta since the baseline is 9 files: MessageRepository, MatchService, ChatService, ConversationRepository and WebSocketConfig in `src/main`; MatchServiceTest, BlockEnforcementIntegrationTest, ChatIntegrationTest and the new ChatServiceDeliverUnreadTest in `src/test`.

## Goal Achievement

ROADMAP Phase 18 has no `success_criteria` array. The truths are the five goal clauses, the 63 PLAN truths from 18-01..18-11, and the 7 truths from 18-12 (75 total; G1-G5 overlap the plan truths and aren't counted separately).

### Goal-level truths

| # | Goal clause | Status | Evidence |
|---|-------------|--------|----------|
| G1 | Reconnect never pushes previews for blocked or ended conversations (W1) | VERIFIED | `deliverUnreadMessages` (ChatService.kt:299-340) still calls `findHiddenConversationIdsForUser` once, partitions the rows, marks suppressed rows delivered without pushing, and pushes only visible ones. After 57c6a39 the hidden set is read first (:306), so a rematch committing mid-call can't produce a push for a still-hidden conversation, nor a stale pre-hide push. Suppression point 2 (MatchService.kt:50) is intact. BlockEnforcementIntegrationTest 14/14 and ChatServiceDeliverUnreadTest 2/2 passed in my run. |
| G2 | Every open Phase 17 review warning fixed | VERIFIED | `git diff add8a2f HEAD` touches no 18-02..18-10 file. The last full suite on that code was 514/0/0 (1 skipped). |
| G3 | Chosen cheap info items fixed | VERIFIED | Files untouched since the prior pass. |
| G4 | Stale-Bearer audit item fixed | VERIFIED | Files untouched (JwtAuthenticationFilter, RequestPaths, AdminTokenFilter). |
| G5 | Every finding has a recorded disposition | VERIFIED | `17-REVIEW-DISPOSITION.md` is `open: 0`. That ledger plus the audit item is the goal's finding set. `18-REVIEW-DISPOSITION.md` now records WR-01 and WR-02 as `fixed`. Its 8 IN rows are post-phase info findings (advisory). |

### 18-12 must-have truths (gap closure G-18-1), re-checked on committed code

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Block path, no reconnect between: all pre-block messages delivered at rematch; first reconnects of B and A push 0 and return 0 | VERIFIED | `pre-block messages do not resurface when the pair rematches before the recipient reconnects` passed (my run, 13:07Z). Helper at BlockEnforcementIntegrationTest.kt:410-453 asserts `[true,true,true]` at rematch, 0 notifications and 0 return for both. The helper's first `sendMessage(matchId=…)` now goes through 5cc4776's `insertIfAbsent` path, so the new conversation-create code is exercised here too. |
| 2 | Same on unmatch path | VERIFIED | `pre-unmatch messages do not resurface …` passed. |
| 3 | No over-suppression: post-rematch message pushed live once, starts undelivered, pushed once on next reconnect | VERIFIED | Control block (helper :442-452) passed on both paths. The WR-02 reorder doesn't affect this sequential case: the hidden read sees the match active. |
| 4 | D-05 as amended: one set-based `@Modifying` UPDATE, reactivation branch only, before `MatchCreatedEvent`; endMatch and BlockService unchanged | VERIFIED | MessageRepository.kt:47-49 is a single JPQL UPDATE. The only call is MatchService.kt:50, inside `if (existing.endedAt != null)`, after `save` and before `publishEvent`. MatchServiceTest 5/5 passed (verifyOrder plus `exactly = 0` on new/active/race paths). `git diff add8a2f HEAD` has no endMatch or BlockService hunk. |
| 5 | ChatService.deliverUnreadMessages: KDoc only, code byte-identical; KDoc names both suppression points | **PASSED (override)** | Override: superseded by the 18-REVIEW WR-02 fix (hidden-first read order, 57c6a39). Accepted by andre.campos on 2026-10-05T13:13:46Z. The KDoc half holds: it names "Suppression point 1 (here)" and "Suppression point 2 (`MatchService.createMatch`, reactivation branch)" and links `markAllDeliveredForMatch`. The code half doesn't: 57c6a39 moved the `hidden` read above the undelivered read (`git diff add8a2f HEAD -U0`: hunks `@@ -277,0 +304,4 @@` and `@@ -283 +312,0 @@`). This is the WR-02 fix, which the prior verifier recommended and 18-REVIEW-FIX.md records. |
| 6 | 18-CONTEXT.md D-05 keeps its sentence and gains a dated G-18-1 amendment; endMatch unchanged | VERIFIED | Amendment line present (committed in 68e0a33). The original D-05 sentence is intact. |
| 7 | No regression: BlockEnforcement, match, chat, moderation packages and PushTriggerIntegrationTest pass | VERIFIED | My targeted run (`--tests com.catspell.api.moderation.* --tests com.catspell.api.match.* --tests com.catspell.api.chat.* --tests com.catspell.api.push.PushTriggerIntegrationTest`): BUILD SUCCESSFUL, 12 classes, 84 tests, 0 failures, 0 errors, 0 skipped. ChatIntegrationTest 10/10 includes the 5cc4776 regression test. |

### Plan must-have truths, 18-01..18-11 (regression check)

| Plan | Truths | Status | Regression evidence |
|------|--------|--------|---------------------|
| 18-01 W1 | 6 | 6 VERIFIED | The hidden-set query (`findHiddenConversationIdsForUser`) is unchanged; 5cc4776 only adds `insertIfAbsent` to ConversationRepository. Suppress-and-mark semantics are unchanged, and only the read order moved. The 18-01 prohibition "no change to MatchService.endMatch" holds. Tests A-G pass among the 14. |
| 18-02 WR-10 | 6 | 6 VERIFIED | Files untouched since add8a2f. ReportService/Report* tests green in my run (ReportServiceIntegrationTest 7, ReportEndpointIntegrationTest 9, ReportNotificationIntegrationTest 2). |
| 18-03 .. 18-10 | 51 | 51 VERIFIED | No file in these plans' `files_modified` appears in `git diff add8a2f HEAD`. The last full-suite run on this code was 514/0/0, recorded in the prior pass. The 5 new src commits don't touch them. |
| 18-11 docs/keys/disposition | 6 | 6 VERIFIED | `17-REVIEW-DISPOSITION.md` `open: 0`. application.yml and CONFIGURATION.md untouched. |

**Score:** 75/75 truths verified (74 VERIFIED, 1 PASSED (override); 0 present-but-behavior-unverified; 0 failed).

### Commit 5cc4776 (outside plan scope): regression analysis

| Change | Risk to a phase-18 must-have | Finding |
|--------|------------------------------|---------|
| `findOrCreateConversation` → `insertIfAbsent` (native `INSERT … ON CONFLICT (match_id) DO NOTHING`), participants written only by the inserting transaction | Could break conversation creation on the block/rematch test paths, or reconnect redelivery, which reads participations | No regression. A rematch reuses the existing conversation (the first `findByMatchId` returns it). The insert path is exercised by every G-18-1 and W1 integration test, all passing. V10 has `DEFAULT gen_random_uuid()` and `DEFAULT NOW()`. UNIQUE index `idx_conversations_match` is the ON CONFLICT arbiter. |
| `setPreserveReceiveOrder(true)` on `/ws` | Affects every STOMP inbound path (chat send, push triggers) | No regression. ChatIntegrationTest 10/10, ConversationListIntegrationTest 10/10, PushTriggerIntegrationTest 4/4. |
| `sendMessage` block and ended checks (D-06) | Must still run after conversation resolution | The `sendMessage` body has no hunk. The checks at ChatService.kt:67-72 still follow `findOrCreateConversation`. |
| ChatIntegrationTest rewrite (sleeps → polling helper) | Could weaken assertions | Assertions are stronger: exact counts (5, 35, 6), `conversations.length() == 1`, and newest-first order checks are kept. |

### Required Artifacts (18-12, plus new tests)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `chat/model/MessageRepository.kt` | `fun markAllDeliveredForMatch` | VERIFIED | Lines 47-49. Wired from MatchService.kt:50. |
| `match/service/MatchService.kt` | `messageRepository.markAllDeliveredForMatch(existing.id!!)` | VERIFIED | Reactivation branch only. |
| `moderation/BlockEnforcementIntegrationTest.kt` | "rematches before the recipient reconnects" | VERIFIED | 2 tests plus a shared helper, passing. |
| `match/MatchServiceTest.kt` | `markAllDeliveredForMatch` | VERIFIED | 5/5. |
| `chat/service/ChatService.kt` | KDoc contains `markAllDeliveredForMatch` | VERIFIED (artifact); truth 5 PASSED (override) | KDoc present. Code changed per WR-02. |
| `18-CONTEXT.md` | contains `G-18-1` | VERIFIED | D-05 amendment. |
| `chat/ChatServiceDeliverUnreadTest.kt` (8a4a95f) | pins WR-02 order | VERIFIED | `verifyOrder { findHiddenConversationIdsForUser; findByConversationIdIn…DeliveredFalse… }`, exactly 1 push for the visible conversation, and both rows marked delivered. 2/2 passed. Swapping the reads back would fail the verifyOrder. |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| MatchService.createMatch (`endedAt != null`) | MessageRepository.markAllDeliveredForMatch | after `save`, before `publishEvent`, same swipe transaction | WIRED |
| DiscoveryService.swipe (`@Transactional`) | MatchService.createMatch | mutual LIKE; the tests reach it via `matchPair` → `POST /api/discovery/swipe` | WIRED |
| ChatService.deliverUnreadMessages | ConversationRepository.findHiddenConversationIdsForUser | one call per reconnect, now BEFORE the undelivered read | WIRED (order pinned by unit test) |
| ChatService.findOrCreateConversation | ConversationRepository.insertIfAbsent | native ON CONFLICT insert, then `findByMatchId` | WIRED (5cc4776; exercised by integration tests) |
| BlockEnforcementIntegrationTest | brokerChannel | ChannelInterceptor capture, `notificationsTo(` | WIRED |
| prior links from 18-02..18-11 | - | unchanged files | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| Rematch sweep | `messages.delivered` | JPQL bulk UPDATE | yes | FLOWING (JDBC `SELECT delivered` in the test) |
| Reconnect pushes | undelivered minus hidden | derived query plus JPQL hidden-id query | yes | FLOWING |
| Conversation create | `conversations` row plus 2 participants | native insert plus JPA saves | yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| G-18-1, W1, WR-02, chat regressions | `./gradlew test --tests 'com.catspell.api.moderation.*' --tests 'com.catspell.api.match.*' --tests 'com.catspell.api.chat.*' --tests 'com.catspell.api.push.PushTriggerIntegrationTest'` (my run, 5m 23s, 13:05-13:08Z) | BUILD SUCCESSFUL. 84 tests, 0 failures, 0 errors, 0 skipped. Classes: ChatIntegrationTest 10, ChatServiceDeliverUnreadTest 2, ConversationListIntegrationTest 10, MatchIntegrationTest 8, MatchServiceTest 5, BlockEndpointIntegrationTest 6, BlockEnforcementIntegrationTest 14, BlockServiceIntegrationTest 7, ReportEndpointIntegrationTest 9, ReportNotificationIntegrationTest 2, ReportServiceIntegrationTest 7, PushTriggerIntegrationTest 4 | PASS |
| Full suite | not re-run | The new commits touch only the chat, match and moderation code covered above. The last full run (514/0/0) predates only the WR-02 reorder, 5cc4776 and the new test, all of which my targeted run covers. | SKIP (targeted run sufficient) |

### Probe Execution

No probes declared in the PLANs or SUMMARYs, and no `scripts/*/tests/probe-*.sh` exist. SKIPPED.

### Prohibitions (18-12)

| Prohibition | Tier | Enforcement evidence | Disposition |
|-------------|------|----------------------|-------------|
| MUST NOT change endMatch, BlockService, sendMessage or the code of deliverUnreadMessages | test | No test enforces it. The diff shows endMatch, BlockService and sendMessage untouched. The deliverUnreadMessages clause is not met literally (WR-02 reorder). | deliverUnreadMessages clause: PASSED (override, same acceptance as truth 5); the rest is observed only (advisory) |
| MUST NOT change message content or delete rows | test | The integration helper reads every pre-hide row back by id after the sweep (`delivered(it)`). The UPDATE sets only `delivered`. | verified (test plus code) |
| MUST NOT mark delivered row by row | test | One `@Modifying` statement. MatchService loads no Message. No test asserts the absence of `saveAll`. | flagged unverified-by-test (observed in code; advisory) |

### Requirements Coverage

| Requirement | Source Plans | Status | Evidence |
|-------------|-------------|--------|----------|
| MOD-02 | 18-01, 18-11, 18-12 | SATISFIED (hardened) | Block suppression holds across a rematch, sequentially and now under the WR-02 interleaving |
| MOD-03 | 18-01, 18-11, 18-12 | SATISFIED (hardened) | Rows kept. No stale previews after an unmatch and a later rematch. |
| MOD-06 | 18-02 | SATISFIED | Unchanged; Report tests green |
| INV-02 | 18-05, 18-10, 18-11 | SATISFIED | Unchanged |
| WAIT-01..WAIT-04 | 18-04..18-11 | SATISFIED | Unchanged |

REQUIREMENTS.md maps no IDs to Phase 18 by design, so none are orphaned.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| 9 src files changed since add8a2f | - | TBD/FIXME/XXX/TODO/HACK | none found | - |
| chat/model/MessageRepository.kt | 47-49 | Bare `@Modifying` (IN-07) | Info | No current caller holds managed Message entities |

### Human Verification Required

None. The override decision is recorded (andre.campos, 2026-10-05T13:13:46Z). 18-UAT.md test 7 now describes the WR-02 reorder instead of "(code byte-identical)".

### Gaps Summary

There are no gaps. 18-12 truth 5, and the deliverUnreadMessages clause of prohibition 1, said that function would change only in its KDoc. Commit 57c6a39 also swapped its two reads to close the 18-REVIEW WR-02 reconnect-vs-rematch race. andre.campos accepted that deviation as an override, so the truth counts as PASSED (override). Every other must-have holds on the committed code. G-18-1 holds: the rematch sweep tests pass for both block and unmatch, along with the post-rematch control. The out-of-scope 5cc4776 change to conversation creation and STOMP ordering doesn't regress any phase-18 must-have.

Not blocking (see Advisory): prohibitions 1 and 3 are test-tier but no test enforces them, and 18-REVIEW IN-01..IN-08 are still `open` and need dispositions before ship.

---

_Verified: 2026-10-05T13:16:00Z_
_Verifier: Claude (gsd-verifier)_
