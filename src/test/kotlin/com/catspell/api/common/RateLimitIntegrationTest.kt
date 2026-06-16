package com.catspell.api.common

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.common.security.RateLimitFilter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    private val rateLimitFilter = RateLimitFilter()

    private val mockMvcWithFilter: MockMvc by lazy {
        val builder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        @Suppress("UNCHECKED_CAST")
        (builder as org.springframework.test.web.servlet.setup.AbstractMockMvcBuilder<*>).addFilters(rateLimitFilter)
        builder.build()
    }

    private fun postLogin(ip: String) = post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"rate-limit@example.com","password":"password123"}""")
        .header("X-Forwarded-For", ip)

    private fun postRegister(email: String, ip: String) = post("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"$email","password":"password123"}""")
        .header("X-Forwarded-For", ip)

    private fun postRefresh(ip: String) = post("/api/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"refreshToken":"fake-token"}""")
        .header("X-Forwarded-For", ip)

    @Test
    fun `should return rate limit headers on auth requests`() {
        mockMvcWithFilter.perform(postLogin("10.0.1.1"))
            .andExpect(header().exists("X-RateLimit-Remaining"))
            .andExpect(header().exists("X-RateLimit-Reset"))
    }

    @Test
    fun `should allow requests within rate limit`() {
        val ip = "10.0.2.1"
        repeat(10) {
            val result = mockMvcWithFilter.perform(postLogin(ip)).andReturn()
            assertNotEquals(429, result.response.status, "Request ${it + 1} should not be rate limited")
        }
    }

    @Test
    fun `should return 429 when rate limit exceeded`() {
        val ip = "10.0.3.1"
        repeat(10) { mockMvcWithFilter.perform(postLogin(ip)) }
        mockMvcWithFilter.perform(postLogin(ip))
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `should return Retry-After header on 429`() {
        val ip = "10.0.4.1"
        repeat(10) { mockMvcWithFilter.perform(postLogin(ip)) }
        mockMvcWithFilter.perform(postLogin(ip))
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
    }

    @Test
    fun `should return problem+json body on 429`() {
        val ip = "10.0.5.1"
        repeat(10) { mockMvcWithFilter.perform(postLogin(ip)) }
        mockMvcWithFilter.perform(postLogin(ip))
            .andExpect(status().isTooManyRequests)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.title").value("Too Many Requests"))
    }

    @Test
    fun `should rate limit register endpoint`() {
        val ip = "10.0.7.1"
        repeat(10) { mockMvcWithFilter.perform(postRegister("rl-reg-$it@example.com", ip)) }
        mockMvcWithFilter.perform(postRegister("rl-reg-extra@example.com", ip))
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `should rate limit refresh endpoint`() {
        val ip = "10.0.8.1"
        repeat(10) { mockMvcWithFilter.perform(postRefresh(ip)) }
        mockMvcWithFilter.perform(postRefresh(ip))
            .andExpect(status().isTooManyRequests)
    }
}
