package com.catspell.api.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-token-expiry}") private val accessTokenExpiry: Long
) {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret))
    }

    fun generateAccessToken(userId: UUID, email: String): String {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + accessTokenExpiry))
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    fun validateToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun extractUserId(token: String): UUID {
        val claims = validateToken(token)
        return UUID.fromString(claims.subject)
    }
}
