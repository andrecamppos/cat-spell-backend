package com.catspell.api.match.service

import com.catspell.api.auth.model.User
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.match.model.Match
import com.catspell.api.match.model.MatchRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MatchService(
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createMatch(userId1: UUID, userId2: UUID): Match? {
        val u1 = if (userId1 < userId2) userId1 else userId2
        val u2 = if (userId1 < userId2) userId2 else userId1

        val existing = matchRepository.findByUserPair(u1, u2)
        if (existing != null) return existing

        val user1 = userRepository.getReferenceById(u1)
        val user2 = userRepository.getReferenceById(u2)

        return try {
            matchRepository.save(Match(user1 = user1, user2 = user2))
        } catch (e: DataIntegrityViolationException) {
            matchRepository.findByUserPair(u1, u2)
        }
    }

    fun findExistingMatch(userId1: UUID, userId2: UUID): Match? {
        val u1 = if (userId1 < userId2) userId1 else userId2
        val u2 = if (userId1 < userId2) userId2 else userId1
        return matchRepository.findByUserPair(u1, u2)
    }
}
