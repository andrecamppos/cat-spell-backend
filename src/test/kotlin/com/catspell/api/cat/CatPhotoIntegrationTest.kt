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
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class CatPhotoIntegrationTest : BaseIntegrationTest() {

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

    private fun requestCatUploadUrl(token: String, catId: String, contentType: String = "image/jpeg", fileName: String = "cat.jpg"): Map<String, Any> {
        val body = mapOf("contentType" to contentType, "fileName" to fileName)
        val result = mockMvc.perform(
            post("/api/cats/$catId/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }

    private fun uploadToS3AndConfirm(token: String, catId: String, s3Key: String, photoId: String): Map<String, Any> {
        val jpegBytes = createMinimalJpeg()
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("catspell-photos")
                .key(s3Key)
                .contentType("image/jpeg")
                .build(),
            RequestBody.fromBytes(jpegBytes)
        )

        val result = mockMvc.perform(
            post("/api/cats/$catId/photos/$photoId/confirm")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }

    private fun createMinimalJpeg(): ByteArray {
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 0xFF0000)
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpeg", baos)
        return baos.toByteArray()
    }

    @Test
    fun `request upload url for cat photo`() {
        val token = registerAndGetToken("catphoto-upload@example.com")
        val catId = createCat(token)

        val body = mapOf("contentType" to "image/jpeg", "fileName" to "cat.jpg")
        mockMvc.perform(
            post("/api/cats/$catId/photos/upload-url")
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
    fun `request upload url with invalid content type returns bad request`() {
        val token = registerAndGetToken("catphoto-badtype@example.com")
        val catId = createCat(token)

        val body = mapOf("contentType" to "image/gif", "fileName" to "cat.gif")
        mockMvc.perform(
            post("/api/cats/$catId/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `upload 11th photo returns bad request`() {
        val token = registerAndGetToken("catphoto-limit@example.com")
        val catId = createCat(token)

        repeat(10) { i ->
            val uploadResult = requestCatUploadUrl(token, catId, fileName = "cat$i.jpg")
            uploadToS3AndConfirm(token, catId, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
        }

        val body = mapOf("contentType" to "image/jpeg", "fileName" to "cat11.jpg")
        mockMvc.perform(
            post("/api/cats/$catId/photos/upload-url")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `confirm upload successfully`() {
        val token = registerAndGetToken("catphoto-confirm@example.com")
        val catId = createCat(token)

        val uploadResult = requestCatUploadUrl(token, catId)
        val confirmResult = uploadToS3AndConfirm(token, catId, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)

        assert(confirmResult["status"] == "ACTIVE")
        assert(confirmResult["thumbnailS3Key"] != null)
    }

    @Test
    fun `confirm upload for non-existent photo returns not found`() {
        val token = registerAndGetToken("catphoto-confirm404@example.com")
        val catId = createCat(token)

        mockMvc.perform(
            post("/api/cats/$catId/photos/${UUID.randomUUID()}/confirm")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `list cat photos returns active photos in order`() {
        val token = registerAndGetToken("catphoto-list@example.com")
        val catId = createCat(token)

        repeat(2) { i ->
            val uploadResult = requestCatUploadUrl(token, catId, fileName = "cat$i.jpg")
            uploadToS3AndConfirm(token, catId, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
        }

        mockMvc.perform(
            get("/api/cats/$catId/photos")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].displayOrder").value(0))
            .andExpect(jsonPath("$[1].displayOrder").value(1))
    }

    @Test
    fun `delete cat photo`() {
        val token = registerAndGetToken("catphoto-delete@example.com")
        val catId = createCat(token)

        val uploadResult = requestCatUploadUrl(token, catId)
        uploadToS3AndConfirm(token, catId, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
        val photoId = uploadResult["photoId"] as String

        mockMvc.perform(
            delete("/api/cats/$catId/photos/$photoId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/cats/$catId/photos")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `delete another user cat photo returns not found`() {
        val token1 = registerAndGetToken("catphoto-owner1@example.com")
        val token2 = registerAndGetToken("catphoto-thief1@example.com")
        val catId = createCat(token1)

        val uploadResult = requestCatUploadUrl(token1, catId)
        uploadToS3AndConfirm(token1, catId, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
        val photoId = uploadResult["photoId"] as String

        mockMvc.perform(
            delete("/api/cats/$catId/photos/$photoId")
                .header("Authorization", "Bearer $token2")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `reorder cat photos`() {
        val token = registerAndGetToken("catphoto-reorder@example.com")
        val catId = createCat(token)

        val photoIds = mutableListOf<String>()
        repeat(3) { i ->
            val uploadResult = requestCatUploadUrl(token, catId, fileName = "cat$i.jpg")
            uploadToS3AndConfirm(token, catId, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)
            photoIds.add(uploadResult["photoId"] as String)
        }

        val reversed = photoIds.reversed()
        val body = mapOf("photoIds" to reversed)
        mockMvc.perform(
            put("/api/cats/$catId/photos/reorder")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)

        val result = mockMvc.perform(
            get("/api/cats/$catId/photos")
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
    fun `reorder with wrong photo IDs returns bad request`() {
        val token = registerAndGetToken("catphoto-reorderbad@example.com")
        val catId = createCat(token)

        val uploadResult = requestCatUploadUrl(token, catId)
        uploadToS3AndConfirm(token, catId, uploadResult["s3Key"] as String, uploadResult["photoId"] as String)

        val body = mapOf("photoIds" to listOf(UUID.randomUUID().toString()))
        mockMvc.perform(
            put("/api/cats/$catId/photos/reorder")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `photo operations on another user cat returns not found`() {
        val token1 = registerAndGetToken("catphoto-owner2@example.com")
        val token2 = registerAndGetToken("catphoto-thief2@example.com")
        val catId = createCat(token1)

        val body = mapOf("contentType" to "image/jpeg", "fileName" to "cat.jpg")
        mockMvc.perform(
            post("/api/cats/$catId/photos/upload-url")
                .header("Authorization", "Bearer $token2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isNotFound)
    }
}
