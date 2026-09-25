package com.catspell.api.moderation.model

import java.time.Instant
import java.util.UUID

data class BlockedUserResponse(
    val userId: UUID,
    val displayName: String,
    val photoThumbnail: String?,
    val blockedAt: Instant
)

data class BlockListResponse(
    val blocks: List<BlockedUserResponse>
)
