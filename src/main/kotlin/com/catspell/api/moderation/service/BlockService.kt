package com.catspell.api.moderation.service

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.common.exception.SelfBlockException
import com.catspell.api.match.service.MatchService
import com.catspell.api.moderation.model.Block
import com.catspell.api.moderation.model.BlockListResponse
import com.catspell.api.moderation.model.BlockRepository
import com.catspell.api.moderation.model.BlockedUserResponse
import com.catspell.api.profile.model.UserPhotoRepository
import com.catspell.api.profile.model.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BlockService(
    private val blockRepository: BlockRepository,
    private val matchService: MatchService,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userPhotoRepository: UserPhotoRepository
) {

    /**
     * Block a target for the caller. Rejects self-block (D-12), is idempotent (re-block of an
     * already-blocked target is a no-op success, D-12), and on a fresh block runs the shared
     * teardown so block ⊇ unmatch — the pair's match is ended and swipe history cleared (D-11).
     */
    @Transactional
    fun block(blockerId: UUID, blockedId: UUID) {
        if (blockerId == blockedId) throw SelfBlockException()
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) return
        blockRepository.save(
            Block(
                blocker = userRepository.getReferenceById(blockerId),
                blocked = userRepository.getReferenceById(blockedId)
            )
        )
        matchService.endMatch(blockerId, blockedId, "BLOCK")
    }

    /**
     * Unblock: delete ONLY the caller's own directional block row (IDOR-scoped, idempotent).
     * Performs no auto-rematch — swipe history was cleared on block, so re-matching requires a
     * fresh mutual swipe (D-08). Removing the row re-enables rediscovery via the feed filter.
     */
    @Transactional
    fun unblock(blockerId: UUID, blockedId: UUID) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId)
    }

    /**
     * The caller's own block list, newest first, as minimal-identity entries (D-13).
     */
    @Transactional(readOnly = true)
    fun getBlockList(blockerId: UUID): BlockListResponse {
        val entries = blockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId).map { block ->
            val blockedId = block.blocked.id!!
            val profile = userProfileRepository.findByUserId(blockedId)
            val photoThumbnail = userPhotoRepository.findByUserIdOrderByDisplayOrderAsc(blockedId)
                .firstOrNull { it.status == "ACTIVE" }
                ?.thumbnailS3Key
            BlockedUserResponse(
                userId = blockedId,
                displayName = profile?.displayName ?: "Unknown",
                photoThumbnail = photoThumbnail,
                blockedAt = block.createdAt
            )
        }
        return BlockListResponse(blocks = entries)
    }

    /**
     * Synchronous, strongly-consistent bidirectional block predicate for imperative read/send
     * checks (D-03). No caching.
     */
    @Transactional(readOnly = true)
    fun isBlockedEitherWay(a: UUID, b: UUID): Boolean = blockRepository.existsBlockBetween(a, b)
}
