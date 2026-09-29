package com.catspell.api.age

import com.catspell.api.common.exception.UnderMinimumAgeException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.Period

/**
 * Default local implementation of [AgeVerifier]. Computes age via [Period] against the
 * server-local clock and enforces the single minimum-age rule (D-07). The interface is the
 * swap point for a future vendor implementation, so no conditional bean wiring is needed here.
 */
@Component
class LocalAgeVerifier(
    @Value("\${app.age.minimum-age:18}") private val minimumAge: Int
) : AgeVerifier {
    override fun requireAdult(dateOfBirth: LocalDate) {
        val age = Period.between(dateOfBirth, LocalDate.now()).years
        if (age < minimumAge) {
            throw UnderMinimumAgeException()
        }
    }
}
