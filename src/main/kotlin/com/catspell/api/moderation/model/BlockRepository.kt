package com.catspell.api.moderation.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BlockRepository : JpaRepository<Block, UUID> {
    @Query(
        "SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Block b " +
            "WHERE (b.blocker.id = :a AND b.blocked.id = :bId) OR (b.blocker.id = :bId AND b.blocked.id = :a)"
    )
    fun existsBlockBetween(@Param("a") a: UUID, @Param("bId") bId: UUID): Boolean

    fun existsByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Boolean

    fun findByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Block?

    @Modifying
    @Query("DELETE FROM Block b WHERE b.blocker.id = :blockerId AND b.blocked.id = :blockedId")
    fun deleteByBlockerIdAndBlockedId(@Param("blockerId") blockerId: UUID, @Param("blockedId") blockedId: UUID): Int

    fun findByBlockerIdOrderByCreatedAtDesc(blockerId: UUID): List<Block>
}
