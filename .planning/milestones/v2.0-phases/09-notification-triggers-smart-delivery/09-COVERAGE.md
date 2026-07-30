# API Coverage — Firebase Cloud Messaging (FCM Admin SDK)

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
>
> Phase 9 extends the Phase 8 FCM integration (`FcmPushProvider` /
> `FirebaseMessaging.send`) with the smart-delivery decision and per-conversation
> collapse. This matrix is the durable subtraction record for the FCM messaging
> surface as it stands after Phase 9.

| capability | decision | reason |
|---|---|---|
| send (single token) | INTEGRATE | Core per-recipient delivery — `FcmPushProvider.send(token, payload)`. |
| notification (title/body) | INTEGRATE | Match + message alerts carry a title/body via `Notification.builder`. |
| data payload (deep-link keys) | INTEGRATE | `putAllData` carries matchId / conversationId / messageId / senderId for client deep-linking. |
| android collapse_key | INTEGRATE | Phase 9 per-conversation collapse via `AndroidConfig.collapse_key`. |
| apns collapse-id | INTEGRATE | Phase 9 per-conversation collapse via APNs `apns-collapse-id` header. |
| dry-run validation | INTEGRATE | `send(token, payload, dryRun)` supports validation-only sends. |
| unregistered-token handling | INTEGRATE | `MessagingErrorCode.UNREGISTERED` → `UNREGISTERED` status drives stale-token pruning in `PushSendService`. |
| error handling (send failures) | INTEGRATE | `FirebaseMessagingException` caught → `ERROR` status; async listener swallows so it never rolls back the domain write. |
| multicast / sendEach (batch send) | OPT-OUT | Fan-out is sequential per-device token; match fan-out is at most 2 users, message push is 1 recipient — no batch need yet. |
| topic messaging (subscribe / publish to topics) | OPT-OUT | All pushes are targeted per-device tokens; no broadcast/topic use case in this milestone. |
| condition messaging | OPT-OUT | No topic combinations — depends on topic messaging, which is opted out. |
| android priority / TTL | OPT-OUT | Default priority/TTL acceptable for match + chat alerts; no time-critical override needed yet. |
| apns aps sound / badge | OPT-OUT | No unread-badge or custom-sound feature this milestone; text alert only. |
| webpush config | OPT-OUT | Mobile-only clients (Android/iOS); no web push target. |
| notification image | OPT-OUT | Text-only notifications this milestone; no rich media. |
| fcm analytics label | OPT-OUT | No delivery-analytics requirement for this milestone. |
