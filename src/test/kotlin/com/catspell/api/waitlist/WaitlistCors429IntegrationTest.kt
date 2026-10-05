package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.concurrent.atomic.AtomicInteger

/**
 * D-14 / WR-07(c): RateLimitFilter runs at HIGHEST_PRECEDENCE, ahead of Spring Security's CorsFilter, so it writes the
 * waitlist join's 429 itself. For an origin in app.waitlist.allowed-origins that 429 must carry the CORS grant and
 * expose Retry-After, so the landing page can read it cross-origin. Foreign origins, a missing Origin and the auth
 * routes never get a grant. Each test uses its own untrusted peer address, so it starts on a fresh per-IP bucket.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.waitlist.allowed-origins=https://landing.example", "rate-limit.waitlist-capacity=2", "rate-limit.capacity=2"])
class WaitlistCors429IntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    companion object {
        private const val LANDING_ORIGIN = "https://landing.example"
        private const val LANDING_ORIGIN_UPPER = "https://LANDING.example"
        private const val FOREIGN_ORIGIN = "https://evil.example"

        private const val ALLOWED_ORIGIN_PEER = "203.0.113.84"
        private const val UPPER_CASE_ORIGIN_PEER = "203.0.113.85"
        private const val FOREIGN_ORIGIN_PEER = "203.0.113.86"
        private const val NO_ORIGIN_PEER = "203.0.113.87"
        private const val AUTH_PEER = "203.0.113.88"

        private val EXPOSED_HEADERS = listOf("Retry-After", "X-RateLimit-Remaining", "X-RateLimit-Reset")

        private val counter = AtomicInteger(0)

        /** A fresh address per request, so the per-email bucket is never what trips. */
        private fun nextEmail(): String = "cors-429-${counter.incrementAndGet()}@example.com"
    }

    /** A join sent directly by [peer] (no X-Forwarded-For), with [origin] as the Origin header when non-null. */
    private fun join(peer: String, origin: String?): MockHttpServletResponse {
        val request = post("/api/waitlist")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"${nextEmail()}"}""")
            .with { it.remoteAddr = peer; it }
        if (origin != null) request.header(HttpHeaders.ORIGIN, origin)
        return mockMvc.perform(request).andReturn().response
    }

    /** Sends three joins from [peer] and returns the responses; the third one is over the capacity of 2. */
    private fun threeJoins(peer: String, origin: String?): List<MockHttpServletResponse> = (1..3).map { join(peer, origin) }

    /** Every value of every header line named [name], split on commas and trimmed. */
    private fun headerValues(response: MockHttpServletResponse, name: String): List<String> =
        response.getHeaders(name).flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `a throttled join from the landing origin gets a 429 the page can read`() {
        val responses = threeJoins(ALLOWED_ORIGIN_PEER, LANDING_ORIGIN)

        assertEquals(listOf(202, 202, 429), responses.map { it.status })
        val throttled = responses[2]
        assertEquals(LANDING_ORIGIN, throttled.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        val exposed = headerValues(throttled, HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)
        EXPOSED_HEADERS.forEach { assertTrue(it in exposed, "Expose-Headers must list $it, got $exposed") }
        assertTrue("Origin" in headerValues(throttled, HttpHeaders.VARY), "the 429 must vary on Origin")
        assertNotNull(throttled.getHeader("Retry-After"), "the 429 must carry Retry-After")
        assertNull(throttled.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS), "the grant never allows credentials")
    }

    @Test
    fun `the 429 grant echoes the request's own spelling of an allowed origin`() {
        val throttled = threeJoins(UPPER_CASE_ORIGIN_PEER, LANDING_ORIGIN_UPPER)[2]

        assertEquals(429, throttled.status)
        assertEquals(LANDING_ORIGIN_UPPER, throttled.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun `a throttled join from a foreign origin gets no grant but still varies on Origin`() {
        // Spring Security's CorsFilter rejects the first two with 403, after the limiter has already spent a token.
        val throttled = threeJoins(FOREIGN_ORIGIN_PEER, FOREIGN_ORIGIN)[2]

        assertEquals(429, throttled.status)
        assertNull(throttled.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN), "a foreign origin must get no grant")
        assertNull(throttled.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
        assertTrue("Origin" in headerValues(throttled, HttpHeaders.VARY), "the 429 must vary on Origin")
    }

    @Test
    fun `a throttled join with no Origin header gets no grant`() {
        val responses = threeJoins(NO_ORIGIN_PEER, null)

        assertEquals(listOf(202, 202, 429), responses.map { it.status })
        assertNull(responses[2].getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertNull(responses[2].getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
    }

    @Test
    fun `a throttled login from the landing origin never gets a CORS grant`() {
        // D-14: the CORS grant is scoped to the join; the auth family's 429 carries none, even for an allowed origin.
        val responses = (1..3).map {
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"cors-429-login@example.com","password":"password123"}""")
                    .header(HttpHeaders.ORIGIN, LANDING_ORIGIN)
                    .with { it.remoteAddr = AUTH_PEER; it }
            ).andReturn().response
        }

        val throttled = responses[2]
        assertEquals(429, throttled.status)
        assertNull(throttled.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN), "a login 429 must get no grant")
        assertNull(throttled.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
    }
}
