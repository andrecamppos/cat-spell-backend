# API Coverage — Firebase Cloud Messaging (firebase-admin 9.9.0)

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
>
> Phase 8 (`push-delivery-foundation`) integrates the FCM `Messaging` surface via
> the `firebase-admin` SDK, confined to `FcmPushProvider`/`FirebaseConfig` behind
> the provider-neutral `PushProvider` seam. This matrix is the subtraction record
> for that surface: every FCM capability is either INTEGRATE or a reasoned OPT-OUT.

| capability | decision | reason |
|---|---|---|
| credential init (service account / FirebaseApp) | INTEGRATE | |
| single-token send (FirebaseMessaging.send) | INTEGRATE | |
| notification message (title/body) | INTEGRATE | |
| data payload (custom key/value map) | INTEGRATE | |
| dry-run send (validate_only) | INTEGRATE | |
| UNREGISTERED error mapping + dead-token pruning | INTEGRATE | |
| config health status (no live send) | INTEGRATE | |
| multicast / sendEach (batch send) | OPT-OUT | not needed yet — Phase 9 fans out per-token via the single-send seam; batch send tracked for a later optimization phase |
| topic messaging (subscribeToTopic / unsubscribeFromTopic / send-to-topic) | OPT-OUT | out of scope — delivery is strictly per-device-token; no topic fan-out in the product |
| condition messaging (topic conditions) | OPT-OUT | out of scope — depends on topics, which are not integrated |
| platform-specific config (AndroidConfig / ApnsConfig) | OPT-OUT | not needed yet — default cross-platform notification is sufficient; per-platform tuning deferred |
| WebPush (WebpushConfig) | OPT-OUT | explicitly out of scope — Platform enum is ANDROID/IOS only; no web push target |
| direct APNs integration | OPT-OUT | out of scope — iOS delivery is routed through FCM, not a direct APNs connection |
