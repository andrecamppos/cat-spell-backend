package com.catspell.api.moderation.controller

import com.catspell.api.moderation.model.ReportRequest
import com.catspell.api.moderation.model.ReportResponse
import com.catspell.api.moderation.service.ReportService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/reports")
class ReportController(
    private val reportService: ReportService
) {

    @PostMapping
    fun report(@Valid @RequestBody request: ReportRequest): ResponseEntity<ReportResponse> {
        val reportId = reportService.report(
            reporterId = extractUserId(),
            reportedId = request.reportedUserId,
            category = request.category,
            details = request.details,
            alsoBlock = request.alsoBlock
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse(reportId))
    }

    private fun extractUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication!!
        return UUID.fromString(authentication.principal as String)
    }
}
