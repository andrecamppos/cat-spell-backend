package com.catspell.api.common

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
 * or 203.0.113.7x address, used by no other test, and asserts requests 1 and 2 are NOT throttled before asserting
 * request 3 is.
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

        /** Tomcat's uncompressed spelling of the IPv6 loopback, which the default `::1` entry must trust. */
        private const val IPV6_LOOPBACK_UNCOMPRESSED = "0:0:0:0:0:0:0:1"
        private const val CLIENT_BEHIND_IPV6_LOOPBACK = "203.0.113.63"
        private const val OTHER_CLIENT_BEHIND_IPV6_LOOPBACK = "203.0.113.64"
        private const val CLIENT_BEHIND_INNER_PROXY = "203.0.113.65"
        private const val CLIENT_ON_SECOND_HEADER_LINE = "203.0.113.66"
        private const val CLIENT_BEFORE_MALFORMED_HOP = "203.0.113.67"
        private const val MALFORMED_HOP = "rl-malformed-hop"

        private const val SPELLING_ENCODED_W_PEER = "203.0.113.70"
        private const val SPELLING_ENCODED_A_PEER = "203.0.113.71"
        private const val SPELLING_ENCODED_T_PEER = "203.0.113.72"
        private const val SPELLING_PATH_PARAM_PEER = "203.0.113.73"
        private const val SPELLING_TRAILING_SLASH_PEER = "203.0.113.74"
        private const val SPELLING_DOUBLE_SLASH_PEER = "203.0.113.75"
        private const val SPELLING_DOT_SEGMENT_PEER = "203.0.113.76"

        /** Percent-encoded spellings: the join handler serves them, so they must share the canonical bucket exactly. */
        private val ENCODED_SPELLINGS = listOf(
            "/api/%77aitlist" to SPELLING_ENCODED_W_PEER,
            "/%61pi/waitlist" to SPELLING_ENCODED_A_PEER,
            "/api/waitlis%74" to SPELLING_ENCODED_T_PEER
        )

        /** Other spellings: each must either share the bucket or never reach the join handler (firewall 400 / 404). */
        private val OTHER_SPELLINGS = listOf(
            "/api/waitlist;x=1" to SPELLING_PATH_PARAM_PEER,
            "/api/waitlist/" to SPELLING_TRAILING_SLASH_PEER,
            "/api//waitlist" to SPELLING_DOUBLE_SLASH_PEER,
            "/api/./waitlist" to SPELLING_DOT_SEGMENT_PEER
        )
    }

    /** One canonical join, then two joins on [spelling], from [peer]; every request uses a unique email. */
    private fun canonicalThenSpellingTwice(spelling: String, peer: String): List<Int> = listOf(
        postRaw("/api/waitlist", """{"email":"rl-spelling-$peer-1@example.com"}""", peer),
        postRaw(spelling, """{"email":"rl-spelling-$peer-2@example.com"}""", peer),
        postRaw(spelling, """{"email":"rl-spelling-$peer-3@example.com"}""", peer)
    )

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

    /** Posts a login from [remoteAddr] with one X-Forwarded-For header value per entry of [forwardedLines]. */
    private fun postLoginWithForwardedLines(remoteAddr: String, vararg forwardedLines: String): Int {
        val builder = post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody())
            .header("X-Forwarded-For", *forwardedLines)
            .with { request -> request.remoteAddr = remoteAddr; request }
        return mockMvc.perform(builder).andReturn().response.status
    }

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

    @Test
    fun `a percent-encoded login path is throttled like the canonical path`() {
        val statuses = (1..3).map { postRaw("/api/auth/%6Cogin", loginBody(), ENCODED_LOGIN_PEER) }
        assertNotEquals(404, statuses[0], "the encoded path must reach the login handler, or this proves nothing")
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket")
        assertEquals(429, statuses[2], "POST /api/auth/%6Cogin must share the per-IP bucket of POST /api/auth/login")
    }

    @Test
    fun `every spelling of the join path that reaches the join handler shares the canonical per-IP bucket`() {
        for ((spelling, peer) in ENCODED_SPELLINGS) {
            val statuses = canonicalThenSpellingTwice(spelling, peer)
            assertEquals(202, statuses[0], "canonical join before $spelling must be accepted, got $statuses")
            assertEquals(
                listOf(202, 202, 429), statuses,
                "$spelling must draw from the canonical POST /api/waitlist bucket, got $statuses"
            )
        }
        for ((spelling, peer) in OTHER_SPELLINGS) {
            val statuses = canonicalThenSpellingTwice(spelling, peer)
            assertEquals(202, statuses[0], "canonical join before $spelling must be accepted, got $statuses")
            assertTrue(
                statuses.count { it == 202 } <= 2,
                "$spelling must not yield a third accepted join from one peer at capacity 2, got $statuses"
            )
        }
    }

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

    @Test
    fun `the uncompressed IPv6 loopback peer is trusted by the default loopback entry`() {
        val statuses = (1..3).map {
            postLoginWithForwardedLines(IPV6_LOOPBACK_UNCOMPRESSED, CLIENT_BEHIND_IPV6_LOOPBACK)
        }
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(429, statuses[2], "three forwards of one client must share its bucket, got $statuses")

        val otherClient = postLoginWithForwardedLines(IPV6_LOOPBACK_UNCOMPRESSED, OTHER_CLIENT_BEHIND_IPV6_LOOPBACK)
        assertNotEquals(
            429, otherClient,
            "a different forwarded client must get its own bucket, proving the IPv6 loopback peer is trusted"
        )
    }

    @Test
    fun `a client hop behind a trusted inner proxy hop is the bucket key`() {
        val statuses = (1..3).map {
            postLoginWithForwardedLines(TRUSTED_PROXY, "10.204.0.$it, $CLIENT_BEHIND_INNER_PROXY, $TRUSTED_PROXY")
        }
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(
            429, statuses[2],
            "the trusted inner hop must be skipped and $CLIENT_BEHIND_INNER_PROXY used as the key, got $statuses"
        )
    }

    @Test
    fun `a proxy-added second X-Forwarded-For header line is honored`() {
        val statuses = (1..3).map {
            postLoginWithForwardedLines(TRUSTED_PROXY, "10.205.0.$it", CLIENT_ON_SECOND_HEADER_LINE)
        }
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(
            429, statuses[2],
            "hops must be read across every header line, so $CLIENT_ON_SECOND_HEADER_LINE is the key, got $statuses"
        )
    }

    @Test
    fun `a malformed rightmost hop from a trusted peer never errors and shares one bucket`() {
        val statuses = (1..3).map {
            postLoginWithForwardedLines(TRUSTED_PROXY, "$CLIENT_BEFORE_MALFORMED_HOP, $MALFORMED_HOP")
        }
        assertTrue(statuses.none { it >= 500 }, "a malformed hop must never produce a server error, got $statuses")
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(429, statuses[2], "requests with a malformed hop must share one bucket, got $statuses")
    }
}
