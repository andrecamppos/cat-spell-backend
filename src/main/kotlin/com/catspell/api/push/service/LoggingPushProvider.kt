package com.catspell.api.push.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["push.enabled"], havingValue = "false", matchIfMissing = true)
class LoggingPushProvider : PushProvider {

    private val log = LoggerFactory.getLogger(LoggingPushProvider::class.java)

    override fun send(token: String, payload: PushPayload): PushResult {
        log.info("[no-op push] token={} title='{}'", maskToken(token), payload.title)
        return PushResult(PushSendStatus.SUCCESS, messageId = "logged")
    }

    private fun maskToken(token: String): String =
        if (token.length <= 8) "***" else "${token.take(6)}...${token.takeLast(4)}"
}
