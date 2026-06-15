package com.catspell.api.discovery

import com.catspell.api.BaseIntegrationTest
import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@SpringBootTest
@AutoConfigureMockMvc
class SwipeMatchIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var s3Client: S3Client

    private fun registerAndGetToken(email: String): String {
        val body = mapOf("email" to email, "password" to "password123")
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return json["accessToken"].asText()
    }

    private fun createProfile(
        token: String,
        displayName: String = "Test User",
        gender: String = "MALE",
        genderPreference: String = "EVERYONE"
    ) {
        val body = mapOf(
            "displayName" to displayName,
            "bio" to "Hello world",
            "dateOfBirth" to "2000-01-15",
            "gender" to gender,
            "genderPreference" to genderPreference,
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

    @Test
    fun `swipe LIKE records swipe and returns matched false when no reciprocal`() {
        val (tokenA, _, _) = setupCompleteUser("swipe-like-a@example.com", "Liker", "FEMALE")
        val (_, catIdB, _) = setupCompleteUser("swipe-like-b@example.com", "LikeTarget", "MALE", catName = "LikeCat")

        val body = mapOf("catId" to catIdB, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.swipeId").exists())
            .andExpect(jsonPath("$.matched").value(false))
            .andExpect(jsonPath("$.matchId").isEmpty)
    }

    @Test
    fun `swipe PASS records swipe`() {
        val (tokenA, _, _) = setupCompleteUser("swipe-pass-a@example.com", "Passer", "FEMALE")
        val (_, catIdB, _) = setupCompleteUser("swipe-pass-b@example.com", "PassTarget", "MALE", catName = "PassCat")

        val body = mapOf("catId" to catIdB, "action" to "PASS")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.swipeId").exists())
            .andExpect(jsonPath("$.matched").value(false))
    }

    @Test
    fun `mutual LIKE creates match`() {
        val (tokenA, catIdA, _) = setupCompleteUser("swipe-mutual-a@example.com", "MutualA", "FEMALE", catName = "CatA")
        val (tokenB, catIdB, _) = setupCompleteUser("swipe-mutual-b@example.com", "MutualB", "MALE", catName = "CatB")

        // A likes B's cat
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdB, "action" to "LIKE")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matched").value(false))

        // B likes A's cat — should trigger match
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdA, "action" to "LIKE")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matched").value(true))
            .andExpect(jsonPath("$.matchId").exists())
    }

    @Test
    fun `duplicate swipe returns conflict`() {
        val (tokenA, _, _) = setupCompleteUser("swipe-dup-a@example.com", "DupA", "FEMALE")
        val (_, catIdB, _) = setupCompleteUser("swipe-dup-b@example.com", "DupB", "MALE", catName = "DupCat")

        val body = mapOf("catId" to catIdB, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk)

        // Second swipe on same cat
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `swipe on own cat returns bad request`() {
        val (tokenA, catIdA, _) = setupCompleteUser("swipe-self-a@example.com", "SelfA", "MALE", catName = "OwnCat")

        val body = mapOf("catId" to catIdA, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `swipe on non-existent cat returns not found`() {
        val (tokenA, _, _) = setupCompleteUser("swipe-nocat-a@example.com", "NoCatA", "FEMALE")

        val body = mapOf("catId" to "00000000-0000-0000-0000-000000000000", "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `swipe requires authentication`() {
        val body = mapOf("catId" to "00000000-0000-0000-0000-000000000000", "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `match is created only once per user pair when liking multiple cats`() {
        val (tokenA, catIdA, _) = setupCompleteUser("swipe-idempotent-a@example.com", "IdempA", "FEMALE", catName = "CatIdempA")
        val tokenB = registerAndGetToken("swipe-idempotent-b@example.com")
        createProfile(tokenB, "IdempB", "MALE")
        setLocation(tokenB)
        addUserPhoto(tokenB)
        val catIdB1 = createCat(tokenB, "CatIdempB1")
        addCatPhoto(tokenB, catIdB1)
        val catIdB2 = createCat(tokenB, "CatIdempB2")
        addCatPhoto(tokenB, catIdB2)

        // A likes B's first cat, B likes A's cat → match
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdB1, "action" to "LIKE")))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdA, "action" to "LIKE")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matched").value(true))

        // A likes B's second cat — should NOT create a second match
        val secondSwipe = mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdB2, "action" to "LIKE")))
        ).andExpect(status().isOk).andReturn()

        // Match list should contain exactly 1 match between A and B
        val result = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val matches = json["matches"]
        val matchesWithB = (0 until matches.size())
            .map { matches[it] }
            .filter { it["otherUser"]["displayName"].asText() == "IdempB" }
        assert(matchesWithB.size == 1) { "Should have exactly 1 match with IdempB, got ${matchesWithB.size}" }
    }

    @Test
    fun `LIKE then PASS from other side does not create match`() {
        val (tokenA, catIdA, _) = setupCompleteUser("swipe-norecip-a@example.com", "NoRecipA", "FEMALE", catName = "CatNR_A")
        val (tokenB, catIdB, _) = setupCompleteUser("swipe-norecip-b@example.com", "NoRecipB", "MALE", catName = "CatNR_B")

        // A likes B's cat
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdB, "action" to "LIKE")))
        ).andExpect(status().isOk)

        // B passes A's cat — no match
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("catId" to catIdA, "action" to "PASS")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matched").value(false))
    }
}
