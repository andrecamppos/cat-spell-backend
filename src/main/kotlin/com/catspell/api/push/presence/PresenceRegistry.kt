package com.catspell.api.push.presence

import org.springframework.stereotype.Component
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, single-instance registry of STOMP presence and active-conversation state (D-06).
 *
 * Presence = a user has at least one live STOMP session. Active-conversation is inferred purely
 * from the client's existing `/topic/chat/{conversationId}` subscription (D-03), so no new client
 * contract is required. All state for a session is cleared on disconnect (PUSH-08).
 */
@Component
class PresenceRegistry {

    private val sessionsByUser = ConcurrentHashMap<UUID, MutableSet<String>>()
    private val userBySession = ConcurrentHashMap<String, UUID>()
    private val destinationsBySession = ConcurrentHashMap<String, MutableMap<String, String>>()

    fun addSession(userId: UUID, sessionId: String) {
        userBySession[sessionId] = userId
        sessionsByUser.compute(userId) { _, existing ->
            (existing ?: Collections.synchronizedSet(HashSet())).apply { add(sessionId) }
        }
    }

    fun removeSession(sessionId: String) {
        val userId = userBySession.remove(sessionId)
        if (userId != null) {
            sessionsByUser.compute(userId) { _, existing ->
                existing?.apply { remove(sessionId) }?.takeIf { it.isNotEmpty() }
            }
        }
        destinationsBySession.remove(sessionId)
    }

    fun addSubscription(sessionId: String, subscriptionId: String, destination: String) {
        destinationsBySession.compute(sessionId) { _, existing ->
            (existing ?: Collections.synchronizedMap(HashMap())).apply { put(subscriptionId, destination) }
        }
    }

    fun removeSubscription(sessionId: String, subscriptionId: String) {
        destinationsBySession.computeIfPresent(sessionId) { _, existing ->
            existing.apply { remove(subscriptionId) }
        }
    }

    fun isOnline(userId: UUID): Boolean =
        sessionsByUser[userId]?.isNotEmpty() ?: false

    fun isViewingConversation(userId: UUID, conversationId: UUID): Boolean {
        val target = "$CHAT_TOPIC_PREFIX$conversationId"
        val sessions = sessionsByUser[userId] ?: return false
        val snapshot = synchronized(sessions) { sessions.toList() }
        return snapshot.any { sessionId ->
            val destinations = destinationsBySession[sessionId] ?: return@any false
            synchronized(destinations) { destinations.values.any { it == target } }
        }
    }

    companion object {
        private const val CHAT_TOPIC_PREFIX = "/topic/chat/"
    }
}
