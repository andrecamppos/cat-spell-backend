package com.catspell.api.moderation.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class ReportRequest(
    val reportedUserId: UUID,

    val category: ReportCategory,

    @field:NotBlank(message = "details is required")
    @field:Size(max = 1000, message = "details must be at most 1000 characters")
    val details: String,

    val alsoBlock: Boolean = false
)
