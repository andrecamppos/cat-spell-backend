package com.catspell.api.email.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["email.enabled"], havingValue = "false", matchIfMissing = true)
class LoggingEmailSender : EmailSender {

    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override fun send(message: EmailMessage): EmailResult {
        log.info("[no-op email] to={} subject='{}'", maskEmail(message.to), message.subject)
        return EmailResult(EmailSendStatus.SUCCESS, messageId = "logged")
    }

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return "***"
        val local = email.take(at)
        val domain = email.substring(at)
        val maskedLocal = if (local.length <= 2) "***" else "${local.take(1)}***${local.takeLast(1)}"
        return "$maskedLocal$domain"
    }
}
