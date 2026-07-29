package com.catspell.api.push.event

import java.util.UUID

/**
 * Domain events consumed by [PushNotificationListener]. They carry only IDs and precomputed strings
 * (never JPA entities) so the async AFTER_COMMIT listener has no lazy-load dependency on a closed
 * persistence context (RESEARCH Pitfall 1).
 */
data class MatchCreatedEvent(
    val matchId: UUID,
    val userId1: UUID,
    val userId2: UUID
)

data class MessageSentEvent(
    val recipientId: UUID,
    val conversationId: UUID,
    val messageId: UUID,
    val senderId: UUID,
    val senderName: String,
    val preview: String
)
