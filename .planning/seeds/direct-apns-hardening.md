---
title: Direct APNs Integration (iOS Reliability Hardening)
trigger_condition: When iOS push delivery reliability becomes a concern, FCM's
  APNs bridge shows silent failures, or the app nears public launch.
planted_date: 2026-07-17
---

# Direct APNs Hardening

## Idea

The initial push notifications implementation uses **FCM-only** (FCM relays to iOS
via its APNs bridge). This is the fastest path but adds a mapping layer that can
silently fail if APNs entitlements/signing are misconfigured, and FCM's iOS
delivery is less reliable than talking to APNs directly for time-sensitive alerts.

Add a **direct APNs provider** (HTTP/2, token-based `.p8` JWT auth) behind the
existing provider abstraction so iOS tokens route straight to Apple while Android
continues via FCM.

## Why Deferred

- FCM-only is sufficient to ship and validate the feature.
- Direct APNs doubles the integration surface (certs/keys, environment handling,
  HTTP/2 connection management) for reliability gains that only matter at scale or
  near launch.

## When To Revisit

- iOS users report missing/delayed notifications.
- Delivery metrics show elevated iOS failure rates through FCM.
- Approaching public launch where match/message alerts are engagement-critical.

## Notes

- Use APNs **token-based auth** (`.p8` JWT), not certificates (certs expire and
  cause sudden outages). Store the key in a secret manager.
- Provider abstraction (`send(token, payload)` routing by platform) already
  accommodates this — no call-site changes expected.
- Map APNs `410 Gone` to the same token-pruning path as FCM `UNREGISTERED`.
