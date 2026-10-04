package com.catspell.api.common

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * T-17-30 (SC3 per-IP half): the REGISTERED RateLimitFilter trusts X-Forwarded-For only when the directly-connecting
 * peer (`remoteAddr`) is a configured trusted proxy (default 127.0.0.1 / ::1). A direct, untrusted caller is keyed on
 * its own socket address, so forging the header can never hand it a fresh bucket.
 *
 * 203.0.113.50 (RFC 5737 TEST-NET-3) is the documented untrusted peer address for this proof. This class shares one
 * cached Spring context, and therefore one per-IP bucket map that refills only once per minute, with
 * WaitlistRateLimitIntegrationTest (identical annotations). Each test therefore pins its own 203.0.113.x neighbour,
 * used by no other test, and asserts requests 1 and 2 are NOT throttled before asserting request 3 is: the third
 * request's 429 then proves exhaustion of a fresh bucket rather than one a sibling test already drained.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["rate-limit.capacity=2", "rate-limit.trusted-proxies=127.0.0.1,::1,198.51.100.0/24", "rate-limit.waitlist-capacity=2", "rate-limit.admin-capacity=2"])
class RateLimitTrustedProxyIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    companion object {
        /** The documented untrusted direct-connection address (absent from the default trusted-proxies set). */
        const val UNTRUSTED_PEER = "203.0.113.50"

        /** Per-test untrusted peers, so no test inherits a bucket another test drained. */
        private const val FORGED_XFF_PEER = "203.0.113.51"
        private const val NO_XFF_PEER = "203.0.113.52"
    }

    private fun login(remoteAddr: String, forwardedFor: String? = null): Int {
        val builder = post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"trusted-proxy@example.com","password":"password123"}""")
            .with { request -> request.remoteAddr = remoteAddr; request }
        if (forwardedFor != null) builder.header("X-Forwarded-For", forwardedFor)
        return mockMvc.perform(builder).andReturn().response.status
    }

    @Test
    fun `a forged X-Forwarded-For from an untrusted remoteAddr never gets a fresh bucket`() {
        assertNotEquals(UNTRUSTED_PEER, FORGED_XFF_PEER)

        val first = login(FORGED_XFF_PEER, "10.201.0.1")
        val second = login(FORGED_XFF_PEER, "10.201.0.2")
        assertNotEquals(429, first, "request 1 must consume from a fresh bucket")
        assertNotEquals(429, second, "request 2 must consume from a fresh bucket")

        val third = login(FORGED_XFF_PEER, "10.201.0.3")
        assertEquals(
            429, third,
            "three distinct forged X-Forwarded-For values from one untrusted peer must share that peer's bucket"
        )
    }

    @Test
    fun `an untrusted remoteAddr without X-Forwarded-For is throttled at capacity`() {
        val first = login(NO_XFF_PEER)
        val second = login(NO_XFF_PEER)
        assertNotEquals(429, first, "request 1 must consume from a fresh bucket")
        assertNotEquals(429, second, "request 2 must consume from a fresh bucket")

        assertEquals(429, login(NO_XFF_PEER), "the third request from one untrusted peer must be throttled")
    }
}
