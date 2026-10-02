package com.catspell.api.waitlist.event

/**
 * Published inside the join transaction when a PENDING entry gets a fresh confirm token, and consumed by the async
 * AFTER_COMMIT [WaitlistEmailListener]. It carries primitives only (never the JPA entity) so the async listener has
 * no lazy-load dependency on a closed persistence context. The raw token lives only here and in the outbound email
 * (D-08); [toString] is redacted so Spring's event debug logging can never print the token or the address.
 */
data class WaitlistConfirmationRequestedEvent(
    val email: String,
    val rawToken: String
) {
    override fun toString(): String = "WaitlistConfirmationRequestedEvent(email=***, rawToken=***)"
}
