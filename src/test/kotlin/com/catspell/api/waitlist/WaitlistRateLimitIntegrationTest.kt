package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.common.security.RateLimitFilter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * D-07 per-IP half (WAIT-03): the REGISTERED RateLimitFilter (not a hand-built one) throttles POST /api/waitlist
 * per client IP and never touches GET /api/waitlist/confirm. Pitfall 1: without the `/api/waitlist` URL pattern on
 * the FilterRegistrationBean the filter never runs on the join in production, so the registration itself is asserted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["rate-limit.capacity=2"])
class WaitlistRateLimitIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var rateLimitFilterRegistration: FilterRegistrationBean<RateLimitFilter>

    companion object {
        private val counter = AtomicInteger(0)

        /** A fresh client IP per test, because the per-IP buckets live in the filter for the whole cached context. */
        private fun nextIp(): String {
            val n = counter.incrementAndGet()
            return "10.170.${n / 256}.${n % 256}"
        }

        /** A fresh address per request, so the per-email bucket is never what trips. */
        private fun nextEmail(): String = "rl-waitlist-${counter.incrementAndGet()}@example.com"

        /**
         * The documented untrusted direct-connection address (RFC 5737 TEST-NET-3), the same literal as
         * RateLimitTrustedProxyIntegrationTest. That class shares this cached context and its bucket map, so the
         * forged-header test below pins its own neighbour address, used by no other test, to start on a fresh bucket.
         */
        const val UNTRUSTED_PEER = "203.0.113.50"
        private const val FORGED_XFF_PEER = "203.0.113.53"
    }

    private fun join(ip: String) = mockMvc.perform(
        post("/api/waitlist")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"${nextEmail()}"}""")
            .header("X-Forwarded-For", ip)
    )

    /** A join sent directly by an untrusted peer that forges X-Forwarded-For (T-17-30). */
    private fun joinFromUntrustedPeer(remoteAddr: String, forgedIp: String) = mockMvc.perform(
        post("/api/waitlist")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"${nextEmail()}"}""")
            .header("X-Forwarded-For", forgedIp)
            .with { request -> request.remoteAddr = remoteAddr; request }
    )

    @Test
    fun `the registered rate-limit filter covers the auth paths and the waitlist join`() {
        val patterns = rateLimitFilterRegistration.urlPatterns
        assertTrue(patterns.contains("/api/auth/*"), "auth throttling must stay registered: $patterns")
        assertTrue(patterns.contains("/api/waitlist"), "the join must be registered or it is never throttled: $patterns")
    }

    @Test
    fun `third join from one IP at capacity 2 is 429 problem json with a whole-second Retry-After`() {
        val ip = nextIp()
        join(ip).andExpect(status().isAccepted)
        join(ip).andExpect(status().isAccepted)

        val third = join(ip)
            .andExpect(status().isTooManyRequests)
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.title").value("Too Many Requests"))
            .andReturn().response

        val retryAfter = third.getHeader("Retry-After")
        assertNotNull(retryAfter, "a 429 must carry Retry-After")
        val seconds = retryAfter!!.toLong()
        assertTrue(seconds >= 1, "Retry-After must be a whole number of seconds >= 1 (rounded up), got $seconds")
    }

    @Test
    fun `confirm links are never throttled even after the IP join bucket is exhausted`() {
        val ip = nextIp()
        join(ip)
        join(ip)
        join(ip).andExpect(status().isTooManyRequests)

        repeat(3) {
            val response = mockMvc.perform(
                get("/api/waitlist/confirm").param("token", "abc").header("X-Forwarded-For", ip)
            )
                .andExpect(header().doesNotExist("X-RateLimit-Remaining"))
                .andReturn().response
            assertNotEquals(429, response.status, "confirm request ${it + 1} must never be rate limited")
        }
    }

    // Still holds after T-17-30: MockMvc's default remoteAddr (127.0.0.1) is a trusted proxy, so this simulates a
    // trusted peer forwarding genuinely distinct client IPs, not a direct untrusted caller spoofing the header.
    @Test
    fun `a different IP is still allowed after one IP is exhausted`() {
        val exhausted = nextIp()
        join(exhausted)
        join(exhausted)
        join(exhausted).andExpect(status().isTooManyRequests)

        join(nextIp()).andExpect(status().isAccepted)
    }

    @Test
    fun `a forged X-Forwarded-For from an untrusted remoteAddr is still throttled on the waitlist join`() {
        assertNotEquals(UNTRUSTED_PEER, FORGED_XFF_PEER)

        val first = joinFromUntrustedPeer(FORGED_XFF_PEER, "10.203.0.1").andReturn().response.status
        val second = joinFromUntrustedPeer(FORGED_XFF_PEER, "10.203.0.2").andReturn().response.status
        assertNotEquals(429, first, "join 1 must consume from a fresh bucket")
        assertNotEquals(429, second, "join 2 must consume from a fresh bucket")

        val third = joinFromUntrustedPeer(FORGED_XFF_PEER, "10.203.0.3").andReturn().response.status
        assertEquals(
            429, third,
            "three distinct forged X-Forwarded-For values from one untrusted peer must share that peer's bucket"
        )
    }
}
