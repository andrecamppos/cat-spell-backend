package com.catspell.api.chat.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConversationParticipantRepository : JpaRepository<ConversationParticipant, UUID> {
    fun findByConversationIdAndUserId(conversationId: UUID, userId: UUID): ConversationParticipant?
    fun findByUserId(userId: UUID): List<ConversationParticipant>
    fun existsByConversationIdAndUserId(conversationId: UUID, userId: UUID): Boolean
}
