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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.actions.DisplayImage2Action;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Composes the normalized eForm HTML that the PDF renderer captures.
 *
 * <p>Extracted from {@link EFormBrowserRenderPageServlet} so the HTTP concerns (loopback gate,
 * token redemption, session auth, CSP headers, response writing) stay in the servlet while the
 * stored-form HTML assembly — letter positioning, signature-image splicing, legacy image-path
 * rewriting, editor stripping, and server-side population of the render grant — lives in one
 * testable place. The render capability is never appended to subresource URLs.</p>
 *
 * <p>The class holds no state of its own and has no request or session dependency, so it is safe
 * to call off a servlet thread. It is not, however, a pure function of its inputs:
 * {@link #buildPdfHtmlForFdid} loads the eForm and its values from the database, and
 * {@code buildPdfHtml} advances the passed {@link io.github.carlos_emr.carlos.eform.data.EForm}
 * through an ordered set/get pipeline whose interleaved EForm mutators
 * ({@code setImagePath}, {@code setNowDateTime}) make the step ordering load-bearing — later
 * rewrites operate on URLs the earlier mutators inject.</p>
 */
public final class EFormRenderPdfHtmlComposer {

    private static final Logger logger = MiscUtils.getLogger();

    private static final String IMAGE_RENDERING_SERVLET_PATH = "/imageRenderingServlet";
    private static final String SIGNATURE_VIEW_SERVLET_NAME = "EFormSignatureViewForPdfGenerationServlet";
    private static final String PDF_SIGNATURE_SERVLET_PATH = "/" + SIGNATURE_VIEW_SERVLET_NAME;
    private static final String DIGITAL_SIGNATURE_ID_PARAM = "digitalSignatureId";
    private static final String IMAGE_VIEW_SERVLET_NAME = "EFormImageViewForPdfGenerationServlet";
    private static final Pattern IMAGE_ASSET_URL_PATTERN = Pattern.compile(
            Pattern.quote(IMAGE_VIEW_SERVLET_NAME) + "\\?([^\\s\\\"'<>)]*)");
    private static final String SIGNATURE_MARKER = "${oscar_signature_code}";
    private static final String RENDER_PROFILE_PROPERTY =
            "eform_pdf_browser_saved_view_profile_enabled";
    private static final Pattern APCACHE_LOOKUP_PATTERN = Pattern.compile(
            "\\.lookup\\s*\\(\\s*(['\"])([A-Za-z0-9_$.-]{1,128})\\1\\s*\\)");
    private static final Pattern APCACHE_VALUES_PATTERN = Pattern.compile(
            "values\\s*:\\s*\\[([^]]{0,4096})]", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_AP_KEY_PATTERN = Pattern.compile(
            "(['\"])([A-Za-z0-9_$.-]{1,128})\\1");
    private static final int MAX_CLINIC_SCRIPT_BYTES = 1_048_576;
    /**
     * Editor/dialog libraries stripped from the render surface. Matched against both the script URL
     * and the {@code imagefile=} asset name, because clinic forms reference these either as webapp
     * paths ({@code /library/eforms/printControl.js}) or through the image servlet
     * ({@code ...?imagefile=editControl2.js}). {@code APCache.js} is intentionally absent — it
     * populates clinical content.
     */
    private static final Set<String> INTERACTIVE_ONLY_SCRIPTS = Set.of(
            "editcontrol2.js", "editcontrol.js", "printcontrol.js", "faxcontrol.js",
            "imagecontrol.js", "signaturecontrol.js", "signaturecontrol.jsp", "signaturecontrol",
            "eform_floating_toolbar.js", "eform_floating_toolbar");
    private static final String EDITOR_BOOTSTRAP_CALL = "insertEditControl()";
    /**
     * Case-insensitive matchers for {@link #hardenLetterHtml}, deliberately ASCII-only.
     *
     * <p>These decide whether attacker-influenced markup is stripped, so they must fold exactly the
     * way an HTML parser does — over ASCII and nothing else. {@code CASE_INSENSITIVE} without
     * {@code UNICODE_CASE} gives precisely that. The earlier {@code toLowerCase(Locale.ROOT)}
     * version was a case-fold in a trust path (the class CLAUDE.md tracks under issue #2496): full
     * Unicode lowering can map non-ASCII code points onto ASCII letters and can change string
     * length, so a filter built on it decides on a string the browser never sees.</p>
     */
    private static final Pattern EVENT_HANDLER_ATTRIBUTE =
            Pattern.compile("on.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_BEARING_ATTRIBUTE =
            Pattern.compile("href|src|action", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_SCHEME =
            Pattern.compile("\\Ajavascript:", Pattern.CASE_INSENSITIVE);

    private EFormRenderPdfHtmlComposer() {
    }

    /**
     * Builds the stored eForm HTML used by the browser PDF renderer.
     *
     * @param formDataId saved eForm data identifier
     * @param contextPath current servlet context path used for local asset URLs
     * @param renderToken render-scoped bootstrap capability used server-side to authorize the
     *        exact referenced image/APCache set; never appended to subresource URLs
     * @return normalized HTML ready for the browser renderer
     * @throws IllegalStateException if the fdid has no stored form HTML, or a stored, non-blank
     *         signature cannot be spliced into the form HTML; a signed document must never render
     *         (and so fax/archive) unsigned
     */
    public static String buildPdfHtmlForFdid(int formDataId, String contextPath, EFormRenderTokenService.RenderToken renderToken) {
        EForm eForm = new EForm(String.valueOf(formDataId));

        EFormValueDao efvDao = SpringUtils.getBean(EFormValueDao.class);
        List<EFormValue> eFormValues = java.util.Objects.requireNonNullElse(
                efvDao.findByFormDataId(formDataId), List.of());
        String projectHome = CarlosProperties.getInstance().getProperty("project_home", "");

        if (logger.isDebugEnabled()) {
            // fdid + value count only; never the render token or any stored eForm field value.
            logger.debug("Composing eForm PDF render HTML: fdid={} storedValues={}",
                    formDataId, eFormValues.size());
        }

        try {
            return buildPdfHtml(eForm, eFormValues, contextPath, projectHome, renderToken);
        } catch (IllegalStateException e) {
            // fdid is a PHI-correlating identifier (joins back to the patient's saved eForm data);
            // sanitize it before logging alongside the (already PHI-free) failure reason.
            logger.error("eForm PDF composition failed: fdid={} reason={}", LogSafe.sanitize(String.valueOf(formDataId)), e.getMessage());
            throw e;
        }
    }

    /**
     * Assembles the final render HTML for a loaded {@link EForm}: injects the stored letter content,
     * splices any stored signature image, rewrites the legacy image references
     * ({@code ../eform/displayImage}, {@code ${oscar_image_path}}, {@code /eform/displayImage}) to the
     * {@code EFormImageViewForPdfGenerationServlet} route, hides print-suppressed blocks, and
     * authorizes the exact referenced image/APCache set without exposing the token to subresources.
     *
     * <p>Ordering is load-bearing: letter and signature content are injected <em>before</em> the
     * image-path rewrites so the rewrites also cover the freshly injected markup.</p>
     *
     * @param renderToken render-scoped bootstrap capability used only for server-side grant
     *        population, or null for the session-authenticated (non-browser) path
     * @return the normalized HTML ready for capture
     * @throws IllegalStateException if the fdid has no stored form HTML or a stored, non-blank
     *         signature cannot be spliced into it
     */
    static String buildPdfHtml(EForm eForm, List<EFormValue> eFormValues, String contextPath, String projectHome, EFormRenderTokenService.RenderToken renderToken) {
        applyLetterHtml(eForm, eFormValues);
        if (eForm.getFormHtml() == null) {
            throw new IllegalStateException("eForm render failed: no stored form HTML for the requested fdid");
        }
        // Letter is a whole-document replacement, so every saved-view substitution and dependency
        // must happen after it. The interactive signature marker is deliberately neutralized rather
        // than passed to EForm.setSignatureCode(), which would mint preview/write state.
        eForm.setContextPath(contextPath);
        eForm.enableRenderNormalization();
        eForm.setFdid(eForm.getFdid());
        applySignatureHtml(eForm, eFormValues, contextPath);
        applyRendererViewProfile(eForm, contextPath, eForm.getFdid());

        String html = eForm.getFormHtml();
        // Normalize the legacy ".do" spelling FIRST (corpus forms hardcode
        // "../eform/displayImage.do?imagefile=…" in signature-stamp scripts): replacing the bare
        // variant alone would leave a stray ".do" glued onto the servlet name
        // ("…Servlet.do?…"), which misses the exact-match servlet mapping and 404s — failing the
        // render's content gate on forms that never even show a stamp.
        html = html.replace("../eform/displayImage.do", imageViewServletBase(projectHome, contextPath));
        html = html.replace("../eform/displayImage", imageViewServletBase(projectHome, contextPath));
        // Legacy eForms reference the calendar widget relative to the /eform/ viewer base
        // ("../share/..." -> /<context>/share/...); on the render servlet's path that same
        // reference resolves to the origin ROOT and 404s, leaving date-picker forms unstyled and
        // failing the render gates. Anchor it to the context explicitly.
        html = html.replace("../share/", contextPath + "/share/");
        html = replaceImagePathMarkerInAttributes(html, imageViewServletImagePrefix(projectHome, contextPath));
        html = html.replace("<div class=\"DoNotPrint\" style=\"", "<div class=\"DoNotPrint\" style=\"display:none;");
        eForm.setFormHtml(html);
        eForm.setImagePath(contextPath);
        html = eForm.getFormHtml();
        String imageViewServletPath = contextPath + "/" + IMAGE_VIEW_SERVLET_NAME;
        // Same ".do"-first ordering for the context-prefixed and bare display routes (the saved
        // instance html carries the context-prefixed spelling baked by the live display path).
        html = html.replace(contextPath + "/eform/displayImage.do", imageViewServletPath);
        html = html.replace(contextPath + "/eform/displayImage", imageViewServletPath);
        html = html.replace("/eform/displayImage.do", imageViewServletPath);
        html = html.replace("/eform/displayImage", imageViewServletPath);
        html = removeAbsentOptionalStamps(html);
        // The AP KEY is deliberately not added to the render grant: the payload is inlined here, so
        // the browser makes no APCache request for it, and granting a key nothing will ask for only
        // widens what the page may fetch.
        //
        // Whether the measurements may be embedded AT ALL is a separate question, and one the
        // renderer cannot answer for itself — it holds no identity. The initiator records it on the
        // grant, and a render whose grant is absent or silent embeds nothing.
        EFormRenderTokenService.RenderGrant renderGrant =
                renderToken == null ? null : EFormRenderTokenService.getInstance().peek(renderToken);
        html = LegacyMeasurementHistory.embed(html, eForm,
                renderGrant != null && renderGrant.allowsMeasurementHistory());
        Set<String> authorizedAssets = new HashSet<>(referencedImageFiles(html));
        Set<String> stampFiles = runtimeSignatureStampFiles(html, eForm, eFormValues);
        // Grant the stamp only when it exists. An absent one would 404 and land in the report as an
        // unexplained failed content resource; reporting it as its own condition instead lets the
        // approval page say what is actually missing.
        Set<String> presentStamps = existingImageFiles(stampFiles);
        authorizedAssets.addAll(presentStamps);
        if (!stampFiles.isEmpty() && presentStamps.isEmpty()) {
            html = markProviderStampMissing(html);
        }
        EFormRenderTokenService.getInstance().authorizeAssets(renderToken, authorizedAssets);
        Set<String> apKeys = new HashSet<>(referencedApCacheKeys(html));
        apKeys.addAll(referencedClinicScriptApCacheKeys(html));
        EFormRenderTokenService.getInstance().authorizeApKeys(renderToken, apKeys);
        eForm.setFormHtml(html);
        eForm.setNowDateTime();
        return eForm.getFormHtml();
    }

    /** Prefix legacy signature-stamp scripts concatenate a provider number onto. */
    private static final String SIGNATURE_STAMP_PREFIX = "consult_sig_";

    /**
     * The provider signature stamp a form will request at runtime, when it references one.
     *
     * <p>The render grant is built by scanning the composed HTML, which cannot see a URL the page
     * assembles later. A widespread legacy stamp script does exactly that:</p>
     *
     * <pre>{@code
     * document.getElementById('StampSignature').src =
     *         "../eform/displayImage.do?imagefile=consult_sig_" + ProviderNumber + ".png";
     * }</pre>
     *
     * <p>Only the prefix is in the document, so the assembled filename was never authorized and the
     * image servlet refused it — a 403 the gate then counted as missing content, blocking the render
     * whether or not the signature existed. Measured as the single largest content-side blocker
     * across the shared-form corpus.</p>
     *
     * <p>The provider number is taken from the form's own stored {@code current_user_id} value (an
     * {@code oscarDB} field the server populated at save time), falling back to the eForm's provider.
     * It is never read from the rendered page: the grant must not be widened by anything the
     * clinic-authored document can influence. Exactly one filename is added, and only when the
     * document shows intent to display a stamp. A stamp that is not on file does not 404: the element
     * the script targets is removed instead, so the condition is reported as itself
     * ({@code providerStampMissing}, blocking) rather than as an anonymous failed resource.</p>
     */
    static Set<String> runtimeSignatureStampFiles(
            String html, EForm eForm, List<EFormValue> eFormValues) {
        if (html == null || !html.contains(SIGNATURE_STAMP_PREFIX)) {
            return Set.of();
        }
        String providerNumber = null;
        if (eFormValues != null) {
            for (EFormValue value : eFormValues) {
                if ("current_user_id".equals(value.getVarName())) {
                    providerNumber = value.getVarValue();
                    break;
                }
            }
        }
        if (providerNumber == null || providerNumber.isBlank()) {
            providerNumber = eForm == null ? null : eForm.getProviderNo();
        }
        if (providerNumber == null || providerNumber.isBlank()) {
            return Set.of();
        }
        // Digits only. The value is server-stored, but this filename becomes an authorization grant,
        // so it is constrained to the shape a provider number can have rather than trusted.
        String trimmed = providerNumber.trim();
        for (int index = 0; index < trimmed.length(); index++) {
            if (!Character.isDigit(trimmed.charAt(index))) {
                return Set.of();
            }
        }
        return Set.of(SIGNATURE_STAMP_PREFIX + trimmed + ".png");
    }

    /**
     * Hidden marker injected when the form expects a provider signature stamp that is not on file.
     *
     * <p>Deliberately NOT {@link #SIGNATURE_UNRENDERED_MARKER}: that one means a <em>signed</em>
     * document's stored signature could not be spliced into its placeholder — an integrity failure.
     * A provider who has never uploaded a stamp image is routine, and collapsing the two would make
     * a signed record that lost its signature indistinguishable from a clinic that never configured
     * stamps.</p>
     */
    static final String PROVIDER_STAMP_MISSING_MARKER =
            "<div id=\"carlos-provider-stamp-missing\" style=\"display:none\"></div>";

    /**
     * Filters a stamp set down to the files actually present in the eForm image directory.
     *
     * <p>Mirrors {@code removeAbsentOptionalStamps}: an unreadable image directory cannot prove an
     * asset absent, so on any lookup failure the name is treated as present and behaviour is
     * unchanged — the render then fails the ordinary way rather than on a guess.</p>
     */
    static Set<String> existingImageFiles(Set<String> fileNames) {
        Set<String> present = new HashSet<>();
        for (String fileName : fileNames) {
            try {
                // length() > 0, not merely isFile(): this decides whether the provider signature
                // stamp EXISTS, and a zero-byte stamp file counted as present means
                // markProviderStampMissing is never called — so providerStampMissing, a BLOCKING
                // component, stays false and the unsigned document ships with no approval prompt.
                // The stamp <img> then fetches 200/0 bytes, which the network gate also scores as
                // loaded. Both detectors miss it unless emptiness is treated as absence here.
                File imageFile = DisplayImage2Action.getImageFile(fileName);
                if (imageFile.isFile() && imageFile.length() > 0) {
                    present.add(fileName);
                }
            } catch (Exception e) {
                present.add(fileName);
            }
        }
        return present;
    }

    /**
     * Neutralizes the runtime stamp assignment and marks the render, so an absent stamp is reported
     * as itself instead of as a 404.
     *
     * <p>The URL is built by the form's own script at load time, so there is no {@code src} to
     * rewrite. Blanking the element the script targets is what stops the request being issued.</p>
     */
    static String markProviderStampMissing(String html) {
        Document document = org.jsoup.Jsoup.parse(html);
        for (Element stamp : document.select("#StampSignature")) {
            stamp.remove();
        }
        document.outputSettings().prettyPrint(false);
        return document.outerHtml() + PROVIDER_STAMP_MISSING_MARKER;
    }

    static Set<String> referencedImageFiles(String html) {
        Set<String> files = new HashSet<>();
        Matcher matcher = IMAGE_ASSET_URL_PATTERN.matcher(html);
        while (matcher.find()) {
            String query = matcher.group(1).replace("&amp;", "&");
            for (String parameter : query.split("&")) {
                int separator = parameter.indexOf('=');
                if (separator > 0 && "imagefile".equals(parameter.substring(0, separator))) {
                    files.add(URLDecoder.decode(
                            parameter.substring(separator + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return Set.copyOf(files);
    }

    static Set<String> referencedApCacheKeys(String html) {
        Set<String> keys = new HashSet<>();
        Matcher lookup = APCACHE_LOOKUP_PATTERN.matcher(html == null ? "" : html);
        while (lookup.find() && keys.size() < 128) {
            keys.add(lookup.group(2));
        }
        Matcher mappings = APCACHE_VALUES_PATTERN.matcher(html == null ? "" : html);
        while (mappings.find() && keys.size() < 128) {
            Matcher values = QUOTED_AP_KEY_PATTERN.matcher(mappings.group(1));
            while (values.find() && keys.size() < 128) {
                keys.add(values.group(2));
            }
        }
        return Set.copyOf(keys);
    }

    static Set<String> referencedClinicScriptApCacheKeys(String html) {
        Set<String> keys = new HashSet<>();
        Document document = org.jsoup.Jsoup.parse(html == null ? "" : html);
        for (Element script : document.select("script[src*=" + IMAGE_VIEW_SERVLET_NAME + "]")) {
            String fileName = imageFileFromUrl(script.attr("src"));
            if (fileName == null || "APCache.js".equals(fileName)
                    || !fileName.matches("[A-Za-z0-9_.-]{1,255}")) {
                continue;
            }
            try {
                java.io.File file = DisplayImage2Action.getImageFile(fileName);
                if (!file.isFile() || file.length() > MAX_CLINIC_SCRIPT_BYTES) {
                    continue;
                }
                keys.addAll(referencedApCacheKeys(Files.readString(
                        file.toPath(), StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                // Missing/unreadable references remain visible to the browser completeness gate.
            }
        }
        return Set.copyOf(keys);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static String removeAbsentOptionalStamps(String html) {
        if (html == null || !html.toLowerCase(java.util.Locale.ROOT).contains("stamps.js")) {
            return html;
        }
        try {
            if (DisplayImage2Action.getImageFile("stamps.js").isFile()) {
                return html;
            }
        } catch (Exception ignored) {
            // An unavailable image directory cannot prove this optional asset absent.
            return html;
        }
        Document document = org.jsoup.Jsoup.parse(html);
        for (Element script : document.select("script[src]")) {
            if ("stamps.js".equalsIgnoreCase(imageFileFromUrl(script.attr("src")))
                    || script.attr("src").toLowerCase(java.util.Locale.ROOT).endsWith("/stamps.js")) {
                script.remove();
            }
        }
        document.outputSettings().prettyPrint(false);
        return document.outerHtml();
    }

    /** Last path segment of a script URL, with any query/fragment and path parameters removed. */
    private static String lastPathSegment(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String path = rawUrl.trim();
        int cut = path.indexOf('?');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        cut = path.indexOf('#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        cut = path.indexOf(';');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String imageFileFromUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.replace("&amp;", "&"));
            String query = uri.getRawQuery();
            if (query == null) {
                return null;
            }
            for (String parameter : query.split("&")) {
                String[] parts = parameter.split("=", 2);
                if (parts.length == 2 && "imagefile".equals(parts[0])) {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
        } catch (IllegalArgumentException | URISyntaxException ignored) {
            return null;
        }
        return null;
    }

    /** Replaces the form body with stored {@code Letter} content and remaps its signature path. */
    private static void applyLetterHtml(EForm eForm, List<EFormValue> eFormValues) {
        for (EFormValue value : eFormValues) {
            if (!"Letter".equals(value.getVarName())) {
                continue;
            }
            String html = hardenLetterHtml(decodeStoredLetter(value.getVarValue()));
            html = html.replace(IMAGE_RENDERING_SERVLET_PATH, PDF_SIGNATURE_SERVLET_PATH);
            eForm.setFormHtml("<html><body style='width:640px;'>" + html + "</body></html>");
            return;
        }
    }

    /**
     * Reverses the entity encoding the Rich Text Letter's {@code saveRTL()} applies before storing
     * the letter in a textarea value. Without this the PDF prints the clinician's markup as literal
     * text ({@code <p>Dear Dr. Smith</p>} instead of a paragraph).
     *
     * <p>The replacement ORDER is load-bearing and deliberately mirrors {@code editControl2.js}
     * character for character: {@code &amp;} decodes LAST, so a letter that legitimately contains
     * the text {@code &lt;} (stored as {@code &amp;lt;}) survives as text instead of turning into a
     * tag. Any divergence here shows up as the PDF and the on-screen editor disagreeing about the
     * same stored letter, so change both together or neither.</p>
     */
    static String decodeStoredLetter(String storedValue) {
        if (storedValue == null) {
            return "";
        }
        return storedValue
                .replace("&#39;", "'")
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");
    }

    /**
     * Removes the interaction hooks from a decoded letter without altering its structure.
     *
     * <p>Decoding is what makes this necessary: before it, a letter's markup reached the page as
     * inert text, so nothing in it could run. Now that the letter is spliced in as real markup on a
     * surface that permits {@code 'unsafe-inline'}, its event handlers and {@code javascript:} URLs
     * would be live. Those are stripped here.</p>
     *
     * <p>Script <em>elements</em> are deliberately kept. They are not decoration in this corpus:
     * {@link #applySignatureHtml} reads the stored signature's geometry out of the letter's own
     * {@code signatureControl.initialize({...})} call, and clinic letters carry image-path fixups in
     * inline scripts. A full allow-list sanitizer removes both and silently costs the clinician a
     * signature. Execution is contained instead of forbidden: the render browser holds no HttpSession,
     * JSESSIONID, CSRF token or user identity — its only credential is the short-lived, HttpOnly
     * capability cookie, which authorizes exactly the grant's asset and AP-key set and nothing else.
     * Its {@code connect-src} admits only the APCache endpoint, off-origin egress is blocked at the
     * network layer, and any non-GET request fails the render gate.</p>
     */
    static String hardenLetterHtml(String letterHtml) {
        if (letterHtml == null || letterHtml.isBlank()) {
            return "";
        }
        Document document = org.jsoup.Jsoup.parseBodyFragment(letterHtml);
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.html)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false);
        for (Element element : document.body().select("*")) {
            for (Attribute attribute : element.attributes().asList()) {
                String name = attribute.getKey();
                if (EVENT_HANDLER_ATTRIBUTE.matcher(name).matches()) {
                    element.removeAttr(name);
                    continue;
                }
                if (URL_BEARING_ATTRIBUTE.matcher(name).matches()
                        && JAVASCRIPT_SCHEME.matcher(
                                attribute.getValue().replaceAll("[\\s\\u0000]", "")).find()) {
                    element.removeAttr(name);
                }
            }
        }
        return document.body().html();
    }

    /**
     * Applies the passive subset of the saved-viewer contract. No editor, toolbar, form action,
     * opener, or signature-capture behavior crosses onto the renderer surface.
     */
    static void applyRendererViewProfile(EForm eForm, String contextPath, String formDataId) {
        String html = eForm.getFormHtml().replace(SIGNATURE_MARKER, "");
        Document document = org.jsoup.Jsoup.parse(html);
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.html)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false);

        removeInteractiveEditorContent(document);

        Element head = document.head();
        ArrayList<Element> dependencies = new ArrayList<>();
        if (isSavedViewProfileEnabled()) {
            dependencies.add(stylesheet(contextPath + "/library/bootstrap/5.3.8/css/bootstrap.min.css"));
            dependencies.add(stylesheet(contextPath + "/library/jquery/jquery-ui-1.14.2.min.css"));
            dependencies.add(script(contextPath + "/library/jquery/jquery-3.7.1.min.js"));
            dependencies.add(script(contextPath + "/library/jquery/jquery-ui-1.14.2.min.js"));
            dependencies.add(script(contextPath + "/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"));
        }
        Element rendererMarker = new Element(Tag.valueOf("script"), "");
        // The compatibility shim is also loaded by the interactive viewer. This marker is set
        // before it runs so the shim can make the one render-only exception for the legacy delayed
        // auto-submit callback without changing clinician-facing eForm behaviour.
        rendererMarker.append("window.__carlosEformPdfRender=true;");
        dependencies.add(rendererMarker);
        dependencies.add(script(contextPath + "/eform/eform-runtime-compat.js"));
        Element signatureCompatibility = new Element(Tag.valueOf("script"), "");
        signatureCompatibility.append(
                "window.signatureControl=window.signatureControl||{};"
                + "window.signatureControl.initialize=function initialize(){};");
        dependencies.add(signatureCompatibility);

        int insertionIndex = 0;
        while (insertionIndex < head.childrenSize()
                && (head.child(insertionIndex).nameIs("meta")
                        || head.child(insertionIndex).nameIs("title"))) {
            insertionIndex++;
        }
        head.insertChildren(insertionIndex, dependencies);

        Element body = document.body();
        addHiddenRendererValue(body, "context", contextPath);
        addHiddenRendererValue(body, "demographicNo", eForm.getDemographicNo());
        addHiddenRendererValue(body, "fid", eForm.getFid());
        addHiddenRendererValue(body, "fdid", formDataId);
        if (isSavedViewProfileEnabled()) {
            addHiddenRendererValue(
                    body,
                    "carlosEformRendererApCacheUrl",
                    contextPath + "/EFormApCacheForPdfGenerationServlet");
        }
        eForm.setFormHtml(document.outerHtml());
    }

    /**
     * Removes the WYSIWYG editor and the other interactive-only control libraries from the render
     * surface. A printed eForm is a passive snapshot of saved clinical content: an editor toolbar,
     * a signature-capture pad, or a fax/print dialog has no meaning in a PDF and must never appear
     * in one.
     *
     * <p>This is also what keeps the render honest. Left in place, {@code editControl2.js} boots a
     * contenteditable iframe and fetches the letter-template list, and the Rich Text Letter's
     * {@code fetchAttached()} polls the attachment sidebar — two same-origin XHRs that the render
     * surface's {@code connect-src} (locked to the APCache endpoint) refuses. Each refusal counts
     * as a failed content resource, so the completeness gate blocked the whole PDF over chrome the
     * clinician was never going to see. Removing the scripts removes the requests, rather than
     * widening the CSP to permit them.</p>
     *
     * <p>{@code APCache.js} is deliberately NOT removed: it populates clinical field content and is
     * the reason the capability-scoped APCache endpoint exists. Only editor/dialog chrome goes.</p>
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static void removeInteractiveEditorContent(Document document) {
        for (Element script : document.select("script[src]")) {
            // Match the FILENAME, not a substring of the whole URL. `source.contains("signaturecontrol")`
            // also deleted a clinic's own /library/clinic/mysignaturecontrol.js — silently, since
            // nothing counts server-side removals.
            String source = script.attr("src");
            String assetName = imageFileFromUrl(source);
            String fileName = assetName != null ? assetName : lastPathSegment(source);
            String candidate = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
            if (INTERACTIVE_ONLY_SCRIPTS.contains(candidate)) {
                script.remove();
            }
        }
        // The editor bootstrap lives in an inline script alongside its cfg_* variables, so the
        // src-based sweep above cannot reach it. Neutralize only the call: the surrounding inline
        // block may also hold clinic-authored logic that legitimately populates content.
        for (Element script : document.select("script:not([src])")) {
            String body = script.data();
            if (body.contains(EDITOR_BOOTSTRAP_CALL)) {
                script.text(body.replace(EDITOR_BOOTSTRAP_CALL, ""));
            }
        }
        // Editor chrome the form itself renders (toolbar mount point, floating controls). The
        // letter body is NOT in here — it is spliced in separately by applyLetterHtml.
        document.select("#edit-controllers, .edit-controllers, #eform_floating_toolbar").remove();
        installStrippedEditorShim(document);
    }

    /**
     * Supplies the few globals that clinic forms call at parse/load time but that lived inside the
     * stripped editor, so removing the editor cannot turn into a blocked render.
     *
     * <p>Two of these bite immediately on the Rich Text Letter: {@code Start()} is the body's
     * {@code onload} handler, and {@code cache} is built by {@code editControl2.js} from APCache's
     * {@code createCache}, then referenced at parse time by the form's own
     * {@code cache.addMapping({...})}. With the editor gone both threw ReferenceErrors, which the
     * completeness gate counts as severe console errors and refuses to print — the clinician would
     * have lost the PDF to chrome that was removed on purpose.</p>
     *
     * <p>{@code cache} is rebuilt for real (not stubbed) whenever {@code APCache.js} is present, so
     * forms that populate clinical fields through it still populate them. Only the editor sinks —
     * {@code doHtml} and friends, which wrote into a contenteditable iframe that no longer exists —
     * become no-ops. The shim is inserted immediately after the {@code APCache.js} script, so
     * {@code createCache} exists and the shim precedes the inline {@code cache.addMapping} calls
     * that follow it. When a form references no APCache script the shim is appended to the end of
     * {@code <head>} instead — still ahead of every body script, but NOT ahead of an inline
     * {@code <head>} script the form authored above it. Every assignment is guarded so a form that
     * ships its own implementation keeps it.</p>
     */
    private static void installStrippedEditorShim(Document document) {
        Element shim = new Element(Tag.valueOf("script"), "");
        shim.append(
                "(function(){\n"
                + "  var w = window;\n"
                + "  if (!w.cache) {\n"
                + "    if (typeof createCache === 'function') {\n"
                + "      w.cache = createCache({\n"
                + "        defaultCacheResponseHandler: function(){},\n"
                + "        cacheResponseErrorHandler: function(){}\n"
                + "      });\n"
                + "    } else {\n"
                // No `get` stub. Returning '' here manufactured a blank clinical field with no
                // request, no error and no gate entry — the renderer inventing empty content is
                // worse than a form script failing loudly, which the console-error gate catches.
                + "      w.cache = { addMapping: function(){}, put: function(){},\n"
                + "        isEmpty: function(){ return true; }, clear: function(){} };\n"
                + "    }\n"
                + "  }\n"
                + "  w.Start = w.Start || function Start(){};\n"
                + "  w.insertEditControl = w.insertEditControl || function insertEditControl(){};\n"
                + "  w.parseTemplate = w.parseTemplate || function parseTemplate(){};\n"
                + "  w.editControlContents = w.editControlContents || function editControlContents(){ return ''; };\n"
                + "  w.seteditControlContents = w.seteditControlContents || function seteditControlContents(){};\n"
                + "  w.doHtml = w.doHtml || function doHtml(){};\n"
                + "  w.printKey = w.printKey || function printKey(){};\n"
                + "  w.checkKeyResponse = w.checkKeyResponse || function checkKeyResponse(){ return false; };\n"
                + "  w.getMeasures = w.getMeasures || function getMeasures(){};\n"
                + "  w.updateAttached = w.updateAttached || function updateAttached(){};\n"
                + "  w.fetchAttached = w.fetchAttached || function fetchAttached(){};\n"
                + "  w.consultantSearch = w.consultantSearch || function consultantSearch(){};\n"
                + "  w.saveRTL = w.saveRTL || function saveRTL(){};\n"
                + "  w.maximize = w.maximize || function maximize(){};\n"
                + "  w.viewsource = w.viewsource || function viewsource(){};\n"
                + "  w.usecss = w.usecss || function usecss(){};\n"
                + "  w.collapseFooter = w.collapseFooter || function collapseFooter(){};\n"
                + "})();");
        Element apCache = document.selectFirst("script[src*=APCache.js]");
        if (apCache != null) {
            apCache.after(shim);
            return;
        }
        Element head = document.head();
        head.insertChildren(head.childrenSize(), java.util.List.of(shim));
    }

    private static boolean isSavedViewProfileEnabled() {
        CarlosProperties properties = CarlosProperties.getInstance();
        return properties.getProperty(RENDER_PROFILE_PROPERTY) == null
                || properties.getBooleanProperty(RENDER_PROFILE_PROPERTY, "true");
    }

    private static Element script(String source) {
        Element element = new Element(Tag.valueOf("script"), "");
        element.attr("type", "text/javascript");
        element.attr("src", source);
        return element;
    }

    private static Element stylesheet(String href) {
        Element element = new Element(Tag.valueOf("link"), "");
        element.attr("rel", "stylesheet");
        element.attr("type", "text/css");
        element.attr("media", "all");
        element.attr("href", href);
        return element;
    }

    private static void addHiddenRendererValue(Element body, String name, String value) {
        if (body.selectFirst("[name=\"" + name + "\"]") != null) {
            return;
        }
        Element input = body.appendElement("input");
        input.attr("type", "hidden");
        input.attr("name", name);
        input.attr("id", name);
        input.attr("value", value == null ? "" : value);
    }

    /**
     * Splices a stored signature image in place of the JS signature pad's {@code signatureDisplay}
     * target, reusing the geometry declared in the form's {@code signatureControl.initialize(...)}
     * call.
     *
     * <p>A blank/whitespace {@code signatureValue} means the form was never signed and is skipped
     * silently. But a <em>present, non-blank</em> stored signature that cannot be spliced — because
     * its URL fails {@link #normalizePdfSignatureUrl} validation, or the form HTML no longer carries
     * the {@code signatureControl.initialize(...)} geometry — must fail the render rather than
     * silently produce an unsigned PDF: a clinician-signed document must never fax/archive without
     * its signature. The caller ({@link #buildPdfHtmlForFdid}) attaches the fdid to this failure.</p>
     *
     * @throws IllegalStateException if a non-blank stored signature cannot be placed in the form HTML
     */
    // MODIFICATION_AFTER_VALIDATION: the regex match on the form HTML only extracts signature-pad
    // geometry; the security validation is on the signature URL (normalizePdfSignatureUrl returns
    // null → throw), and buildSignatureImageMarkup HTML-attribute-encodes it before insertion.
    @SuppressFBWarnings(value = "MODIFICATION_AFTER_VALIDATION", justification = "the html.replace only substitutes a fixed placeholder div; the signature URL is validated by normalizePdfSignatureUrl and encoded by buildSignatureImageMarkup before insertion")
    private static void applySignatureHtml(EForm eForm, List<EFormValue> eFormValues, String contextPath) {
        for (EFormValue value : eFormValues) {
            if (!"signatureValue".equals(value.getVarName())) {
                continue;
            }
            String storedSignature = value.getVarValue();
            if (storedSignature == null || storedSignature.isBlank()) {
                // An unsigned form legitimately carries a blank signatureValue; nothing to splice.
                continue;
            }
            String html = eForm.getFormHtml();
            String signatureInit = "signatureControl.initialize\\s*\\(\\s*\\{\\s*eform:true,\\s+height:(\\d+),\\s+width:(\\d+),\\s+top:(\\d+),\\s+left:(\\d+)\\s*\\}\\s*\\)";
            Matcher matcher = Pattern.compile(signatureInit).matcher(html);
            if (!matcher.find() || matcher.groupCount() != 4) {
                // A present signature that cannot be placed is a render failure, not a cosmetic skip:
                // a clinician-signed document must never fax/archive without its signature.
                logger.error("eForm PDF render failed: signed form's geometry (signatureControl.initialize) not found; fdid logged by caller");
                throw new IllegalStateException("Signed eForm cannot be rendered: signature placement not found in form HTML");
            }
            String sign = normalizePdfSignatureUrl(storedSignature, contextPath);
            if (sign == null) {
                logger.error("eForm PDF render failed: stored signature URL rejected by normalization");
                throw new IllegalStateException("Signed eForm cannot be rendered: stored signature URL is invalid");
            }
            String left = matcher.group(4), top = matcher.group(3), width = matcher.group(2), height = matcher.group(1);
            String spliced = html.replace("<div id=\"signatureDisplay\"></div>",
                    buildSignatureImageMarkup(sign, left, top, width, height));
            if (spliced.equals(html)) {
                // Mark a signed form whose signature placeholder is unavailable so post-layout
                // completeness reporting requires exact clinician approval.
                logger.error("eForm PDF: signed form's signature placeholder was altered/removed; "
                        + "signature not rendered, prompting the clinician (fdid logged by caller)");
                eForm.setFormHtml(html + SIGNATURE_UNRENDERED_MARKER);
            } else {
                eForm.setFormHtml(spliced);
            }
        }
    }

    /**
     * Hidden marker injected when a non-blank stored signature could not be spliced because the
     * placeholder was altered/removed. {@code COMPUTE_PAGE_GEOMETRY_JS} treats its presence as a
     * broken signature that requires exact informed approval.
     */
    static final String SIGNATURE_UNRENDERED_MARKER =
            "<div id=\"carlos-signature-unrendered\" style=\"display:none\"></div>";

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

    /** Legacy eForm image-path marker and the URL-encoded form browsers bake into resolved src attributes. */
    private static final String IMAGE_PATH_MARKER = "${oscar_image_path}";
    private static final String IMAGE_PATH_MARKER_URLENCODED = "$%7Boscar_image_path%7D";

    /**
     * Replaces the legacy {@code ${oscar_image_path}} marker (and its URL-encoded form) with the
     * render-asset servlet prefix — but ONLY inside element <em>attribute values</em>, never inside
     * {@code <script>} text. A blind whole-string replace also rewrote the marker where corpus forms
     * use it as a JavaScript string literal: the widespread "standalone development" helper
     * {@code src.replace("$%7Boscar_image_path%7D","")} then became
     * {@code src.replace("<asset-servlet-prefix>","")}, and on the HTTP loopback render surface
     * (which the helper's {@code indexOf("https") == -1} check treats as standalone dev) it stripped
     * the entire rewritten prefix from every background image, blanking the form. Scoping the
     * replacement to attributes leaves such script literals untouched (their replace becomes a
     * harmless no-op at render time) while still rewriting every real asset reference, including
     * {@code style} attributes with {@code url(${oscar_image_path}…)} backgrounds.
     *
     * <p>Skips the jsoup round-trip entirely when neither marker form is present. The round-trip
     * itself is safe here: this composer output is exclusively the browser-render surface, whose
     * content is already jsoup-normalized by {@link EForm#getFormHtml()} on this path (the parse
     * settings below mirror {@code ConvertToEdoc.parseDocument}, which that pass uses — inlined
     * rather than called so this pure string helper never triggers ConvertToEdoc's app-context
     * static initialization).</p>
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    static String replaceImagePathMarkerInAttributes(String html, String assetPrefix) {
        if (!html.contains(IMAGE_PATH_MARKER) && !html.contains(IMAGE_PATH_MARKER_URLENCODED)) {
            return html;
        }
        // DOCTYPE declarations are mandatory for a stable round-trip. HTML5 if none is declared.
        String documentString = html.trim().toLowerCase(java.util.Locale.ROOT).startsWith("<!doctype")
                ? html
                : "<!DOCTYPE html>\n" + html;
        Document document = org.jsoup.Jsoup.parse(documentString);
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.html)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                .charset("UTF-8")
                .prettyPrint(false);
        for (Element element : document.getAllElements()) {
            for (Attribute attribute : element.attributes().asList()) {
                String value = attribute.getValue();
                if (value.contains(IMAGE_PATH_MARKER) || value.contains(IMAGE_PATH_MARKER_URLENCODED)) {
                    element.attr(attribute.getKey(), encodeUrlHostileFileNameCharacters(value
                            .replace(IMAGE_PATH_MARKER, assetPrefix)
                            .replace(IMAGE_PATH_MARKER_URLENCODED, assetPrefix), assetPrefix));
                }
            }
        }
        return document.outerHtml();
    }

    /**
     * Characters that are legal in a filename but illegal unencoded in a request target. Tomcat
     * rejects such a request at the HTTP parser — {@code Invalid character found in the request
     * target}, HTTP 400 — before this webapp sees it, so a form packaging an image named e.g.
     * {@code scan-1[1].png} cannot render it at all. Real OSCAR Galaxy packages ship exactly such
     * names, and the ZIP importer stores them verbatim.
     *
     * <p>Deliberately a small fixed set rather than general URL encoding: {@code /}, {@code ?},
     * {@code &amp;} and {@code =} are structural here and encoding them would break references that
     * work today. Quotes and angle brackets are excluded for a different reason — they delimit the
     * value, so treating them as both "encode this" and "the filename ends here" is ambiguous, and
     * no filename an HTML attribute can unambiguously reference contains them. Mirrors the
     * viewer-path set in {@code EFormBase.setImagePath}.</p>
     */
    private static final String URL_HOSTILE_FILENAME_CHARACTERS = "[]{}|\\^ ()";

    /**
     * Percent-encodes {@link #URL_HOSTILE_FILENAME_CHARACTERS} in each filename following
     * {@code assetPrefix} in an already-substituted attribute value.
     *
     * <p>The value is a parsed jsoup attribute, so the filename normally runs to the end of it
     * ({@code src="…?imagefile=my scan[1].png"} — the space belongs to the filename). When the
     * reference is embedded rather than the whole value, as in
     * {@code style="background-image:url(…bg.png)"}, the token ends at the first {@code )}, quote or
     * whitespace instead; encoding the closing paren would corrupt the CSS.</p>
     */
    static String encodeUrlHostileFileNameCharacters(String value, String assetPrefix) {
        int markerAt = value.indexOf(assetPrefix);
        if (markerAt < 0 || assetPrefix.isEmpty()) {
            return value;
        }
        StringBuilder encoded = new StringBuilder(value.length());
        int cursor = 0;
        while (markerAt >= 0) {
            int fileNameStart = markerAt + assetPrefix.length();
            encoded.append(value, cursor, fileNameStart);
            boolean wholeValue = markerAt == 0;
            int index = fileNameStart;
            while (index < value.length()) {
                char current = value.charAt(index);
                // ')' delimits only an embedded reference such as url(...). When the attribute
                // value IS the URL, a paren belongs to the filename and must be encoded: the grant
                // pattern excludes ')' to protect url(...), so a raw one truncated the captured
                // name and the asset was refused 403.
                if (current == '\'' || (!wholeValue
                        && (current == ')' || Character.isWhitespace(current)))) {
                    break;
                }
                if (URL_HOSTILE_FILENAME_CHARACTERS.indexOf(current) >= 0) {
                    encoded.append(String.format("%%%02X", (int) current));
                } else {
                    encoded.append(current);
                }
                index++;
            }
            cursor = index;
            markerAt = value.indexOf(assetPrefix, cursor);
        }
        encoded.append(value, cursor, value.length());
        return encoded.toString();
    }

    // Package-private for the slash-normalization unit test.
    static String imageViewServletBase(String projectHome, String contextPath) {
        // Prefer the ACTUAL servlet context path: it is where this webapp — and therefore the image
        // servlet — is really mounted. project_home is a legacy OscarDocument DIRECTORY name (e.g.
        // "oscar" in EFORM_IMAGES_DIR=/var/lib/OscarDocument/oscar/...), not a URL path; deployments
        // routinely set it differently from the context (dev: project_home=oscar, context=/carlos),
        // and preferring it emitted /oscar/EFormImageViewForPdfGenerationServlet URLs that 404 and
        // fail the render's content gate. Fall back to project_home only when no context path is
        // available at all — never emit a leading "//…" (a protocol-relative URL to an external
        // host): project_home may carry a leading slash, so it is slash-stripped before prefixing.
        String base = normalizeContextPath(contextPath);
        if (base.isEmpty()) {
            String normalizedProjectHome = projectHome == null ? "" : stripSlashes(projectHome.trim());
            base = normalizedProjectHome.isEmpty() ? "" : "/" + normalizedProjectHome;
        }
        return base + "/" + IMAGE_VIEW_SERVLET_NAME;
    }

    private static String stripSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
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
