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
    private static final String PDF_SIGNATURE_SERVLET_PATH = "/EFormSignatureViewForPdfGenerationServlet";
    private static final String DIGITAL_SIGNATURE_ID_PARAM = "digitalSignatureId";
    private static final String PROVIDER_ID_PARAM = "providerId";

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
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", buildContentSecurityPolicy(browserRender));

            boolean prepareForFax = "true".equals(request.getParameter("prepareForFax"));
            String id = request.getParameter("fdid");
            String providerId = browserRender ? authorizedRendererProviderId(request) : request.getParameter(PROVIDER_ID_PARAM);
            if (browserRender && providerId == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Renderer request requires an authenticated matching provider session");
                return;
            }

            if (id == null || id.trim().isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: fdid");
                return;
            }

            Integer formDataId = parseFormDataId(id, response);
            if (formDataId == null) {
                return;
            }

            String html = buildPdfHtmlForFdid(formDataId, request.getContextPath(), request.getHeader("User-Agent"), providerId, prepareForFax);

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
     * @return normalized HTML ready for the browser renderer
     */
    public static String buildPdfHtmlForFdid(int formDataId, String contextPath, String userAgent, String providerId, boolean prepareForFax) {
        EForm eForm = new EForm(String.valueOf(formDataId));
        eForm.setSignatureCode(contextPath, userAgent, eForm.getDemographicNo(), providerId);
        eForm.setContextPath(contextPath);

        EFormValueDao efvDao = SpringUtils.getBean(EFormValueDao.class);
        List<EFormValue> eFormValues = efvDao.findByFormDataId(formDataId);
        String projectHome = CarlosProperties.getInstance().getProperty("project_home");

        return buildPdfHtml(eForm, eFormValues, contextPath, projectHome, prepareForFax);
    }

    private static String authorizedRendererProviderId(HttpServletRequest request) {
        String providerId = request.getParameter(PROVIDER_ID_PARAM);
        String canonicalProviderId = providerId == null ? "" : providerId.trim();
        if (canonicalProviderId.isEmpty()) {
            logger.warn("Renderer request rejected: missing providerId for authenticated browser render");
            return null;
        }

        HttpSession session = request.getSession(false);
        LoggedInInfo loggedInInfo = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProvider() == null || loggedInInfo.getLoggedInSecurity() == null) {
            logger.warn("Renderer request rejected: no authenticated session was present");
            return null;
        }

        String sessionProviderId = loggedInInfo.getLoggedInProviderNo();
        if (sessionProviderId == null || !sessionProviderId.equals(canonicalProviderId)) {
            logger.warn("Renderer request rejected: provider mismatch for authenticated session");
            return null;
        }

        return canonicalProviderId;
    }

    // normalizePdfSignatureUrl constrains signature URLs to local servlet paths with numeric ids before markup insertion.
    @SuppressFBWarnings(value = "MODIFICATION_AFTER_VALIDATION", justification = "normalizePdfSignatureUrl constrains the signature URL to a local servlet path with a numeric id, and buildSignatureImageMarkup HTML-attribute-encodes it before insertion.")
    static String buildPdfHtml(EForm eForm, List<EFormValue> eFormValues, String contextPath, String projectHome, boolean prepareForFax) {
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
        String imageViewServletPath = contextPath + "/EFormImageViewForPdfGenerationServlet";
        html = html.replace(contextPath + "/eform/displayImage", imageViewServletPath);
        html = html.replace("/eform/displayImage", imageViewServletPath);
        eForm.setFormHtml(html);
        eForm.setNowDateTime();
        return eForm.getFormHtml();
    }

    private static void applyLetterHtml(EForm eForm, List<EFormValue> eFormValues, boolean prepareForFax) {
        String letterHtml = null;
        for (EFormValue value : eFormValues) {
            if (!"Letter".equals(value.getVarName())) {
                continue;
            }
            letterHtml = value.getVarValue();
        }
        if (letterHtml == null) {
            return;
        }

        String html = letterHtml.replace(IMAGE_RENDERING_SERVLET_PATH, PDF_SIGNATURE_SERVLET_PATH);
        if (prepareForFax) {
            html = "<div style=\"position:relative\"><div style=\"position:absolute; margin-top:35px;\">" + html + "</div></div>";
        }
        eForm.setFormHtml("<html><body style='width:640px;'>" + html + "</body></html>");
    }

    private static void applySignatureHtml(EForm eForm, List<EFormValue> eFormValues, String contextPath) {
        for (EFormValue value : eFormValues) {
            if ("signatureValue".equals(value.getVarName())) {
                String html = eForm.getFormHtml();
                String signatureInit = "signatureControl.initialize\\s*\\(\\s*\\{\\s*eform:true,\\s+height:(\\d+),\\s+width:(\\d+),\\s+top:(\\d+),\\s+left:(\\d+)\\s*\\}\\s*\\)";
                Pattern pattern = Pattern.compile(signatureInit);
                Matcher matcher = pattern.matcher(html);
                boolean matchFound = matcher.find();
                if (matchFound && matcher.groupCount() == 4) {
                    String sign = normalizePdfSignatureUrl(value.getVarValue(), contextPath);
                    if (sign == null) {
                        logger.warn("Skipping invalid signature URL while preparing eForm PDF");
                    } else {
                        String left = matcher.group(4), top = matcher.group(3), width = matcher.group(2), height = matcher.group(1);
                        eForm.setFormHtml(html.replace("<div id=\"signatureDisplay\"></div>",
                                buildSignatureImageMarkup(sign, left, top, width, height)));
                    }
                }
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
        return "/" + projectHome + "/EFormImageViewForPdfGenerationServlet";
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
