package com.catspell.api.invite.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "referrals")
class Referral(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    // Raw UUID columns — FK integrity is enforced in the V23 DDL, not via @ManyToOne.
    @Column(name = "referrer_id", nullable = false)
    var referrerId: UUID,

    @Column(name = "invitee_id", nullable = false)
    var inviteeId: UUID,

    @Column(name = "invite_id", nullable = false)
    var inviteId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Referral) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
