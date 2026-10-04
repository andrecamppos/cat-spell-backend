package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.common.security.JwtAuthenticationFilter
import com.catspell.api.waitlist.controller.WaitlistController
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.atomic.AtomicInteger

/**
 * WAIT-01 success criterion 1 / D-04: no caller can tell new, pending, confirmed, invited, `+suffix` and per-email
 * throttled addresses apart from the join response. Also proves the JWT filter ignores stale Bearer headers on the
 * public waitlist routes (RESEARCH Pattern 1 step 2) and the operator routes (D-12), while still rejecting them on
 * protected routes and on look-alike paths (IN-06).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WaitlistEnumerationSafetyIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    companion object {
        private const val STALE_BEARER = "Bearer garbage"
        private val counter = AtomicInteger(0)
    }

    private data class JoinShape(val status: Int, val contentType: String?, val body: String)

    /** Distinct local part per call: the per-email buckets persist for the whole cached context. */
    private fun address(label: String): String = "enum-$label-${counter.incrementAndGet()}@example.com"

    private fun join(email: String): JoinShape {
        val response = mockMvc.perform(
            post("/api/waitlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andReturn().response
        return JoinShape(response.status, response.contentType, response.contentAsString)
    }

    private fun setStatus(normalizedEmail: String, status: String) {
        val updated = jdbcTemplate.update(
            "UPDATE waitlist_entries SET status = ? WHERE normalized_email = ?", status, normalizedEmail
        )
        assertEquals(1, updated, "test setup: $normalizedEmail must exist to become $status")
    }

    /**
     * Calls the filter bean directly with servletPath set explicitly, as Tomcat does for the DispatcherServlet "/"
     * mapping. MockMvc leaves servletPath empty; the skip list reads RequestPaths.normalized, so it applies in both
     * shapes, and these tests pin the production (Tomcat) shape.
     */
    private fun runJwtFilter(method: String, servletPath: String): Pair<MockFilterChain, MockHttpServletResponse> {
        val request = MockHttpServletRequest(method, servletPath).apply {
            this.servletPath = servletPath
            addHeader(HttpHeaders.AUTHORIZATION, STALE_BEARER)
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        jwtAuthenticationFilter.doFilter(request, response, chain)
        return chain to response
    }

    @Test
    fun `a stale Bearer header does not block the public join`() {
        val (chain, response) = runJwtFilter("POST", "/api/waitlist")
        assertNotNull(chain.request, "the join must reach the rest of the filter chain")
        assertEquals(200, response.status, "the JWT filter must not write a 401 on the join")
    }

    @Test
    fun `a stale Bearer header does not block the confirm link`() {
        val (chain, response) = runJwtFilter("GET", "/api/waitlist/confirm")
        assertNotNull(chain.request, "the confirm link must reach the rest of the filter chain")
        assertEquals(200, response.status, "the JWT filter must not write a 401 on the confirm link")
    }

    @Test
    fun `a stale Bearer header is still rejected on a protected route`() {
        val (chain, response) = runJwtFilter("GET", "/api/profile")
        assertNull(chain.request, "a protected route with an invalid token must stop at the JWT filter")
        assertEquals(401, response.status)
    }

    @Test
    fun `a stale Bearer header does not block the operator waitlist list`() {
        // D-12: operator routes authenticate with X-Admin-Token in AdminTokenFilter, never with a JWT.
        val (chain, response) = runJwtFilter("GET", "/api/admin/waitlist")
        assertNotNull(chain.request, "the operator list must reach the rest of the filter chain")
        assertNotEquals(401, response.status, "the JWT filter must not write a 401 on an operator route")
    }

    @Test
    fun `a stale Bearer header does not block operator invite issuance`() {
        val (chain, response) = runJwtFilter("POST", "/api/admin/invites")
        assertNotNull(chain.request, "invite issuance must reach the rest of the filter chain")
        assertNotEquals(401, response.status, "the JWT filter must not write a 401 on an operator route")
    }

    @Test
    fun `a stale Bearer header is still rejected on a waitlist look-alike path`() {
        // IN-06: the waitlist skip is exact, so /api/waitlistX keeps its Bearer validation.
        val (chain, response) = runJwtFilter("POST", "/api/waitlistX")
        assertNull(chain.request, "a look-alike path with an invalid token must stop at the JWT filter")
        assertEquals(401, response.status)
    }

    @Test
    fun `new pending confirmed invited suffix-variant and throttled joins are byte-identical`() {
        val newShape = join(address("new"))

        val pending = address("pending")
        join(pending)
        val pendingShape = join(pending)

        val confirmed = address("confirmed")
        join(confirmed)
        setStatus(confirmed, "CONFIRMED")
        val confirmedShape = join(confirmed)

        val invited = address("invited")
        join(invited)
        setStatus(invited, "INVITED")
        val invitedShape = join(invited)

        val suffixBase = address("suffix")
        join(suffixBase)
        setStatus(suffixBase, "CONFIRMED")
        val suffixShape = join(suffixBase.replace("@", "+promo@"))

        val throttled = address("throttled")
        repeat(3) { join(throttled) } // test yml: app.waitlist.per-email-capacity = 3
        val throttledShape = join(throttled)

        val shapes = mapOf(
            "pending duplicate" to pendingShape,
            "confirmed duplicate" to confirmedShape,
            "invited duplicate" to invitedShape,
            "+suffix variant of a confirmed address" to suffixShape,
            "per-email exhausted" to throttledShape
        )
        assertEquals(202, newShape.status)
        shapes.forEach { (state, shape) ->
            assertEquals(newShape, shape, "the $state join must be byte-identical to a new join")
        }
        assertEquals(
            WaitlistController.WAITLIST_JOIN_MESSAGE,
            objectMapper.readTree(newShape.body)["message"].asText()
        )
    }
}
