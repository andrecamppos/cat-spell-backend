package com.catspell.api.profile

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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class PhotoIntegrationTest : BaseIntegrationTest() {

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

    private fun createProfile(token: String) {
        val body = mapOf(
            "displayName" to "Test User",
            "bio" to "Hello world",
            "dateOfBirth" to "2000-01-15",
            "gender" to "MALE",
            "genderPreference" to "FEMALE",
            "ageMin" to 18,
            "ageMax" to 30,
            "maxDistanceKm" to 50
        )
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated)
    }

    private fun requestUploadUrl(token: String, contentType: String = "image/jpeg", fileName: String = "cat.jpg"): Map<String, Any> {
        val body = mapOf("contentType" to contentType, "fileName" to fileName)
        val result = mockMvc.perform(
            post("/api/profile/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }

    private fun uploadToS3AndConfirm(token: String, s3Key: String, photoId: String): Map<String, Any> {
        // Upload a small valid JPEG directly to S3 via the S3 client (simulating presigned URL upload)
        val jpegBytes = createMinimalJpeg()
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("catspell-photos")
                .key(s3Key)
                .contentType("image/jpeg")
                .build(),
            RequestBody.fromBytes(jpegBytes)
        )

        // Confirm the upload
        val result = mockMvc.perform(
            post("/api/profile/photos/$photoId/confirm")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }

    private fun createMinimalJpeg(): ByteArray {
        // Create a minimal 1x1 JPEG image
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 0xFF0000)
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpeg", baos)
        return baos.toByteArray()
    }

    @Test
    fun `request upload url successfully`() {
        val token = registerAndGetToken("photo-upload-url@example.com")
        createProfile(token)

        val body = mapOf("contentType" to "image/jpeg", "fileName" to "cat.jpg")
        mockMvc.perform(
            post("/api/profile/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photoId").exists())
            .andExpect(jsonPath("$.uploadUrl").exists())
            .andExpect(jsonPath("$.s3Key").exists())
    }

    @Test
    fun `request upload url invalid type returns bad request`() {
        val token = registerAndGetToken("photo-invalid-type@example.com")
        createProfile(token)

        val body = mapOf("contentType" to "image/gif", "fileName" to "cat.gif")
        mockMvc.perform(
            post("/api/profile/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `request upload url exceeds limit returns bad request`() {
        val token = registerAndGetToken("photo-limit@example.com")
        createProfile(token)

        // Create 6 photos (request + confirm each)
        repeat(6) { i ->
            val uploadResult = requestUploadUrl(token, fileName = "cat$i.jpg")
            uploadToS3AndConfirm(token, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
        }

        // 7th request should fail
        val body = mapOf("contentType" to "image/jpeg", "fileName" to "cat7.jpg")
        mockMvc.perform(
            post("/api/profile/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `confirm upload successfully`() {
        val token = registerAndGetToken("photo-confirm@example.com")
        createProfile(token)

        val uploadResult = requestUploadUrl(token)
        val s3Key = uploadResult["s3Key"] as String
        val photoId = uploadResult["photoId"] as String

        val confirmResult = uploadToS3AndConfirm(token, s3Key, photoId)

        assert(confirmResult["status"] == "ACTIVE")
        assert(confirmResult["thumbnailS3Key"] != null)
        assert(confirmResult["photoId"] == photoId)
    }

    @Test
    fun `confirm upload photo not found returns not found`() {
        val token = registerAndGetToken("photo-confirm-404@example.com")
        createProfile(token)

        val randomId = UUID.randomUUID()
        mockMvc.perform(
            post("/api/profile/photos/$randomId/confirm")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete photo successfully`() {
        val token = registerAndGetToken("photo-delete@example.com")
        createProfile(token)

        val uploadResult = requestUploadUrl(token)
        uploadToS3AndConfirm(token, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
        val photoId = uploadResult["photoId"] as String

        mockMvc.perform(
            delete("/api/profile/photos/$photoId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNoContent)

        // Verify photo is gone
        mockMvc.perform(
            get("/api/profile/photos")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `delete photo not owned returns not found`() {
        val token1 = registerAndGetToken("photo-owner@example.com")
        createProfile(token1)
        val uploadResult = requestUploadUrl(token1)
        uploadToS3AndConfirm(token1, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
        val photoId = uploadResult["photoId"] as String

        // Second user tries to delete first user's photo
        val token2 = registerAndGetToken("photo-thief@example.com")
        createProfile(token2)

        mockMvc.perform(
            delete("/api/profile/photos/$photoId")
                .header("Authorization", "Bearer $token2")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `reorder photos successfully`() {
        val token = registerAndGetToken("photo-reorder@example.com")
        createProfile(token)

        // Upload 3 photos
        val photoIds = mutableListOf<String>()
        repeat(3) { i ->
            val uploadResult = requestUploadUrl(token, fileName = "cat$i.jpg")
            uploadToS3AndConfirm(token, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
            photoIds.add(uploadResult["photoId"] as String)
        }

        // Reverse order
        val reversed = photoIds.reversed()
        val body = mapOf("photoIds" to reversed)
        mockMvc.perform(
            put("/api/profile/photos/reorder")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)

        // Verify new order
        val result = mockMvc.perform(
            get("/api/profile/photos")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andReturn()
        @Suppress("UNCHECKED_CAST")
        val photos = objectMapper.readValue(result.response.contentAsString, List::class.java) as List<Map<String, Any>>
        assert(photos[0]["id"] == reversed[0])
        assert(photos[1]["id"] == reversed[1])
        assert(photos[2]["id"] == reversed[2])
    }

    @Test
    fun `reorder photos mismatch returns bad request`() {
        val token = registerAndGetToken("photo-reorder-bad@example.com")
        createProfile(token)

        val uploadResult = requestUploadUrl(token)
        uploadToS3AndConfirm(token, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)

        // Send incomplete list
        val body = mapOf("photoIds" to listOf(UUID.randomUUID().toString()))
        mockMvc.perform(
            put("/api/profile/photos/reorder")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `list photos successfully`() {
        val token = registerAndGetToken("photo-list@example.com")
        createProfile(token)

        repeat(2) { i ->
            val uploadResult = requestUploadUrl(token, fileName = "cat$i.jpg")
            uploadToS3AndConfirm(token, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
        }

        mockMvc.perform(
            get("/api/profile/photos")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].displayOrder").value(0))
            .andExpect(jsonPath("$[1].displayOrder").value(1))
    }

    @Test
    fun `photo endpoints require authentication`() {
        val body = mapOf("contentType" to "image/jpeg", "fileName" to "cat.jpg")
        mockMvc.perform(
            post("/api/profile/photos/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnauthorized)
    }
}
