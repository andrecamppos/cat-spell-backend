package com.catspell.api.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun registerUser(email: String, password: String = "password123") {
        val body = mapOf("email" to email, "password" to password)
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
    }

    @Test
    fun `validation error format - invalid email`() {
        val body = mapOf("email" to "not-an-email", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Validation Error"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Validation failed"))
            .andExpect(jsonPath("$.violations").isArray)
            .andExpect(jsonPath("$.violations[0].field").value("email"))
    }

    @Test
    fun `validation error - password too short`() {
        val body = mapOf("email" to "short-pass@example.com", "password" to "short")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violations").isArray)
            .andExpect(jsonPath("$.violations[0].field").value("password"))
            .andExpect(jsonPath("$.violations[0].message").value("must be at least 8 characters"))
    }

    @Test
    fun `validation error - multiple fields`() {
        val body = mapOf("email" to "bad", "password" to "short")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violations").isArray)
            .andExpect(jsonPath("$.violations.length()").value(2))
    }

    @Test
    fun `duplicate email error`() {
        registerUser("dup-error@example.com")
        val body = mapOf("email" to "dup-error@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `auth error vague - wrong password`() {
        registerUser("vague-auth@example.com")
        val body = mapOf("email" to "vague-auth@example.com", "password" to "wrongpassword")
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.detail").value("Invalid credentials"))
            .andReturn()

        val responseBody = result.response.contentAsString
        assert(!responseBody.contains("password", ignoreCase = true) ||
                responseBody.contains("Invalid credentials")) {
            "Auth error should not reveal which field is wrong"
        }
    }

    @Test
    fun `auth error same for missing email - prevents user enumeration`() {
        val body = mapOf("email" to "does-not-exist@example.com", "password" to "password123")
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.detail").value("Invalid credentials"))
    }

    @Test
    fun `error response content type is application problem+json`() {
        val body = mapOf("email" to "content-type@example.com", "password" to "short")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
    }

    @Test
    fun `missing request body`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
    }
}
