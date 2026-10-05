package com.catspell.api.common

import com.catspell.api.common.security.RequestPaths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.mock.web.MockHttpServletRequest

/**
 * D-16 / current IN-01: RequestPaths.normalized gives the container-normalized path, so the rate limiter matches every
 * spelling Tomcat routes to a throttled handler. servletPath and pathInfo are set explicitly to cover both shapes:
 * Tomcat (normalized servletPath) and MockMvc (empty servletPath, un-normalized pathInfo).
 */
class RequestPathsTest {

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> = listOf(
            Arguments.of("/api/waitlist", null, "/api/waitlist"),
            Arguments.of("", "/api/auth/./login", "/api/auth/login"),
            Arguments.of("", "/api//waitlist", "/api/waitlist"),
            Arguments.of("", "/api/waitlist;x=1", "/api/waitlist"),
            Arguments.of("/api", "/waitlist", "/api/waitlist"),
            Arguments.of("", "/api/x/../waitlist", "/api/waitlist"),
            // The trailing slash is kept, so /api/waitlist/ is never mistaken for the exact join path.
            Arguments.of("", "/api/waitlist/", "/api/waitlist/")
        )
    }

    @ParameterizedTest(name = "servletPath={0}, pathInfo={1} -> {2}")
    @MethodSource("cases")
    fun `normalized resolves the container path`(servletPath: String, pathInfo: String?, expected: String) {
        val request = MockHttpServletRequest("POST", "/ignored")
        request.servletPath = servletPath
        request.pathInfo = pathInfo

        assertEquals(expected, RequestPaths.normalized(request), "servletPath=$servletPath pathInfo=$pathInfo")
    }
}
