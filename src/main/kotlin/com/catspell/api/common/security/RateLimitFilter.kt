package com.catspell.api.common.security

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.MediaType
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class RateLimitFilter(private val capacity: Long = 10) : Filter {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    private val AUTH_PATHS = setOf(
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/forgot-password"
    )

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        val path = httpRequest.requestURI
        if (!AUTH_PATHS.any { path.startsWith(it) }) {
            chain.doFilter(request, response)
            return
        }

        val clientIp = resolveClientIp(httpRequest)
        val bucket = buckets.computeIfAbsent(clientIp) { createBucket() }
        val probe = bucket.tryConsumeAndReturnRemaining(1)

        if (probe.isConsumed) {
            httpResponse.setHeader("X-RateLimit-Remaining", probe.remainingTokens.toString())
            httpResponse.setHeader("X-RateLimit-Reset", (probe.nanosToWaitForReset / 1_000_000_000).toString())
            chain.doFilter(request, response)
        } else {
            val retryAfterSeconds = (probe.nanosToWaitForRefill / 1_000_000_000) + 1
            httpResponse.status = 429
            httpResponse.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            httpResponse.setHeader("Retry-After", retryAfterSeconds.toString())
            httpResponse.setHeader("X-RateLimit-Remaining", "0")
            httpResponse.setHeader("X-RateLimit-Reset", retryAfterSeconds.toString())
            httpResponse.writer.write(
                """{"title":"Too Many Requests","status":429,"detail":"Rate limit exceeded. Try again in $retryAfterSeconds seconds."}"""
            )
        }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwardedFor = request.getHeader("X-Forwarded-For")
        if (forwardedFor != null && forwardedFor.isNotBlank()) {
            return forwardedFor.split(",").first().trim()
        }
        return request.remoteAddr
    }

    private fun createBucket(): Bucket {
        val bandwidth = Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, Duration.ofMinutes(1))
            .build()
        return Bucket.builder().addLimit(bandwidth).build()
    }
}

@Configuration
class RateLimitFilterConfig(
    @Value("\${rate-limit.capacity:10}") private val capacity: Long
) {

    @Bean
    fun rateLimitFilterRegistration(): FilterRegistrationBean<RateLimitFilter> {
        val registration = FilterRegistrationBean(RateLimitFilter(capacity))
        registration.addUrlPatterns("/api/auth/*")
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE)
        return registration
    }
}
