package com.catspell.api.waitlist.controller

import com.catspell.api.common.security.AdminTokenGuard
import com.catspell.api.waitlist.model.ConvertWaitlistResponse
import com.catspell.api.waitlist.model.WaitlistEntryResponse
import com.catspell.api.waitlist.service.WaitlistService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Operator-only waitlist view (WAIT-04, D-09). There is no admin JWT/role. The access boundary is the central
 * AdminTokenFilter on every `/api/admin` path (D-11): it runs before Spring Security and MVC, so a caller without the
 * right `X-Admin-Token` gets the generic 401 before any parameter is converted or validated (IN-09) and before any
 * entry is read. Each handler still calls [AdminTokenGuard.require] as its first statement, as defense in depth.
 */
@RestController
@RequestMapping("/api/admin/waitlist")
class WaitlistAdminController(
    private val waitlistService: WaitlistService,
    private val adminTokenGuard: AdminTokenGuard
) {

    @GetMapping
    fun list(
        @RequestHeader(value = "X-Admin-Token", required = false) token: String?,
        @RequestParam(defaultValue = "confirmed") status: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<WaitlistEntryResponse>> {
        adminTokenGuard.require(token)
        return ResponseEntity.ok(waitlistService.listByStatus(status, limit))
    }

    /**
     * Convert one CONFIRMED entry into an emailed, organic invite (D-09/D-10) and return the raw code once. The guard
     * runs before any lookup, so unauthenticated callers get the same 401 for existing and random ids and cannot
     * probe which entries exist. With the correct token, a malformed UUID fails path conversion with 400, which
     * reveals no data; without it, AdminTokenFilter answers 401 first.
     */
    @PostMapping("/{id}/invite")
    fun convert(
        @RequestHeader(value = "X-Admin-Token", required = false) token: String?,
        @PathVariable id: UUID
    ): ResponseEntity<ConvertWaitlistResponse> {
        adminTokenGuard.require(token)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ConvertWaitlistResponse(id, waitlistService.convertToInvite(id)))
    }
}
