package com.catspell.api.common.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UrlPathHelper

/**
 * The one request-path source for path-based security decisions: the rate limiter, and from 18-10 the JWT filter's
 * skip list (D-16, current IN-01).
 */
internal object RequestPaths {

    /**
     * The container-normalized request path: `servletPath + pathInfo`, with `;` path parameters removed and dot
     * segments (`/./`, `/../`) and empty segments (`//`) resolved.
     *
     * In production Tomcat has already percent-decoded and normalized servletPath before any filter runs, so this is
     * the exact path the servlet mapping and the handler see. The extra normalization makes MockMvc match production:
     * MockMvc leaves servletPath empty and puts the decoded but un-normalized URI in pathInfo. Matching on this path
     * means a spelling such as `/api/auth/./login` is throttled by the filter itself, without relying on
     * StrictHttpFirewall rejecting it later in the chain.
     */
    fun normalized(request: HttpServletRequest): String {
        val containerPath = request.servletPath + (request.pathInfo ?: "")
        val withoutParams = UrlPathHelper.defaultInstance.removeSemicolonContent(containerPath)
        return UriComponentsBuilder.fromPath(withoutParams).build().normalize().path ?: withoutParams
    }
}
