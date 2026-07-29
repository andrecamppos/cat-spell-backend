package com.catspell.api.push.event

import com.catspell.api.push.service.PushNotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Consumes push domain events asynchronously and only AFTER the publishing transaction commits
 * (D-07, PUSH-10). `@Async` (enabled by `@EnableAsync` on CatSpellApplication) moves FCM I/O off the
 * request/persistence thread; each handler swallows and logs exceptions so a slow or failing push
 * never propagates back to — or rolls back — the domain write.
 */
@Component
class PushNotificationListener(
    private val pushNotificationService: PushNotificationService
) {

    private val log = LoggerFactory.getLogger(PushNotificationListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMatchCreated(event: MatchCreatedEvent) {
        try {
            pushNotificationService.notifyMatch(listOf(event.userId1, event.userId2), event.matchId)
        } catch (e: Exception) {
            log.warn("Match push dispatch failed for match {}: {}", event.matchId, e.message)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMessageSent(event: MessageSentEvent) {
        try {
            pushNotificationService.notifyMessage(
                event.recipientId,
                event.conversationId,
                event.messageId,
                event.senderId,
                event.senderName,
                event.preview
            )
        } catch (e: Exception) {
            log.warn("Message push dispatch failed for message {}: {}", event.messageId, e.message)
        }
    }
}
