package com.catspell.api.common.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.security.SignatureException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return path.startsWith("/api/auth/register") ||
                path.startsWith("/api/auth/login") ||
                path.startsWith("/api/auth/refresh") ||
                path.startsWith("/actuator/health")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)

        try {
            val claims = jwtService.validateToken(token)
            val userId = claims.subject
            val email = claims["email"] as? String

            val authentication = UsernamePasswordAuthenticationToken(
                userId, null, emptyList()
            )
            SecurityContextHolder.getContext().authentication = authentication
        } catch (e: ExpiredJwtException) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        } catch (e: SignatureException) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        } catch (e: Exception) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }

        filterChain.doFilter(request, response)
    }
}
