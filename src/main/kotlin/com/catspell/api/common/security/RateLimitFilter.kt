package com.catspell.api.common.security

import com.catspell.api.common.config.WaitlistCorsPolicy
import com.catspell.api.common.ratelimit.RateLimitBuckets
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.cors.CorsConfiguration
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Rate-limit response headers a cross-origin page may read on a join 429; Retry-After is not CORS-safelisted. */
private const val EXPOSED_RATE_LIMIT_HEADERS = "Retry-After, X-RateLimit-Remaining, X-RateLimit-Reset"

/**
 * Per-client-IP throttle with three independent bucket families, each refilling once per minute:
 * - AUTH: the /api/auth endpoints in AUTH_PATHS ([capacity], rate-limit.capacity).
 * - WAITLIST_JOIN: POST /api/waitlist ([waitlistCapacity], rate-limit.waitlist-capacity). D-14 / WR-07(a): the public
 *   landing-page join has its own budget, so it can never spend a shared IP's login budget, and the reverse.
 * - ADMIN: /api/admin and everything under it, any method ([adminCapacity], rate-limit.admin-capacity). D-13 / WR-08:
 *   every operator request counts, and the filter runs ahead of the X-Admin-Token check, so guessing the operator
 *   secret is throttled whatever the guess.
 *
 * WR-10: every family is a bounded, access-expiring [RateLimitBuckets] store (at most [maxTrackedKeys] client IPs each),
 * so rotating source addresses can never grow memory without bound. Every parameter has a default, so `RateLimitFilter()`
 * still builds a working AUTH limiter.
 *
 * D-14 / WR-07(c): [corsConfiguration] is the landing-page join's CORS policy ([WaitlistCorsPolicy]). A WAITLIST_JOIN
 * 429 granted to an allowed origin carries Access-Control-Allow-Origin and exposes the rate-limit headers, so the page
 * can read Retry-After cross-origin. Null (no allowed origins) means the 429 never carries a CORS grant.
 */
class RateLimitFilter(
    capacity: Long = 10,
    private val trustedProxies: Set<String> = setOf("127.0.0.1", "::1"),
    waitlistCapacity: Long = 10,
    adminCapacity: Long = 5,
    maxTrackedKeys: Long = 100_000,
    private val corsConfiguration: CorsConfiguration? = null
) : Filter {

    private enum class BucketFamily { AUTH, WAITLIST_JOIN, ADMIN }

    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)

    private val authBuckets = RateLimitBuckets(capacity, Duration.ofMinutes(1), maxTrackedKeys)
    private val waitlistJoinBuckets = RateLimitBuckets(waitlistCapacity, Duration.ofMinutes(1), maxTrackedKeys)
    private val adminBuckets = RateLimitBuckets(adminCapacity, Duration.ofMinutes(1), maxTrackedKeys)

    // D-17 / current IN-05: guards the one-shot misconfiguration WARN, so it can never be used to flood the log.
    private val warnedUntrustedForwardedFor = AtomicBoolean(false)

    // Built once at construction, so an invalid trusted-proxies entry fails bean creation (and startup) loudly.
    private val trustedProxyMatcher = TrustedProxyMatcher(trustedProxies)

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

        // T-17-32 / CR-02: match on the container-normalized path (percent-decoded, `;` parameters stripped, `//`
        // collapsed, and since D-16 / current IN-01 also dot segments resolved), never the raw requestURI. The servlet
        // mapping, Spring Security's matchers and Spring MVC routing all decode before matching, so a raw-URI check let
        // `/api/%77aitlist` or `/api/auth/%6Cogin` skip the limit. Resolving dot segments here means `/api/auth/./login`
        // is throttled by this filter, not only rejected later by StrictHttpFirewall.
        val path = RequestPaths.normalized(httpRequest)
        val family = bucketFamilyFor(httpRequest.method, path)
        if (family == null) {
            chain.doFilter(request, response)
            return
        }

        val clientIp = resolveClientIp(httpRequest)
        val buckets = when (family) {
            BucketFamily.AUTH -> authBuckets
            BucketFamily.WAITLIST_JOIN -> waitlistJoinBuckets
            BucketFamily.ADMIN -> adminBuckets
        }
        val probe = buckets.bucketFor(clientIp).tryConsumeAndReturnRemaining(1)

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
            if (family == BucketFamily.WAITLIST_JOIN) addJoinCorsHeaders(httpRequest, httpResponse)
            httpResponse.writer.write(
                """{"title":"Too Many Requests","status":429,"detail":"Rate limit exceeded. Try again in $retryAfterSeconds seconds."}"""
            )
        }
    }

    /**
     * D-14: this 429 is written before Spring Security's CorsFilter runs, so the limiter mirrors the same join policy
     * itself. The response always varies on Origin; only an origin the policy allows gets the grant, echoed in the
     * request's own spelling, never a wildcard and never with credentials. No CORS processor is used, because one
     * would answer a disallowed origin with 403 and hide the 429.
     */
    private fun addJoinCorsHeaders(request: HttpServletRequest, response: HttpServletResponse) {
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN)
        val allowedOrigin = corsConfiguration?.checkOrigin(request.getHeader(HttpHeaders.ORIGIN)) ?: return
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin)
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, EXPOSED_RATE_LIMIT_HEADERS)
    }

    /** The family that throttles this request, or null when the request is never throttled. */
    private fun bucketFamilyFor(method: String, path: String): BucketFamily? = when {
        // Exact method + path match for the public waitlist join (D-07). A `/api/waitlist` prefix match would also
        // throttle GET /api/waitlist/confirm links and CORS preflights, which must never be limited.
        method == "POST" && path == "/api/waitlist" -> BucketFamily.WAITLIST_JOIN
        // D-13: any method, so every operator request counts (Open Question 1: successes count too).
        path == "/api/admin" || path.startsWith("/api/admin/") -> BucketFamily.ADMIN
        AUTH_PATHS.any { path.startsWith(it) } -> BucketFamily.AUTH
        else -> null
    }

    // T-17-30: an internet-facing caller controls every request header it sends but never remoteAddr, so
    // X-Forwarded-For is honored only when the directly-connecting peer is a configured trusted proxy. An untrusted
    // peer is always keyed on its own socket address and can never borrow another IP's bucket by forging the header.
    // T-17-33 / CR-01: for a trusted peer, the key is the rightmost hop that is not itself a trusted proxy, read across
    // every X-Forwarded-For header line in arrival order (Tomcat RemoteIpValve semantics). Hops to the left of it are
    // client-written and never chosen, so both appending proxies (nginx $proxy_add_x_forwarded_for, AWS ALB) and
    // overwriting proxies work. If every hop is trusted, or there is none, the peer address itself is the key.
    // D-15 / current WR-02: every hop is canonicalized (brackets and a trailing `:port` stripped, then re-rendered from
    // the parsed address bytes) before both the trust check and key selection, so `ip:port` hops, trusted hops written
    // with a port and alternative spellings all land on one bucket per address. A hop that is still not an IP literal
    // fails safe: the peer address is the key, so arbitrary hop text can never mint a fresh bucket.
    private fun resolveClientIp(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr
        if (!trustedProxyMatcher.matches(remoteAddr)) {
            // D-17: a reverse proxy missing from the trusted list makes every client share its bucket. Say so once,
            // naming the peer and the setting. The header value is never logged.
            val sentForwardedFor = request.getHeader("X-Forwarded-For") != null
            if (sentForwardedFor && warnedUntrustedForwardedFor.compareAndSet(false, true)) {
                log.warn(
                    "Ignoring X-Forwarded-For from untrusted peer {}; if this is your reverse proxy, add it to " +
                        "rate-limit.trusted-proxies (RATE_LIMIT_TRUSTED_PROXIES)",
                    remoteAddr
                )
            }
            return remoteAddr
        }
        val hops = request.getHeaders("X-Forwarded-For")?.toList().orEmpty()
            .flatMap { line -> line.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (hop in hops.asReversed()) {
            val canonical = TrustedProxyMatcher.canonicalize(hop) ?: return remoteAddr
            if (trustedProxyMatcher.matches(canonical)) continue
            return canonical
        }
        return remoteAddr
    }
}

