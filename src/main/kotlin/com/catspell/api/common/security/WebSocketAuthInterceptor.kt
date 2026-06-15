package com.catspell.api.common.security

import com.catspell.api.chat.model.ConversationParticipantRepository
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WebSocketAuthInterceptor(
    private val jwtService: JwtService,
    private val conversationParticipantRepository: ConversationParticipantRepository
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> handleConnect(accessor)
            StompCommand.SUBSCRIBE -> handleSubscribe(accessor)
            else -> {}
        }

        return message
    }

    private fun handleConnect(accessor: StompHeaderAccessor) {
        val authHeader = accessor.getFirstNativeHeader("Authorization")
            ?: throw MessagingException("Authentication failed")

        if (!authHeader.startsWith("Bearer ")) {
            throw MessagingException("Authentication failed")
        }

        val token = authHeader.substring(7)
        try {
            val userId = jwtService.extractUserId(token)
            accessor.user = UsernamePasswordAuthenticationToken(
                userId.toString(), null, emptyList()
            )
        } catch (e: Exception) {
            throw MessagingException("Authentication failed")
        }
    }

    private fun handleSubscribe(accessor: StompHeaderAccessor) {
        val destination = accessor.destination ?: return
        val chatPrefix = "/topic/chat/"

        if (destination.startsWith(chatPrefix)) {
            val conversationIdStr = destination.substring(chatPrefix.length)
            val conversationId = try {
                UUID.fromString(conversationIdStr)
            } catch (e: IllegalArgumentException) {
                throw MessagingException("Invalid conversation ID")
            }

            val userId = (accessor.user as? UsernamePasswordAuthenticationToken)?.name
                ?: throw MessagingException("Not authenticated")

            val isParticipant = conversationParticipantRepository.existsByConversationIdAndUserId(
                conversationId, UUID.fromString(userId)
            )

            if (!isParticipant) {
                throw MessagingException("Not a participant")
            }
        }
    }
}
