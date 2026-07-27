package com.catspell.api.push

import com.catspell.api.push.config.FirebaseConfig
import com.catspell.api.push.service.FcmPushProvider
import com.catspell.api.push.service.LoggingPushProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class PushProviderSelectionTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(
            LoggingPushProvider::class.java,
            FcmPushProvider::class.java,
            FirebaseConfig::class.java
        )

    @Test
    fun `logging provider selected when push disabled or missing`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(LoggingPushProvider::class.java)
            assertThat(context).doesNotHaveBean(FcmPushProvider::class.java)
        }
    }

    @Test
    fun `context fails fast when push enabled and credentials blank`() {
        runner.withPropertyValues("push.enabled=true").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .rootCause()
                .hasMessageContaining("FIREBASE_CREDENTIALS_BASE64")
        }
    }
}
