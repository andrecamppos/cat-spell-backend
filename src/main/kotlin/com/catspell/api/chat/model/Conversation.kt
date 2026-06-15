package com.catspell.api.chat.model

import com.catspell.api.match.model.Match
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "conversations")
class Conversation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    var match: Match,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_message_at")
    var lastMessageAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Conversation) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
