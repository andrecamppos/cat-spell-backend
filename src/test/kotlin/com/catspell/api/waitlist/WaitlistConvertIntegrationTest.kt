package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.common.exception.WaitlistEntryNotConvertibleException
import com.catspell.api.common.exception.WaitlistInviteDeliveryException
import com.catspell.api.email.service.EmailMessage
import com.catspell.api.email.service.EmailResult
import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.EmailSender
import com.catspell.api.waitlist.service.WaitlistService
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CONVERT_ADMIN_SECRET = "test-admin-secret"
private const val CONVERT_URL = "/api/admin/waitlist/{id}/invite"
private const val CONVERT_TOKEN_HEADER = "X-Admin-Token"
private const val INVITE_URL = "catspell://register"

/** Seeds one waitlist row supplying every NOT NULL column (the create-drop test schema has no DB defaults). */
private fun seedEntry(jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate, email: String, status: String): UUID {
    val id = UUID.randomUUID()
    val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    jdbcTemplate.update(
        """
        INSERT INTO waitlist_entries (id, email, normalized_email, status, confirm_token_hash, confirm_token_expires_at,
                                      confirmed_at, invited_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        id,
        email,
        email.lowercase(),
        status,
        "hash-$id".take(64),
        Timestamp.from(now.plus(7, ChronoUnit.DAYS)),
        if (status == "PENDING") null else Timestamp.from(now),
        if (status == "INVITED") Timestamp.from(now) else null,
        Timestamp.from(now),
        Timestamp.from(now)
    )
    return id
}

/**
 * Proves WAIT-04 conversion (D-10): a CONFIRMED entry becomes exactly one organic invite that is emailed to the
 * entry's address; non-CONFIRMED, unknown and concurrent duplicate conversions are rejected without side effects,
 * and a failed delivery rolls the whole conversion back so the entry stays retryable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(WaitlistConvertIntegrationTest.MockEmailConfig::class)
@TestPropertySource(properties = ["app.invite.admin-token=$CONVERT_ADMIN_SECRET"])
class WaitlistConvertIntegrationTest : BaseIntegrationTest() {

    @TestConfiguration
    class MockEmailConfig {
        @Bean
        @Primary
        fun emailSender(): EmailSender = mockk(relaxed = true)
    }

    @Autowired lateinit var waitlistService: WaitlistService
    @Autowired lateinit var emailSender: EmailSender
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private val sentMessages = CopyOnWriteArrayList<EmailMessage>()

    @BeforeEach
    fun setupEmailCapture() {
        clearMocks(emailSender)
        sentMessages.clear()
        every { emailSender.send(capture(sentMessages)) } returns EmailResult(EmailSendStatus.SUCCESS, messageId = "test")
    }

    private fun inviteCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invites", Int::class.java)!!

    private fun referralCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM referrals", Int::class.java)!!

    private fun entryStatus(id: UUID): String =
        jdbcTemplate.queryForObject("SELECT status FROM waitlist_entries WHERE id = ?", String::class.java, id)!!

    private fun entryInvitedAt(id: UUID): Timestamp? =
        jdbcTemplate.queryForObject("SELECT invited_at FROM waitlist_entries WHERE id = ?", Timestamp::class.java, id)

    private fun sha256Hex(s: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))

    private fun assertRolledBack(id: UUID) {
        assertEquals(0, inviteCount(), "a failed conversion must leave no orphan invite")
        assertEquals("CONFIRMED", entryStatus(id), "a failed conversion must leave the entry CONFIRMED")
        assertNull(entryInvitedAt(id), "a failed conversion must leave invited_at NULL")
    }

    @Test
    fun `converting a confirmed entry issues one organic invite and emails the code`() {
        val id = seedEntry(jdbcTemplate, "Confirmed.User@example.com", "CONFIRMED")

        val code = waitlistService.convertToInvite(id)

        assertTrue(code.isNotBlank())
        assertEquals(1, inviteCount())
        assertEquals(sha256Hex(code), jdbcTemplate.queryForObject("SELECT code_hash FROM invites", String::class.java))
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invites WHERE referrer_user_id IS NULL", Int::class.java))
        assertEquals(0, referralCount(), "a waitlist conversion must not fabricate referral attribution (D-10)")
        assertEquals("INVITED", entryStatus(id))
        assertNotNull(entryInvitedAt(id))

        assertEquals(1, sentMessages.size)
        val message = sentMessages.single()
        assertEquals("Confirmed.User@example.com", message.to)
        assertTrue(message.textBody.contains(code), "text body must show the raw code")
        assertTrue(message.textBody.contains("$INVITE_URL?code=$code"), "text body must carry the invite link")
        assertTrue(message.htmlBody.contains("$INVITE_URL?code=$code"), "html body must carry the invite link")
    }

    @Test
    fun `converting a pending entry is rejected with no invite and no email`() {
        val id = seedEntry(jdbcTemplate, "pending@example.com", "PENDING")

        assertThrows<WaitlistEntryNotConvertibleException> { waitlistService.convertToInvite(id) }

        assertEquals(0, inviteCount())
        assertEquals("PENDING", entryStatus(id))
        verify(exactly = 0) { emailSender.send(any()) }
    }

    @Test
    fun `converting an already invited entry is rejected with no invite and no email`() {
        val id = seedEntry(jdbcTemplate, "invited@example.com", "INVITED")

        assertThrows<WaitlistEntryNotConvertibleException> { waitlistService.convertToInvite(id) }

        assertEquals(0, inviteCount())
        verify(exactly = 0) { emailSender.send(any()) }
    }

    @Test
    fun `converting an unknown id throws not found`() {
        assertThrows<ResourceNotFoundException> { waitlistService.convertToInvite(UUID.randomUUID()) }

        assertEquals(0, inviteCount())
        verify(exactly = 0) { emailSender.send(any()) }
    }

    @Test
    fun `sender error rolls the conversion back and a later retry succeeds`() {
        val id = seedEntry(jdbcTemplate, "retry@example.com", "CONFIRMED")
        every { emailSender.send(any()) } returns EmailResult(EmailSendStatus.ERROR, errorDetail = "provider down")

        assertThrows<WaitlistInviteDeliveryException> { waitlistService.convertToInvite(id) }
        assertRolledBack(id)

        every { emailSender.send(capture(sentMessages)) } returns EmailResult(EmailSendStatus.SUCCESS, messageId = "ok")
        val code = waitlistService.convertToInvite(id)

        assertEquals(1, inviteCount())
        assertEquals(sha256Hex(code), jdbcTemplate.queryForObject("SELECT code_hash FROM invites", String::class.java))
        assertEquals("INVITED", entryStatus(id))
    }

    @Test
    fun `sender exception rolls the conversion back`() {
        val id = seedEntry(jdbcTemplate, "boom@example.com", "CONFIRMED")
        every { emailSender.send(any()) } throws RuntimeException("smtp exploded")

        assertThrows<WaitlistInviteDeliveryException> { waitlistService.convertToInvite(id) }

        assertRolledBack(id)
    }

    @Test
    fun `concurrent converts of one confirmed entry produce exactly one invite`() {
        val id = seedEntry(jdbcTemplate, "race@example.com", "CONFIRMED")
        val threads = 4
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val outcomes = CopyOnWriteArrayList<Result<String>>()
        try {
            val futures = (1..threads).map {
                pool.submit {
                    start.await()
                    outcomes.add(runCatching { waitlistService.convertToInvite(id) })
                }
            }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(threads, outcomes.size)
        assertEquals(1, outcomes.count { it.isSuccess }, "exactly one concurrent convert may win")
        val failures = outcomes.mapNotNull { it.exceptionOrNull() }
        assertEquals(threads - 1, failures.size)
        assertTrue(failures.all { it is WaitlistEntryNotConvertibleException }, "losers must see not-convertible, got $failures")
        assertEquals(1, inviteCount())
        assertEquals(1, sentMessages.size)
    }

    // ---- HTTP: POST /api/admin/waitlist/{id}/invite (D-09) ----

    private fun convertRequest(id: UUID, token: String?) =
        mockMvc.perform(post(CONVERT_URL, id).apply { if (token != null) header(CONVERT_TOKEN_HEADER, token) })

    private fun assertNoSideEffects(id: UUID?, expectedStatus: String?) {
        if (id != null && expectedStatus != null) assertEquals(expectedStatus, entryStatus(id))
        assertEquals(0, inviteCount())
        verify(exactly = 0) { emailSender.send(any()) }
    }

    @Test
    fun `http convert of a confirmed entry returns 201 with the entry id and a code stored hashed`() {
        val id = seedEntry(jdbcTemplate, "http-ok@example.com", "CONFIRMED")

        val result = convertRequest(id, CONVERT_ADMIN_SECRET)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.entryId").value(id.toString()))
            .andExpect(jsonPath("$.code").isNotEmpty)
            .andReturn()

        val code = objectMapper.readTree(result.response.contentAsString)["code"].asText()
        assertEquals(sha256Hex(code), jdbcTemplate.queryForObject("SELECT code_hash FROM invites", String::class.java))
        assertEquals("INVITED", entryStatus(id))
    }

    @Test
    fun `http convert of a pending entry returns 409 not convertible`() {
        val id = seedEntry(jdbcTemplate, "http-pending@example.com", "PENDING")

        convertRequest(id, CONVERT_ADMIN_SECRET)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.code").value("WAITLIST_ENTRY_NOT_CONVERTIBLE"))

        assertNoSideEffects(id, "PENDING")
    }

    @Test
    fun `http second convert of the same entry returns 409 with the same body`() {
        val id = seedEntry(jdbcTemplate, "http-twice@example.com", "CONFIRMED")
        convertRequest(id, CONVERT_ADMIN_SECRET).andExpect(status().isCreated)

        convertRequest(id, CONVERT_ADMIN_SECRET)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.code").value("WAITLIST_ENTRY_NOT_CONVERTIBLE"))

        assertEquals(1, inviteCount(), "a re-convert must not issue a second invite (no idempotent re-send)")
        assertEquals(1, sentMessages.size)
    }

    @Test
    fun `http convert of an unknown id returns 404`() {
        convertRequest(UUID.randomUUID(), CONVERT_ADMIN_SECRET)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("Not Found"))

        assertNoSideEffects(null, null)
    }

    @Test
    fun `http convert with a failing sender returns 502 and leaves the entry confirmed`() {
        val id = seedEntry(jdbcTemplate, "http-502@example.com", "CONFIRMED")
        every { emailSender.send(any()) } returns EmailResult(EmailSendStatus.ERROR, errorDetail = "provider down")

        convertRequest(id, CONVERT_ADMIN_SECRET)
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.title").value("Bad Gateway"))
            .andExpect(jsonPath("$.code").value("WAITLIST_INVITE_DELIVERY_FAILED"))

        assertRolledBack(id)
    }

    @Test
    fun `http convert with a missing token returns 401 for an existing id with no side effects`() {
        val id = seedEntry(jdbcTemplate, "http-notoken@example.com", "CONFIRMED")

        convertRequest(id, null)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))

        assertNoSideEffects(id, "CONFIRMED")
    }

    @Test
    fun `http convert with a wrong token returns 401 for an existing id with no side effects`() {
        val id = seedEntry(jdbcTemplate, "http-wrongtoken@example.com", "CONFIRMED")

        convertRequest(id, "wrong-secret")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))

        assertNoSideEffects(id, "CONFIRMED")
    }

    @Test
    fun `http convert with a missing token returns 401 for a random id too`() {
        convertRequest(UUID.randomUUID(), null)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))

        assertNoSideEffects(null, null)
    }

    @Test
    fun `http convert with a wrong token returns 401 for a random id too`() {
        convertRequest(UUID.randomUUID(), "wrong-secret")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))

        assertNoSideEffects(null, null)
    }
}

/**
 * Proves deny-by-default for the convert route: with a blank app.invite.admin-token every request is 401, even one
 * carrying a plausible token, and nothing is converted, issued or emailed. The property is pinned explicitly because
 * the test application.yml reads INVITE_ADMIN_TOKEN from the environment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(WaitlistConvertIntegrationTest.MockEmailConfig::class)
@TestPropertySource(properties = ["app.invite.admin-token="])
class WaitlistConvertDenyByDefaultIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var emailSender: EmailSender

    @BeforeEach
    fun resetSender() {
        clearMocks(emailSender)
        every { emailSender.send(any()) } returns EmailResult(EmailSendStatus.SUCCESS, messageId = "test")
    }

    @Test
    fun `convert is 401 when admin-token is blank even with a plausible header`() {
        val id = seedEntry(jdbcTemplate, "deny@example.com", "CONFIRMED")

        mockMvc.perform(post(CONVERT_URL, id).header(CONVERT_TOKEN_HEADER, CONVERT_ADMIN_SECRET))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))

        assertEquals("CONFIRMED", jdbcTemplate.queryForObject("SELECT status FROM waitlist_entries WHERE id = ?", String::class.java, id))
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invites", Int::class.java))
        verify(exactly = 0) { emailSender.send(any()) }
    }

    @Test
    fun `convert is 401 when admin-token is blank and no header is sent`() {
        mockMvc.perform(post(CONVERT_URL, UUID.randomUUID()))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invites", Int::class.java))
    }
}
