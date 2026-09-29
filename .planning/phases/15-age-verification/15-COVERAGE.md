# API Coverage — age-verification vendor (deferred)

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
>
> Phase 15 delivers a **server-side self-attested DOB gate** behind a local
> `AgeVerifier` seam (`LocalAgeVerifier`). No external age-verification vendor
> API/SDK is integrated in this phase — the seam exists precisely so a vendor
> can drop in later without touching call sites (D-03). Third-party
> age-estimation / ID-verification is explicitly out of scope for v2.2
> (AGE-01/AGE-02 defer the vendor; see 15-CONTEXT.md §Deferred). This matrix
> records that every vendor capability is a deliberate OPT-OUT for now, not a
> forgotten hole.

| capability | decision | reason |
|---|---|---|
| self-attested DOB gate (local) | INTEGRATE | delivered this phase via AgeVerifier/LocalAgeVerifier — server-side hard gate at register |
| facial age-estimation | OPT-OUT | deferred — no vendor integrated this phase; AgeVerifier seam is the future drop-in point (AGE-01/02, D-03) |
| ID-document verification | OPT-OUT | deferred — self-attested DOB only for v2.2; vendor added when a target market's law requires it |
| declared age range (platform) | OPT-OUT | deferred — Apple Declared Age Range / equivalent not integrated; out of scope for v2.2 |
| liveness / selfie check | OPT-OUT | deferred — no biometric verification this phase |
| age re-verification / step-up | OPT-OUT | deferred — DOB captured once at signup and immutable (D-06); no re-verification flow |
| per-jurisdiction minimum-age routing | OPT-OUT | deferred — single global minimum via app.age.minimum-age (default 18); market-specific routing out of scope |
