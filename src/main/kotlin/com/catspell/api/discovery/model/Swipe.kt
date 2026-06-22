package com.catspell.api.discovery.model

import com.catspell.api.auth.model.User
import com.catspell.api.cat.model.CatProfile
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "swipes")
class Swipe(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "swiper_id", nullable = false)
    var swiper: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = true)
    var catProfile: CatProfile? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    var targetUser: User,

    @Column(nullable = false, length = 10)
    var action: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Swipe) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
