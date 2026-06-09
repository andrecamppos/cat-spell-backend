package com.catspell.api.common.exception

import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(ex.statusCode, ex.reason ?: "Error")
        return ResponseEntity.status(ex.statusCode).body(problem)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            ex.statusCode, "Validation failed"
        )
        problem.title = "Validation Error"
        problem.setProperty("violations", ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to it.defaultMessage)
        })
        return problem
    }
}
