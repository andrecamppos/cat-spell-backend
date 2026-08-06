package com.catspell.api.common.exception

class DuplicateEmailException(message: String = "Email already registered") : RuntimeException(message)

class InvalidCredentialsException(message: String = "Invalid credentials") : RuntimeException(message)

class InvalidTokenException(message: String = "Invalid or expired token") : RuntimeException(message)

class ResourceNotFoundException(message: String) : RuntimeException(message)

class PhotoLimitExceededException(message: String = "Maximum 6 photos allowed") : RuntimeException(message)

class InvalidPhotoTypeException(message: String = "Only JPEG and PNG photos are allowed") : RuntimeException(message)

class CatLimitExceededException(message: String = "Maximum 5 cats allowed") : RuntimeException(message)

class CatPhotoLimitExceededException(message: String = "Maximum 10 photos per cat allowed") : RuntimeException(message)

class ProfileIncompleteException(
    val missingFields: List<String> = emptyList(),
    message: String = "Complete your profile to use discovery"
) : RuntimeException(message)

class DuplicateSwipeException(message: String = "Already swiped on this profile") : RuntimeException(message)

class SelfSwipeException(message: String = "Cannot swipe on yourself") : RuntimeException(message)
