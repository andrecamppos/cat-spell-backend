package com.catspell.api.profile.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
    fun findByUserId(userId: UUID): UserProfile?
    fun existsByUserId(userId: UUID): Boolean
}
