package com.catspell.api.auth.service

import com.catspell.api.auth.model.*
import com.catspell.api.common.security.JwtService
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        }

        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)
        )
        val savedUser = userRepository.save(user)

        val accessToken = jwtService.generateAccessToken(savedUser.id!!, savedUser.email)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = ""
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        val accessToken = jwtService.generateAccessToken(user.id!!, user.email)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = ""
        )
    }
}
