package com.catspell.api.auth.controller

import com.catspell.api.auth.model.AuthResponse
import com.catspell.api.auth.model.LoginRequest
import com.catspell.api.auth.model.RefreshRequest
import com.catspell.api.auth.model.RegisterRequest
import com.catspell.api.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val response = authService.refreshToken(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/me")
    fun me(): ResponseEntity<Map<String, String>> {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = authentication.principal as String
        return ResponseEntity.ok(mapOf("userId" to userId))
    }
}
