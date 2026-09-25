package com.catspell.api.moderation.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReportRepository : JpaRepository<Report, UUID> {
    fun countByReportedId(reportedId: UUID): Long
}
