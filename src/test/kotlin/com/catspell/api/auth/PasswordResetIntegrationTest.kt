package com.catspell.api.auth

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.PasswordResetTokenRepository
import com.catspell.api.auth.model.RefreshTokenRepository
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.email.service.EmailMessage
import com.catspell.api.email.service.EmailResult
import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.EmailSender
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@Import(PasswordResetIntegrationTest.MockEmailConfig::class)
class PasswordResetIntegrationTest : BaseIntegrationTest() {

    @TestConfiguration
    class MockEmailConfig {
        @Bean
        @Primary
        fun emailSender(): EmailSender = mockk(relaxed = true)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var emailSender: EmailSender

    private val sentMessages = mutableListOf<EmailMessage>()

    @BeforeEach
    fun setupEmailCapture() {
        clearMocks(emailSender)
        sentMessages.clear()
        every { emailSender.send(capture(sentMessages)) } returns
            EmailResult(EmailSendStatus.SUCCESS, messageId = "test")
    }

    private fun register(email: String, password: String = "password123"): Pair<String, String> {
        val body = mapOf("email" to email, "password" to password)
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return Pair(json["accessToken"].asText(), json["refreshToken"].asText())
    }

    private fun forgotPassword(email: String) = mockMvc.perform(
        post("/api/auth/forgot-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email"}""")
    )

    private fun resetPassword(token: String, newPassword: String) = mockMvc.perform(
        post("/api/auth/reset-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("token" to token, "newPassword" to newPassword)))
    )

    private fun login(email: String, password: String) = mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password)))
    )

    private fun refresh(refreshToken: String) = mockMvc.perform(
        post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken)))
    )

    /** Extract the raw reset token from the most recently captured reset email. */
    private fun capturedResetToken(): String {
        val message = sentMessages.last()
        val match = Regex("token=([A-Za-z0-9_-]+)").find(message.textBody)
            ?: error("No reset token found in captured email body")
        return match.groupValues[1]
    }

    @Test
    fun `RECOV-01 - forgot-password for registered user returns 202 with generic body`() {
        register("recov01@example.com")
        sentMessages.clear()

        forgotPassword("recov01@example.com")
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.message").isNotEmpty)
    }

    @Test
    fun `RECOV-04 - forgot-password identical response for registered vs unregistered`() {
        register("recov04-known@example.com")

        val known = forgotPassword("recov04-known@example.com").andReturn().response
        val unknown = forgotPassword("recov04-unknown@example.com").andReturn().response

        assertEquals(known.status, unknown.status, "status must be identical")
        assertEquals(202, known.status)
        assertEquals(
            known.contentAsString,
            unknown.contentAsString,
            "body bytes must be identical regardless of email existence"
        )
    }

    @Test
    fun `RECOV-05 - stored reset token is hashed not the raw emailed token`() {
        val user = userRepository.findByEmail("recov05-hash@example.com")
        assertNull(user)
        register("recov05-hash@example.com")
        sentMessages.clear()

        forgotPassword("recov05-hash@example.com").andExpect(status().isAccepted)
        val rawToken = capturedResetToken()

        val registered = userRepository.findByEmail("recov05-hash@example.com")!!
        val rows = passwordResetTokenRepository.findAllByUserAndUsedAtIsNull(registered)
        assertEquals(1, rows.size)
        assertNotEquals(rawToken, rows.first().tokenHash, "stored value must be a hash, not the raw token")
    }

    @Test
    fun `RECOV-03 - reset-password with valid token returns 200 no tokens and allows login`() {
        register("recov03@example.com", "oldpassword1")
        sentMessages.clear()
        forgotPassword("recov03@example.com").andExpect(status().isAccepted)
        val rawToken = capturedResetToken()

        val resetResult = resetPassword(rawToken, "newpassword1")
            .andExpect(status().isOk)
            .andReturn()

        val body = resetResult.response.contentAsString
        assertTrue(body.isBlank(), "reset-password 200 body must be empty (no tokens)")
        assertFalse(body.contains("accessToken"), "reset response must not carry an access token")
        assertFalse(body.contains("refreshToken"), "reset response must not carry a refresh token")

        login("recov03@example.com", "newpassword1").andExpect(status().isOk)
        login("recov03@example.com", "oldpassword1").andExpect(status().isUnauthorized)
    }

    @Test
    fun `RECOV-05 - reused reset token is rejected 401`() {
        register("recov05-reuse@example.com")
        sentMessages.clear()
        forgotPassword("recov05-reuse@example.com").andExpect(status().isAccepted)
        val rawToken = capturedResetToken()

        resetPassword(rawToken, "newpassword1").andExpect(status().isOk)
        // Second submission with the now-used token (single-use adjacency backstop).
        resetPassword(rawToken, "anotherpass1").andExpect(status().isUnauthorized)
    }

    @Test
    fun `RECOV-05 - expired reset token is rejected 401`() {
        register("recov05-expired@example.com")
        sentMessages.clear()
        forgotPassword("recov05-expired@example.com").andExpect(status().isAccepted)
        val rawToken = capturedResetToken()

        val registered = userRepository.findByEmail("recov05-expired@example.com")!!
        val storedToken = passwordResetTokenRepository.findAllByUserAndUsedAtIsNull(registered).first()
        storedToken.expiresAt = Instant.now().minusSeconds(3600)
        passwordResetTokenRepository.save(storedToken)

        resetPassword(rawToken, "newpassword1").andExpect(status().isUnauthorized)
    }

    @Test
    fun `RECOV-06 - refresh token issued before reset is rejected after reset`() {
        val (_, refreshToken) = register("recov06@example.com")
        sentMessages.clear()
        forgotPassword("recov06@example.com").andExpect(status().isAccepted)
        val rawToken = capturedResetToken()

        resetPassword(rawToken, "newpassword1").andExpect(status().isOk)

        refresh(refreshToken).andExpect(status().isUnauthorized)
    }

    @Test
    fun `reset-password with blank newPassword is rejected 400`() {
        resetPassword("some-token", "")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `reset-password with unknown token is rejected 401`() {
        resetPassword("totally-unknown-token", "newpassword1")
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `concurrent forgot-password requests each return 202`() {
        repeat(5) { register("recov01-concurrent-$it@example.com") }

        val executor = Executors.newFixedThreadPool(5)
        val statuses = java.util.Collections.synchronizedList(mutableListOf<Int>())
        try {
            val tasks = (0 until 5).map { i ->
                java.util.concurrent.Callable {
                    val status = mockMvc.perform(
                        post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"email":"recov01-concurrent-$i@example.com"}""")
                    ).andReturn().response.status
                    statuses.add(status)
                }
            }
            executor.invokeAll(tasks)
        } finally {
            executor.shutdown()
            executor.awaitTermination(30, TimeUnit.SECONDS)
        }

        assertEquals(5, statuses.size)
        assertTrue(statuses.all { it == 202 }, "all concurrent forgot-password requests must return 202, got $statuses")
    }
}
