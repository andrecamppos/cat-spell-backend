package com.catspell.api.moderation

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.email.service.EmailSender
import com.catspell.api.moderation.model.ReportCategory
import com.catspell.api.moderation.model.ReportRepository
import com.catspell.api.moderation.service.ReportService
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.util.UUID

/**
 * Proves the MOD-07 out-of-band operator notification: after a report commits, the operator is
 * emailed asynchronously (AFTER_COMMIT, off-thread), and a failing email send neither propagates to
 * the caller nor rolls back the persisted report. EmailSender is replaced with a MockK @Primary bean
 * so the send is observable and can be made to fail.
 */
@SpringBootTest
@Import(ReportNotificationIntegrationTest.MockEmailConfig::class)
class ReportNotificationIntegrationTest : BaseIntegrationTest() {

    @TestConfiguration
    class MockEmailConfig {
        @Bean
        @Primary
        fun emailSender(): EmailSender = mockk(relaxed = true)
    }

    @Autowired lateinit var reportService: ReportService
    @Autowired lateinit var reportRepository: ReportRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var emailSender: EmailSender

    @Value("\${app.report.operator-email}")
    lateinit var operatorEmail: String

    @BeforeEach
    fun resetMock() {
        clearMocks(emailSender)
    }

    private fun createUser(email: String): UUID {
        val user = userRepository.save(User(email = email, passwordHash = "test-hash"))
        return user.id!!
    }

    @Test
    fun `operator is emailed once after the report commits`() {
        val reporter = createUser("rn-ok-reporter@example.com")
        val reported = createUser("rn-ok-reported@example.com")

        val id = reportService.report(reporter, reported, ReportCategory.SPAM, "spammy", alsoBlock = false)

        // Report row is committed synchronously on the calling thread.
        assertTrue(reportRepository.existsById(id))

        // The operator email is sent asynchronously AFTER commit, addressed solely to the operator.
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(exactly = 1) { emailSender.send(match { it.to == operatorEmail }) }
        }
        // The reported user is never a recipient.
        verify(exactly = 0) { emailSender.send(match { it.to != operatorEmail }) }
    }

    @Test
    fun `report survives an email-send failure`() {
        val reporter = createUser("rn-fail-reporter@example.com")
        val reported = createUser("rn-fail-reported@example.com")

        every { emailSender.send(any()) } throws RuntimeException("simulated mail outage")

        // The report call must return normally despite the async send failure.
        val id = reportService.report(reporter, reported, ReportCategory.HARASSMENT, "abusive", alsoBlock = false)
        assertNotNull(id)

        // The committed report survives the email-send failure (MOD-07 success criterion).
        assertTrue(reportRepository.existsById(id))

        // The failing send was still attempted (exception swallowed by the listener, not propagated).
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(atLeast = 1) { emailSender.send(match { it.to == operatorEmail }) }
        }
    }
}
