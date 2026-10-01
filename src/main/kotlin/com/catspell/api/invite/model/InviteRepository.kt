package com.catspell.api.invite.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface InviteRepository : JpaRepository<Invite, UUID> {
    fun findByCodeHash(codeHash: String): Invite?

    /**
     * Atomically claim an unconsumed invite in a single conditional UPDATE. The `consumedAt IS NULL`
     * guard is evaluated under a row lock by the database, so exactly one of any concurrent callers
     * observes a matching row and receives a non-zero result — closing the read-check-write single-use
     * race (INV-04, D-07). Returns the number of rows updated (1 = claimed, 0 = already consumed).
     */
    @Modifying
    @Query("UPDATE Invite i SET i.consumedAt = :now, i.consumedBy = :inviteeId WHERE i.id = :id AND i.consumedAt IS NULL")
    fun markConsumed(@Param("id") id: UUID, @Param("inviteeId") inviteeId: UUID, @Param("now") now: Instant): Int
}
