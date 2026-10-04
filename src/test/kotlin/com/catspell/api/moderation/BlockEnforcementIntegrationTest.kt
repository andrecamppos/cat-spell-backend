package com.catspell.api.moderation

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.chat.model.SendMessageRequest
import com.catspell.api.chat.service.ChatService
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.match.model.MatchRepository
import com.catspell.api.match.service.MatchService
import com.catspell.api.moderation.service.BlockService
import com.catspell.api.chat.model.MessageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.support.AbstractSubscribableChannel
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest
@AutoConfigureMockMvc
class BlockEnforcementIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var s3Client: S3Client

    @Autowired lateinit var blockService: BlockService
    @Autowired lateinit var matchService: MatchService
    @Autowired lateinit var chatService: ChatService
    @Autowired lateinit var matchRepository: MatchRepository
    @Autowired lateinit var messageRepository: MessageRepository

    // SimpMessagingTemplate.convertAndSendToUser publishes `/user/<id>/queue/notifications` on
    // this channel, so a missing reconnect push is observed directly at the broker (Pitfall 5b).
    @Autowired @Qualifier("brokerChannel") lateinit var brokerChannel: AbstractSubscribableChannel

    private val capturedDestinations = CopyOnWriteArrayList<String>()

    private val captureInterceptor = object : ChannelInterceptor {
        override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
            SimpMessageHeaderAccessor.getDestination(message.headers)?.let { capturedDestinations.add(it) }
            return message
        }
    }

    @BeforeEach
    fun registerBrokerCapture() {
        capturedDestinations.clear()
        brokerChannel.addInterceptor(captureInterceptor)
    }

    @AfterEach
    fun removeBrokerCapture() {
        brokerChannel.removeInterceptor(captureInterceptor)
        capturedDestinations.clear()
    }

    private fun notificationsTo(userId: UUID): Int =
        capturedDestinations.count { it == "/user/$userId/queue/notifications" }

    private fun delivered(messageId: UUID): Boolean =
        jdbcTemplate.queryForObject("SELECT delivered FROM messages WHERE id = ?", Boolean::class.java, messageId)!!

    // ---- setup helpers ----

    private fun registerAndGetToken(email: String): String {
        val body = mapOf("email" to email, "password" to "password123", "dateOfBirth" to "2000-01-15")
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
        markEmailVerified(email)
        val result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["accessToken"].asText()
    }

    private fun createProfile(token: String, displayName: String, gender: String) {
        val body = mapOf(
            "displayName" to displayName, "bio" to "Hello world",
            "gender" to gender, "genderPreference" to "EVERYONE", "ageMin" to 18, "ageMax" to 50, "maxDistanceKm" to 100
        )
        mockMvc.perform(post("/api/profile").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated)
    }

    private fun setLocation(token: String, lat: Double = 40.7128, lng: Double = -74.0060) {
        val body = mapOf("latitude" to lat, "longitude" to lng)
        mockMvc.perform(put("/api/profile/location").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isOk)
    }

    private fun createCat(token: String, name: String): String {
        val body = mapOf("name" to name, "age" to 2, "ageUnit" to "YEARS")
        val result = mockMvc.perform(post("/api/cats").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asText()
    }

    private fun addUserPhoto(token: String) {
        val upload = requestUpload("/api/profile/photos/upload-url", token)
        uploadToS3(upload["s3Key"] as String)
        mockMvc.perform(post("/api/profile/photos/${upload["photoId"]}/confirm").header("Authorization", "Bearer $token")).andExpect(status().isOk)
    }

    private fun addCatPhoto(token: String, catId: String) {
        val upload = requestUpload("/api/cats/$catId/photos/upload-url", token)
        uploadToS3(upload["s3Key"] as String)
        mockMvc.perform(post("/api/cats/$catId/photos/${upload["photoId"]}/confirm").header("Authorization", "Bearer $token")).andExpect(status().isOk)
    }

    private fun requestUpload(path: String, token: String): Map<String, Any> {
        val body = mapOf("contentType" to "image/jpeg", "fileName" to "photo.jpg")
        val result = mockMvc.perform(post(path).header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }

    private fun uploadToS3(s3Key: String) {
        s3Client.putObject(PutObjectRequest.builder().bucket("catspell-photos").key(s3Key).contentType("image/jpeg").build(), RequestBody.fromBytes(minimalJpeg()))
    }

    private fun minimalJpeg(): ByteArray {
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 0xFF0000)
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpeg", baos)
        return baos.toByteArray()
    }

    private data class TestUser(val token: String, val catId: String, val id: UUID)

    private fun setupUser(email: String, displayName: String, gender: String, catName: String): TestUser {
        val token = registerAndGetToken(email)
        createProfile(token, displayName, gender)
        setLocation(token)
        addUserPhoto(token)
        val catId = createCat(token, catName)
        addCatPhoto(token, catId)
        return TestUser(token, catId, userId(email))
    }

    private fun userId(email: String): UUID =
        UUID.fromString(jdbcTemplate.queryForObject("SELECT id::text FROM users WHERE email = ?", String::class.java, email))

    private fun swipe(token: String, catId: String, action: String = "LIKE") {
        mockMvc.perform(post("/api/discovery/swipe").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("catId" to catId, "action" to action)))).andExpect(status().isOk)
    }

    private fun matchPair(a: TestUser, b: TestUser): UUID {
        swipe(a.token, b.catId)
        swipe(b.token, a.catId)
        val match = matchRepository.findByUserPair(a.id, b.id)
        assertNotNull(match, "mutual like should have created a match")
        return match!!.id!!
    }

    private fun feedUserIds(token: String): Set<UUID> {
        val result = mockMvc.perform(get("/api/discovery/feed").param("pageSize", "50").header("Authorization", "Bearer $token")).andExpect(status().isOk).andReturn()
        val cards = objectMapper.readTree(result.response.contentAsString)["cards"]
        return (0 until cards.size()).map { UUID.fromString(cards[it]["userId"].asText()) }.toSet()
    }

    private fun matchOtherUserIds(token: String): Set<UUID> {
        val result = mockMvc.perform(get("/api/matches").header("Authorization", "Bearer $token")).andExpect(status().isOk).andReturn()
        val matches = objectMapper.readTree(result.response.contentAsString)["matches"]
        return (0 until matches.size()).map { UUID.fromString(matches[it]["otherUser"]["userId"].asText()) }.toSet()
    }

    private fun conversationIds(token: String): Set<UUID> {
        val result = mockMvc.perform(get("/api/conversations").header("Authorization", "Bearer $token")).andExpect(status().isOk).andReturn()
        val convs = objectMapper.readTree(result.response.contentAsString)["conversations"]
        return (0 until convs.size()).map { UUID.fromString(convs[it]["conversationId"].asText()) }.toSet()
    }

    // ---- tests ----

    @Test
    fun `block hides both users from each others feed and profile-detail 404 both directions`() {
        val a = setupUser("be-feedA@example.com", "FeedA", "FEMALE", "FeedCatA")
        val b = setupUser("be-feedB@example.com", "FeedB", "MALE", "FeedCatB")

        // Baseline: mutually visible in each other's feed.
        assertTrue(b.id in feedUserIds(a.token), "B visible to A before block")
        assertTrue(a.id in feedUserIds(b.token), "A visible to B before block")

        blockService.block(a.id, b.id)

        assertFalse(b.id in feedUserIds(a.token), "B hidden from A's feed after block")
        assertFalse(a.id in feedUserIds(b.token), "A hidden from B's feed after block (bidirectional)")

        // Profile-detail 404 in BOTH directions, via cat-owner and user-profile surfaces.
        mockMvc.perform(get("/api/discovery/cats/${b.catId}/owner").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/discovery/cats/${a.catId}/owner").header("Authorization", "Bearer ${b.token}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/discovery/users/${b.id}/profile").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/discovery/users/${a.id}/profile").header("Authorization", "Bearer ${b.token}")).andExpect(status().isNotFound)
    }

    @Test
    fun `blocked matched pair cannot send or open chat and conversation hidden while messages persist`() {
        val a = setupUser("be-chatA@example.com", "ChatA", "FEMALE", "ChatCatA")
        val b = setupUser("be-chatB@example.com", "ChatB", "MALE", "ChatCatB")
        val matchId = matchPair(a, b)

        // Create the conversation + a persisted message before the block.
        val sent = chatService.sendMessage(a.id, SendMessageRequest(matchId = matchId, content = "hi B"))
        val convId = sent.conversationId
        assertTrue(convId in conversationIds(a.token), "conversation listed before block")

        blockService.block(a.id, b.id)

        // Send rejected for both participants (WebSocket entry uses this same service path).
        assertThrows<ResourceNotFoundException> { chatService.sendMessage(a.id, SendMessageRequest(conversationId = convId, content = "again")) }
        assertThrows<ResourceNotFoundException> { chatService.sendMessage(b.id, SendMessageRequest(conversationId = convId, content = "reply")) }

        // Opening the thread's messages is rejected (404) for both.
        mockMvc.perform(get("/api/conversations/$convId/messages").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/conversations/$convId/messages").header("Authorization", "Bearer ${b.token}")).andExpect(status().isNotFound)

        // Conversation hidden from both lists.
        assertFalse(convId in conversationIds(a.token), "conversation hidden from A after block")
        assertFalse(convId in conversationIds(b.token), "conversation hidden from B after block")

        // Message rows retained as evidence.
        assertTrue(messageRepository.existsById(sent.messageId), "message rows persist after block")
    }

    @Test
    fun `matched-then-blocked pair absent from matches and fresh swipe returns 404`() {
        val a = setupUser("be-matchA@example.com", "MMatchA", "FEMALE", "MMatchCatA")
        val b = setupUser("be-matchB@example.com", "MMatchB", "MALE", "MMatchCatB")
        matchPair(a, b)

        assertTrue(b.id in matchOtherUserIds(a.token), "match present before block")

        blockService.block(a.id, b.id)

        // Match-lookup surface: ended match absent from both users' match lists.
        assertFalse(b.id in matchOtherUserIds(a.token), "match absent from A after block")
        assertFalse(a.id in matchOtherUserIds(b.token), "match absent from B after block")

        // A fresh swipe cannot form/reactivate a match — swipe guard returns 404.
        mockMvc.perform(post("/api/discovery/swipe").header("Authorization", "Bearer ${a.token}").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("catId" to b.catId, "action" to "LIKE")))).andExpect(status().isNotFound)
        // No ACTIVE match exists for the blocked pair.
        val ended = matchRepository.findByUserPair(a.id, b.id)
        assertNotNull(ended)
        assertNotNull(ended!!.endedAt)
    }

    @Test
    fun `unmatch hides conversation for both and allows feed reappearance`() {
        val a = setupUser("be-unmA@example.com", "UnmA", "FEMALE", "UnmCatA")
        val b = setupUser("be-unmB@example.com", "UnmB", "MALE", "UnmCatB")
        val matchId = matchPair(a, b)
        val sent = chatService.sendMessage(a.id, SendMessageRequest(matchId = matchId, content = "hi"))
        val convId = sent.conversationId

        matchService.unmatch(a.id, b.id)

        // Conversation hidden for both.
        assertFalse(convId in conversationIds(a.token), "conversation hidden from A after unmatch")
        assertFalse(convId in conversationIds(b.token), "conversation hidden from B after unmatch")

        // Rediscovery allowed: swipe history cleared, so each reappears in the other's feed.
        assertTrue(b.id in feedUserIds(a.token), "B reappears in A's feed after unmatch")
        assertTrue(a.id in feedUserIds(b.token), "A reappears in B's feed after unmatch")
    }

    @Test
    fun `block prevents feed reappearance until unblock`() {
        val a = setupUser("be-reappA@example.com", "ReappA", "FEMALE", "ReappCatA")
        val b = setupUser("be-reappB@example.com", "ReappB", "MALE", "ReappCatB")

        assertTrue(b.id in feedUserIds(a.token), "B visible before block")

        blockService.block(a.id, b.id)
        assertFalse(b.id in feedUserIds(a.token), "B hidden while blocked")

        blockService.unblock(a.id, b.id)
        assertTrue(b.id in feedUserIds(a.token), "B reappears after unblock")
    }

    // ---- W1 / D-04: reconnect redelivery skips hidden conversations ----

    @Test
    fun `reconnect does not push a message left undelivered before a block and marks it delivered`() {
        val a = setupUser("be-redeliver-a1@example.com", "RedelA1", "FEMALE", "RedelCatA1")
        val b = setupUser("be-redeliver-b1@example.com", "RedelB1", "MALE", "RedelCatB1")
        val matchId = matchPair(a, b)
        val sent = chatService.sendMessage(a.id, SendMessageRequest(matchId = matchId, content = "hi B"))
        assertFalse(delivered(sent.messageId), "message starts undelivered")

        blockService.block(a.id, b.id)

        capturedDestinations.clear()
        val pushed = chatService.deliverUnreadMessages(b.id)

        assertEquals(0, notificationsTo(b.id), "no reconnect preview for a blocked pair")
        assertEquals(0, pushed, "pushed count excludes the hidden conversation")
        assertTrue(delivered(sent.messageId), "suppressed message is marked delivered (D-04)")
        assertTrue(messageRepository.existsById(sent.messageId), "message row kept as evidence")
    }

    @Test
    fun `reconnect still pushes a visible conversation while suppressing a blocked one`() {
        val a = setupUser("be-redeliver-a2@example.com", "RedelA2", "FEMALE", "RedelCatA2")
        val b = setupUser("be-redeliver-b2@example.com", "RedelB2", "MALE", "RedelCatB2")
        val c = setupUser("be-redeliver-c2@example.com", "RedelC2", "FEMALE", "RedelCatC2")
        val abMatch = matchPair(a, b)
        val cbMatch = matchPair(c, b)
        val hiddenMsg = chatService.sendMessage(a.id, SendMessageRequest(matchId = abMatch, content = "hi B"))
        val visibleMsg = chatService.sendMessage(c.id, SendMessageRequest(matchId = cbMatch, content = "hello B"))

        blockService.block(a.id, b.id)

        capturedDestinations.clear()
        val pushed = chatService.deliverUnreadMessages(b.id)

        assertEquals(1, pushed, "only the visible conversation is pushed")
        assertEquals(1, notificationsTo(b.id), "exactly one reconnect notification for B")
        assertTrue(delivered(hiddenMsg.messageId), "hidden message marked delivered")
        assertTrue(delivered(visibleMsg.messageId), "visible message marked delivered after push")
    }

    @Test
    fun `reconnect does not push a message left undelivered before an unmatch`() {
        val a = setupUser("be-redeliver-c-a@example.com", "RedelCA", "FEMALE", "RedelCatCA")
        val b = setupUser("be-redeliver-c-b@example.com", "RedelCB", "MALE", "RedelCatCB")
        val matchId = matchPair(a, b)
        val sent = chatService.sendMessage(a.id, SendMessageRequest(matchId = matchId, content = "hi B"))

        matchService.unmatch(a.id, b.id)

        capturedDestinations.clear()
        val pushed = chatService.deliverUnreadMessages(b.id)

        assertEquals(0, notificationsTo(b.id), "no reconnect preview after unmatch")
        assertEquals(0, pushed, "pushed count is 0 for an ended conversation")
        assertTrue(delivered(sent.messageId), "suppressed message is marked delivered")
    }

    @Test
    fun `message suppressed after an unmatch does not resurface after a rematch`() {
        val a = setupUser("be-redeliver-d-a@example.com", "RedelDA", "FEMALE", "RedelCatDA")
        val b = setupUser("be-redeliver-d-b@example.com", "RedelDB", "MALE", "RedelCatDB")
        val matchId = matchPair(a, b)
        val sent = chatService.sendMessage(a.id, SendMessageRequest(matchId = matchId, content = "hi B"))
        matchService.unmatch(a.id, b.id)
        capturedDestinations.clear()
        assertEquals(0, chatService.deliverUnreadMessages(b.id), "first reconnect suppressed")

        val rematchId = matchPair(a, b)
        assertEquals(matchId, rematchId, "rematch reactivates the same match row")
        assertEquals(null, matchRepository.findByUserPair(a.id, b.id)!!.endedAt, "rematched match is active")

        capturedDestinations.clear()
        val pushed = chatService.deliverUnreadMessages(b.id)

        assertEquals(0, notificationsTo(b.id), "old message is not pushed after the rematch")
        assertEquals(0, pushed, "nothing pushed on the second reconnect")
        assertTrue(delivered(sent.messageId), "message stays delivered")
    }

    @Test
    fun `message suppressed after a block does not resurface after unblock and rematch`() {
        val a = setupUser("be-redeliver-e-a@example.com", "RedelEA", "FEMALE", "RedelCatEA")
        val b = setupUser("be-redeliver-e-b@example.com", "RedelEB", "MALE", "RedelCatEB")
        val matchId = matchPair(a, b)
        val sent = chatService.sendMessage(a.id, SendMessageRequest(matchId = matchId, content = "hi B"))
        blockService.block(a.id, b.id)
        capturedDestinations.clear()
        assertEquals(0, chatService.deliverUnreadMessages(b.id), "reconnect while blocked is suppressed")
        assertEquals(0, notificationsTo(b.id), "no push while blocked")

        blockService.unblock(a.id, b.id)
        val rematchId = matchPair(a, b)
        assertEquals(matchId, rematchId, "rematch reactivates the same match row")

        capturedDestinations.clear()
        val pushed = chatService.deliverUnreadMessages(b.id)

        assertEquals(0, notificationsTo(b.id), "old message is not pushed after unblock and rematch")
        assertEquals(0, pushed, "nothing pushed after the rematch")
        assertTrue(delivered(sent.messageId), "message stays delivered")
    }

    @Test
    fun `reconnect does not push when the recipient blocked the sender`() {
        val a = setupUser("be-redeliver-f-a@example.com", "RedelFA", "FEMALE", "RedelCatFA")
        val b = setupUser("be-redeliver-f-b@example.com", "RedelFB", "MALE", "RedelCatFB")
        val matchId = matchPair(a, b)
        val sent = chatService.sendMessage(a.id, SendMessageRequest(matchId = matchId, content = "hi B"))

        blockService.block(b.id, a.id)

        capturedDestinations.clear()
        val pushed = chatService.deliverUnreadMessages(b.id)

        assertEquals(0, notificationsTo(b.id), "no reconnect preview when the recipient is the blocker")
        assertEquals(0, pushed, "pushed count is 0")
        assertTrue(delivered(sent.messageId), "suppressed message is marked delivered")
    }

    @Test
    fun `a block row alone with the match still active suppresses the reconnect push`() {
        val a = setupUser("be-redeliver-g-a@example.com", "RedelGA", "FEMALE", "RedelCatGA")
        val b = setupUser("be-redeliver-g-b@example.com", "RedelGB", "MALE", "RedelCatGB")
        val matchId = matchPair(a, b)
        val sent = chatService.sendMessage(a.id, SendMessageRequest(matchId = matchId, content = "hi B"))

        // Bypass BlockService (which always ends the match) so only the EXISTS branch can hide it.
        jdbcTemplate.update(
            "INSERT INTO blocks (id, blocker_id, blocked_id, created_at) VALUES (gen_random_uuid(), ?, ?, NOW())",
            b.id, a.id
        )
        assertEquals(null, matchRepository.findByUserPair(a.id, b.id)!!.endedAt, "match is still active")

        capturedDestinations.clear()
        val pushed = chatService.deliverUnreadMessages(b.id)

        assertEquals(0, notificationsTo(b.id), "block row alone suppresses the reconnect push")
        assertEquals(0, pushed, "pushed count is 0")
        assertTrue(delivered(sent.messageId), "suppressed message is marked delivered")
    }
}
