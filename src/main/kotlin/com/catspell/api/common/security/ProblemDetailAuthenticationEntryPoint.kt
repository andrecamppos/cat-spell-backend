package com.catspell.api.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

internal fun writeUnauthorized(response: HttpServletResponse, detail: String) {
    if (response.isCommitted) return
    response.status = HttpServletResponse.SC_UNAUTHORIZED
    response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    response.writer.write(
        """{"title":"Unauthorized","status":401,"detail":"$detail"}"""
    )
}

@Component
class ProblemDetailAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        writeUnauthorized(response, "Authentication required")
    }
}
