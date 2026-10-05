# Phase 18: Address tech debt: post-block redelivery + waitlist review warnings - Context

**Gathered:** 2026-10-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Close the v2.2 milestone-audit tech debt on two fronts, adding no new user capability:

1. **W1 (MOD-02, MOD-03):** WebSocket reconnect redelivery (`ChatService.deliverUnreadMessages`) must stop pushing previews for conversations that are hidden: the match has ended, or the pair is blocked either way.
2. **Phase 17 review findings:** fix every open warning-level finding and a chosen set of cheap info-level items across the waitlist, rate limiter, trusted-proxy matcher and admin boundary. Then update `17-REVIEW-DISPOSITION.md` so each finding has a recorded outcome.

**In scope (finding IDs as they appear in the two Phase 17 reviews):**
- First review (`git show aa09317:.planning/phases/17-waitlist-landing-page-api/17-REVIEW.md`): WR-03, WR-04, WR-05, WR-06, WR-07, WR-08, IN-01 (hardcoded TTL in copy), IN-02 (admin KDoc, fixed by the central guard), IN-06 (broad JWT skip prefix), IN-07 (redirect URLs parsed at runtime). Also the two warnings dropped from the disposition file: the old WR-01 (unbounded bucket maps) and the old WR-02 (~72 mails/day per victim).
- Current review (`.planning/phases/17-waitlist-landing-page-api/17-REVIEW.md`): WR-01 (non-octet CIDR tests), WR-02 (`ip:port` hops fail open), IN-01 (dot-segment spellings, key on the normalized servletPath), IN-02 (rename `isIpLiteral`), IN-04 (docs env-var table), IN-05 (one-shot WARN on untrusted X-Forwarded-For).
- Audit item "Unchecked": admin endpoints are `permitAll` but not in the JWT skip list, so a stale or invalid Bearer header may be rejected before the admin guard runs. Verify it, then fix it.

