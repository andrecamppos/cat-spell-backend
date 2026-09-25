package com.catspell.api.moderation.event

import java.time.Instant
import java.util.UUID

/**
 * Domain event consumed by the async AFTER_COMMIT report notification listener. It carries only IDs
 * and precomputed strings (never the [com.catspell.api.moderation.model.Report] JPA entity) so the
 * async listener has no lazy-load dependency on a closed persistence context (RESEARCH Pitfall 2).
 */
data class ReportCreatedEvent(
    val reportId: UUID,
    val reporterId: UUID,
    val reportedId: UUID,
    val category: String,
    val details: String,
    val createdAt: Instant
)
