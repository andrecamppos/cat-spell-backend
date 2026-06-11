package com.catspell.api.common.exception

class DuplicateEmailException(message: String = "Email already registered") : RuntimeException(message)

class InvalidCredentialsException(message: String = "Invalid credentials") : RuntimeException(message)

class InvalidTokenException(message: String = "Invalid or expired token") : RuntimeException(message)

class ResourceNotFoundException(message: String) : RuntimeException(message)

class PhotoLimitExceededException(message: String = "Maximum 6 photos allowed") : RuntimeException(message)

class InvalidPhotoTypeException(message: String = "Only JPEG and PNG photos are allowed") : RuntimeException(message)
