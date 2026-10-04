package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.email.service.EmailMessage
import com.catspell.api.email.service.EmailResult
import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.EmailSender
import com.catspell.api.waitlist.event.WaitlistConfirmationRequestedEvent
import com.catspell.api.waitlist.service.WaitlistService
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves WAIT-02 double opt-in: a join that creates or rotates a PENDING entry emails exactly one single-use
 * confirmation link after commit (only the SHA-256 hash is stored), CONFIRMED/INVITED re-joins send nothing (D-04),
 * and the confirmation event never exposes the token or the address through toString (D-08).
 * EmailSender is replaced by a MockK @Primary bean; the send runs on the async executor, so captures go into a
 * thread-safe list and are awaited. Each test uses a distinct address because per-email buckets live in the
 * service bean for the whole cached context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(WaitlistConfirmIntegrationTest.MockEmailConfig::class)
class WaitlistConfirmIntegrationTest : BaseIntegrationTest() {

    @TestConfiguration
    class MockEmailConfig {
        @Bean
        @Primary
        fun emailSender(): EmailSender = mockk(relaxed = true)
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var emailSender: EmailSender
    @Autowired lateinit var waitlistService: WaitlistService

    private val sentMessages = CopyOnWriteArrayList<EmailMessage>()

    @BeforeEach
    fun setupEmailCapture() {
        clearMocks(emailSender)
        sentMessages.clear()
        every { emailSender.send(capture(sentMessages)) } returns
            EmailResult(EmailSendStatus.SUCCESS, messageId = "test")
    }

    private fun join(email: String) {
        mockMvc.perform(
            post("/api/waitlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andExpect(status().isAccepted)
    }

    private fun awaitEmails(n: Int) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted { assertEquals(n, sentMessages.size) }
    }

    private fun awaitNoEmail() {
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).until { sentMessages.isEmpty() }
    }

    private fun tokenFrom(message: EmailMessage): String =
        Regex("token=([A-Za-z0-9_-]+)").find(message.textBody)?.groupValues?.get(1)
            ?: error("No confirm token found in captured email body")

