package com.catspell.api.push.service

import com.catspell.api.push.model.DeviceToken
import com.catspell.api.push.model.DeviceTokenRepository
import com.catspell.api.push.model.RegisterDeviceRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository
) {

    @Transactional
    fun register(userId: UUID, request: RegisterDeviceRequest) {
        val existing = deviceTokenRepository.findByUserIdAndDeviceId(userId, request.deviceId)
        val now = Instant.now()
        if (existing != null) {
            existing.token = request.token
            existing.platform = request.platform
            existing.active = true
            existing.deactivatedAt = null
            existing.updatedAt = now
            deviceTokenRepository.save(existing)
        } else {
            deviceTokenRepository.save(
                DeviceToken(
                    userId = userId,
                    deviceId = request.deviceId,
                    token = request.token,
                    platform = request.platform,
                    active = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    @Transactional
    fun unregister(userId: UUID, deviceId: String) {
        val existing = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId) ?: return
        if (existing.active) {
            existing.active = false
            existing.deactivatedAt = Instant.now()
            existing.updatedAt = Instant.now()
            deviceTokenRepository.save(existing)
        }
    }

    @Transactional
    fun deactivateToken(token: String) {
        val existing = deviceTokenRepository.findByToken(token) ?: return
        existing.active = false
        existing.deactivatedAt = Instant.now()
        existing.updatedAt = Instant.now()
        deviceTokenRepository.save(existing)
    }
}
