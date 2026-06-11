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

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun registerUser(email: String = "test@example.com", password: String = "password123"): String {
        val body = mapOf("email" to email, "password" to password)
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andReturn()
        return result.response.contentAsString
    }

    private fun registerAndExtractToken(email: String, password: String = "password123"): String {
        val responseBody = registerUser(email, password)
        val json = objectMapper.readTree(responseBody)
        return json["accessToken"].asText()
    }

    @Test
    fun `register successfully`() {
        val body = mapOf("email" to "register-success@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `register duplicate email`() {
        registerUser(email = "duplicate@example.com")
        val body = mapOf("email" to "duplicate@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
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
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `login successfully`() {
        registerUser(email = "login-success@example.com", password = "password123")
        val body = mapOf("email" to "login-success@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `login invalid credentials`() {
        registerUser(email = "login-fail@example.com", password = "password123")
        val body = mapOf("email" to "login-fail@example.com", "password" to "wrongpassword")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
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
}
