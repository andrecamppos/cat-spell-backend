package com.catspell.api.auth

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.EmailVerificationTokenRepository
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(EmailVerificationIntegrationTest.MockEmailConfig::class)
class EmailVerificationIntegrationTest : BaseIntegrationTest() {

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
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

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

    private fun register(email: String, password: String = "password123") = mockMvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password)))
    )

    private fun login(email: String, password: String) = mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password)))
    )

    private fun verifyEmail(token: String) = mockMvc.perform(
        post("/api/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("token" to token)))
    )

    private fun resendVerification(email: String) = mockMvc.perform(
        post("/api/auth/resend-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email"}""")
    )

    /** Extract the raw verification token from the most recently captured email. */
    private fun capturedVerifyToken(): String {
        val message = sentMessages.last()
        val match = Regex("token=([A-Za-z0-9_-]+)").find(message.textBody)
            ?: error("No verification token found in captured email body")
        return match.groupValues[1]
    }

    @Test
    fun `VERIFY-01 - register creates unverified user, sends one email, returns 201 with no tokens`() {
        val result = register("verify01@example.com")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.message").isNotEmpty)
            .andReturn()

        val body = result.response.contentAsString
        assertFalse(body.contains("accessToken"), "register 201 must not carry an access token")
        assertFalse(body.contains("refreshToken"), "register 201 must not carry a refresh token")

        assertEquals(1, sentMessages.size, "exactly one verification email must be sent on register")

        val user = userRepository.findByEmail("verify01@example.com")!!
        assertNull(user.emailVerifiedAt, "a freshly registered user must be unverified")

        val rawToken = capturedVerifyToken()
        val rows = emailVerificationTokenRepository.findAllByUserAndUsedAtIsNull(user)
        assertEquals(1, rows.size)
        assertNotEquals(rawToken, rows.first().tokenHash, "stored value must be a hash, not the raw token")
    }

    @Test
    fun `VERIFY-03 - login before verification is 403 EMAIL_NOT_VERIFIED, unknown or wrong password is 401`() {
        register("verify03@example.com", "password123").andExpect(status().isCreated)

        login("verify03@example.com", "password123")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))

        login("verify03-unknown@example.com", "password123").andExpect(status().isUnauthorized)
        login("verify03@example.com", "wrongpassword").andExpect(status().isUnauthorized)
    }

    @Test
    fun `VERIFY-02 - verify-email with valid token returns 200 no tokens and allows login`() {
        register("verify02@example.com", "password123").andExpect(status().isCreated)
        val rawToken = capturedVerifyToken()

        val verifyResult = verifyEmail(rawToken)
            .andExpect(status().isOk)
            .andReturn()

        val body = verifyResult.response.contentAsString
        assertTrue(body.isBlank(), "verify-email 200 body must be empty (no tokens)")
        assertFalse(body.contains("accessToken"), "verify response must not carry an access token")
        assertFalse(body.contains("refreshToken"), "verify response must not carry a refresh token")

        val user = userRepository.findByEmail("verify02@example.com")!!
        assertNotNull(user.emailVerifiedAt, "email_verified_at must be set after verification")

        login("verify02@example.com", "password123").andExpect(status().isOk)
    }

    @Test
    fun `VERIFY-02 - reused token is 401 and force-expired token is 401`() {
        register("verify02-reuse@example.com").andExpect(status().isCreated)
        val rawToken = capturedVerifyToken()

        verifyEmail(rawToken).andExpect(status().isOk)
        // Second submission with the now-used token (single-use).
        verifyEmail(rawToken).andExpect(status().isUnauthorized)

        // Fresh registration whose token is force-expired via the repository.
        register("verify02-expired@example.com").andExpect(status().isCreated)
        val expiredRaw = capturedVerifyToken()
        val expiredUser = userRepository.findByEmail("verify02-expired@example.com")!!
        val storedToken = emailVerificationTokenRepository.findAllByUserAndUsedAtIsNull(expiredUser).first()
        storedToken.expiresAt = Instant.now().minusSeconds(3600)
        emailVerificationTokenRepository.save(storedToken)

        verifyEmail(expiredRaw).andExpect(status().isUnauthorized)
    }

    @Test
    fun `VERIFY-02 - blank token is rejected and unknown token is 401`() {
        verifyEmail("").andExpect(status().is4xxClientError)
        verifyEmail("totally-unknown-token").andExpect(status().isUnauthorized)
    }

    @Test
    fun `VERIFY-04 - resend returns identical 202 for unverified, unknown, and already-verified emails`() {
        register("verify04-unverified@example.com").andExpect(status().isCreated)

        val unverified = resendVerification("verify04-unverified@example.com")
            .andExpect(status().isAccepted)
            .andReturn().response

        val unknown = resendVerification("verify04-unknown@example.com")
            .andExpect(status().isAccepted)
            .andReturn().response

        // Register and fully verify a third account.
        register("verify04-verified@example.com").andExpect(status().isCreated)
        val verifiedRaw = capturedVerifyToken()
        verifyEmail(verifiedRaw).andExpect(status().isOk)

        val alreadyVerified = resendVerification("verify04-verified@example.com")
            .andExpect(status().isAccepted)
            .andReturn().response

        assertEquals(unverified.contentAsString, unknown.contentAsString,
            "resend body must be identical for unverified vs unknown")
        assertEquals(unverified.contentAsString, alreadyVerified.contentAsString,
            "resend body must be identical for unverified vs already-verified")
    }

    @Test
    fun `VERIFY-04 - resend issues a fresh token and invalidates the prior one`() {
        register("verify04-resend@example.com").andExpect(status().isCreated)
        val firstToken = capturedVerifyToken()

        resendVerification("verify04-resend@example.com").andExpect(status().isAccepted)
        val secondToken = capturedVerifyToken()
        assertNotEquals(firstToken, secondToken, "resend must issue a new raw token")

        // The prior token was invalidated, so verifying with it fails.
        verifyEmail(firstToken).andExpect(status().isUnauthorized)
        // The newest token verifies successfully.
        verifyEmail(secondToken).andExpect(status().isOk)
    }
}
