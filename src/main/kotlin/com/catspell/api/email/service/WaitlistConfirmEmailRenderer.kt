package com.catspell.api.email.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * Renders the waitlist double opt-in email (WAIT-02). The link points at the public web confirm endpoint
 * (D-01/D-02), never a catspell:// deep link, and is built with UriComponentsBuilder so a configured URL that already
 * carries a query gets `&token=` appended (IN-12). The Base64url token needs no URL encoding.
 *
 * Copy contract (D-09): only the link in the most recent email works, and joining again sends a fresh one at most once
 * per resend cooldown. The expiry and the cooldown are rendered from `app.waitlist.confirm-token-ttl-hours` and
 * `app.waitlist.resend-cooldown-minutes` (same defaults as WaitlistService), never hardcoded (IN-08). Every failed
 * confirmation lands on the single configured error URL, so the copy makes no claim about what a used link means.
 */
@Component
class WaitlistConfirmEmailRenderer(
    @Value("\${app.waitlist.confirm-url:http://localhost:8080/api/waitlist/confirm}") private val confirmUrl: String,
    @Value("\${app.waitlist.confirm-token-ttl-hours:168}") private val confirmTokenTtlHours: Long,
    @Value("\${app.waitlist.resend-cooldown-minutes:15}") private val resendCooldownMinutes: Long
) {

    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val confirmLink = UriComponentsBuilder.fromUriString(confirmUrl)
            .queryParam("token", rawToken)
            .build()
            .toUriString()
        val subject = "Confirm your spot on the Cat Spell waitlist"
        val ttl = formatTtl(confirmTokenTtlHours)
        val newestLinkNotice = newestLinkNotice()

        val htmlBody = """
            <!DOCTYPE html>
            <html>
              <body>
                <p>Thanks for joining the Cat Spell waitlist!</p>
                <p>Tap the link below to confirm your spot. It works once and expires in $ttl.</p>
                <p><a href="$confirmLink">Confirm my spot</a></p>
                <p>$newestLinkNotice</p>
                <p>If you didn't sign up for the Cat Spell waitlist, you can safely ignore this email.</p>
              </body>
            </html>
        """.trimIndent()

        val textBody = """
            Thanks for joining the Cat Spell waitlist!

            Open this link to confirm your spot (it works once and expires in $ttl):
            $confirmLink

            $newestLinkNotice

            If you didn't sign up for the Cat Spell waitlist, you can safely ignore this email.
        """.trimIndent()

        return EmailMessage(
            to = recipientEmail,
            subject = subject,
            htmlBody = htmlBody,
            textBody = textBody
        )
    }

    /** The "only the newest link works" sentence; the cooldown clause is dropped when no cooldown is configured. */
    private fun newestLinkNotice(): String {
        val base = "Only the link in the most recent email from us works. If you need a new link, join the waitlist again"
        if (resendCooldownMinutes <= 0) return "$base."
        return "$base; we send at most one new link every ${plural(resendCooldownMinutes, "minute")}."
    }

    /** Whole days when the TTL is a multiple of 24 hours, otherwise hours. */
    private fun formatTtl(hours: Long): String =
        if (hours > 0 && hours % HOURS_PER_DAY == 0L) plural(hours / HOURS_PER_DAY, "day") else plural(hours, "hour")

    private fun plural(count: Long, unit: String): String = if (count == 1L) "1 $unit" else "$count ${unit}s"

    private companion object {
        const val HOURS_PER_DAY = 24L
    }
}
