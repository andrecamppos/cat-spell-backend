package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.waitlist.controller.WaitlistController
import com.catspell.api.waitlist.service.WaitlistService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Tracer for Phase 17 (WAIT-01, WAIT-02): an unauthenticated POST /api/waitlist travels V24 → entity → native
 * upsert → service → public controller → SecurityConfig whitelist and leaves one PENDING row holding only the
 * SHA-256 hash of a 7-day confirm token. per-email-capacity is raised so the re-join / concurrency cases plan 17-06
 * adds to this class are not throttled. Each test uses a unique email: per-email buckets live in the service bean
 * for the whole cached context and are not reset by the per-test TRUNCATE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.waitlist.per-email-capacity=100"])
class WaitlistJoinIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `unauthenticated join returns 202 and persists one PENDING entry with a hashed confirm token`() {
        mockMvc.perform(
            post("/api/waitlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"tracer@example.com"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.message").value(WaitlistController.WAITLIST_JOIN_MESSAGE))

        val rows = jdbcTemplate.queryForList(
            "SELECT status, normalized_email, confirm_token_hash, confirm_token_expires_at FROM waitlist_entries"
        )
        assertEquals(1, rows.size, "exactly one waitlist entry must exist")
        val row = rows.single()
        assertEquals("PENDING", row["status"])
        assertEquals("tracer@example.com", row["normalized_email"])
        val hash = row["confirm_token_hash"] as String?
        assertNotNull(hash, "a confirm token hash must be stored")
        assertTrue(Regex("^[0-9a-f]{64}$").matches(hash!!), "confirm_token_hash must be 64-char lowercase SHA-256 hex")
        assertNotNull(row["confirm_token_expires_at"], "the confirm token must carry an expiry")
    }

    // ---- Plan 17-06: join contract (validation, re-join states, concurrency, TTL) ----

    @Autowired lateinit var waitlistService: WaitlistService

    private fun joinRequest(body: String): MvcResult =
        mockMvc.perform(
            post("/api/waitlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andReturn()

    private fun join(email: String): MvcResult = joinRequest("""{"email":"$email"}""")

    private fun rowCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM waitlist_entries", Int::class.java) ?: 0

    private fun entry(normalizedEmail: String): Map<String, Any?> =
        jdbcTemplate.queryForMap(
            "SELECT email, status, confirm_token_hash, updated_at FROM waitlist_entries WHERE normalized_email = ?",
            normalizedEmail
        )

    private fun assertIdenticalResponse(expected: MvcResult, actual: MvcResult) {
        assertEquals(expected.response.status, actual.response.status, "re-join status must match the first join")
        assertEquals(
            expected.response.contentAsString,
            actual.response.contentAsString,
            "re-join body must be byte-identical to the first join (D-04)"
        )
    }

    private fun assertRejectedWithoutWrite(body: String, expectedTitle: String) {
        mockMvc.perform(
            post("/api/waitlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value(expectedTitle))
        assertEquals(0, rowCount(), "a rejected join must not write a row")
    }

    @Test
    fun `an empty email returns 400 Validation Error and writes nothing`() {
        assertRejectedWithoutWrite("""{"email":""}""", VALIDATION_ERROR)
    }

    @Test
    fun `a whitespace-only email returns 400 Validation Error and writes nothing`() {
        assertRejectedWithoutWrite("""{"email":"   "}""", VALIDATION_ERROR)
    }

    @Test
    fun `a malformed email returns 400 Validation Error and writes nothing`() {
        assertRejectedWithoutWrite("""{"email":"not-an-email"}""", VALIDATION_ERROR)
    }

    @Test
    fun `a 256-character email returns 400 Validation Error and writes nothing`() {
        val tooLong = "a".repeat(244) + "@example.com"
        assertEquals(256, tooLong.length)
        assertRejectedWithoutWrite("""{"email":"$tooLong"}""", VALIDATION_ERROR)
    }

    @Test
    fun `a body with no email field returns 400 and writes nothing`() {
        assertRejectedWithoutWrite("""{}""", BAD_REQUEST)
    }

    @Test
    fun `a PENDING re-join rotates the token hash and overwrites email with the latest trimmed submission`() {
        val first = join("pending-rejoin@example.com")
        val before = entry("pending-rejoin@example.com")

        val second = join("  Pending-Rejoin+landing@Example.com  ")
        val after = entry("pending-rejoin@example.com")

        assertIdenticalResponse(first, second)
        assertEquals(1, rowCount(), "a re-join must not create a second row")
        assertEquals("PENDING", after["status"])
        assertNotEquals(before["confirm_token_hash"], after["confirm_token_hash"], "a PENDING re-join must rotate the hash")
        assertEquals("Pending-Rejoin+landing@Example.com", after["email"], "email must be the latest trimmed submission")
    }

    @Test
    fun `a CONFIRMED re-join changes nothing and returns the identical 202`() {
        assertTerminalStateRejoinIsNoOp("confirmed-rejoin@example.com", "CONFIRMED")
    }

    @Test
    fun `an INVITED re-join changes nothing and returns the identical 202`() {
        assertTerminalStateRejoinIsNoOp("invited-rejoin@example.com", "INVITED")
    }

    private fun assertTerminalStateRejoinIsNoOp(address: String, terminalStatus: String) {
        val first = join(address)
        // Push updated_at into the past so any write by the re-join would be visible.
        jdbcTemplate.update(
            """
            UPDATE waitlist_entries
               SET status = ?, confirmed_at = NOW(),
                   invited_at = CASE WHEN ? = 'INVITED' THEN NOW() ELSE NULL END,
                   updated_at = NOW() - INTERVAL '1 day'
             WHERE normalized_email = ?
            """.trimIndent(),
            terminalStatus, terminalStatus, address
        )
        val before = entry(address)

        val second = join(address.uppercase())
        val after = entry(address)

        assertIdenticalResponse(first, second)
        assertEquals(1, rowCount())
        assertEquals(terminalStatus, after["status"], "status must be unchanged")
        assertEquals(before["confirm_token_hash"], after["confirm_token_hash"], "hash must be unchanged")
        assertEquals(before["email"], after["email"], "email must be unchanged")
        assertEquals(before["updated_at"], after["updated_at"], "updated_at must be unchanged (no write)")
    }

    @Test
    fun `8 concurrent joins for the same new email complete without error and leave exactly one row`() {
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val startGate = CountDownLatch(1)
        try {
            val futures: List<Future<*>> = (1..threads).map {
                pool.submit {
                    startGate.await()
                    waitlistService.join("race@example.com")
                }
            }
            startGate.countDown()
            // Future.get() rethrows any failure from a join as an ExecutionException, failing the test.
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(1, rowCount(), "ON CONFLICT DO NOTHING must leave exactly one row under concurrency")
        assertEquals("PENDING", entry("race@example.com")["status"])
    }

    @Test
    fun `confirm_token_expires_at is the join instant plus 168 hours`() {
        val before = Instant.now()
        val result = join("ttl@example.com")
        val after = Instant.now()
        assertEquals(202, result.response.status)

        val expiresAt = jdbcTemplate.queryForObject(
            "SELECT confirm_token_expires_at FROM waitlist_entries WHERE normalized_email = ?",
            Timestamp::class.java, "ttl@example.com"
        )!!.toInstant()

        val lower = before.plus(CONFIRM_TOKEN_TTL).minusMillis(1)
        val upper = after.plus(CONFIRM_TOKEN_TTL).plusMillis(1)
        assertFalse(expiresAt.isBefore(lower), "expiry $expiresAt must be >= $lower")
        assertFalse(expiresAt.isAfter(upper), "expiry $expiresAt must be <= $upper")
    }

    companion object {
        private const val VALIDATION_ERROR = "Validation Error"
        private const val BAD_REQUEST = "Bad Request"
        private val CONFIRM_TOKEN_TTL: Duration = Duration.ofHours(168)
    }
}
