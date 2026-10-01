package com.catspell.api.invite.model

import java.util.UUID

// Nullable referrer — operator/bootstrap codes omit it (NO @NotBlank/@NotNull). D-11.
data class IssueInviteRequest(val referrerUserId: UUID? = null)
