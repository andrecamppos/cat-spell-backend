package com.catspell.api.profile.model

import com.catspell.api.auth.model.User
import jakarta.persistence.*
import org.locationtech.jts.geom.Point
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "user_profiles")
class UserProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User,

    @Column(name = "display_name", nullable = false, length = 100)
    var displayName: String,

    @Column(length = 1000)
    var bio: String? = null,

    @Column(name = "date_of_birth", nullable = false)
    var dateOfBirth: LocalDate,

    @Column(nullable = false, length = 20)
    var gender: String,

    @Column(name = "gender_preference", nullable = false, length = 20)
    var genderPreference: String,

    @Column(name = "age_min", nullable = false)
    var ageMin: Int,

    @Column(name = "age_max", nullable = false)
    var ageMax: Int,

    @Column(name = "max_distance_km", nullable = false)
    var maxDistanceKm: Int,

    @Column(columnDefinition = "geometry(Point,4326)")
    var location: Point? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserProfile) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
