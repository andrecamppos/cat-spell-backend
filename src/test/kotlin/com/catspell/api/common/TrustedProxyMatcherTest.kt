package com.catspell.api.common

import com.catspell.api.common.security.TrustedProxyMatcher
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WR-09 / T-17-34 / T-17-35: trusted-proxy membership is decided by parsed address value or CIDR prefix, only within
 * one address family, and only over strict IP literals, so header text never reaches DNS. Pure class, no Spring.
 */
class TrustedProxyMatcherTest {

    private val defaultMatcher = TrustedProxyMatcher(listOf("127.0.0.1", "::1"))

    @Test
    fun `default loopback entries match every textual form of loopback`() {
        listOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1").forEach {
            assertTrue(defaultMatcher.matches(it), "$it must be trusted by the default loopback entries")
        }
        listOf("127.0.0.2", "10.0.0.1", "::2").forEach {
            assertFalse(defaultMatcher.matches(it), "$it must not be trusted by the default loopback entries")
        }
    }

    @Test
    fun `CIDR ranges trust exactly their edges and nothing one step outside`() {
        val matcher = TrustedProxyMatcher(listOf("10.88.0.0/16"))
        assertTrue(matcher.matches("10.88.0.0"), "the first address of 10.88.0.0/16 must be trusted")
        assertTrue(matcher.matches("10.88.255.255"), "the last address of 10.88.0.0/16 must be trusted")
        assertFalse(matcher.matches("10.87.255.255"), "one below 10.88.0.0/16 must not be trusted")
        assertFalse(matcher.matches("10.89.0.0"), "one above 10.88.0.0/16 must not be trusted")

        val everyIpv4 = TrustedProxyMatcher(listOf("0.0.0.0/0"))
        assertTrue(everyIpv4.matches("203.0.113.1"), "0.0.0.0/0 must trust every IPv4 address")
        assertFalse(everyIpv4.matches("::1"), "0.0.0.0/0 must not trust an IPv6 address")
    }

    @Test
    fun `an address never matches a range of the other family and never throws`() {
        val ipv6Range = TrustedProxyMatcher(listOf("2001:db8::/40"))
        assertDoesNotThrow { ipv6Range.matches("10.0.0.1") }
        assertFalse(ipv6Range.matches("10.0.0.1"), "an IPv4 address must not match an IPv6 range")

        val ipv4Range = TrustedProxyMatcher(listOf("10.0.0.0/8"))
        assertFalse(ipv4Range.matches("a00::1"), "an IPv6 address must not match an IPv4 range")
    }

    @Test
    fun `strings that are not strict IP literals are never trusted`() {
        val everything = TrustedProxyMatcher(listOf("0.0.0.0/0", "::/0"))
        listOf(
            "", "   ", "unknown", "localhost", "example.com", "999.1.1.1", "1.2.3", "010.0.0.1", "[::1]", "fe80::1%eth0"
        ).forEach {
            assertFalse(TrustedProxyMatcher.isIpLiteral(it), "'$it' must not be accepted as an IP literal")
            assertDoesNotThrow { everything.matches(it) }
            assertFalse(everything.matches(it), "'$it' must never be trusted, even by a match-all range")
        }
        assertFalse(everything.matches(null), "a null address must never be trusted")
    }

    @Test
    fun `an invalid trusted-proxies entry fails construction`() {
        listOf("proxy.internal", "10.0.0.0/33", "10.0.0.0/abc", "10.0.0.0/8/8", "::1/129").forEach { entry ->
            val error = assertThrows(IllegalArgumentException::class.java) { TrustedProxyMatcher(listOf(entry)) }
            assertTrue(
                error.message!!.contains("'$entry'"),
                "the error must name the bad entry '$entry', got: ${error.message}"
            )
        }
    }

    @Test
    fun `blank entries are ignored`() {
        val matcher = TrustedProxyMatcher(listOf(" ", ""))
        listOf("127.0.0.1", "::1", "10.0.0.1").forEach {
            assertFalse(matcher.matches(it), "a matcher built only from blank entries must trust nothing, got $it")
        }
    }
}
