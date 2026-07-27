package com.catspell.api.push

import com.catspell.api.push.service.PushPayload
import com.catspell.api.push.service.PushProvider
import com.catspell.api.push.service.PushResult
import com.catspell.api.push.service.PushSendService
import com.catspell.api.push.service.PushSendStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PushProviderContractTest {

    @Test
    fun `send passes the expected payload shape to the provider`() {
        val provider = mockk<PushProvider>()
        val payloadSlot = slot<PushPayload>()
        every { provider.send(any(), capture(payloadSlot)) } returns PushResult(PushSendStatus.SUCCESS)

        val service = PushSendService(provider, mockk(relaxed = true))
        service.send("tok", PushPayload("Match!", "You have a new match", mapOf("type" to "match")))

        val captured = payloadSlot.captured
        assertEquals("Match!", captured.title)
        assertEquals("You have a new match", captured.body)
        assertEquals("match", captured.data["type"])
    }
}
