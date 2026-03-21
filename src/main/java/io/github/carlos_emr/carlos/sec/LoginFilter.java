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

package io.github.carlos_emr.carlos.sec;

import java.io.IOException;
import java.util.Date;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Servlet filter that enforces authentication and session management for CARLOS EMR.
 *
 * <p>This filter intercepts all HTTP requests and performs the following security checks:
 * <ol>
 *   <li>Token-based authentication (for API/service requests)</li>
 *   <li>Session existence and validity checking</li>
 *   <li>Inactivity timeout enforcement</li>
 *   <li>URL exemption for public resources (login page, images, web services, etc.)</li>
 * </ol>
 *
 * <p>Security features:
 * <ul>
 *   <li><b>Session validation:</b> Redirects to logout page if no valid session exists</li>
 *   <li><b>Inactivity timeout:</b> Enforces configurable inactivity limit (INACTIVITY_LIMIT_MINS property)</li>
 *   <li><b>Token authentication:</b> Supports SecurityTokenManager for stateless API access</li>
 *   <li><b>Selective timeout:</b> Different URL exemptions for session validation vs. timeout tracking</li>
 * </ul>
 *
 * <p>URL exemption lists:
 * <ul>
 *   <li><b>EXEMPT_URLS:</b> URLs that don't require authentication (login page, public assets, web services)</li>
 *   <li><b>EXEMPT_URLS_FOR_REQUEST_TIMEOUT:</b> URLs that don't reset the inactivity timer (AJAX polling, etc.)</li>
 *   <li><b>EXEMPT_URLS_FOR_REQUEST_TIMEOUT_REDIRECT:</b> URLs exempt from timeout redirect (already on logout/login pages)</li>
 * </ul>
 *
 * <p>Inactivity timeout behavior:
 * <ul>
 *   <li>Tracks last request time in session attribute "last_request_time"</li>
 *   <li>Compares time since last request to INACTIVITY_LIMIT_MINS property</li>
 *   <li>Redirects to logout page if inactivity limit exceeded</li>
 *   <li>Does not update last request time for URLs in EXEMPT_URLS_FOR_REQUEST_TIMEOUT</li>
 * </ul>
 *
 * <p>Token-based authentication flow:
 * <ol>
 *   <li>Client requests token with request_token=true parameter</li>
 *   <li>Filter delegates to {@link SecurityTokenManager#requestToken}</li>
 *   <li>Client includes token in subsequent requests</li>
 *   <li>Filter validates token via {@link SecurityTokenManager#handleToken}</li>
 *   <li>Valid token sets "user" attribute in session</li>
 * </ol>
 *
 * <p>Configuration (web.xml):
 * <pre>
 * &lt;filter&gt;
 *     &lt;filter-name&gt;LoginFilter&lt;/filter-name&gt;
 *     &lt;filter-class&gt;io.github.carlos_emr.carlos.sec.LoginFilter&lt;/filter-class&gt;
 * &lt;/filter&gt;
 * &lt;filter-mapping&gt;
 *     &lt;filter-name&gt;LoginFilter&lt;/filter-name&gt;
 *     &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 * &lt;/filter-mapping&gt;
 * </pre>
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>INACTIVITY_LIMIT_MINS - Maximum minutes of inactivity before forced logout</li>
 * </ul>
 *
 * @see SecurityTokenManager for token-based authentication
 * @see io.github.carlos_emr.carlos.login.Login2Action for standard login authentication
 * @see io.github.carlos_emr.carlos.login.Logout2Action for logout and session cleanup
 * @since 2026-02-10
 */
public class LoginFilter implements Filter {

    /** Logger instance for filter events and debugging */
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * URLs exempt from authentication requirement.
     *
     * <p>Requests to these URLs bypass session validation and are allowed
     * without an authenticated session. This includes:
     * <ul>
     *   <li>Login/logout pages (index.jsp, logout.jsp, login.do)</li>
     *   <li>Public static resources (images, CSS, JavaScript, fonts)</li>
     *   <li>Lab upload endpoints (for external lab system integration)</li>
     *   <li>PDF generation servlets (for external document generation)</li>
     *   <li>Web services (/ws/* for SOAP/REST APIs)</li>
     *   <li>CSRF Guard endpoints (/csrfguard)</li>
     *   <li>MFA endpoints (/mfa/* for multi-factor authentication)</li>
     * </ul>
     *
     * <p>SECURITY NOTE: Any URL added to this list will be publicly accessible
     * without authentication. Ensure no PHI-exposing endpoints are included.
     */
    private static final String[] EXEMPT_URLS = {
            "/images/Oscar.ico",
            "/images/Logo.png",
            "/images/cloud-bg.svg",
            "/signature_pad/",
            "/lab/CMLlabUpload.do",
            "/lab/newLabUpload.do",
            "/lab/CA/ON/uploadComplete.jsp",
            "/login.do",
            "/logout.jsp",
            "/index.jsp",
            "/forcepasswordreset.jsp",
            "/loginfailed.jsp",
            "/index.html",
            "/eformViewForPdfGenerationServlet",
            "/LabViewForPdfGenerationServlet",
            "/oscarFacesheet/token_error.jsp",
            "/ws/",
            "/EFormViewForPdfGenerationServlet",
            "/EFormSignatureViewForPdfGenerationServlet",
            "/EFormImageViewForPdfGenerationServlet",
            "/js/bootstap",
            "/css/bootstrap",
            "/css/Roboto.css",
            "/loginResource",
            "/css/font/Roboto",
		"/csrfguard",
		"/mfa/",
		// Heartbeat endpoint must be reachable without an active session so windows
		// can detect server-side logout/timeout even after the session has been destroyed
		"/status/sessionHeartbeat.jsp"
    };

    /**
     * URLs exempt from inactivity timeout timer reset.
     *
     * <p>Requests to these URLs do not update the "last_request_time" session
     * attribute, preventing them from extending the user's session. This is
     * important for:
     * <ul>
     *   <li>AJAX polling endpoints (SystemMessage.do, FacilityMessage.do, tabAlertsRefresh.jsp)</li>
     *   <li>Static resources that shouldn't reset activity timer (JS, CSS, fonts)</li>
     *   <li>Provider control page refresh (providercontrol.jsp)</li>
     * </ul>
     *
     * <p>By exempting these URLs, background polling and resource loading won't
     * prevent legitimate inactivity timeouts, improving security.
     */
    private static final String[] EXEMPT_URLS_FOR_REQUEST_TIMEOUT = {
            "/images/Oscar.ico",
            "/images/Logo.png",
            "/login.do",
            "/logout.jsp",
            "/index.jsp",
            "/loginfailed.jsp",
            "/index.html",
            "/eformViewForPdfGenerationServlet",
            "/LabViewForPdfGenerationServlet",
            "/oscarFacesheet/token_error.jsp",
            "/ws/",
            "/EFormViewForPdfGenerationServlet",
            "/EFormSignatureViewForPdfGenerationServlet",
            "/EFormImageViewForPdfGenerationServlet",
            "/provider/providercontrol.jsp",
            "/js",
            "/provider/tabAlertsRefresh.jsp",
            "/SystemMessage.do",
            "/FacilityMessage.do",
            "/js/bootstrap",
            "/css/bootstrap",
            "/css/Roboto.css",
            "/loginResource",
            "/css/font/Roboto",
            // Heartbeat polling must not extend the inactivity timer, otherwise
            // background heartbeats would prevent legitimate session timeouts
            "/status/sessionHeartbeat.jsp"
    };

    /**
     * URLs exempt from inactivity timeout redirect.
     *
     * <p>If inactivity timeout is exceeded, users are normally redirected to
     * logout.jsp. However, if the user is already on one of these pages,
     * the redirect is skipped to avoid infinite redirect loops.
     */
    private static final String[] EXEMPT_URLS_FOR_REQUEST_TIMEOUT_REDIRECT = {
            "/logout.jsp",
            "/index.jsp",
            "/loginfailed.jsp",
            "/index.html"
    };

    /**
     * Initializes the filter on application startup.
     *
     * <p>Logs filter initialization for debugging and audit purposes.
     *
     * @param config FilterConfig servlet filter configuration (unused)
     * @throws ServletException if filter initialization fails
     */
    public void init(FilterConfig config) throws ServletException {
        logger.info("Starting Filter : " + getClass().getSimpleName());
        String limitProp = CarlosProperties.getInstance().getProperty("INACTIVITY_LIMIT_MINS");
        if (limitProp == null || limitProp.trim().isEmpty()) {
            logger.warn("INACTIVITY_LIMIT_MINS not configured, using default: 60 minutes");
        } else {
            logger.info("INACTIVITY_LIMIT_MINS configured: {} minutes", limitProp.trim());
        }
    }

    /**
     * Filters every HTTP request to enforce authentication and session management.
     *
     * <p>Request processing flow:
     * <ol>
     *   <li>Check for token-based authentication request/validation</li>
     *   <li>Verify session exists and contains "user" attribute</li>
     *   <li>Check URL against exemption lists</li>
     *   <li>Enforce inactivity timeout if configured</li>
     *   <li>Update last request time if not exempted</li>
     *   <li>Pass request to next filter in chain</li>
     * </ol>
     *
     * <p>Token-based authentication:
     * <ul>
     *   <li>request_token=true → generates and returns new token</li>
     *   <li>token parameter → validates token and sets session "user" attribute</li>
     * </ul>
     *
     * <p>Session validation:
     * <ul>
     *   <li>If no session or no "user" attribute → redirect to logout.jsp (unless URL is exempt)</li>
     *   <li>If session exists → check inactivity timeout</li>
     * </ul>
     *
     * <p>Inactivity timeout:
     * <ul>
     *   <li>Compares current time to "last_request_time" session attribute</li>
     *   <li>If exceeded INACTIVITY_LIMIT_MINS → redirect to logout.jsp</li>
     *   <li>Updates "last_request_time" unless URL is in EXEMPT_URLS_FOR_REQUEST_TIMEOUT</li>
     * </ul>
     *
     * @param request ServletRequest the HTTP request to filter
     * @param response ServletResponse the HTTP response
     * @param chain FilterChain the filter chain to continue processing
     * @throws IOException if I/O error occurs during filtering
     * @throws ServletException if servlet-level error occurs during filtering
     * @see SecurityTokenManager for token-based authentication
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        logger.debug("Entering LoginFilter.doFilter()");

        // Cast to HTTP-specific interfaces for session and redirect support
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String contextPath = httpRequest.getContextPath();
        String requestURI = httpRequest.getRequestURI();
        String InActivityLimitInMins = CarlosProperties.getInstance().getProperty("INACTIVITY_LIMIT_MINS");
        if (InActivityLimitInMins == null || InActivityLimitInMins.trim().isEmpty()) {
            InActivityLimitInMins = "60";
        }

        // Handle token-based authentication (for API/service requests)
        SecurityTokenManager stm = SecurityTokenManager.getInstance();
        if (stm != null) {
            // Client requesting new token (request_token=true)
            if (request.getParameter("request_token") != null && request.getParameter("request_token").equals("true")) {
                stm.requestToken(httpRequest, httpResponse, chain);
                return;
            }

            // Client sending token for validation (sets "user" attribute in session if valid)
            if (request.getParameter("token") != null || request.getAttribute("token") != null) {
                boolean success = stm.handleToken(httpRequest, httpResponse, chain);
                if (!success) {
                    return;
                }
            }
        }

        // Retrieve existing session without creating new one
        HttpSession session = httpRequest.getSession(false);
        // Redirect to logout page if no valid authenticated session exists
        if (session == null || session.getAttribute("user") == null) {

            // If the requested resource is not exempt, redirect to logout page
            // SECURITY: Root directory auto-exemption was removed to prevent
            // accidental exposure of resources. All exemptions must be explicit.
            if (!inListOfExemptions(requestURI, contextPath, EXEMPT_URLS)) {
                httpResponse.sendRedirect(contextPath + "/logout.jsp");
                return;
            }
        }
        // Enforce inactivity timeout if configured and session exists
        else if (session != null && InActivityLimitInMins != null) {
            try {
                long minLimit = Long.parseLong(InActivityLimitInMins);

                Date lastRequestDate = (Date) session.getAttribute("last_request_time");
                Date thisRequestDate = new Date();
                long timeSinceLastRequest = -1;
                if (lastRequestDate != null) {
                    // Calculate time before session expires (convert minutes to milliseconds)
                    long timeBeforeExpire = 60 * 1000 * minLimit;
                    long lastRequest = lastRequestDate.getTime();
                    long thisRequest = thisRequestDate.getTime();
                    timeSinceLastRequest = thisRequest - lastRequest;
                    logger.debug("lastRequestDate.getTime() " + lastRequestDate.getTime() + " thisRequestDate.getTime() " + thisRequestDate.getTime() + " -- " + timeSinceLastRequest);
                    // Redirect to logout if inactivity limit exceeded (unless already on logout/login page)
                    if (timeSinceLastRequest > timeBeforeExpire && !inListOfExemptions(requestURI, contextPath, EXEMPT_URLS_FOR_REQUEST_TIMEOUT_REDIRECT)) {
                        httpResponse.sendRedirect(contextPath + "/logout.jsp");
                        return;
                    }
                }

                if (!inListOfExemptions(requestURI, contextPath, EXEMPT_URLS_FOR_REQUEST_TIMEOUT)) {
                    logger.debug("reseting timer list uri " + httpRequest.getRequestURI());
                    session.setAttribute("last_request_time", thisRequestDate);
                }
            } catch (Exception e) {
                logger.error("ERROR checking for last activity. Limit Activity :" + InActivityLimitInMins, e);
            }
        }


        // Continue filter chain processing
        logger.debug("LoginFilter chainning");
        chain.doFilter(request, response);
    }

    /**
     * Checks if a request URI matches any URL in the exemption list.
     *
     * <p>This method performs prefix matching, so "/images/Oscar.ico" in the
     * exemption list will match "/contextPath/images/Oscar.ico" but not
     * "/contextPath/images/Oscar.ico.bak".
     *
     * @param requestURI String the full request URI including context path
     * @param contextPath String the servlet context path (e.g., "/carlos")
     * @param EXEMPT_URLS String[] array of exempt URL prefixes (without context path)
     * @return boolean true if request URI starts with any exempt URL, false otherwise
     */
    boolean inListOfExemptions(String requestURI, String contextPath, String[] EXEMPT_URLS) {
        // Treat context root (e.g. /carlos/) as equivalent to /index.jsp (welcome file)
        if (requestURI.equals(contextPath) || requestURI.equals(contextPath + "/")) {
            requestURI = contextPath + "/index.jsp";
        }
        for (String exemptUrl : EXEMPT_URLS) {
            if (requestURI.startsWith(contextPath + exemptUrl)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Cleanup method called when filter is destroyed on application shutdown.
     *
     * <p>Currently no cleanup is needed for this filter.
     */
    public void destroy() {
    }

}
