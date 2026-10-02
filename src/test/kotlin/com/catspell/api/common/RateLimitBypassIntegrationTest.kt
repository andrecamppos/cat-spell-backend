package com.catspell.api.common

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.net.URI

/**
 * WAIT-03 per-IP bypasses from 17-REVIEW (CR-01, CR-02), against the REGISTERED RateLimitFilter.
 *
 * Shares one cached Spring context (identical annotations) and per-IP bucket map with
 * RateLimitTrustedProxyIntegrationTest and WaitlistRateLimitIntegrationTest, so each test pins its own 203.0.113.6x
 * address, used by no other test, and asserts requests 1 and 2 are NOT throttled before asserting request 3 is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["rate-limit.capacity=2"])
class RateLimitBypassIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    companion object {
        private const val ENCODED_WAITLIST_PEER = "203.0.113.60"
        private const val ENCODED_LOGIN_PEER = "203.0.113.61"
        private const val REAL_CLIENT_BEHIND_PROXY = "203.0.113.62"
        private const val TRUSTED_PROXY = "127.0.0.1"
    }

    /** Sends [rawUri] verbatim, so the filter sees the undecoded requestURI exactly as Tomcat would hand it over. */
    private fun postRaw(rawUri: String, body: String, remoteAddr: String, forwardedFor: String? = null): Int {
        val builder = post(URI(rawUri))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with { request -> request.remoteAddr = remoteAddr; request }
        if (forwardedFor != null) builder.header("X-Forwarded-For", forwardedFor)
        return mockMvc.perform(builder).andReturn().response.status
    }

    private fun loginBody() = """{"email":"bypass@example.com","password":"password123"}"""

    @Disabled("CR-02: RateLimitFilter matches the raw undecoded requestURI — enable when RateLimitFilter is fixed")
    @Test
    fun `a percent-encoded waitlist join path is throttled like the canonical path`() {
        val statuses = (1..3).map {
            postRaw("/api/%77aitlist", """{"email":"rl-encoded-$it@example.com"}""", ENCODED_WAITLIST_PEER)
        }
        assertNotEquals(404, statuses[0], "the encoded path must reach the join handler, or this proves nothing")
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket")
        assertEquals(429, statuses[2], "POST /api/%77aitlist must share the per-IP bucket of POST /api/waitlist")
    }

    @Disabled("CR-02: RateLimitFilter matches the raw undecoded requestURI — enable when RateLimitFilter is fixed")
    @Test
    fun `a percent-encoded login path is throttled like the canonical path`() {
        val statuses = (1..3).map { postRaw("/api/auth/%6Cogin", loginBody(), ENCODED_LOGIN_PEER) }
        assertNotEquals(404, statuses[0], "the encoded path must reach the login handler, or this proves nothing")
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket")
        assertEquals(429, statuses[2], "POST /api/auth/%6Cogin must share the per-IP bucket of POST /api/auth/login")
    }

    @Disabled("CR-01: resolveClientIp keys on the client-controlled leftmost X-Forwarded-For hop — enable when RateLimitFilter is fixed")
    @Test
    fun `rotating a client-controlled leftmost X-Forwarded-For hop behind a trusted proxy shares the real client bucket`() {
        val statuses = (1..3).map {
            postRaw("/api/auth/login", loginBody(), TRUSTED_PROXY, "10.202.0.$it, $REAL_CLIENT_BEHIND_PROXY")
        }
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket")
        assertEquals(
            429, statuses[2],
            "the bucket must key on the rightmost non-trusted hop ($REAL_CLIENT_BEHIND_PROXY), not the forged leftmost one"
        )
    }
}
