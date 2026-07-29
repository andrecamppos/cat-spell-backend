package com.catspell.api.match.service

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.cat.model.CatPhotoRepository
import com.catspell.api.cat.model.CatProfileRepository
import com.catspell.api.match.model.*
import com.catspell.api.profile.model.UserPhotoRepository
import com.catspell.api.profile.model.UserProfileRepository
import com.catspell.api.push.event.MatchCreatedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MatchService(
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userPhotoRepository: UserPhotoRepository,
    private val catProfileRepository: CatProfileRepository,
    private val catPhotoRepository: CatPhotoRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun createMatch(userId1: UUID, userId2: UUID): Match? {
        val u1 = if (userId1 < userId2) userId1 else userId2
        val u2 = if (userId1 < userId2) userId2 else userId1

        val existing = matchRepository.findByUserPair(u1, u2)
        if (existing != null) return existing

        val user1 = userRepository.getReferenceById(u1)
        val user2 = userRepository.getReferenceById(u2)

        return try {
            val match = matchRepository.save(Match(user1 = user1, user2 = user2))
            // Publish only on a genuinely new match (not the existing-match return or the
            // duplicate-key fallback) to avoid duplicate notifications (T-9-08).
            eventPublisher.publishEvent(MatchCreatedEvent(match.id!!, u1, u2))
            match
        } catch (e: DataIntegrityViolationException) {
            matchRepository.findByUserPair(u1, u2)
        }
    }

    fun findExistingMatch(userId1: UUID, userId2: UUID): Match? {
        val u1 = if (userId1 < userId2) userId1 else userId2
        val u2 = if (userId1 < userId2) userId2 else userId1
        return matchRepository.findByUserPair(u1, u2)
    }

    @Transactional(readOnly = true)
    fun getMatches(userId: UUID): MatchListResponse {
        val matches = matchRepository.findByUser1IdOrUser2Id(userId)

        val responses = matches.map { match ->
            val otherId = if (match.user1.id == userId) match.user2.id!! else match.user1.id!!

            val otherProfile = userProfileRepository.findByUserId(otherId)
            val otherPhotoThumbnail = userPhotoRepository.findByUserIdOrderByDisplayOrderAsc(otherId)
                .firstOrNull { it.status == "ACTIVE" }
                ?.thumbnailS3Key

            val otherCats = catProfileRepository.findByUserId(otherId).map { cp ->
                val catPhoto = catPhotoRepository.findByCatProfileIdOrderByDisplayOrderAsc(cp.id!!)
                    .firstOrNull { it.status == "ACTIVE" }
                MatchCatSummary(
                    name = cp.name,
                    photoThumbnail = catPhoto?.thumbnailS3Key
                )
            }

            MatchResponse(
                matchId = match.id!!,
                matchedAt = match.matchedAt,
                otherUser = MatchUserSummary(
                    userId = otherId,
                    displayName = otherProfile?.displayName ?: "Unknown",
                    photoThumbnail = otherPhotoThumbnail
                ),
                otherUserCats = otherCats
            )
        }

        return MatchListResponse(matches = responses)
    }
}
