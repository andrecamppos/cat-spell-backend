package com.catspell.api

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.utility.DockerImageName

abstract class BaseIntegrationTest {

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    /**
     * Promote a freshly-registered (unverified) user past the Phase 11 login hard-gate by stamping
     * email_verified_at directly. Tests use this instead of bypassing the gate, so the real gate
     * behavior stays exercised (VERIFY-03).
     */
    protected fun markEmailVerified(email: String) {
        jdbcTemplate.update("UPDATE users SET email_verified_at = NOW() WHERE email = ?", email)
    }

    @BeforeEach
    fun cleanDatabase() {
        val tables = jdbcTemplate.queryForList(
            """
            SELECT tablename FROM pg_tables
            WHERE schemaname = 'public'
              AND tablename NOT IN ('spatial_ref_sys', 'flyway_schema_history')
            """.trimIndent(),
            String::class.java
        )
        if (tables.isNotEmpty()) {
            val joined = tables.joinToString(", ") { "\"$it\"" }
            jdbcTemplate.execute("TRUNCATE TABLE $joined RESTART IDENTITY CASCADE")
        }
    }

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
        ).withDatabaseName("catspell")
            .withUrlParam("currentSchema", "public")
            .apply { start() }

        @JvmStatic
        val minio: GenericContainer<*> = GenericContainer(DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "catspell")
            .withEnv("MINIO_ROOT_PASSWORD", "catspell123")
            .withCommand("server /data")
            .waitingFor(HttpWaitStrategy().forPort(9000).forPath("/minio/health/live").forStatusCode(200))
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("storage.s3.endpoint") { "http://${minio.host}:${minio.getMappedPort(9000)}" }
            registry.add("storage.s3.access-key") { "catspell" }
            registry.add("storage.s3.secret-key") { "catspell123" }
        }
    }
}
