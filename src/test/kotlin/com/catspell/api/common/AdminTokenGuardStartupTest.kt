package com.catspell.api.common

import com.catspell.api.common.exception.AdminAuthException
import com.catspell.api.common.security.AdminTokenGuard
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Proves the D-13 (WR-08) startup rule on the operator shared secret: a blank `app.invite.admin-token` starts and
 * denies everything, a non-blank token under 32 characters fails startup without echoing the value, and 32 or more
 * characters starts normally. Plain JUnit + ApplicationContextRunner, no Testcontainers.
 */
class AdminTokenGuardStartupTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(AdminTokenGuard::class.java)

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    @Test
    fun `blank admin token starts the context`() {
        runner.withPropertyValues("app.invite.admin-token=").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(AdminTokenGuard::class.java)
        }
    }

    @Test
    fun `31-character admin token fails startup without revealing the token`() {
        val shortToken = "s".repeat(30) + "Z"
        runner.withPropertyValues("app.invite.admin-token=$shortToken").run { context ->
            assertThat(context).hasFailed()
            val root = rootCause(requireNotNull(context.startupFailure))
            assertThat(root).isInstanceOf(IllegalStateException::class.java)
            assertThat(root.message).contains("at least 32 characters")
            assertThat(root.message).doesNotContain(shortToken)
        }
    }

    @Test
    fun `32-character admin token starts the context`() {
        runner.withPropertyValues("app.invite.admin-token=${"k".repeat(32)}").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(AdminTokenGuard::class.java)
        }
    }

    @Test
    fun `guard with a 32-character token accepts only the exact token`() {
        val token = "0123456789abcdef0123456789ABCDEF"
        val guard = AdminTokenGuard(token)

        assertThatCode { guard.require(token) }.doesNotThrowAnyException()
        assertThatThrownBy { guard.require("x".repeat(32)) }.isInstanceOf(AdminAuthException::class.java)
        assertThatThrownBy { guard.require(null) }.isInstanceOf(AdminAuthException::class.java)
    }
}
