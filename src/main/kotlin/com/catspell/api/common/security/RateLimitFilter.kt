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

class RateLimitFilter(
    private val capacity: Long = 10,
    private val trustedProxies: Set<String> = setOf("127.0.0.1", "::1")
) : Filter {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    private val AUTH_PATHS = setOf(
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/forgot-password",
        "/api/auth/resend-verification",
        "/api/auth/change-email"
    )

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        val path = httpRequest.requestURI
        // Exact method + path match for the public waitlist join (D-07). A `/api/waitlist` prefix entry in AUTH_PATHS
        // would also throttle GET /api/waitlist/confirm links and CORS preflights, which must never be limited.
        val isWaitlistJoin = httpRequest.method == "POST" && path == "/api/waitlist"
        if (!isWaitlistJoin && !AUTH_PATHS.any { path.startsWith(it) }) {
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

    // T-17-30: an internet-facing caller controls every request header it sends but never remoteAddr, so
    // X-Forwarded-For is honored only when the directly-connecting peer is a configured trusted proxy. An untrusted
    // peer is always keyed on its own socket address and can never borrow another IP's bucket by forging the header.
    private fun resolveClientIp(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr
        if (remoteAddr !in trustedProxies) {
            return remoteAddr
        }
        val forwardedFor = request.getHeader("X-Forwarded-For")
        if (forwardedFor != null && forwardedFor.isNotBlank()) {
            return forwardedFor.split(",").first().trim()
        }
        return remoteAddr
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
    @Value("\${rate-limit.capacity:10}") private val capacity: Long,
    // Exact-match peer addresses allowed to set X-Forwarded-For (no CIDR / hop counting). Override with
    // RATE_LIMIT_TRUSTED_PROXIES when the reverse proxy connects from another address.
    @Value("\${rate-limit.trusted-proxies:127.0.0.1,::1}") private val trustedProxiesRaw: String
) {

    @Bean
    fun rateLimitFilterRegistration(): FilterRegistrationBean<RateLimitFilter> {
        val trustedProxies = trustedProxiesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val registration = FilterRegistrationBean(RateLimitFilter(capacity, trustedProxies))
        // "/api/waitlist" is a servlet exact-match pattern. Without it the filter never runs on the join (Pitfall 1).
        registration.addUrlPatterns("/api/auth/*", "/api/waitlist")
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE)
        return registration
    }
}
