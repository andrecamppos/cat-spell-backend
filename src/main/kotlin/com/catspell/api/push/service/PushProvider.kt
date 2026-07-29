package com.catspell.api.push.service

data class PushPayload(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val collapseKey: String? = null
)

enum class PushSendStatus { SUCCESS, UNREGISTERED, ERROR }

data class PushResult(
    val status: PushSendStatus,
    val messageId: String? = null,
    val errorDetail: String? = null
)

interface PushProvider {
    fun send(token: String, payload: PushPayload): PushResult
}
