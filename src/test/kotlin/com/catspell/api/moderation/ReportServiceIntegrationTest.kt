package com.catspell.api.moderation

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.common.exception.SelfReportException
import com.catspell.api.moderation.model.ReportCategory
import com.catspell.api.moderation.model.ReportRepository
import com.catspell.api.moderation.service.ReportService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ReportServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    @Autowired lateinit var reportService: ReportService
    @Autowired lateinit var reportRepository: ReportRepository

    // ---- setup helpers ----

    /** Register a verified user and return its id. Service-layer tests only need the user row to exist. */
    private fun registerUser(email: String): UUID {
        val body = mapOf("email" to email, "password" to "password123", "dateOfBirth" to "2000-01-15")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
        markEmailVerified(email)
        return userId(email)
    }

    private fun userId(email: String): UUID =
        UUID.fromString(jdbcTemplate.queryForObject("SELECT id::text FROM users WHERE email = ?", String::class.java, email))

    private fun blocksBetween(a: UUID, b: UUID): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM blocks WHERE blocker_id = ? AND blocked_id = ?",
            Int::class.java, a, b
        )!!

    // ---- tests ----

    @Test
    fun `valid report persists exactly one row and returns id`() {
        val reporter = registerUser("rs-ok-reporter@example.com")
        val reported = registerUser("rs-ok-reported@example.com")

        val id = reportService.report(reporter, reported, ReportCategory.SPAM, "spammy behavior", alsoBlock = false)

        assertEquals(1, reportRepository.countByReportedId(reported))
        assertTrue(reportRepository.existsById(id))
    }

    @Test
    fun `repeat report of the same pair persists a second row (no dedupe)`() {
        val reporter = registerUser("rs-dup-reporter@example.com")
        val reported = registerUser("rs-dup-reported@example.com")

        reportService.report(reporter, reported, ReportCategory.HARASSMENT, "first", alsoBlock = false)
        reportService.report(reporter, reported, ReportCategory.HARASSMENT, "second", alsoBlock = false)

        assertEquals(2, reportRepository.countByReportedId(reported))
    }

    @Test
    fun `self report throws SelfReportException and persists no row`() {
        val reporter = registerUser("rs-self@example.com")

        assertThrows<SelfReportException> {
            reportService.report(reporter, reporter, ReportCategory.OTHER, "myself", alsoBlock = false)
        }
        assertEquals(0, reportRepository.countByReportedId(reporter))
    }

    @Test
    fun `non-existent target throws ResourceNotFoundException and persists no row`() {
        val reporter = registerUser("rs-404-reporter@example.com")
        val ghost = UUID.randomUUID()

        assertThrows<ResourceNotFoundException> {
            reportService.report(reporter, ghost, ReportCategory.FAKE_PROFILE, "ghost target", alsoBlock = false)
        }
        assertEquals(0, reportRepository.countByReportedId(ghost))
    }

    @Test
    fun `exceeding per-reporter cap throws 429 while a different reporter is unaffected`() {
        val reporter = registerUser("rs-cap-reporter@example.com")
        val other = registerUser("rs-cap-other@example.com")
        val reported = registerUser("rs-cap-reported@example.com")

        // Default per-reporter capacity is 5; the request AT the cap still succeeds.
        repeat(5) {
            reportService.report(reporter, reported, ReportCategory.SPAM, "report $it", alsoBlock = false)
        }

        val ex = assertThrows<ResponseStatusException> {
            reportService.report(reporter, reported, ReportCategory.SPAM, "over the cap", alsoBlock = false)
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.statusCode)

        // A different reporter has an independent bucket and is unaffected.
        val otherId = reportService.report(other, reported, ReportCategory.SPAM, "different reporter", alsoBlock = false)
        assertTrue(reportRepository.existsById(otherId))
    }

    @Test
    fun `alsoBlock true creates a block row for the pair`() {
        val reporter = registerUser("rs-block-reporter@example.com")
        val reported = registerUser("rs-block-reported@example.com")

        reportService.report(reporter, reported, ReportCategory.INAPPROPRIATE_CONTENT, "block them", alsoBlock = true)

        assertEquals(1, reportRepository.countByReportedId(reported))
        assertEquals(1, blocksBetween(reporter, reported))
    }

    @Test
    fun `alsoBlock false creates no block row`() {
        val reporter = registerUser("rs-noblock-reporter@example.com")
        val reported = registerUser("rs-noblock-reported@example.com")

        reportService.report(reporter, reported, ReportCategory.INAPPROPRIATE_CONTENT, "no block", alsoBlock = false)

        assertEquals(1, reportRepository.countByReportedId(reported))
        assertEquals(0, blocksBetween(reporter, reported))
    }
}
