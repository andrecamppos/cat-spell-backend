package com.catspell.api.common.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(title = "Cat Spell API", version = "1.0", description = "Cat-first dating app backend API"),
    security = [SecurityRequirement(name = "bearerAuth")]
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
class OpenApiConfig {

    @Bean
    fun authApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("auth")
        .pathsToMatch("/api/auth/**")
        .build()

    @Bean
    fun userApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("user")
        .pathsToMatch("/api/profile/**", "/api/photos/**")
        .build()

    @Bean
    fun catsApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("cats")
        .pathsToMatch("/api/cats/**")
        .build()

    @Bean
    fun discoveryApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("discovery")
        .pathsToMatch("/api/discovery/**", "/api/swipe/**", "/api/matches/**")
        .build()

    @Bean
    fun chatApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("chat")
        .pathsToMatch("/api/chat/**", "/api/conversations/**")
        .build()
}
