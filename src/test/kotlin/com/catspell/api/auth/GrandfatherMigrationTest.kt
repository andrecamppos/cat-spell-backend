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
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Proves VERIFY-05: the V17 grandfather backfill unlocks pre-existing accounts. A legacy user row with
 * email_verified_at IS NULL is stamped with its created_at by the backfill statement and can then log in
 * (the Phase 11 login hard-gate no longer trips). The backfill is idempotent and a no-op on already-set rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GrandfatherMigrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    private val backfillSql =
        "UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL"

    private fun insertLegacyUser(email: String, createdAt: Instant, verifiedAt: Instant? = null) {
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password_hash, created_at, updated_at, email_verified_at) VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            email,
            passwordEncoder.encode("password123"),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt),
            verifiedAt?.let { Timestamp.from(it) }
        )
    }

    private fun verifiedAtOf(email: String): Timestamp? =
        jdbcTemplate.queryForObject(
            "SELECT email_verified_at FROM users WHERE email = ?",
            Timestamp::class.java,
            email
        )

    private fun createdAtOf(email: String): Timestamp =
        jdbcTemplate.queryForObject(
            "SELECT created_at FROM users WHERE email = ?",
            Timestamp::class.java,
            email
        )!!

    @Test
    fun `VERIFY-05 - backfill grandfathers a NULL-verified legacy user who can then log in`() {
        val email = "grandfather-legacy@example.com"
        val createdAt = Instant.now().minus(30, ChronoUnit.DAYS)
        insertLegacyUser(email, createdAt)

        // Pre-condition: unverified, so login is gated.
        assertNull(verifiedAtOf(email), "legacy row must start unverified")

        val rowsAffected = jdbcTemplate.update(backfillSql)
        assertTrue(rowsAffected >= 1, "backfill must touch the NULL-verified legacy row")

        // The row is now verified with email_verified_at = created_at.
        assertEquals(createdAtOf(email), verifiedAtOf(email), "backfill must set email_verified_at = created_at")

        // The grandfathered user can log in (the 403 gate no longer trips).
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `VERIFY-05 - backfill is idempotent and does not overwrite an already-verified row`() {
        val nullEmail = "grandfather-null@example.com"
        val setEmail = "grandfather-set@example.com"
        val createdAt = Instant.now().minus(10, ChronoUnit.DAYS)
        val alreadyVerifiedAt = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)

        insertLegacyUser(nullEmail, createdAt)
        insertLegacyUser(setEmail, createdAt, verifiedAt = alreadyVerifiedAt)

        val firstRun = jdbcTemplate.update(backfillSql)
        assertTrue(firstRun >= 1, "first run backfills the NULL row")

        // The already-set row keeps its original verified instant (WHERE IS NULL guard), not created_at.
        assertEquals(
            Timestamp.from(alreadyVerifiedAt),
            verifiedAtOf(setEmail),
            "an already-verified row must not be overwritten"
        )

        // Re-running is a no-op now that no NULL rows remain.
        val secondRun = jdbcTemplate.update(backfillSql)
        assertEquals(0, secondRun, "re-running the backfill must affect 0 rows (idempotent)")
    }
}
