<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada

    Migrated from Apache CXF to ScribeJava OAuth1 implementation.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ page import="com.github.scribejava.core.model.OAuth1RequestToken" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.AppUserDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.AppDefinitionDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.AppUser" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.AppDefinition" %>
<%@ page import="io.github.carlos_emr.carlos.app.AppOAuth1Config" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="java.util.Date" %>
<%@ page import="javax.servlet.http.*" %>
<%@ page import="org.slf4j.Logger, org.slf4j.LoggerFactory" %>

<%!
  // Declare a JSP-level logger
  private static final Logger log = LoggerFactory.getLogger("oauth1_jsp");
%>

<%
    // Lookup DAOs
    AppUserDao appUserDao      = SpringUtils.getBean(AppUserDao.class);
    AppDefinitionDao appDefDao = SpringUtils.getBean(AppDefinitionDao.class);

    // Determine which app we’re handling
    Integer appId = null;
    String idParam = request.getParameter("id");
    if (idParam != null) {
        try {
            appId = Integer.parseInt(idParam);
        } catch (NumberFormatException e) {
            // K2A handling removed
        }
    } else if (session.getAttribute("appId") != null) {
        appId = (Integer) session.getAttribute("appId");
    }

    if (appId == null) {
        response.sendRedirect("close.jsp");
        return;
    }

    // Load our OAuth1 app configuration
    AppDefinition   appDef      = appDefDao.find(appId);
    AppOAuth1Config oauthConfig = AppOAuth1Config.fromDocument(appDef.getConfig());

    // Check if we’re in the “request token” phase or “access token” phase
    String oauthVerifier = request.getParameter("oauth_verifier");
    if (oauthVerifier == null) {
        // --- Phase 1: redirect client to providers’s auth URL ---
        try {
            OAuth1RequestToken requestToken =
                (OAuth1RequestToken) request.getAttribute("requestToken");

            // Build context for logging
            String appIdContext = (session != null && session.getAttribute("appId") != null)
                ? "appId=" + session.getAttribute("appId") + ", "
                : "";
            String userContext = (session != null && session.getAttribute("user") != null)
                ? "user=" + session.getAttribute("user") + ", "
                : "";
            String contextMsg = appIdContext + userContext + "requestURI=" + request.getRequestURI();

            if (requestToken == null) {
                // Warn and redirect to a friendly error page
                log.warn("Missing requestToken: {}", contextMsg);
                session.setAttribute("oauthErrorMessage",
                    "Authentication failed: missing request token.");
                response.sendRedirect(request.getContextPath() + "/errorpage.jsp");
                return;
            }

            // Persist for phase 2
            session.setAttribute("requestToken", requestToken);
            session.setAttribute("appId", appId);

            // Authorization URL was prepared by the service layer
            String authUrl = (String) request.getAttribute("authorizationUrl");
            response.sendRedirect(authUrl);
            return;

        } catch (Exception e) {
            log.debug("Entering OAuth callback, sessionId={}, requestURI={}"
                , session.getId(), request.getRequestURI());
            session.setAttribute("oauthMessage", "Error requesting token");
            response.sendRedirect("close.jsp");
            return;
        }
    } else {
        // --- Phase 2: handling callback, exchange for access token ---
        try {
            OAuth1RequestToken requestToken =
                (OAuth1RequestToken) session.getAttribute("requestToken");
            if (requestToken == null) {
                throw new IllegalStateException("No requestToken in session");
            }

            // These were set by your service layer
            String accessTokenString = (String) request.getAttribute("accessToken");
            String accessTokenSecret = (String) request.getAttribute("accessTokenSecret");

            // guard against missing values
            if (accessTokenString == null || accessTokenSecret == null) {
                log.warn("Missing OAuth1 access token: token={} secret={}"
                        , accessTokenString, accessTokenSecret);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                                    "OAuth flow failed: missing access token");
                return;
            }

            // Persist the authorized user
            AppUser appUser = new AppUser();
            appUser.setAppId(appId);
            appUser.setProviderNo((String) session.getAttribute("user"));

            // Build the XML blob of token & secret
            String authData = AppOAuth1Config.getTokenXML(accessTokenString, accessTokenSecret);
            appUser.setAuthenticationData(authData);
            appUser.setAdded(new Date());

            appUserDao.saveEntity(appUser);

        } catch (Exception e) {
            log.error("Failed to persist AppUser for appId={}"
                , appId, e);
            session.setAttribute("oauthMessage", "Error verifying authentication");
            response.sendRedirect("close.jsp");
            return;
        }

        // Success!
        session.setAttribute("oauthMessage", "Success");
        session.removeAttribute("requestToken");
        session.removeAttribute("appId");
        response.sendRedirect("close.jsp");
        return;
    }
%>