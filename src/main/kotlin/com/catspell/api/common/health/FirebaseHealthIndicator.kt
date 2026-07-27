package com.catspell.api.common.health

import com.google.firebase.FirebaseApp
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

@Component
class FirebaseHealthIndicator(
    @Value("\${push.enabled:false}") private val pushEnabled: Boolean,
    private val firebaseApp: ObjectProvider<FirebaseApp>
) : HealthIndicator {

    override fun health(): Health {
        if (!pushEnabled) {
            return Health.up().withDetail("push", "disabled").build()
        }
        return if (firebaseApp.ifAvailable != null) {
            Health.up().withDetail("push", "enabled").build()
        } else {
            Health.down().withDetail("push", "enabled-but-uninitialized").build()
        }
    }
}
