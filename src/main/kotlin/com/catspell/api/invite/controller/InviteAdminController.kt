package com.catspell.api.invite.controller

import com.catspell.api.common.exception.AdminAuthException
import com.catspell.api.invite.model.IssueInviteRequest
import com.catspell.api.invite.model.IssueInviteResponse
import com.catspell.api.invite.service.InviteService
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

/**
 * Operator-only invite issuance (INV-02, D-01/D-02). The route is permitAll in SecurityConfig — there is no
 * admin JWT/role (D-03) — so [requireValidAdminToken] is the ENTIRE access-control boundary. It is
 * deny-by-default (a blank configured token rejects everything) and compares in constant time.
 */
@RestController
@RequestMapping("/api/admin/invites")
class InviteAdminController(
    private val inviteService: InviteService,
    @Value("\${app.invite.admin-token:}") private val adminToken: String
) {

    @PostMapping
    fun issue(
        @RequestHeader(value = "X-Admin-Token", required = false) token: String?,
        @Valid @RequestBody body: IssueInviteRequest
    ): ResponseEntity<IssueInviteResponse> {
        requireValidAdminToken(token)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(IssueInviteResponse(inviteService.create(body.referrerUserId)))
    }

    private fun requireValidAdminToken(provided: String?) {
        // Deny-by-default when unconfigured; constant-time compare — never ==/String.equals (Pitfall 4, D-03).
        if (adminToken.isBlank() ||
            provided == null ||
            !MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), adminToken.toByteArray(Charsets.UTF_8))
        ) {
            throw AdminAuthException()
        }
    }
}
