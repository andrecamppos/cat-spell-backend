package com.catspell.api.auth.controller

import com.catspell.api.auth.model.AuthResponse
import com.catspell.api.auth.model.ChangeEmailRequest
import com.catspell.api.auth.model.ChangePasswordRequest
import com.catspell.api.auth.model.ConfirmEmailChangeRequest
import com.catspell.api.auth.model.ForgotPasswordRequest
import com.catspell.api.auth.model.GenericMessageResponse
import com.catspell.api.auth.model.LoginRequest
import com.catspell.api.auth.model.RefreshRequest
import com.catspell.api.auth.model.RegisterRequest
import com.catspell.api.auth.model.ResendVerificationRequest
import com.catspell.api.auth.model.ResetPasswordRequest
import com.catspell.api.auth.model.VerifyEmailRequest
import com.catspell.api.auth.service.AuthService
import com.catspell.api.auth.service.EmailChangeService
import com.catspell.api.auth.service.EmailVerificationService
import com.catspell.api.auth.service.PasswordResetService
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val passwordResetService: PasswordResetService,
    private val emailVerificationService: EmailVerificationService,
    private val emailChangeService: EmailChangeService
) {

    @SecurityRequirements
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<GenericMessageResponse> {
        authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            GenericMessageResponse("Registration received. Check your email to verify your account before logging in.")
        )
    }

    @SecurityRequirements
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    @SecurityRequirements
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val response = authService.refreshToken(request)
        return ResponseEntity.ok(response)
    }

    @SecurityRequirements
    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<GenericMessageResponse> {
        passwordResetService.requestReset(request.email)
        return ResponseEntity.accepted().body(
            GenericMessageResponse("If an account exists for that email, a password reset link has been sent.")
        )
    }

    @SecurityRequirements
    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Void> {
        authService.resetPassword(request.token, request.newPassword)
        return ResponseEntity.ok().build()
    }

    @SecurityRequirements
    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest): ResponseEntity<Void> {
        authService.verifyEmail(request.token)
        return ResponseEntity.ok().build()
    }

    @SecurityRequirements
    @PostMapping("/resend-verification")
    fun resendVerification(@Valid @RequestBody request: ResendVerificationRequest): ResponseEntity<GenericMessageResponse> {
        emailVerificationService.resend(request.email)
        return ResponseEntity.accepted().body(
            GenericMessageResponse("If an unverified account exists for that email, a verification link has been sent.")
        )
    }

    @GetMapping("/me")
    fun me(): ResponseEntity<Map<String, String>> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userId = authentication.principal as String
        return ResponseEntity.ok(mapOf("userId" to userId))
    }

    // Authenticated (NO @SecurityRequirements): the current password re-verification happens in the
    // service; a wrong password yields 403 INVALID_CURRENT_PASSWORD and success returns no tokens.
    @PostMapping("/change-password")
    fun changePassword(@Valid @RequestBody request: ChangePasswordRequest): ResponseEntity<Void> {
        authService.changePassword(extractUserId(), request.currentPassword, request.newPassword)
        return ResponseEntity.ok().build()
    }

    // Authenticated (NO @SecurityRequirements): requests a change to a new address; the account email
    // is NOT changed until the confirm link is used.
    @PostMapping("/change-email")
    fun changeEmail(@Valid @RequestBody request: ChangeEmailRequest): ResponseEntity<GenericMessageResponse> {
        emailChangeService.requestChange(extractUserId(), request.currentPassword, request.newEmail)
        return ResponseEntity.accepted().body(
            GenericMessageResponse("Check your new email address to confirm the change.")
        )
    }

    @SecurityRequirements
    @PostMapping("/confirm-email-change")
    fun confirmEmailChange(@Valid @RequestBody request: ConfirmEmailChangeRequest): ResponseEntity<Void> {
        authService.confirmEmailChange(request.token)
        return ResponseEntity.ok().build()
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
