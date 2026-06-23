package com.catspell.api.discovery.controller

import com.catspell.api.discovery.model.FeedResponse
import com.catspell.api.discovery.model.OwnerProfileResponse
import com.catspell.api.discovery.model.SwipeRequest
import com.catspell.api.discovery.model.SwipeResponse
import com.catspell.api.discovery.service.DiscoveryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/discovery")
class DiscoveryController(
    private val discoveryService: DiscoveryService
) {

    @GetMapping("/feed")
    fun getFeed(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<FeedResponse> {
        val userId = extractUserId()
        val response = discoveryService.getFeed(userId, cursor, pageSize)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/cats/{catId}/owner")
    fun getOwnerProfile(@PathVariable catId: UUID): ResponseEntity<OwnerProfileResponse> {
        val userId = extractUserId()
        val response = discoveryService.getOwnerProfile(userId, catId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/users/{userId}/profile")
    fun getUserProfile(@PathVariable userId: UUID): ResponseEntity<OwnerProfileResponse> {
        val requesterId = extractUserId()
        val response = discoveryService.getUserProfile(requesterId, userId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/swipe")
    fun swipe(@Valid @RequestBody request: SwipeRequest): ResponseEntity<SwipeResponse> {
        val userId = extractUserId()
        val response = discoveryService.swipe(userId, request)
        return ResponseEntity.ok(response)
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
