package com.catspell.api.invite

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.invite.service.InviteService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Proves INV-05 / D-11 / D-12: consuming a referrer-bearing invite writes exactly one referrals row linking
 * (referrer, invitee, invite); consuming a bootstrap (null-referrer) invite writes none; and a self-referral
 * (referrer == invitee) writes none.
 */
@SpringBootTest
class ReferralAttributionIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var inviteService: InviteService
    @Autowired lateinit var userRepository: UserRepository

    private fun newUser(email: String): User = userRepository.save(User(email = email, passwordHash = "hash"))

    private fun referralCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM referrals", Int::class.java)!!

    @Test
    fun `consuming a referrer-bearing invite writes exactly one referral row`() {
        val referrer = newUser("referrer@example.com")
        val invite = inviteService.validate(inviteService.create(referrer.id))
        val invitee = newUser("invitee@example.com")

        inviteService.consume(invite, invitee)

        assertEquals(1, referralCount())
        val row = jdbcTemplate.queryForMap("SELECT referrer_id, invitee_id, invite_id FROM referrals")
        assertEquals(referrer.id, row["referrer_id"], "referral must attribute to the real referrer")
        assertEquals(invitee.id, row["invitee_id"])
        assertEquals(invite.id, row["invite_id"])
    }

    @Test
    fun `consuming a bootstrap null-referrer invite writes no referral row`() {
        val invite = inviteService.validate(inviteService.create(null))
        inviteService.consume(invite, newUser("bootstrap-invitee@example.com"))

        assertEquals(0, referralCount(), "bootstrap codes must produce zero referral rows")
    }

    @Test
    fun `a self-referral where referrer equals invitee writes no referral row`() {
        val user = newUser("self-ref@example.com")
        val invite = inviteService.validate(inviteService.create(user.id))

        inviteService.consume(invite, user)

        assertEquals(0, referralCount(), "a self-referral must not write a referral row")
    }
}
