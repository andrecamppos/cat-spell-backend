package com.catspell.api.cat.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CatPhotoRepository : JpaRepository<CatPhoto, UUID> {
    fun findByCatProfileIdOrderByDisplayOrderAsc(catProfileId: UUID): List<CatPhoto>
    fun countByCatProfileIdAndStatus(catProfileId: UUID, status: String): Int
    fun findByIdAndCatProfileId(id: UUID, catProfileId: UUID): CatPhoto?
    fun findByCatProfileId(catProfileId: UUID): List<CatPhoto>
}
