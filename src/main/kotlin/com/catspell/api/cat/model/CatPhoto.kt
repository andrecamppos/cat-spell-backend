package com.catspell.api.cat.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "cat_photos")
class CatPhoto(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_profile_id", nullable = false)
    var catProfile: CatProfile,

    @Column(name = "s3_key", nullable = false, length = 500)
    var s3Key: String,

    @Column(name = "thumbnail_s3_key", length = 500)
    var thumbnailS3Key: String? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int,

    @Column(name = "content_type", nullable = false, length = 50)
    var contentType: String,

    @Column(name = "file_size_bytes", nullable = false)
    var fileSizeBytes: Long = 0,

    @Column(nullable = false, length = 20)
    var status: String = "PENDING",

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CatPhoto) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
