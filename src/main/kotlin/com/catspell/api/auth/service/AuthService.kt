package com.catspell.api.auth.service

import com.catspell.api.auth.model.*
import com.catspell.api.common.exception.DuplicateEmailException
import com.catspell.api.common.exception.EmailNotVerifiedException
import com.catspell.api.common.exception.InvalidCredentialsException
import com.catspell.api.common.exception.InvalidCurrentPasswordException
import com.catspell.api.common.exception.InvalidTokenException
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.common.security.JwtService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val emailChangeRequestRepository: EmailChangeRequestRepository,
    private val emailVerificationService: EmailVerificationService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    @Value("\${jwt.refresh-token-expiry-days:30}") private val refreshTokenExpiryDays: Long
) {

    fun register(request: RegisterRequest) {
        if (userRepository.existsByEmail(request.email)) {
            throw DuplicateEmailException()
        }

        // Create the account unverified (emailVerifiedAt = null) and send the first verification email.
        // No session is minted — the user must verify then log in fresh (breaking contract, D-01, VERIFY-01).
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)!!
        )
        val savedUser = userRepository.save(user)

        emailVerificationService.issueAndSend(savedUser)
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        // Login hard-gate (VERIFY-03, D-03): evaluated AFTER the password check so an unknown email or a
        // wrong password still yields a generic 401; only a correct-password-but-unverified attempt reveals
        // the distinct EMAIL_NOT_VERIFIED (403) gate, which the app uses to route to the resend screen.
        if (user.emailVerifiedAt == null) {
            throw EmailNotVerifiedException()
        }

        val accessToken = jwtService.generateAccessToken(user.id!!, user.email)
        val refreshToken = createRefreshToken(user)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    @Transactional(noRollbackFor = [InvalidTokenException::class])
    fun refreshToken(request: RefreshRequest): AuthResponse {
        val storedToken = refreshTokenRepository.findByToken(request.refreshToken)
            ?: throw InvalidTokenException()

        if (storedToken.revoked) {
            revokeAllUserTokens(storedToken.user)
            throw InvalidTokenException("Token reuse detected")
        }

        if (storedToken.expiresAt.isBefore(Instant.now())) {
            throw InvalidTokenException()
        }

        val newRefreshTokenString = createRefreshToken(storedToken.user)

        storedToken.revoked = true
        storedToken.replacedBy = newRefreshTokenString
        refreshTokenRepository.save(storedToken)

        val accessToken = jwtService.generateAccessToken(storedToken.user.id!!, storedToken.user.email)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = newRefreshTokenString
        )
    }

    @Transactional
    fun resetPassword(rawToken: String, newPassword: String) {
        val tokenHash = hashToken(rawToken)
        val resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
            ?: throw InvalidTokenException()

        if (resetToken.expiresAt.isBefore(Instant.now())) {
            throw InvalidTokenException()
        }

        // Atomically claim the token. If another concurrent request already consumed it, the
        // conditional update matches zero rows and we reject — enforcing single-use without a
        // read-check-write race (a plain usedAt check + save would let two callers both succeed).
        if (passwordResetTokenRepository.markUsed(resetToken.id!!, Instant.now()) == 0) {
            throw InvalidTokenException()
        }

        val user = resetToken.user
        user.passwordHash = passwordEncoder.encode(newPassword)!!
        user.updatedAt = Instant.now()
        userRepository.save(user)

        revokeAllUserTokens(user)
    }

    @Transactional
    fun verifyEmail(rawToken: String) {
        val tokenHash = hashToken(rawToken)
        val verificationToken = emailVerificationTokenRepository.findByTokenHash(tokenHash)
            ?: throw InvalidTokenException()

        if (verificationToken.expiresAt.isBefore(Instant.now())) {
            throw InvalidTokenException()
        }

        // Atomically claim the token. If another concurrent request already consumed it, the conditional
        // update matches zero rows and we reject — enforcing single-use without a read-check-write race.
        if (emailVerificationTokenRepository.markUsed(verificationToken.id!!, Instant.now()) == 0) {
            throw InvalidTokenException()
        }

        // Stamp the account verified. Mint NO session and do NOT revoke refresh tokens (D-02):
        // register created no session, so the user simply logs in fresh afterward.
        val user = verificationToken.user
        user.emailVerifiedAt = Instant.now()
        user.updatedAt = Instant.now()
        userRepository.save(user)
    }

    @Transactional
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        // Re-verify the current password BEFORE any mutation (D-01/D-02, ACCT-01). A wrong password
        // yields a distinct 403 INVALID_CURRENT_PASSWORD and leaves the hash + all sessions untouched.
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw InvalidCurrentPasswordException()
        }

        user.passwordHash = passwordEncoder.encode(newPassword)!!
        user.updatedAt = Instant.now()
        userRepository.save(user)

        // A credential change forces a fresh login (D-03, ACCT-02): revoke every active session and
        // mint no new tokens.
        revokeAllUserTokens(user)
    }

    @Transactional
    fun confirmEmailChange(rawToken: String) {
        val tokenHash = hashToken(rawToken)
        val request = emailChangeRequestRepository.findByTokenHash(tokenHash)
            ?: throw InvalidTokenException()

        if (request.expiresAt.isBefore(Instant.now())) {
            throw InvalidTokenException()
        }

        // Atomically claim the token. If another concurrent request already consumed it, the conditional
        // update matches zero rows and we reject — the email swaps at most once (D-08).
        if (emailChangeRequestRepository.markUsed(request.id!!, Instant.now()) == 0) {
            throw InvalidTokenException()
        }

        // Only now — after a valid single-use claim — swap the account email, stamp it verified, and
        // revoke ALL sessions so the identity change forces a fresh login (D-08, ACCT-04). Mint no tokens.
        val user = request.user
        user.email = request.newEmail
        user.emailVerifiedAt = Instant.now()
        user.updatedAt = Instant.now()
        userRepository.save(user)

        revokeAllUserTokens(user)
    }

    private fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }

    private fun createRefreshToken(user: User): String {
        val tokenString = UUID.randomUUID().toString()
        val refreshToken = RefreshToken(
            user = user,
            token = tokenString,
            expiresAt = Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(refreshToken)
        return tokenString
    }

    private fun revokeAllUserTokens(user: User) {
        val activeTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user)
        activeTokens.forEach { it.revoked = true }
        refreshTokenRepository.saveAll(activeTokens)
    }
}
