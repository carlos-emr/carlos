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
 * Servlet filter that populates the {@link LoggedInInfo} object in the HTTP session
 * on every request.
 *
 * <p>Extracts the logged-in provider, security credentials, current facility, locale,
 * and IP address from the session and request, then stores a fresh {@link LoggedInInfo}
 * instance in the session for use by downstream components.
 *
 * @since 2026-03-17
 */
public class LoggedInUserFilter implements jakarta.servlet.Filter {
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Initializes the filter and logs startup.
     *
     * @param filterConfig FilterConfig the filter configuration
     * @throws ServletException if initialization fails
     */
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Starting Filter : " + getClass().getSimpleName());
    }

    /**
     * Generates and stores a fresh {@link LoggedInInfo} in the session, then continues
     * the filter chain.
     *
     * @param tmpRequest  ServletRequest the servlet request
     * @param tmpResponse ServletResponse the servlet response
     * @param chain       FilterChain the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    public void doFilter(ServletRequest tmpRequest, ServletResponse tmpResponse, FilterChain chain) throws IOException, ServletException {
        logger.debug("Entering LoggedInUserFilter.doFilter()");

        // set new / current data
        HttpServletRequest request = (HttpServletRequest) tmpRequest;
        LoggedInInfo x = generateLoggedInInfoFromSession(request);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), x);

        logger.debug("LoggedInUserFilter chainning");
        chain.doFilter(tmpRequest, tmpResponse);
    }

    /**
     * Called when the filter is taken out of service. No cleanup is required.
     */
    public void destroy() {
        // can't think of anything to do right now.
    }

    /**
     * Creates a {@link LoggedInInfo} from the current HTTP session attributes and request data.
     *
     * @param request HttpServletRequest the current request
     * @return LoggedInInfo populated with provider, security, facility, locale, and IP data
     */
    public static LoggedInInfo generateLoggedInInfoFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession();

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
}
