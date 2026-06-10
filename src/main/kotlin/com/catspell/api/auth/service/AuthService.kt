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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
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
