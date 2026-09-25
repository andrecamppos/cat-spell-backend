package com.catspell.api.moderation.controller

import com.catspell.api.moderation.model.BlockListResponse
import com.catspell.api.moderation.service.BlockService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/blocks")
class BlockController(
    private val blockService: BlockService
) {

    @PostMapping("/{targetUserId}")
    fun block(@PathVariable targetUserId: UUID): ResponseEntity<Void> {
        blockService.block(extractUserId(), targetUserId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{targetUserId}")
    fun unblock(@PathVariable targetUserId: UUID): ResponseEntity<Void> {
        blockService.unblock(extractUserId(), targetUserId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun list(): ResponseEntity<BlockListResponse> {
        return ResponseEntity.ok(blockService.getBlockList(extractUserId()))
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
