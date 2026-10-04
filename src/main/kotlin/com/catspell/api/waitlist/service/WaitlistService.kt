package com.catspell.api.waitlist.service

import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.common.exception.WaitlistEntryNotConvertibleException
import com.catspell.api.common.exception.WaitlistInviteDeliveryException
import com.catspell.api.common.ratelimit.RateLimitBuckets
import com.catspell.api.email.service.EmailMessage
import com.catspell.api.email.service.EmailResult
import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.EmailSender
import com.catspell.api.email.service.WaitlistInviteEmailRenderer
import com.catspell.api.invite.service.InviteService
import com.catspell.api.waitlist.event.WaitlistConfirmationRequestedEvent
import com.catspell.api.waitlist.model.WaitlistEntryRepository
import com.catspell.api.waitlist.model.WaitlistEntryResponse
import com.catspell.api.waitlist.model.WaitlistStatus
import io.github.bucket4j.Bucket
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
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
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

@Service
class WaitlistService(
    private val waitlistEntryRepository: WaitlistEntryRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val inviteService: InviteService,
    private val emailSender: EmailSender,
    private val waitlistInviteEmailRenderer: WaitlistInviteEmailRenderer,
    @Value("\${app.waitlist.confirm-token-ttl-hours:168}") private val confirmTokenTtlHours: Long,
    @Value("\${app.waitlist.per-email-capacity:3}") private val perEmailCapacity: Long,
    @Value("\${app.waitlist.per-email-refill-hours:24}") private val perEmailRefillHours: Long,
    @Value("\${app.waitlist.resend-cooldown-minutes:15}") private val resendCooldownMinutes: Long,
    @Value("\${rate-limit.max-tracked-keys:100000}") private val maxTrackedKeys: Long,
    @Value("\${app.waitlist.invite-send-timeout-ms:10000}") private val inviteSendTimeoutMs: Long
) {

    init {
        require(resendCooldownMinutes >= 0) { "app.waitlist.resend-cooldown-minutes must be >= 0, was $resendCooldownMinutes" }
        require(inviteSendTimeoutMs > 0) { "app.waitlist.invite-send-timeout-ms must be > 0, was $inviteSendTimeoutMs" }
    }

    private val log = LoggerFactory.getLogger(WaitlistService::class.java)

    // Small private pool that runs the invite send so convertToInvite can bound it with a timeout (D-10, WR-05).
    // Deliberately NOT a Spring bean: Spring Boot only auto-configures its applicationTaskExecutor when no Executor
    // bean exists, so registering this pool as a bean would silently move every @Async listener (push, email,
    // report, WebSocket reconnect) onto these two threads. Daemon threads; idle core threads time out after 60 s.
    private val inviteSendExecutor = ThreadPoolExecutor(
        2,
        2,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(10),
        object : ThreadFactory {
            private val counter = AtomicInteger(1)
            override fun newThread(runnable: Runnable): Thread =
                Thread(runnable, "waitlist-invite-send-${counter.getAndIncrement()}").apply { isDaemon = true }
        }
    ).apply { allowCoreThreadTimeOut(true) }

    @PreDestroy
    fun shutdownInviteSendExecutor() {
        inviteSendExecutor.shutdownNow()
    }

    private val secureRandom = SecureRandom()

    // One shared bucket per D-03 normalized email (default 3 per 24 h, D-07): tryConsume caps the confirm tokens
    // minted per refill window regardless of request parallelism, and +suffix/case variants cannot reset it. The
    // store is bounded and access-expiring (WR-10), so attacker-chosen addresses cannot grow memory without bound.
    private val emailBuckets = RateLimitBuckets(perEmailCapacity, Duration.ofHours(perEmailRefillHours), maxTrackedKeys)

    private fun emailBucket(normalizedEmail: String): Bucket = emailBuckets.bucketFor(normalizedEmail)

    /**
     * Join the waitlist. Upserts a PENDING entry keyed by the D-03 normalized email (race-free via
     * ON CONFLICT DO NOTHING) and rotates its confirm token while the entry is still PENDING and outside the resend
     * cooldown (`app.waitlist.resend-cooldown-minutes`, default 15, D-07). A re-join inside the cooldown, or of a
     * CONFIRMED/INVITED entry (D-04), changes nothing. The per-email bucket (default 3 per 24 h) is checked first, so
     * an exhausted bucket returns before any DB call, and a no-op re-join inside the cooldown still spends one token
     * (RESEARCH Open Question 2). When the token was rotated, a [WaitlistConfirmationRequestedEvent] addressed to the
     * email stored at first insert is published, so the confirmation is sent after commit (WAIT-02). The submitted
     * variant is never written or mailed: the delivery address is pinned at first insert (D-08, WR-04). Never throws
     * for duplicates, cooldown or throttling, so the caller can always answer with the same 202.
     * Neither the raw token nor the email is logged.
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
            hashToken(rawToken),
            now.plus(confirmTokenTtlHours, ChronoUnit.HOURS),
            now,
            now.minus(Duration.ofMinutes(resendCooldownMinutes)),
            WaitlistStatus.PENDING
        )
        // Only a new entry, or a PENDING one outside the cooldown, gets a link (D-04, D-07). Published inside this
        // transaction so the AFTER_COMMIT listener fires only once the new hash is durable.
        if (rotated != 1) return
        val storedEmail = waitlistEntryRepository.findStoredEmail(normalized) ?: return
        eventPublisher.publishEvent(WaitlistConfirmationRequestedEvent(storedEmail, rawToken))
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
     * Convert one CONFIRMED entry into an organic invite and email it (WAIT-04, D-10: flush, send, roll back). In ONE
     * transaction:
     * 1. claim CONFIRMED→INVITED with a conditional UPDATE (single winner under concurrency);
     * 2. issue the invite through [InviteService.create] with no referrer; it joins this transaction and flushes the
     *    invite row, so the INSERT has hit the database before any email I/O (WR-05);
     * 3. render the message (a renderer bug propagates unchanged as a 500, it is not a delivery failure);
     * 4. send it on a small private executor and wait at most `app.waitlist.invite-send-timeout-ms` (default 10000).
     *    The send stays synchronous inside the transaction, so the row lock and pooled connection are held for at most
     *    the timeout.
     *
     * A send that times out, is rejected (queue full), throws, or reports anything but SUCCESS raises
     * [WaitlistInviteDeliveryException] with the underlying cause chained (WR-06). That rolls the claim and the invite
     * back, so the entry stays CONFIRMED and the operator can retry (502). Each failure logs ONE WARN with a fixed
     * message and at most the exception type; the recipient, the raw code and any provider error detail are never
     * logged.
     *
     * Residual: on timeout the worker is interrupted (`cancel(true)`), but a provider that ignores interrupts may
     * still deliver a code that was rolled back. Redeeming it fails with the generic invite-required 403, and the
     * operator's retry sends a valid code. Any future real [EmailSender] must also bound its own I/O.
     *
     * Returns the raw code exactly once. Unknown id → [ResourceNotFoundException]; not CONFIRMED →
     * [WaitlistEntryNotConvertibleException].
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
        // Rendered outside the delivery error mapping: a renderer bug is a 500, not a retryable 502.
        val message = waitlistInviteEmailRenderer.render(recipient, rawCode)
        val result = sendBounded(message)
        if (result.status != EmailSendStatus.SUCCESS) {
            log.warn("Waitlist invite email was not accepted by the email provider")
            throw WaitlistInviteDeliveryException()
        }
        return rawCode
    }

    /**
     * Runs [EmailSender.send] on [inviteSendExecutor] and waits at most [inviteSendTimeoutMs]. Every failure is
     * logged once (fixed text, exception type only) and rethrown as [WaitlistInviteDeliveryException] with its cause.
     */
    private fun sendBounded(message: EmailMessage): EmailResult {
        val future = try {
            inviteSendExecutor.submit(Callable { emailSender.send(message) })
        } catch (ex: RejectedExecutionException) {
            log.warn("Waitlist invite email send rejected: send queue full")
            throw WaitlistInviteDeliveryException(cause = ex)
        }
        try {
            return future.get(inviteSendTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (ex: TimeoutException) {
            future.cancel(true)
            log.warn("Waitlist invite email send timed out after {} ms", inviteSendTimeoutMs)
            throw WaitlistInviteDeliveryException(cause = ex)
        } catch (ex: ExecutionException) {
            val cause = ex.cause ?: ex
            log.warn("Waitlist invite email send failed: {}", cause.javaClass.simpleName)
            throw WaitlistInviteDeliveryException(cause = cause)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            log.warn("Waitlist invite email send interrupted")
            throw WaitlistInviteDeliveryException(cause = ex)
        }
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
