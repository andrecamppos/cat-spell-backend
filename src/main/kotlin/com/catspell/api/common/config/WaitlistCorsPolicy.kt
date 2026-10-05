package com.catspell.api.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration

/**
 * The one source of truth for the landing page's cross-origin join CORS policy (RESEARCH Pattern 7, D-14). Spring
 * Security's CORS registration for `POST /api/waitlist` and the rate limiter's 429 both read [configuration], so the
 * two can never drift apart.
 *
 * Only the explicit origins in `app.waitlist.allowed-origins` (comma-separated, never a wildcard) are allowed, for POST
 * with a Content-Type header and no credentials. A blank value leaves [configuration] null, and then neither Spring
 * Security nor the limiter emits any CORS header.
 */
@Component
class WaitlistCorsPolicy(@Value("\${app.waitlist.allowed-origins:}") rawOrigins: String) {

    /** The join's CORS configuration, or null when no origin is configured. */
    val configuration: CorsConfiguration? = buildConfiguration(rawOrigins)

    private fun buildConfiguration(rawOrigins: String): CorsConfiguration? {
        val origins = rawOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (origins.isEmpty()) return null
        return CorsConfiguration().apply {
            allowedOrigins = origins
            allowedMethods = listOf("POST")
            allowedHeaders = listOf("Content-Type")
            allowCredentials = false
            maxAge = 3600L
        }
    }
}
