package com.catspell.api.cat.controller

import com.catspell.api.cat.model.CatProfileResponse
import com.catspell.api.cat.model.CreateCatProfileRequest
import com.catspell.api.cat.model.UpdateCatProfileRequest
import com.catspell.api.cat.service.CatProfileService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/cats")
class CatProfileController(
    private val catProfileService: CatProfileService
) {

    @PostMapping
    fun createCatProfile(@Valid @RequestBody request: CreateCatProfileRequest): ResponseEntity<CatProfileResponse> {
        val userId = extractUserId()
        val response = catProfileService.createCatProfile(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listCatProfiles(): ResponseEntity<List<CatProfileResponse>> {
        val userId = extractUserId()
        val response = catProfileService.listCatProfiles(userId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{catId}")
    fun getCatProfile(@PathVariable catId: UUID): ResponseEntity<CatProfileResponse> {
        val userId = extractUserId()
        val response = catProfileService.getCatProfile(userId, catId)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/{catId}")
    fun updateCatProfile(
        @PathVariable catId: UUID,
        @Valid @RequestBody request: UpdateCatProfileRequest
    ): ResponseEntity<CatProfileResponse> {
        val userId = extractUserId()
        val response = catProfileService.updateCatProfile(userId, catId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{catId}")
    fun deleteCatProfile(@PathVariable catId: UUID): ResponseEntity<Void> {
        val userId = extractUserId()
        catProfileService.deleteCatProfile(userId, catId)
        return ResponseEntity.noContent().build()
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
