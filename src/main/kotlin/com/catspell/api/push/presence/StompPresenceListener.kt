package com.catspell.api.push.presence

import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent
import java.util.UUID

/**
 * Maps STOMP session lifecycle events onto [PresenceRegistry] mutations. Active-conversation is
 * inferred from the client's existing `/topic/chat/{conversationId}` subscription (D-03) — no new
 * client contract. Listeners are intentionally NOT `@Async`: registry mutations are cheap in-memory
 * operations that must be ordered relative to the connection lifecycle.
 */
@Component
class StompPresenceListener(
    private val presenceRegistry: PresenceRegistry
) {

    @EventListener
    fun handleSessionConnected(event: SessionConnectedEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val userId = accessor.user?.name?.let { parseUserId(it) } ?: return
        val sessionId = accessor.sessionId ?: return
        presenceRegistry.addSession(userId, sessionId)
    }

    @EventListener
    fun handleSessionSubscribe(event: SessionSubscribeEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = accessor.sessionId ?: return
        val subscriptionId = accessor.subscriptionId ?: return
        val destination = accessor.destination ?: return
        if (!destination.startsWith(CHAT_TOPIC_PREFIX)) return
        presenceRegistry.addSubscription(sessionId, subscriptionId, destination)
    }

    @EventListener
    fun handleSessionUnsubscribe(event: SessionUnsubscribeEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = accessor.sessionId ?: return
        val subscriptionId = accessor.subscriptionId ?: return
        presenceRegistry.removeSubscription(sessionId, subscriptionId)
    }

    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        presenceRegistry.removeSession(event.sessionId)
    }

    private fun parseUserId(raw: String): UUID? =
        try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        private const val CHAT_TOPIC_PREFIX = "/topic/chat/"
    }
}
