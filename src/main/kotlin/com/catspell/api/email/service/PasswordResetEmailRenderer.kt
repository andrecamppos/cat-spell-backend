package com.catspell.api.email.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PasswordResetEmailRenderer(
    @Value("\${app.reset-password-url}") private val resetPasswordUrl: String
) {

    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val resetLink = "$resetPasswordUrl?token=$rawToken"
        val subject = "Reset your Cat Spell password"

        val htmlBody = """
            <!DOCTYPE html>
            <html>
              <body>
                <p>We received a request to reset your Cat Spell password.</p>
                <p>Tap the link below to choose a new password. This link expires soon and can only be used once.</p>
                <p><a href="$resetLink">Reset my password</a></p>
                <p>If you didn't request this, you can safely ignore this email.</p>
              </body>
            </html>
        """.trimIndent()

        val textBody = """
            We received a request to reset your Cat Spell password.

            Open this link to choose a new password (it expires soon and can only be used once):
            $resetLink

            If you didn't request this, you can safely ignore this email.
        """.trimIndent()

        return EmailMessage(
            to = recipientEmail,
            subject = subject,
            htmlBody = htmlBody,
            textBody = textBody
        )
    }
}
