package com.catspell.api.push.controller

import com.catspell.api.push.model.RegisterDeviceRequest
import com.catspell.api.push.service.DeviceTokenService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/devices")
class DeviceController(
    private val deviceTokenService: DeviceTokenService
) {

    @PostMapping
    fun register(@Valid @RequestBody request: RegisterDeviceRequest): ResponseEntity<Void> {
        deviceTokenService.register(extractUserId(), request)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{deviceId}")
    fun unregister(@PathVariable deviceId: String): ResponseEntity<Void> {
        deviceTokenService.unregister(extractUserId(), deviceId)
        return ResponseEntity.noContent().build()
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
