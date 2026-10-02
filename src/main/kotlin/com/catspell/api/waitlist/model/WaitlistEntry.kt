package com.catspell.api.waitlist.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "waitlist_entries")
class WaitlistEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    // Delivery address: trimmed, case preserved, overwritten on a PENDING re-join.
    @Column(name = "email", nullable = false, length = 255)
    var email: String,

    // D-03 dedupe + per-email bucket key. unique = true makes the create-drop test schema build the
    // unique index that ON CONFLICT (normalized_email) needs (RESEARCH Pitfall 6).
    @Column(name = "normalized_email", nullable = false, unique = true, length = 255)
    var normalizedEmail: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: WaitlistStatus = WaitlistStatus.PENDING,

    // SHA-256 hex of the confirm token; the raw token is never persisted (D-08).
    @Column(name = "confirm_token_hash", unique = true, length = 64)
    var confirmTokenHash: String? = null,

    @Column(name = "confirm_token_expires_at")
    var confirmTokenExpiresAt: Instant? = null,

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,

    @Column(name = "invited_at")
    var invitedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaitlistEntry) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
