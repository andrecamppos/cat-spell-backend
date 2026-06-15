package com.catspell.api.chat.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SendMessageRequest(
    val conversationId: UUID? = null,
    val matchId: UUID? = null,
    @field:NotBlank
    @field:Size(max = 1000)
    val content: String
)

data class ChatMessageResponse(
    val messageId: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val senderName: String,
    val content: String,
    val createdAt: Instant
)

data class ChatNotification(
    val conversationId: UUID,
    val messageId: UUID,
    val senderName: String,
    val preview: String
)

data class MessagePageResponse(
    val messages: List<ChatMessageResponse>,
    val nextCursor: Instant?,
    val hasMore: Boolean
)
