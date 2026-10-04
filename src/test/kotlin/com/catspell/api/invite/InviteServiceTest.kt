package com.catspell.api.invite

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.invite.model.Invite
import com.catspell.api.invite.model.InviteRepository
import com.catspell.api.invite.model.ReferralRepository
import com.catspell.api.invite.service.InviteService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.HexFormat

/**
 * D-10 / WR-05: [InviteService.create] flushes the invite row at creation, so a caller that mails the code right
 * afterwards (waitlist conversion, operator issuance) never sends a code whose INSERT is still pending.
 */
class InviteServiceTest {

    private val inviteRepository = mockk<InviteRepository>()
    private val referralRepository = mockk<ReferralRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)

    private val service = InviteService(inviteRepository, referralRepository, userRepository)

    private fun sha256Hex(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    @Test
    fun `create flushes the invite row once and stores only the hash of the returned code`() {
        val saved = slot<Invite>()
        every { inviteRepository.saveAndFlush(capture(saved)) } answers { firstArg() }
        every { inviteRepository.save(any<Invite>()) } answers { firstArg() }

        val rawCode = service.create(null)

        verify(exactly = 1) { inviteRepository.saveAndFlush(any<Invite>()) }
        verify(exactly = 0) { inviteRepository.save(any<Invite>()) }
        assertTrue(rawCode.isNotBlank(), "create must return a raw code")
        assertEquals(sha256Hex(rawCode), saved.captured.codeHash, "only the SHA-256 hex of the code is persisted")
        assertEquals(null, saved.captured.referrerUserId, "a null referrer stays null")
    }
}
