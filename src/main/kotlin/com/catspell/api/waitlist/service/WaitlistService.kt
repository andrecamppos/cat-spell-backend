package com.catspell.api.waitlist.service

import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.common.exception.WaitlistEntryNotConvertibleException
import com.catspell.api.common.exception.WaitlistInviteDeliveryException
import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.EmailSender
import com.catspell.api.email.service.WaitlistInviteEmailRenderer
import com.catspell.api.invite.service.InviteService
import com.catspell.api.waitlist.event.WaitlistConfirmationRequestedEvent
import com.catspell.api.waitlist.model.WaitlistEntryRepository
import com.catspell.api.waitlist.model.WaitlistEntryResponse
import com.catspell.api.waitlist.model.WaitlistStatus
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class WaitlistService(
    private val waitlistEntryRepository: WaitlistEntryRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val inviteService: InviteService,
    private val emailSender: EmailSender,
    private val waitlistInviteEmailRenderer: WaitlistInviteEmailRenderer,
    @Value("\${app.waitlist.confirm-token-ttl-hours:168}") private val confirmTokenTtlHours: Long,
    @Value("\${app.waitlist.per-email-capacity:3}") private val perEmailCapacity: Long,
    @Value("\${app.waitlist.per-email-refill-hours:1}") private val perEmailRefillHours: Long
) {

    private val secureRandom = SecureRandom()

    // One shared bucket per D-03 normalized email: computeIfAbsent + tryConsume caps the confirm tokens minted
    // per refill window regardless of request parallelism, and +suffix/case variants cannot reset it (D-07).
    private val emailBuckets = ConcurrentHashMap<String, Bucket>()

    private fun emailBucket(normalizedEmail: String): Bucket = emailBuckets.computeIfAbsent(normalizedEmail) {
        val bandwidth = Bandwidth.builder()
            .capacity(perEmailCapacity)
            .refillIntervally(perEmailCapacity, Duration.ofHours(perEmailRefillHours))
            .build()
        Bucket.builder().addLimit(bandwidth).build()
    }

    /**
     * Join the waitlist. Upserts a PENDING entry keyed by the D-03 normalized email (race-free via
     * ON CONFLICT DO NOTHING) and rotates its confirm token while the entry is still PENDING. CONFIRMED/INVITED
     * entries are left untouched (silent no-op, D-04). An exhausted per-email bucket returns silently before any
     * DB call (D-07). When the token was rotated, a [WaitlistConfirmationRequestedEvent] is published so the
     * confirmation email is sent after commit (WAIT-02). Never throws for duplicates or throttling, so the caller can
     * always answer with the same 202.
     * Neither the raw token nor the email is logged (D-08).
     */
    @Transactional
    fun join(email: String) {
        val trimmed = email.trim()
        val normalized = WaitlistEmailNormalizer.normalize(email)
        if (!emailBucket(normalized).tryConsume(1)) return
        val now = Instant.now()
        waitlistEntryRepository.insertIfAbsent(trimmed, normalized, now)
        val rawToken = generateRawToken()
        val rotated = waitlistEntryRepository.rotatePendingToken(
            normalized,
            trimmed,
            hashToken(rawToken),
            now.plus(confirmTokenTtlHours, ChronoUnit.HOURS),
            now,
            WaitlistStatus.PENDING
        )
        // Only a new/still-PENDING entry gets a link; CONFIRMED/INVITED re-joins stay silent (D-04). Published
        // inside this transaction so the AFTER_COMMIT listener fires only once the new hash is durable.
        if (rotated == 1) {
            eventPublisher.publishEvent(WaitlistConfirmationRequestedEvent(trimmed, rawToken))
        }
    }

    /**
     * Claim a confirm token exactly once (WAIT-02). A blank or missing token, or any claim that updates 0 rows
     * (unknown, expired, already used, rotated away), returns false; the caller maps that to the error redirect
     * (D-01, D-08). The token is never logged.
     */
    @Transactional
    fun confirm(rawToken: String?): Boolean {
        if (rawToken.isNullOrBlank()) return false
        return waitlistEntryRepository.claimConfirm(
            hashToken(rawToken),
            Instant.now(),
            WaitlistStatus.PENDING,
            WaitlistStatus.CONFIRMED
        ) == 1
    }

    /**
     * Operator list of entries in one state (D-09). [status] is matched case-insensitively against [WaitlistStatus]
     * after trimming; an unknown value or a [limit] outside 1..500 throws [IllegalArgumentException] (400). Results
     * are ordered by `confirmed_at` ascending and never include token material.
     */
    @Transactional(readOnly = true)
    fun listByStatus(status: String, limit: Int): List<WaitlistEntryResponse> {
        val requested = status.trim()
        val resolved = WaitlistStatus.entries.firstOrNull { it.name.equals(requested, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown waitlist status")
        if (limit !in 1..500) {
            throw IllegalArgumentException("limit must be between 1 and 500")
        }
        return waitlistEntryRepository
            .findByStatusOrderByConfirmedAtAscCreatedAtAsc(resolved, PageRequest.of(0, limit))
            .map { entry ->
                WaitlistEntryResponse(
                    id = entry.id!!,
                    email = entry.email,
                    status = entry.status,
                    createdAt = entry.createdAt,
                    confirmedAt = entry.confirmedAt,
                    invitedAt = entry.invitedAt
                )
            }
    }

    /**
     * Convert one CONFIRMED entry into an organic invite and email it (WAIT-04, D-10). In ONE transaction: claim
     * CONFIRMED→INVITED with a conditional UPDATE (single winner under concurrency), issue the invite through
     * [InviteService.create] with no referrer (REQUIRED joins this transaction), and send the email synchronously.
     * A send that throws or reports anything but SUCCESS raises [WaitlistInviteDeliveryException], which rolls the
     * claim and the invite back so the entry stays CONFIRMED and retryable. Returns the raw code exactly once.
     * Unknown id → [ResourceNotFoundException]; not CONFIRMED → [WaitlistEntryNotConvertibleException].
     * Neither the raw code nor the email is logged.
     */
    @Transactional
    fun convertToInvite(entryId: UUID): String {
        val entry = waitlistEntryRepository.findById(entryId).orElseThrow {
            ResourceNotFoundException("Waitlist entry not found")
        }
        // Read the email from the entity loaded BEFORE the bulk update; its status field is stale afterwards.
        val recipient = entry.email
        if (waitlistEntryRepository.markInvited(entryId, Instant.now(), WaitlistStatus.CONFIRMED, WaitlistStatus.INVITED) == 0) {
            throw WaitlistEntryNotConvertibleException()
        }
        val rawCode = inviteService.create(null)   // organic: never a referrer for waitlist conversions (D-10)
        val result = try {
            emailSender.send(waitlistInviteEmailRenderer.render(recipient, rawCode))
        } catch (ex: Exception) {
            throw WaitlistInviteDeliveryException()
        }
        if (result.status != EmailSendStatus.SUCCESS) {
            throw WaitlistInviteDeliveryException()
        }
        return rawCode
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(rawCode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rawCode.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }
}
