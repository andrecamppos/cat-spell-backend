package com.catspell.api.push.service

import com.catspell.api.push.model.DeviceTokenRepository
import com.catspell.api.push.presence.PresenceRegistry
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Core Phase 9 send-decision + fan-out logic. Turns a "match created" / "message sent" fact plus
 * presence state into zero-or-more [PushSendService.send] calls with the correct payloads.
 *
 * - Match (D-02): push every matched user with NO live STOMP session; suppress online users.
 * - Message (D-04): send when the recipient is offline OR online-but-not-viewing that conversation.
 * - Message payload (D-01/D-05/D-06): sender name + preview, deep-link data, collapseKey = conversationId.
 */
@Service
class PushNotificationService(
    private val presenceRegistry: PresenceRegistry,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSendService: PushSendService
) {

    fun notifyMatch(userIds: List<UUID>, matchId: UUID) {
        val payload = PushPayload(
            title = "It's a match!",
            body = "You have a new match on Cat Spell",
            data = mapOf("type" to "match", "matchId" to matchId.toString())
        )
        userIds
            .filterNot { presenceRegistry.isOnline(it) } // D-02: suppress online matched users
            .forEach { pushToAllDevices(it, payload) }
    }

    fun notifyMessage(
        recipientId: UUID,
        conversationId: UUID,
        messageId: UUID,
        senderId: UUID,
        senderName: String,
        preview: String
    ) {
        // D-04: send only when offline, or online but not currently viewing this conversation.
        val shouldSend = !presenceRegistry.isOnline(recipientId) ||
            !presenceRegistry.isViewingConversation(recipientId, conversationId)
        if (!shouldSend) return

        val payload = PushPayload(
            title = senderName,
            body = preview,
            data = mapOf(
                "type" to "message",
                "conversationId" to conversationId.toString(),
                "messageId" to messageId.toString(),
                "senderId" to senderId.toString()
            ),
            collapseKey = conversationId.toString() // D-05/D-06: collapse per conversation
        )
        pushToAllDevices(recipientId, payload)
    }

    private fun pushToAllDevices(userId: UUID, payload: PushPayload) {
        deviceTokenRepository.findAllByUserIdAndActiveTrue(userId).forEach { device ->
            // PushSendService already prunes UNREGISTERED tokens; do not re-implement here.
            pushSendService.send(device.token, payload)
        }
    }
}
