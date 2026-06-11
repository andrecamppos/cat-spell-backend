package com.catspell.api.profile.model

import jakarta.validation.constraints.*
import java.time.LocalDate

data class CreateProfileRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val displayName: String,

    @field:Size(max = 1000)
    val bio: String? = null,

    @field:NotNull
    val dateOfBirth: LocalDate,

    @field:NotBlank
    val gender: String,

    @field:NotBlank
    val genderPreference: String,

    @field:NotNull
    @field:Min(18)
    @field:Max(99)
    val ageMin: Int,

    @field:NotNull
    @field:Min(18)
    @field:Max(99)
    val ageMax: Int,

    @field:NotNull
    @field:Min(1)
    val maxDistanceKm: Int
)

data class UpdateProfileRequest(
    @field:Size(max = 100)
    val displayName: String? = null,

    @field:Size(max = 1000)
    val bio: String? = null,

    val dateOfBirth: LocalDate? = null,

    val gender: String? = null,

    val genderPreference: String? = null,

    @field:Min(18)
    @field:Max(99)
    val ageMin: Int? = null,

    @field:Min(18)
    @field:Max(99)
    val ageMax: Int? = null,

    @field:Min(1)
    val maxDistanceKm: Int? = null
)

data class UpdateLocationRequest(
    @field:NotNull
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double,

    @field:NotNull
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double
)

data class ProfileResponse(
    val displayName: String,
    val bio: String?,
    val dateOfBirth: LocalDate,
    val gender: String,
    val genderPreference: String,
    val ageMin: Int,
    val ageMax: Int,
    val maxDistanceKm: Int,
    val latitude: Double?,
    val longitude: Double?
)
