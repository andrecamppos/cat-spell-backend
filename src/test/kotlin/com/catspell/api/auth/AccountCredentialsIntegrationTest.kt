package com.catspell.api.auth

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.EmailChangeRequestRepository
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
@Import(AccountCredentialsIntegrationTest.MockEmailConfig::class)
class AccountCredentialsIntegrationTest : BaseIntegrationTest() {

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
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var emailChangeRequestRepository: EmailChangeRequestRepository

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

    // --- helpers -----------------------------------------------------------

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

    /** Register, stamp verified (past the Phase 11 login gate), and log in. Returns [accessToken, refreshToken]. */
    private fun registerVerifyLogin(email: String, password: String = "password123"): Pair<String, String> {
        register(email, password).andExpect(status().isCreated)
        markEmailVerified(email)
        val result = login(email, password).andExpect(status().isOk).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return json["accessToken"].asText() to json["refreshToken"].asText()
    }

    private fun changePassword(token: String, currentPassword: String, newPassword: String) = mockMvc.perform(
        post("/api/auth/change-password")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("currentPassword" to currentPassword, "newPassword" to newPassword)))
    )

    private fun changeEmail(token: String, currentPassword: String, newEmail: String) = mockMvc.perform(
        post("/api/auth/change-email")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("currentPassword" to currentPassword, "newEmail" to newEmail)))
    )

    private fun confirmEmailChange(token: String) = mockMvc.perform(
        post("/api/auth/confirm-email-change")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("token" to token)))
    )

    private fun refresh(refreshToken: String) = mockMvc.perform(
        post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken)))
    )

    /** Extract the raw token from the confirm email captured for [recipient]. */
    private fun capturedConfirmToken(recipient: String): String {
        val message = sentMessages.last { it.to == recipient }
        val match = Regex("token=([A-Za-z0-9_-]+)").find(message.textBody)
            ?: error("No confirm token found in captured email body for $recipient")
        return match.groupValues[1]
    }

    // --- ACCT-01 / ACCT-02: change-password --------------------------------

    @Test
    fun `ACCT-01 - wrong current password is 403 INVALID_CURRENT_PASSWORD and leaves the password unchanged`() {
        val (token, _) = registerVerifyLogin("acct01-wrong@example.com")

        changePassword(token, currentPassword = "not-my-password", newPassword = "brandNewPass1")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"))

        // Password unchanged: the original password still logs in.
        login("acct01-wrong@example.com", "password123").andExpect(status().isOk)
        // The rejected new password does not work.
        login("acct01-wrong@example.com", "brandNewPass1").andExpect(status().isUnauthorized)
    }

    @Test
    fun `ACCT-02 - successful change-password returns no tokens, swaps the password, and revokes all sessions`() {
        val (token, preChangeRefresh) = registerVerifyLogin("acct02-ok@example.com")

        val result = changePassword(token, currentPassword = "password123", newPassword = "brandNewPass1")
            .andExpect(status().isOk)
            .andReturn()

        val body = result.response.contentAsString
        assertTrue(body.isBlank(), "change-password 200 body must be empty (no tokens)")
        assertFalse(body.contains("accessToken"), "change-password must not carry an access token")
        assertFalse(body.contains("refreshToken"), "change-password must not carry a refresh token")

        // New password logs in; the old one no longer does.
        login("acct02-ok@example.com", "brandNewPass1").andExpect(status().isOk)
        login("acct02-ok@example.com", "password123").andExpect(status().isUnauthorized)

        // A refresh token minted before the change is revoked (all sessions dropped).
        refresh(preChangeRefresh).andExpect(status().isUnauthorized)
    }

    @Test
    fun `ACCT-01 - newPassword shorter than 8 chars is rejected 400`() {
        val (token, _) = registerVerifyLogin("acct01-short@example.com")

        changePassword(token, currentPassword = "password123", newPassword = "short")
            .andExpect(status().isBadRequest)

        // No state change: the original password still logs in.
        login("acct01-short@example.com", "password123").andExpect(status().isOk)
    }
}
