package com.catspell.api.push.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceTokenRepository : JpaRepository<DeviceToken, UUID> {
    fun findByUserIdAndDeviceId(userId: UUID, deviceId: String): DeviceToken?
    fun findAllByUserIdAndActiveTrue(userId: UUID): List<DeviceToken>
    fun findByToken(token: String): DeviceToken?
}
