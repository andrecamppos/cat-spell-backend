package com.catspell.api.discovery.model

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class SwipeRequest(
    @field:NotNull
    val catId: UUID,

    @field:NotNull
    @field:Pattern(regexp = "LIKE|PASS", message = "Action must be LIKE or PASS")
    val action: String
)

data class SwipeResponse(
    val swipeId: UUID,
    val matched: Boolean,
    val matchId: UUID? = null
)

data class FeedItemResponse(
    val catId: UUID,
    val name: String,
    val age: Int,
    val ageUnit: String,
    val breed: String?,
    val bio: String?,
    val ownerId: UUID,
    val ownerDisplayName: String,
    val catPhotoThumbnail: String?,
    val ownerPhotoThumbnail: String?,
    val distanceKm: Int
)

data class FeedResponse(
    val cats: List<FeedItemResponse>,
    val cursor: CursorResponse?
)

data class CursorResponse(
    val seed: Double,
    val offset: Int,
    val hasMore: Boolean
)

data class FeedRequest(
    val cursor: String? = null,
    val pageSize: Int = 20
)
