package com.catspell.api.common.security

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Decides whether a peer address or an X-Forwarded-For hop is a configured trusted proxy (WR-09, T-17-34, T-17-35).
 *
 * Each entry is an exact IPv4/IPv6 address or a CIDR range (`10.0.0.0/8`, `2001:db8::/32`). Membership compares parsed
 * address bytes, so `::1` also trusts Tomcat's `0:0:0:0:0:0:0:1` and `::ffff:127.0.0.1` equals `127.0.0.1`. A candidate
 * never matches a range of the other address family. Only text that passes the private shape pre-filter
 * (`hasIpLiteralShape`) is ever handed to [InetAddress], so header text can never trigger a DNS lookup, and neither
 * [matches] nor [canonicalize] throws on request content.
 *
 * Built only from the JDK on purpose: Spring Security's IP matcher has no address-family check in its CIDR branch and
 * lets IPv4-shaped strings such as `999.1.1.1` through to DNS resolution.
 *
 * @param entries configured trusted-proxy entries; blank ones are skipped, and any other unparseable entry makes
 * construction throw [IllegalArgumentException] so a bad value fails startup instead of silently changing trust
 */
class TrustedProxyMatcher(entries: Collection<String>) {

    /** One trusted entry: the network address bytes and how many leading bits must match. */
    private class Range(private val network: ByteArray, private val prefixBits: Int) {

        /** True when [candidate] is the same address family and shares the first [prefixBits] bits. */
        fun contains(candidate: ByteArray): Boolean {
            if (candidate.size != network.size) return false
            val wholeBytes = prefixBits / 8
            for (i in 0 until wholeBytes) {
                if (candidate[i] != network[i]) return false
            }
            val remainingBits = prefixBits % 8
            if (remainingBits == 0) return true
            val mask = (0xFF shl (8 - remainingBits)) and 0xFF
            return (candidate[wholeBytes].toInt() and mask) == (network[wholeBytes].toInt() and mask)
        }
    }

    private val ranges: List<Range> = entries.map { it.trim() }.filter { it.isNotEmpty() }.map { parseEntry(it) }

    /**
     * Reports whether [address] is a trusted proxy.
     *
     * @param address a peer address or one X-Forwarded-For hop, exactly as received
     * @return true only for a strict IP literal inside a configured entry; false for null, blank or non-literal text
     */
    fun matches(address: String?): Boolean {
        if (address.isNullOrBlank() || !hasIpLiteralShape(address)) return false
        val candidate = parseLiteral(address) ?: return false
        return ranges.any { it.contains(candidate) }
    }

    private fun parseEntry(entry: String): Range {
        val parts = entry.split("/")
        if (parts.size > 2 || !hasIpLiteralShape(parts[0])) throw invalidEntry(entry)
        val network = parseLiteral(parts[0]) ?: throw invalidEntry(entry)
        val maxBits = network.size * 8
        if (parts.size == 1) return Range(network, maxBits)

        val prefixText = parts[1]
        if (prefixText.isEmpty() || prefixText.length > 3 || !prefixText.all { it in '0'..'9' }) {
            throw invalidEntry(entry)
        }
        val prefixBits = prefixText.toInt()
        if (prefixBits > maxBits) throw invalidEntry(entry)
        return Range(network, prefixBits)
    }

    private fun invalidEntry(entry: String) = IllegalArgumentException(
        "Invalid rate-limit.trusted-proxies entry '$entry': expected an IP address or CIDR range such as 10.0.0.0/8"
    )

    companion object {
        private const val IPV4_OCTET = "(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])"
        private val IPV4_LITERAL = Regex("$IPV4_OCTET(\\.$IPV4_OCTET){3}")
        private val IPV6_SHAPE = Regex("[0-9A-Fa-f:][0-9A-Fa-f:.]*")
        private const val MAX_IPV6_LITERAL_LENGTH = 45
        private val PORT = Regex("[0-9]{1,5}")

        /**
         * Canonicalizes one X-Forwarded-For hop to the address form Tomcat uses for `remoteAddr` (D-15, current WR-02).
         *
         * Accepted shapes, and nothing else:
         * - `[v6]` or `[v6]:port`: after `]` comes the end of the text, or `:` followed by 1-5 digits
         * - `a.b.c.d:port`: exactly one `:`, and the port is 1-5 digits
         * - a bare IPv4 or IPv6 literal
         *
         * A bare IPv6 address followed by a port without brackets (`2001:db8::1:80`) is ambiguous; it is treated as an
         * address, never as address plus port. The host part must pass the shape pre-filter before the JDK parses it,
         * so header text never triggers a DNS lookup. The result is re-rendered from the parsed bytes, so `::1` becomes
         * `0:0:0:0:0:0:0:1` and `::ffff:10.0.0.1` becomes `10.0.0.1`.
         *
         * @param hop one trimmed X-Forwarded-For hop, exactly as received
         * @return the canonical address text, or null when the hop is not one of the accepted shapes or does not parse
         */
        fun canonicalize(hop: String): String? {
            val host = when {
                hop.startsWith("[") -> {
                    val end = hop.indexOf(']')
                    if (end < 0) return null
                    val rest = hop.substring(end + 1)
                    if (rest.isNotEmpty() && !(rest.startsWith(":") && PORT.matches(rest.substring(1)))) return null
                    hop.substring(1, end)
                }
                hop.count { it == ':' } == 1 -> {
                    val (address, port) = hop.split(':')
                    if (!PORT.matches(port)) return null
                    address
                }
                else -> hop
            }
            if (!hasIpLiteralShape(host)) return null
            return parseLiteral(host)?.let { InetAddress.getByAddress(it).hostAddress }
        }

        /**
         * Shape pre-filter only (current IN-02): guarantees the JDK parses [value] as a literal without any name
         * resolution. It does NOT prove [value] is a valid address (`1.2.3.4:5678` passes the IPv6 branch), so a
         * caller must still parse it with [parseLiteral].
         *
         * @param value candidate text
         * @return true for four decimal octets (0-255, no leading zeros), or for an IPv6-shaped string of at most 45
         * characters that contains ':' and holds only hex digits, ':' and '.'; the JDK always treats the latter as an
         * IPv6 literal and never resolves it
         */
        private fun hasIpLiteralShape(value: String): Boolean =
            IPV4_LITERAL.matches(value) ||
                (value.length <= MAX_IPV6_LITERAL_LENGTH && value.contains(':') && IPV6_SHAPE.matches(value))

        /** Parses a value that already passed [hasIpLiteralShape]; returns null when the JDK rejects it. */
        private fun parseLiteral(literal: String): ByteArray? = try {
            InetAddress.getByName(literal).address
        } catch (e: UnknownHostException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
