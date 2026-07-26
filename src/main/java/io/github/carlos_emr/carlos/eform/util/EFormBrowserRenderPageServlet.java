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

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.HtmlResponse;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Serves the token- or session-authorized eForm HTML surface that the loopback browser PDF
 * renderer ({@link EFormBrowserPdfService}) navigates to and captures. It performs no PDF
 * conversion itself.
 *
 * <p>This class owns only the HTTP concerns: the loopback-only remote-address gate, render-token
 * redemption (browser path) or session {@code _eform} authorization (session path), the
 * per-render Content-Security-Policy, and writing the response. The stored-form HTML assembly is
 * delegated to {@link EFormRenderPdfHtmlComposer}.</p>
 *
 * <p>The registered {@code /EFormViewForPdfGenerationServlet} route is consumed by the renderer and
 * by its security filters, so it is part of the internal render contract.</p>
 */
public final class EFormBrowserRenderPageServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = MiscUtils.getLogger();
    private static final String PROVIDER_ID_PARAM = "providerId";
    /** Render-scoped grant parameter redeemed against {@link EFormRenderTokenService}. */
    static final String RENDER_TOKEN_PARAM = "renderToken";

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String remoteAddress = request.getRemoteAddr();
            logger.debug("EFormBrowserRenderPageServlet request from : {}", remoteAddress);
            if (!"127.0.0.1".equals(remoteAddress) && !"0:0:0:0:0:0:0:1".equals(remoteAddress) && !"::1".equals(remoteAddress)) {
                logger.warn("Unauthorised request made to EFormBrowserRenderPageServlet from address : {}", remoteAddress);
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
                logger.debug("EFormBrowserRenderPageServlet rejected: missing required fdid parameter");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: fdid");
                return;
            }

            Integer formDataId = parseFormDataId(id, response);
            if (formDataId == null) {
                return;
            }

            String providerId;
            EFormRenderTokenService.RenderToken renderToken = null;
            EFormRenderTokenService.RenderGrant browserGrant = null;
            if (browserRender) {
                renderToken = EFormRenderTokenService.RenderToken.fromRequestValue(
                        request.getParameter(RENDER_TOKEN_PARAM));
                EFormRenderTokenService.RenderSession renderSession =
                        EFormRendererRequestAuthorization.exchangeBootstrap(request, renderToken);
                browserGrant = EFormRenderTokenService.getInstance().peek(renderToken);
                if (renderSession == null || browserGrant == null || browserGrant.fdid() != formDataId) {
                    logger.warn("Renderer request rejected: missing, expired, replayed, or mismatched render token");
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Saved eForm PDF rendering requires a valid render token");
                    return;
                }
                EFormRendererRequestAuthorization.setRendererCookie(request, response, renderSession);
                providerId = browserGrant.providerNo();
                logger.debug("EFormBrowserRenderPageServlet authorized browser-render via render grant: fdid={}", formDataId);
            } else {
                LoggedInInfo loggedInInfo = authorizedEformReadRequest(request, formDataId);
                if (loggedInInfo == null) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Saved eForm PDF rendering requires an authenticated _eform session");
                    return;
                }
                // Mirror the sibling session gate (web.eform.EformViewForPdfGenerationServlet):
                // the render surface is scoped to the authenticated provider. The session's
                // provider number is authoritative; a present-but-different request-supplied
                // providerId is rejected, never trusted.
                providerId = loggedInInfo.getLoggedInProviderNo();
                String requestedProviderId = request.getParameter(PROVIDER_ID_PARAM);
                if (requestedProviderId != null && !requestedProviderId.trim().isEmpty()
                        && !requestedProviderId.trim().equals(providerId)) {
                    logger.warn("Saved eForm PDF request rejected: providerId does not match the authenticated session");
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Renderer request requires a matching provider session");
                    return;
                }
                logger.debug("EFormBrowserRenderPageServlet authorized via _eform session: fdid={}", formDataId);
            }

            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader(
                    "Content-Security-Policy",
                    buildContentSecurityPolicy(browserRender, request));

            String html = EFormRenderPdfHtmlComposer.buildPdfHtmlForFdid(
                    formDataId, request.getContextPath(), providerId, renderToken);
            if (browserGrant != null) {
                EFormRendererRequestAuthorization.authorizeReferencedStaticResources(
                        browserGrant,
                        html,
                        request.getContextPath(),
                        getServletConfig() == null ? null : getServletContext());
            }

            HtmlResponse.of(HtmlResponse.DEFAULT_HTML_CONTENT_TYPE_WITH_CHARSET, html).writeTo(response);
        } catch (IOException e) {
            // A write/sendError I/O failure is almost always a client disconnect mid-response; the
            // response is already (partly) committed. Handle it here rather than letting it escape the
            // servlet method (S1989) — there is nothing left to send. URL-redacted: the request URI can
            // carry the fdid/render token.
            logger.debug("EFormBrowserRenderPageServlet response I/O failed (client likely disconnected): {}",
                    RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        } catch (Exception e) {
            // Same redaction rule as the renderer: this route's request URL carries the fdid and
            // render token, and container/machinery exceptions can embed the request URI — log
            // the type and a redacted message, never the raw throwable. The frame-only stack
            // summary is PHI-safe and keeps a message-less NPE diagnosable (no more "error=null"
            // with zero frames).
            logger.error("Unexpected error in EFormBrowserRenderPageServlet: type={} error={} at={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())),
                    RenderLogRedaction.stackSummary(e));
            if (!response.isCommitted()) {
                try {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "An internal error occurred. Please try again or contact your system administrator.");
                } catch (IOException io) {
                    // Client went away before we could send the error page; nothing more to do.
                    logger.debug("EFormBrowserRenderPageServlet could not send error response (client likely disconnected): {}",
                            RenderLogRedaction.redactUrls(String.valueOf(io.getMessage())));
                }
            }
        }
    }

    /**
     * Session-auth path for non-browser callers: returns the logged-in user only when the request
     * carries an authenticated session with provider + security context and {@code _eform} read
     * privilege; returns null (logging the reason) on any denial.
     */
    private static LoggedInInfo authorizedEformReadRequest(
            HttpServletRequest request, int formDataId) {
        HttpSession session = request.getSession(false);
        LoggedInInfo loggedInInfo = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProvider() == null || loggedInInfo.getLoggedInSecurity() == null) {
            logger.warn("Saved eForm PDF request rejected: no authenticated session was present");
            return null;
        }

        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        EFormData eFormData = SpringUtils.getBean(EFormDataDao.class).find(formDataId);
        if (eFormData == null) {
            logger.warn("Saved eForm PDF request rejected: requested eForm was not found");
            return null;
        }
        String demographicId = eFormData.getDemographicId() == null
                ? null : String.valueOf(eFormData.getDemographicId());
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, "_eform", SecurityInfoManager.READ, demographicId)) {
            logger.warn("Saved eForm PDF request rejected: authenticated session lacks _eform read privilege");
            return null;
        }

        return loggedInInfo;
    }

    /**
     * Test-only convenience overload. With no request it can only ever produce
     * {@code connect-src 'none'}, so it cannot express the browser-render contract — see the
     * two-argument method for the real policy.
     */
    static String buildContentSecurityPolicy(boolean browserRender) {
        return buildContentSecurityPolicy(browserRender, null);
    }

    /**
     * Builds the response Content-Security-Policy.
     *
     * <p>Legacy server-side rendering blocks all scripts ({@code script-src 'none'}); the
     * browser-render path must allow same-origin inline/eval scripts and {@code blob:}/{@code data:}
     * images because the eForm is a JavaScript-built document the headless browser has to execute to
     * capture it.</p>
     *
     * <p>Both policies set {@code base-uri 'none'}, {@code form-action 'none'} and
     * {@code frame-ancestors 'none'}, so a stored form cannot retarget relative URLs, submit
     * anywhere, or be framed. They differ on fetch origins: the legacy policy is {@code 'self'},
     * while the browser-render policy narrows {@code connect-src} to the single exact loopback
     * APCache endpoint — and to {@code 'none'} when that endpoint cannot be safely formed, so an
     * unparseable origin disables the bridge rather than widening it.</p>
     */
    static String buildContentSecurityPolicy(
            boolean browserRender, HttpServletRequest request) {
        if (!browserRender) {
            return "default-src 'self'; script-src 'none'; object-src 'none'; base-uri 'none'; "
                    + "form-action 'none'; frame-ancestors 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:";
        }
        String connectSource = "'none'";
        if (request != null
                && EFormRendererRequestAuthorization.isLoopback(request.getServerName())) {
            try {
                int port = request.getServerPort();
                java.net.URI endpoint = new java.net.URI(
                        request.getScheme(),
                        null,
                        request.getServerName(),
                        port,
                        request.getContextPath() + "/EFormApCacheForPdfGenerationServlet",
                        null,
                        null);
                connectSource = endpoint.toASCIIString();
            } catch (java.net.URISyntaxException ignored) {
                // Fail closed: APCache is disabled when a safe exact endpoint cannot be formed.
            }
        }
        return "default-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                + "object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; "
                + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
                + "font-src 'self' data:; connect-src " + connectSource;
    }

    /**
     * Parses the {@code fdid} parameter. Returns null <em>after</em> sending a 400 response when it
     * is non-numeric, so the caller must stop processing on a null return.
     */
    private static Integer parseFormDataId(String id, HttpServletResponse response) throws IOException {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid parameter: fdid must be a valid number");
            return null;
        }
    }
}
