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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Session-gated loopback entry for the legacy renderer URL: verifies the authenticated provider
 * matches the requested providerId, then forwards to /eform/efmshowform_data with the canonical
 * session-verified provider id. Conversion itself happens in the browser renderer
 * (EFormBrowserPdfService).
 *
 * <p>Naming-convention exception: this class deliberately keeps the legacy {@code Eform} casing
 * (rather than the project-standard {@code EForm}). It is mapped in {@code web.xml} to the lowercase
 * URL {@code /eformViewForPdfGenerationServlet}; the similarly-named uppercase URL
 * {@code /EFormViewForPdfGenerationServlet} maps to the separate render-page servlet
 * {@code eform.util.EFormBrowserRenderPageServlet}. The two were untangled to remove exactly this
 * same-name-different-case ambiguity, so renaming this class would reintroduce it.</p>
 */
public final class EformViewForPdfGenerationServlet extends HttpServlet {

    public static final String SKIP_HTML_INJECTION_ATTRIBUTE = EformViewForPdfGenerationServlet.class.getName() + ".skipHtmlInjection";
    private static final String PROVIDER_ID_PARAM = "providerId";

    private static final Logger logger = MiscUtils.getLogger();

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String remoteAddress = request.getRemoteAddr();
            logger.debug("EformViewForPdfGenerationServlet request from : {}", remoteAddress);
            if (!isLocalRequest(remoteAddress)) {
                logger.warn("Unauthorised request made to EformViewForPdfGenerationServlet from address : {}", remoteAddress);
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String providerNo = request.getParameter(PROVIDER_ID_PARAM);
            if (providerNo == null || providerNo.isBlank()) {
                logger.debug("EformViewForPdfGenerationServlet rejected: missing required {} parameter", PROVIDER_ID_PARAM);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: " + PROVIDER_ID_PARAM);
                return;
            }
            String canonicalProviderNo = providerNo.trim();

            String sessionProviderNo = authorizedRendererProviderNo(request, canonicalProviderNo);
            if (sessionProviderNo == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Renderer request requires an authenticated matching provider session");
                return;
            }
            logger.debug("EformViewForPdfGenerationServlet authorized provider-scoped render; forwarding to eForm view");

            response.setHeader("X-Content-Type-Options", "nosniff");
            // Content-Security-Policy is intentionally NOT set here: the forwarded
            // /eform/efmshowform_data view (efmshowform_data.jsp) owns and sets the effective
            // CSP for the rendered form surface, and any header set here would be overwritten.
            request.setAttribute(SKIP_HTML_INJECTION_ATTRIBUTE, Boolean.TRUE);

            RequestDispatcher requestDispatcher = request.getRequestDispatcher("/eform/efmshowform_data");
            requestDispatcher.forward(wrapRequestWithCanonicalProviderId(request, sessionProviderNo), response);
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

    String authorizedRendererProviderNo(HttpServletRequest request, String providerNo) {
        // Use getSession(false) so rejected renderer probes never create a new HTTP session.
        HttpSession session = request.getSession(false);
        LoggedInInfo loggedInInfo = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProvider() == null || loggedInInfo.getLoggedInSecurity() == null) {
            logger.warn("Renderer request rejected: no authenticated session was present");
            return null;
        }

        String sessionProviderNo = loggedInInfo.getLoggedInProviderNo();
        if (sessionProviderNo == null || !sessionProviderNo.equals(providerNo)) {
            logger.warn("Renderer request rejected: provider mismatch for authenticated session");
            return null;
        }

        return sessionProviderNo;
    }


    private static HttpServletRequest wrapRequestWithCanonicalProviderId(HttpServletRequest request, String canonicalProviderNo) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                if (PROVIDER_ID_PARAM.equals(name)) {
                    return canonicalProviderNo;
                }
                return super.getParameter(name);
            }

            @Override
            public String[] getParameterValues(String name) {
                if (PROVIDER_ID_PARAM.equals(name)) {
                    return new String[] {canonicalProviderNo};
                }
                return super.getParameterValues(name);
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                Map<String, String[]> parameterMap = new HashMap<>(super.getParameterMap());
                parameterMap.put(PROVIDER_ID_PARAM, new String[] {canonicalProviderNo});
                return Collections.unmodifiableMap(parameterMap);
            }
        };
    }

    private static boolean isLocalRequest(String remoteAddress) {
        return "127.0.0.1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress);
    }
}
