package com.catspell.api.email.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EmailVerificationEmailRenderer(
    @Value("\${app.verify-email-url}") private val verifyEmailUrl: String
) {

    fun render(recipientEmail: String, rawToken: String): EmailMessage {
        val verifyLink = "$verifyEmailUrl?token=$rawToken"
        val subject = "Verify your Cat Spell email"

        val htmlBody = """
            <!DOCTYPE html>
            <html>
              <body>
                <p>Welcome to Cat Spell!</p>
                <p>Confirm your email address to finish signing up. Tap the link below — it expires soon and can only be used once.</p>
                <p><a href="$verifyLink">Verify my email</a></p>
                <p>If you didn't create a Cat Spell account, you can safely ignore this email.</p>
              </body>
            </html>
        """.trimIndent()

        val textBody = """
            Welcome to Cat Spell!

            Confirm your email address to finish signing up. Open this link (it expires soon and can only be used once):
            $verifyLink

            If you didn't create a Cat Spell account, you can safely ignore this email.
        """.trimIndent()

        return EmailMessage(
            to = recipientEmail,
            subject = subject,
            htmlBody = htmlBody,
            textBody = textBody
        )
    }
}
