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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves INV-03: with app.invite.enabled=true, register requires a valid unconsumed code. A valid code →
 * 201 and the invite becomes consumed; a missing code → 403 INVITE_REQUIRED with no users row written.
 * The code is minted directly via InviteService.create (not the admin endpoint) to keep this independent
 * of plan 16-03 within parallel wave 3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.invite.enabled=true", "app.invite.admin-token=test-admin-secret"])
class InviteGateIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var inviteService: InviteService

    companion object {
        private val ipCounter = AtomicInteger(0)
        private fun nextIp(): String {
            val n = ipCounter.incrementAndGet()
            return "10.131.${n / 256}.${n % 256}"
        }
    }

    private fun body(email: String, inviteCode: String?, includeInvite: Boolean): String {
        val m = buildMap {
            put("email", email)
            put("password", "password123")
            put("dateOfBirth", LocalDate.now().minusYears(25).toString())
            if (includeInvite) put("inviteCode", inviteCode)
        }
        return objectMapper.writeValueAsString(m)
    }

    private fun register(email: String, inviteCode: String?, includeInvite: Boolean) =
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, inviteCode, includeInvite))
                .header("X-Forwarded-For", nextIp())
        )

    private fun userCount(email: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Int::class.java, email)!!

    private fun consumedInviteCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invites WHERE consumed_at IS NOT NULL", Int::class.java)!!

    @Test
    fun `gated register with a valid code succeeds and consumes the invite`() {
        val code = inviteService.create(null)
        val email = "gated-valid@example.com"

        register(email, code, includeInvite = true).andExpect(status().isCreated)

        assertEquals(1, userCount(email))
        assertEquals(1, consumedInviteCount(), "the valid code must be marked consumed")
    }

    @Test
    fun `gated register with a missing code is rejected 403 and writes no users row`() {
        val email = "gated-missing@example.com"

        register(email, null, includeInvite = false)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("INVITE_REQUIRED"))

        assertEquals(0, userCount(email), "a rejected gated register must leave no orphan account")
    }
}
