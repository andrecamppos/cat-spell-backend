package com.catspell.api.push

import com.catspell.api.push.presence.PresenceRegistry
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PresenceRegistryTest {

    private lateinit var registry: PresenceRegistry

    @BeforeEach
    fun setUp() {
        registry = PresenceRegistry()
    }

    @Test
    fun `user is offline with no sessions and online after adding a session`() {
        val user = UUID.randomUUID()

        assertFalse(registry.isOnline(user))

        registry.addSession(user, "s1")
        assertTrue(registry.isOnline(user))
    }

    @Test
    fun `user stays online until the last of multiple sessions disconnects`() {
        val user = UUID.randomUUID()

        registry.addSession(user, "s1")
        registry.addSession(user, "s2")
        assertTrue(registry.isOnline(user))

        registry.removeSession("s1")
        assertTrue(registry.isOnline(user))

        registry.removeSession("s2")
        assertFalse(registry.isOnline(user))
    }

    @Test
    fun `isViewingConversation reflects chat topic subscription lifecycle`() {
        val user = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        registry.addSession(user, "s1")

        assertFalse(registry.isViewingConversation(user, conversationId))

        registry.addSubscription("s1", "sub-1", "/topic/chat/$conversationId")
        assertTrue(registry.isViewingConversation(user, conversationId))

        registry.removeSubscription("s1", "sub-1")
        assertFalse(registry.isViewingConversation(user, conversationId))
    }

    @Test
    fun `removeSession clears presence and all subscriptions for that session only`() {
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        val conversationId = UUID.randomUUID()

        registry.addSession(user1, "s1")
        registry.addSubscription("s1", "sub-1", "/topic/chat/$conversationId")
        registry.addSession(user2, "s2")
        registry.addSubscription("s2", "sub-2", "/topic/chat/$conversationId")

        registry.removeSession("s1")

        assertFalse(registry.isOnline(user1))
        assertFalse(registry.isViewingConversation(user1, conversationId))
        // Second user is untouched
        assertTrue(registry.isOnline(user2))
        assertTrue(registry.isViewingConversation(user2, conversationId))
    }

    @Test
    fun `isViewingConversation ignores non-chat destinations and other conversations`() {
        val user = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        val otherConversationId = UUID.randomUUID()
        registry.addSession(user, "s1")

        registry.addSubscription("s1", "sub-1", "/topic/other")
        registry.addSubscription("s1", "sub-2", "/topic/chat/$otherConversationId")

        assertFalse(registry.isViewingConversation(user, conversationId))
        assertTrue(registry.isViewingConversation(user, otherConversationId))
    }

    @Test
    fun `isViewingConversation is true when any of a users sessions holds the subscription`() {
        val user = UUID.randomUUID()
        val conversationId = UUID.randomUUID()

        registry.addSession(user, "s1")
        registry.addSession(user, "s2")
        registry.addSubscription("s2", "sub-9", "/topic/chat/$conversationId")

        assertTrue(registry.isViewingConversation(user, conversationId))
    }
}
