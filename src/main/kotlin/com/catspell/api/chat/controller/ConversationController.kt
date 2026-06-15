package com.catspell.api.chat.controller

import com.catspell.api.chat.model.ConversationListResponse
import com.catspell.api.chat.model.MessagePageResponse
import com.catspell.api.chat.service.ChatService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/conversations")
class ConversationController(
    private val chatService: ChatService
) {

    @GetMapping
    fun getConversations(): ResponseEntity<ConversationListResponse> {
        val userId = extractUserId()
        val response = chatService.getConversations(userId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: UUID): ResponseEntity<Void> {
        val userId = extractUserId()
        chatService.markRead(userId, id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/messages")
    fun getMessages(
        @PathVariable id: UUID,
        @RequestParam(required = false) cursor: Instant?,
        @RequestParam(defaultValue = "30") size: Int
    ): ResponseEntity<MessagePageResponse> {
        val userId = extractUserId()
        val response = chatService.getMessages(userId, id, cursor, size)
        return ResponseEntity.ok(response)
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
