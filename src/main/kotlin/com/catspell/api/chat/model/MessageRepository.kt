package com.catspell.api.chat.model

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {
    fun findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
        conversationId: UUID,
        cursor: Instant,
        pageable: Pageable
    ): List<Message>

    fun findByConversationIdOrderByCreatedAtDesc(
        conversationId: UUID,
        pageable: Pageable
    ): List<Message>

    fun countByConversationIdAndSenderIdNotAndCreatedAtAfter(
        conversationId: UUID,
        userId: UUID,
        after: Instant
    ): Long

    fun findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc(
        conversationIds: List<UUID>,
        userId: UUID
    ): List<Message>

    fun findTopByConversationIdOrderByCreatedAtDesc(conversationId: UUID): Message?

    fun countByConversationIdAndSenderIdNot(conversationId: UUID, senderId: UUID): Long
}
