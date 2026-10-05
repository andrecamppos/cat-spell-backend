# API Coverage — Invite-Only Access & Referral

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
>
> Note: this phase builds the application's own invite/referral REST surface
> (backed by PostgreSQL); it does not consume an external third-party API/SDK.
> The verify:pre detector flagged generic "integration"/"REST"/"endpoint" prose,
> so this matrix enumerates the invite capability surface delivered by the phase.
> Every capability was built and is covered by passing integration tests
> (see 16-01..16-04 SUMMARY.md), hence all INTEGRATE with no opt-outs.

| capability | decision | reason |
|---|---|---|
| invite-schema-and-constraints | INTEGRATE | |
| invite-issuance-admin-endpoint | INTEGRATE | |
| invite-code-hashing-at-rest | INTEGRATE | |
| invite-single-use-consumption | INTEGRATE | |
| invite-validation-generic-403 | INTEGRATE | |
| invite-gate-on-registration | INTEGRATE | |
| public-mode-gate-off | INTEGRATE | |
| referral-attribution | INTEGRATE | |
| admin-token-auth | INTEGRATE | |
| enumeration-safe-responses | INTEGRATE | |
