package com.catspell.api.push

import com.catspell.api.push.model.DeviceToken
import com.catspell.api.push.model.DeviceTokenRepository
import com.catspell.api.push.model.Platform
import com.catspell.api.push.presence.PresenceRegistry
import com.catspell.api.push.service.PushNotificationService
import com.catspell.api.push.service.PushPayload
import com.catspell.api.push.service.PushResult
import com.catspell.api.push.service.PushSendService
import com.catspell.api.push.service.PushSendStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class PushNotificationServiceTest {

    private val presenceRegistry = mockk<PresenceRegistry>()
    private val deviceTokenRepository = mockk<DeviceTokenRepository>()
    private val pushSendService = mockk<PushSendService>(relaxed = true)

    private val service = PushNotificationService(presenceRegistry, deviceTokenRepository, pushSendService)

    private fun token(userId: UUID, value: String): DeviceToken =
        DeviceToken(userId = userId, deviceId = "dev-$value", token = value, platform = Platform.ANDROID)

    @Test
    fun `notifyMatch pushes only offline users and carries matchId`() {
        val online = UUID.randomUUID()
        val offline = UUID.randomUUID()
        val matchId = UUID.randomUUID()

        every { presenceRegistry.isOnline(online) } returns true
        every { presenceRegistry.isOnline(offline) } returns false
        every { deviceTokenRepository.findAllByUserIdAndActiveTrue(offline) } returns listOf(token(offline, "off-tok"))

        val payloadSlot = slot<PushPayload>()
        every { pushSendService.send(capture(slot()), capture(payloadSlot)) } returns PushResult(PushSendStatus.SUCCESS)

        service.notifyMatch(listOf(online, offline), matchId)

        verify(exactly = 0) { deviceTokenRepository.findAllByUserIdAndActiveTrue(online) }
        verify(exactly = 1) { pushSendService.send("off-tok", any()) }
        assertEquals(matchId.toString(), payloadSlot.captured.data["matchId"])
        assertEquals("match", payloadSlot.captured.data["type"])
    }

    @Test
    fun `notifyMatch with both offline pushes both users`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val matchId = UUID.randomUUID()

        every { presenceRegistry.isOnline(a) } returns false
        every { presenceRegistry.isOnline(b) } returns false
        every { deviceTokenRepository.findAllByUserIdAndActiveTrue(a) } returns listOf(token(a, "a-tok"))
        every { deviceTokenRepository.findAllByUserIdAndActiveTrue(b) } returns listOf(token(b, "b-tok"))

        service.notifyMatch(listOf(a, b), matchId)

        verify(exactly = 1) { pushSendService.send("a-tok", any()) }
        verify(exactly = 1) { pushSendService.send("b-tok", any()) }
    }

    @Test
    fun `notifyMatch with both online sends nothing`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()

        every { presenceRegistry.isOnline(a) } returns true
        every { presenceRegistry.isOnline(b) } returns true

        service.notifyMatch(listOf(a, b), UUID.randomUUID())

        verify(exactly = 0) { pushSendService.send(any(), any()) }
    }

    @Test
    fun `notifyMessage sends when recipient is offline`() {
        val recipient = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        every { presenceRegistry.isOnline(recipient) } returns false
        every { deviceTokenRepository.findAllByUserIdAndActiveTrue(recipient) } returns listOf(token(recipient, "r-tok"))

        service.notifyMessage(recipient, conversationId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "hi there")

        verify(exactly = 1) { pushSendService.send("r-tok", any()) }
    }

    @Test
    fun `notifyMessage sends when recipient is online but not viewing the conversation`() {
        val recipient = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        every { presenceRegistry.isOnline(recipient) } returns true
        every { presenceRegistry.isViewingConversation(recipient, conversationId) } returns false
        every { deviceTokenRepository.findAllByUserIdAndActiveTrue(recipient) } returns listOf(token(recipient, "r-tok"))

        service.notifyMessage(recipient, conversationId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "hi there")

        verify(exactly = 1) { pushSendService.send("r-tok", any()) }
    }

    @Test
    fun `notifyMessage suppresses when recipient is online and viewing the conversation`() {
        val recipient = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        every { presenceRegistry.isOnline(recipient) } returns true
        every { presenceRegistry.isViewingConversation(recipient, conversationId) } returns true

        service.notifyMessage(recipient, conversationId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "hi there")

        verify(exactly = 0) { pushSendService.send(any(), any()) }
    }

    @Test
    fun `notifyMessage payload carries preview title body deep-link data and collapseKey`() {
        val recipient = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        every { presenceRegistry.isOnline(recipient) } returns false
        every { deviceTokenRepository.findAllByUserIdAndActiveTrue(recipient) } returns listOf(token(recipient, "r-tok"))

        val payloadSlot = slot<PushPayload>()
        every { pushSendService.send(any(), capture(payloadSlot)) } returns PushResult(PushSendStatus.SUCCESS)

        service.notifyMessage(recipient, conversationId, messageId, senderId, "Alice", "hi there")

        val p = payloadSlot.captured
        assertEquals("Alice", p.title)
        assertEquals("hi there", p.body)
        assertEquals("message", p.data["type"])
        assertEquals(conversationId.toString(), p.data["conversationId"])
        assertEquals(messageId.toString(), p.data["messageId"])
        assertEquals(senderId.toString(), p.data["senderId"])
        assertEquals(conversationId.toString(), p.collapseKey)
    }

    @Test
    fun `fan-out sends once per active device token`() {
        val recipient = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        every { presenceRegistry.isOnline(recipient) } returns false
        every { deviceTokenRepository.findAllByUserIdAndActiveTrue(recipient) } returns
            listOf(token(recipient, "tok-1"), token(recipient, "tok-2"))

        service.notifyMessage(recipient, conversationId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "hi")

        verify(exactly = 1) { pushSendService.send("tok-1", any()) }
        verify(exactly = 1) { pushSendService.send("tok-2", any()) }
    }

    @Test
    fun `recipient with zero active tokens produces zero sends without error`() {
        val recipient = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        every { presenceRegistry.isOnline(recipient) } returns false
        every { deviceTokenRepository.findAllByUserIdAndActiveTrue(recipient) } returns emptyList()

        service.notifyMessage(recipient, conversationId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "hi")

        verify(exactly = 0) { pushSendService.send(any(), any()) }
    }
}
