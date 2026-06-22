package com.catspell.api.discovery.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SwipeRepository : JpaRepository<Swipe, UUID> {
    fun existsBySwiperIdAndCatProfileId(swiperId: UUID, catProfileId: UUID): Boolean

    fun existsBySwiperIdAndTargetUserIdAndCatProfileIsNull(swiperId: UUID, targetUserId: UUID): Boolean

    @Query("SELECT s FROM Swipe s WHERE s.swiper.id = :swiperId AND s.targetUser.id = :targetUserId AND s.action = :action")
    fun findBySwiperIdAndTargetUserIdAndAction(
        @Param("swiperId") swiperId: UUID,
        @Param("targetUserId") targetUserId: UUID,
        @Param("action") action: String
    ): List<Swipe>

    @Query(
        value = """
            SELECT 'CAT' AS type,
                   cp.id AS cat_id, cp.name AS cat_name, cp.age AS cat_age, cp.age_unit AS cat_age_unit,
                   cp.breed AS cat_breed, cp.bio AS cat_bio,
                   cp.user_id AS user_id,
                   up.display_name AS display_name,
                   (SELECT cph.thumbnail_s3_key FROM cat_photos cph
                    WHERE cph.cat_profile_id = cp.id AND cph.status = 'ACTIVE'
                    ORDER BY cph.display_order ASC LIMIT 1) AS cat_photo_thumbnail,
                   (SELECT uph.thumbnail_s3_key FROM user_photos uph
                    WHERE uph.user_id = cp.user_id AND uph.status = 'ACTIVE'
                    ORDER BY uph.display_order ASC LIMIT 1) AS user_photo_thumbnail,
                   CAST(ROUND(ST_Distance(up.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000) AS INTEGER) AS distance_km
            FROM cat_profiles cp
            JOIN user_profiles up ON up.user_id = cp.user_id
            JOIN user_profiles requester ON requester.user_id = :requesterId
            WHERE cp.user_id != :requesterId
              AND ST_DWithin(up.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :maxDistanceMeters)
              AND (requester.gender_preference = 'EVERYONE' OR requester.gender_preference = up.gender)
              AND (up.gender_preference = 'EVERYONE' OR up.gender_preference = requester.gender)
              AND EXTRACT(YEAR FROM AGE(CURRENT_DATE, up.date_of_birth)) BETWEEN requester.age_min AND requester.age_max
              AND EXTRACT(YEAR FROM AGE(CURRENT_DATE, requester.date_of_birth)) BETWEEN up.age_min AND up.age_max
              AND up.display_name IS NOT NULL AND up.bio IS NOT NULL
              AND up.date_of_birth IS NOT NULL AND up.gender IS NOT NULL
              AND up.location IS NOT NULL
              AND EXISTS (SELECT 1 FROM user_photos uph2 WHERE uph2.user_id = cp.user_id AND uph2.status = 'ACTIVE')
              AND EXISTS (SELECT 1 FROM cat_photos cph2 WHERE cph2.cat_profile_id = cp.id AND cph2.status = 'ACTIVE')
              AND NOT EXISTS (SELECT 1 FROM swipes s WHERE s.swiper_id = :requesterId AND s.target_user_id = cp.user_id)
            ORDER BY random()
            LIMIT :pageSize OFFSET :offset
        """,
        nativeQuery = true
    )
    fun findDiscoveryFeed(
        @Param("requesterId") requesterId: UUID,
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("maxDistanceMeters") maxDistanceMeters: Double,
        @Param("pageSize") pageSize: Int,
        @Param("offset") offset: Int
    ): List<FeedProjection>
}
