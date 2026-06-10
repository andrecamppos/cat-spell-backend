package com.catspell.api.auth.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByToken(token: String): RefreshToken?
    fun findAllByUserAndRevokedFalse(user: User): List<RefreshToken>
}
