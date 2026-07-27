package com.catspell.api.push.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PushSendService(
    private val pushProvider: PushProvider,
    private val deviceTokenService: DeviceTokenService
) {

    private val log = LoggerFactory.getLogger(PushSendService::class.java)

    fun send(token: String, payload: PushPayload): PushResult {
        val result = pushProvider.send(token, payload)
        when (result.status) {
            PushSendStatus.UNREGISTERED -> deviceTokenService.deactivateToken(token)
            PushSendStatus.ERROR -> log.debug("Push send returned ERROR: {}", result.errorDetail)
            PushSendStatus.SUCCESS -> {}
        }
        return result
    }
}
