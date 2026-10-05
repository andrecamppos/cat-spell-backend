package com.catspell.api.common

import com.catspell.api.common.config.WaitlistCorsPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * D-14: the one landing-page join CORS policy, read by Spring Security and by the rate limiter's 429, stays
 * explicit-origin, POST-only and credential-free, and is absent when no origin is configured.
 */
class WaitlistCorsPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = ["", "  ,  "])
    fun `a blank origin list leaves no configuration`(raw: String) {
        assertNull(WaitlistCorsPolicy(raw).configuration)
    }

    @Test
    fun `configured origins are trimmed and the join is POST-only with no credentials`() {
        val configuration = WaitlistCorsPolicy(" https://a.example , https://b.example ").configuration

        assertNotNull(configuration)
        assertEquals(listOf("https://a.example", "https://b.example"), configuration!!.allowedOrigins)
        assertEquals(listOf("POST"), configuration.allowedMethods)
        assertEquals(listOf("Content-Type"), configuration.allowedHeaders)
        assertEquals(false, configuration.allowCredentials)
        assertEquals(3600L, configuration.maxAge)
    }

    @Test
    fun `a foreign origin is not allowed and no wildcard is ever granted`() {
        val configuration = WaitlistCorsPolicy("https://a.example,https://b.example").configuration!!

        assertNull(configuration.checkOrigin("https://evil.example"))
        assertNull(configuration.checkOrigin(null), "a request without Origin gets no grant")
        assertFalse(configuration.allowedOrigins!!.contains("*"), "the policy never allows a wildcard origin")
        assertTrue(configuration.allowedOriginPatterns.isNullOrEmpty(), "the policy uses no origin patterns")
    }
}
