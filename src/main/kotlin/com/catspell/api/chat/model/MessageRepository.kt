package com.catspell.api.chat.model

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /**
     * Marks every still-undelivered message of the match's single conversation delivered, in
     * both directions, in one set-based statement. Called only from
     * `MatchService.createMatch`'s reactivation branch so that messages left over from before a
     * block or unmatch cannot resurface as previews after a rematch (G-18-1, D-04, D-05 as
     * amended). Message content and rows are untouched; only the delivered flag changes.
     *
     * @return the number of message rows updated
     */
    @Modifying
    @Query("UPDATE Message m SET m.delivered = true WHERE m.conversation.match.id = :matchId AND m.delivered = false")
    fun markAllDeliveredForMatch(@Param("matchId") matchId: UUID): Int
}
