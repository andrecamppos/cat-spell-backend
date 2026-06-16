package com.catspell.api.common.security

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.MediaType
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class RateLimitFilter : Filter {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    private val AUTH_PATHS = setOf(
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/refresh"
    )

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        val path = httpRequest.servletPath
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
        val bandwidth = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)))
        return Bucket.builder().addLimit(bandwidth).build()
    }
}

@Configuration
class RateLimitFilterConfig {

    @Bean
    fun rateLimitFilterRegistration(): FilterRegistrationBean<RateLimitFilter> {
        val registration = FilterRegistrationBean<RateLimitFilter>()
        registration.filter = RateLimitFilter()
        registration.addUrlPatterns("/api/auth/*")
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}
