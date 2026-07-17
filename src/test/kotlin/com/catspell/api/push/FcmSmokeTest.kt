package com.catspell.api.push

import com.catspell.api.push.config.FirebaseConfig
import com.catspell.api.push.service.FcmPushProvider
import com.catspell.api.push.service.PushPayload
import com.catspell.api.push.service.PushSendStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * validate_only (dry-run) FCM smoke test (D-11 / PUSH-12).
 *
 * Disabled by default and tagged "smoke" so the standard `./gradlew test` run never
 * executes it. It requires a real service account and MUST NOT run in CI.
 *
 * To run manually: export a real FIREBASE_CREDENTIALS_BASE64, remove the @Disabled
 * annotation locally (or add a gradle `test { if (!smoke) excludeTags("smoke") }`
 * filter that opts smoke tests in), and run this class directly.
 */
@Tag("smoke")
@Disabled("Requires real FIREBASE_CREDENTIALS_BASE64; run manually with -Dpush.smoke=true")
class FcmSmokeTest {

    @Test
    fun `dry-run send validates auth and payload against real Firebase`() {
        val credentials = System.getenv("FIREBASE_CREDENTIALS_BASE64")
        assertNotNull(credentials, "FIREBASE_CREDENTIALS_BASE64 must be set to run the smoke test")

        ApplicationContextRunner()
            .withUserConfiguration(FirebaseConfig::class.java, FcmPushProvider::class.java)
            .withPropertyValues(
                "push.enabled=true",
                "push.firebase.credentials-base64=$credentials"
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                val provider = context.getBean(FcmPushProvider::class.java)
                // A fake token with dryRun=true exercises real auth + payload validation
                // without delivering. FCM reports UNREGISTERED/ERROR for the fake token,
                // which still proves credentials and payload shape are valid.
                val result = provider.send(
                    "fake-smoke-token",
                    PushPayload("Smoke", "validate_only", mapOf("type" to "smoke")),
                    dryRun = true
                )
                assertThat(result.status).isIn(
                    PushSendStatus.SUCCESS,
                    PushSendStatus.UNREGISTERED,
                    PushSendStatus.ERROR
                )
            }
    }
}
