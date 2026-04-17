/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;

/**
 * Rebuilds {@link LoggedInInfo} from the authenticated HTTP session before
 * Struts action execution.
 *
 * <p>The filter intentionally uses {@link HttpServletRequest#getSession(boolean)}
 * so anonymous or static requests do not create new sessions. Only sessions
 * that already contain the canonical authenticated markers are enriched with a
 * derived {@link LoggedInInfo} object.</p>
 *
 * @since 2026-04-15
 */
public class LoggedInUserFilter implements jakarta.servlet.Filter {
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Logs filter startup for container diagnostics.
     *
     * @param filterConfig servlet filter config supplied by the container
     * @throws ServletException if the filter cannot be initialized
     */
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Starting Filter : " + getClass().getSimpleName());
    }

    /**
     * Populates {@link LoggedInInfo} only for already-authenticated sessions,
     * then continues the request chain.
     *
     * @param tmpRequest servlet request
     * @param tmpResponse servlet response
     * @param chain downstream filter chain
     * @throws IOException if downstream processing fails with I/O errors
     * @throws ServletException if downstream processing fails at servlet level
     */
    public void doFilter(ServletRequest tmpRequest, ServletResponse tmpResponse, FilterChain chain) throws IOException, ServletException {
        logger.debug("Entering LoggedInUserFilter.doFilter()");

        HttpServletRequest request = (HttpServletRequest) tmpRequest;
        HttpSession session = request.getSession(false);
        if (hasAuthenticatedSession(session)) {
            LoggedInInfo loggedInInfo = generateLoggedInInfoFromSession(request, session);
            LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        }

        logger.debug("LoggedInUserFilter chainning");
        chain.doFilter(tmpRequest, tmpResponse);
    }

    public void destroy() {
        // can't think of anything to do right now.
    }

    /**
     * Derives a {@link LoggedInInfo} object from the current authenticated
     * session, or returns {@code null} when the request has no authenticated
     * session.
     *
     * @param request current HTTP request
     * @return derived logged-in info, or {@code null} for anonymous/incomplete sessions
     */
    public static LoggedInInfo generateLoggedInInfoFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!hasAuthenticatedSession(session)) {
            return null;
        }
        return generateLoggedInInfoFromSession(request, session);
    }

    /**
     * Builds the derived logged-in wrapper from canonical session attributes.
     */
    private static LoggedInInfo generateLoggedInInfoFromSession(HttpServletRequest request, HttpSession session) {

        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setSession(session);
        loggedInInfo.setCurrentFacility((Facility) session.getAttribute(SessionConstants.CURRENT_FACILITY));
        loggedInInfo.setLoggedInProvider((Provider) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER));
        loggedInInfo.setLoggedInSecurity((Security) session.getAttribute(SessionConstants.LOGGED_IN_SECURITY));
        loggedInInfo.setInitiatingCode(request.getRequestURI());
        loggedInInfo.setLocale(request.getLocale());
        loggedInInfo.setIp(request.getRemoteAddr());

        return (loggedInInfo);
    }

    /**
     * Checks whether the session has the minimum authenticated markers needed
     * to derive {@link LoggedInInfo} safely.
     */
    private static boolean hasAuthenticatedSession(HttpSession session) {
        return session != null
                && session.getAttribute("user") != null
                && session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER) != null
                && session.getAttribute(SessionConstants.LOGGED_IN_SECURITY) != null;
    }
}
