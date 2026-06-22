package com.catspell.api.discovery.model

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class SwipeRequest(
    val catId: UUID? = null,

    val targetUserId: UUID? = null,

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
    val type: String,
    val catId: UUID? = null,
    val catName: String? = null,
    val catAge: Int? = null,
    val catAgeUnit: String? = null,
    val breed: String? = null,
    val catBio: String? = null,
    val userId: UUID,
    val displayName: String,
    val catPhotoThumbnail: String? = null,
    val userPhotoThumbnail: String? = null,
    val distanceKm: Int
)

data class FeedResponse(
    val cards: List<FeedItemResponse>,
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

data class OwnerProfileResponse(
    val userId: UUID,
    val displayName: String,
    val bio: String?,
    val age: Int,
    val gender: String,
    val photos: List<OwnerPhotoResponse>,
    val cats: List<OwnerCatSummary>
)

data class OwnerPhotoResponse(
    val s3Key: String,
    val thumbnailS3Key: String?
)

data class OwnerCatSummary(
    val id: UUID,
    val name: String,
    val age: Int,
    val breed: String?,
    val photoThumbnail: String?
)
