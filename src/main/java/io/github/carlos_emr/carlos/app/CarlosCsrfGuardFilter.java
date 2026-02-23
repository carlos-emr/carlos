/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.app;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.owasp.csrfguard.CsrfGuard;
import org.owasp.csrfguard.CsrfValidator;
import org.owasp.csrfguard.http.InterceptRedirectResponse;
import org.owasp.csrfguard.session.LogicalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * CARLOS CSRF Guard filter for CSRFGuard 4.5.
 *
 * <p>A custom filter is necessary because the stock {@code org.owasp.csrfguard.CsrfGuardFilter}
 * does not wrap multipart/form-data requests, causing CSRF token extraction to consume the input
 * stream and break downstream file upload processing.</p>
 *
 * <p>Operates in <strong>blocking mode</strong>: validates CSRF tokens and rejects requests
 * that fail validation. The configured CSRFGuard actions (Log, Redirect) handle the response
 * for invalid requests.</p>
 *
 * <p>Additionally wraps multipart/form-data requests with {@link MultiReadHttpServletRequest}
 * so that the request body input stream can be read multiple times — once by {@link CsrfValidator}
 * for CSRF token extraction, and again by downstream servlets for normal request processing.</p>
 *
 * <p>Replaces the legacy {@code OscarCsrfGuardFilter} (deleted in the CSRFGuard 4.5 migration)
 * which used CSRFGuard 3.1.0 APIs.</p>
 *
 * @since 2026-02-22
 */
public class CarlosCsrfGuardFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CarlosCsrfGuardFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // CsrfGuard manages its own configuration lifecycle via CsrfGuard.getInstance()
    }

    /**
     * Validates the CSRF token on each request.
     *
     * <p>Flow: wraps multipart requests with {@link MultiReadHttpServletRequest} for dual-read
     * support, delegates token validation to {@link CsrfValidator#isValid}, generates tokens
     * if absent for the current session via {@code TokenService}, then continues the filter
     * chain on success.</p>
     *
     * @param request      the servlet request
     * @param response     the servlet response
     * @param filterChain  the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            LOGGER.warn("CSRF filter received non-HTTP request type: {}", request.getClass().getName());
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        CsrfGuard csrfGuard;
        try {
            csrfGuard = CsrfGuard.getInstance();
        } catch (Exception e) {
            LOGGER.error("CsrfGuard is not initialized — cannot validate CSRF tokens. "
                    + "Rejecting request to {} {}",
                    httpRequest.getMethod(), httpRequest.getRequestURI(), e);
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        if (!csrfGuard.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap multipart requests so the input stream can be read by both CsrfValidator and downstream servlets
        if (ServletFileUpload.isMultipartContent(httpRequest)) {
            try {
                httpRequest = new MultiReadHttpServletRequest(httpRequest);
            } catch (Exception e) {
                LOGGER.error("Failed to wrap multipart request for {} {} — rejecting with 400",
                        httpRequest.getMethod(), httpRequest.getRequestURI(), e);
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        }

        InterceptRedirectResponse interceptResponse = new InterceptRedirectResponse(httpResponse, httpRequest, csrfGuard);

        // Validate the request (CsrfValidator logs violations and invokes configured actions)
        boolean valid;
        try {
            valid = new CsrfValidator().isValid(httpRequest, interceptResponse);
        } catch (Exception e) {
            LOGGER.error("Unexpected error during CSRF validation for {} {} — rejecting with 403",
                    httpRequest.getMethod(), httpRequest.getRequestURI(), e);
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!valid) {
            LOGGER.warn("CSRF validation failed for {} {} — request blocked",
                    httpRequest.getMethod(), httpRequest.getRequestURI());
            // Actions (Log, Redirect) have already been executed by CsrfValidator.
            // Defensive fallback: if the configured actions did not commit the response
            // (e.g., Redirect action is misconfigured or absent), send 403 explicitly.
            if (!httpResponse.isCommitted()) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
            return;
        }

        // Generate session/page tokens if not yet created for this request
        try {
            LogicalSession logicalSession = csrfGuard.getLogicalSessionExtractor().extract(httpRequest);
            if (logicalSession != null) {
                csrfGuard.getTokenService().generateTokensIfAbsent(
                        logicalSession.getKey(), httpRequest.getMethod(), httpRequest.getRequestURI());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate CSRF tokens for validated request {} {} — "
                    + "continuing without token generation (next POST from this page WILL fail validation)",
                    httpRequest.getMethod(), httpRequest.getRequestURI(), e);
        }

        // Validation passed — continue the filter chain
        filterChain.doFilter(httpRequest, interceptResponse);
    }

    @Override
    public void destroy() {
        // No cleanup required
    }
}
