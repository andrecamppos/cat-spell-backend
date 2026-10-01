package com.catspell.api.common.exception

class DuplicateEmailException(message: String = "Email already registered") : RuntimeException(message)

class InvalidCredentialsException(message: String = "Invalid credentials") : RuntimeException(message)

class InvalidTokenException(message: String = "Invalid or expired token") : RuntimeException(message)

class EmailNotVerifiedException(message: String = "Email address not verified") : RuntimeException(message)

class UnderMinimumAgeException(message: String = "You must be at least 18 years old to sign up") : RuntimeException(message)

class InvalidCurrentPasswordException(message: String = "Current password is incorrect") : RuntimeException(message)

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

class SelfBlockException(message: String = "Cannot block yourself") : RuntimeException(message)

class SelfReportException(message: String = "Cannot report yourself") : RuntimeException(message)

// Single generic invite failure: invalid, consumed, and missing-when-gated codes all throw THIS one
// exception so the responses are byte-indistinguishable (D-10, INV-04 enumeration safety).
class InviteRequiredException(message: String = "A valid invite is required to sign up") : RuntimeException(message)

// Admin issuance shared-secret failure → generic 401 with no hint about token correctness (D-03).
class AdminAuthException(message: String = "Not authorized") : RuntimeException(message)
