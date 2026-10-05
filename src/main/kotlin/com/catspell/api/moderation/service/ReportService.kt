package com.catspell.api.moderation.service

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.common.exception.SelfReportException
import com.catspell.api.common.ratelimit.RateLimitBuckets
import com.catspell.api.moderation.event.ReportCreatedEvent
import com.catspell.api.moderation.model.Report
import com.catspell.api.moderation.model.ReportCategory
import com.catspell.api.moderation.model.ReportRepository
import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.UUID

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val userRepository: UserRepository,
    private val blockService: BlockService,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${app.report.per-reporter-capacity:5}") private val perReporterCapacity: Long,
    @Value("\${app.report.per-reporter-refill-hours:1}") private val perReporterRefillHours: Long,
    @Value("\${rate-limit.max-tracked-keys:100000}") private val maxTrackedKeys: Long
) {

    // One bucket per reporter in a bounded, access-expiring store (WR-10), so reporter-key churn cannot grow memory.
    private val reporterBuckets = RateLimitBuckets(perReporterCapacity, Duration.ofHours(perReporterRefillHours), maxTrackedKeys)

    private fun reporterBucket(reporterId: UUID): Bucket = reporterBuckets.bucketFor(reporterId.toString())

    /**
     * Persist a report of [reportedId] by [reporterId]. Guard order (RESEARCH A5): self-report (400) →
     * target-exists (404) → per-reporter rate limit (429) — so an obviously-invalid self/nonexistent
     * request never consumes a rate-limit token. Every report persists (no dedupe, D-04). When
     * [alsoBlock] is true the block is composed atomically in the same transaction (D-09). The operator
     * notification is out-of-band: only a [ReportCreatedEvent] is published (delivered AFTER_COMMIT by
     * the notification listener) — no email is ever sent inside this transaction (D-02, MOD-07).
     */
    @Transactional
    fun report(reporterId: UUID, reportedId: UUID, category: ReportCategory, details: String, alsoBlock: Boolean): UUID {
        if (reporterId == reportedId) throw SelfReportException()
        if (!userRepository.existsById(reportedId)) throw ResourceNotFoundException("User not found")
        if (!reporterBucket(reporterId).tryConsume(1)) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many reports. Try again later.")
        }

        val saved = reportRepository.save(
            Report(
                reporter = userRepository.getReferenceById(reporterId),
                reported = userRepository.getReferenceById(reportedId),
                category = category,
                details = details
            )
        )

        if (alsoBlock) blockService.block(reporterId, reportedId)

        eventPublisher.publishEvent(
            ReportCreatedEvent(saved.id!!, reporterId, reportedId, category.name, details, saved.createdAt)
        )

        return saved.id!!
    }
}
