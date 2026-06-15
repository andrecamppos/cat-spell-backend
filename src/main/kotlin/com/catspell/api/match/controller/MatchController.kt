package com.catspell.api.match.controller

import com.catspell.api.match.model.MatchListResponse
import com.catspell.api.match.service.MatchService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/matches")
class MatchController(
    private val matchService: MatchService
) {

    @GetMapping
    fun getMatches(): ResponseEntity<MatchListResponse> {
        val userId = extractUserId()
        val response = matchService.getMatches(userId)
        return ResponseEntity.ok(response)
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
