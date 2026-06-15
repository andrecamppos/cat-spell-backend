package com.catspell.api.match.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MatchRepository : JpaRepository<Match, UUID> {
    @Query("SELECT m FROM Match m WHERE m.user1.id = :userId OR m.user2.id = :userId ORDER BY m.matchedAt DESC")
    fun findByUser1IdOrUser2Id(@Param("userId") userId: UUID): List<Match>

    @Query("SELECT m FROM Match m WHERE (m.user1.id = :u1 AND m.user2.id = :u2) OR (m.user1.id = :u2 AND m.user2.id = :u1)")
    fun findByUserPair(@Param("u1") u1: UUID, @Param("u2") u2: UUID): Match?
}
