package com.catspell.api.email.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Invite email for a converted waitlist entry (WAIT-04, D-10). Shows the single-use code as plain text, so it can be
 * typed at sign-up, plus a deep link carrying it. The URL default keeps the bean resolvable when the key is absent.
 */
@Component
class WaitlistInviteEmailRenderer(
    @Value("\${app.waitlist.invite-url:catspell://register}") private val inviteUrl: String
) {

    fun render(recipientEmail: String, rawCode: String): EmailMessage {
        val inviteLink = "$inviteUrl?code=$rawCode"
        val subject = "Your Cat Spell invite is here"

        val htmlBody = """
            <!DOCTYPE html>
            <html>
              <body>
                <p>Good news: your spot on the Cat Spell waitlist came up!</p>
                <p>Your invite code is:</p>
                <p><strong>$rawCode</strong></p>
                <p>Enter it when you sign up, or tap the link below to open the sign-up screen with the code filled in. The code can only be used once.</p>
                <p><a href="$inviteLink">Join Cat Spell</a></p>
                <p>If you didn't join the Cat Spell waitlist, you can safely ignore this email.</p>
              </body>
            </html>
        """.trimIndent()

        val textBody = """
            Good news: your spot on the Cat Spell waitlist came up!

            Your invite code is:
            $rawCode

            Enter it when you sign up, or open this link to sign up with the code filled in (the code can only be used once):
            $inviteLink

            If you didn't join the Cat Spell waitlist, you can safely ignore this email.
        """.trimIndent()

        return EmailMessage(
            to = recipientEmail,
            subject = subject,
            htmlBody = htmlBody,
            textBody = textBody
        )
    }
}