@Configuration
class RateLimitFilterConfig(
    @Value("\${rate-limit.capacity:10}") private val capacity: Long,
    // Peers allowed to set X-Forwarded-For: exact IPv4/IPv6 addresses or CIDR ranges, compared by address value (see
    // TrustedProxyMatcher). An invalid entry fails startup. Override with RATE_LIMIT_TRUSTED_PROXIES when the reverse
    // proxy connects from another address or range.
    @Value("\${rate-limit.trusted-proxies:127.0.0.1,::1}") private val trustedProxiesRaw: String,
    // D-14: per-IP budget for POST /api/waitlist, separate from the auth budget above.
    @Value("\${rate-limit.waitlist-capacity:10}") private val waitlistCapacity: Long,
    // D-13: strict per-IP budget for /api/admin/** that counts every request. Operators batch-converting entries should
    // honor Retry-After or raise RATE_LIMIT_ADMIN_CAPACITY.
    @Value("\${rate-limit.admin-capacity:5}") private val adminCapacity: Long,
    // WR-10: the most client IPs each bucket family tracks at once.
    @Value("\${rate-limit.max-tracked-keys:100000}") private val maxTrackedKeys: Long,
    // D-14: the same join CORS policy Spring Security registers, so a throttled join is readable by the same origins.
    private val waitlistCorsPolicy: WaitlistCorsPolicy
) {

    @Bean
    fun rateLimitFilterRegistration(): FilterRegistrationBean<RateLimitFilter> {
        val trustedProxies = trustedProxiesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val registration = FilterRegistrationBean(
            RateLimitFilter(
                capacity, trustedProxies, waitlistCapacity, adminCapacity, maxTrackedKeys, waitlistCorsPolicy.configuration
            )
        )
        // "/api/waitlist" is a servlet exact-match pattern. Without it the filter never runs on the join (Pitfall 1).
        // The servlet path pattern for the operator routes also matches the bare /api/admin path itself.
        registration.addUrlPatterns("/api/auth/*", "/api/waitlist", "/api/admin/*")
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE)
        return registration
    }
}
