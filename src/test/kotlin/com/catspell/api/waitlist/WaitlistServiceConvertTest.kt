package com.catspell.api.waitlist

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.catspell.api.common.exception.WaitlistInviteDeliveryException
import com.catspell.api.email.service.EmailMessage
import com.catspell.api.email.service.EmailResult
import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.EmailSender
import com.catspell.api.email.service.WaitlistInviteEmailRenderer
import com.catspell.api.invite.service.InviteService
import com.catspell.api.waitlist.model.WaitlistEntry
import com.catspell.api.waitlist.model.WaitlistEntryRepository
import com.catspell.api.waitlist.model.WaitlistStatus
import com.catspell.api.waitlist.service.WaitlistService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * D-10 (WR-05, WR-06): convertToInvite issues the invite before any send, bounds the send with the configured
 * timeout, chains every delivery failure's cause into [WaitlistInviteDeliveryException], lets renderer bugs through
 * unchanged, and logs one address-free WARN per failure. Pure mockk, no Spring context.
 */
class WaitlistServiceConvertTest {

    companion object {
        private const val RECIPIENT = "victim@example.com"
        private const val RAW_CODE = "raw-invite-code"
        private const val SEND_TIMEOUT_MS = 200L
    }

    private val waitlistEntryRepository = mockk<WaitlistEntryRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val inviteService = mockk<InviteService>()
    private val emailSender = mockk<EmailSender>()
    private val renderer = mockk<WaitlistInviteEmailRenderer>()

    private val service = WaitlistService(
        waitlistEntryRepository = waitlistEntryRepository,
        eventPublisher = eventPublisher,
        inviteService = inviteService,
        emailSender = emailSender,
        waitlistInviteEmailRenderer = renderer,
        confirmTokenTtlHours = 168,
        perEmailCapacity = 3,
        perEmailRefillHours = 24,
        resendCooldownMinutes = 15,
        maxTrackedKeys = 1000,
        inviteSendTimeoutMs = SEND_TIMEOUT_MS
    )

    private val entryId = UUID.randomUUID()
    private val message = EmailMessage(RECIPIENT, "subject", "<p>$RAW_CODE</p>", RAW_CODE)

    private val serviceLogger = LoggerFactory.getLogger(WaitlistService::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        appender.start()
        serviceLogger.addAppender(appender)
        val entry = WaitlistEntry(
            id = entryId,
            email = RECIPIENT,
            normalizedEmail = RECIPIENT,
            status = WaitlistStatus.CONFIRMED
        )
        every { waitlistEntryRepository.findById(entryId) } returns Optional.of(entry)
        every {
            waitlistEntryRepository.markInvited(entryId, any(), WaitlistStatus.CONFIRMED, WaitlistStatus.INVITED)
        } returns 1
        every { inviteService.create(null) } returns RAW_CODE
        every { renderer.render(RECIPIENT, RAW_CODE) } returns message
    }

    @AfterEach
    fun tearDown() {
        serviceLogger.detachAppender(appender)
        appender.stop()
        service.shutdownInviteSendExecutor()
    }

    private fun warnEvents(): List<ILoggingEvent> = appender.list.filter { it.level == Level.WARN }

    @Test
    fun `a successful convert creates the invite before sending and returns the raw code`() {
        every { emailSender.send(message) } returns EmailResult(EmailSendStatus.SUCCESS, messageId = "ok")

        val code = service.convertToInvite(entryId)

        assertEquals(RAW_CODE, code)
        verifyOrder {
            inviteService.create(null)
            emailSender.send(any())
        }
        assertTrue(warnEvents().isEmpty(), "a successful send logs no WARN")
    }

    @Test
    fun `a sender exception is chained as the cause of the delivery exception`() {
        val failure = IllegalStateException("smtp down")
        every { emailSender.send(any()) } throws failure

        val thrown = assertThrows<WaitlistInviteDeliveryException> { service.convertToInvite(entryId) }

        assertTrue(thrown.cause is IllegalStateException, "cause must be the sender's own exception, was ${thrown.cause}")
        assertEquals("smtp down", thrown.cause?.message)
        assertEquals(1, warnEvents().size, "exactly one WARN per failure")
        assertFalse(warnEvents().single().formattedMessage.contains("smtp down"), "the exception message is not logged")
    }

    @Test
    fun `a hung sender is cut off by the timeout with a TimeoutException cause`() {
        every { emailSender.send(any()) } answers {
            Thread.sleep(5_000)
            EmailResult(EmailSendStatus.SUCCESS)
        }

        val startedAt = System.nanoTime()
        val thrown = assertThrows<WaitlistInviteDeliveryException> { service.convertToInvite(entryId) }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue(thrown.cause is TimeoutException, "cause must be a TimeoutException, was ${thrown.cause}")
        assertTrue(elapsedMs < 2_000, "the send must be cut off near the ${SEND_TIMEOUT_MS} ms timeout, took $elapsedMs ms")
        assertEquals(1, warnEvents().size, "exactly one WARN per failure")
    }

    @Test
    fun `a non-SUCCESS result logs one address-free WARN and throws`() {
        every { emailSender.send(any()) } returns
            EmailResult(EmailSendStatus.ERROR, errorDetail = "rejected for victim@example.com")

        assertThrows<WaitlistInviteDeliveryException> { service.convertToInvite(entryId) }

        assertEquals(1, warnEvents().size, "exactly one WARN per failure")
        appender.list.forEach { event ->
            assertFalse(event.formattedMessage.contains(RECIPIENT), "no log line may contain the recipient: ${event.formattedMessage}")
            assertFalse(event.formattedMessage.contains("victim@example.com"), "no log line may contain errorDetail")
            assertFalse(event.formattedMessage.contains(RAW_CODE), "no log line may contain the raw code")
        }
    }

    @Test
    fun `a renderer failure propagates unchanged and nothing is sent`() {
        every { renderer.render(RECIPIENT, RAW_CODE) } throws IllegalArgumentException("bad template")

        val thrown = assertThrows<IllegalArgumentException> { service.convertToInvite(entryId) }

        assertEquals("bad template", thrown.message)
        verify(exactly = 0) { emailSender.send(any()) }
    }
}
