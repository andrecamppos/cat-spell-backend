---
status: complete
phase: 09-notification-triggers-smart-delivery
source: [09-01-SUMMARY.md, 09-02-SUMMARY.md, 09-03-SUMMARY.md]
started: 2026-07-29T16:28:42Z
updated: 2026-07-29T16:38:00Z
---

## Current Test

[testing complete]

## Tests

### 1. isOnline reflects live STOMP sessions
expected: isOnline true with >=1 live session, false only after the last disconnects
result: pass
source: automated
coverage_id: 09-01-D1

### 2. isViewingConversation tracks chat topic subscribe/unsubscribe
expected: reflects /topic/chat/{id} subscribe/unsubscribe, ignores non-chat destinations
result: pass
source: automated
coverage_id: 09-01-D2

### 3. removeSession isolates per-session cleanup
expected: clears presence + subscriptions for that session only, other users untouched
result: pass
source: automated
coverage_id: 09-01-D3

### 4. Match push presence suppression
expected: mutual match pushes offline users, suppresses online ones, payload carries matchId
result: pass
source: automated
coverage_id: 09-02-D1

### 5. Message push payload shape
expected: message pushes recipient only with sender-name title + preview body + deep-link data
result: pass
source: automated
coverage_id: 09-02-D2

### 6. Per-conversation collapse mapping
expected: collapseKey = conversationId maps to FCM AndroidConfig.collapse_key + APNs apns-collapse-id
result: pass
source: automated
coverage_id: 09-02-D3

### 7. Message send decision matrix
expected: send when offline OR online-but-not-viewing; suppress when online AND viewing
result: pass
source: automated
coverage_id: 09-02-D4

### 8. Async dispatch after commit
expected: persisted chat message dispatches notifyMessage asynchronously AFTER commit
result: pass
source: automated
coverage_id: 09-03-D1

### 9. Push failure never blocks/rolls back persistence
expected: slow/failing push neither blocks nor rolls back message persistence; row committed
result: pass
source: automated
coverage_id: 09-03-D2

### 10. Event publication without request-path regression
expected: MatchService/ChatService publish events from @Transactional paths, no regression
result: pass
source: automated
coverage_id: 09-03-D3

### 11. STOMP presence event routing
expected: StompPresenceListener routes SessionConnected/Subscribe/Unsubscribe/Disconnect to the matching registry mutations (online/offline + active-conversation state)
result: pass
note: "Verified via ./gradlew test PushTriggerIntegrationTest (real STOMP session, async dispatch) + PresenceRegistryTest — BUILD SUCCESSFUL"

### 12. Duplicate-match notification suppression
expected: createMatch fires a match notification only on a genuinely new match — NOT on the existing-match return path or the duplicate-key fallback (no double push on a re-swipe)
result: pass
note: "Verified by code review (single publishEvent on new-save branch only) + new regression test MatchServiceTest (4 tests, BUILD SUCCESSFUL): new-match publishes once, existing-match/re-swipe/duplicate-key race publish zero."

## Summary

total: 12
passed: 12
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
