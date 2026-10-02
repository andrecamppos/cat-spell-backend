package com.catspell.api.common.config

import com.catspell.api.common.security.JwtAuthenticationFilter
import com.catspell.api.common.security.ProblemDetailAuthenticationEntryPoint
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authenticationEntryPoint: ProblemDetailAuthenticationEntryPoint,
    @Value("\${app.waitlist.allowed-origins:}") private val waitlistAllowedOrigins: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            // The source is passed explicitly so Spring Security never resolves a CorsConfigurationSource by name/type.
            // CORS runs before authorization, so preflights need no OPTIONS permitAll.
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/confirm-email-change").permitAll()
                // Public waitlist routes (Phase 17). The confirm handler lands in plan 17-02.
                it.requestMatchers(HttpMethod.POST, "/api/waitlist").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/waitlist/confirm").permitAll()
                it.requestMatchers("/api/admin/invites").permitAll()
                // Operator waitlist routes (D-09): the shared AdminTokenGuard is the access boundary, not a JWT.
                it.requestMatchers("/api/admin/waitlist", "/api/admin/waitlist/**").permitAll()
                it.requestMatchers("/v3/api-docs/**").permitAll()
                it.requestMatchers("/actuator/health").permitAll()
                it.requestMatchers("/ws/**").permitAll()
                it.requestMatchers("/error").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
            }

        return http.build()
    }

    /**
     * Narrow CORS for the landing page's cross-origin join (RESEARCH Pattern 7). Only `POST /api/waitlist` is mapped,
     * only for the explicit origins in `app.waitlist.allowed-origins` (never a wildcard), with no credentials. A blank
     * value registers nothing, so no CORS headers are emitted. `/api/waitlist/confirm` is a top-level navigation and
     * the `/ws` STOMP endpoint keeps its own WebSocketConfig origin handling, so neither is mapped here.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        val origins = waitlistAllowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (origins.isNotEmpty()) {
            val config = CorsConfiguration().apply {
                allowedOrigins = origins
                allowedMethods = listOf("POST")
                allowedHeaders = listOf("Content-Type")
                allowCredentials = false
                maxAge = 3600L
            }
            source.registerCorsConfiguration("/api/waitlist", config)
        }
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
