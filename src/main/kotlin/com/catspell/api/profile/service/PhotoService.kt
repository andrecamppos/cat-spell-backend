package com.catspell.api.profile.service

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.common.exception.InvalidPhotoTypeException
import com.catspell.api.common.exception.PhotoLimitExceededException
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.profile.model.*
import net.coobird.thumbnailator.Thumbnails
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

@Service
class PhotoService(
    private val userPhotoRepository: UserPhotoRepository,
    private val storageService: StorageService,
    private val userRepository: UserRepository
) {
    companion object {
        private const val MAX_PHOTOS = 6
        private const val MAX_FILE_SIZE_BYTES = 10_485_760L
        private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png")
    }

    @Transactional
    fun requestUploadUrl(userId: UUID, request: UploadUrlRequest): UploadUrlResponse {
        if (request.contentType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidPhotoTypeException()
        }

        val activeCount = userPhotoRepository.countByUserIdAndStatus(userId, "ACTIVE") +
            userPhotoRepository.countByUserIdAndStatus(userId, "PENDING")
        if (activeCount >= MAX_PHOTOS) {
            throw PhotoLimitExceededException()
        }

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val extension = if (request.contentType == "image/jpeg") "jpg" else "png"
        val s3Key = "photos/$userId/${UUID.randomUUID()}.$extension"

        val displayOrder = userPhotoRepository.findByUserIdOrderByDisplayOrderAsc(userId).size

        val photo = UserPhoto(
            user = user,
            s3Key = s3Key,
            displayOrder = displayOrder,
            contentType = request.contentType,
            status = "PENDING"
        )
        val saved = userPhotoRepository.save(photo)

        val uploadUrl = storageService.generatePresignedUploadUrl(s3Key, request.contentType, MAX_FILE_SIZE_BYTES)

        return UploadUrlResponse(
            photoId = saved.id!!,
            uploadUrl = uploadUrl,
            s3Key = s3Key
        )
    }

    @Transactional
    fun confirmUpload(userId: UUID, photoId: UUID): ConfirmUploadResponse {
        val photo = userPhotoRepository.findByIdAndUserId(photoId, userId)
            ?: throw ResourceNotFoundException("Photo not found")

        if (photo.status != "PENDING") {
            throw IllegalStateException("Photo is not in PENDING status")
        }

        if (!storageService.objectExists(photo.s3Key)) {
            throw IllegalStateException("Photo has not been uploaded to S3")
        }

        val originalBytes = storageService.getObject(photo.s3Key)
        val thumbnailBytes = generateThumbnail(originalBytes)
        val thumbnailKey = "thumbnails/$userId/$photoId.jpg"
        storageService.putObject(thumbnailKey, thumbnailBytes, "image/jpeg")

        photo.thumbnailS3Key = thumbnailKey
        photo.status = "ACTIVE"
        photo.fileSizeBytes = originalBytes.size.toLong()
        val saved = userPhotoRepository.save(photo)

        return ConfirmUploadResponse(
            photoId = saved.id!!,
            s3Key = saved.s3Key,
            thumbnailS3Key = saved.thumbnailS3Key!!,
            displayOrder = saved.displayOrder,
            status = saved.status
        )
    }

    @Transactional
    fun deletePhoto(userId: UUID, photoId: UUID) {
        val photo = userPhotoRepository.findByIdAndUserId(photoId, userId)
            ?: throw ResourceNotFoundException("Photo not found")

        storageService.deleteObject(photo.s3Key)
        photo.thumbnailS3Key?.let { storageService.deleteObject(it) }
        userPhotoRepository.delete(photo)

        val remaining = userPhotoRepository.findByUserIdOrderByDisplayOrderAsc(userId)
        remaining.forEachIndexed { index, p -> p.displayOrder = index }
        userPhotoRepository.saveAll(remaining)
    }

    @Transactional
    fun reorderPhotos(userId: UUID, request: ReorderRequest) {
        val photos = userPhotoRepository.findByUserIdOrderByDisplayOrderAsc(userId)
            .filter { it.status == "ACTIVE" }

        val activeIds = photos.map { it.id }.toSet()
        val requestIds = request.photoIds.toSet()
        if (activeIds != requestIds || activeIds.size != request.photoIds.size) {
            throw IllegalArgumentException("Photo IDs must match all active photos exactly")
        }

        val photoMap = photos.associateBy { it.id }
        request.photoIds.forEachIndexed { index, id ->
            photoMap[id]!!.displayOrder = index
        }
        userPhotoRepository.saveAll(photos)
    }

    fun listPhotos(userId: UUID): List<PhotoResponse> {
        return userPhotoRepository.findByUserIdOrderByDisplayOrderAsc(userId)
            .filter { it.status == "ACTIVE" }
            .map { toResponse(it) }
    }

    private fun generateThumbnail(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        Thumbnails.of(ByteArrayInputStream(data))
            .size(200, 200)
            .outputFormat("jpeg")
            .toOutputStream(output)
        return output.toByteArray()
    }

    private fun toResponse(photo: UserPhoto): PhotoResponse {
        return PhotoResponse(
            id = photo.id!!,
            s3Key = photo.s3Key,
            thumbnailS3Key = photo.thumbnailS3Key,
            displayOrder = photo.displayOrder,
            contentType = photo.contentType,
            status = photo.status,
            createdAt = photo.createdAt
        )
    }
}
