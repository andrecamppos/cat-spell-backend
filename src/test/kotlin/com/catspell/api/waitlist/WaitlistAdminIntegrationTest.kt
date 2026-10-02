package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val ADMIN_WAITLIST_URL = "/api/admin/waitlist"
private const val ADMIN_TOKEN_HEADER = "X-Admin-Token"
private const val ADMIN_SECRET = "test-admin-secret"

/**
 * Proves the operator waitlist list (WAIT-04, D-09) with a CONFIGURED admin token: confirmed-only default ordered
 * by confirmed_at, case-insensitive status filter, bounded limit, no token material in the response, and the
 * shared-secret guard running before any parameter validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.invite.admin-token=$ADMIN_SECRET"])
class WaitlistAdminIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    private val confirmedEarly = UUID.randomUUID()
    private val confirmedLate = UUID.randomUUID()
    private val pending = UUID.randomUUID()
    private val invited = UUID.randomUUID()

    private fun seed(id: UUID, email: String, status: String, confirmedAt: Instant?, invitedAt: Instant?, createdAt: Instant) {
        jdbcTemplate.update(
            """
            INSERT INTO waitlist_entries (id, email, normalized_email, status, confirm_token_hash, confirm_token_expires_at,
                                          confirmed_at, invited_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            email,
            email.lowercase(),
            status,
            "hash-$id".take(64),
            Timestamp.from(createdAt.plus(7, ChronoUnit.DAYS)),
            confirmedAt?.let { Timestamp.from(it) },
            invitedAt?.let { Timestamp.from(it) },
            Timestamp.from(createdAt),
            Timestamp.from(createdAt)
        )
    }

    @BeforeEach
    fun seedEntries() {
        val base = Instant.now().minus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        // Insert the later confirmation first so ordering is proven by confirmed_at, not by insertion order.
        seed(confirmedLate, "late@example.com", "CONFIRMED", base.plus(2, ChronoUnit.DAYS), null, base)
        seed(confirmedEarly, "early@example.com", "CONFIRMED", base.plus(1, ChronoUnit.DAYS), null, base.plus(1, ChronoUnit.HOURS))
        seed(pending, "pending@example.com", "PENDING", null, null, base)
        seed(invited, "invited@example.com", "INVITED", base.plus(1, ChronoUnit.DAYS), base.plus(3, ChronoUnit.DAYS), base)
    }

    @Test
    fun `correct token with no params lists only confirmed entries ordered by confirmedAt without token material`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(confirmedEarly.toString()))
            .andExpect(jsonPath("$[0].email").value("early@example.com"))
            .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
            .andExpect(jsonPath("$[0].createdAt").exists())
            .andExpect(jsonPath("$[0].confirmedAt").exists())
            .andExpect(jsonPath("$[1].id").value(confirmedLate.toString()))
            .andExpect(jsonPath("$[0].confirmTokenHash").doesNotExist())
            .andExpect(jsonPath("$[0].confirmTokenExpiresAt").doesNotExist())
            .andExpect(jsonPath("$[0].normalizedEmail").doesNotExist())
    }

    @Test
    fun `status filter is case-insensitive`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).param("status", "CONFIRMED").header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(confirmedEarly.toString()))
            .andExpect(jsonPath("$[1].id").value(confirmedLate.toString()))
    }

    @Test
    fun `status pending lists only pending entries`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).param("status", "pending").header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(pending.toString()))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
    }

    @Test
    fun `status invited lists only invited entries with invitedAt`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).param("status", "invited").header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(invited.toString()))
            .andExpect(jsonPath("$[0].invitedAt").exists())
    }

    @Test
    fun `unknown status returns 400`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).param("status", "bogus").header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Bad Request"))
    }

    @Test
    fun `limit 1 returns only the earliest confirmation`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).param("limit", "1").header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(confirmedEarly.toString()))
    }

    @Test
    fun `limit 0 returns 400`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).param("limit", "0").header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `limit 501 returns 400`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).param("limit", "501").header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `missing token returns generic 401`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$[0]").doesNotExist())
    }

    @Test
    fun `wrong token returns generic 401`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).header(ADMIN_TOKEN_HEADER, "wrong-secret"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))
    }

    @Test
    fun `wrong token with an invalid status is 401 not 400`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).param("status", "bogus").header(ADMIN_TOKEN_HEADER, "wrong-secret"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))
    }
}

/**
 * Proves deny-by-default (T-17-18): with a blank app.invite.admin-token the permitAll list route returns 401 for
 * every request, including one carrying a plausible token, so no waitlist email is ever exposed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.invite.admin-token="])
class WaitlistAdminDenyByDefaultIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `list is 401 when admin-token is blank even with a plausible header`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL).header(ADMIN_TOKEN_HEADER, ADMIN_SECRET))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))
    }

    @Test
    fun `list is 401 when admin-token is blank and no header is sent`() {
        mockMvc.perform(get(ADMIN_WAITLIST_URL))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))
    }
}
