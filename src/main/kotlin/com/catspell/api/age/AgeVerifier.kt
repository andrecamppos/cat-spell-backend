package com.catspell.api.age

import java.time.LocalDate

/**
 * Seam for the hard minimum-age gate. Call sites depend only on this interface, so a
 * vendor age-verification implementation can drop in later without call-site changes (D-03).
 *
 * Implementations throw [com.catspell.api.common.exception.UnderMinimumAgeException] when the
 * supplied date of birth implies an age below the configured minimum.
 */
interface AgeVerifier {
    fun requireAdult(dateOfBirth: LocalDate)
}
