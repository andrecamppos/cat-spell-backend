package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.concurrent.atomic.AtomicInteger

private const val LANDING_ORIGIN = "https://landing.example"
private const val FOREIGN_ORIGIN = "https://evil.example"

private fun preflight(path: String, origin: String) =
    options(path)
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")

/**
 * RESEARCH Pattern 7 / Open Question 2: with `app.waitlist.allowed-origins` configured, only that explicit origin
 * may call POST /api/waitlist cross-origin, never with credentials, and no other path gains CORS access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.waitlist.allowed-origins=$LANDING_ORIGIN"])
class WaitlistCorsIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    companion object {
        private val counter = AtomicInteger(0)
    }

    @Test
    fun `preflight from the configured origin is allowed for POST without credentials`() {
        val response = mockMvc.perform(preflight("/api/waitlist", LANDING_ORIGIN))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LANDING_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
            .andReturn().response

        val allowedMethods = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
        assertNotNull(allowedMethods, "the preflight must list the allowed methods")
        assertTrue(allowedMethods!!.contains("POST"), "POST must be allowed, got $allowedMethods")
    }

    @Test
    fun `preflight from a foreign origin gets no Access-Control-Allow-Origin`() {
        mockMvc.perform(preflight("/api/waitlist", FOREIGN_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }

    @Test
    fun `actual join from the configured origin is accepted with Access-Control-Allow-Origin`() {
        val n = counter.incrementAndGet()
        mockMvc.perform(
            post("/api/waitlist")
                .header(HttpHeaders.ORIGIN, LANDING_ORIGIN)
                .header("X-Forwarded-For", "10.171.0.$n")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cors-join-$n@example.com"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LANDING_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }

    @Test
    fun `preflight to a non-join path from the configured origin gets no Access-Control-Allow-Origin`() {
        mockMvc.perform(preflight("/api/auth/login", LANDING_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }
}

/**
 * Default (blank) `app.waitlist.allowed-origins`: the CORS mapping is inert and the join emits no CORS headers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WaitlistCorsDisabledIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `blank allowed-origins emits no CORS headers for the join`() {
        mockMvc.perform(preflight("/api/waitlist", LANDING_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }
}
