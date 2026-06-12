package com.catspell.api.cat.service

import com.catspell.api.cat.model.*
import com.catspell.api.common.exception.CatPhotoLimitExceededException
import com.catspell.api.common.exception.InvalidPhotoTypeException
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.profile.service.StorageService
import net.coobird.thumbnailator.Thumbnails
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

@Service
class CatPhotoService(
    private val catPhotoRepository: CatPhotoRepository,
    private val catProfileRepository: CatProfileRepository,
    private val storageService: StorageService
) {
    companion object {
        private const val MAX_PHOTOS = 10
        private const val MAX_FILE_SIZE_BYTES = 10_485_760L
        private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png")
    }

    @Transactional
    fun requestUploadUrl(userId: UUID, catId: UUID, request: CatUploadUrlRequest): CatUploadUrlResponse {
        val catProfile = verifyCatOwnership(userId, catId)

        if (request.contentType !in ALLOWED_CONTENT_TYPES) {
            throw InvalidPhotoTypeException()
        }

        val activeCount = catPhotoRepository.countByCatProfileIdAndStatus(catId, "ACTIVE") +
            catPhotoRepository.countByCatProfileIdAndStatus(catId, "PENDING")
        if (activeCount >= MAX_PHOTOS) {
            throw CatPhotoLimitExceededException()
        }

        val extension = if (request.contentType == "image/jpeg") "jpg" else "png"
        val s3Key = "cats/$catId/${UUID.randomUUID()}.$extension"

        val displayOrder = catPhotoRepository.findByCatProfileIdOrderByDisplayOrderAsc(catId).size

        val photo = CatPhoto(
            catProfile = catProfile,
            s3Key = s3Key,
            displayOrder = displayOrder,
            contentType = request.contentType,
            status = "PENDING"
        )
        val saved = catPhotoRepository.save(photo)

        val uploadUrl = storageService.generatePresignedUploadUrl(s3Key, request.contentType, MAX_FILE_SIZE_BYTES)

        return CatUploadUrlResponse(
            photoId = saved.id!!,
            uploadUrl = uploadUrl,
            s3Key = s3Key
        )
    }

    @Transactional
    fun confirmUpload(userId: UUID, catId: UUID, photoId: UUID): CatConfirmUploadResponse {
        verifyCatOwnership(userId, catId)

        val photo = catPhotoRepository.findByIdAndCatProfileId(photoId, catId)
            ?: throw ResourceNotFoundException("Cat photo not found")

        if (photo.status != "PENDING") {
            throw IllegalStateException("Photo is not in PENDING status")
        }

        if (!storageService.objectExists(photo.s3Key)) {
            throw IllegalStateException("Photo has not been uploaded to S3")
        }

        val originalBytes = storageService.getObject(photo.s3Key)
        val thumbnailBytes = generateThumbnail(originalBytes)
        val thumbnailKey = "thumbnails/cats/$catId/$photoId.jpg"
        storageService.putObject(thumbnailKey, thumbnailBytes, "image/jpeg")

        photo.thumbnailS3Key = thumbnailKey
        photo.status = "ACTIVE"
        photo.fileSizeBytes = originalBytes.size.toLong()
        val saved = catPhotoRepository.save(photo)

        return CatConfirmUploadResponse(
            photoId = saved.id!!,
            s3Key = saved.s3Key,
            thumbnailS3Key = saved.thumbnailS3Key!!,
            displayOrder = saved.displayOrder,
            status = saved.status
        )
    }

    @Transactional
    fun deletePhoto(userId: UUID, catId: UUID, photoId: UUID) {
        verifyCatOwnership(userId, catId)

        val photo = catPhotoRepository.findByIdAndCatProfileId(photoId, catId)
            ?: throw ResourceNotFoundException("Cat photo not found")

        storageService.deleteObject(photo.s3Key)
        photo.thumbnailS3Key?.let { storageService.deleteObject(it) }
        catPhotoRepository.delete(photo)

        val remaining = catPhotoRepository.findByCatProfileIdOrderByDisplayOrderAsc(catId)
        remaining.forEachIndexed { index, p -> p.displayOrder = index }
        catPhotoRepository.saveAll(remaining)
    }

    @Transactional
    fun reorderPhotos(userId: UUID, catId: UUID, request: CatReorderRequest) {
        verifyCatOwnership(userId, catId)

        val photos = catPhotoRepository.findByCatProfileIdOrderByDisplayOrderAsc(catId)
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
        catPhotoRepository.saveAll(photos)
    }

    fun listPhotos(userId: UUID, catId: UUID): List<CatPhotoResponse> {
        verifyCatOwnership(userId, catId)
        return catPhotoRepository.findByCatProfileIdOrderByDisplayOrderAsc(catId)
            .filter { it.status == "ACTIVE" }
            .map { toResponse(it) }
    }

    private fun verifyCatOwnership(userId: UUID, catId: UUID): CatProfile {
        return catProfileRepository.findByIdAndUserId(catId, userId)
            ?: throw ResourceNotFoundException("Cat profile not found")
    }

    private fun generateThumbnail(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        Thumbnails.of(ByteArrayInputStream(data))
            .size(200, 200)
            .outputFormat("jpeg")
            .toOutputStream(output)
        return output.toByteArray()
    }

    private fun toResponse(catPhoto: CatPhoto): CatPhotoResponse {
        return CatPhotoResponse(
            id = catPhoto.id!!,
            s3Key = catPhoto.s3Key,
            thumbnailS3Key = catPhoto.thumbnailS3Key,
            displayOrder = catPhoto.displayOrder,
            contentType = catPhoto.contentType,
            status = catPhoto.status,
            createdAt = catPhoto.createdAt
        )
    }
}
