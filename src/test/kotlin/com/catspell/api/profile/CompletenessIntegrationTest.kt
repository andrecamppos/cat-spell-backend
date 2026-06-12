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

@SpringBootTest
@AutoConfigureMockMvc
class CompletenessIntegrationTest : BaseIntegrationTest() {

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

    private fun setLocation(token: String) {
        val body = mapOf("latitude" to 40.7128, "longitude" to -74.0060)
        mockMvc.perform(
            put("/api/profile/location")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk)
    }

    private fun uploadAndConfirmPhoto(token: String) {
        val uploadBody = mapOf("contentType" to "image/jpeg", "fileName" to "cat.jpg")
        val uploadResult = mockMvc.perform(
            post("/api/profile/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(uploadBody))
        ).andExpect(status().isOk).andReturn()

        @Suppress("UNCHECKED_CAST")
        val uploadResponse = objectMapper.readValue(uploadResult.response.contentAsString, Map::class.java) as Map<String, Any>
        val s3Key = uploadResponse["s3Key"] as String
        val photoId = uploadResponse["photoId"] as String

        // Upload a minimal JPEG to S3
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpeg", baos)
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("catspell-photos")
                .key(s3Key)
                .contentType("image/jpeg")
                .build(),
            RequestBody.fromBytes(baos.toByteArray())
        )

        mockMvc.perform(
            post("/api/profile/photos/$photoId/confirm")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
    }

    @Test
    fun `incomplete without profile`() {
        val token = registerAndGetToken("completeness-no-profile@example.com")

        mockMvc.perform(
            get("/api/profile/completeness")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.complete").value(false))
            .andExpect(jsonPath("$.missingFields").isArray)
            .andExpect(jsonPath("$.missingFields[0]").value("profile"))
    }

    @Test
    fun `incomplete without photo`() {
        val token = registerAndGetToken("completeness-no-photo@example.com")
        createProfile(token)
        setLocation(token)

        mockMvc.perform(
            get("/api/profile/completeness")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.complete").value(false))
            .andExpect(jsonPath("$.missingFields[?(@ == 'photo')]").exists())
    }

    @Test
    fun `incomplete without location`() {
        val token = registerAndGetToken("completeness-no-location@example.com")
        createProfile(token)
        uploadAndConfirmPhoto(token)

        mockMvc.perform(
            get("/api/profile/completeness")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.complete").value(false))
            .andExpect(jsonPath("$.missingFields[?(@ == 'location')]").exists())
    }

    @Test
    fun `complete profile`() {
        val token = registerAndGetToken("completeness-full@example.com")
        createProfile(token)
        setLocation(token)
        uploadAndConfirmPhoto(token)

        mockMvc.perform(
            get("/api/profile/completeness")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.complete").value(true))
            .andExpect(jsonPath("$.missingFields").isEmpty)
    }
}
