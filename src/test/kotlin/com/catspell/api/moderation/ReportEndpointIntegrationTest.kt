package com.catspell.api.moderation

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.moderation.model.BlockRepository
import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ReportEndpointIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var blockRepository: BlockRepository

    // ---- helpers (authenticated report tests need only a JWT + existing target rows) ----

    private fun registerAndGetToken(email: String): String {
        val body = mapOf("email" to email, "password" to "password123", "dateOfBirth" to "2000-01-15")
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
        markEmailVerified(email)
        val result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["accessToken"].asText()
    }

    private fun userId(email: String): UUID =
        UUID.fromString(jdbcTemplate.queryForObject("SELECT id::text FROM users WHERE email = ?", String::class.java, email))

    private fun reportJson(reportedUserId: UUID, category: String, details: String, alsoBlock: Boolean = false): String =
        objectMapper.writeValueAsString(
            mapOf(
                "reportedUserId" to reportedUserId.toString(),
                "category" to category,
                "details" to details,
                "alsoBlock" to alsoBlock
            )
        )

    private fun postReport(token: String, json: String) =
        mockMvc.perform(
            post("/api/reports")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        )

    // ---- tests ----

    @Test
    fun `valid report returns 201 with reportId`() {
        val token = registerAndGetToken("re-ok-reporter@example.com")
        val reported = registerAndGetToken("re-ok-reported@example.com").let { userId("re-ok-reported@example.com") }

        postReport(token, reportJson(reported, "SPAM", "spammy behavior"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reportId").exists())
    }

    @Test
    fun `blank details returns 400`() {
        val token = registerAndGetToken("re-blank-reporter@example.com")
        val reported = registerAndGetToken("re-blank-reported@example.com").let { userId("re-blank-reported@example.com") }

        postReport(token, reportJson(reported, "SPAM", "   "))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `details of exactly 1000 chars returns 201 and 1001 returns 400`() {
        val token = registerAndGetToken("re-boundary-reporter@example.com")
        val reported = registerAndGetToken("re-boundary-reported@example.com").let { userId("re-boundary-reported@example.com") }

        postReport(token, reportJson(reported, "HARASSMENT", "a".repeat(1000)))
            .andExpect(status().isCreated)

        postReport(token, reportJson(reported, "HARASSMENT", "a".repeat(1001)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `unknown lowercase category returns 400`() {
        val token = registerAndGetToken("re-cat-reporter@example.com")
        val reported = registerAndGetToken("re-cat-reported@example.com").let { userId("re-cat-reported@example.com") }

        postReport(token, reportJson(reported, "spam", "lowercase category"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `self report returns 400 with Bad Request title`() {
        val token = registerAndGetToken("re-self@example.com")
        val self = userId("re-self@example.com")

        postReport(token, reportJson(self, "OTHER", "reporting myself"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Bad Request"))
    }

    @Test
    fun `non-existent target returns 404`() {
        val token = registerAndGetToken("re-404-reporter@example.com")

        postReport(token, reportJson(UUID.randomUUID(), "FAKE_PROFILE", "ghost target"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `over per-reporter cap returns 429`() {
        val token = registerAndGetToken("re-cap-reporter@example.com")
        val reported = registerAndGetToken("re-cap-reported@example.com").let { userId("re-cap-reported@example.com") }

        // Default per-reporter capacity is 5 — the request AT the cap still succeeds.
        repeat(5) {
            postReport(token, reportJson(reported, "SPAM", "report $it"))
                .andExpect(status().isCreated)
        }
        postReport(token, reportJson(reported, "SPAM", "over the cap"))
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `unauthenticated report returns 401`() {
        val reported = registerAndGetToken("re-auth-reported@example.com").let { userId("re-auth-reported@example.com") }

        mockMvc.perform(
            post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reportJson(reported, "SPAM", "no auth"))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `alsoBlock true returns 201 and blocks the reported user`() {
        val token = registerAndGetToken("re-block-reporter@example.com")
        val reporter = userId("re-block-reporter@example.com")
        val reported = registerAndGetToken("re-block-reported@example.com").let { userId("re-block-reported@example.com") }

        postReport(token, reportJson(reported, "INAPPROPRIATE_CONTENT", "block them", alsoBlock = true))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reportId").exists())

        assertTrue(blockRepository.existsByBlockerIdAndBlockedId(reporter, reported))
    }
}
