package com.catspell.api.invite

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

/**
 * Proves INV-02 issuance + referrer validation with a CONFIGURED admin token. The shared-secret header is the
 * sole access barrier (the route is permitAll).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.invite.admin-token=test-admin-secret"])
class InviteAdminEndpointIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository

    private fun inviteCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invites", Int::class.java)!!

    private fun sha256Hex(s: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))

    @Test
    fun `correct token issues 201 with a code stored hashed`() {
        val result = mockMvc.perform(
            post("/api/admin/invites")
                .header("X-Admin-Token", "test-admin-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").isNotEmpty)
            .andReturn()

        val code = objectMapper.readTree(result.response.contentAsString)["code"].asText()
        assertEquals(1, inviteCount())
        val storedHash = jdbcTemplate.queryForObject("SELECT code_hash FROM invites", String::class.java)
        assertEquals(sha256Hex(code), storedHash, "the issued code must be stored only as its SHA-256 hash")
    }

    @Test
    fun `wrong token returns 401 and writes no invite`() {
        mockMvc.perform(
            post("/api/admin/invites")
                .header("X-Admin-Token", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isUnauthorized)

        assertEquals(0, inviteCount())
    }

    @Test
    fun `missing token returns 401 and writes no invite`() {
        mockMvc.perform(
            post("/api/admin/invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isUnauthorized)

        assertEquals(0, inviteCount())
    }

    @Test
    fun `unknown referrerUserId returns 400 and writes no invite`() {
        mockMvc.perform(
            post("/api/admin/invites")
                .header("X-Admin-Token", "test-admin-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"referrerUserId":"${UUID.randomUUID()}"}""")
        )
            .andExpect(status().isBadRequest)

        assertEquals(0, inviteCount())
    }

    @Test
    fun `a real referrerUserId returns 201`() {
        val referrer = userRepository.save(User(email = "admin-referrer@example.com", passwordHash = "hash"))
        mockMvc.perform(
            post("/api/admin/invites")
                .header("X-Admin-Token", "test-admin-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"referrerUserId":"${referrer.id}"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").isNotEmpty)

        assertEquals(1, inviteCount())
    }
}

/**
 * Proves deny-by-default (Pitfall 4): with a blank/unset app.invite.admin-token, the permitAll route denies
 * EVERY request regardless of the header, and mints nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.invite.admin-token="])
class InviteAdminEndpointIntegrationTestDenyByDefault : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    private fun inviteCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invites", Int::class.java)!!

    @Test
    fun `any post is 401 when admin-token is blank even with a header`() {
        mockMvc.perform(
            post("/api/admin/invites")
                .header("X-Admin-Token", "anything")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isUnauthorized)

        assertEquals(0, inviteCount())
    }

    @Test
    fun `post with no header is 401 when admin-token is blank`() {
        mockMvc.perform(
            post("/api/admin/invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isUnauthorized)

        assertEquals(0, inviteCount())
    }
}
