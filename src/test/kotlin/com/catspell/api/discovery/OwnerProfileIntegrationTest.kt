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
class OwnerProfileIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var s3Client: S3Client

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

    private fun createProfile(
        token: String,
        displayName: String = "Test User",
        bio: String = "Hello world",
        dateOfBirth: String = "2000-01-15",
        gender: String = "MALE",
        genderPreference: String = "EVERYONE"
    ) {
        val body = mapOf(
            "displayName" to displayName,
            "bio" to bio,
            "dateOfBirth" to dateOfBirth,
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

    private fun createCat(token: String, name: String = "TestCat", breed: String? = "Persian"): String {
        val body = mutableMapOf<String, Any>("name" to name, "age" to 2, "ageUnit" to "YEARS")
        if (breed != null) body["breed"] = breed
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

    @Test
    fun `owner profile returns displayName bio age gender`() {
        val ownerToken = registerAndGetToken("owner-profile-a@example.com")
        createProfile(ownerToken, "OwnerAlice", "Cat lover", "2000-01-15", "FEMALE")
        setLocation(ownerToken)
        addUserPhoto(ownerToken)
        val catId = createCat(ownerToken, "MyCat")
        addCatPhoto(ownerToken, catId)

        val viewerToken = registerAndGetToken("owner-viewer-a@example.com")

        mockMvc.perform(
            get("/api/discovery/cats/$catId/owner")
                .header("Authorization", "Bearer $viewerToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("OwnerAlice"))
            .andExpect(jsonPath("$.bio").value("Cat lover"))
            .andExpect(jsonPath("$.gender").value("FEMALE"))
            .andExpect(jsonPath("$.age").isNumber)
    }

    @Test
    fun `owner profile age is calculated from DOB`() {
        val ownerToken = registerAndGetToken("owner-age-calc@example.com")
        createProfile(ownerToken, "AgeCalcOwner", "Bio", "2000-06-15", "MALE")
        setLocation(ownerToken)
        addUserPhoto(ownerToken)
        val catId = createCat(ownerToken, "AgeCat")
        addCatPhoto(ownerToken, catId)

        val viewerToken = registerAndGetToken("owner-age-viewer@example.com")

        val result = mockMvc.perform(
            get("/api/discovery/cats/$catId/owner")
                .header("Authorization", "Bearer $viewerToken")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val age = json["age"].asInt()
        assert(age >= 25 && age <= 26) { "Age should be approximately 25-26, got $age" }
    }

    @Test
    fun `owner profile includes photos`() {
        val ownerToken = registerAndGetToken("owner-photos@example.com")
        createProfile(ownerToken, "PhotoOwner", "Bio", "2000-01-15", "MALE")
        setLocation(ownerToken)
        addUserPhoto(ownerToken)
        addUserPhoto(ownerToken)
        val catId = createCat(ownerToken, "PhotoCat")
        addCatPhoto(ownerToken, catId)

        val viewerToken = registerAndGetToken("owner-photos-viewer@example.com")

        mockMvc.perform(
            get("/api/discovery/cats/$catId/owner")
                .header("Authorization", "Bearer $viewerToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photos").isArray)
            .andExpect(jsonPath("$.photos.length()").value(2))
            .andExpect(jsonPath("$.photos[0].s3Key").exists())
            .andExpect(jsonPath("$.photos[0].thumbnailS3Key").exists())
    }

    @Test
    fun `owner profile includes all cats`() {
        val ownerToken = registerAndGetToken("owner-allcats@example.com")
        createProfile(ownerToken, "MultiCatOwner", "Bio", "2000-01-15", "FEMALE")
        setLocation(ownerToken)
        addUserPhoto(ownerToken)
        val cat1Id = createCat(ownerToken, "CatOne", "Siamese")
        addCatPhoto(ownerToken, cat1Id)
        val cat2Id = createCat(ownerToken, "CatTwo", "Persian")
        addCatPhoto(ownerToken, cat2Id)

        val viewerToken = registerAndGetToken("owner-allcats-viewer@example.com")

        val result = mockMvc.perform(
            get("/api/discovery/cats/$cat1Id/owner")
                .header("Authorization", "Bearer $viewerToken")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cats = json["cats"]
        assert(cats.size() >= 2) { "Owner should have at least 2 cats, got ${cats.size()}" }
        val catNames = (0 until cats.size()).map { cats[it]["name"].asText() }
        assert("CatOne" in catNames) { "CatOne should appear" }
        assert("CatTwo" in catNames) { "CatTwo should appear" }
    }

    @Test
    fun `owner profile non-existent cat returns 404`() {
        val token = registerAndGetToken("owner-nocat@example.com")
        mockMvc.perform(
            get("/api/discovery/cats/00000000-0000-0000-0000-000000000000/owner")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `owner profile requires authentication`() {
        mockMvc.perform(get("/api/discovery/cats/00000000-0000-0000-0000-000000000000/owner"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `owner profile accessible by any authenticated user`() {
        val ownerToken = registerAndGetToken("owner-access-a@example.com")
        createProfile(ownerToken, "AccessOwner", "Bio", "2000-01-15", "MALE")
        setLocation(ownerToken)
        addUserPhoto(ownerToken)
        val catId = createCat(ownerToken, "AccessCat")
        addCatPhoto(ownerToken, catId)

        val otherToken = registerAndGetToken("owner-access-other@example.com")

        mockMvc.perform(
            get("/api/discovery/cats/$catId/owner")
                .header("Authorization", "Bearer $otherToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("AccessOwner"))
    }
}
