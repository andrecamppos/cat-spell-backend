package com.catspell.api.email.service

data class EmailMessage(
    val to: String,
    val subject: String,
    val htmlBody: String,
    val textBody: String
)

enum class EmailSendStatus { SUCCESS, ERROR }

data class EmailResult(
    val status: EmailSendStatus,
    val messageId: String? = null,
    val errorDetail: String? = null
)

interface EmailSender {
    fun send(message: EmailMessage): EmailResult
}
