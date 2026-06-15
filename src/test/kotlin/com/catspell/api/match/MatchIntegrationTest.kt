package com.catspell.api.match

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
class MatchIntegrationTest : BaseIntegrationTest() {

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

    @Test
    fun `match list returns empty array when no matches`() {
        val (tokenA, _, _) = setupCompleteUser("match-empty-a@example.com", "EmptyA")

        mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matches").isArray)
            .andExpect(jsonPath("$.matches").isEmpty)
    }

    @Test
    fun `mutual like creates match visible in both users lists`() {
        val (tokenA, catIdA, _) = setupCompleteUser("match-mutual-a@example.com", "MutualMatchA", "FEMALE", catName = "CatMA")
        val (tokenB, catIdB, _) = setupCompleteUser("match-mutual-b@example.com", "MutualMatchB", "MALE", catName = "CatMB")

        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        // A sees B in their match list
        val resultA = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val jsonA = objectMapper.readTree(resultA.response.contentAsString)
        val matchesA = jsonA["matches"]
        val matchB = (0 until matchesA.size()).firstOrNull { matchesA[it]["otherUser"]["displayName"].asText() == "MutualMatchB" }
        assert(matchB != null) { "A should see B in match list" }

        // B sees A in their match list
        val resultB = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenB")
        ).andExpect(status().isOk).andReturn()

        val jsonB = objectMapper.readTree(resultB.response.contentAsString)
        val matchesB = jsonB["matches"]
        val matchA = (0 until matchesB.size()).firstOrNull { matchesB[it]["otherUser"]["displayName"].asText() == "MutualMatchA" }
        assert(matchA != null) { "B should see A in match list" }
    }

    @Test
    fun `match includes other users displayName and photo thumbnail`() {
        val (tokenA, catIdA, _) = setupCompleteUser("match-info-a@example.com", "InfoUserA", "FEMALE", catName = "InfoCatA")
        val (tokenB, catIdB, _) = setupCompleteUser("match-info-b@example.com", "InfoUserB", "MALE", catName = "InfoCatB")

        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        val result = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val matches = json["matches"]
        val match = (0 until matches.size()).map { matches[it] }
            .first { it["otherUser"]["displayName"].asText() == "InfoUserB" }

        assert(match["matchId"] != null) { "matchId should exist" }
        assert(match["matchedAt"] != null) { "matchedAt should exist" }
        assert(match["otherUser"]["userId"] != null) { "otherUser.userId should exist" }
        assert(match["otherUser"]["photoThumbnail"] != null) { "otherUser.photoThumbnail should exist" }
    }

    @Test
    fun `match includes other users cats with names and photo thumbnails`() {
        val (tokenA, catIdA, _) = setupCompleteUser("match-cats-a@example.com", "CatsUserA", "FEMALE", catName = "CatCA")
        val (tokenB, catIdB, _) = setupCompleteUser("match-cats-b@example.com", "CatsUserB", "MALE", catName = "CatCB")

        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        val result = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val matches = json["matches"]
        val match = (0 until matches.size()).map { matches[it] }
            .first { it["otherUser"]["displayName"].asText() == "CatsUserB" }

        val otherCats = match["otherUserCats"]
        assert(otherCats.size() >= 1) { "Other user should have at least 1 cat" }
        val catNames = (0 until otherCats.size()).map { otherCats[it]["name"].asText() }
        assert("CatCB" in catNames) { "CatCB should appear in other user's cats" }
    }

    @Test
    fun `match list requires authentication`() {
        mockMvc.perform(get("/api/matches"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `user with multiple matches sees all of them`() {
        val (tokenA, catIdA, _) = setupCompleteUser("match-multi-a@example.com", "MultiA", "FEMALE", catName = "MultiCatA")
        val (tokenB, catIdB, _) = setupCompleteUser("match-multi-b@example.com", "MultiB", "MALE", catName = "MultiCatB")
        val (tokenC, catIdC, _) = setupCompleteUser("match-multi-c@example.com", "MultiC", "MALE", catName = "MultiCatC")

        createMutualMatch(tokenA, catIdA, tokenB, catIdB)
        createMutualMatch(tokenA, catIdA, tokenC, catIdC)

        val result = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val matches = json["matches"]
        val otherNames = (0 until matches.size()).map { matches[it]["otherUser"]["displayName"].asText() }
        assert("MultiB" in otherNames) { "MultiB should appear in matches" }
        assert("MultiC" in otherNames) { "MultiC should appear in matches" }
    }

    @Test
    fun `match list correctly resolves other user from both sides`() {
        val (tokenA, catIdA, _) = setupCompleteUser("match-resolve-a@example.com", "ResolveA", "FEMALE", catName = "ResolveCatA")
        val (tokenB, catIdB, _) = setupCompleteUser("match-resolve-b@example.com", "ResolveB", "MALE", catName = "ResolveCatB")

        createMutualMatch(tokenA, catIdA, tokenB, catIdB)

        // A sees B as other
        val resultA = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()
        val jsonA = objectMapper.readTree(resultA.response.contentAsString)
        val matchA = (0 until jsonA["matches"].size()).map { jsonA["matches"][it] }
            .first { it["otherUser"]["displayName"].asText() == "ResolveB" }
        assert(matchA["otherUser"]["displayName"].asText() == "ResolveB") { "A should see B as other" }

        // B sees A as other
        val resultB = mockMvc.perform(
            get("/api/matches")
                .header("Authorization", "Bearer $tokenB")
        ).andExpect(status().isOk).andReturn()
        val jsonB = objectMapper.readTree(resultB.response.contentAsString)
        val matchB = (0 until jsonB["matches"].size()).map { jsonB["matches"][it] }
            .first { it["otherUser"]["displayName"].asText() == "ResolveA" }
        assert(matchB["otherUser"]["displayName"].asText() == "ResolveA") { "B should see A as other" }
    }
}
