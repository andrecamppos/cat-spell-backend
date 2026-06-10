package com.catspell.api.common.exception

class DuplicateEmailException(message: String = "Email already registered") : RuntimeException(message)

class InvalidCredentialsException(message: String = "Invalid credentials") : RuntimeException(message)

class InvalidTokenException(message: String = "Invalid or expired token") : RuntimeException(message)

class ResourceNotFoundException(message: String) : RuntimeException(message)
