package com.catspell.api.cat.controller

import com.catspell.api.cat.model.*
import com.catspell.api.cat.service.CatPhotoService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/cats/{catId}/photos")
class CatPhotoController(
    private val catPhotoService: CatPhotoService
) {

    @PostMapping("/upload-url")
    fun requestUploadUrl(
        @PathVariable catId: UUID,
        @Valid @RequestBody request: CatUploadUrlRequest
    ): ResponseEntity<CatUploadUrlResponse> {
        val userId = extractUserId()
        val response = catPhotoService.requestUploadUrl(userId, catId, request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{photoId}/confirm")
    fun confirmUpload(
        @PathVariable catId: UUID,
        @PathVariable photoId: UUID
    ): ResponseEntity<CatConfirmUploadResponse> {
        val userId = extractUserId()
        val response = catPhotoService.confirmUpload(userId, catId, photoId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{photoId}")
    fun deletePhoto(
        @PathVariable catId: UUID,
        @PathVariable photoId: UUID
    ): ResponseEntity<Void> {
        val userId = extractUserId()
        catPhotoService.deletePhoto(userId, catId, photoId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/reorder")
    fun reorderPhotos(
        @PathVariable catId: UUID,
        @Valid @RequestBody request: CatReorderRequest
    ): ResponseEntity<Void> {
        val userId = extractUserId()
        catPhotoService.reorderPhotos(userId, catId, request)
        return ResponseEntity.ok().build()
    }

    @GetMapping
    fun listPhotos(@PathVariable catId: UUID): ResponseEntity<List<CatPhotoResponse>> {
        val userId = extractUserId()
        val response = catPhotoService.listPhotos(userId, catId)
        return ResponseEntity.ok(response)
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
