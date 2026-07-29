package com.catspell.api.match

import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.cat.model.CatPhotoRepository
import com.catspell.api.cat.model.CatProfileRepository
import com.catspell.api.match.model.Match
import com.catspell.api.match.model.MatchRepository
import com.catspell.api.match.service.MatchService
import com.catspell.api.profile.model.UserPhotoRepository
import com.catspell.api.profile.model.UserProfileRepository
import com.catspell.api.push.event.MatchCreatedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

/**
 * Unit tests for the duplicate-match notification-suppression contract (T-9-08): a
 * [MatchCreatedEvent] must be published only on a genuinely new match, never on the
 * existing-match return path or the duplicate-key race fallback.
 */
class MatchServiceTest {

    private val matchRepository = mockk<MatchRepository>()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val userProfileRepository = mockk<UserProfileRepository>()
    private val userPhotoRepository = mockk<UserPhotoRepository>()
    private val catProfileRepository = mockk<CatProfileRepository>()
    private val catPhotoRepository = mockk<CatPhotoRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = MatchService(
        matchRepository,
        userRepository,
        userProfileRepository,
        userPhotoRepository,
        catProfileRepository,
        catPhotoRepository,
        eventPublisher
    )

    private fun savedMatch(id: UUID): Match =
        Match(user1 = mockk(), user2 = mockk()).apply { this.id = id }

    @Test
    fun `createMatch publishes MatchCreatedEvent exactly once on a genuinely new match`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val u1 = if (a < b) a else b
        val u2 = if (a < b) b else a
        val matchId = UUID.randomUUID()

        every { matchRepository.findByUserPair(u1, u2) } returns null
        every { userRepository.getReferenceById(any<UUID>()) } returns mockk<User>()
        every { matchRepository.save(any()) } returns savedMatch(matchId)

        val eventSlot = slot<MatchCreatedEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

        service.createMatch(a, b)

        verify(exactly = 1) { eventPublisher.publishEvent(any<MatchCreatedEvent>()) }
        assertEquals(matchId, eventSlot.captured.matchId)
        assertEquals(u1, eventSlot.captured.userId1)
        assertEquals(u2, eventSlot.captured.userId2)
    }

    @Test
    fun `createMatch does not publish when the match already exists`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val u1 = if (a < b) a else b
        val u2 = if (a < b) b else a

        every { matchRepository.findByUserPair(u1, u2) } returns savedMatch(UUID.randomUUID())

        service.createMatch(a, b)

        verify(exactly = 0) { eventPublisher.publishEvent(any<MatchCreatedEvent>()) }
        verify(exactly = 0) { matchRepository.save(any()) }
    }

    @Test
    fun `re-swiping the same pair publishes only once across two calls`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val u1 = if (a < b) a else b
        val u2 = if (a < b) b else a
        val existing = savedMatch(UUID.randomUUID())

        // First call: no existing match -> save -> publish. Second call (swapped order):
        // existing match found -> early return, no publish.
        every { matchRepository.findByUserPair(u1, u2) } returnsMany listOf(null, existing)
        every { userRepository.getReferenceById(any<UUID>()) } returns mockk<User>()
        every { matchRepository.save(any()) } returns existing

        service.createMatch(a, b)
        service.createMatch(b, a)

        verify(exactly = 1) { eventPublisher.publishEvent(any<MatchCreatedEvent>()) }
    }

    @Test
    fun `createMatch does not publish on the duplicate-key race fallback`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val u1 = if (a < b) a else b
        val u2 = if (a < b) b else a

        // A concurrent insert won the race: our pre-check saw no match, but the save
        // violates the unique constraint and we fall back to re-reading the winner's row.
        every { matchRepository.findByUserPair(u1, u2) } returnsMany listOf(null, savedMatch(UUID.randomUUID()))
        every { userRepository.getReferenceById(any<UUID>()) } returns mockk<User>()
        every { matchRepository.save(any()) } throws DataIntegrityViolationException("duplicate key")

        service.createMatch(a, b)

        verify(exactly = 0) { eventPublisher.publishEvent(any<MatchCreatedEvent>()) }
    }
}