    private fun sha256Hex(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    private fun storedHash(normalizedEmail: String): String? = jdbcTemplate.queryForObject(
        "SELECT confirm_token_hash FROM waitlist_entries WHERE normalized_email = ?",
        String::class.java,
        normalizedEmail
    )

    private fun setStatus(normalizedEmail: String, status: String) {
        jdbcTemplate.update("UPDATE waitlist_entries SET status = ? WHERE normalized_email = ?", status, normalizedEmail)
    }

    // ---- Task 1: confirmation email after commit ----

    @Test
    fun `first join sends exactly one confirmation email whose token hashes to the stored hash`() {
        join("  confirm-first@example.com ")

        awaitEmails(1)
        val message = sentMessages.single()
        assertEquals("confirm-first@example.com", message.to, "the email goes to the stored (trimmed) address")
        assertTrue(
            message.textBody.contains("http://localhost:8080/api/waitlist/confirm?token="),
            "the link must be app.waitlist.confirm-url + ?token="
        )
        val token = tokenFrom(message)
        assertEquals(storedHash("confirm-first@example.com"), sha256Hex(token), "only the SHA-256 of the token is stored")
    }

    /** Moves the entry's updated_at 16 minutes into the past, just outside the default 15-minute resend cooldown. */
    private fun backdate(normalizedEmail: String) {
        jdbcTemplate.update(
            "UPDATE waitlist_entries SET updated_at = updated_at - INTERVAL '16 minutes' WHERE normalized_email = ?",
            normalizedEmail
        )
    }

    @Test
    fun `a pending re-join inside the cooldown sends no second email`() {
        join("confirm-cooldown@example.com")
        awaitEmails(1)
        val firstToken = tokenFrom(sentMessages[0])

        join("confirm-cooldown@example.com")

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted {
            assertEquals(1, sentMessages.size, "a re-join inside the resend cooldown must not send a second email")
        }
        assertEquals(storedHash("confirm-cooldown@example.com"), sha256Hex(firstToken), "the first link must stay live")
    }

    @Test
    fun `a variant re-join outside the cooldown mails the first stored address`() {
        join("pin-first@example.com")
        awaitEmails(1)
        backdate("pin-first@example.com")

        join("Pin-First+x@example.com")
        awaitEmails(2)

        assertEquals("pin-first@example.com", sentMessages[1].to, "the fresh link must go to the address stored at first insert")
        assertEquals(storedHash("pin-first@example.com"), sha256Hex(tokenFrom(sentMessages[1])))
    }

    @Test
    fun `pending re-join outside the cooldown sends a second email with a fresh token and the stored hash matches the newest`() {
        join("confirm-rejoin@example.com")
        awaitEmails(1)
        backdate("confirm-rejoin@example.com")
        join("confirm-rejoin@example.com")
        awaitEmails(2)

        val first = tokenFrom(sentMessages[0])
        val second = tokenFrom(sentMessages[1])
        assertNotEquals(first, second, "a pending re-join must mint a new token")
        assertEquals(storedHash("confirm-rejoin@example.com"), sha256Hex(second), "the stored hash must match the newest token")
    }

    @Test
    fun `re-join of a CONFIRMED entry sends no email`() {
        join("confirm-done@example.com")
        awaitEmails(1)
        sentMessages.clear()
        setStatus("confirm-done@example.com", "CONFIRMED")

        join("confirm-done@example.com")
        awaitNoEmail()
    }

    @Test
    fun `re-join of an INVITED entry sends no email`() {
        join("confirm-invited@example.com")
        awaitEmails(1)
        sentMessages.clear()
        setStatus("confirm-invited@example.com", "INVITED")

        join("confirm-invited@example.com")
        awaitNoEmail()
    }

    @Test
    fun `confirmation event toString redacts the token and the email`() {
        val text = WaitlistConfirmationRequestedEvent("a@b.example", "tok-123").toString()
        assertFalse(text.contains("tok-123"), "event toString must not contain the raw token")
        assertFalse(text.contains("a@b.example"), "event toString must not contain the email")
    }

    // ---- Task 2: GET /api/waitlist/confirm single-use claim + 302 ----

    private fun joinAndCaptureToken(email: String): String {
        val before = sentMessages.size
        join(email)
        awaitEmails(before + 1)
        return tokenFrom(sentMessages.last())
    }

    private fun confirmVia(token: String?) = mockMvc.perform(
        if (token == null) get("/api/waitlist/confirm") else get("/api/waitlist/confirm").param("token", token)
    )

    private fun rowSnapshot(normalizedEmail: String): Map<String, Any?> = jdbcTemplate.queryForMap(
        "SELECT status, confirm_token_hash, confirm_token_expires_at, confirmed_at, updated_at " +
            "FROM waitlist_entries WHERE normalized_email = ?",
        normalizedEmail
    )

    @Test
    fun `valid token confirms the entry and redirects to the success URL with no-referrer and no-store`() {
        val token = joinAndCaptureToken("confirm-valid@example.com")

        confirmVia(token)
            .andExpect(status().isFound)
            .andExpect(header().string("Location", CONFIRM_SUCCESS_URL))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Cache-Control", "no-store"))

        val row = rowSnapshot("confirm-valid@example.com")
        assertEquals("CONFIRMED", row["status"])
        assertNotNull(row["confirmed_at"], "confirmed_at must be stamped")
        assertEquals(sha256Hex(token), row["confirm_token_hash"], "the hash is deliberately kept after confirm")
    }

    @Test
    fun `reused token redirects to the error URL and leaves confirmed_at unchanged`() {
        val token = joinAndCaptureToken("confirm-reuse@example.com")
        confirmVia(token).andExpect(header().string("Location", CONFIRM_SUCCESS_URL))
        val confirmedAt = rowSnapshot("confirm-reuse@example.com")["confirmed_at"]

        confirmVia(token)
            .andExpect(status().isFound)
            .andExpect(header().string("Location", CONFIRM_ERROR_URL))

        assertEquals(confirmedAt, rowSnapshot("confirm-reuse@example.com")["confirmed_at"])
    }

    @Test
    fun `unknown, blank and missing tokens redirect to the error URL and change no row`() {
        joinAndCaptureToken("confirm-unknown@example.com")
        val before = rowSnapshot("confirm-unknown@example.com")

        for (token in listOf("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "", null)) {
            confirmVia(token)
                .andExpect(status().isFound)
                .andExpect(header().string("Location", CONFIRM_ERROR_URL))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"))
        }

        assertEquals(before, rowSnapshot("confirm-unknown@example.com"), "no failed confirm may change the row")
    }

    @Test
    fun `token expired by one second redirects to the error URL while an unexpired token succeeds`() {
        val expiredToken = joinAndCaptureToken("confirm-expired@example.com")
        jdbcTemplate.update(
            "UPDATE waitlist_entries SET confirm_token_expires_at = ? WHERE normalized_email = ?",
            Timestamp.from(Instant.now().minusSeconds(1)),
            "confirm-expired@example.com"
        )

        confirmVia(expiredToken).andExpect(header().string("Location", CONFIRM_ERROR_URL))
        assertEquals("PENDING", rowSnapshot("confirm-expired@example.com")["status"], "expired claim must not confirm")

        val freshToken = joinAndCaptureToken("confirm-unexpired@example.com")
        confirmVia(freshToken).andExpect(header().string("Location", CONFIRM_SUCCESS_URL))
        assertEquals("CONFIRMED", rowSnapshot("confirm-unexpired@example.com")["status"])
    }

    @Test
    fun `pending re-join invalidates the first link and only the newest token confirms`() {
        val firstToken = joinAndCaptureToken("confirm-rotated@example.com")
        backdate("confirm-rotated@example.com")
        val secondToken = joinAndCaptureToken("confirm-rotated@example.com")

        confirmVia(firstToken).andExpect(header().string("Location", CONFIRM_ERROR_URL))
        assertEquals("PENDING", rowSnapshot("confirm-rotated@example.com")["status"])

        confirmVia(secondToken).andExpect(header().string("Location", CONFIRM_SUCCESS_URL))
        assertEquals("CONFIRMED", rowSnapshot("confirm-rotated@example.com")["status"])
    }

    @Test
    fun `eight concurrent confirms of one token yield exactly one success`() {
        val token = joinAndCaptureToken("confirm-race@example.com")
        val pool = Executors.newFixedThreadPool(8)
        val startGate = CountDownLatch(1)
        try {
            val futures = (1..8).map {
                pool.submit<Boolean> {
                    startGate.await()
                    waitlistService.confirm(token)
                }
            }
            startGate.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it }, "exactly one concurrent confirm may win the single-use claim")
        } finally {
            pool.shutdownNow()
        }
        assertEquals("CONFIRMED", rowSnapshot("confirm-race@example.com")["status"])
    }

    companion object {
        /** Literal values of app.waitlist.confirm-success-url / app.waitlist.confirm-error-url in the test yml. */
        const val CONFIRM_SUCCESS_URL = "http://localhost:3000/waitlist/confirmed"
        const val CONFIRM_ERROR_URL = "http://localhost:3000/waitlist/link-invalid"
    }
}
