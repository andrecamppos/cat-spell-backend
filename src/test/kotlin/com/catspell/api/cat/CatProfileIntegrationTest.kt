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

@SpringBootTest
@AutoConfigureMockMvc
class CatProfileIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

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

    private fun createCatBody(
        name: String = "Whiskers",
        age: Int = 3,
        ageUnit: String = "YEARS",
        breed: String? = "Persian",
        bio: String? = "A fluffy cat"
    ): Map<String, Any?> = mapOf(
        "name" to name,
        "age" to age,
        "ageUnit" to ageUnit,
        "breed" to breed,
        "bio" to bio
    )

    @Test
    fun `create cat profile successfully`() {
        val token = registerAndGetToken("cat-create@example.com")
        mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody()))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Whiskers"))
            .andExpect(jsonPath("$.age").value(3))
            .andExpect(jsonPath("$.ageUnit").value("YEARS"))
            .andExpect(jsonPath("$.breed").value("Persian"))
            .andExpect(jsonPath("$.bio").value("A fluffy cat"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists())
    }

    @Test
    fun `create cat with missing required fields returns bad request`() {
        val token = registerAndGetToken("cat-missing@example.com")
        val body = mapOf("breed" to "Persian")
        mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create 6th cat returns conflict`() {
        val token = registerAndGetToken("cat-limit@example.com")
        repeat(5) { i ->
            mockMvc.perform(
                post("/api/cats")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createCatBody(name = "Cat $i")))
            ).andExpect(status().isCreated)
        }
        mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody(name = "Cat 6")))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `list cats returns all user cats`() {
        val token = registerAndGetToken("cat-list@example.com")
        mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody(name = "Cat A")))
        )
        mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody(name = "Cat B")))
        )
        mockMvc.perform(
            get("/api/cats")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `get single cat profile`() {
        val token = registerAndGetToken("cat-get@example.com")
        val createResult = mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody()))
        ).andReturn()
        val catId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            get("/api/cats/$catId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Whiskers"))
            .andExpect(jsonPath("$.id").value(catId))
    }

    @Test
    fun `get another user cat returns not found`() {
        val token1 = registerAndGetToken("cat-owner1@example.com")
        val token2 = registerAndGetToken("cat-other1@example.com")
        val createResult = mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody()))
        ).andReturn()
        val catId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            get("/api/cats/$catId")
                .header("Authorization", "Bearer $token2")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `update cat profile`() {
        val token = registerAndGetToken("cat-update@example.com")
        val createResult = mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody()))
        ).andReturn()
        val catId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        val updateBody = mapOf("name" to "Updated Whiskers", "bio" to "Updated bio")
        mockMvc.perform(
            put("/api/cats/$catId")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Whiskers"))
            .andExpect(jsonPath("$.bio").value("Updated bio"))
            .andExpect(jsonPath("$.breed").value("Persian"))
    }

    @Test
    fun `update another user cat returns not found`() {
        val token1 = registerAndGetToken("cat-owner2@example.com")
        val token2 = registerAndGetToken("cat-other2@example.com")
        val createResult = mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody()))
        ).andReturn()
        val catId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            put("/api/cats/$catId")
                .header("Authorization", "Bearer $token2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "Hacked")))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete cat profile`() {
        val token = registerAndGetToken("cat-delete@example.com")
        val createResult = mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody()))
        ).andReturn()
        val catId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            delete("/api/cats/$catId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/cats/$catId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete another user cat returns not found`() {
        val token1 = registerAndGetToken("cat-owner3@example.com")
        val token2 = registerAndGetToken("cat-other3@example.com")
        val createResult = mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCatBody()))
        ).andReturn()
        val catId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            delete("/api/cats/$catId")
                .header("Authorization", "Bearer $token2")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `create cat with optional fields null`() {
        val token = registerAndGetToken("cat-optional@example.com")
        val body = mapOf("name" to "Simple Cat", "age" to 1, "ageUnit" to "MONTHS")
        mockMvc.perform(
            post("/api/cats")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Simple Cat"))
            .andExpect(jsonPath("$.age").value(1))
            .andExpect(jsonPath("$.ageUnit").value("MONTHS"))
            .andExpect(jsonPath("$.breed").isEmpty)
            .andExpect(jsonPath("$.bio").isEmpty)
    }

    @Test
    fun `cat endpoints require authentication`() {
        mockMvc.perform(get("/api/cats"))
            .andExpect(status().isUnauthorized)
    }
}
