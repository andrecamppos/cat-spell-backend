package com.catspell.api.moderation

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.chat.model.MessageRepository
import com.catspell.api.chat.model.SendMessageRequest
import com.catspell.api.chat.service.ChatService
import com.catspell.api.common.exception.SelfBlockException
import com.catspell.api.discovery.model.SwipeRepository
import com.catspell.api.match.model.MatchRepository
import com.catspell.api.moderation.model.BlockRepository
import com.catspell.api.moderation.service.BlockService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class BlockServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var s3Client: S3Client

    @Autowired lateinit var blockService: BlockService
    @Autowired lateinit var chatService: ChatService
    @Autowired lateinit var blockRepository: BlockRepository
    @Autowired lateinit var matchRepository: MatchRepository
    @Autowired lateinit var swipeRepository: SwipeRepository
    @Autowired lateinit var messageRepository: MessageRepository

    // ---- setup helpers (mirrors SwipeMatchIntegrationTest) ----

    private fun registerAndGetToken(email: String): String {
        val body = mapOf("email" to email, "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
        markEmailVerified(email)
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["accessToken"].asText()
    }

    private fun createProfile(token: String, displayName: String, gender: String) {
        val body = mapOf(
            "displayName" to displayName,
            "bio" to "Hello world",
            "dateOfBirth" to "2000-01-15",
            "gender" to gender,
            "genderPreference" to "EVERYONE",
            "ageMin" to 18,
            "ageMax" to 50,
            "maxDistanceKm" to 100
        )
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated)
    }

    private fun setLocation(token: String, lat: Double = 40.7128, lng: Double = -74.0060) {
        val body = mapOf("latitude" to lat, "longitude" to lng)
        mockMvc.perform(
            put("/api/profile/location")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk)
    }

    private fun createCat(token: String, name: String): String {
        val body = mapOf("name" to name, "age" to 2, "ageUnit" to "YEARS")
        val result = mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asText()
    }

    private fun addUserPhoto(token: String) {
        val upload = requestUpload("/api/profile/photos/upload-url", token)
        uploadToS3(upload["s3Key"] as String)
        mockMvc.perform(
            post("/api/profile/photos/${upload["photoId"]}/confirm").header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
    }

    private fun addCatPhoto(token: String, catId: String) {
        val upload = requestUpload("/api/cats/$catId/photos/upload-url", token)
        uploadToS3(upload["s3Key"] as String)
        mockMvc.perform(
            post("/api/cats/$catId/photos/${upload["photoId"]}/confirm").header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
    }

    private fun requestUpload(path: String, token: String): Map<String, Any> {
        val body = mapOf("contentType" to "image/jpeg", "fileName" to "photo.jpg")
        val result = mockMvc.perform(
            post(path)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }

    private fun uploadToS3(s3Key: String) {
        s3Client.putObject(
            PutObjectRequest.builder().bucket("catspell-photos").key(s3Key).contentType("image/jpeg").build(),
            RequestBody.fromBytes(minimalJpeg())
        )
    }

    private fun minimalJpeg(): ByteArray {
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 0xFF0000)
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpeg", baos)
        return baos.toByteArray()
    }

    private fun setupUser(email: String, displayName: String, gender: String, catName: String): Triple<String, String, UUID> {
        val token = registerAndGetToken(email)
        createProfile(token, displayName, gender)
        setLocation(token)
        addUserPhoto(token)
        val catId = createCat(token, catName)
        addCatPhoto(token, catId)
        return Triple(token, catId, userId(email))
    }

    private fun userId(email: String): UUID =
        UUID.fromString(jdbcTemplate.queryForObject("SELECT id::text FROM users WHERE email = ?", String::class.java, email))

    private fun swipe(token: String, catId: String, action: String = "LIKE") {
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catId, "action" to action)))
        ).andExpect(status().isOk)
    }

    private fun swipesBetween(a: UUID, b: UUID): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM swipes WHERE (swiper_id = ? AND target_user_id = ?) OR (swiper_id = ? AND target_user_id = ?)",
            Int::class.java, a, b, b, a
        )!!

    // ---- tests ----

    @Test
    fun `block self throws SelfBlockException`() {
        val (_, _, aId) = setupUser("bs-self@example.com", "SelfBlocker", "MALE", "SelfCat")
        assertThrows<SelfBlockException> { blockService.block(aId, aId) }
    }

    @Test
    fun `double block leaves exactly one row`() {
        val (_, _, aId) = setupUser("bs-dupA@example.com", "DupA", "FEMALE", "DupCatA")
        val (_, _, bId) = setupUser("bs-dupB@example.com", "DupB", "MALE", "DupCatB")

        blockService.block(aId, bId)
        blockService.block(aId, bId)

        assertEquals(1, blockRepository.findByBlockerIdOrderByCreatedAtDesc(aId).size)
        assertTrue(blockRepository.existsByBlockerIdAndBlockedId(aId, bId))
    }

    @Test
    fun `block of matched pair ends match, clears swipes, retains match row and messages`() {
        val (tokenA, catIdA, aId) = setupUser("bs-matchA@example.com", "MatchA", "FEMALE", "MCatA")
        val (tokenB, catIdB, bId) = setupUser("bs-matchB@example.com", "MatchB", "MALE", "MCatB")

        // Drive a mutual like -> match.
        swipe(tokenA, catIdB)
        swipe(tokenB, catIdA)

        val match = matchRepository.findByUserPair(aId, bId)
        assertNotNull(match, "mutual like should have created a match")
        assertNull(match!!.endedAt, "match starts active")

        // Send a message so we can prove message rows persist through teardown.
        val sent = chatService.sendMessage(aId, SendMessageRequest(matchId = match.id, content = "hi there"))
        assertTrue(messageRepository.existsById(sent.messageId))
        assertTrue(swipesBetween(aId, bId) > 0, "swipe rows exist before block")

        blockService.block(aId, bId)

        // Match is soft-ended (row retained), reason BLOCK.
        val ended = matchRepository.findByUserPair(aId, bId)
        assertNotNull(ended)
        assertNotNull(ended!!.endedAt, "match should be ended after block")
        assertEquals("BLOCK", ended.endedReason)

        // Swipe history between the pair is cleared (both directions).
        assertEquals(0, swipesBetween(aId, bId))

        // Messages are retained as evidence (soft state, D-07).
        assertTrue(messageRepository.existsById(sent.messageId), "message rows must persist after block")
    }

    @Test
    fun `isBlockedEitherWay is true both directions after block and false after unblock`() {
        val (_, _, aId) = setupUser("bs-biA@example.com", "BiA", "FEMALE", "BiCatA")
        val (_, _, bId) = setupUser("bs-biB@example.com", "BiB", "MALE", "BiCatB")

        blockService.block(aId, bId)
        assertTrue(blockService.isBlockedEitherWay(aId, bId))
        assertTrue(blockService.isBlockedEitherWay(bId, aId))

        blockService.unblock(aId, bId)
        assertFalse(blockService.isBlockedEitherWay(aId, bId))
        assertFalse(blockService.isBlockedEitherWay(bId, aId))
    }

    @Test
    fun `unblock removes only the callers own row and creates no match`() {
        val (_, _, aId) = setupUser("bs-ubA@example.com", "UbA", "FEMALE", "UbCatA")
        val (_, _, bId) = setupUser("bs-ubB@example.com", "UbB", "MALE", "UbCatB")

        blockService.block(aId, bId)
        blockService.block(bId, aId)

        blockService.unblock(aId, bId)

        assertFalse(blockRepository.existsByBlockerIdAndBlockedId(aId, bId), "A->B row removed")
        assertTrue(blockRepository.existsByBlockerIdAndBlockedId(bId, aId), "B->A row untouched")
        // Unblock performs no auto-rematch — no active match between the pair.
        assertNull(matchRepository.findByUserPair(aId, bId))
    }

    @Test
    fun `unblock is idempotent`() {
        val (_, _, aId) = setupUser("bs-idemA@example.com", "IdemA", "FEMALE", "IdemCatA")
        val (_, _, bId) = setupUser("bs-idemB@example.com", "IdemB", "MALE", "IdemCatB")

        blockService.unblock(aId, bId) // no row exists — must not throw
        blockService.block(aId, bId)
        blockService.unblock(aId, bId)
        blockService.unblock(aId, bId) // second removal — still success
        assertFalse(blockService.isBlockedEitherWay(aId, bId))
    }

    @Test
    fun `getBlockList returns minimal identity newest first`() {
        val (_, _, aId) = setupUser("bs-listA@example.com", "ListA", "FEMALE", "ListCatA")
        val (_, _, bId) = setupUser("bs-listB@example.com", "ListB", "MALE", "ListCatB")
        val (_, _, cId) = setupUser("bs-listC@example.com", "ListC", "MALE", "ListCatC")

        blockService.block(aId, bId)
        Thread.sleep(10)
        blockService.block(aId, cId)

        val list = blockService.getBlockList(aId).blocks
        assertEquals(2, list.size)
        // Newest first: C was blocked after B.
        assertEquals(cId, list[0].userId)
        assertEquals(bId, list[1].userId)
        assertEquals("ListC", list[0].displayName)
        assertNotNull(list[0].blockedAt)
    }
}
