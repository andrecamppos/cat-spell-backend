package com.catspell.api.email

import com.catspell.api.email.service.EmailMessage
import com.catspell.api.email.service.WaitlistConfirmEmailRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val DEFAULT_CONFIRM_URL = "http://localhost:8080/api/waitlist/confirm"
private const val RAW_TOKEN = "AbC-_9"
private const val RECIPIENT = "someone@example.com"

/**
 * Copy and link contract of the waitlist confirmation email (D-09, first-review IN-01 → IN-08, IN-05 → IN-12).
 */
class WaitlistConfirmEmailRendererTest {

    private fun render(
        confirmUrl: String = DEFAULT_CONFIRM_URL,
        ttlHours: Long = 168,
        cooldownMinutes: Long = 15
    ): EmailMessage = WaitlistConfirmEmailRenderer(confirmUrl, ttlHours, cooldownMinutes).render(RECIPIENT, RAW_TOKEN)

    @Test
    fun `default config renders the 7 day expiry, the newest-link rule and the cooldown in both bodies`() {
        val message = render()

        for (body in listOf(message.htmlBody, message.textBody)) {
            assertTrue(body.contains("7 days"), "expiry must be rendered from the 168h TTL: $body")
            assertTrue(body.contains("most recent email"), "copy must say only the newest link works: $body")
            assertTrue(body.contains("15 minutes"), "copy must render the resend cooldown: $body")
            assertFalse(body.contains("still confirmed"), "the false 'still confirmed' claim must be gone: $body")
        }
        assertTrue(message.textBody.contains("$DEFAULT_CONFIRM_URL?token=$RAW_TOKEN"))
        assertTrue(message.htmlBody.contains("$DEFAULT_CONFIRM_URL?token=$RAW_TOKEN"))
    }

    @Test
    fun `a 24 hour TTL renders as 1 day`() {
        assertTrue(render(ttlHours = 24).textBody.contains("expires in 1 day)"))
    }

    @Test
    fun `a TTL that is not a whole number of days renders in hours`() {
        assertTrue(render(ttlHours = 36).textBody.contains("expires in 36 hours"))
    }

    @Test
    fun `a 1 hour TTL renders in the singular`() {
        assertTrue(render(ttlHours = 1).textBody.contains("expires in 1 hour)"))
    }

    @Test
    fun `a 1 minute cooldown renders in the singular`() {
        val text = render(cooldownMinutes = 1).textBody
        assertTrue(text.contains("every 1 minute."), text)
    }

    @Test
    fun `a zero cooldown still says to join again but renders no minute count`() {
        val message = render(cooldownMinutes = 0)

        for (body in listOf(message.htmlBody, message.textBody)) {
            assertTrue(body.contains("join the waitlist again"), body)
            assertFalse(body.contains("0 minutes"), body)
            assertFalse(body.contains("every"), body)
        }
    }

    @Test
    fun `a confirm URL that already has a query gets the token appended with an ampersand`() {
        val text = render(confirmUrl = "https://x.example/c?a=b").textBody

        assertTrue(text.contains("https://x.example/c?a=b&token=$RAW_TOKEN"), text)
        assertFalse(text.contains("?a=b?token"), text)
    }

    @Test
    fun `recipient and subject are unchanged`() {
        val message = render()

        assertEquals(RECIPIENT, message.to)
        assertEquals("Confirm your spot on the Cat Spell waitlist", message.subject)
    }
}
