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


package io.github.carlos_emr.carlos.web.eform;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.FacilityManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * The purpose of this servlet is to allow a local process to convert an html page into a pdf file in a manner similar to viewing a pdf with a browser and selecting print to file
 */
public final class EformViewForPdfGenerationServlet extends HttpServlet {

    public static final String SKIP_HTML_INJECTION_ATTRIBUTE = EformViewForPdfGenerationServlet.class.getName() + ".skipHtmlInjection";

    private static final Logger logger = MiscUtils.getLogger();

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String remoteAddress = request.getRemoteAddr();
            logger.debug("EformPdfServlet request from : {}", remoteAddress);
            if (!"127.0.0.1".equals(remoteAddress) && !"0:0:0:0:0:0:0:1".equals(remoteAddress) && !"::1".equals(remoteAddress)) {
                logger.warn("Unauthorised request made to EformPdfServlet from address : {}", remoteAddress);
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String providerNo = request.getParameter("providerId");
            if (providerNo == null || providerNo.isBlank()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: providerId");
                return;
            }

            if (!establishRendererSession(request, providerNo.trim())) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unable to initialize renderer session");
                return;
            }
            request.setAttribute(SKIP_HTML_INJECTION_ATTRIBUTE, Boolean.TRUE);

            RequestDispatcher requestDispatcher = request.getRequestDispatcher("/eform/efmshowform_data");
            requestDispatcher.forward(request, response);
        } catch (ServletException | IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in EformViewForPdfGenerationServlet", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }

    boolean establishRendererSession(HttpServletRequest request, String providerNo) {
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
        FacilityManager facilityManager = SpringUtils.getBean(FacilityManager.class);

        Provider provider = providerDao.getProvider(providerNo);
        Security security = securityDao.getByProviderNo(providerNo);
        if (provider == null || security == null) {
            logger.warn("Renderer session initialization failed: provider or security record not found");
            return false;
        }

        String trustedProviderNo = provider.getProviderNo();
        HttpSession session = request.getSession(true);
        session.setAttribute("user", trustedProviderNo); // nosemgrep: java.servlets.security.tainted-session-from-http-request-deepsemgrep.tainted-session-from-http-request-deepsemgrep -- localhost-only renderer route; session user comes from DAO-backed provider lookup, not raw request data
        session.setAttribute("provider", provider); // nosemgrep: java.servlets.security.tainted-session-from-http-request-deepsemgrep.tainted-session-from-http-request-deepsemgrep -- localhost-only renderer route; provider object resolved from DAO before session bootstrap
        session.setAttribute(SessionConstants.LOGGED_IN_PROVIDER, provider); // nosemgrep: java.servlets.security.tainted-session-from-http-request-deepsemgrep.tainted-session-from-http-request-deepsemgrep -- localhost-only renderer route; provider object resolved from DAO before session bootstrap
        session.setAttribute(SessionConstants.LOGGED_IN_SECURITY, security); // nosemgrep: java.servlets.security.tainted-session-from-http-request-deepsemgrep.tainted-session-from-http-request-deepsemgrep -- localhost-only renderer route; security object resolved from DAO before session bootstrap

        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setSession(session);
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLoggedInSecurity(security);
        loggedInInfo.setInitiatingCode(EformViewForPdfGenerationServlet.class.getName());
        loggedInInfo.setLocale(request.getLocale());
        loggedInInfo.setIp("127.0.0.1");

        Facility facility = facilityManager.getDefaultFacility(loggedInInfo);
        if (facility != null) {
            session.setAttribute(SessionConstants.CURRENT_FACILITY, facility); // nosemgrep: java.servlets.security.tainted-session-from-http-request-deepsemgrep.tainted-session-from-http-request-deepsemgrep -- localhost-only renderer route; facility derived from trusted provider/security session bootstrap
            loggedInInfo.setCurrentFacility(facility);
        }

        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        return true;
    }
}
