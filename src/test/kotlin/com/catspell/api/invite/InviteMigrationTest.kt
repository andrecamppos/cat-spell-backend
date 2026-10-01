package com.catspell.api.invite

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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Proves the V23 schema (INV-04 schema): boots with Flyway ENABLED and Hibernate ddl-auto=validate so
 * the real V23 DDL runs against a Postgres database and the Invite/Referral entities are validated
 * against it. The default test profile disables Flyway and uses create-drop, so this test overrides
 * both via @TestPropertySource.
 *
 * ISOLATION: every other integration context shares the one `catspell` database on the Testcontainers
 * Postgres using Hibernate ddl-auto=create-drop. A Flyway-enabled context pointed at that same database
 * is order-dependent — if a create-drop context populates `public` first, Flyway refuses to migrate
 * ("non-empty schema but no schema history table"), and running Flyway's own clean on the shared DB
 * would wipe schema out from under the other cached contexts. To stay deterministic AND leave the
 * shared DB untouched, this test runs against a dedicated, freshly (re)created database on the SAME
 * container; Flyway migrates it from empty (V3 installs PostGIS there), so no clean is ever needed.
 *
 * Column/constraint shape is introspected via jdbcTemplate; the referrals UNIQUE(invitee_id) and
 * chk_referrals_no_self CHECK are proven by asserting violating inserts are rejected.
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
    ]
)
class InviteMigrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        private const val MIGRATION_DB = "invite_migration_test"

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

    private fun insertUser(id: UUID, email: String) {
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            id, email, "hash", Timestamp.from(now), Timestamp.from(now)
        )
    }

    private fun insertInvite(id: UUID, codeHash: String) {
        jdbcTemplate.update("INSERT INTO invites (id, code_hash) VALUES (?, ?)", id, codeHash)
    }

    private fun insertReferral(referrerId: UUID, inviteeId: UUID, inviteId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO referrals (id, referrer_id, invitee_id, invite_id) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), referrerId, inviteeId, inviteId
        )
    }

    @Test
    fun `invites table has expected column nullability`() {
        assertEquals("NO", isNullable("invites", "code_hash"), "code_hash must be NOT NULL")
        assertEquals("YES", isNullable("invites", "referrer_user_id"), "referrer_user_id must be nullable (bootstrap codes)")
        assertEquals("YES", isNullable("invites", "consumed_at"), "consumed_at must be nullable (NULL = unconsumed)")
        assertNotNull(isNullable("invites", "consumed_by"), "consumed_by column must exist")
        assertNotNull(isNullable("invites", "created_at"), "created_at column must exist")
    }

    @Test
    fun `invites code_hash has a UNIQUE constraint`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
            WHERE c.conrelid = 'invites'::regclass AND c.contype = 'u' AND a.attname = 'code_hash'
            """.trimIndent(),
            Int::class.java
        )
        assertEquals(1, count, "invites.code_hash must be covered by a UNIQUE constraint")
    }

    @Test
    fun `referrals FK columns are NOT NULL and the named constraints exist`() {
        assertEquals("NO", isNullable("referrals", "referrer_id"))
        assertEquals("NO", isNullable("referrals", "invitee_id"))
        assertEquals("NO", isNullable("referrals", "invite_id"))
        assertEquals(1, constraintCount("chk_referrals_no_self"), "chk_referrals_no_self CHECK must exist")
        assertEquals(1, constraintCount("uq_referrals_invitee"), "uq_referrals_invitee UNIQUE must exist")
    }

    @Test
    fun `two referrals with the same invitee_id violate uq_referrals_invitee`() {
        val referrerA = UUID.randomUUID()
        val referrerB = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        val invite = UUID.randomUUID()
        insertUser(referrerA, "ref-a@example.com")
        insertUser(referrerB, "ref-b@example.com")
        insertUser(invitee, "invitee@example.com")
        insertInvite(invite, "hash-${UUID.randomUUID()}")

        insertReferral(referrerA, invitee, invite)
        assertThrows(DataIntegrityViolationException::class.java) {
            insertReferral(referrerB, invitee, invite)
        }
    }

    @Test
    fun `a referral with referrer_id equal to invitee_id violates chk_referrals_no_self`() {
        val user = UUID.randomUUID()
        val invite = UUID.randomUUID()
        insertUser(user, "self@example.com")
        insertInvite(invite, "hash-${UUID.randomUUID()}")

        assertThrows(DataIntegrityViolationException::class.java) {
            insertReferral(user, user, invite)
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
