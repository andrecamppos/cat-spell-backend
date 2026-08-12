package com.catspell.api.auth.service

import com.catspell.api.auth.model.EmailVerificationToken
import com.catspell.api.auth.model.EmailVerificationTokenRepository
import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.email.service.EmailSender
import com.catspell.api.email.service.EmailVerificationEmailRenderer
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

@Service
class EmailVerificationService(
    private val userRepository: UserRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val emailSender: EmailSender,
    private val emailVerificationEmailRenderer: EmailVerificationEmailRenderer,
    @Value("\${app.resend-verification.per-email-capacity:3}") private val perEmailCapacity: Long,
    @Value("\${app.resend-verification.per-email-refill-hours:1}") private val perEmailRefillHours: Long,
    @Value("\${app.verify-token.ttl-hours:24}") private val verifyTokenTtlHours: Long
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
     * Reusable internal issue path (VERIFY-01): invalidate any prior unused verification tokens, mint a
     * fresh SHA-256-hashed single-use token expiring in [verifyTokenTtlHours] hours (D-08), persist it, and
     * send exactly one verification email via the existing EmailSender seam. The raw token is only ever
     * embedded in the outbound email link — never persisted or logged.
     */
    fun issueAndSend(user: User) {
        val priorTokens = emailVerificationTokenRepository.findAllByUserAndUsedAtIsNull(user)
        if (priorTokens.isNotEmpty()) {
            val now = Instant.now()
            priorTokens.forEach { it.usedAt = now }
            emailVerificationTokenRepository.saveAll(priorTokens)
        }

        val rawToken = generateRawToken()
        val verificationToken = EmailVerificationToken(
            user = user,
            tokenHash = hashToken(rawToken),
            expiresAt = Instant.now().plus(verifyTokenTtlHours, ChronoUnit.HOURS)
        )
        emailVerificationTokenRepository.save(verificationToken)

        val message = emailVerificationEmailRenderer.render(user.email, rawToken)
        emailSender.send(message)
    }

    /**
     * Enumeration-safe resend flow (VERIFY-04, D-04/D-05): ALWAYS returns normally regardless of whether the
     * email is unknown, already verified, or per-email rate-limited. Callers cannot distinguish these cases —
     * no distinct return, no exception, no 429, no existence/verification signal.
     */
    fun resend(email: String) {
        val normalizedEmail = email.trim().lowercase()

        // Per-email guard (D-05): on exhaustion, silently skip the send and still return normally.
        if (!emailBucket(normalizedEmail).tryConsume(1)) {
            return
        }

        // Look up the user with the email exactly as submitted (matching register/login, which store and
        // query verbatim). The normalized value above is used solely as the rate-limit bucket key.
        val user = userRepository.findByEmail(email) ?: return

        // Already-verified is indistinguishable from unknown — return silently either way (D-04).
        if (user.emailVerifiedAt != null) {
            return
        }

        issueAndSend(user)
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
