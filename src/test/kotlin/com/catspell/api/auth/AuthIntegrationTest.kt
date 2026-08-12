package com.catspell.api.auth

import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import com.catspell.api.BaseIntegrationTest
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    companion object {
        private val ipCounter = AtomicInteger(0)
        private fun nextIp(): String {
            val n = ipCounter.incrementAndGet()
            return "10.100.${n / 256}.${n % 256}"
        }
    }

    /** POST /register (Phase 11 contract: 201, generic body, NO tokens). Returns the response body. */
    private fun registerUser(email: String = "test@example.com", password: String = "password123"): String {
        val body = mapOf("email" to email, "password" to password)
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        ).andReturn()
        return result.response.contentAsString
    }

    /** POST /login and return the response body (tokens live here now, not on register). */
    private fun login(email: String, password: String = "password123"): String {
        val body = mapOf("email" to email, "password" to password)
        return mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        ).andReturn().response.contentAsString
    }

    /** Register, verify, log in, and return the access token from the LOGIN response. */
    private fun registerAndExtractToken(email: String, password: String = "password123"): String {
        registerUser(email, password)
        markEmailVerified(email)
        val json = objectMapper.readTree(login(email, password))
        return json["accessToken"].asText()
    }

    @Test
    fun `register successfully returns 201 with generic body and no tokens`() {
        val body = mapOf("email" to "register-success@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.message").isNotEmpty)
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
    }

    @Test
    fun `register duplicate email`() {
        registerUser(email = "duplicate@example.com")
        val body = mapOf("email" to "duplicate@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `register invalid password`() {
        val body = mapOf("email" to "short@example.com", "password" to "short")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `login before verification is forbidden with EMAIL_NOT_VERIFIED`() {
        registerUser(email = "login-unverified@example.com", password = "password123")
        val body = mapOf("email" to "login-unverified@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
    }

    @Test
    fun `login successfully after verification`() {
        registerUser(email = "login-success@example.com", password = "password123")
        markEmailVerified("login-success@example.com")
        val body = mapOf("email" to "login-success@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `login invalid credentials`() {
        registerUser(email = "login-fail@example.com", password = "password123")
        markEmailVerified("login-fail@example.com")
        val body = mapOf("email" to "login-fail@example.com", "password" to "wrongpassword")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login non-existent user`() {
        val body = mapOf("email" to "nonexistent@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoint with valid token`() {
        val token = registerAndExtractToken("protected-valid@example.com")
        mockMvc.perform(
            get("/api/auth/me")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").isNotEmpty)
    }

    @Test
    fun `protected endpoint without token`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoint with invalid token`() {
        mockMvc.perform(
            get("/api/auth/me")
                .header("Authorization", "Bearer invalid.jwt.token")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `register with invalid email`() {
        val body = mapOf("email" to "not-an-email", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `login returns access and refresh tokens after verification`() {
        registerUser(email = "login-refresh@example.com", password = "password123")
        markEmailVerified("login-refresh@example.com")
        val body = mapOf("email" to "login-refresh@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `refresh token successfully`() {
        registerUser(email = "refresh-success@example.com")
        markEmailVerified("refresh-success@example.com")
        val json = objectMapper.readTree(login("refresh-success@example.com"))
        val refreshToken = json["refreshToken"].asText()

        val body = mapOf("refreshToken" to refreshToken)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `refresh with invalid token`() {
        val body = mapOf("refreshToken" to "invalid-refresh-token")
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh token reuse detection`() {
        registerUser(email = "refresh-reuse@example.com")
        markEmailVerified("refresh-reuse@example.com")
        val json = objectMapper.readTree(login("refresh-reuse@example.com"))
        val refreshToken = json["refreshToken"].asText()

        val ip = nextIp()
        val body = mapOf("refreshToken" to refreshToken)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", ip)
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", ip)
        )
            .andExpect(status().isUnauthorized)
    }
}
