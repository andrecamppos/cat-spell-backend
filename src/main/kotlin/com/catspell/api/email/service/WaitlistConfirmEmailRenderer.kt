package com.catspell.api.email.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Renders the waitlist double opt-in email (WAIT-02). The link points at the public web confirm endpoint
 * (D-01/D-02), never a catspell:// deep link; the Base64url token needs no URL encoding.
 */
@Component
class WaitlistConfirmEmailRenderer(
    @Value("\${app.waitlist.confirm-url:http://localhost:8080/api/waitlist/confirm}") private val confirmUrl: String
) {

    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val confirmLink = "$confirmUrl?token=$rawToken"
        val subject = "Confirm your spot on the Cat Spell waitlist"

        val htmlBody = """
            <!DOCTYPE html>
            <html>
              <body>
                <p>Thanks for joining the Cat Spell waitlist!</p>
                <p>Tap the link below to confirm your spot. It works once and expires in 7 days.</p>
                <p><a href="$confirmLink">Confirm my spot</a></p>
                <p>Some email security tools open links automatically. If the page says this link was already used, your spot is still confirmed.</p>
                <p>If you didn't sign up for the Cat Spell waitlist, you can safely ignore this email.</p>
              </body>
            </html>
        """.trimIndent()

        val textBody = """
            Thanks for joining the Cat Spell waitlist!

            Open this link to confirm your spot (it works once and expires in 7 days):
            $confirmLink

            Some email security tools open links automatically. If the page says this link was already used, your spot is still confirmed.

            If you didn't sign up for the Cat Spell waitlist, you can safely ignore this email.
        """.trimIndent()

        return EmailMessage(
            to = recipientEmail,
            subject = subject,
            htmlBody = htmlBody,
            textBody = textBody
        )
    }
}
