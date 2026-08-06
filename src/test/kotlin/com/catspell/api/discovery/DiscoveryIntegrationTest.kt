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
class DiscoveryIntegrationTest : BaseIntegrationTest() {

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
        bio: String = "Hello world",
        dateOfBirth: String = "2000-01-15",
        gender: String = "MALE",
        genderPreference: String = "EVERYONE",
        ageMin: Int = 18,
        ageMax: Int = 50,
        maxDistanceKm: Int = 100
    ) {
        val body = mapOf(
            "displayName" to displayName,
            "bio" to bio,
            "dateOfBirth" to dateOfBirth,
            "gender" to gender,
            "genderPreference" to genderPreference,
            "ageMin" to ageMin,
            "ageMax" to ageMax,
            "maxDistanceKm" to maxDistanceKm
        )
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated)
    }

    private fun setLocation(token: String, lat: Double, lng: Double) {
        val body = mapOf("latitude" to lat, "longitude" to lng)
        mockMvc.perform(
            put("/api/profile/location")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk)
    }

    private fun createCat(token: String, name: String = "TestCat"): String {
        val body = mapOf("name" to name, "age" to 2, "ageUnit" to "YEARS", "breed" to "Persian", "bio" to "A cute cat")
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

    private fun setupCompleteUser(
        email: String,
        displayName: String = "User",
        gender: String = "MALE",
        genderPreference: String = "EVERYONE",
        dateOfBirth: String = "2000-01-15",
        lat: Double = 40.7128,
        lng: Double = -74.0060,
        ageMin: Int = 18,
        ageMax: Int = 50,
        maxDistanceKm: Int = 100,
        catName: String = "TestCat"
    ): Triple<String, String, String> {
        val token = registerAndGetToken(email)
        createProfile(token, displayName, "Bio for $displayName", dateOfBirth, gender, genderPreference, ageMin, ageMax, maxDistanceKm)
        setLocation(token, lat, lng)
        addUserPhoto(token)
        val catId = createCat(token, catName)
        addCatPhoto(token, catId)
        return Triple(token, catId, email)
    }

    private fun setupCatlessUser(
        email: String,
        displayName: String = "User",
        gender: String = "MALE",
        genderPreference: String = "EVERYONE",
        dateOfBirth: String = "2000-01-15",
        lat: Double = 40.7128,
        lng: Double = -74.0060,
        ageMin: Int = 18,
        ageMax: Int = 50,
        maxDistanceKm: Int = 100
    ): Pair<String, String> {
        val token = registerAndGetToken(email)
        createProfile(token, displayName, "Bio for $displayName", dateOfBirth, gender, genderPreference, ageMin, ageMax, maxDistanceKm)
        setLocation(token, lat, lng)
        addUserPhoto(token)
        return Pair(token, email)
    }

    private fun extractUserId(token: String): String {
        val result = mockMvc.perform(
            get("/api/auth/me")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["userId"].asText()
    }

    @Test
    fun `feed returns cat profiles with cat-first data`() {
        val (tokenA, _, _) = setupCompleteUser("disc-catfirst-a@example.com", "Alice", "FEMALE", lat = -33.8688, lng = 151.2093)
        setupCompleteUser("disc-catfirst-b@example.com", "Bob", "MALE", catName = "Whiskers", lat = -33.8690, lng = 151.2095)

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards").isArray)
            .andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        val whiskers = (0 until cards.size()).firstOrNull { cards[it]["catName"].asText() == "Whiskers" }
        assert(whiskers != null) { "Whiskers should appear in feed" }
        val w = cards[whiskers!!]
        assert(w["catId"] != null) { "catId should exist" }
        assert(w["catAge"].asInt() == 2) { "catAge should be 2" }
        assert(w["userId"] != null) { "userId should exist" }
        assert(w["displayName"].asText() == "Bob") { "displayName should be Bob" }
        assert(w["distanceKm"].isInt) { "distanceKm should be an integer" }
    }

    @Test
    fun `feed excludes own cats`() {
        val (tokenA, _, _) = setupCompleteUser("disc-own-a@example.com", "OwnA", "FEMALE", catName = "OwnACatUnique")
        setupCompleteUser("disc-own-b@example.com", "OwnB", "MALE", catName = "OtherCat")

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        val catNames = (0 until cards.size()).map { cards[it]["catName"].asText() }
        assert("OwnACatUnique" !in catNames) { "Own cat should not appear in feed" }
    }

    @Test
    fun `feed filters by distance`() {
        // User A in New York
        val (tokenA, _, _) = setupCompleteUser("disc-dist-a@example.com", "DistA", "FEMALE", lat = 40.7128, lng = -74.0060, maxDistanceKm = 10)
        // User B in New York (close)
        setupCompleteUser("disc-dist-b@example.com", "NearUser", "MALE", lat = 40.7200, lng = -74.0000, catName = "NearCat")
        // User C in Los Angeles (far away)
        setupCompleteUser("disc-dist-c@example.com", "FarUser", "MALE", lat = 34.0522, lng = -118.2437, catName = "FarCat")

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val catNames = (0 until json["cards"].size()).map { json["cards"][it]["catName"].asText() }
        assert("NearCat" in catNames) { "Near cat should appear in feed" }
        assert("FarCat" !in catNames) { "Far cat should not appear in feed" }
    }

    @Test
    fun `feed excludes already-swiped cats`() {
        val (tokenA, _, _) = setupCompleteUser("disc-swiped-a@example.com", "SwipedA", "FEMALE")
        val (_, catIdB, _) = setupCompleteUser("disc-swiped-b@example.com", "SwipedB", "MALE", catName = "SwipedCat")

        // Swipe on the cat
        val swipeBody = mapOf("catId" to catIdB, "action" to "PASS")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(swipeBody))
        ).andExpect(status().isOk)

        // Feed should not contain the swiped cat
        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val catNames = (0 until json["cards"].size()).map { json["cards"][it]["catName"].asText() }
        assert("SwipedCat" !in catNames) { "Swiped cat should not appear in feed" }
    }

    @Test
    fun `feed respects bidirectional gender preference`() {
        // User A is FEMALE seeking MALE only
        val (tokenA, _, _) = setupCompleteUser("disc-gender-a@example.com", "GenderA", "FEMALE", genderPreference = "MALE")
        // User B is MALE seeking FEMALE — mutual match
        setupCompleteUser("disc-gender-b@example.com", "MatchGender", "MALE", genderPreference = "FEMALE", catName = "MatchCat")
        // User C is FEMALE seeking MALE — A seeks MALE, C is FEMALE so A shouldn't see C
        setupCompleteUser("disc-gender-c@example.com", "NoMatchGender", "FEMALE", genderPreference = "MALE", catName = "NoMatchCat")

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val catNames = (0 until json["cards"].size()).map { json["cards"][it]["catName"].asText() }
        assert("MatchCat" in catNames) { "Gender-matching cat should appear" }
        assert("NoMatchCat" !in catNames) { "Gender-mismatched cat should not appear" }
    }

    @Test
    fun `feed respects bidirectional age range`() {
        // User A is 25 yo, seeking 20-30
        val (tokenA, _, _) = setupCompleteUser("disc-age-a@example.com", "AgeA", "FEMALE", dateOfBirth = "2000-01-15", ageMin = 20, ageMax = 30)
        // User B is 25 yo, seeking 20-30 — mutual match
        setupCompleteUser("disc-age-b@example.com", "AgeMatch", "MALE", dateOfBirth = "2000-06-15", ageMin = 20, ageMax = 30, catName = "AgeMatchCat")
        // User C is 45 yo, seeking 40-50 — C doesn't want A's age range
        setupCompleteUser("disc-age-c@example.com", "AgeNoMatch", "MALE", dateOfBirth = "1980-01-15", ageMin = 40, ageMax = 50, catName = "AgeNoMatchCat")

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val catNames = (0 until json["cards"].size()).map { json["cards"][it]["catName"].asText() }
        assert("AgeMatchCat" in catNames) { "Age-matching cat should appear" }
        assert("AgeNoMatchCat" !in catNames) { "Age-mismatched cat should not appear" }
    }

    @Test
    fun `feed returns 400 when requester has no location`() {
        val token = registerAndGetToken("disc-noloc@example.com")
        createProfile(token)
        addUserPhoto(token)

        mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.missingFields[?(@ == 'location')]").exists())
    }

    @Test
    fun `swipe returns 400 when requester profile is incomplete`() {
        // Target is a fully set-up user; the swiper has a profile but never set a location.
        val (_, catIdA, _) = setupCompleteUser("disc-swipe-incomplete-target@example.com", "SwipeTarget", "FEMALE", catName = "SwipeCat")

        val token = registerAndGetToken("disc-swipe-incomplete@example.com")
        createProfile(token)
        addUserPhoto(token)

        val body = mapOf("catId" to catIdA, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.missingFields[?(@ == 'location')]").exists())
    }

    @Test
    fun `feed shows cats from profiles without bio`() {
        val (tokenA, _, _) = setupCompleteUser("disc-complete-a@example.com", "CompleteA", "FEMALE")
        // User B has no bio — bio is optional for discovery
        val tokenB = registerAndGetToken("disc-nobio-b@example.com")
        val noBioBody = mapOf(
            "displayName" to "NoBio",
            "dateOfBirth" to "2000-01-15",
            "gender" to "MALE",
            "genderPreference" to "EVERYONE",
            "ageMin" to 18,
            "ageMax" to 50,
            "maxDistanceKm" to 100
        )
        mockMvc.perform(
            post("/api/profile")
                .header("Authorization", "Bearer $tokenB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noBioBody))
        ).andExpect(status().isCreated)
        setLocation(tokenB, 40.7128, -74.0060)
        addUserPhoto(tokenB)
        val catId = createCat(tokenB, "NoBioCat")
        addCatPhoto(tokenB, catId)

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val catNames = (0 until json["cards"].size()).map { json["cards"][it]["catName"].asText() }
        assert("NoBioCat" in catNames) { "Cat from profile without bio should appear in feed" }
    }

    @Test
    fun `feed only shows cats with active photo`() {
        val (tokenA, _, _) = setupCompleteUser("disc-nophoto-a@example.com", "NoPhotoA", "FEMALE")
        // User B with complete profile but cat has no photo
        val tokenB = registerAndGetToken("disc-nophoto-b@example.com")
        createProfile(tokenB, "NoPhotoCatUser", "Bio", "2000-01-15", "MALE", "EVERYONE")
        setLocation(tokenB, 40.7128, -74.0060)
        addUserPhoto(tokenB)
        createCat(tokenB, "NoPhotoCat")  // No photo added to cat

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val catNames = (0 until json["cards"].size()).map { json["cards"][it]["catName"].asText() }
        assert("NoPhotoCat" !in catNames) { "Cat without photo should not appear" }
    }

    @Test
    fun `feed returns distance as rounded integer km`() {
        val (tokenA, _, _) = setupCompleteUser("disc-distkm-a@example.com", "DistKmA", "FEMALE", lat = 40.7128, lng = -74.0060)
        setupCompleteUser("disc-distkm-b@example.com", "DistKmB", "MALE", lat = 40.7200, lng = -74.0000, catName = "DistCat")

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        if (json["cards"].size() > 0) {
            val distanceKm = json["cards"][0]["distanceKm"]
            assert(distanceKm.isInt) { "distanceKm should be an integer" }
        }
    }

    @Test
    fun `feed pagination returns different cats on second page using cursor`() {
        val (tokenA, _, _) = setupCompleteUser("disc-page-a@example.com", "PageA", "FEMALE", lat = 40.7128, lng = -74.0060)
        // Create multiple users with cats to fill two pages
        for (i in 1..5) {
            setupCompleteUser("disc-page-other$i@example.com", "PageUser$i", "MALE", lat = 40.7128 + (i * 0.001), lng = -74.0060, catName = "PageCat$i")
        }

        // First page with small pageSize
        val firstResult = mockMvc.perform(
            get("/api/discovery/feed")
                .param("pageSize", "2")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val firstJson = objectMapper.readTree(firstResult.response.contentAsString)
        val firstCats = firstJson["cards"]
        assert(firstCats.size() == 2) { "First page should have 2 cats, got ${firstCats.size()}" }

        val cursor = firstJson["cursor"]
        assert(cursor != null && !cursor.isNull) { "Cursor should be present for pagination" }
        assert(cursor["hasMore"].asBoolean()) { "hasMore should be true when more cats exist" }

        // Second page using cursor — service expects comma-separated "seed,offset" Base64 encoded
        val cursorString = "${cursor["seed"].asDouble()},${cursor["offset"].asInt()}"
        val encodedCursor = java.util.Base64.getEncoder().encodeToString(cursorString.toByteArray())
        val secondResult = mockMvc.perform(
            get("/api/discovery/feed")
                .param("pageSize", "2")
                .param("cursor", encodedCursor)
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val secondJson = objectMapper.readTree(secondResult.response.contentAsString)
        val secondCats = secondJson["cards"]
        assert(secondCats.size() > 0) { "Second page should have cats" }

        // Cats on second page should differ from first page
        val firstIds = (0 until firstCats.size()).map { firstCats[it]["catId"].asText() }.toSet()
        val secondIds = (0 until secondCats.size()).map { secondCats[it]["catId"].asText() }.toSet()
        assert(firstIds.intersect(secondIds).isEmpty()) { "Second page should not contain same cats as first page" }
    }

    @Test
    fun `feed requires authentication`() {
        mockMvc.perform(get("/api/discovery/feed"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `feed returns empty list when no eligible cats exist`() {
        // User with complete profile but no other users in the system nearby
        val token = registerAndGetToken("disc-empty-feed@example.com")
        createProfile(token, "LonelyUser", "Bio", "2000-01-15", "FEMALE", "MALE")
        setLocation(token, 0.0, 0.0) // Remote location with no other users
        addUserPhoto(token)
        val catId = createCat(token, "LonelyCat")
        addCatPhoto(token, catId)

        mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards").isArray)
            .andExpect(jsonPath("$.cards").isEmpty)
    }

    @Test
    fun `feed pagination with pageSize larger than available cats returns all and hasMore false`() {
        val (tokenA, _, _) = setupCompleteUser("disc-bigpage-a@example.com", "BigPageA", "FEMALE", lat = 51.5074, lng = -0.1278)
        setupCompleteUser("disc-bigpage-b@example.com", "BigPageB", "MALE", lat = 51.5080, lng = -0.1280, catName = "BigPageCat")

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .param("pageSize", "100")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        assert(cards.size() >= 1) { "Should have at least 1 cat" }
        val cursor = json["cursor"]
        if (cursor != null && !cursor.isNull) {
            assert(!cursor["hasMore"].asBoolean()) { "hasMore should be false when all cats fit in one page" }
        }
    }

    @Test
    fun `human card appears in mixed feed for catless user`() {
        val (tokenA, _, _) = setupCompleteUser("disc-human-a@example.com", "Alice", "FEMALE")
        setupCatlessUser("disc-human-b@example.com", "Bob", "MALE")

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        val bob = (0 until cards.size()).firstOrNull { cards[it]["displayName"].asText() == "Bob" }
        assert(bob != null) { "Bob (catless) should appear in feed" }
        val b = cards[bob!!]
        assert(b["type"].asText() == "HUMAN") { "type should be HUMAN" }
        assert(b["catId"].isNull) { "catId should be null for HUMAN card" }
        assert(b["catName"].isNull) { "catName should be null for HUMAN card" }
        assert(b["userId"] != null && !b["userId"].isNull) { "userId should exist" }
        assert(b["displayName"].asText() == "Bob") { "displayName should be Bob" }
    }

    @Test
    fun `cat card appears with type discriminator`() {
        val (tokenA, _) = setupCatlessUser("disc-cattype-a@example.com", "CatTypeA", "FEMALE")
        setupCompleteUser("disc-cattype-b@example.com", "CatTypeB", "MALE", catName = "TypedCat")

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        val typed = (0 until cards.size()).firstOrNull { cards[it]["catName"]?.asText() == "TypedCat" }
        assert(typed != null) { "TypedCat should appear in feed" }
        val c = cards[typed!!]
        assert(c["type"].asText() == "CAT") { "type should be CAT" }
        assert(!c["catId"].isNull) { "catId should not be null for CAT card" }
        assert(c["catName"].asText() == "TypedCat") { "catName should be TypedCat" }
    }

    @Test
    fun `feed mixes both CAT and HUMAN cards`() {
        val (tokenA, _, _) = setupCompleteUser("disc-mix-a@example.com", "MixViewer", "FEMALE", lat = 48.8566, lng = 2.3522)
        setupCompleteUser("disc-mix-b@example.com", "CatOwner", "MALE", lat = 48.8570, lng = 2.3530, catName = "MixCat")
        setupCatlessUser("disc-mix-c@example.com", "CatlessUser", "MALE", lat = 48.8575, lng = 2.3535)

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        val types = (0 until cards.size()).map { cards[it]["type"].asText() }.toSet()
        assert("CAT" in types) { "Feed should contain CAT cards" }
        assert("HUMAN" in types) { "Feed should contain HUMAN cards" }
    }

    @Test
    fun `one card per user for multi-cat owner`() {
        val (tokenA, _, _) = setupCompleteUser("disc-multi-a@example.com", "MultiViewer", "FEMALE", lat = 35.6762, lng = 139.6503)
        val (tokenB, _, _) = setupCompleteUser("disc-multi-b@example.com", "MultiCatOwner", "MALE", lat = 35.6770, lng = 139.6510, catName = "Cat1")
        val cat2Id = createCat(tokenB, "Cat2")
        addCatPhoto(tokenB, cat2Id)

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        val userIdB = extractUserId(tokenB)
        val cardsForB = (0 until cards.size()).filter { cards[it]["userId"].asText() == userIdB }
        assert(cardsForB.size == 1) { "Should have exactly 1 card for multi-cat owner, got ${cardsForB.size}" }
    }

    @Test
    fun `swipe on human card with targetUserId`() {
        val (tokenA, _, _) = setupCompleteUser("disc-hswipe-a@example.com", "HSwipeA", "FEMALE")
        val (tokenB, _) = setupCatlessUser("disc-hswipe-b@example.com", "HSwipeB", "MALE")
        val userIdB = extractUserId(tokenB)

        val body = mapOf("targetUserId" to userIdB, "action" to "LIKE")
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
    fun `swipe rejects both catId and targetUserId`() {
        val (tokenA, catIdA, _) = setupCompleteUser("disc-both-a@example.com", "BothA", "FEMALE")
        val (tokenB, _) = setupCatlessUser("disc-both-b@example.com", "BothB", "MALE")
        val userIdB = extractUserId(tokenB)

        val body = mapOf("catId" to catIdA, "targetUserId" to userIdB, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `swipe rejects neither catId nor targetUserId`() {
        val (tokenA, _, _) = setupCompleteUser("disc-neither-a@example.com", "NeitherA", "FEMALE")

        val body = mapOf("action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `human card excluded after swipe`() {
        val (tokenA, _, _) = setupCompleteUser("disc-hexcl-a@example.com", "HExclA", "FEMALE")
        val (tokenB, _) = setupCatlessUser("disc-hexcl-b@example.com", "HExclB", "MALE")
        val userIdB = extractUserId(tokenB)

        val body = mapOf("targetUserId" to userIdB, "action" to "PASS")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk)

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        val bCards = (0 until cards.size()).filter { cards[it]["userId"].asText() == userIdB }
        assert(bCards.isEmpty()) { "Swiped human should not appear in feed" }
    }

    @Test
    fun `cat card excluded by owner after swipe`() {
        val (tokenA, _, _) = setupCompleteUser("disc-cexcl-a@example.com", "CExclA", "FEMALE")
        val (tokenB, catIdB, _) = setupCompleteUser("disc-cexcl-b@example.com", "CExclB", "MALE", catName = "ExclCat")

        val body = mapOf("catId" to catIdB, "action" to "PASS")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk)

        val result = mockMvc.perform(
            get("/api/discovery/feed")
                .header("Authorization", "Bearer $tokenA")
        ).andExpect(status().isOk).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        val cards = json["cards"]
        val userIdB = extractUserId(tokenB)
        val bCards = (0 until cards.size()).filter { cards[it]["userId"].asText() == userIdB }
        assert(bCards.isEmpty()) { "Owner should be excluded from feed after swiping on their cat" }
    }

    @Test
    fun `mutual match between catless users`() {
        val (tokenA, _) = setupCatlessUser("disc-hmatch-a@example.com", "HMatchA", "FEMALE")
        val (tokenB, _) = setupCatlessUser("disc-hmatch-b@example.com", "HMatchB", "MALE")
        val userIdA = extractUserId(tokenA)
        val userIdB = extractUserId(tokenB)

        val bodyAB = mapOf("targetUserId" to userIdB, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyAB))
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.matched").value(false))

        val bodyBA = mapOf("targetUserId" to userIdA, "action" to "LIKE")
        val matchResult = mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyBA))
        ).andExpect(status().isOk).andReturn()

        val matchJson = objectMapper.readTree(matchResult.response.contentAsString)
        assert(matchJson["matched"].asBoolean()) { "Second LIKE should create match" }
        assert(matchJson["matchId"] != null && !matchJson["matchId"].isNull) { "matchId should be present" }
    }

    @Test
    fun `cross-type mutual match cat owner and catless user`() {
        val (tokenA, catIdA, _) = setupCompleteUser("disc-xmatch-a@example.com", "XMatchA", "FEMALE", catName = "XCatA")
        val (tokenB, _) = setupCatlessUser("disc-xmatch-b@example.com", "XMatchB", "MALE")
        val userIdA = extractUserId(tokenA)
        val userIdB = extractUserId(tokenB)

        val bodyAB = mapOf("targetUserId" to userIdB, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyAB))
        ).andExpect(status().isOk)

        val bodyBA = mapOf("catId" to catIdA, "action" to "LIKE")
        val matchResult = mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyBA))
        ).andExpect(status().isOk).andReturn()

        val matchJson = objectMapper.readTree(matchResult.response.contentAsString)
        assert(matchJson["matched"].asBoolean()) { "Cross-type mutual LIKE should create match" }
        assert(matchJson["matchId"] != null && !matchJson["matchId"].isNull) { "matchId should be present" }
    }

    @Test
    fun `human card detail endpoint returns profile`() {
        val (tokenA, _, _) = setupCompleteUser("disc-hdetail-a@example.com", "HDetailA", "FEMALE")
        val (tokenB, _) = setupCatlessUser("disc-hdetail-b@example.com", "HDetailB", "MALE")
        val userIdB = extractUserId(tokenB)

        mockMvc.perform(
            get("/api/discovery/users/$userIdB/profile")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userIdB))
            .andExpect(jsonPath("$.displayName").value("HDetailB"))
            .andExpect(jsonPath("$.bio").exists())
            .andExpect(jsonPath("$.age").isNumber)
            .andExpect(jsonPath("$.gender").value("MALE"))
            .andExpect(jsonPath("$.photos").isArray)
            .andExpect(jsonPath("$.cats").isEmpty)
    }

    @Test
    fun `human card detail requires authentication`() {
        mockMvc.perform(
            get("/api/discovery/users/00000000-0000-0000-0000-000000000000/profile")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `self swipe on human card returns 400`() {
        val (tokenA, _) = setupCatlessUser("disc-hself-a@example.com", "HSelfA", "FEMALE")
        val userIdA = extractUserId(tokenA)

        val body = mapOf("targetUserId" to userIdA, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `duplicate human swipe returns 409`() {
        val (tokenA, _, _) = setupCompleteUser("disc-hdup-a@example.com", "HDupA", "FEMALE")
        val (tokenB, _) = setupCatlessUser("disc-hdup-b@example.com", "HDupB", "MALE")
        val userIdB = extractUserId(tokenB)

        val body = mapOf("targetUserId" to userIdB, "action" to "LIKE")
        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/discovery/swipe")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isConflict)
    }
}
