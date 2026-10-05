package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.email.service.EmailMessage
import com.catspell.api.email.service.EmailResult
import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.EmailSender
import com.catspell.api.waitlist.service.WaitlistEmailNormalizer
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
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * WAIT-03 / D-07 (WR-03): concurrent joins for one brand-new normalized email mint exactly ONE confirm link. The
 * first rotate stamps the hash and updated_at; every other racing join blocks on the row lock, re-evaluates the
 * resend-cooldown predicate against the committed row, and matches 0 rows. The per-email bucket cap is proven
 * separately by WaitlistPerEmailLimitIntegrationTest, which escapes the cooldown by backdating. EmailSender is a
 * MockK @Primary bean; sends run on the async executor after commit, so captures go into a thread-safe list and are
 * awaited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(WaitlistPerEmailConcurrencyIntegrationTest.MockEmailConfig::class)
class WaitlistPerEmailConcurrencyIntegrationTest : BaseIntegrationTest() {

    @TestConfiguration
    class MockEmailConfig {
        @Bean
        @Primary
        fun emailSender(): EmailSender = mockk(relaxed = true)
    }

    companion object {
        private const val ADDRESS = "concurrency-cap@example.com"
    }

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

    private fun tokenFrom(message: EmailMessage): String =
        Regex("token=([A-Za-z0-9_-]+)").find(message.textBody)?.groupValues?.get(1)
            ?: error("No confirm token found in captured email body")

    private fun sha256Hex(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    @Test
    fun `20 concurrent joins for one new email send exactly one confirmation email`() {
        val pool = Executors.newFixedThreadPool(20)
        val startGate = CountDownLatch(1)
        try {
            val futures: List<Future<*>> = (1..20).map {
                pool.submit {
                    startGate.await()
                    waitlistService.join(ADDRESS)
                }
            }
            startGate.countDown()
            // Future.get() rethrows any failure from a join as an ExecutionException, failing the test.
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        // `during` makes the count hold at exactly 1 for a window, so a late second send cannot slip past.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertEquals(1, sentMessages.size, "the resend cooldown must let exactly one racing join send a link")
        }

        val normalized = WaitlistEmailNormalizer.normalize(ADDRESS)
        val rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM waitlist_entries WHERE normalized_email = ?",
            Int::class.java, normalized
        )
        assertEquals(1, rows, "exactly one row for the normalized address")

        val storedHash = jdbcTemplate.queryForObject(
            "SELECT confirm_token_hash FROM waitlist_entries WHERE normalized_email = ?",
            String::class.java, normalized
        )
        val sentHashes = sentMessages.map { sha256Hex(tokenFrom(it)) }.toSet()
        assertEquals(1, sentHashes.size, "exactly one distinct token may be minted")
        assertEquals(sentHashes.single(), storedHash, "stored confirm_token_hash must be the SHA-256 of the sent token")
    }
}
