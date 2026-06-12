package com.catspell.api.cat.model

import com.catspell.api.auth.model.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "cat_profiles")
class CatProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false)
    var age: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "age_unit", nullable = false, length = 10)
    var ageUnit: AgeUnit,

    @Column(length = 100)
    var breed: String? = null,

    @Column(length = 500)
    var bio: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CatProfile) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
