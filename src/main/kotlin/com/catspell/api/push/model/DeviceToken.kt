package com.catspell.api.push.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class Platform { ANDROID, IOS }

@Entity
@Table(name = "device_tokens")
class DeviceToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "device_id", nullable = false)
    var deviceId: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var token: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    var platform: Platform,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "deactivated_at")
    var deactivatedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceToken) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
