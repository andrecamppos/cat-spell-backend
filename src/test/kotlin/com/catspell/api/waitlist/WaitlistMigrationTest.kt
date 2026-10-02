package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.sql.DriverManager
import java.util.UUID

/**
 * Proves the V24 schema (D-03 dedupe key, D-08 hash-only token column, PENDING/CONFIRMED/INVITED status set):
 * boots with Flyway ENABLED and Hibernate ddl-auto=validate so the real V1..V24 DDL runs against an empty
 * Postgres database and the WaitlistEntry entity is validated against it.
 *
 * ISOLATION: same pattern as InviteMigrationTest, but on its OWN private database name
 * (`waitlist_migration_test`) so the two suites' DROP/CREATE DATABASE never race each other and neither
 * touches the shared `catspell` database (T-17-29). This class deliberately does NOT extend
 * BaseIntegrationTest — that base would point the context back at the shared database.
 *
 * Nothing truncates between tests here, so every insert uses a unique address.
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
    ]
)
class WaitlistMigrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        private const val MIGRATION_DB = "waitlist_migration_test"
        private const val TABLE = "waitlist_entries"

        @JvmStatic
        @DynamicPropertySource
        fun migrationDatasource(registry: DynamicPropertyRegistry) {
            // Reuse the shared container but point this context at a private, pristine database so the
            // real migration set applies from empty without touching (or being disturbed by) `catspell`.
            val base = BaseIntegrationTest.postgres
            DriverManager.getConnection(base.jdbcUrl, base.username, base.password).use { conn ->
                conn.createStatement().use { st ->
                    st.execute("DROP DATABASE IF EXISTS $MIGRATION_DB")
                    st.execute("CREATE DATABASE $MIGRATION_DB")
                }
            }
            val migrationUrl = base.jdbcUrl.replaceFirst("/${base.databaseName}", "/$MIGRATION_DB")
            registry.add("spring.datasource.url") { migrationUrl }
            registry.add("spring.datasource.username", base::getUsername)
            registry.add("spring.datasource.password", base::getPassword)
            // The full application context still needs the S3/MinIO config to start.
            registry.add("storage.s3.endpoint") {
                "http://${BaseIntegrationTest.minio.host}:${BaseIntegrationTest.minio.getMappedPort(9000)}"
            }
            registry.add("storage.s3.access-key") { "catspell" }
            registry.add("storage.s3.secret-key") { "catspell123" }
        }
    }

    private fun uniqueAddress(prefix: String): String = "$prefix-${UUID.randomUUID()}@example.com"

    private fun insertMinimal(email: String, normalizedEmail: String = email) {
        jdbcTemplate.update(
            "INSERT INTO waitlist_entries (email, normalized_email) VALUES (?, ?)",
            email, normalizedEmail
        )
    }

    @Test
    fun `waitlist_entries columns have the expected nullability`() {
        val expected = linkedMapOf(
            "email" to "NO",
            "normalized_email" to "NO",
            "status" to "NO",
            "confirm_token_hash" to "YES",
            "confirm_token_expires_at" to "YES",
            "confirmed_at" to "YES",
            "invited_at" to "YES",
            "created_at" to "NO",
            "updated_at" to "NO"
        )
        for ((column, nullable) in expected) {
            assertEquals(nullable, isNullable(TABLE, column), "$TABLE.$column is_nullable")
        }
    }

    @Test
    fun `waitlist_entries has exactly the ten expected columns and no others`() {
        // D-05: email-only storage. A literal set (not derived from the entity or V24) so drift in either fails here,
        // including any added IP, user-agent, referrer or profile column.
        val expected = setOf(
            "id",
            "email",
            "normalized_email",
            "status",
            "confirm_token_hash",
            "confirm_token_expires_at",
            "confirmed_at",
            "invited_at",
            "created_at",
            "updated_at"
        )
        val actual = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
            String::class.java, TABLE
        ).toSet()

        assertEquals(expected, actual, "$TABLE must have exactly the D-05 email-only column set")
    }

    @Test
    fun `the three named waitlist constraints exist`() {
        assertEquals(1, constraintCount("uq_waitlist_entries_normalized_email"), "normalized_email UNIQUE must exist")
        assertEquals(1, constraintCount("uq_waitlist_entries_confirm_token_hash"), "confirm_token_hash UNIQUE must exist")
        assertEquals(1, constraintCount("chk_waitlist_entries_status"), "status CHECK must exist")
    }

    @Test
    fun `a duplicate normalized_email violates uq_waitlist_entries_normalized_email`() {
        val normalized = uniqueAddress("dup")
        insertMinimal(normalized, normalized)

        assertThrows(DataIntegrityViolationException::class.java) {
            insertMinimal(normalized.uppercase(), normalized)
        }
    }

    @Test
    fun `a status outside PENDING CONFIRMED INVITED violates chk_waitlist_entries_status`() {
        val address = uniqueAddress("bogus")

        assertThrows(DataIntegrityViolationException::class.java) {
            jdbcTemplate.update(
                "INSERT INTO waitlist_entries (email, normalized_email, status) VALUES (?, ?, ?)",
                address, address, "BOGUS"
            )
        }
    }

    @Test
    fun `a row inserted with only email and normalized_email defaults to PENDING with a created_at`() {
        val address = uniqueAddress("default")
        insertMinimal(address)

        val row = jdbcTemplate.queryForMap(
            "SELECT status, created_at, updated_at, confirm_token_hash FROM waitlist_entries WHERE normalized_email = ?",
            address
        )
        assertEquals("PENDING", row["status"])
        assertNotNull(row["created_at"], "created_at must default to NOW()")
        assertNotNull(row["updated_at"], "updated_at must default to NOW()")
        assertNull(row["confirm_token_hash"])
    }

    @Test
    fun `two rows with a NULL confirm_token_hash are allowed`() {
        val first = uniqueAddress("null-hash-a")
        val second = uniqueAddress("null-hash-b")
        insertMinimal(first)
        insertMinimal(second)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM waitlist_entries WHERE normalized_email IN (?, ?) AND confirm_token_hash IS NULL",
            Int::class.java, first, second
        )
        assertEquals(2, count, "UNIQUE(confirm_token_hash) must not reject multiple NULLs")
    }

    @Test
    fun `a duplicate non-null confirm_token_hash violates uq_waitlist_entries_confirm_token_hash`() {
        val hash = "a".repeat(64)
        val first = uniqueAddress("hash-a")
        val second = uniqueAddress("hash-b")
        jdbcTemplate.update(
            "INSERT INTO waitlist_entries (email, normalized_email, confirm_token_hash) VALUES (?, ?, ?)",
            first, first, hash
        )

        assertThrows(DataIntegrityViolationException::class.java) {
            jdbcTemplate.update(
                "INSERT INTO waitlist_entries (email, normalized_email, confirm_token_hash) VALUES (?, ?, ?)",
                second, second, hash
            )
        }
    }

    private fun isNullable(table: String, column: String): String? =
        jdbcTemplate.queryForObject(
            "SELECT is_nullable FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            String::class.java, table, column
        )

    private fun constraintCount(name: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?",
            Int::class.java, name
        ) ?: 0
}
