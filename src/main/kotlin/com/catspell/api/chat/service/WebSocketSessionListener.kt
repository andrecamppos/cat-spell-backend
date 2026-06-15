package com.catspell.api.chat.service

import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import java.util.UUID

@Component
class WebSocketSessionListener(
    private val chatService: ChatService
) {

    @Async
    @EventListener
    fun handleSessionConnected(event: SessionConnectedEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val userId = accessor.user?.name ?: return

        try {
            val uuid = UUID.fromString(userId)
            Thread.sleep(200)
            chatService.deliverUnreadMessages(uuid)
        } catch (_: Exception) {
            // Silently ignore delivery errors on reconnect
        }
    }
}
