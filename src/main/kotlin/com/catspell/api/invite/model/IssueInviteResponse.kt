package com.catspell.api.invite.model

// The raw invite code, returned exactly once at issuance and never persisted raw (D-06).
data class IssueInviteResponse(val code: String)
