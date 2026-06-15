package com.catspell.api.match.model

import java.time.Instant
import java.util.UUID

data class MatchResponse(
    val matchId: UUID,
    val matchedAt: Instant,
    val otherUser: MatchUserSummary,
    val otherUserCats: List<MatchCatSummary>
)

data class MatchUserSummary(
    val userId: UUID,
    val displayName: String,
    val photoThumbnail: String?
)

data class MatchCatSummary(
    val name: String,
    val photoThumbnail: String?
)

data class MatchListResponse(
    val matches: List<MatchResponse>
)
