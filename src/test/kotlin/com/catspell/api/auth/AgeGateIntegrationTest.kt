package com.catspell.api.auth

import com.catspell.api.BaseIntegrationTest
import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves AGE-01/AGE-02: the register endpoint collects a self-attested date of birth, hard-blocks
 * under-18 signups server-side with 422 + code=UNDER_MINIMUM_AGE (no users row written), accepts and
 * persists an 18+ DOB (201), and rejects a missing/future DOB with a distinct 400 (Bean Validation).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgeGateIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    companion object {
        private val ipCounter = AtomicInteger(0)
        private fun nextIp(): String {
            val n = ipCounter.incrementAndGet()
            return "10.120.${n / 256}.${n % 256}"
        }
    }

    private fun registerBody(email: String, dateOfBirth: String?): String {
        val body = buildMap {
            put("email", email)
            put("password", "password123")
            if (dateOfBirth != null) put("dateOfBirth", dateOfBirth)
        }
        return objectMapper.writeValueAsString(body)
    }

    private fun userCount(email: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Int::class.java, email)!!

    private fun dobOf(email: String): LocalDate? =
        jdbcTemplate.queryForObject("SELECT date_of_birth FROM users WHERE email = ?", java.sql.Date::class.java, email)
            ?.toLocalDate()

    @Test
    fun `AGE-02 - under-18 signup is rejected with 422 code and writes no users row`() {
        val email = "under18@example.com"
        val dob = LocalDate.now().minusYears(17).toString()
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, dob))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("UNDER_MINIMUM_AGE"))

        assertEquals(0, userCount(email), "no users row may be written for an under-18 rejection")
    }

    @Test
    fun `AGE-01 - exactly-18 signup succeeds and persists users date_of_birth`() {
        val email = "exactly18@example.com"
        val dob = LocalDate.now().minusYears(18)
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, dob.toString()))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isCreated)

        assertEquals(dob, dobOf(email), "users.date_of_birth must equal the self-attested DOB")
    }

    @Test
    fun `missing dateOfBirth is a 400 bean-validation error, not a 422`() {
        val email = "no-dob@example.com"
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, null))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isBadRequest)

        assertEquals(0, userCount(email))
    }

    @Test
    fun `future dateOfBirth is a 400 bean-validation error`() {
        val email = "future-dob@example.com"
        val dob = LocalDate.now().plusDays(1).toString()
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, dob))
                .header("X-Forwarded-For", nextIp())
        )
            .andExpect(status().isBadRequest)

        assertEquals(0, userCount(email))
    }
}
