package com.catspell.api.auth.service

import com.catspell.api.auth.model.*
import com.catspell.api.common.exception.DuplicateEmailException
import com.catspell.api.common.exception.InvalidCredentialsException
import com.catspell.api.common.exception.InvalidTokenException
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
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    @Value("\${jwt.refresh-token-expiry-days:30}") private val refreshTokenExpiryDays: Long
) {

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw DuplicateEmailException()
        }

        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)!!
        )
        val savedUser = userRepository.save(user)

        val accessToken = jwtService.generateAccessToken(savedUser.id!!, savedUser.email)
        val refreshToken = createRefreshToken(savedUser)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException()
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
