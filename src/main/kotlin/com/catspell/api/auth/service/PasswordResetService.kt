package com.catspell.api.auth.service

import com.catspell.api.auth.model.PasswordResetToken
import com.catspell.api.auth.model.PasswordResetTokenRepository
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.email.service.EmailSender
import com.catspell.api.email.service.PasswordResetEmailRenderer
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
class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailSender: EmailSender,
    private val passwordResetEmailRenderer: PasswordResetEmailRenderer,
    @Value("\${app.forgot-password.per-email-capacity:3}") private val perEmailCapacity: Long,
    @Value("\${app.forgot-password.per-email-refill-hours:1}") private val perEmailRefillHours: Long,
    @Value("\${app.reset-token.ttl-minutes:30}") private val resetTokenTtlMinutes: Long
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
     * Enumeration-safe forgot-password flow (D-05 / RECOV-04): ALWAYS returns normally regardless of
     * whether the email is registered or the per-email cap is exhausted. Callers cannot distinguish
     * a known email, an unknown email, or a rate-limited email — no distinct return, no exception, no 429.
     */
    fun requestReset(email: String) {
        val normalizedEmail = email.trim().lowercase()

        // Per-email guard (D-04 / RECOV-07): on exhaustion, silently skip the send and still return
        // normally — never surface a 429 or any existence signal.
        if (!emailBucket(normalizedEmail).tryConsume(1)) {
            return
        }

        // Look up the user with the email exactly as submitted — matching register/login, which store
        // and query the address verbatim. Normalizing only here (e.g. lowercasing) would silently fail
        // to match any account registered with different casing, locking that user out of recovery.
        // The normalized value above is used solely as the rate-limit bucket key.
        val user = userRepository.findByEmail(email) ?: return

        // Invalidate any prior unused tokens so only the freshest link is usable.
        val priorTokens = passwordResetTokenRepository.findAllByUserAndUsedAtIsNull(user)
        if (priorTokens.isNotEmpty()) {
            val now = Instant.now()
            priorTokens.forEach { it.usedAt = now }
            passwordResetTokenRepository.saveAll(priorTokens)
        }

        val rawToken = generateRawToken()
        val resetToken = PasswordResetToken(
            user = user,
            tokenHash = hashToken(rawToken),
            expiresAt = Instant.now().plus(resetTokenTtlMinutes, ChronoUnit.MINUTES)
        )
        passwordResetTokenRepository.save(resetToken)

        val message = passwordResetEmailRenderer.render(user.email, rawToken)
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
