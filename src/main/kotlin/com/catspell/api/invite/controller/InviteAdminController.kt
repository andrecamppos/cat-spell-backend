package com.catspell.api.invite.controller

import com.catspell.api.common.security.AdminTokenGuard
import com.catspell.api.invite.model.IssueInviteRequest
import com.catspell.api.invite.model.IssueInviteResponse
import com.catspell.api.invite.service.InviteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Operator-only invite issuance (INV-02, D-01/D-02). There is no admin JWT/role (D-03). The access boundary is the
 * central AdminTokenFilter on every `/api/admin` path (D-11): it runs before Spring Security and MVC, so a caller
 * without the right `X-Admin-Token` gets the generic 401 before the body is read or validated (IN-09). The handler's
 * own [AdminTokenGuard.require] call stays as defense in depth. Both use the same deny-by-default, constant-time check.
 */
@RestController
@RequestMapping("/api/admin/invites")
class InviteAdminController(
    private val inviteService: InviteService,
    private val adminTokenGuard: AdminTokenGuard
) {

    @PostMapping
    fun issue(
        @RequestHeader(value = "X-Admin-Token", required = false) token: String?,
        @Valid @RequestBody body: IssueInviteRequest
    ): ResponseEntity<IssueInviteResponse> {
        adminTokenGuard.require(token)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(IssueInviteResponse(inviteService.create(body.referrerUserId)))
    }
}
