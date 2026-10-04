package com.catspell.api.common.security

import com.catspell.api.common.exception.AdminAuthException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter

/**
 * The central, deny-by-default `X-Admin-Token` boundary for every operator path under `/api/admin` (D-11, WR-08).
 *
 * It runs on every `/api/admin` request, including routes that have no handler yet, so a new operator endpoint is
 * guarded even if its handler forgets to call [AdminTokenGuard.require]. A missing or wrong token gets the generic
 * `401 Unauthorized` / `Not authorized` problem response, the same body the handler-level check produces.
 *
 * Ordering ([AdminTokenFilterConfig]): it runs after RateLimitFilter (`HIGHEST_PRECEDENCE`), so token guesses are
 * throttled first, and before Spring Security (-100) and MVC. So a stale `Authorization: Bearer` header cannot answer
 * 401 first (D-12), and a malformed query parameter cannot answer 400 before the token is checked (IN-09).
 *
 * It is deliberately not a Spring component. Spring Boot would also register a `Filter` bean for every path, on top of
 * the operator-path registration, so the instance is created only inside [AdminTokenFilterConfig]. The comparison is not
 * reimplemented here: [AdminTokenGuard.require] stays the single constant-time check.
 */
class AdminTokenFilter(
    private val adminTokenGuard: AdminTokenGuard
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            adminTokenGuard.require(request.getHeader(ADMIN_TOKEN_HEADER))
        } catch (e: AdminAuthException) {
            writeUnauthorized(response, "Not authorized")
            return
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val ADMIN_TOKEN_HEADER = "X-Admin-Token"
    }
}

/** Registers [AdminTokenFilter] on every path under `/api/admin`, between RateLimitFilter and Spring Security's chain. */
@Configuration
class AdminTokenFilterConfig(
    private val adminTokenGuard: AdminTokenGuard
) {

    @Bean
    fun adminTokenFilterRegistration(): FilterRegistrationBean<AdminTokenFilter> {
        val registration = FilterRegistrationBean(AdminTokenFilter(adminTokenGuard))
        registration.addUrlPatterns("/api/admin/*")
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10)
        return registration
    }
}
