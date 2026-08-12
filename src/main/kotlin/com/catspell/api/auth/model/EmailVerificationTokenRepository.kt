package com.catspell.api.auth.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, UUID> {
    fun findByTokenHash(tokenHash: String): EmailVerificationToken?
    fun findAllByUserAndUsedAtIsNull(user: User): List<EmailVerificationToken>

    /**
     * Atomically claim an unused token in a single conditional UPDATE. The `usedAt IS NULL` guard is
     * evaluated under a row lock by the database, so exactly one of any concurrent callers observes a
     * matching row and receives a non-zero result — closing the read-check-write single-use race.
     * Returns the number of rows updated (1 = claimed, 0 = already used).
     */
    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    fun markUsed(@Param("id") id: UUID, @Param("now") now: Instant): Int
}