**Out of scope:** W2 (legacy accounts have no path to set a DOB), W3 (a waitlist invite isn't bound to the waitlist email; by design), the production reverse-proxy shape check (done at deploy), first-review IN-03 (admin list pagination), current-review IN-03 (IPv4-mapped CIDR message, zone-scoped peers).

</domain>

<decisions>
## Implementation Decisions

### Scope
- **D-01:** Close **all open warning-level findings** listed in the Phase Boundary, plus the two dropped warnings. Re-add the dropped ones under **new IDs** (suggested: WR-10 unbounded bucket maps, WR-11 per-email daily volume) so the earlier IDs aren't reused again.
- **D-02:** Info-level items: take only the cheap ones listed in the Phase Boundary, plus the stale-Bearer verification. Pagination, IPv4-mapped CIDR and zone-scoped peers get disposition `deferred`, with the reason in the Source cell.
- **D-03:** The bucket-map eviction fix covers **every** service using the unbounded `ConcurrentHashMap<String, Bucket>` pattern: `WaitlistService`, `EmailVerificationService`, `EmailChangeService`, `PasswordResetService`, and `RateLimitFilter`. Use one shared helper, not four copies.

### W1: Redelivery on reconnect
- **D-04:** In `deliverUnreadMessages`, an undelivered message in a hidden conversation (`match.endedAt != null`, or `isBlockedEitherWay` for the pair) is **skipped and marked `delivered = true`**. No preview is pushed, and it can't resurface if the pair later rematches (the same match row is reactivated, see `MatchService.kt:41-46`). The message row itself is kept as server-side evidence (Phase 13 D-04/D-07). — **Reversibility:** costly — afterwards, a suppressed message can't be told apart from one that was pushed; the `delivered` flag is the only record.
- **D-05:** The fix lives **only on the reconnect path**. Block/unmatch teardown (`MatchService.endMatch`) is unchanged. Resolve hidden conversations in a set-based way (one query or join), not one block lookup per conversation.
  - **Amended 2026-10-05 (UAT G-18-1, plan 18-12):** One exception: `MatchService.createMatch`'s reactivation branch (`existing.endedAt != null`) runs one set-based UPDATE, `MessageRepository.markAllDeliveredForMatch`, which marks every still-undelivered message of the match's conversation delivered before `MatchCreatedEvent` is published. Why: the reconnect path alone misses a rematch that happens before the recipient's next reconnect (18-REVIEW WR-01). Block/unmatch teardown in `MatchService.endMatch` stays unchanged, and there is still no per-message loop.
- **D-06:** No additional block re-check at push time for a send-vs-block race. The send path already rejects a blocked or ended pair before saving.

### Waitlist re-join and hijack (WR-03, WR-04, dropped WR-02 → WR-11)
- **D-07:** **Resend cooldown + daily cap.** A PENDING re-join inside a configurable cooldown (default 15 min) returns the same enumeration-safe `202` and does nothing: no token rotation, no email. The per-email bucket widens to 3 per 24 h. Both values are configurable `app.waitlist.*` keys (`@Value`, no `@ConfigurationProperties`). This refines Phase 17 D-04: outside the cooldown, a PENDING re-join still rotates the token and sends a fresh email.
- **D-08:** **Pin the first-stored address.** A re-join never changes `waitlist_entries.email`. Drop `e.email = :email` from the rotate UPDATE. The fresh confirm link and the later invite both go to the address stored at first insert. D-03 normalization from Phase 17 (trim + lowercase + strip `+suffix`) stays as is. Correct the `WaitlistEmailNormalizer` KDoc, which claims distinct mailboxes are never merged.
- **D-09:** **Confirm email copy:** remove the false "your spot is still confirmed" sentence. Say that only the most recent email's link works, and that joining again sends a fresh one (subject to the cooldown). Render the configured TTL instead of a hardcoded "7 days" (first-review IN-01). Keep **one** error URL; the landing-page redirect contract doesn't change.

### Waitlist convert I/O (WR-05, WR-06)
- **D-10:** **Flush, send, roll back.** Flush the invite row (`saveAndFlush` or an explicit flush) before the email is sent. The send stays synchronous inside the transaction, so a provider failure rolls back INVITED and the invite row and returns the existing 502, which the operator can retry. Give the send a strict timeout. Log the failure at WARN with the exception type and no email address. Add a `cause` parameter to `WaitlistInviteDeliveryException` and chain it. Also handle the `EmailSendStatus` non-success branch, logging without the address.

### Admin boundary (WR-08, stale-Bearer item)
- **D-11:** **Enforce the admin token centrally** on `/api/admin/**` with a `OncePerRequestFilter` or `HandlerInterceptor` that reuses the existing constant-time, deny-by-default `AdminTokenGuard` check, so any future handler is protected automatically. Remove the per-handler `require(...)` calls or keep them as defense in depth (Claude's discretion). This also makes the 401 come before parameter conversion (first-review IN-02).
- **D-12:** Add `/api/admin` to the JWT filter's skip list, so a stale or invalid Bearer header can't 401 the operator before the admin check runs. First reproduce the failure with a test.
- **D-13:** **Throttle + minimum length.** Put a strict per-IP bucket on `/api/admin/**` (e.g. 5/min, configurable). **Fail startup** when `app.invite.admin-token` is non-blank and shorter than 32 characters. Blank still means deny all. — **Reversibility:** costly — any deploy with a short `INVITE_ADMIN_TOKEN` won't start until the token is rotated; document this in `docs/CONFIGURATION.md`.

### Rate limiter (WR-07, current WR-02, current IN-01, IN-05)
- **D-14:** **Separate join bucket + CORS on 429.** `POST /api/waitlist` gets its own bucket map and capacity key, apart from `/api/auth/*`. The 429 response includes `Access-Control-Allow-Origin` (and the headers needed to expose `Retry-After`) for origins in `WAITLIST_ALLOWED_ORIGINS`, so the landing page can read it. Document that server-to-server mode requires the landing server in `RATE_LIMIT_TRUSTED_PROXIES`, forwarding the visitor IP.
- **D-15:** Current WR-02: use the review's suggested fix. Canonicalize each X-Forwarded-For hop (strip `[...]` and `:port`, re-render from the parsed bytes) before both the trust check and key selection. A hop that still isn't a literal **falls back to `remoteAddr`** (fail safe). Update the malformed-hop test to expect the peer's bucket, add a rotating-port test, and update `docs/CONFIGURATION.md`.
- **D-16:** Current IN-01: match throttled paths on the container-normalized path (`servletPath + pathInfo`, or an equivalent normalization), not only `UrlPathHelper`. Add `/api/auth/./login` to the spellings test.
- **D-17:** Current IN-05: log a WARN **once** (guarded by an `AtomicBoolean`) the first time an untrusted peer sends X-Forwarded-For, naming the peer address and the config key.

### Bucket eviction (dropped WR-01 → WR-10)
- **D-18:** **Add Caffeine** (`com.github.ben-manes.caffeine:caffeine`, version managed by the Spring Boot BOM, no explicit version) as the backing store for every bucket map in D-03: `maximumSize` plus `expireAfterAccess` equal to that bucket's refill window. The user approved this new dependency for this phase. — **Reversibility:** reversible — it sits behind one shared helper.

### Claude's Discretion
- Exact config key names and defaults: cooldown, daily cap, admin bucket capacity, join bucket capacity, Caffeine max size, send timeout. Follow the existing `app.*` / `rate-limit.*` + `@Value` conventions, and declare each key in `application.yml` and `docs/CONFIGURATION.md`.
- Filter vs interceptor for D-11, and its ordering relative to `RateLimitFilter` and the JWT filter.
- How the send timeout is applied for D-10 (sender-level or call-level).
- Cheap first-review one-liners that also got dropped from the disposition file: IN-04 (remove the unused `findByNormalizedEmail`) and IN-05 (build links with `UriComponentsBuilder`). Include them if trivial; either way, give them a disposition under new IDs.
- How the remaining "cheap" items are implemented: exact-match JWT skip list (`/api/waitlist`, `/api/waitlist/confirm`), redirect URLs parsed in the constructor so a bad value fails at startup, `isIpLiteral` renamed or made private, and the docs env-var table and Production row.
- Plan and wave grouping. A natural split: W1 chat fix / waitlist service + email / rate limiter + trusted proxy / admin boundary / shared eviction helper + disposition bookkeeping.
- Disposition bookkeeping format: new IDs for the dropped findings, `fixed` with the plan reference, and `deferred` with a reason for out-of-scope items.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Audit and findings
- `.planning/v2.2-MILESTONE-AUDIT.md` — Source of this phase. W1 analysis (integration row 1g), the full tech-debt list, and the stale-Bearer "Unchecked" item.
- `.planning/phases/17-waitlist-landing-page-api/17-REVIEW.md` — Current (post-17-09) review: WR-01, WR-02, IN-01..IN-05, with suggested fixes and code snippets.
- `git show aa09317:.planning/phases/17-waitlist-landing-page-api/17-REVIEW.md` — First Phase 17 review: the detailed text and fixes for WR-01 (unbounded maps) and WR-02 (72/day), which were dropped, plus WR-03..WR-08 and IN-01..IN-07 of that review.
- `.planning/phases/17-waitlist-landing-page-api/17-REVIEW-DISPOSITION.md` — Disposition record to update at the end of the phase (dropped IDs need re-adding under new IDs).

### Prior decisions this phase refines
- `.planning/phases/17-waitlist-landing-page-api/17-CONTEXT.md` — D-03 normalization (kept), D-04 re-join behavior (refined by D-07/D-08 here), D-07 rate limiting, D-09/D-10 admin convert.
- `.planning/phases/13-blocking-unmatch/13-CONTEXT.md` — D-02/D-03 bidirectional block enforcement, D-04/D-05/D-07 hidden conversations with history retained, D-08 rematch semantics.

### Operator docs
- `docs/CONFIGURATION.md` — Must document every new or changed key, the admin-token minimum length, the server-to-server trusted-proxy requirement, and the corrected rate-limiting section.
- `AGENTS.md` — Podman (not Docker), `@Value`-only config binding, test command.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BlockService.isBlockedEitherWay` (`moderation/service/BlockService.kt:79`) and `blockRepository.existsBlockBetween`: the bidirectional predicate. For D-05, prefer a set-based repository query over calling it once per conversation.
- `AdminTokenGuard` (`common/security/AdminTokenGuard.kt`): the constant-time, deny-by-default check to reuse in the central filter (D-11).
- `TrustedProxyMatcher` (`common/security/TrustedProxyMatcher.kt`): `parseLiteral` / `isIpLiteral` are the base for hop canonicalization (D-15).
- Bucket4j (`com.bucket4j:bucket4j-core:8.10.1`) is already a dependency. The per-email bucket pattern repeats in `EmailVerificationService.kt:35-37`, `EmailChangeService.kt:42-44`, `PasswordResetService.kt`, and `WaitlistService.kt:47-55`.
- The `ReportNotificationListener` AFTER_COMMIT pattern exists, but D-10 deliberately keeps the invite send synchronous.

### Established Patterns
- Config through `@Value` with defaults in `application.yml` (`${ENV:default}`). No `@ConfigurationProperties`.
- Pretend-not-exist 404 for hidden or blocked resources (Phase 13 D-01/D-02). Enumeration-safe identical `202` for waitlist joins (Phase 17 D-04).
- Soft state only. Never hard-delete matches or messages.
- Integration tests use Testcontainers. The full suite takes ~14 min / 411 tests, so run gates in the background.

### Integration Points
- `chat/service/ChatService.kt:263-291` `deliverUnreadMessages`, called from `chat/service/WebSocketSessionListener.kt` (async, 200 ms delay). Uses `MessageRepository.findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc` and `ConversationParticipantRepository.findByUserId`.
- `waitlist/service/WaitlistService.kt` (join/rotate lines ~70-86, convert lines ~141-161), `waitlist/model/WaitlistEntryRepository.kt` (`rotatePendingToken` UPDATE), `waitlist/service/WaitlistEmailNormalizer.kt`.
- `email/service/WaitlistConfirmEmailRenderer.kt`, `email/service/WaitlistInviteEmailRenderer.kt`.
- `invite/service/InviteService.kt` (`create`: save → flush for D-10).
- `common/security/RateLimitFilter.kt` (HIGHEST_PRECEDENCE, ahead of CORS), `common/security/JwtAuthenticationFilter.kt:27` (skip list), `common/config/SecurityConfig.kt:39-43, 76`.
- `waitlist/controller/WaitlistController.kt:50-52` (redirect URLs), `waitlist/controller/WaitlistAdminController.kt`, `invite/controller/InviteAdminController.kt`, `common/exception/GlobalExceptionHandler.kt` (`handleWaitlistInviteDelivery`).
- Existing tests to extend: `src/test/kotlin/com/catspell/api/common/{RateLimitBypassIntegrationTest,RateLimitIntegrationTest,RateLimitTrustedProxyIntegrationTest,TrustedProxyMatcherTest}.kt`, plus the Phase 13 block-enforcement integration tests.

</code_context>

<specifics>
## Specific Ideas

- The review files include concrete fix snippets: the `canonicalize(hop)` sketch and the non-octet CIDR edge tests (`172.16.0.0/12`, `2001:db8:ab00::/41`) in the current review, and the resend-cooldown `WHERE ... e.updatedAt < :resendCutoff` clause in the first review's WR-02. Use them as the starting point.
- W1 must be proven with a test: a message is left undelivered, then a block (and separately an unmatch) happens, then the recipient reconnects. Expect no `/queue/notifications` push and `delivered = true`. After a rematch and another reconnect, still no push.

</specifics>

<deferred>
## Deferred Ideas

- **W2:** an endpoint that lets legacy accounts without a DOB set one. It's a new capability and needs its own phase.
- **W3:** binding a waitlist-conversion invite to the waitlist email. By design (bearer code, INV-04).
- **Production reverse-proxy shape check:** set `RATE_LIMIT_TRUSTED_PROXIES` / `WAITLIST_ALLOWED_ORIGINS` at deploy time (deferred UAT test 3).
- **Invite email in a real client:** UAT follow-up at deploy.
- **First-review IN-03:** operator waitlist list pagination past 500 rows.
- **Current-review IN-03:** a clearer startup error for IPv4-mapped CIDR entries, and support for zone-scoped (link-local) peers.
- **Shared/distributed buckets** (bucket4j `ProxyManager` over JDBC/Redis) for multi-instance deployments. Caffeine bounds memory per instance only.

</deferred>

---

*Phase: 18-address-tech-debt-post-block-redelivery-waitlist-review-warn*
*Context gathered: 2026-10-03*
