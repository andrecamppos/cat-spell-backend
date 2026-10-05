package com.catspell.api.common

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.common.security.RateLimitFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.net.URI

/**
 * WAIT-03 per-IP bypasses from 17-REVIEW (CR-01, CR-02, and IN-01 path normalization), against the REGISTERED
 * RateLimitFilter.
 *
 * Shares one cached Spring context (byte-identical test property arrays) and per-IP bucket maps with
 * RateLimitTrustedProxyIntegrationTest and WaitlistRateLimitIntegrationTest, so each test pins its own 203.0.113.6x,
 * 203.0.113.7x or 203.0.113.8x address, used by no other test, and asserts requests 1 and 2 are NOT throttled before asserting
 * request 3 is. The shared array also trusts 198.51.100.0/24 (RFC 5737 TEST-NET-2), a range reserved for these tests'
 * trusted inner proxies and trusted peers, so a test that needs its own trusted peer never shares 127.0.0.1's bucket.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["rate-limit.capacity=2", "rate-limit.trusted-proxies=127.0.0.1,::1,198.51.100.0/24", "rate-limit.waitlist-capacity=2", "rate-limit.admin-capacity=2"])
class RateLimitBypassIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var rateLimitFilterRegistration: FilterRegistrationBean<RateLimitFilter>

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

        /** D-15: hops with a port (and bracketed IPv6 hops) must key on the canonical client address. */
        private const val CLIENT_WITH_ROTATING_PORT = "203.0.113.68"
        private const val IPV6_CLIENT_WITH_ROTATING_PORT = "2001:db8::68"
        private const val CLIENT_BEHIND_TRUSTED_HOP_WITH_PORT = "203.0.113.69"

        /** Inside the test-only trusted range 198.51.100.0/24. */
        private const val TRUSTED_INNER_PROXY_WITH_PORT = "198.51.100.9"
        private const val TRUSTED_PEER_FOR_MALFORMED_HOP = "198.51.100.10"
        private const val OTHER_TRUSTED_PEER_FOR_MALFORMED_HOP = "198.51.100.11"

        private const val SPELLING_ENCODED_W_PEER = "203.0.113.70"
        private const val SPELLING_ENCODED_A_PEER = "203.0.113.71"
        private const val SPELLING_ENCODED_T_PEER = "203.0.113.72"
        private const val SPELLING_PATH_PARAM_PEER = "203.0.113.73"
        private const val SPELLING_TRAILING_SLASH_PEER = "203.0.113.74"
        private const val SPELLING_DOUBLE_SLASH_PEER = "203.0.113.75"
        private const val SPELLING_DOT_SEGMENT_PEER = "203.0.113.76"

        /** D-16 / current IN-01: dot-segment spellings, throttled by the filter itself rather than the firewall. */
        private const val DOT_SEGMENT_LOGIN_PEER = "203.0.113.83"
        private const val DOT_SEGMENT_JOIN_PEER = "203.0.113.84"

        /** Percent-encoded spellings: the join handler serves them, so they must share the canonical bucket exactly. */
        private val ENCODED_SPELLINGS = listOf(
            "/api/%77aitlist" to SPELLING_ENCODED_W_PEER,
            "/%61pi/waitlist" to SPELLING_ENCODED_A_PEER,
            "/api/waitlis%74" to SPELLING_ENCODED_T_PEER
        )

        /**
         * D-16: spellings Tomcat normalizes to /api/waitlist (empty segment, path parameter). The filter matches on the
         * container-normalized path, so each must draw from the canonical bucket even if the firewall that rejects them
         * today (400) is ever loosened. The dot-segment join spelling is proven separately below: MockMvc maps filters
         * on a path that keeps dot segments, so through MockMvc it never reaches the exact `/api/waitlist` mapping.
         */
        private val NORMALIZED_SPELLINGS = listOf(
            "/api//waitlist" to SPELLING_DOUBLE_SLASH_PEER,
            "/api/waitlist;x=1" to SPELLING_PATH_PARAM_PEER
        )

        /** Other spellings: each must either share the bucket or never reach the join handler (firewall 400 / 404). */
        private val OTHER_SPELLINGS = listOf(
            "/api/waitlist/" to SPELLING_TRAILING_SLASH_PEER,
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
        for ((spelling, peer) in NORMALIZED_SPELLINGS) {
            val statuses = canonicalThenSpellingTwice(spelling, peer)
            assertEquals(202, statuses[0], "canonical join before $spelling must be accepted, got $statuses")
            assertTrue(
                statuses[1] in setOf(202, 400),
                "$spelling request 2 must pass the filter (handler 202 or firewall 400), got $statuses"
            )
            assertEquals(
                429, statuses[2],
                "$spelling must draw from the canonical POST /api/waitlist bucket in the filter itself, got $statuses"
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
    fun `a dot-segment login path is throttled by the filter like the canonical path`() {
        val statuses = listOf(
            postRaw("/api/auth/login", loginBody(), DOT_SEGMENT_LOGIN_PEER),
            postRaw("/api/auth/./login", loginBody(), DOT_SEGMENT_LOGIN_PEER),
            postRaw("/api/auth/./login", loginBody(), DOT_SEGMENT_LOGIN_PEER)
        )
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(
            429, statuses[2],
            "POST /api/auth/./login must share the canonical login bucket in the filter, not rely on the firewall, " +
                "got $statuses"
        )
    }

    /**
     * Drives the REGISTERED filter instance directly with an un-normalized dot-segment join, shaped as MockMvc builds it
     * (empty servletPath, the URI in pathInfo). MockMvc's own filter mapping never routes `/api/./waitlist` to the exact
     * `/api/waitlist` pattern, whereas Tomcat maps filters on the normalized URI, so in production this request does
     * reach the filter and must then be throttled by the filter's own path match. Returns the status after the filter
     * (200 when it passed the request on to the chain).
     */
    private fun postDotSegmentJoinToFilter(remoteAddr: String): Int {
        val request = MockHttpServletRequest("POST", "/api/./waitlist")
        request.servletPath = ""
        request.pathInfo = "/api/./waitlist"
        request.remoteAddr = remoteAddr
        val response = MockHttpServletResponse()
        val filter = requireNotNull(rateLimitFilterRegistration.filter) { "the rate-limit filter must be registered" }
        filter.doFilter(request, response, MockFilterChain())
        return response.status
    }

    @Test
    fun `a dot-segment join path is throttled by the filter itself like the canonical path`() {
        val statuses = listOf(
            postRaw("/api/waitlist", """{"email":"rl-dot-join-1@example.com"}""", DOT_SEGMENT_JOIN_PEER),
            postDotSegmentJoinToFilter(DOT_SEGMENT_JOIN_PEER),
            postDotSegmentJoinToFilter(DOT_SEGMENT_JOIN_PEER)
        )
        assertEquals(202, statuses[0], "the canonical join must be accepted, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from the canonical join bucket, got $statuses")
        assertEquals(
            429, statuses[2],
            "POST /api/./waitlist must draw from the canonical POST /api/waitlist bucket in the filter, got $statuses"
        )
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
    fun `a malformed rightmost hop from a trusted peer never errors and falls back to the peer bucket`() {
        val forwarded = "$CLIENT_BEFORE_MALFORMED_HOP, $MALFORMED_HOP"
        val statuses = (1..3).map { postLoginWithForwardedLines(TRUSTED_PEER_FOR_MALFORMED_HOP, forwarded) }
        assertTrue(statuses.none { it >= 500 }, "a malformed hop must never produce a server error, got $statuses")
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(429, statuses[2], "requests with a malformed hop must share the peer's bucket, got $statuses")

        val otherPeer = postLoginWithForwardedLines(OTHER_TRUSTED_PEER_FOR_MALFORMED_HOP, forwarded)
        assertNotEquals(
            429, otherPeer,
            "the same malformed hop from another trusted peer must get that peer's bucket, so the key is the peer, " +
                "not the hop text"
        )
    }

    @Test
    fun `a forwarded IPv4 hop with a rotating port shares the client bucket`() {
        val statuses = (1..3).map {
            postLoginWithForwardedLines(TRUSTED_PROXY, "$CLIENT_WITH_ROTATING_PORT:${40000 + it}")
        }
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(
            429, statuses[2],
            "the port must be stripped so every connection keys on $CLIENT_WITH_ROTATING_PORT, got $statuses"
        )
    }

    @Test
    fun `a forwarded bracketed IPv6 hop with a rotating port shares the client bucket`() {
        val statuses = (1..3).map {
            postLoginWithForwardedLines(TRUSTED_PROXY, "[$IPV6_CLIENT_WITH_ROTATING_PORT]:${41000 + it}")
        }
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(
            429, statuses[2],
            "brackets and port must be stripped so every connection keys on $IPV6_CLIENT_WITH_ROTATING_PORT, " +
                "got $statuses"
        )
    }

    @Test
    fun `a trusted inner proxy hop written with a port is skipped`() {
        val statuses = (1..3).map {
            postLoginWithForwardedLines(
                TRUSTED_PROXY,
                "10.206.0.$it, $CLIENT_BEHIND_TRUSTED_HOP_WITH_PORT, $TRUSTED_INNER_PROXY_WITH_PORT:${443 + it}"
            )
        }
        assertNotEquals(429, statuses[0], "request 1 must consume from a fresh bucket, got $statuses")
        assertNotEquals(429, statuses[1], "request 2 must consume from a fresh bucket, got $statuses")
        assertEquals(
            429, statuses[2],
            "$TRUSTED_INNER_PROXY_WITH_PORT:<port> must be recognized as trusted and skipped, so " +
                "$CLIENT_BEHIND_TRUSTED_HOP_WITH_PORT is the key, got $statuses"
        )
    }
}
