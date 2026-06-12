package com.catspell.api.cat.service

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.cat.model.*
import com.catspell.api.common.exception.CatLimitExceededException
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.profile.service.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CatProfileService(
    private val catProfileRepository: CatProfileRepository,
    private val userRepository: UserRepository,
    private val catPhotoRepository: CatPhotoRepository,
    private val storageService: StorageService
) {

    companion object {
        const val MAX_CATS_PER_USER = 5
    }

    @Transactional
    fun createCatProfile(userId: UUID, request: CreateCatProfileRequest): CatProfileResponse {
        if (catProfileRepository.countByUserId(userId) >= MAX_CATS_PER_USER) {
            throw CatLimitExceededException()
        }

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val catProfile = CatProfile(
            user = user,
            name = request.name,
            age = request.age,
            ageUnit = request.ageUnit,
            breed = request.breed,
            bio = request.bio
        )

        val saved = catProfileRepository.save(catProfile)
        return toResponse(saved)
    }

    fun listCatProfiles(userId: UUID): List<CatProfileResponse> {
        return catProfileRepository.findByUserId(userId).map { toResponse(it) }
    }

    fun getCatProfile(userId: UUID, catId: UUID): CatProfileResponse {
        val catProfile = catProfileRepository.findByIdAndUserId(catId, userId)
            ?: throw ResourceNotFoundException("Cat profile not found")
        return toResponse(catProfile)
    }

    @Transactional
    fun updateCatProfile(userId: UUID, catId: UUID, request: UpdateCatProfileRequest): CatProfileResponse {
        val catProfile = catProfileRepository.findByIdAndUserId(catId, userId)
            ?: throw ResourceNotFoundException("Cat profile not found")

        request.name?.let { catProfile.name = it }
        request.age?.let { catProfile.age = it }
        request.ageUnit?.let { catProfile.ageUnit = it }
        request.breed?.let { catProfile.breed = it }
        request.bio?.let { catProfile.bio = it }

        catProfile.updatedAt = Instant.now()
        val saved = catProfileRepository.save(catProfile)
        return toResponse(saved)
    }

    @Transactional
    fun deleteCatProfile(userId: UUID, catId: UUID) {
        val catProfile = catProfileRepository.findByIdAndUserId(catId, userId)
            ?: throw ResourceNotFoundException("Cat profile not found")

        val photos = catPhotoRepository.findByCatProfileId(catId)
        photos.forEach { photo ->
            storageService.deleteObject(photo.s3Key)
            photo.thumbnailS3Key?.let { storageService.deleteObject(it) }
        }
        catPhotoRepository.deleteAll(photos)
        catPhotoRepository.flush()

        catProfileRepository.delete(catProfile)
    }

    private fun toResponse(catProfile: CatProfile): CatProfileResponse {
        return CatProfileResponse(
            id = catProfile.id!!,
            name = catProfile.name,
            age = catProfile.age,
            ageUnit = catProfile.ageUnit,
            breed = catProfile.breed,
            bio = catProfile.bio,
            createdAt = catProfile.createdAt,
            updatedAt = catProfile.updatedAt
        )
    }
}
