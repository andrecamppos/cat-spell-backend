package com.catspell.api.profile.service

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.profile.model.*
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID

@Service
class ProfileService(
    private val userProfileRepository: UserProfileRepository,
    private val userRepository: UserRepository,
    private val userPhotoRepository: UserPhotoRepository
) {
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Transactional
    fun createProfile(userId: UUID, request: CreateProfileRequest): ProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        if (userProfileRepository.existsByUserId(userId)) {
            throw IllegalStateException("Profile already exists")
        }

        validateAge(request.dateOfBirth)
        validateAgeRange(request.ageMin, request.ageMax)
        validateGender(request.gender)
        validateGenderPreference(request.genderPreference)

        val profile = UserProfile(
            user = user,
            displayName = request.displayName,
            bio = request.bio,
            dateOfBirth = request.dateOfBirth,
            gender = request.gender,
            genderPreference = request.genderPreference,
            ageMin = request.ageMin,
            ageMax = request.ageMax,
            maxDistanceKm = request.maxDistanceKm
        )

        val saved = userProfileRepository.save(profile)
        return toResponse(saved)
    }

    fun getProfile(userId: UUID): ProfileResponse {
        val profile = userProfileRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Profile not found")
        return toResponse(profile)
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): ProfileResponse {
        val profile = userProfileRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Profile not found")

        request.displayName?.let { profile.displayName = it }
        request.bio?.let { profile.bio = it }
        request.dateOfBirth?.let {
            validateAge(it)
            profile.dateOfBirth = it
        }
        request.gender?.let { profile.gender = it }
        request.genderPreference?.let { profile.genderPreference = it }
        request.ageMin?.let { profile.ageMin = it }
        request.ageMax?.let { profile.ageMax = it }
        request.maxDistanceKm?.let { profile.maxDistanceKm = it }

        if (request.ageMin != null || request.ageMax != null) {
            validateAgeRange(profile.ageMin, profile.ageMax)
        }

        profile.updatedAt = Instant.now()
        val saved = userProfileRepository.save(profile)
        return toResponse(saved)
    }

    @Transactional
    fun updateLocation(userId: UUID, request: UpdateLocationRequest): ProfileResponse {
        val profile = userProfileRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Profile not found")

        profile.location = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))
        profile.updatedAt = Instant.now()
        val saved = userProfileRepository.save(profile)
        return toResponse(saved)
    }

    private fun validateAge(dateOfBirth: LocalDate) {
        val age = Period.between(dateOfBirth, LocalDate.now()).years
        if (age < 18) {
            throw IllegalArgumentException("User must be at least 18 years old")
        }
    }

    private fun validateAgeRange(ageMin: Int, ageMax: Int) {
        if (ageMin > ageMax) {
            throw IllegalArgumentException("ageMin must be less than or equal to ageMax")
        }
    }

    private fun validateGender(gender: String) {
        if (gender !in VALID_GENDERS) {
            throw IllegalArgumentException("Gender must be one of: $VALID_GENDERS")
        }
    }

    private fun validateGenderPreference(genderPreference: String) {
        if (genderPreference !in VALID_GENDER_PREFERENCES) {
            throw IllegalArgumentException("Gender preference must be one of: $VALID_GENDER_PREFERENCES")
        }
    }

    companion object {
        private val VALID_GENDERS = setOf("MALE", "FEMALE")
        private val VALID_GENDER_PREFERENCES = setOf("MALE", "FEMALE", "EVERYONE")
    }

    fun checkCompleteness(userId: UUID): CompletenessResponse {
        val missingFields = mutableListOf<String>()
        val profile = userProfileRepository.findByUserId(userId)

        if (profile == null) {
            missingFields.add("profile")
            return CompletenessResponse(isComplete = false, missingFields = missingFields)
        }

        if (profile.displayName.isBlank()) missingFields.add("displayName")
        if (profile.bio.isNullOrBlank()) missingFields.add("bio")
        if (profile.gender.isBlank()) missingFields.add("gender")
        if (profile.genderPreference.isBlank()) missingFields.add("genderPreference")
        if (profile.location == null) missingFields.add("location")

        val activePhotos = userPhotoRepository.countByUserIdAndStatus(userId, "ACTIVE")
        if (activePhotos < 1) missingFields.add("photo")

        return CompletenessResponse(isComplete = missingFields.isEmpty(), missingFields = missingFields)
    }

    private fun toResponse(profile: UserProfile): ProfileResponse {
        return ProfileResponse(
            displayName = profile.displayName,
            bio = profile.bio,
            dateOfBirth = profile.dateOfBirth,
            gender = profile.gender,
            genderPreference = profile.genderPreference,
            ageMin = profile.ageMin,
            ageMax = profile.ageMax,
            maxDistanceKm = profile.maxDistanceKm,
            latitude = profile.location?.y,
            longitude = profile.location?.x
        )
    }
}
