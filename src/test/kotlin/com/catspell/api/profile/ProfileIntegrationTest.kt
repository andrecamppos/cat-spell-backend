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

@SpringBootTest
@AutoConfigureMockMvc
class ProfileIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String = "profile-test@example.com"): String {
        val body = mapOf("email" to email, "password" to "password123")
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return json["accessToken"].asText()
    }

    private fun createProfileBody(
        displayName: String = "Test User",
        bio: String? = "Hello world",
        dateOfBirth: String = "2000-01-15",
        gender: String = "MALE",
        genderPreference: String = "FEMALE",
        ageMin: Int = 18,
        ageMax: Int = 30,
        maxDistanceKm: Int = 50
    ): Map<String, Any?> = mapOf(
        "displayName" to displayName,
        "bio" to bio,
        "dateOfBirth" to dateOfBirth,
        "gender" to gender,
        "genderPreference" to genderPreference,
        "ageMin" to ageMin,
        "ageMax" to ageMax,
        "maxDistanceKm" to maxDistanceKm
    )

    @Test
    fun `create profile successfully`() {
        val token = registerAndGetToken("create-profile@example.com")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProfileBody()))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.bio").value("Hello world"))
            .andExpect(jsonPath("$.gender").value("MALE"))
            .andExpect(jsonPath("$.genderPreference").value("FEMALE"))
            .andExpect(jsonPath("$.ageMin").value(18))
            .andExpect(jsonPath("$.ageMax").value(30))
            .andExpect(jsonPath("$.maxDistanceKm").value(50))
    }

    @Test
    fun `get profile successfully`() {
        val token = registerAndGetToken("get-profile@example.com")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProfileBody()))
        )
        mockMvc.perform(
            get("/api/profile")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Test User"))
    }

    @Test
    fun `get profile not found`() {
        val token = registerAndGetToken("no-profile@example.com")
        mockMvc.perform(
            get("/api/profile")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `update profile successfully`() {
        val token = registerAndGetToken("update-profile@example.com")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProfileBody()))
        )

        val updateBody = mapOf("displayName" to "Updated Name", "bio" to "Updated bio")
        mockMvc.perform(
            put("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Updated Name"))
            .andExpect(jsonPath("$.bio").value("Updated bio"))
    }

    @Test
    fun `update location successfully`() {
        val token = registerAndGetToken("update-location@example.com")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProfileBody()))
        )

        val locationBody = mapOf("latitude" to 40.7128, "longitude" to -74.0060)
        mockMvc.perform(
            put("/api/profile/location")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(locationBody))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.latitude").value(40.7128))
            .andExpect(jsonPath("$.longitude").value(-74.0060))
    }

    @Test
    fun `create profile duplicate returns conflict`() {
        val token = registerAndGetToken("duplicate-profile@example.com")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProfileBody()))
        )
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProfileBody()))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `create profile underage returns bad request`() {
        val token = registerAndGetToken("underage@example.com")
        val body = createProfileBody(dateOfBirth = "2015-01-15")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create profile invalid age range returns bad request`() {
        val token = registerAndGetToken("bad-age-range@example.com")
        val body = createProfileBody(ageMin = 30, ageMax = 20)
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create profile missing required fields returns bad request`() {
        val token = registerAndGetToken("missing-fields@example.com")
        val body = mapOf("displayName" to "Test")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create profile invalid gender returns bad request`() {
        val token = registerAndGetToken("invalid-gender@example.com")
        val body = createProfileBody(gender = "OTHER")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `update profile partial only changes provided fields`() {
        val token = registerAndGetToken("partial-update@example.com")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProfileBody()))
        )

        val updateBody = mapOf("bio" to "Only bio changed")
        mockMvc.perform(
            put("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.bio").value("Only bio changed"))
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.gender").value("MALE"))
            .andExpect(jsonPath("$.ageMin").value(18))
            .andExpect(jsonPath("$.ageMax").value(30))
            .andExpect(jsonPath("$.maxDistanceKm").value(50))
    }

    @Test
    fun `create profile bio too long returns bad request`() {
        val token = registerAndGetToken("long-bio@example.com")
        val body = createProfileBody(bio = "a".repeat(1001))
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `profile requires authentication`() {
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `update location without profile returns not found`() {
        val token = registerAndGetToken("no-profile-location@example.com")
        val locationBody = mapOf("latitude" to 40.7128, "longitude" to -74.0060)
        mockMvc.perform(
            put("/api/profile/location")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(locationBody))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `update location invalid coordinates returns bad request`() {
        val token = registerAndGetToken("invalid-coords@example.com")
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProfileBody()))
        )

        val body = mapOf("latitude" to 100.0, "longitude" to -74.0060)
        mockMvc.perform(
            put("/api/profile/location")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }
}
