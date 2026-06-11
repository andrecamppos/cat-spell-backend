package com.catspell.api.profile.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserPhotoRepository : JpaRepository<UserPhoto, UUID> {
    fun findByUserIdOrderByDisplayOrderAsc(userId: UUID): List<UserPhoto>
    fun countByUserIdAndStatus(userId: UUID, status: String): Int
    fun findByIdAndUserId(id: UUID, userId: UUID): UserPhoto?
}
