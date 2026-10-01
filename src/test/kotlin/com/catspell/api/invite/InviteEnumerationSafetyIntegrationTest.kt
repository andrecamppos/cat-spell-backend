package com.catspell.api.invite

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.invite.service.InviteService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves INV-04 / D-10: with the gate ON, an invalid (never-issued), an already-consumed, and a missing
 * invite code all return a BYTE-IDENTICAL error — same 403 status, same title, same INVITE_REQUIRED code —
 * so a caller cannot distinguish the failure modes. Also confirms a rejected register leaves no orphan account.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.invite.enabled=true"])
class InviteEnumerationSafetyIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var inviteService: InviteService

    companion object {
        private val ipCounter = AtomicInteger(0)
        private fun nextIp(): String {
            val n = ipCounter.incrementAndGet()
            return "10.132.${n / 256}.${n % 256}"
        }
    }

    private data class ErrorShape(val status: Int, val title: String?, val code: String?)

    private fun body(email: String, inviteCode: String?, includeInvite: Boolean): String {
        val m = buildMap {
            put("email", email)
            put("password", "password123")
            put("dateOfBirth", LocalDate.now().minusYears(25).toString())
            if (includeInvite) put("inviteCode", inviteCode)
        }
        return objectMapper.writeValueAsString(m)
    }

    private fun register(email: String, inviteCode: String?, includeInvite: Boolean = true) =
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, inviteCode, includeInvite))
                .header("X-Forwarded-For", nextIp())
        )

    private fun errorOf(email: String, inviteCode: String?, includeInvite: Boolean = true): ErrorShape {
        val res = register(email, inviteCode, includeInvite).andReturn().response
        val json = objectMapper.readTree(res.contentAsString)
        return ErrorShape(res.status, json.get("title")?.asText(), json.get("code")?.asText())
    }

    private fun userCount(email: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Int::class.java, email)!!

    @Test
    fun `invalid consumed and missing codes are indistinguishable`() {
        // (a) never-issued / garbage code
        val invalid = errorOf("enum-invalid@example.com", "never-issued-code")

        // (b) a previously-consumed code: mint it, register once (consumes), then reuse it
        val code = inviteService.create(null)
        register("enum-consume-first@example.com", code).andReturn()
        val consumed = errorOf("enum-consumed@example.com", code)

        // (c) a missing code
        val missing = errorOf("enum-missing@example.com", null, includeInvite = false)

        assertEquals(403, invalid.status)
        assertEquals("INVITE_REQUIRED", invalid.code)
        assertEquals(invalid, consumed, "invalid and consumed codes must return an identical error")
        assertEquals(invalid, missing, "invalid and missing codes must return an identical error")
    }

    @Test
    fun `a rejected gated register writes no orphan users row`() {
        val email = "enum-orphan@example.com"
        errorOf(email, "definitely-not-a-code")
        assertEquals(0, userCount(email), "a failed gated register must not persist an account")
    }
}
