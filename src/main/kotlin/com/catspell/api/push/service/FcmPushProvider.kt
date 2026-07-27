package com.catspell.api.push.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["push.enabled"], havingValue = "true")
class FcmPushProvider(
    private val firebaseMessaging: FirebaseMessaging
) : PushProvider {

    private val log = LoggerFactory.getLogger(FcmPushProvider::class.java)

    override fun send(token: String, payload: PushPayload): PushResult =
        send(token, payload, dryRun = false)

    fun send(token: String, payload: PushPayload, dryRun: Boolean): PushResult {
        val message = buildMessage(token, payload)
        return try {
            val messageId = firebaseMessaging.send(message, dryRun)
            PushResult(PushSendStatus.SUCCESS, messageId = messageId)
        } catch (e: FirebaseMessagingException) {
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) {
                PushResult(PushSendStatus.UNREGISTERED, errorDetail = e.message)
            } else {
                log.warn("FCM send failed: {}", e.message)
                PushResult(PushSendStatus.ERROR, errorDetail = e.message)
            }
        }
    }

    private fun buildMessage(token: String, payload: PushPayload): Message =
        Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(payload.title)
                    .setBody(payload.body)
                    .build()
            )
            .putAllData(payload.data)
            .build()
}
