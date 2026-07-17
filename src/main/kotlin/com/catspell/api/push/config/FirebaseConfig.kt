package com.catspell.api.push.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.util.Base64

@Configuration
@ConditionalOnProperty(name = ["push.enabled"], havingValue = "true")
class FirebaseConfig(
    @Value("\${push.firebase.credentials-base64:}") private val credentialsBase64: String
) {

    @Bean
    fun firebaseApp(): FirebaseApp {
        check(credentialsBase64.isNotBlank()) {
            "push.enabled=true but FIREBASE_CREDENTIALS_BASE64 is missing"
        }
        val decoded = Base64.getDecoder().decode(credentialsBase64)
        val credentials = GoogleCredentials.fromStream(ByteArrayInputStream(decoded))
        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build()
        return if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
        } else {
            FirebaseApp.getInstance()
        }
    }

    @Bean
    fun firebaseMessaging(app: FirebaseApp): FirebaseMessaging = FirebaseMessaging.getInstance(app)
}
