package com.catspell.api.push

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.push.model.DeviceToken
import com.catspell.api.push.model.DeviceTokenRepository
import com.catspell.api.push.model.Platform
import com.catspell.api.push.service.PushPayload
import com.catspell.api.push.service.PushProvider
import com.catspell.api.push.service.PushResult
import com.catspell.api.push.service.PushSendService
import com.catspell.api.push.service.PushSendStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.util.UUID

@SpringBootTest
@Import(TokenPruningTest.MockPushConfig::class)
class TokenPruningTest : BaseIntegrationTest() {

    @TestConfiguration
    class MockPushConfig {
        @Bean
        @Primary
        fun pushProvider(): PushProvider = mockk()
    }

    @Autowired
    lateinit var pushProvider: PushProvider

    @Autowired
    lateinit var pushSendService: PushSendService

    @Autowired
    lateinit var deviceTokenRepository: DeviceTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    private fun persistActiveToken(fcmToken: String): UUID {
        val user = userRepository.save(
            User(email = "prune-${UUID.randomUUID()}@example.com", passwordHash = "x")
        )
        val saved = deviceTokenRepository.save(
            DeviceToken(
                userId = user.id!!,
                deviceId = "device-${UUID.randomUUID()}",
                token = fcmToken,
                platform = Platform.ANDROID,
                active = true
            )
        )
        return saved.id!!
    }

    @Test
    fun `UNREGISTERED result prunes the token`() {
        val id = persistActiveToken("fcm-unregistered")
        every { pushProvider.send(any(), any()) } returns PushResult(PushSendStatus.UNREGISTERED)

        pushSendService.send("fcm-unregistered", PushPayload("t", "b"))

        val row = deviceTokenRepository.findById(id).get()
        assertFalse(row.active)
        assertNotNull(row.deactivatedAt)
    }

    @Test
    fun `ERROR result does not prune the token`() {
        val id = persistActiveToken("fcm-error")
        every { pushProvider.send(any(), any()) } returns PushResult(PushSendStatus.ERROR)

        pushSendService.send("fcm-error", PushPayload("t", "b"))

        val row = deviceTokenRepository.findById(id).get()
        assertTrue(row.active)
    }

    @Test
    fun `SUCCESS result does not prune the token`() {
        val id = persistActiveToken("fcm-success")
        every { pushProvider.send(any(), any()) } returns PushResult(PushSendStatus.SUCCESS, messageId = "m1")

        pushSendService.send("fcm-success", PushPayload("t", "b"))

        val row = deviceTokenRepository.findById(id).get()
        assertTrue(row.active)
    }
}
