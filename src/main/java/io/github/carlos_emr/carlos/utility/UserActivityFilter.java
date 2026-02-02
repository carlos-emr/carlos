//CHECKSTYLE:OFF
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
 */


package io.github.carlos_emr.carlos.utility;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Date;

/**
 * Filter for determining the inactivity of a user with a session. Pages that automatically refresh should be marked with the parameter autoRefresh=true
 */
public final class UserActivityFilter implements Filter {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String LAST_USER_ACTIVITY = "LAST_USER_ACTIVITY";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        boolean redirectToLogout = false;
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;


            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(httpRequest);
            Long now = (new Date()).getTime();

            HttpSession session = httpRequest.getSession(false);
            if (session != null && !httpRequest.getRequestURL().toString().contains(httpRequest.getContextPath() + "/logout.jsp")) {
                Long lastActivity = (Long) session.getAttribute(LAST_USER_ACTIVITY);

                if (lastActivity == null) {
                    lastActivity = now; // set new last activity
                }
                if (now - lastActivity > session.getMaxInactiveInterval() * 1000L) {
                    LogAction.addLog((String) session.getAttribute("user"), LogConst.LOGOUT, LogConst.CON_LOGIN, "logged out due to inactivity", request.getRemoteAddr());
                    logger.warn("User providerNo=" + loggedInInfo.getLoggedInProviderNo() + " logged out due to inactivity");
                    redirectToLogout = true;
                } else if (isUserRequest(httpRequest)) {
                    // Reset activity timer in session
                    session.setAttribute(LAST_USER_ACTIVITY, now);
                }
            }
        }
        if (redirectToLogout) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.sendRedirect(((HttpServletRequest) request).getContextPath() + "/logout.jsp?autoLogout=true&errorMessage=logged out due to inactivity");
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isUserRequest(HttpServletRequest httpRequest) {
        if (Boolean.parseBoolean(httpRequest.getParameter("autoRefresh"))) { // is autorefresh
            return false;
        } else if (httpRequest.getRequestURL().toString().endsWith(httpRequest.getContextPath() + "/JavaScriptServlet")) { // is csrf servlet js
            return false;
        }
        return true;
    }
}