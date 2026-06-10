package com.catspell.api.auth.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false, unique = true)
    var token: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column(name = "replaced_by")
    var replacedBy: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RefreshToken) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
