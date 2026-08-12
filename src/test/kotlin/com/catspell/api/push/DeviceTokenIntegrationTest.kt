package com.catspell.api.push

import com.catspell.api.BaseIntegrationTest
import com.catspell.api.auth.model.UserRepository
import com.catspell.api.push.model.DeviceTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class DeviceTokenIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var deviceTokenRepository: DeviceTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    private fun registerAndGetToken(email: String, ip: String): String {
        val body = mapOf("email" to email, "password" to "password123")
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", ip)
        )
        markEmailVerified(email)
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-Forwarded-For", ip)
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return json["accessToken"].asText()
    }

    private fun userIdFor(email: String): UUID =
        userRepository.findByEmail(email)!!.id!!

    private fun registerDevice(token: String, deviceId: String, fcmToken: String, platform: String) =
        mockMvc.perform(
            post("/api/devices")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("token" to fcmToken, "deviceId" to deviceId, "platform" to platform)
                    )
                )
        )

    @Test
    fun `register creates active row`() {
        val email = "device-create@example.com"
        val token = registerAndGetToken(email, "10.2.0.1")

        registerDevice(token, "d1", "fcm-token-1", "ANDROID")
            .andExpect(status().isNoContent)

        val userId = userIdFor(email)
        val rows = deviceTokenRepository.findAll().filter { it.userId == userId }
        assertEquals(1, rows.size)
        assertTrue(rows[0].active)
        assertEquals("fcm-token-1", rows[0].token)
    }

    @Test
    fun `re-register same device updates token and reactivates`() {
        val email = "device-reregister@example.com"
        val token = registerAndGetToken(email, "10.2.0.2")

        registerDevice(token, "d1", "fcm-token-old", "ANDROID").andExpect(status().isNoContent)
        mockMvc.perform(
            delete("/api/devices/d1").header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)
        registerDevice(token, "d1", "fcm-token-new", "IOS").andExpect(status().isNoContent)

        val userId = userIdFor(email)
        val rows = deviceTokenRepository.findAll().filter { it.userId == userId }
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals("fcm-token-new", row.token)
        assertTrue(row.active)
        assertNull(row.deactivatedAt)
    }

    @Test
    fun `unregister soft-deactivates only named device`() {
        val email = "device-multi@example.com"
        val token = registerAndGetToken(email, "10.2.0.3")

        registerDevice(token, "d1", "fcm-1", "ANDROID").andExpect(status().isNoContent)
        registerDevice(token, "d2", "fcm-2", "IOS").andExpect(status().isNoContent)

        mockMvc.perform(
            delete("/api/devices/d1").header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        val userId = userIdFor(email)
        val d1 = deviceTokenRepository.findByUserIdAndDeviceId(userId, "d1")!!
        val d2 = deviceTokenRepository.findByUserIdAndDeviceId(userId, "d2")!!
        assertFalse(d1.active)
        assertNotNull(d1.deactivatedAt)
        assertTrue(d2.active)
    }

    @Test
    fun `IDOR user B cannot deactivate user A device`() {
        val emailA = "device-idor-a@example.com"
        val emailB = "device-idor-b@example.com"
        val tokenA = registerAndGetToken(emailA, "10.2.0.4")
        val tokenB = registerAndGetToken(emailB, "10.2.0.5")

        registerDevice(tokenA, "d1", "fcm-a", "ANDROID").andExpect(status().isNoContent)

        mockMvc.perform(
            delete("/api/devices/d1").header("Authorization", "Bearer $tokenB")
        ).andExpect(status().isNoContent)

        val userIdA = userIdFor(emailA)
        val aRow = deviceTokenRepository.findByUserIdAndDeviceId(userIdA, "d1")!!
        assertTrue(aRow.active)
    }

    @Test
    fun `unauthenticated request rejected`() {
        mockMvc.perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("token" to "fcm", "deviceId" to "d1", "platform" to "ANDROID")
                    )
                )
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `unknown platform value rejected`() {
        val token = registerAndGetToken("device-badplatform@example.com", "10.2.0.6")

        registerDevice(token, "d1", "fcm", "WEB")
            .andExpect(status().isBadRequest)
    }
}
