package com.catspell.api.auth.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByTokenHash(tokenHash: String): PasswordResetToken?
    fun findAllByUserAndUsedAtIsNull(user: User): List<PasswordResetToken>
}
