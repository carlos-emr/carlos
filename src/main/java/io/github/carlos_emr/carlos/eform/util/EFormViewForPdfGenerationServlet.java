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
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * The purpose of this servlet is to allow a local process to convert an eform html page into a pdf file.
 */
public final class EFormViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String IMAGE_RENDERING_SERVLET_PATH = "/imageRenderingServlet";
    private static final String PDF_SIGNATURE_SERVLET_PATH = "/EFormSignatureViewForPdfGenerationServlet";
    private static final String DIGITAL_SIGNATURE_ID_PARAM = "digitalSignatureId";

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            // ensure it's a local machine request... no one else should be calling this servlet.
            String remoteAddress = request.getRemoteAddr();
            logger.debug("EFormViewForPdfGenerationServlet request from : {}", remoteAddress);
            if (!"127.0.0.1".equals(remoteAddress) && !"0:0:0:0:0:0:0:1".equals(remoteAddress) && !"::1".equals(remoteAddress)) {
                logger.warn("Unauthorised request made to EFormViewForPdfGenerationServlet from address : {}", remoteAddress);
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return; // Critical: stop execution for non-localhost requests
            }

            // Add security headers to restrict content capabilities (no scripts, no plugins)
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'none'; object-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:");

            boolean prepareForFax = "true".equals(request.getParameter("prepareForFax"));
            String id = request.getParameter("fdid");
            String providerId = request.getParameter("providerId");

            // Validate required parameters
            if (id == null || id.trim().isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: fdid");
                return;
            }

            // Validate id is a valid integer
            int formDataId;
            try {
                formDataId = Integer.parseInt(id);
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid parameter: fdid must be a valid number");
                return;
            }

            EForm eForm = new EForm(id);
            eForm.setSignatureCode(request.getContextPath(), request.getHeader("User-Agent"), eForm.getDemographicNo(), providerId);
            eForm.setContextPath(request.getContextPath());
            String projectHome = CarlosProperties.getInstance().getProperty("project_home");

            EFormValueDao efvDao = (EFormValueDao) SpringUtils.getBean(EFormValueDao.class);
            List<EFormValue> eFormValues = efvDao.findByFormDataId(formDataId);
            for (EFormValue value : eFormValues) {
                if (value.getVarName().equals("Letter")) {
                    String html = value.getVarValue();
                    html = html.replace(IMAGE_RENDERING_SERVLET_PATH, PDF_SIGNATURE_SERVLET_PATH);
                    if (prepareForFax) {
                        html = "<div style=\"position:relative\"><div style=\"position:absolute; margin-top:35px;\">" + html + "</div></div>";
                    }
                    html = "<html><body style='width:640px;'>" + html + "</body></html>";
                    eForm.setFormHtml(html);
                }
                if (value.getVarName().equals("signatureValue")) {

                    // Checking to see if there are any parameters for the signature in the html.
                    String html = eForm.getFormHtml();
                    String signatureInit = "signatureControl.initialize\\s*\\(\\s*\\{\\s*eform:true,\\s+height:(\\d+),\\s+width:(\\d+),\\s+top:(\\d+),\\s+left:(\\d+)\\s*\\}\\s*\\)";
                    Pattern pattern = Pattern.compile(signatureInit);
                    Matcher matcher = pattern.matcher(html);
                    boolean matchFound = matcher.find();
                    if (matchFound && matcher.groupCount() == 4) {
                        String sign = normalizePdfSignatureUrl(value.getVarValue(), request.getContextPath());
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

            eForm.setFormHtml(eForm.getFormHtml().replace("../eform/displayImage", "/" + projectHome + "/EFormImageViewForPdfGenerationServlet"));
            eForm.setFormHtml(eForm.getFormHtml().replace("${oscar_image_path}", "/" + projectHome + "/EFormImageViewForPdfGenerationServlet?imagefile="));
            eForm.setFormHtml(eForm.getFormHtml().replace("$%7Boscar_image_path%7D", "/" + projectHome + "/EFormImageViewForPdfGenerationServlet?imagefile="));
            eForm.setFormHtml(eForm.getFormHtml().replace("<div class=\"DoNotPrint\" style=\"", "<div class=\"DoNotPrint\" style=\"display:none;"));
            eForm.setImagePath(request.getContextPath());
            eForm.setNowDateTime();

            response.setContentType("text/html;charset=UTF-8");
            response.getOutputStream().write(eForm.getFormHtml().getBytes(Charset.forName("UTF-8")));
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
