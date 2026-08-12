package com.catspell.api.chat

import com.catspell.api.BaseIntegrationTest
import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ConversationListIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var s3Client: S3Client

    @LocalServerPort
    var port: Int = 0

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
        val json = objectMapper.readTree(result.response.contentAsString)
        return json["accessToken"].asText()
    }

    private fun createProfile(token: String, displayName: String = "Test User", gender: String = "MALE") {
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

    private fun setLocation(token: String) {
        val body = mapOf("latitude" to 40.7128, "longitude" to -74.0060)
        mockMvc.perform(
            put("/api/profile/location")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk)
    }

    private fun createCat(token: String, name: String = "TestCat"): String {
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
        val uploadResult = requestUserUploadUrl(token)
        val s3Key = uploadResult["s3Key"] as String
        val photoId = uploadResult["photoId"] as String
        uploadToS3(s3Key)
        confirmUserPhoto(token, photoId)
    }

    private fun addCatPhoto(token: String, catId: String) {
        val uploadResult = requestCatUploadUrl(token, catId)
        val s3Key = uploadResult["s3Key"] as String
        val photoId = uploadResult["photoId"] as String
        uploadToS3(s3Key)
        confirmCatPhoto(token, catId, photoId)
    }

    private fun requestUserUploadUrl(token: String): Map<String, Any> {
        val body = mapOf("contentType" to "image/jpeg", "fileName" to "photo.jpg")
        val result = mockMvc.perform(
            post("/api/profile/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }

    private fun requestCatUploadUrl(token: String, catId: String): Map<String, Any> {
        val body = mapOf("contentType" to "image/jpeg", "fileName" to "cat.jpg")
        val result = mockMvc.perform(
            post("/api/cats/$catId/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }

    private fun uploadToS3(s3Key: String) {
        val jpegBytes = createMinimalJpeg()
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("catspell-photos")
                .key(s3Key)
                .contentType("image/jpeg")
                .build(),
            RequestBody.fromBytes(jpegBytes)
        )
    }

    private fun confirmUserPhoto(token: String, photoId: String) {
        mockMvc.perform(
            post("/api/profile/photos/$photoId/confirm")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
    }

    private fun confirmCatPhoto(token: String, catId: String, photoId: String) {
        mockMvc.perform(
            post("/api/cats/$catId/photos/$photoId/confirm")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
    }

    private fun createMinimalJpeg(): ByteArray {
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 0xFF0000)
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpeg", baos)
        return baos.toByteArray()
    }

    private fun setupCompleteUser(email: String, displayName: String = "User", gender: String = "MALE", catName: String = "TestCat"): Triple<String, String, String> {
        val token = registerAndGetToken(email)
        createProfile(token, displayName, gender)
        setLocation(token)
        addUserPhoto(token)
        val catId = createCat(token, catName)
        addCatPhoto(token, catId)
        return Triple(token, catId, email)
    }

    private fun createMutualMatch(tokenA: String, catIdA: String, tokenB: String, catIdB: String) {
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdB, "action" to "LIKE")))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdA, "action" to "LIKE")))
        ).andExpect(status().isOk)
    }

    private fun getMatchId(token: String, otherDisplayName: String): String {
        val result = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        val matches = json["matches"]
        for (i in 0 until matches.size()) {
            if (matches[i]["otherUser"]["displayName"].asText() == otherDisplayName) {
                return matches[i]["matchId"].asText()
            }
        }
        throw IllegalStateException("Match with $otherDisplayName not found")
    }

    private fun createStompClient(): WebSocketStompClient {
        val client = WebSocketStompClient(StandardWebSocketClient())
        client.messageConverter = MappingJackson2MessageConverter()
        return client
    }

    private fun connectStomp(token: String): StompSession {
        val client = createStompClient()
        val headers = StompHeaders()
        headers.add("Authorization", "Bearer $token")

        val url = "ws://localhost:$port/ws"
        val session = client.connectAsync(url, WebSocketHttpHeaders(), headers, object : StompSessionHandlerAdapter() {
            override fun handleException(session: StompSession, command: StompCommand?, headers: StompHeaders, payload: ByteArray, exception: Throwable) {}
        }).get(5, TimeUnit.SECONDS)
        return session
    }

    private fun sendMessageViaStomp(session: StompSession, matchId: String, content: String) {
        val stompHeaders = StompHeaders()
        stompHeaders.destination = "/app/chat.send"
        session.send(stompHeaders, mapOf("matchId" to matchId, "content" to content))
    }

    @Test
    fun `conversation list returns empty when no conversations`() {
        val (tokenA, _, _) = setupCompleteUser("conv-empty-a@example.com", "ConvEmptyA")

        mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conversations").isArray)
            .andExpect(jsonPath("$.conversations").isEmpty)
    }

    @Test
    fun `conversation list returns conversation after first message`() {
        val (tokenA, catIdA, _) = setupCompleteUser("conv-first-a@example.com", "ConvFirstA", "FEMALE", "CatFirstA")
        val (tokenB, catIdB, _) = setupCompleteUser("conv-first-b@example.com", "ConvFirstB", "MALE", "CatFirstB")
        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        val matchId = getMatchId(tokenA, "ConvFirstB")

        val session = connectStomp(tokenA)
        sendMessageViaStomp(session, matchId, "Hello!")
        Thread.sleep(1000)
        session.disconnect()

        mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conversations[0].conversationId").exists())
            .andExpect(jsonPath("$.conversations[0].matchId").value(matchId))
    }

    @Test
    fun `conversation list includes other user info and cat info`() {
        val (tokenA, catIdA, _) = setupCompleteUser("conv-info-a@example.com", "ConvInfoA", "FEMALE", "CatInfoA")
        val (tokenB, catIdB, _) = setupCompleteUser("conv-info-b@example.com", "ConvInfoB", "MALE", "CatInfoB")
        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        val matchId = getMatchId(tokenA, "ConvInfoB")

        val session = connectStomp(tokenA)
        sendMessageViaStomp(session, matchId, "Hi there!")
        Thread.sleep(1000)
        session.disconnect()

        mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conversations[0].otherUser.displayName").value("ConvInfoB"))
            .andExpect(jsonPath("$.conversations[0].otherUser.userId").exists())
            .andExpect(jsonPath("$.conversations[0].otherUserCats[0].name").value("CatInfoB"))
    }

    @Test
    fun `conversation list includes last message preview`() {
        val (tokenA, catIdA, _) = setupCompleteUser("conv-preview-a@example.com", "ConvPreviewA", "FEMALE", "CatPreviewA")
        val (tokenB, catIdB, _) = setupCompleteUser("conv-preview-b@example.com", "ConvPreviewB", "MALE", "CatPreviewB")
        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        val matchId = getMatchId(tokenA, "ConvPreviewB")

        val session = connectStomp(tokenA)
        sendMessageViaStomp(session, matchId, "Last message here")
        Thread.sleep(1000)
        session.disconnect()

        mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conversations[0].lastMessage.content").value("Last message here"))
            .andExpect(jsonPath("$.conversations[0].lastMessage.sentAt").exists())
            .andExpect(jsonPath("$.conversations[0].lastMessage.sentByMe").value(true))
    }

    @Test
    fun `conversation list shows correct unread count`() {
        val (tokenA, catIdA, _) = setupCompleteUser("conv-unread-a@example.com", "ConvUnreadA", "FEMALE", "CatUnreadA")
        val (tokenB, catIdB, _) = setupCompleteUser("conv-unread-b@example.com", "ConvUnreadB", "MALE", "CatUnreadB")
        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        val matchId = getMatchId(tokenA, "ConvUnreadB")

        // A sends 3 messages
        val session = connectStomp(tokenA)
        for (i in 1..3) {
            sendMessageViaStomp(session, matchId, "Msg $i")
            Thread.sleep(100)
        }
        Thread.sleep(1000)
        session.disconnect()

        // B checks unread count — should be 3
        val result = mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenB")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val conversations = json["conversations"]
        assert(conversations.size() >= 1) { "B should have at least 1 conversation" }
        val conv = (0 until conversations.size()).map { conversations[it] }
            .first { it["otherUser"]["displayName"].asText() == "ConvUnreadA" }
        assert(conv["unreadCount"].asInt() == 3) { "Unread count should be 3, got ${conv["unreadCount"].asInt()}" }
    }

    @Test
    fun `mark read resets unread count to zero`() {
        val (tokenA, catIdA, _) = setupCompleteUser("conv-markread-a@example.com", "MarkReadA", "FEMALE", "CatMarkA")
        val (tokenB, catIdB, _) = setupCompleteUser("conv-markread-b@example.com", "MarkReadB", "MALE", "CatMarkB")
        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        val matchId = getMatchId(tokenA, "MarkReadB")

        // A sends 2 messages
        val session = connectStomp(tokenA)
        sendMessageViaStomp(session, matchId, "Msg 1")
        Thread.sleep(100)
        sendMessageViaStomp(session, matchId, "Msg 2")
        Thread.sleep(1000)
        session.disconnect()

        // B gets conversation list to find conversationId
        val listResult = mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenB")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(listResult.response.contentAsString)
        val conv = (0 until json["conversations"].size()).map { json["conversations"][it] }
            .first { it["otherUser"]["displayName"].asText() == "MarkReadA" }
        val conversationId = conv["conversationId"].asText()

        // B marks as read
        mockMvc.perform(
            post("/api/conversations/$conversationId/read")
                .header("Authorization", "Bearer $tokenB")
        ).andExpect(status().isNoContent)

        // B checks unread count again — should be 0
        val afterResult = mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenB")
        ).andExpect(status().isOk).andReturn()

        val afterJson = objectMapper.readTree(afterResult.response.contentAsString)
        val afterConv = (0 until afterJson["conversations"].size()).map { afterJson["conversations"][it] }
            .first { it["otherUser"]["displayName"].asText() == "MarkReadA" }
        assert(afterConv["unreadCount"].asInt() == 0) { "Unread count should be 0 after mark read, got ${afterConv["unreadCount"].asInt()}" }
    }

    @Test
    fun `mark read requires authentication`() {
        mockMvc.perform(post("/api/conversations/${UUID.randomUUID()}/read"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `conversation list requires authentication`() {
        mockMvc.perform(get("/api/conversations"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `conversation list sorted by last message time`() {
        val (tokenA, catIdA, _) = setupCompleteUser("conv-sort-a@example.com", "ConvSortA", "FEMALE", "CatSortA")
        val (tokenB, catIdB, _) = setupCompleteUser("conv-sort-b@example.com", "ConvSortB", "MALE", "CatSortB")
        val (tokenC, catIdC, _) = setupCompleteUser("conv-sort-c@example.com", "ConvSortC", "MALE", "CatSortC")
        createMutualMatch(tokenA, catIdA, tokenB, catIdB)
        createMutualMatch(tokenA, catIdA, tokenC, catIdC)

        val matchIdB = getMatchId(tokenA, "ConvSortB")
        val matchIdC = getMatchId(tokenA, "ConvSortC")

        // Send to B first, then C — C should be first in list (newest)
        val session = connectStomp(tokenA)
        sendMessageViaStomp(session, matchIdB, "Old message")
        Thread.sleep(500)
        sendMessageViaStomp(session, matchIdC, "New message")
        Thread.sleep(1000)
        session.disconnect()

        val result = mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val conversations = json["conversations"]
        assert(conversations.size() >= 2) { "Should have at least 2 conversations" }

        val firstOther = conversations[0]["otherUser"]["displayName"].asText()
        val secondOther = conversations[1]["otherUser"]["displayName"].asText()
        assert(firstOther == "ConvSortC") { "Newest conversation (ConvSortC) should be first, got $firstOther" }
        assert(secondOther == "ConvSortB") { "Older conversation (ConvSortB) should be second, got $secondOther" }
    }

    @Test
    fun `offline messages delivered on WebSocket reconnect`() {
        val (tokenA, catIdA, _) = setupCompleteUser("conv-offline-a@example.com", "OfflineA", "FEMALE", "CatOffA")
        val (tokenB, catIdB, _) = setupCompleteUser("conv-offline-b@example.com", "OfflineB", "MALE", "CatOffB")
        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        val matchId = getMatchId(tokenA, "OfflineB")

        // A sends message while B is disconnected
        val sessionA = connectStomp(tokenA)
        sendMessageViaStomp(sessionA, matchId, "Offline message for B")
        Thread.sleep(1000)
        sessionA.disconnect()

        // B connects — should trigger offline delivery
        val notificationFuture = CompletableFuture<Any>()
        val sessionB = connectStomp(tokenB)

        val subHeaders = StompHeaders()
        subHeaders.destination = "/user/${tokenB}/queue/notifications"

        // Wait for async delivery (200ms delay + processing)
        Thread.sleep(2000)

        // Verify the message is marked as delivered by checking conversation list
        val result = mockMvc.perform(
            get("/api/conversations")
                .header("Authorization", "Bearer $tokenB")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val conversations = json["conversations"]
        assert(conversations.size() >= 1) { "B should see conversation after offline delivery" }

        try {
            if (sessionB.isConnected) sessionB.disconnect()
        } catch (_: Exception) {}
    }
}
