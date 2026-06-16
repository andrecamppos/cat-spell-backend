package com.catspell.api.common.health

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.stereotype.Component

@Component
class WebSocketHealthIndicator(
    private val userRegistry: SimpUserRegistry
) : HealthIndicator {

    override fun health(): Health {
        return Health.up()
            .withDetail("activeSessions", userRegistry.userCount)
            .build()
    }
}
