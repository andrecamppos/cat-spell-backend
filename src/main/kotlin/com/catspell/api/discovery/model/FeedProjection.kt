package com.catspell.api.discovery.model

import java.util.UUID

interface FeedProjection {
    fun getCatId(): UUID
    fun getName(): String
    fun getAge(): Int
    fun getAgeUnit(): String
    fun getBreed(): String?
    fun getBio(): String?
    fun getOwnerId(): UUID
    fun getOwnerDisplayName(): String
    fun getCatPhotoThumbnail(): String?
    fun getOwnerPhotoThumbnail(): String?
    fun getDistanceKm(): Int
}
