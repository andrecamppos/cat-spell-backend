package com.catspell.api.common.security

import com.catspell.api.common.exception.AdminAuthException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * The single shared `X-Admin-Token` check for every operator endpoint (invite issuance, waitlist admin — D-03,
 * D-09). Operator routes are permitAll in SecurityConfig with no admin JWT/role, so [require] is the ENTIRE
 * access-control boundary for them.
 *
 * It is deny-by-default: a blank `app.invite.admin-token` rejects every request, and no default token ships.
 * The comparison is constant-time via [MessageDigest.isEqual] — never use ==/String.equals for the secret, and
 * never log it.
 */
@Component
class AdminTokenGuard(
    @Value("\${app.invite.admin-token:}") private val adminToken: String
) {

    /** Throws [AdminAuthException] (generic 401) unless [provided] matches the configured admin token. */
    fun require(provided: String?) {
        // Deny-by-default when unconfigured; constant-time compare — never ==/String.equals (Pitfall 4, D-03).
        if (adminToken.isBlank() ||
            provided == null ||
            !MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), adminToken.toByteArray(Charsets.UTF_8))
        ) {
            throw AdminAuthException()
        }
    }
}
