package com.catspell.api.profile.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

@Service
class StorageService(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    @Value("\${storage.s3.bucket}") private val bucket: String
) {
    private val log = LoggerFactory.getLogger(StorageService::class.java)

    @PostConstruct
    fun createBucketIfNotExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
            log.info("S3 bucket '{}' already exists", bucket)
        } catch (e: NoSuchBucketException) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
            log.info("Created S3 bucket '{}'", bucket)
        } catch (e: Exception) {
            log.warn("Could not check/create S3 bucket '{}': {}", bucket, e.message)
        }
    }

    fun generatePresignedUploadUrl(s3Key: String, contentType: String, maxSizeBytes: Long): String {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .putObjectRequest(putObjectRequest)
            .build()

        return s3Presigner.presignPutObject(presignRequest).url().toString()
    }

    fun objectExists(s3Key: String): Boolean {
        return try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(s3Key).build())
            true
        } catch (e: NoSuchKeyException) {
            false
        }
    }

    fun deleteObject(s3Key: String) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(s3Key).build())
    }

    fun getObject(s3Key: String): ByteArray {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(s3Key).build()).asByteArray()
    }

    fun putObject(s3Key: String, data: ByteArray, contentType: String) {
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(s3Key).contentType(contentType).build(),
            RequestBody.fromBytes(data)
        )
    }
}
