package com.catspell.api.invite.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invites")
class Invite(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    var codeHash: String,

    // Plain UUID column (NOT @ManyToOne) — the service only needs the id, and bootstrap/operator
    // codes leave this null (D-11).
    @Column(name = "referrer_user_id")
    var referrerUserId: UUID? = null,

    // NULL = unconsumed; set atomically by InviteRepository.markConsumed (the single-use claim, D-07).
    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,

    @Column(name = "consumed_by")
    var consumedBy: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Invite) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
