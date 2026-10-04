package com.catspell.api.email

import com.catspell.api.email.service.WaitlistInviteEmailRenderer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val RAW_CODE = "AbC-_9"
private const val RECIPIENT = "someone@example.com"

/** Invite link construction (first-review IN-05 → IN-12). */
class WaitlistInviteEmailRendererTest {

    @Test
    fun `the default deep link gets the code as its only query parameter in both bodies`() {
        val message = WaitlistInviteEmailRenderer("catspell://register").render(RECIPIENT, RAW_CODE)

        assertTrue(message.textBody.contains("catspell://register?code=$RAW_CODE"), message.textBody)
        assertTrue(message.htmlBody.contains("catspell://register?code=$RAW_CODE"), message.htmlBody)
    }

    @Test
    fun `an invite URL that already has a query gets the code appended with an ampersand`() {
        val message = WaitlistInviteEmailRenderer("https://x.example/r?ref=lp").render(RECIPIENT, RAW_CODE)

        for (body in listOf(message.textBody, message.htmlBody)) {
            assertTrue(body.contains("https://x.example/r?ref=lp&code=$RAW_CODE"), body)
            assertFalse(body.contains("?ref=lp?code"), body)
        }
    }

    @Test
    fun `the raw code still appears on its own line so it can be typed at sign-up`() {
        val message = WaitlistInviteEmailRenderer("catspell://register").render(RECIPIENT, RAW_CODE)

        assertTrue(message.textBody.lines().any { it.trim() == RAW_CODE }, message.textBody)
        assertTrue(message.htmlBody.lines().any { it.trim() == "<p><strong>$RAW_CODE</strong></p>" }, message.htmlBody)
    }
}
