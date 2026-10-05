package com.catspell.api.common.security

import com.catspell.api.common.exception.AdminAuthException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * The single constant-time `X-Admin-Token` check for every operator endpoint (invite issuance, waitlist admin — D-03,
 * D-09). Operator routes have no admin JWT/role. [AdminTokenFilter] calls [require] on every `/api/admin` path before
 * Spring Security and MVC run, which makes it the access boundary (D-11); each operator handler calls [require] again
 * as defense in depth. There is no second comparison anywhere else.
 *
 * It is deny-by-default: a blank `app.invite.admin-token` rejects every request, and no default token ships.
 * A non-blank token shorter than [MIN_ADMIN_TOKEN_LENGTH] characters fails application startup (D-13, WR-08), so a
 * weak, brute-forceable secret can never guard the operator routes. The startup message never contains the token.
 * The comparison is constant-time via [MessageDigest.isEqual] — never use ==/String.equals for the secret, and
 * never log it.
 */
@Component
class AdminTokenGuard(
    @Value("\${app.invite.admin-token:}") private val adminToken: String
) {

    init {
        // Fixed message, never interpolated: the token must not reach logs or the startup failure (T-18-15).
        check(adminToken.isBlank() || adminToken.length >= MIN_ADMIN_TOKEN_LENGTH) {
            "app.invite.admin-token must be blank (operator endpoints disabled) or at least 32 characters"
        }
    }

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

    companion object {
        /** Minimum length of a non-blank operator shared secret (D-13). */
        const val MIN_ADMIN_TOKEN_LENGTH = 32
    }
}
