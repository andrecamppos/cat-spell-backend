package com.catspell.api.auth.model

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class RegisterRequest(
    @field:Email(message = "must be a valid email address")
    val email: String,

    @field:Size(min = 8, message = "must be at least 8 characters")
    val password: String,

    @field:NotNull
    @field:Past(message = "date of birth must be in the past")
    val dateOfBirth: LocalDate,

    // Optional at the DTO layer — requiredness is enforced in AuthService only when app.invite.enabled=true,
    // so public mode (gate off) never rejects a registration that omits it (D-08/D-09, Pitfall 5).
    val inviteCode: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String
)

data class ForgotPasswordRequest(
    @field:Email(message = "must be a valid email address")
    val email: String
)

data class ResetPasswordRequest(
    val token: String,

    @field:Size(min = 8, message = "must be at least 8 characters")
    val newPassword: String
)

data class VerifyEmailRequest(
    val token: String
)

data class ResendVerificationRequest(
    @field:Email(message = "must be a valid email address")
    val email: String
)

data class GenericMessageResponse(
    val message: String
)

data class ChangePasswordRequest(
    val currentPassword: String,

    @field:Size(min = 8, message = "must be at least 8 characters")
    val newPassword: String
)

data class ChangeEmailRequest(
    val currentPassword: String,

    @field:Email(message = "must be a valid email address")
    val newEmail: String
)

data class ConfirmEmailChangeRequest(
    val token: String
)
