/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Composes the normalized eForm HTML that the PDF renderer captures.
 *
 * <p>Extracted from {@link EFormBrowserRenderPageServlet} so the HTTP concerns (loopback gate,
 * token redemption, session auth, CSP headers, response writing) stay in the servlet while the
 * stored-form HTML assembly — letter positioning, signature-image splicing, legacy image-path
 * rewriting, and render-token propagation onto asset URLs — lives in one testable place.</p>
 *
 * <p>All methods are stateless functions of their inputs; the class is a pure composer with no
 * request or session dependency.</p>
 */
public final class EformRenderPdfHtmlComposer {

    private static final Logger logger = MiscUtils.getLogger();

    private static final String IMAGE_RENDERING_SERVLET_PATH = "/imageRenderingServlet";
    private static final String SIGNATURE_VIEW_SERVLET_NAME = "EFormSignatureViewForPdfGenerationServlet";
    private static final String PDF_SIGNATURE_SERVLET_PATH = "/" + SIGNATURE_VIEW_SERVLET_NAME;
    private static final String DIGITAL_SIGNATURE_ID_PARAM = "digitalSignatureId";
    private static final String IMAGE_VIEW_SERVLET_NAME = "EFormImageViewForPdfGenerationServlet";

    private EformRenderPdfHtmlComposer() {
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
     * Assembles the final render HTML for a loaded {@link EForm}: injects the stored letter content,
     * splices any stored signature image, rewrites the legacy image references
     * ({@code ../eform/displayImage}, {@code ${oscar_image_path}}, {@code /eform/displayImage}) to the
     * {@code EFormImageViewForPdfGenerationServlet} route, hides print-suppressed blocks, and — when
     * {@code renderToken} is non-null — carries the render grant onto every asset URL.
     *
     * <p>Ordering is load-bearing: letter and signature content are injected <em>before</em> the
     * image-path rewrites so the rewrites also cover the freshly injected markup.</p>
     *
     * @param renderToken render-scoped grant spliced onto asset URLs, or null for the
     *        session-authenticated (non-browser) path
     * @return the normalized HTML ready for capture
     */
    // normalizePdfSignatureUrl constrains signature URLs to local servlet paths with numeric ids before markup insertion.
    @SuppressFBWarnings(value = "MODIFICATION_AFTER_VALIDATION", justification = "normalizePdfSignatureUrl constrains the signature URL to a local servlet path with a numeric id, and buildSignatureImageMarkup HTML-attribute-encodes it before insertion.")
    static String buildPdfHtml(EForm eForm, List<EFormValue> eFormValues, String contextPath, String projectHome, boolean prepareForFax, String renderToken) {
        applyLetterHtml(eForm, eFormValues, prepareForFax);
        applySignatureHtml(eForm, eFormValues, contextPath);

        String html = eForm.getFormHtml();
        html = html.replace("../eform/displayImage", imageViewServletBase(projectHome, contextPath));
        html = html.replace("${oscar_image_path}", imageViewServletImagePrefix(projectHome, contextPath));
        html = html.replace("$%7Boscar_image_path%7D", imageViewServletImagePrefix(projectHome, contextPath));
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
        String tokenPrefix = "?" + EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM
                + "=" + SafeEncode.forUriComponent(renderToken) + "&";
        return html
                .replace(IMAGE_VIEW_SERVLET_NAME + "?", IMAGE_VIEW_SERVLET_NAME + tokenPrefix)
                .replace(SIGNATURE_VIEW_SERVLET_NAME + "?", SIGNATURE_VIEW_SERVLET_NAME + tokenPrefix);
    }

    /**
     * Replaces the form body with the stored {@code Letter} content, remapping its legacy
     * signature-render path and, for fax preview, wrapping it in the absolute-positioned offset the
     * fax cover page expects. No-op when the form has no {@code Letter} value.
     */
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

    /**
     * Splices a stored signature image in place of the JS signature pad's {@code signatureDisplay}
     * target, reusing the geometry declared in the form's {@code signatureControl.initialize(...)}
     * call. Logs a warning and skips the signature when the stored URL fails
     * {@link #normalizePdfSignatureUrl} validation.
     */
    // MODIFICATION_AFTER_VALIDATION: the regex match on the form HTML only extracts signature-pad
    // geometry; the security validation is on the signature URL (normalizePdfSignatureUrl returns
    // null → skip), and buildSignatureImageMarkup HTML-attribute-encodes it before insertion.
    @SuppressFBWarnings(value = "MODIFICATION_AFTER_VALIDATION", justification = "the html.replace only substitutes a fixed placeholder div; the signature URL is validated by normalizePdfSignatureUrl and encoded by buildSignatureImageMarkup before insertion")
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

    /**
     * Validates and canonicalizes a stored signature reference into a safe, local
     * {@code EFormSignatureViewForPdfGenerationServlet?digitalSignatureId=...} URL with a numeric id.
     *
     * <p>Security boundary: returns null unless the input is a purely local, root-relative reference
     * to that servlet with a numeric id. Any scheme, host, authority, fragment, opaque form,
     * HTML-attribute metacharacter, or non-numeric id is rejected — so a non-null result is always
     * safe to place in an {@code src} attribute (after {@link #buildSignatureImageMarkup} applies
     * HTML-attribute encoding).</p>
     */
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

    /** Builds the positioned signature {@code <img>}, HTML-attribute-encoding the (already validated) URL. */
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

    private static String imageViewServletBase(String projectHome, String contextPath) {
        // Prefer the configured project_home. When it is blank/unset, fall back to the servlet
        // context path so a non-root deployment (e.g. /carlos) still resolves — never emit a leading
        // "//…" (a protocol-relative URL to an external host) or drop the context prefix entirely.
        String base = (projectHome == null || projectHome.isBlank())
                ? normalizeContextPath(contextPath)
                : "/" + projectHome.trim();
        return base + "/" + IMAGE_VIEW_SERVLET_NAME;
    }

    private static String imageViewServletImagePrefix(String projectHome, String contextPath) {
        return imageViewServletBase(projectHome, contextPath) + "?imagefile=";
    }

    private static String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        return contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath;
    }

    /**
     * Returns the first digit-only value of {@code parameterName} in the raw query string, or null.
     * The digit-only constraint keeps a metacharacter out of the id that is spliced back into the
     * canonicalized signature URL.
     */
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
