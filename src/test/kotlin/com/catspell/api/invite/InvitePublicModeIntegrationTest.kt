package com.catspell.api.invite

import com.catspell.api.BaseIntegrationTest
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
 * Proves INV-01 / D-09: with app.invite.enabled=false the invite path never runs. Register succeeds (201)
 * whether the inviteCode is absent, empty, or garbage, and no invite is consumed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.invite.enabled=false"])
class InvitePublicModeIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    companion object {
        private val ipCounter = AtomicInteger(0)
        private fun nextIp(): String {
            val n = ipCounter.incrementAndGet()
            return "10.130.${n / 256}.${n % 256}"
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
    fun `register with no inviteCode succeeds in public mode`() {
        val email = "public-missing@example.com"
        register(email, null, includeInvite = false).andExpect(status().isCreated)
        assertEquals(1, userCount(email))
        assertEquals(0, consumedInviteCount())
    }

    @Test
    fun `register with an empty inviteCode succeeds in public mode`() {
        val email = "public-empty@example.com"
        register(email, "", includeInvite = true).andExpect(status().isCreated)
        assertEquals(1, userCount(email))
        assertEquals(0, consumedInviteCount())
    }

    @Test
    fun `register with a garbage inviteCode succeeds in public mode`() {
        val email = "public-garbage@example.com"
        register(email, "not-a-real-code", includeInvite = true).andExpect(status().isCreated)
        assertEquals(1, userCount(email))
        assertEquals(0, consumedInviteCount())
    }
}
