package com.catspell.api.cat.model

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateCatProfileRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    @field:NotNull
    @field:Min(0)
    val age: Int,

    @field:NotNull
    val ageUnit: AgeUnit,

    @field:Size(max = 100)
    val breed: String? = null,

    @field:Size(max = 500)
    val bio: String? = null
)

data class UpdateCatProfileRequest(
    @field:Size(max = 100)
    val name: String? = null,

    @field:Min(0)
    val age: Int? = null,

    val ageUnit: AgeUnit? = null,

    @field:Size(max = 100)
    val breed: String? = null,

    @field:Size(max = 500)
    val bio: String? = null
)

data class CatProfileResponse(
    val id: UUID,
    val name: String,
    val age: Int,
    val ageUnit: AgeUnit,
    val breed: String?,
    val bio: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
