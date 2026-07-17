package com.catspell.api.push.model

import jakarta.validation.constraints.NotBlank

data class RegisterDeviceRequest(
    @field:NotBlank val token: String,
    @field:NotBlank val deviceId: String,
    val platform: Platform
)
