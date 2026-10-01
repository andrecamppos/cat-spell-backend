package com.catspell.api.invite

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.common.exception.InviteRequiredException
import com.catspell.api.invite.service.InviteService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves INV-04: invite codes are stored only hashed-at-rest, every failure mode of validate() throws the
 * SAME generic exception (enumeration safety), and the single-use claim is atomic — concurrent consumption
 * of one code yields exactly one winner (the probe INV-04 ordering backstop: winner unspecified, count == 1).
 */
@SpringBootTest
class InviteSingleUseIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var inviteService: InviteService
    @Autowired lateinit var userRepository: UserRepository

    private fun newUser(email: String): User = userRepository.save(User(email = email, passwordHash = "hash"))

    private fun sha256Hex(s: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))

    @Test
    fun `invite code is stored only as its SHA-256 hash`() {
        val raw = inviteService.create(null)

        val storedHash = jdbcTemplate.queryForObject("SELECT code_hash FROM invites", String::class.java)
        assertEquals(sha256Hex(raw), storedHash, "code_hash must equal SHA-256(rawCode)")
        assertNotEquals(raw, storedHash, "the raw code must never be stored verbatim")

        val rawMatches = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM invites WHERE code_hash = ? OR consumed_by::text = ? OR referrer_user_id::text = ?",
            Int::class.java, raw, raw, raw
        )
        assertEquals(0, rawMatches, "the raw code must not appear in any invites column")
    }

    @Test
    fun `null blank unknown and consumed codes all throw the same generic exception`() {
        assertThrows(InviteRequiredException::class.java) { inviteService.validate(null) }
        assertThrows(InviteRequiredException::class.java) { inviteService.validate("") }
        assertThrows(InviteRequiredException::class.java) { inviteService.validate("   ") }
        assertThrows(InviteRequiredException::class.java) { inviteService.validate("not-a-real-code") }

        val raw = inviteService.create(null)
        val invite = inviteService.validate(raw)
        inviteService.consume(invite, newUser("consumed@example.com"))
        assertThrows(InviteRequiredException::class.java) { inviteService.validate(raw) }
    }

    @Test
    fun `a second consume of an already-consumed invite throws the generic exception`() {
        val raw = inviteService.create(null)
        val invite = inviteService.validate(raw)
        inviteService.consume(invite, newUser("first-winner@example.com"))

        assertThrows(InviteRequiredException::class.java) {
            inviteService.consume(invite, newUser("second-loser@example.com"))
        }
    }

    @Test
    fun `concurrent consumption of one code yields exactly one winner`() {
        val raw = inviteService.create(null)
        val invite = inviteService.validate(raw)

        val threads = 4
        val invitees = (1..threads).map { newUser("race-$it@example.com") }
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val successes = AtomicInteger(0)
        val failures = AtomicInteger(0)

        val futures = invitees.map { invitee ->
            pool.submit {
                start.await()
                try {
                    inviteService.consume(invite, invitee)
                    successes.incrementAndGet()
                } catch (e: InviteRequiredException) {
                    failures.incrementAndGet()
                }
            }
        }
        start.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, successes.get(), "exactly one concurrent consume may win")
        assertEquals(threads - 1, failures.get(), "all other concurrent consumes must get the generic exception")

        val consumed = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM invites WHERE consumed_at IS NOT NULL", Int::class.java
        )
        assertEquals(1, consumed, "at most one invite may be consumed")
    }
}
