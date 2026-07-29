package com.catspell.api.push

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.chat.model.MessageRepository
import com.catspell.api.chat.model.SendMessageRequest
import com.catspell.api.chat.service.ChatService
import com.catspell.api.match.service.MatchService
import com.catspell.api.push.model.DeviceToken
import com.catspell.api.push.model.DeviceTokenRepository
import com.catspell.api.push.model.Platform
import com.catspell.api.push.service.PushNotificationService
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.util.UUID

/**
 * Proves the Phase 9 async trigger pipeline (PUSH-10, D-07): a persisted chat message dispatches a
 * push AFTER commit, on a separate thread, and a slow/failing push neither blocks nor rolls back
 * message persistence. PushNotificationService is replaced with a MockK @Primary bean so no real
 * FCM call occurs and dispatch is observable from backend state only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PushTriggerIntegrationTest.MockPushConfig::class)
class PushTriggerIntegrationTest : BaseIntegrationTest() {

    @TestConfiguration
    class MockPushConfig {
        @Bean
        @Primary
        fun mockPushNotificationService(): PushNotificationService = mockk(relaxed = true)
    }

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var messageRepository: MessageRepository
    @Autowired lateinit var matchService: MatchService
    @Autowired lateinit var chatService: ChatService
    @Autowired lateinit var pushNotificationService: PushNotificationService

    @BeforeEach
    fun resetMock() {
        clearMocks(pushNotificationService)
    }

    private fun createUser(email: String): UUID {
        val user = userRepository.save(User(email = email, passwordHash = "test-hash"))
        return user.id!!
    }

    private fun registerDevice(userId: UUID, token: String) {
        deviceTokenRepository.save(
            DeviceToken(userId = userId, deviceId = "dev-$token", token = token, platform = Platform.ANDROID)
        )
    }

    @Test
    fun `message send dispatches notifyMessage asynchronously after the transaction commits`() {
        val senderId = createUser("push-trigger-sender-a@example.com")
        val recipientId = createUser("push-trigger-recipient-b@example.com")
        registerDevice(recipientId, "recipient-token")
        val matchId = matchService.createMatch(senderId, recipientId)!!.id!!

        // Recipient has no STOMP session in the registry -> offline -> a push is expected.
        val response = chatService.sendMessage(senderId, SendMessageRequest(matchId = matchId, content = "hello there"))

        // Message row is committed synchronously on the request path.
        assertTrue(messageRepository.existsById(response.messageId))

        // notifyMessage is invoked asynchronously AFTER commit (never on the calling thread's return).
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(exactly = 1) {
                pushNotificationService.notifyMessage(
                    recipientId,
                    response.conversationId,
                    response.messageId,
                    senderId,
                    any(),
                    "hello there"
                )
            }
        }
    }

    @Test
    fun `a failing push neither blocks nor rolls back message persistence`() {
        val senderId = createUser("push-trigger-sender-c@example.com")
        val recipientId = createUser("push-trigger-recipient-d@example.com")
        registerDevice(recipientId, "recipient-token-2")
        val matchId = matchService.createMatch(senderId, recipientId)!!.id!!

        every {
            pushNotificationService.notifyMessage(any(), any(), any(), any(), any(), any())
        } throws RuntimeException("simulated FCM failure")

        // sendMessage must still return successfully despite the push failure on the async listener.
        val response = chatService.sendMessage(senderId, SendMessageRequest(matchId = matchId, content = "still delivered"))

        assertNotNull(response.messageId)
        assertEquals("still delivered", response.content)
        // The message row is committed regardless of push outcome.
        assertTrue(messageRepository.existsById(response.messageId))

        // The failing dispatch was still attempted (exception swallowed by the listener, not propagated).
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(atLeast = 1) {
                pushNotificationService.notifyMessage(recipientId, response.conversationId, any(), senderId, any(), any())
            }
        }
    }
}
