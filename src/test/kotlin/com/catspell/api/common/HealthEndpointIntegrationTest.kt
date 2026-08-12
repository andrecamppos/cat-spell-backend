package com.catspell.api.common

import tools.jackson.databind.ObjectMapper
import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val body = mapOf("email" to email, "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", "10.1.0.1")
        )
        markEmailVerified(email)
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", "10.1.0.1")
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return json["accessToken"].asText()
    }

    @Test
    fun `should return aggregate health status for anonymous request`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
    }

    @Test
    fun `should return detailed health for authenticated request`() {
        val token = registerAndGetToken("health-detail@example.com")
        mockMvc.perform(
            get("/actuator/health")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.db").exists())
            .andExpect(jsonPath("$.components.s3").exists())
            .andExpect(jsonPath("$.components.webSocket").exists())
    }

    @Test
    fun `should report S3 health status`() {
        val token = registerAndGetToken("health-s3@example.com")
        mockMvc.perform(
            get("/actuator/health")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.s3.status").value("UP"))
            .andExpect(jsonPath("$.components.s3.details.bucket").value("catspell-photos"))
    }

    @Test
    fun `should report WebSocket health status`() {
        val token = registerAndGetToken("health-ws@example.com")
        mockMvc.perform(
            get("/actuator/health")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.webSocket.status").value("UP"))
            .andExpect(jsonPath("$.components.webSocket.details.activeSessions").exists())
    }

    @Test
    fun `should report database health status`() {
        val token = registerAndGetToken("health-db@example.com")
        mockMvc.perform(
            get("/actuator/health")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.db.status").value("UP"))
    }

    @Test
    fun `should not expose info endpoint`() {
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isUnauthorized)
    }
}
