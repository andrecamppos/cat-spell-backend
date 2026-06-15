package com.catspell.api.chat.controller

import com.catspell.api.chat.model.SendMessageRequest
import com.catspell.api.chat.service.ChatService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller
import java.security.Principal
import java.util.UUID

@Controller
class ChatController(
    private val chatService: ChatService
) {

    @MessageMapping("/chat.send")
    fun sendMessage(@Payload request: SendMessageRequest, principal: Principal) {
        val userId = UUID.fromString(principal.name)
        chatService.sendMessage(userId, request)
    }
}
