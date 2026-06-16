package com.catspell.api.common.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest

@Component
class S3HealthIndicator(
    private val s3Client: S3Client,
    @Value("\${storage.s3.bucket}") private val bucketName: String
) : HealthIndicator {

    override fun health(): Health {
        return try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())
            Health.up().withDetail("bucket", bucketName).build()
        } catch (e: Exception) {
            Health.down(e).withDetail("bucket", bucketName).build()
        }
    }
}
