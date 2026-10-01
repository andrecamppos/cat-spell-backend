package com.catspell.api.invite.service

import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.common.exception.InviteRequiredException
import com.catspell.api.invite.model.Invite
import com.catspell.api.invite.model.InviteRepository
import com.catspell.api.invite.model.Referral
import com.catspell.api.invite.model.ReferralRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

@Service
class InviteService(
    private val inviteRepository: InviteRepository,
    private val referralRepository: ReferralRepository,
    private val userRepository: UserRepository
) {

    private val secureRandom = SecureRandom()

    /**
     * Issue a new invite for an optional referrer. A non-null referrer must be a real user (D-12); an
     * unknown referrer is rejected with 400 (IllegalArgumentException → handleIllegalArgument). Only the
     * SHA-256 hash of the code is persisted; the raw code is returned exactly once and never stored (D-06).
     */
    @Transactional
    fun create(referrerUserId: UUID?): String {
        if (referrerUserId != null && !userRepository.existsById(referrerUserId)) {
            // 400 via GlobalExceptionHandler.handleIllegalArgument (D-12).
            throw IllegalArgumentException("Unknown referrer")
        }
        val rawCode = generateRawCode()
        inviteRepository.save(Invite(codeHash = hashToken(rawCode), referrerUserId = referrerUserId))
        return rawCode
    }

    /**
     * Resolve a submitted code to its unconsumed Invite. Null, blank, not-found, and already-consumed codes
     * ALL throw the same generic [InviteRequiredException] so the failure modes are indistinguishable
     * (D-10, Pitfall 1 — enumeration safety).
     */
    fun validate(code: String?): Invite {
        if (code.isNullOrBlank()) throw InviteRequiredException()
        val invite = inviteRepository.findByCodeHash(hashToken(code)) ?: throw InviteRequiredException()
        if (invite.consumedAt != null) throw InviteRequiredException()
        return invite
    }

    /**
     * Atomically claim [invite] for [invitee] and, only when a real distinct referrer exists, write the
     * referral attribution. A 0-row claim means the code was raced/already consumed → the same generic
     * [InviteRequiredException], and the surrounding transaction rolls back the account insert (Pitfall 3).
     */
    @Transactional
    fun consume(invite: Invite, invitee: User) {
        if (inviteRepository.markConsumed(invite.id!!, invitee.id!!, Instant.now()) == 0) {
            throw InviteRequiredException()
        }
        val referrerId = invite.referrerUserId ?: return   // bootstrap code → no referral row (D-11, Pitfall 6)
        if (referrerId == invitee.id) return                // defensive self-referral guard (D-12)
        referralRepository.save(Referral(referrerId = referrerId, inviteeId = invitee.id!!, inviteId = invite.id!!))
    }

    private fun generateRawCode(): String {
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
