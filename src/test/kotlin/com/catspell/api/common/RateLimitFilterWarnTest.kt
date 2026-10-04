package com.catspell.api.common

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.catspell.api.common.security.RateLimitFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * D-17 / current IN-05: the first time an untrusted peer sends X-Forwarded-For, RateLimitFilter logs exactly one WARN
 * naming the peer and the trusted-proxies setting, so a missing reverse-proxy entry is visible at runtime. Pure JUnit
 * over a fresh filter per test (the one-shot guard is per filter instance), no Spring context.
 */
class RateLimitFilterWarnTest {

    companion object {
        private const val LOGIN_PATH = "/api/auth/login"
        private const val FORWARDED_CLIENT = "10.9.9.9"
        private const val FIRST_UNTRUSTED_PEER = "203.0.113.90"
        private const val SECOND_UNTRUSTED_PEER = "203.0.113.91"
        private const val UNTRUSTED_PEER_WITHOUT_XFF = "203.0.113.92"
        private const val TRUSTED_PEER = "127.0.0.1"
    }

    private val filterLogger = LoggerFactory.getLogger(RateLimitFilter::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        filterLogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        filterLogger.detachAppender(appender)
        appender.stop()
    }

    /** Sends one login through [filter] from [remoteAddr], with X-Forwarded-For when [forwardedFor] is set. */
    private fun postLogin(filter: RateLimitFilter, remoteAddr: String, forwardedFor: String? = null) {
        val request = MockHttpServletRequest("POST", LOGIN_PATH)
        request.servletPath = LOGIN_PATH
        request.remoteAddr = remoteAddr
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor)
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())
    }

    private fun warnEvents(): List<ILoggingEvent> = appender.list.filter { it.level == Level.WARN }

    @Test
    fun `the first untrusted peer that sends X-Forwarded-For logs exactly one WARN`() {
        val filter = RateLimitFilter()

        postLogin(filter, FIRST_UNTRUSTED_PEER, FORWARDED_CLIENT)
        postLogin(filter, FIRST_UNTRUSTED_PEER, FORWARDED_CLIENT)
        postLogin(filter, SECOND_UNTRUSTED_PEER, FORWARDED_CLIENT)

        val warnings = warnEvents()
        assertEquals(1, warnings.size, "only the first untrusted forwarded request may warn, got $warnings")
        val message = warnings.single().formattedMessage
        assertTrue(message.contains(FIRST_UNTRUSTED_PEER), "the WARN must name the peer, got: $message")
        assertTrue(message.contains("rate-limit.trusted-proxies"), "the WARN must name the config key, got: $message")
        assertTrue(message.contains("RATE_LIMIT_TRUSTED_PROXIES"), "the WARN must name the env var, got: $message")
        assertTrue(!message.contains(FORWARDED_CLIENT), "the WARN must never log the header value, got: $message")
    }

    @Test
    fun `an untrusted peer without X-Forwarded-For never warns`() {
        val filter = RateLimitFilter()

        postLogin(filter, UNTRUSTED_PEER_WITHOUT_XFF)

        assertEquals(0, warnEvents().size, "a request without X-Forwarded-For must not warn, got ${warnEvents()}")
    }

    @Test
    fun `a trusted peer that sends X-Forwarded-For never warns`() {
        val filter = RateLimitFilter()

        postLogin(filter, TRUSTED_PEER, FORWARDED_CLIENT)

        assertEquals(0, warnEvents().size, "a trusted proxy's X-Forwarded-For must not warn, got ${warnEvents()}")
    }
}
