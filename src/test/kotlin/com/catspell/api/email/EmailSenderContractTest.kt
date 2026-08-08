package com.catspell.api.email

import com.catspell.api.email.service.EmailMessage
import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.LoggingEmailSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class EmailSenderContractTest {

    private val sender = LoggingEmailSender()

    private fun sampleMessage() = EmailMessage(
        to = "someone@example.com",
        subject = "Reset your Cat Spell password",
        htmlBody = "<p>link</p>",
        textBody = "link"
    )

    @Test
    fun `send returns SUCCESS without throwing`() {
        val result = sender.send(sampleMessage())

        assertThat(result.status).isEqualTo(EmailSendStatus.SUCCESS)
        assertThat(result.messageId).isEqualTo("logged")
    }

    @Test
    fun `concurrent sends each return SUCCESS`() {
        val first = CompletableFuture.supplyAsync { sender.send(sampleMessage()) }
        val second = CompletableFuture.supplyAsync { sender.send(sampleMessage()) }

        CompletableFuture.allOf(first, second).join()

        assertThat(first.get().status).isEqualTo(EmailSendStatus.SUCCESS)
        assertThat(second.get().status).isEqualTo(EmailSendStatus.SUCCESS)
    }
}
