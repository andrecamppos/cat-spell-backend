package com.catspell.api.waitlist.service

/**
 * D-03 normalization for the waitlist dedupe key and per-email throttle key: trim + lowercase, then strip a
 * local-part `+suffix` (split on the LAST '@'). Dots are preserved and no provider-specific alias rules apply, so
 * distinct mailboxes are never merged. Never throws: input without a usable '@' is returned trimmed + lowercased
 * (request validation rejects it before it reaches the service).
 */
object WaitlistEmailNormalizer {

    fun normalize(raw: String): String {
        val lowered = raw.trim().lowercase()
        val at = lowered.lastIndexOf('@')
        if (at <= 0) return lowered
        val local = lowered.substring(0, at)
        val domain = lowered.substring(at + 1)
        val plus = local.indexOf('+')
        val base = if (plus > 0) local.substring(0, plus) else local
        return "$base@$domain"
    }
}
