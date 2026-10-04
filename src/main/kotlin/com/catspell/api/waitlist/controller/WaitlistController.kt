package com.catspell.api.waitlist.controller

import com.catspell.api.auth.model.GenericMessageResponse
import com.catspell.api.waitlist.model.JoinWaitlistRequest
import com.catspell.api.waitlist.service.WaitlistService
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/waitlist")
class WaitlistController(
    private val waitlistService: WaitlistService,
    @Value("\${app.waitlist.confirm-success-url:http://localhost:3000/waitlist/confirmed}")
    confirmSuccessUrl: String,
    @Value("\${app.waitlist.confirm-error-url:http://localhost:3000/waitlist/link-invalid}")
    confirmErrorUrl: String
) {

    // Parsed once here: a malformed value throws IllegalArgumentException while the bean is built (startup),
    // never after a user's token has already been claimed (IN-07).
    private val successUri: URI = URI.create(confirmSuccessUrl)
    private val errorUri: URI = URI.create(confirmErrorUrl)

    /**
     * Public, unauthenticated join. Every accepted request — new, pending, confirmed, invited or throttled —
     * gets the identical 202 body, so the response is not a membership oracle (D-04).
     */
    @SecurityRequirements
    @PostMapping
    fun join(@Valid @RequestBody request: JoinWaitlistRequest): ResponseEntity<GenericMessageResponse> {
        waitlistService.join(request.email)
        return ResponseEntity.accepted().body(GenericMessageResponse(WAITLIST_JOIN_MESSAGE))
    }

    /**
     * Public double opt-in link target (WAIT-02, D-01). Claims the token once and 302-redirects to the configured web
     * success URL; every failure (blank, missing, unknown, expired, reused, rotated away) goes to the configured error
     * URL, never a 400 or a JSON body. The Location comes only from those two config values, so no request input is
     * echoed into it (open-redirect guard). `no-referrer` and `no-store` keep the token out of Referer headers and caches.
     * Both URLs are parsed once at startup (IN-07), so a misconfigured value fails the deploy instead of turning a
     * just-claimed token into a server error; the handler only picks one of the pre-parsed URIs.
     */
    @SecurityRequirements
    @GetMapping("/confirm")
    fun confirm(@RequestParam(name = "token", required = false) token: String?): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(if (waitlistService.confirm(token)) successUri else errorUri)
            .header("Referrer-Policy", "no-referrer")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .build()
    }

    companion object {
        const val WAITLIST_JOIN_MESSAGE = "Check your inbox to confirm your spot on the Cat Spell waitlist."
    }
}
