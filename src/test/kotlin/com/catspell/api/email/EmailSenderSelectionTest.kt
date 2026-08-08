package com.catspell.api.email

import com.catspell.api.email.service.LoggingEmailSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class EmailSenderSelectionTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(LoggingEmailSender::class.java)

    @Test
    fun `logging sender selected when email disabled or missing`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(LoggingEmailSender::class.java)
        }
    }

    @Test
    fun `logging sender not selected when email enabled`() {
        runner.withPropertyValues("email.enabled=true").run { context ->
            assertThat(context).doesNotHaveBean(LoggingEmailSender::class.java)
        }
    }
}
