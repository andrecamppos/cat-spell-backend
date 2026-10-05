package com.catspell.api.waitlist

import com.catspell.api.waitlist.controller.WaitlistController
import com.catspell.api.waitlist.service.WaitlistService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.net.URI

private const val SUCCESS_URL = "http://localhost:3000/ok"
private const val ERROR_URL = "http://localhost:3000/e"
private const val MALFORMED_URL = "http://bad host/x"

/** Redirect URLs are parsed once at construction, so a malformed value fails startup (first-review IN-07). */
class WaitlistControllerUrlTest {

    @Test
    fun `a malformed success URL fails construction`() {
        assertThrows<IllegalArgumentException> {
            WaitlistController(mockk(relaxed = true), MALFORMED_URL, ERROR_URL)
        }
    }

    @Test
    fun `a malformed error URL fails construction`() {
        assertThrows<IllegalArgumentException> {
            WaitlistController(mockk(relaxed = true), SUCCESS_URL, MALFORMED_URL)
        }
    }

    @Test
    fun `a claimed token redirects to the success URL with the no-referrer and no-store headers`() {
        val waitlistService = mockk<WaitlistService>()
        every { waitlistService.confirm("t") } returns true

        val response = WaitlistController(waitlistService, SUCCESS_URL, ERROR_URL).confirm("t")

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertEquals(URI.create(SUCCESS_URL), response.headers.location)
        assertEquals("no-referrer", response.headers.getFirst("Referrer-Policy"))
        assertEquals("no-store", response.headers.getFirst(HttpHeaders.CACHE_CONTROL))
    }

    @Test
    fun `a failed confirmation redirects to the error URL with the same headers`() {
        val waitlistService = mockk<WaitlistService>()
        every { waitlistService.confirm("t") } returns false

        val response = WaitlistController(waitlistService, SUCCESS_URL, ERROR_URL).confirm("t")

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertEquals(URI.create(ERROR_URL), response.headers.location)
        assertEquals("no-referrer", response.headers.getFirst("Referrer-Policy"))
        assertEquals("no-store", response.headers.getFirst(HttpHeaders.CACHE_CONTROL))
    }
}
