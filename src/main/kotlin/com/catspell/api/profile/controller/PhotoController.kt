package com.catspell.api.profile.controller

import com.catspell.api.profile.model.*
import com.catspell.api.profile.service.PhotoService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/profile/photos")
class PhotoController(
    private val photoService: PhotoService
) {

    @PostMapping("/upload-url")
    fun requestUploadUrl(@Valid @RequestBody request: UploadUrlRequest): ResponseEntity<UploadUrlResponse> {
        val userId = extractUserId()
        val response = photoService.requestUploadUrl(userId, request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/confirm")
    fun confirmUpload(@PathVariable id: UUID): ResponseEntity<ConfirmUploadResponse> {
        val userId = extractUserId()
        val response = photoService.confirmUpload(userId, id)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    fun deletePhoto(@PathVariable id: UUID): ResponseEntity<Void> {
        val userId = extractUserId()
        photoService.deletePhoto(userId, id)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/reorder")
    fun reorderPhotos(@Valid @RequestBody request: ReorderRequest): ResponseEntity<Void> {
        val userId = extractUserId()
        photoService.reorderPhotos(userId, request)
        return ResponseEntity.ok().build()
    }

    @GetMapping
    fun listPhotos(): ResponseEntity<List<PhotoResponse>> {
        val userId = extractUserId()
        val response = photoService.listPhotos(userId)
        return ResponseEntity.ok(response)
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
