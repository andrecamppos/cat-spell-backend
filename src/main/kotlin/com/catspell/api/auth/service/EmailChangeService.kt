package com.catspell.api.auth.service

import com.catspell.api.auth.model.EmailChangeRequest
import com.catspell.api.auth.model.EmailChangeRequestRepository
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.common.exception.DuplicateEmailException
import com.catspell.api.common.exception.InvalidCurrentPasswordException
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.email.service.EmailChangeEmailRenderer
import com.catspell.api.email.service.EmailSender
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
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
class EmailChangeService(
    private val userRepository: UserRepository,
    private val emailChangeRequestRepository: EmailChangeRequestRepository,
    private val emailSender: EmailSender,
    private val emailChangeEmailRenderer: EmailChangeEmailRenderer,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.change-email.per-email-capacity:3}") private val perEmailCapacity: Long,
    @Value("\${app.change-email.per-email-refill-hours:1}") private val perEmailRefillHours: Long,
    @Value("\${app.confirm-email-change-token.ttl-hours:24}") private val confirmTokenTtlHours: Long
) {

    private val secureRandom = SecureRandom()

    private val emailBuckets = ConcurrentHashMap<String, Bucket>()

    private fun emailBucket(normalizedEmail: String): Bucket = emailBuckets.computeIfAbsent(normalizedEmail) {
        val bandwidth = Bandwidth.builder()
            .capacity(perEmailCapacity)
            .refillIntervally(perEmailCapacity, Duration.ofHours(perEmailRefillHours))
            .build()
        Bucket.builder().addLimit(bandwidth).build()
    }

    /**
     * Change-email step 1 (request): the authenticated owner asks to move the account to [newEmail].
     * Re-verify the current password (D-01/D-02), reject an address already owned by another account
     * (D-06, ACCT-05), then mint a single-use SHA-256-hashed token and email a confirm link to the NEW
     * address (D-05, ACCT-03). The account email is NOT changed here — that only happens on confirm.
     */
    fun requestChange(userId: UUID, currentPassword: String, newEmail: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw InvalidCurrentPasswordException()
        }

        // Reject a taken address BEFORE minting anything (D-06, ACCT-05). This flow is authenticated and
        // rate-limited, so a real 409 is acceptable here (unlike the enumeration-safe recovery flows).
        if (userRepository.existsByEmail(newEmail)) {
            throw DuplicateEmailException()
        }

        // Per-target-address guard (anti-inbox-bombing): throttle confirm sends to a given new address.
        // Unlike the enumeration-safe recovery flows, this authenticated flow surfaces a real 429.
        val normalizedEmail = newEmail.trim().lowercase()
        if (!emailBucket(normalizedEmail).tryConsume(1)) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many email-change requests for this address. Try again later."
            )
        }

        // Invalidate any prior unused requests so only the freshest confirm link is usable.
        val priorRequests = emailChangeRequestRepository.findAllByUserAndUsedAtIsNull(user)
        if (priorRequests.isNotEmpty()) {
            val now = Instant.now()
            priorRequests.forEach { it.usedAt = now }
            emailChangeRequestRepository.saveAll(priorRequests)
        }

        val rawToken = generateRawToken()
        val request = EmailChangeRequest(
            user = user,
            newEmail = newEmail,
            tokenHash = hashToken(rawToken),
            expiresAt = Instant.now().plus(confirmTokenTtlHours, ChronoUnit.HOURS)
        )
        emailChangeRequestRepository.save(request)

        // Send the confirm link to the NEW address only — never to the current account email (D-05).
        val message = emailChangeEmailRenderer.render(newEmail, rawToken)
        emailSender.send(message)
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }
}
