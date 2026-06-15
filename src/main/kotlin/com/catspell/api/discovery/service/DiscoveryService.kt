package com.catspell.api.discovery.service

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.cat.model.CatProfileRepository
import com.catspell.api.common.exception.DuplicateSwipeException
import com.catspell.api.common.exception.LocationRequiredException
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.common.exception.SelfSwipeException
import com.catspell.api.discovery.model.*
import com.catspell.api.match.service.MatchService
import com.catspell.api.profile.model.UserProfileRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Base64
import java.util.UUID

@Service
class DiscoveryService(
    private val swipeRepository: SwipeRepository,
    private val catProfileRepository: CatProfileRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userRepository: UserRepository,
    private val matchService: MatchService,
    private val entityManager: EntityManager
) {

    @Transactional(readOnly = true)
    fun getFeed(userId: UUID, cursor: String?, pageSize: Int): FeedResponse {
        val profile = userProfileRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Profile not found")

        val location = profile.location
            ?: throw LocationRequiredException()

        val seed: Double
        val offset: Int

        if (cursor != null) {
            val decoded = String(Base64.getDecoder().decode(cursor))
            val parts = decoded.split(",")
            seed = parts[0].toDouble()
            offset = parts[1].toInt()
        } else {
            seed = Math.random()
            offset = 0
        }

        val effectivePageSize = pageSize.coerceIn(1, 50)
        val maxDistanceMeters = profile.maxDistanceKm.toDouble() * 1000

        entityManager.createNativeQuery("SELECT setseed(:seed)")
            .setParameter("seed", seed)
            .singleResult

        val results = swipeRepository.findDiscoveryFeed(
            requesterId = userId,
            lat = location.y,
            lng = location.x,
            maxDistanceMeters = maxDistanceMeters,
            pageSize = effectivePageSize + 1,
            offset = offset
        )

        val hasMore = results.size > effectivePageSize
        val items = results.take(effectivePageSize)

        val cats = items.map { proj ->
            FeedItemResponse(
                catId = proj.getCatId(),
                name = proj.getName(),
                age = proj.getAge(),
                ageUnit = proj.getAgeUnit(),
                breed = proj.getBreed(),
                bio = proj.getBio(),
                ownerId = proj.getOwnerId(),
                ownerDisplayName = proj.getOwnerDisplayName(),
                catPhotoThumbnail = proj.getCatPhotoThumbnail(),
                ownerPhotoThumbnail = proj.getOwnerPhotoThumbnail(),
                distanceKm = proj.getDistanceKm()
            )
        }

        val nextCursor = if (hasMore) {
            CursorResponse(
                seed = seed,
                offset = offset + effectivePageSize,
                hasMore = true
            )
        } else null

        return FeedResponse(cats = cats, cursor = nextCursor)
    }

    @Transactional
    fun swipe(userId: UUID, request: SwipeRequest): SwipeResponse {
        val cat = catProfileRepository.findById(request.catId)
            .orElseThrow { ResourceNotFoundException("Cat not found") }

        val catOwnerId = cat.user.id!!

        if (catOwnerId == userId) {
            throw SelfSwipeException()
        }

        if (swipeRepository.existsBySwiperIdAndCatProfileId(userId, request.catId)) {
            throw DuplicateSwipeException()
        }

        val swiper = userRepository.getReferenceById(userId)
        val targetUser = userRepository.getReferenceById(catOwnerId)

        val swipe = Swipe(
            swiper = swiper,
            catProfile = cat,
            targetUser = targetUser,
            action = request.action
        )
        val savedSwipe = swipeRepository.save(swipe)

        if (request.action == "LIKE") {
            val reverseLikes = swipeRepository.findBySwiperIdAndTargetUserIdAndAction(
                swiperId = catOwnerId,
                targetUserId = userId,
                action = "LIKE"
            )
            if (reverseLikes.isNotEmpty()) {
                val match = matchService.createMatch(userId, catOwnerId)
                if (match != null) {
                    return SwipeResponse(
                        swipeId = savedSwipe.id!!,
                        matched = true,
                        matchId = match.id
                    )
                }
            }
        }

        return SwipeResponse(
            swipeId = savedSwipe.id!!,
            matched = false
        )
    }
}
