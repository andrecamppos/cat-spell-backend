package com.catspell.api.email.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EmailChangeEmailRenderer(
    @Value("\${app.confirm-email-change-url}") private val confirmEmailChangeUrl: String
) {

    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val confirmLink = "$confirmEmailChangeUrl?token=$rawToken"
        val subject = "Confirm your new Cat Spell email"

        val htmlBody = """
            <!DOCTYPE html>
            <html>
              <body>
                <p>You asked to change the email address on your Cat Spell account to this one.</p>
                <p>Confirm this new address by tapping the link below — it expires soon and can only be used once.</p>
                <p><a href="$confirmLink">Confirm my new email</a></p>
                <p>If you didn't request this change, you can safely ignore this email — your account email won't change.</p>
              </body>
            </html>
        """.trimIndent()

        val textBody = """
            You asked to change the email address on your Cat Spell account to this one.

            Confirm this new address by opening the link below (it expires soon and can only be used once):
            $confirmLink

            If you didn't request this change, you can safely ignore this email — your account email won't change.
        """.trimIndent()

        return EmailMessage(
            to = recipientEmail,
            subject = subject,
            htmlBody = htmlBody,
            textBody = textBody
        )
    }
}
