package com.catspell.api.waitlist

import com.catspell.api.waitlist.service.WaitlistEmailNormalizer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** D-03 normalization rules for the waitlist dedupe + per-email throttle key. Pure function, no Spring context. */
class WaitlistEmailNormalizerTest {

    @Test
    fun `trims, lowercases and strips a plus suffix`() {
        assertEquals("a@example.com", WaitlistEmailNormalizer.normalize(" A+Tag@Example.COM "))
    }

    @Test
    fun `cuts the local part at the first plus`() {
        assertEquals("a@x.com", WaitlistEmailNormalizer.normalize("a+b+c@x.com"))
    }

    @Test
    fun `keeps a plus that is the first local character`() {
        assertEquals("+only@x.com", WaitlistEmailNormalizer.normalize("+only@x.com"))
    }

    @Test
    fun `preserves dots in the local part`() {
        assertEquals("a.b@gmail.com", WaitlistEmailNormalizer.normalize("a.b@gmail.com"))
    }

    @Test
    fun `splits on the last at sign`() {
        assertEquals("\"q@z\"@x.com", WaitlistEmailNormalizer.normalize("\"Q@Z\"@X.com"))
    }

    @Test
    fun `returns input without an at sign unchanged`() {
        assertEquals("noatsign", WaitlistEmailNormalizer.normalize("noatsign"))
    }

    @Test
    fun `returns input with an empty local part unchanged`() {
        assertEquals("@x.com", WaitlistEmailNormalizer.normalize("@x.com"))
    }

    @Test
    fun `never throws on malformed input`() {
        listOf("", "   ", "@", "@@", "a@", "+@", "noatsign", "@x.com", "a+@", "\"@\"").forEach { input ->
            assertDoesNotThrow { WaitlistEmailNormalizer.normalize(input) }
        }
    }
}
