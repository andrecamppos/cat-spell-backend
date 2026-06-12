package com.catspell.api.auth

import com.catspell.api.auth.model.RefreshTokenRepository
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
import java.time.Instant
import com.catspell.api.BaseIntegrationTest

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    private fun registerAndGetTokens(email: String, password: String = "password123"): Pair<String, String> {
        val body = mapOf("email" to email, "password" to password)
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return Pair(json["accessToken"].asText(), json["refreshToken"].asText())
    }

    private fun loginAndGetTokens(email: String, password: String = "password123"): Pair<String, String> {
        val body = mapOf("email" to email, "password" to password)
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return Pair(json["accessToken"].asText(), json["refreshToken"].asText())
    }

    private fun refreshAndGetTokens(refreshToken: String): Pair<String, String> {
        val body = mapOf("refreshToken" to refreshToken)
        val result = mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return Pair(json["accessToken"].asText(), json["refreshToken"].asText())
    }

    @Test
    fun `refresh token successfully`() {
        val (_, refreshToken) = registerAndGetTokens("refresh-success@example.com")
        val body = mapOf("refreshToken" to refreshToken)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `refresh token rotation - old token rejected`() {
        val (_, originalRefreshToken) = registerAndGetTokens("rotation@example.com")
        refreshAndGetTokens(originalRefreshToken)

        val body = mapOf("refreshToken" to originalRefreshToken)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh token theft detection - reuse revokes all tokens`() {
        val (_, tokenA) = registerAndGetTokens("theft@example.com")
        val (_, tokenB) = refreshAndGetTokens(tokenA)

        val bodyA = mapOf("refreshToken" to tokenA)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyA))
        )
            .andExpect(status().isUnauthorized)

        val bodyB = mapOf("refreshToken" to tokenB)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyB))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh token expired`() {
        val (_, refreshToken) = registerAndGetTokens("expired@example.com")
        val storedToken = refreshTokenRepository.findByToken(refreshToken)!!
        storedToken.expiresAt = Instant.now().minusSeconds(3600)
        refreshTokenRepository.save(storedToken)

        val body = mapOf("refreshToken" to refreshToken)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh token invalid`() {
        val body = mapOf("refreshToken" to "invalid-random-uuid")
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `register returns refresh token`() {
        val body = mapOf("email" to "register-refresh@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `login returns refresh token`() {
        registerAndGetTokens("login-refresh@example.com")
        val body = mapOf("email" to "login-refresh@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `multi-device sessions - independent refresh tokens`() {
        registerAndGetTokens("multi-device@example.com")
        val (_, tokenDevice1) = loginAndGetTokens("multi-device@example.com")
        val (_, tokenDevice2) = loginAndGetTokens("multi-device@example.com")

        assertNotEquals(tokenDevice1, tokenDevice2)

        val body1 = mapOf("refreshToken" to tokenDevice1)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body1))
        )
            .andExpect(status().isOk)

        val body2 = mapOf("refreshToken" to tokenDevice2)
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body2))
        )
            .andExpect(status().isOk)
    }
}
