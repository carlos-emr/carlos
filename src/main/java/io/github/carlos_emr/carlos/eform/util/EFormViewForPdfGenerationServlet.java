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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.HtmlResponse;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The purpose of this servlet is to allow a local process to convert an eform html page into a pdf file.
 */
public final class EFormViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String IMAGE_RENDERING_SERVLET_PATH = "/imageRenderingServlet";
    private static final String SIGNATURE_VIEW_SERVLET_NAME = "EFormSignatureViewForPdfGenerationServlet";
    private static final String PDF_SIGNATURE_SERVLET_PATH = "/" + SIGNATURE_VIEW_SERVLET_NAME;
    private static final String DIGITAL_SIGNATURE_ID_PARAM = "digitalSignatureId";
    private static final String PROVIDER_ID_PARAM = "providerId";
    /** Render-scoped grant parameter redeemed against {@link EFormRenderTokenService}. */
    static final String RENDER_TOKEN_PARAM = "renderToken";
    private static final String IMAGE_VIEW_SERVLET_NAME = "EFormImageViewForPdfGenerationServlet";

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

            String html = buildPdfHtmlForFdid(formDataId, request.getContextPath(), request.getHeader("User-Agent"), providerId, prepareForFax, renderToken);

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
     * Builds the stored eForm HTML used by the browser PDF renderer.
     *
     * @param formDataId saved eForm data identifier
     * @param contextPath current servlet context path used for local asset URLs
     * @param userAgent renderer user agent used by existing signature setup logic
     * @param providerId provider number used for provider-scoped signature rendering
     * @param prepareForFax true when fax preview positioning wrappers are required
     * @param renderToken render-scoped grant appended to local asset URLs so the sessionless
     *        render browser can fetch its images; null for the session-authenticated path
     * @return normalized HTML ready for the browser renderer
     */
    public static String buildPdfHtmlForFdid(int formDataId, String contextPath, String userAgent, String providerId, boolean prepareForFax, String renderToken) {
        EForm eForm = new EForm(String.valueOf(formDataId));
        eForm.setSignatureCode(contextPath, userAgent, eForm.getDemographicNo(), providerId);
        eForm.setContextPath(contextPath);

        EFormValueDao efvDao = SpringUtils.getBean(EFormValueDao.class);
        List<EFormValue> eFormValues = efvDao.findByFormDataId(formDataId);
        String projectHome = CarlosProperties.getInstance().getProperty("project_home", "");

        return buildPdfHtml(eForm, eFormValues, contextPath, projectHome, prepareForFax, renderToken);
    }

    /**
     * Redeems the single-use render grant carried by a browser-render request.
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

    // normalizePdfSignatureUrl constrains signature URLs to local servlet paths with numeric ids before markup insertion.
    @SuppressFBWarnings(value = "MODIFICATION_AFTER_VALIDATION", justification = "normalizePdfSignatureUrl constrains the signature URL to a local servlet path with a numeric id, and buildSignatureImageMarkup HTML-attribute-encodes it before insertion.")
    static String buildPdfHtml(EForm eForm, List<EFormValue> eFormValues, String contextPath, String projectHome, boolean prepareForFax, String renderToken) {
        applyLetterHtml(eForm, eFormValues, prepareForFax);
        applySignatureHtml(eForm, eFormValues, contextPath);

        String html = eForm.getFormHtml();
        html = html.replace("../eform/displayImage", imageViewServletBase(projectHome));
        html = html.replace("${oscar_image_path}", imageViewServletImagePrefix(projectHome));
        html = html.replace("$%7Boscar_image_path%7D", imageViewServletImagePrefix(projectHome));
        html = html.replace("<div class=\"DoNotPrint\" style=\"", "<div class=\"DoNotPrint\" style=\"display:none;");
        eForm.setFormHtml(html);
        eForm.setImagePath(contextPath);
        html = eForm.getFormHtml();
        String imageViewServletPath = contextPath + "/" + IMAGE_VIEW_SERVLET_NAME;
        html = html.replace(contextPath + "/eform/displayImage", imageViewServletPath);
        html = html.replace("/eform/displayImage", imageViewServletPath);
        html = appendRenderTokenToAssetUrls(html, renderToken);
        eForm.setFormHtml(html);
        eForm.setNowDateTime();
        return eForm.getFormHtml();
    }

    /**
     * Appends the render-scoped grant to every eForm asset-servlet URL — background/asset images
     * ({@code EFormImageViewForPdfGenerationServlet}) and digital signatures
     * ({@code EFormSignatureViewForPdfGenerationServlet}) — so the sessionless render browser can
     * fetch each subresource under the same grant. Every such URL has been normalized to
     * {@code .../<servlet>?<params>} by this point, so one insertion at the {@code ?} boundary covers
     * all forms ({@code ${oscar_image_path}}, {@code /eform/displayImage}, and the signature path).
     * No-op on the session-authenticated path (null token). The token is a request parameter, so it
     * is URI-component encoded before it is spliced into the asset URLs (which live inside HTML
     * {@code src} attributes): a well-formed URL-safe-base64 grant passes through unchanged, while any
     * injected metacharacter is neutralized for both the query-string and HTML-attribute contexts.
     */
    private static String appendRenderTokenToAssetUrls(String html, String renderToken) {
        if (renderToken == null || renderToken.isEmpty()) {
            return html;
        }
        String tokenPrefix = "?" + RENDER_TOKEN_PARAM + "=" + SafeEncode.forUriComponent(renderToken) + "&";
        return html
                .replace(IMAGE_VIEW_SERVLET_NAME + "?", IMAGE_VIEW_SERVLET_NAME + tokenPrefix)
                .replace(SIGNATURE_VIEW_SERVLET_NAME + "?", SIGNATURE_VIEW_SERVLET_NAME + tokenPrefix);
    }

    private static void applyLetterHtml(EForm eForm, List<EFormValue> eFormValues, boolean prepareForFax) {
        for (EFormValue value : eFormValues) {
            if (!"Letter".equals(value.getVarName())) {
                continue;
            }
            String html = value.getVarValue();
            html = html.replace(IMAGE_RENDERING_SERVLET_PATH, PDF_SIGNATURE_SERVLET_PATH);
            if (prepareForFax) {
                html = "<div style=\"position:relative\"><div style=\"position:absolute; margin-top:35px;\">" + html + "</div></div>";
            }
            eForm.setFormHtml("<html><body style='width:640px;'>" + html + "</body></html>");
            return;
        }
    }

    private static void applySignatureHtml(EForm eForm, List<EFormValue> eFormValues, String contextPath) {
        for (EFormValue value : eFormValues) {
            if (!"signatureValue".equals(value.getVarName())) {
                continue;
            }
            String html = eForm.getFormHtml();
            String signatureInit = "signatureControl.initialize\\s*\\(\\s*\\{\\s*eform:true,\\s+height:(\\d+),\\s+width:(\\d+),\\s+top:(\\d+),\\s+left:(\\d+)\\s*\\}\\s*\\)";
            Pattern pattern = Pattern.compile(signatureInit);
            Matcher matcher = pattern.matcher(html);
            boolean matchFound = matcher.find();
            if (matchFound && matcher.groupCount() == 4) {
                String sign = normalizePdfSignatureUrl(value.getVarValue(), contextPath);
                if (sign == null) {
                    logger.warn("Skipping invalid signature URL while preparing eForm PDF");
                    continue;
                }
                String left = matcher.group(4), top = matcher.group(3), width = matcher.group(2), height = matcher.group(1);
                eForm.setFormHtml(html.replace("<div id=\"signatureDisplay\"></div>",
                        buildSignatureImageMarkup(sign, left, top, width, height)));
            }
        }
    }

    static String buildContentSecurityPolicy(boolean browserRender) {
        if (!browserRender) {
            return "default-src 'self'; script-src 'none'; object-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:";
        }
        return "default-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; object-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'";
    }

    private static String imageViewServletBase(String projectHome) {
        // An empty/blank project_home (root context, or unconfigured) must not produce a leading
        // "//…" — the browser reads that as a protocol-relative URL to an external host, which would
        // make every eForm asset fetch fail.
        if (projectHome == null || projectHome.isBlank()) {
            return "/" + IMAGE_VIEW_SERVLET_NAME;
        }
        return "/" + projectHome + "/" + IMAGE_VIEW_SERVLET_NAME;
    }

    private static String imageViewServletImagePrefix(String projectHome) {
        return imageViewServletBase(projectHome) + "?imagefile=";
    }

    static String normalizePdfSignatureUrl(String rawUrl, String contextPath) {
        if (rawUrl == null) {
            return null;
        }

        String rewritten = rawUrl.trim().replace(IMAGE_RENDERING_SERVLET_PATH, PDF_SIGNATURE_SERVLET_PATH);
        if (rewritten.isEmpty() || containsUnsafeHtmlAttributeCharacters(rewritten)) {
            return null;
        }

        final URI uri;
        try {
            uri = new URI(rewritten);
        } catch (URISyntaxException e) {
            return null;
        }

        if (uri.isOpaque() || uri.getScheme() != null || uri.getHost() != null || uri.getRawAuthority() != null || uri.getFragment() != null) {
            return null;
        }

        String normalizedContextPath = normalizeContextPath(contextPath);
        String uriPath = uri.getPath();
        String contextScopedPath = normalizedContextPath + PDF_SIGNATURE_SERVLET_PATH;

        if (!PDF_SIGNATURE_SERVLET_PATH.equals(uriPath) && !contextScopedPath.equals(uriPath)) {
            return null;
        }

        String digitalSignatureId = extractDigitsQueryParam(uri.getRawQuery(), DIGITAL_SIGNATURE_ID_PARAM);
        if (digitalSignatureId == null) {
            return null;
        }

        if (contextScopedPath.equals(uriPath)) {
            return contextScopedPath + "?" + DIGITAL_SIGNATURE_ID_PARAM + "=" + digitalSignatureId;
        }
        return PDF_SIGNATURE_SERVLET_PATH + "?" + DIGITAL_SIGNATURE_ID_PARAM + "=" + digitalSignatureId;
    }

    private static Integer parseFormDataId(String id, HttpServletResponse response) throws IOException {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid parameter: fdid must be a valid number");
            return null;
        }
    }

    static String buildSignatureImageMarkup(String signatureUrl, String left, String top, String width, String height) {
        return String.format(
                "<div id=\"signatureDisplay\"><img src=\"%s\" style=\"position:absolute;left:%s;top:%s;width:%s;height:%s;\" /> </div>",
                SafeEncode.forHtmlAttribute(signatureUrl), left, top, width, height);
    }

    private static boolean containsUnsafeHtmlAttributeCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '"' || current == '\'' || current == '<' || current == '>' || current == '\r' || current == '\n') {
                return true;
            }
        }
        return false;
    }

    private static String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        return contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath;
    }

    private static String extractDigitsQueryParam(String rawQuery, String parameterName) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parameterName.equals(parts[0]) && parts[1].matches("\\d+")) {
                return parts[1];
            }
        }

        return null;
    }
}
