package com.catspell.api.chat

import com.catspell.api.chat.model.Conversation
import com.catspell.api.chat.model.ConversationParticipant
import com.catspell.api.chat.model.ConversationParticipantRepository
import com.catspell.api.chat.model.ConversationRepository
import com.catspell.api.chat.model.Message
import com.catspell.api.chat.model.MessageRepository
import com.catspell.api.chat.service.ChatService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.UUID

/**
 * Pins WR-02 / D-04: deliverUnreadMessages resolves hidden conversations BEFORE reading
 * undelivered rows, suppresses hidden-conversation messages, and pushes only visible ones.
 */
class ChatServiceDeliverUnreadTest {

    private val conversationRepository = mockk<ConversationRepository>()
    private val participantRepository = mockk<ConversationParticipantRepository>()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)

    private val service = ChatService(
        conversationRepository, participantRepository, messageRepository,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), messagingTemplate, mockk(relaxed = true), mockk(relaxed = true)
    )

    private val userId = UUID.randomUUID()
    private val hiddenConvId = UUID.randomUUID()
    private val visibleConvId = UUID.randomUUID()

    private fun participation(convId: UUID): ConversationParticipant {
        val conv = mockk<Conversation> { every { id } returns convId }
        return mockk { every { conversation } returns conv }
    }

    private fun message(convId: UUID): Message {
        val conv = mockk<Conversation> { every { id } returns convId }
        return mockk(relaxed = true) {
            every { conversation } returns conv
            every { id } returns UUID.randomUUID()
            every { sender } returns mockk { every { id } returns UUID.randomUUID() }
            every { content } returns "hello"
        }
    }

    @Test
    fun `hidden ids are resolved before undelivered messages are read`() {
        val hiddenMsg = message(hiddenConvId)
        val visibleMsg = message(visibleConvId)
        every { participantRepository.findByUserId(userId) } returns
            listOf(participation(hiddenConvId), participation(visibleConvId))
        every { conversationRepository.findHiddenConversationIdsForUser(userId) } returns listOf(hiddenConvId)
        every {
            messageRepository.findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc(any(), userId)
        } returns listOf(hiddenMsg, visibleMsg)
        every { messageRepository.save(any<Message>()) } answers { firstArg() }

        val count = service.deliverUnreadMessages(userId)

        verifyOrder {
            conversationRepository.findHiddenConversationIdsForUser(userId)
            messageRepository.findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc(any(), userId)
        }
        assertEquals(1, count)
        verify { hiddenMsg.delivered = true }
        verify { visibleMsg.delivered = true }
        verify(exactly = 1) { messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", any<Any>()) }
        verify {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications",
                match<Any> { (it as com.catspell.api.chat.model.ChatNotification).conversationId == visibleConvId })
        }
    }

    @Test
    fun `no participations returns zero without reading hidden or undelivered`() {
        every { participantRepository.findByUserId(userId) } returns emptyList()

        assertEquals(0, service.deliverUnreadMessages(userId))

        verify(exactly = 0) { conversationRepository.findHiddenConversationIdsForUser(any()) }
        verify(exactly = 0) {
            messageRepository.findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc(any(), any())
        }
        verify(exactly = 0) { messagingTemplate.convertAndSendToUser(any(), any(), any<Any>()) }
    }
}
