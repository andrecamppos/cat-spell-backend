package com.catspell.api.age

import com.catspell.api.common.exception.UnderMinimumAgeException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LocalAgeVerifierTest {

    private val verifier = LocalAgeVerifier(18)

    @Test
    fun `exactly 18 today passes`() {
        assertDoesNotThrow { verifier.requireAdult(LocalDate.now().minusYears(18)) }
    }

    @Test
    fun `18 years and 1 day passes`() {
        assertDoesNotThrow { verifier.requireAdult(LocalDate.now().minusYears(18).minusDays(1)) }
    }

    @Test
    fun `17 years and 364 days throws`() {
        assertThrows(UnderMinimumAgeException::class.java) {
            verifier.requireAdult(LocalDate.now().minusYears(18).plusDays(1))
        }
    }

    @Test
    fun `clearly under 18 throws`() {
        assertThrows(UnderMinimumAgeException::class.java) {
            verifier.requireAdult(LocalDate.now().minusYears(10))
        }
    }
}
