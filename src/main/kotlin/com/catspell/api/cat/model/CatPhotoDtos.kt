package com.catspell.api.cat.model

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CatUploadUrlRequest(
    @field:NotBlank
    val contentType: String,

    @field:NotBlank
    val fileName: String
)

data class CatUploadUrlResponse(
    val photoId: UUID,
    val uploadUrl: String,
    val s3Key: String
)

data class CatConfirmUploadResponse(
    val photoId: UUID,
    val s3Key: String,
    val thumbnailS3Key: String,
    val displayOrder: Int,
    val status: String
)

data class CatReorderRequest(
    val photoIds: List<UUID>
)

data class CatPhotoResponse(
    val id: UUID,
    val s3Key: String,
    val thumbnailS3Key: String?,
    val displayOrder: Int,
    val contentType: String,
    val status: String,
    val createdAt: Instant
)
