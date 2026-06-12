package com.catspell.api.cat

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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@SpringBootTest
@AutoConfigureMockMvc
class CatCascadeDeleteIntegrationTest : BaseIntegrationTest() {

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

    private fun createCat(token: String, name: String = "CascadeCat"): String {
        val body = mapOf("name" to name, "age" to 2, "ageUnit" to "YEARS")
        val result = mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asText()
    }

    private fun uploadAndConfirmCatPhoto(token: String, catId: String): Pair<String, String> {
        val uploadBody = mapOf("contentType" to "image/jpeg", "fileName" to "cat.jpg")
        val uploadResult = mockMvc.perform(
            post("/api/cats/$catId/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(uploadBody))
        ).andExpect(status().isOk).andReturn()

        @Suppress("UNCHECKED_CAST")
        val uploadMap = objectMapper.readValue(uploadResult.response.contentAsString, Map::class.java) as Map<String, Any>
        val s3Key = uploadMap["s3Key"] as String
        val photoId = uploadMap["photoId"] as String

        val jpegBytes = createMinimalJpeg()
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("catspell-photos")
                .key(s3Key)
                .contentType("image/jpeg")
                .build(),
            RequestBody.fromBytes(jpegBytes)
        )

        val confirmResult = mockMvc.perform(
            post("/api/cats/$catId/photos/$photoId/confirm")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()

        @Suppress("UNCHECKED_CAST")
        val confirmMap = objectMapper.readValue(confirmResult.response.contentAsString, Map::class.java) as Map<String, Any>
        val thumbnailS3Key = confirmMap["thumbnailS3Key"] as String

        return Pair(s3Key, thumbnailS3Key)
    }

    private fun createMinimalJpeg(): ByteArray {
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 0xFF0000)
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpeg", baos)
        return baos.toByteArray()
    }

    private fun s3ObjectExists(key: String): Boolean {
        return try {
            s3Client.headObject(HeadObjectRequest.builder().bucket("catspell-photos").key(key).build())
            true
        } catch (e: NoSuchKeyException) {
            false
        }
    }

    @Test
    fun `delete cat profile with photos removes cat and photos`() {
        val token = registerAndGetToken("cascade-delete1@example.com")
        val catId = createCat(token)
        val (s3Key, thumbnailKey) = uploadAndConfirmCatPhoto(token, catId)

        // Verify photo exists
        mockMvc.perform(
            get("/api/cats/$catId/photos")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))

        // Delete cat profile
        mockMvc.perform(
            delete("/api/cats/$catId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNoContent)

        // Cat profile gone
        mockMvc.perform(
            get("/api/cats/$catId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)

        // S3 objects cleaned up
        assert(!s3ObjectExists(s3Key)) { "S3 original should be deleted after cat cascade" }
        assert(!s3ObjectExists(thumbnailKey)) { "S3 thumbnail should be deleted after cat cascade" }
    }

    @Test
    fun `delete cat profile then list cats excludes deleted cat`() {
        val token = registerAndGetToken("cascade-delete2@example.com")
        val catId1 = createCat(token, "Cat A")
        val catId2 = createCat(token, "Cat B")

        mockMvc.perform(
            delete("/api/cats/$catId1")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/cats")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Cat B"))
    }

    @Test
    fun `delete cat with multiple photos cleans all S3 objects`() {
        val token = registerAndGetToken("cascade-delete3@example.com")
        val catId = createCat(token)
        val (s3Key1, thumbKey1) = uploadAndConfirmCatPhoto(token, catId)
        val (s3Key2, thumbKey2) = uploadAndConfirmCatPhoto(token, catId)

        // Delete cat
        mockMvc.perform(
            delete("/api/cats/$catId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNoContent)

        // All S3 objects gone
        assert(!s3ObjectExists(s3Key1)) { "S3 original 1 should be deleted" }
        assert(!s3ObjectExists(thumbKey1)) { "S3 thumbnail 1 should be deleted" }
        assert(!s3ObjectExists(s3Key2)) { "S3 original 2 should be deleted" }
        assert(!s3ObjectExists(thumbKey2)) { "S3 thumbnail 2 should be deleted" }
    }
}
