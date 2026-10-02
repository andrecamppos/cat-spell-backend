package com.catspell.api.waitlist.model

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * Public join body: email only (D-05). The submitted value is trimmed BEFORE bean validation runs, so a pasted
 * address with surrounding whitespace is accepted (D-03 normalization starts with trim) instead of failing @Email.
 * @NotBlank is required because Hibernate Validator's @Email accepts the empty string (RESEARCH Pitfall 9).
 */
class JoinWaitlistRequest(email: String) {
    @field:NotBlank
    @field:Email(message = "must be a valid email address")
    @field:Size(max = 255)
    val email: String = email.trim()
}

/**
 * One row of the operator waitlist list (D-09). Deliberately omits the confirm token hash, its expiry, and the
 * normalized dedupe key: the operator only needs to know who is waiting and since when.
 */
data class WaitlistEntryResponse(
    val id: UUID,
    val email: String,
    val status: WaitlistStatus,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val invitedAt: Instant?
)

/**
 * 201 body of a waitlist conversion (D-09/D-10). [code] is the raw single-use invite code, returned exactly once so
 * the operator can deliver it by hand while no real email provider is configured; only its SHA-256 is stored.
 */
data class ConvertWaitlistResponse(
    val entryId: UUID,
    val code: String
)
