package com.catspell.api.moderation

import com.catspell.api.BaseIntegrationTest
import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
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
class BlockEndpointIntegrationTest : BaseIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var s3Client: S3Client

    private fun registerAndGetToken(email: String): String {
        val body = mapOf("email" to email, "password" to "password123")
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
        markEmailVerified(email)
        val result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["accessToken"].asText()
    }

    private fun createProfile(token: String, displayName: String, gender: String) {
        val body = mapOf(
            "displayName" to displayName, "bio" to "Hello world", "dateOfBirth" to "2000-01-15",
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

    private fun swipe(token: String, catId: String) {
        mockMvc.perform(post("/api/discovery/swipe").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("catId" to catId, "action" to "LIKE")))).andExpect(status().isOk)
    }

    // ---- tests ----

    @Test
    fun `block then list shows minimal identity`() {
        val a = setupUser("ep-listA@example.com", "EpListA", "FEMALE", "EpListCatA")
        val b = setupUser("ep-listB@example.com", "EpListB", "MALE", "EpListCatB")

        mockMvc.perform(post("/api/blocks/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/blocks").header("Authorization", "Bearer ${a.token}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.blocks.length()").value(1))
            .andExpect(jsonPath("$.blocks[0].userId").value(b.id.toString()))
            .andExpect(jsonPath("$.blocks[0].displayName").value("EpListB"))
            .andExpect(jsonPath("$.blocks[0].photoThumbnail").exists())
            .andExpect(jsonPath("$.blocks[0].blockedAt").exists())
            // Minimal identity only — no bio / cats fields (D-13).
            .andExpect(jsonPath("$.blocks[0].bio").doesNotExist())
            .andExpect(jsonPath("$.blocks[0].cats").doesNotExist())
    }

    @Test
    fun `self block returns 400 problem body`() {
        val a = setupUser("ep-selfA@example.com", "EpSelfA", "MALE", "EpSelfCat")

        mockMvc.perform(post("/api/blocks/${a.id}").header("Authorization", "Bearer ${a.token}"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.detail").exists())
    }

    @Test
    fun `block is idempotent and list shows target once`() {
        val a = setupUser("ep-idemA@example.com", "EpIdemA", "FEMALE", "EpIdemCatA")
        val b = setupUser("ep-idemB@example.com", "EpIdemB", "MALE", "EpIdemCatB")

        mockMvc.perform(post("/api/blocks/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNoContent)
        mockMvc.perform(post("/api/blocks/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/blocks").header("Authorization", "Bearer ${a.token}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.blocks.length()").value(1))
    }

    @Test
    fun `unblock removes from list and is idempotent`() {
        val a = setupUser("ep-ubA@example.com", "EpUbA", "FEMALE", "EpUbCatA")
        val b = setupUser("ep-ubB@example.com", "EpUbB", "MALE", "EpUbCatB")

        mockMvc.perform(post("/api/blocks/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNoContent)
        mockMvc.perform(delete("/api/blocks/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/blocks").header("Authorization", "Bearer ${a.token}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.blocks.length()").value(0))

        // Second delete is still 204 (idempotent).
        mockMvc.perform(delete("/api/blocks/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNoContent)
    }

    @Test
    fun `each caller sees only their own block list`() {
        val a = setupUser("ep-idorA@example.com", "EpIdorA", "FEMALE", "EpIdorCatA")
        val b = setupUser("ep-idorB@example.com", "EpIdorB", "MALE", "EpIdorCatB")

        mockMvc.perform(post("/api/blocks/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNoContent)

        // B has blocked no-one — B's list is empty and never reveals A's blocks (IDOR).
        mockMvc.perform(get("/api/blocks").header("Authorization", "Bearer ${b.token}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.blocks.length()").value(0))
    }

    @Test
    fun `unmatch returns 204 then 404 on repeat`() {
        val a = setupUser("ep-unmA@example.com", "EpUnmA", "FEMALE", "EpUnmCatA")
        val b = setupUser("ep-unmB@example.com", "EpUnmB", "MALE", "EpUnmCatB")

        // Drive a mutual like -> active match.
        swipe(a.token, b.catId)
        swipe(b.token, a.catId)

        mockMvc.perform(delete("/api/matches/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNoContent)
        // No active match anymore -> 404.
        mockMvc.perform(delete("/api/matches/${b.id}").header("Authorization", "Bearer ${a.token}")).andExpect(status().isNotFound)
    }
}
