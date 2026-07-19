/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.HtmlResponse;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * The purpose of this servlet is to allow a local process to convert an eform html page into a pdf file.
 *
 * <p>This class owns only the HTTP concerns: the loopback-only remote-address gate, render-token
 * redemption (browser path) or session {@code _eform} authorization (session path), the
 * per-render Content-Security-Policy, and writing the response. The stored-form HTML assembly is
 * delegated to {@link EformRenderPdfHtmlComposer}.</p>
 */
public final class EFormViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String PROVIDER_ID_PARAM = "providerId";
    /** Render-scoped grant parameter redeemed against {@link EFormRenderTokenService}. */
    static final String RENDER_TOKEN_PARAM = "renderToken";

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String remoteAddress = request.getRemoteAddr();
            logger.debug("EFormViewForPdfGenerationServlet request from : {}", remoteAddress);
            if (!"127.0.0.1".equals(remoteAddress) && !"0:0:0:0:0:0:0:1".equals(remoteAddress) && !"::1".equals(remoteAddress)) {
                logger.warn("Unauthorised request made to EFormViewForPdfGenerationServlet from address : {}", remoteAddress);
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            boolean browserRender = "true".equals(request.getParameter("browserRender"));
            if (browserRender) {
                // Signal the response-rewriting filters to leave this render's HTML untouched so the
                // captured eForm DOM is byte-for-byte the stored form. CsrfGuardScriptInjectionFilter
                // also skips this route by URI (it checks before doFilter); LogoutBroadcastFilter reads
                // this attribute after doFilter.
                request.setAttribute(
                        io.github.carlos_emr.carlos.web.eform.EformViewForPdfGenerationServlet.SKIP_HTML_INJECTION_ATTRIBUTE,
                        Boolean.TRUE);
            }
            String id = request.getParameter("fdid");
            if (id == null || id.trim().isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: fdid");
                return;
            }

            Integer formDataId = parseFormDataId(id, response);
            if (formDataId == null) {
                return;
            }

            String providerId;
            String renderToken = null;
            if (browserRender) {
                EFormRenderTokenService.RenderGrant grant = redeemedRenderGrant(request, formDataId);
                if (grant == null) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Saved eForm PDF rendering requires a valid render token");
                    return;
                }
                providerId = grant.providerNo();
                // Carry the grant forward onto the eForm's own asset URLs so the sessionless render
                // browser can fetch its background/asset images under the same render-scoped token.
                renderToken = request.getParameter(RENDER_TOKEN_PARAM);
            } else {
                LoggedInInfo loggedInInfo = authorizedEformReadRequest(request);
                if (loggedInInfo == null) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Saved eForm PDF rendering requires an authenticated _eform session");
                    return;
                }
                providerId = request.getParameter(PROVIDER_ID_PARAM);
            }

            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", buildContentSecurityPolicy(browserRender));

            boolean prepareForFax = "true".equals(request.getParameter("prepareForFax"));

            String html = EformRenderPdfHtmlComposer.buildPdfHtmlForFdid(
                    formDataId, request.getContextPath(), request.getHeader("User-Agent"), providerId, prepareForFax, renderToken);

            HtmlResponse.of(HtmlResponse.DEFAULT_HTML_CONTENT_TYPE_WITH_CHARSET, html).writeTo(response);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in EFormViewForPdfGenerationServlet", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }

    /**
     * Redeems the render-scoped grant carried by a browser-render request.
     *
     * <p>Renderer requests carry no HTTP session by design. Authorization happened when
     * {@code EformDataManagerImpl} passed its {@code _eform} privilege check and minted a grant
     * bound to this fdid. Redemption is render-scoped ({@code peek}, not consume) so the same grant
     * also authorizes the eForm's loopback asset-image subresources during the render; the renderer
     * invalidates the token when the render finishes. Fail-closed on any mismatch.</p>
     *
     * @return the grant, or null when the token is missing, expired, invalidated, or bound to a
     *         different saved eForm
     */
    static EFormRenderTokenService.RenderGrant redeemedRenderGrant(HttpServletRequest request, int formDataId) {
        EFormRenderTokenService.RenderGrant grant =
                EFormRenderTokenService.getInstance().peek(request.getParameter(RENDER_TOKEN_PARAM));
        if (grant == null || grant.fdid() != formDataId) {
            logger.warn("Renderer request rejected: missing, expired, or mismatched render token");
            return null;
        }
        return grant;
    }

    private static LoggedInInfo authorizedEformReadRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        LoggedInInfo loggedInInfo = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProvider() == null || loggedInInfo.getLoggedInSecurity() == null) {
            logger.warn("Saved eForm PDF request rejected: no authenticated session was present");
            return null;
        }

        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null)) {
            logger.warn("Saved eForm PDF request rejected: authenticated session lacks _eform read privilege");
            return null;
        }

        return loggedInInfo;
    }

    static String buildContentSecurityPolicy(boolean browserRender) {
        if (!browserRender) {
            return "default-src 'self'; script-src 'none'; object-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:";
        }
        return "default-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; object-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'";
    }

    private static Integer parseFormDataId(String id, HttpServletResponse response) throws IOException {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid parameter: fdid must be a valid number");
            return null;
        }
    }
}
