package com.catspell.api.chat.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ConversationRepository : JpaRepository<Conversation, UUID> {
    fun findByMatchId(matchId: UUID): Conversation?

    /**
     * Race-free create of a match's conversation. The UNIQUE(match_id) constraint is the arbiter: when two first
     * messages for the same match are handled concurrently, exactly one inserts and the other blocks until that
     * transaction ends, then skips the row instead of failing with a duplicate-key error that would drop its
     * message. Returns the number of rows inserted (1 = created here, 0 = another transaction already created it).
     */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO conversations (id, match_id, created_at)
            VALUES (gen_random_uuid(), :matchId, :now)
            ON CONFLICT (match_id) DO NOTHING
        """
    )
    fun insertIfAbsent(@Param("matchId") matchId: UUID, @Param("now") now: Instant): Int

    @Query("SELECT c FROM Conversation c JOIN ConversationParticipant cp ON cp.conversation = c WHERE cp.user.id = :userId AND c.match.endedAt IS NULL ORDER BY c.lastMessageAt DESC NULLS LAST")
    fun findConversationsByUserId(@Param("userId") userId: UUID): List<Conversation>

    /**
     * Ids of the caller's hidden conversations: the match has ended (unmatch or block), or a block
     * row exists between the two matched users in either direction. Resolved in ONE set-based query
     * per reconnect rather than one block lookup per conversation (D-05).
     */
    @Query(
        """
        SELECT c.id FROM Conversation c JOIN c.match m JOIN ConversationParticipant cp ON cp.conversation = c
        WHERE cp.user.id = :userId
          AND (m.endedAt IS NOT NULL OR EXISTS (
                SELECT b.id FROM Block b
                WHERE (b.blocker.id = m.user1.id AND b.blocked.id = m.user2.id)
                   OR (b.blocker.id = m.user2.id AND b.blocked.id = m.user1.id)))
        """
    )
    fun findHiddenConversationIdsForUser(@Param("userId") userId: UUID): List<UUID>
}
