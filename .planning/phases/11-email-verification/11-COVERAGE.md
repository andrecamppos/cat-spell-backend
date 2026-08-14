# API Coverage — Email delivery (internal EmailSender seam)

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
>
> Phase 11 (`email-verification`) integrates **no external / third-party API**.
> The `verify:pre` api-coverage gate fired on a weak `(surface)/api` signal that
> refers to this phase's *own* HTTP REST surface (`POST /api/auth/verify-email`,
> `POST /api/auth/resend-verification`, the `register` 201 change) — not an
> outbound integration. Email is dispatched through the internal `EmailSender`
> abstraction (stubbed with a MockK `@Primary` bean in tests, EMAIL-02); 11-03
> records "No external service configuration required." This matrix is the
> subtraction record for any external email-provider surface: everything is a
> reasoned OPT-OUT because there is no such provider in this phase.

| capability | decision | reason |
|---|---|---|
| external email provider SDK/REST (SendGrid / SES / Mailgun / Postmark / Resend) | OPT-OUT | not integrated — mail is sent via the internal `EmailSender` seam; no third-party email API is called in this phase |
| provider delivery webhooks / bounce & complaint callbacks | OPT-OUT | out of scope — no external provider, so there is no inbound delivery/bounce webhook surface |
| provider template / campaign API | OPT-OUT | out of scope — verification emails are rendered in-app, not via a provider template service |
| provider suppression / contact-list management | OPT-OUT | out of scope — no external provider contact lists; verification targets a single known user email |
