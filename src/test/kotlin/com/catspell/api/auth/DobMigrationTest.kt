package com.catspell.api.auth

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Proves AGE-03: the V22 DOB relocation grandfathers pre-existing accounts. A user with a NULL
 * users.date_of_birth (registered before DOB collection, or never completed a profile) remains able
 * to log in and is NOT age-gated. The DOB backfill's `WHERE date_of_birth IS NULL` guard is idempotent
 * (a second run affects 0 rows). DOBs are relocated verbatim — no under-18 handling (D-12).
 *
 * Note: user_profiles.date_of_birth was dropped by V22, so this test drives the guard semantics against
 * users directly rather than the profile-sourced backfill statement.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DobMigrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    private fun insertUser(email: String, dateOfBirth: LocalDate?, verified: Boolean = true) {
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password_hash, created_at, updated_at, email_verified_at, date_of_birth) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            email,
            passwordEncoder.encode("password123"),
            Timestamp.from(now.minus(30, ChronoUnit.DAYS)),
            Timestamp.from(now),
            if (verified) Timestamp.from(now) else null,
            dateOfBirth?.let { Date.valueOf(it) }
        )
    }

    private fun dobOf(email: String): LocalDate? =
        jdbcTemplate.queryForObject("SELECT date_of_birth FROM users WHERE email = ?", Date::class.java, email)
            ?.toLocalDate()

    @Test
    fun `AGE-03 - grandfathered NULL-DOB account stays NULL, can log in, and is not age-gated`() {
        val email = "grandfather-null-dob@example.com"
        insertUser(email, dateOfBirth = null)

        assertNull(dobOf(email), "grandfathered account must start with a NULL DOB")

        // Login never invokes the AgeVerifier — the age gate is register-only — so a NULL-DOB
        // existing account logs in normally and is not rejected.
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)

        assertNull(dobOf(email), "login must not backfill or mutate the grandfathered NULL DOB")
    }

    @Test
    fun `AGE-03 - a completed account ends with a non-null DOB and can log in`() {
        val email = "backfilled-dob@example.com"
        val dob = LocalDate.now().minusYears(30)
        insertUser(email, dateOfBirth = dob)

        assertEquals(dob, dobOf(email), "a completed/backfilled account must carry its DOB on users")

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password123"}""")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `AGE-03 - the WHERE date_of_birth IS NULL backfill guard is idempotent`() {
        val nullEmail = "backfill-null@example.com"
        val setEmail = "backfill-set@example.com"
        val presetDob = LocalDate.now().minusYears(40)

        insertUser(nullEmail, dateOfBirth = null)
        insertUser(setEmail, dateOfBirth = presetDob)

        val backfillSql = "UPDATE users SET date_of_birth = ? WHERE date_of_birth IS NULL"
        val fill = Date.valueOf(LocalDate.now().minusYears(25))

        val firstRun = jdbcTemplate.update(backfillSql, fill)
        assertTrue(firstRun >= 1, "first run must backfill the NULL-DOB row")

        // The already-set row keeps its original DOB (WHERE IS NULL guard leaves it untouched).
        assertEquals(presetDob, dobOf(setEmail), "an already-set DOB must not be overwritten")

        // Re-running is a no-op now that no NULL DOBs remain.
        val secondRun = jdbcTemplate.update(backfillSql, fill)
        assertEquals(0, secondRun, "re-running the backfill must affect 0 rows (idempotent)")
    }
}
