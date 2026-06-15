package com.catspell.api.chat.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ConversationRepository : JpaRepository<Conversation, UUID> {
    fun findByMatchId(matchId: UUID): Conversation?

    @Query("SELECT c FROM Conversation c JOIN ConversationParticipant cp ON cp.conversation = c WHERE cp.user.id = :userId ORDER BY c.lastMessageAt DESC NULLS LAST")
    fun findConversationsByUserId(@Param("userId") userId: UUID): List<Conversation>
}
