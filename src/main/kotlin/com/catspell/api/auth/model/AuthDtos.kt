package com.catspell.api.auth.model

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email(message = "must be a valid email address")
    val email: String,

    @field:Size(min = 8, message = "must be at least 8 characters")
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String
)
