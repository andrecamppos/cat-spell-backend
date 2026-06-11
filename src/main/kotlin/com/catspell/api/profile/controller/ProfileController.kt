package com.catspell.api.profile.controller

import com.catspell.api.profile.model.CreateProfileRequest
import com.catspell.api.profile.model.ProfileResponse
import com.catspell.api.profile.model.UpdateLocationRequest
import com.catspell.api.profile.model.UpdateProfileRequest
import com.catspell.api.profile.service.ProfileService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/profile")
class ProfileController(
    private val profileService: ProfileService
) {

    @PostMapping
    fun createProfile(@Valid @RequestBody request: CreateProfileRequest): ResponseEntity<ProfileResponse> {
        val userId = extractUserId()
        val response = profileService.createProfile(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun getProfile(): ResponseEntity<ProfileResponse> {
        val userId = extractUserId()
        val response = profileService.getProfile(userId)
        return ResponseEntity.ok(response)
    }

    @PutMapping
    fun updateProfile(@Valid @RequestBody request: UpdateProfileRequest): ResponseEntity<ProfileResponse> {
        val userId = extractUserId()
        val response = profileService.updateProfile(userId, request)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/location")
    fun updateLocation(@Valid @RequestBody request: UpdateLocationRequest): ResponseEntity<ProfileResponse> {
        val userId = extractUserId()
        val response = profileService.updateLocation(userId, request)
        return ResponseEntity.ok(response)
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
