package com.catspell.api.profile.model

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class UploadUrlRequest(
    @field:NotBlank
    val contentType: String,

    @field:NotBlank
    val fileName: String
)

data class UploadUrlResponse(
    val photoId: UUID,
    val uploadUrl: String,
    val s3Key: String
)

data class ConfirmUploadResponse(
    val photoId: UUID,
    val s3Key: String,
    val thumbnailS3Key: String,
    val displayOrder: Int,
    val status: String
)

data class ReorderRequest(
    val photoIds: List<UUID>
)

data class PhotoResponse(
    val id: UUID,
    val s3Key: String,
    val thumbnailS3Key: String?,
    val displayOrder: Int,
    val contentType: String,
    val status: String,
    val createdAt: Instant
)

data class CompletenessResponse(
    val isComplete: Boolean,
    val missingFields: List<String>
)
