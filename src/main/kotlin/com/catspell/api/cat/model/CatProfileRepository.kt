package com.catspell.api.cat.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CatProfileRepository : JpaRepository<CatProfile, UUID> {
    fun findByUserId(userId: UUID): List<CatProfile>
    fun findByIdAndUserId(id: UUID, userId: UUID): CatProfile?
    fun countByUserId(userId: UUID): Int
}
