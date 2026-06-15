package com.catspell.api.chat.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConversationRepository : JpaRepository<Conversation, UUID> {
    fun findByMatchId(matchId: UUID): Conversation?
}
