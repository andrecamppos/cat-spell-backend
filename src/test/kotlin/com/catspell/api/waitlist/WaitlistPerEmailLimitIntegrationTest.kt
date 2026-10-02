package com.catspell.api.waitlist

import com.catspell.api.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * D-03 + D-07 (WAIT-03): `+suffix` / case variants collapse onto one waitlist identity AND one per-email bucket,
 * dotted variants stay distinct, and exhausting the bucket is silent (identical 202, token not rotated). Each test
 * uses distinct addresses because the buckets live in the service bean for the whole cached context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.waitlist.per-email-capacity=2"])
class WaitlistPerEmailLimitIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    private fun join(email: String): MockHttpServletResponse =
        mockMvc.perform(
            post("/api/waitlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andReturn().response

    private fun rowCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM waitlist_entries", Int::class.java)!!

    private fun row(normalizedEmail: String): Map<String, Any?> =
        jdbcTemplate.queryForMap(
            "SELECT email, normalized_email, confirm_token_hash FROM waitlist_entries WHERE normalized_email = ?",
            normalizedEmail
        )

    @Test
    fun `plus and case variants share one row and one bucket and the over-limit join is silent`() {
        val first = join(" B+Tag@Example.COM ")
        assertEquals(202, first.status)
        assertEquals(1, rowCount())
        val hash1 = row("b@example.com")["confirm_token_hash"] as String

        val second = join("b@example.com")
        assertEquals(202, second.status)
        assertEquals(1, rowCount(), "the case/+suffix variant must not create a second entry")
        val afterSecond = row("b@example.com")
        val hash2 = afterSecond["confirm_token_hash"] as String
        assertNotEquals(hash1, hash2, "join 2 (within capacity) must rotate the confirm token")
        assertEquals("b@example.com", afterSecond["email"], "a PENDING re-join stores the address just submitted")

        val third = join("b+other@example.com")
        assertEquals(first.status, third.status, "the over-limit join must return the identical status")
        assertEquals(
            first.contentAsString, third.contentAsString,
            "the over-limit join must return a byte-identical body"
        )
        val afterThird = row("b@example.com")
        assertEquals(hash2, afterThird["confirm_token_hash"], "an exhausted bucket must leave the token unchanged")
        assertEquals("b@example.com", afterThird["email"], "an exhausted bucket must leave the email unchanged")
        assertEquals(1, rowCount())
    }

    @Test
    fun `dotted addresses stay two distinct entries`() {
        assertEquals(202, join("c.d@gmail.com").status)
        assertEquals(202, join("cd@gmail.com").status)

        assertEquals(2, rowCount(), "dots are significant; no provider-specific dot stripping (D-03)")
        assertNotNull(row("c.d@gmail.com")["confirm_token_hash"])
        assertNotNull(row("cd@gmail.com")["confirm_token_hash"])
    }

    @Test
    fun `exhausting one address bucket does not affect another address`() {
        join("e@example.com")
        join("e@example.com")
        val exhaustedHash = row("e@example.com")["confirm_token_hash"]
        join("e@example.com")
        assertEquals(exhaustedHash, row("e@example.com")["confirm_token_hash"], "e@ is over its limit")

        assertEquals(202, join("f@example.com").status)
        val fHash1 = row("f@example.com")["confirm_token_hash"] as String
        join("f@example.com")
        assertNotEquals(fHash1, row("f@example.com")["confirm_token_hash"], "f@ has its own, untouched bucket")
    }
}
