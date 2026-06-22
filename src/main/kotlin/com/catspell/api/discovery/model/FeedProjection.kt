package com.catspell.api.discovery.model

import java.util.UUID

interface FeedProjection {
    fun getType(): String
    fun getCatId(): UUID?
    fun getCatName(): String?
    fun getCatAge(): Int?
    fun getCatAgeUnit(): String?
    fun getCatBreed(): String?
    fun getCatBio(): String?
    fun getCatPhotoThumbnail(): String?
    fun getUserId(): UUID
    fun getDisplayName(): String
    fun getUserPhotoThumbnail(): String?
    fun getDistanceKm(): Int
}
