/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import jakarta.servlet.ServletContext;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utility for converting HTML content into PDF documents and wrapping them as EDoc objects
 * in the CARLOS EMR document management system.
 *
 * <p>This is useful for converting Forms and eForms with well-structured HTML into
 * PDF documents that can be attached to Consultation Requests, Faxes, or transferred
 * to other file systems. NOT ALL DOCUMENTS ARE CONVERTIBLE. USE AT OWN RISK.
 *
 * <p>The conversion pipeline is:
 * <ol>
 *   <li>Parse and clean HTML with Jsoup (DOCTYPE injection, resource path validation)</li>
 *   <li>Translate relative resource paths to encoded {@code file:} URIs; an image that cannot be
 *       read stays as an unavailable marker, which prints as a box</li>
 *   <li>Render through {@link HtmlPdfRenderService} (Flying Saucer), the one HTML-to-PDF engine of
 *       CARLOS EMR; it prints a simpler version of a document it cannot lay out as written rather
 *       than none at all</li>
 * </ol>
 *
 * <p>Thread safety: conversion, document parsing and tidying entry points are synchronized
 * to prevent concurrent modification of the shared {@link #realPath} field.
 *
 * @see EDoc
 * @see EDocFactory
 * @see HtmlPdfRenderService
 * @since 2018-10-15
 */
public final class ConvertToEdoc {

    private static final Logger logger = MiscUtils.getLogger();

    public enum DocumentType {eForm, form}

    public enum ElementAttribute {src, href, value, name, id, title, type, rel, media}

    /**
     * The extensions a local resource path may have. The picture types are the ones the HTML-to-PDF service
     * draws when it is given a file: PNG, JPEG and GIF as they are, BMP, WBMP and TIFF after converting them,
     * and SVG after removing its outside references. WebP and HEIC stay out on purpose: the service does not
     * decode them, so they print as the "[image unavailable]" box whether or not they are listed here.
     */
    private enum FileType {pdf, css, jpeg, png, gif, js, jpg, bmp, wbmp, tif, tiff, svg}

    public static final String CUSTOM_STYLESHEET_ID = "pdfMediaStylesheet";
    private static final String OSCAR_IMAGE_PATH_TOKEN = "${oscar_image_path}";
    /** Percent-encoded form of {@link #OSCAR_IMAGE_PATH_TOKEN} as it can appear in stored src/href markup. */
    private static final String OSCAR_IMAGE_PATH_TOKEN_ENCODED = "%24%7Boscar_image_path%7D";
    private static final String DEFAULT_FILENAME = "temporaryPDF";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_CONTENT_TYPE = "application/pdf";
    private static final String SYSTEM_ID = "-1";
    private static final String BACKGROUND_ATTRIBUTE = "background";
    /** Marks an {@code <img>} whose source cannot be used; the HTML-to-PDF module prints the "[image unavailable]" box for it. */
    public static final String UNAVAILABLE_IMAGE_ATTRIBUTE = "data-carlos-image-unavailable";
    private static final String STYLE_ATTRIBUTE = "style";
    private static String realPath;

    /**
     * Creates an EDoc from an EForm whose PDF has already been generated at the specified path.
     *
     * @param eForm EFormData the electronic form data providing metadata
     * @param eFormPDFPath Path the path to the pre-generated PDF file
     * @return EDoc the document object referencing the PDF
     * @throws PDFGenerationException if the PDF file at the given path is not readable
     */
    public synchronized static EDoc from(EFormData eForm, Path eFormPDFPath) throws PDFGenerationException {
        String demographicNo = eForm.getDemographicId() + "";
        String filename = buildFilename(eForm.getFormName(), demographicNo);
        String eDocDescription = (eForm.getSubject() == null || eForm.getSubject().trim().isEmpty()) ? eForm.getFormName() : eForm.getSubject();
        EDoc edoc = null;

        if (Files.isReadable(eFormPDFPath)) {
            edoc = buildEDoc(eFormPDFPath.getFileName().toString(),
                    eDocDescription,
                    null,
                    eForm.getProviderNo(),
                    demographicNo,
                    DocumentType.eForm,
                    eFormPDFPath.getParent().toString());
        } else {
            throw new PDFGenerationException("Could not read temporary PDF file " + filename);
        }

        return edoc;
    }

    /**
     * Converts an EForm to a temporary PDF file without creating an EDoc entity.
     * The caller is responsible for deleting the temporary file after use.
     *
     * @param eform EFormData the electronic form data to convert
     * @return Path the temporary file path to the produced PDF, or null if conversion fails
     */
    public synchronized static Path saveAsTempPDF(EFormData eform) {
        String eformString = eform.getFormData();
        String filename = buildFilename(eform.getFormName(), eform.getDemographicId() + "");
        return execute(eformString, filename, HtmlPdfRenderSource.HRM);
    }

    /**
     * Converts a Form (via its transport container) to a temporary PDF file without
     * creating an EDoc entity. The caller is responsible for deleting the temporary file after use.
     *
     * @param formTransportContainer FormTransportContainer containing form HTML, metadata, and real path
     * @return Path the temporary file path to the produced PDF, or null if conversion fails
     */
    public synchronized static Path saveAsTempPDF(FormTransportContainer formTransportContainer) {
        String htmlString = formTransportContainer.getHTML();
        ConvertToEdoc.realPath = formTransportContainer.getRealPath();
        String filename = buildFilename(formTransportContainer.getFormName(), formTransportContainer.getDemographicNo());
        return execute(htmlString, filename, HtmlPdfRenderSource.FORM);
    }

    /**
     * Converts email HTML content to a temporary PDF file without creating an EDoc entity.
     * The caller is responsible for deleting the temporary file after use.
     *
     * @param emailData EmailData containing the encrypted/HTML email message body
     * @return Path the temporary file path to the produced PDF, or null if conversion fails
     */
    public synchronized static Path saveAsTempPDF(EmailData emailData) {
        String htmlString = emailData.getEncryptedMessage();
        String filename = buildFilename("emailbody_", "");
        return execute(htmlString, filename, HtmlPdfRenderSource.EMAIL);
    }

    /**
     * Executes the HTML-to-PDF conversion pipeline and saves the result to the temp directory.
     *
     * <p>Tidying the HTML, rendering and saving are one unit: anything that goes wrong in them (blank
     * HTML, a render that cannot produce a PDF, a temporary file that cannot be written) is logged
     * once, with the sanitised file name and the class of the cause, never with the HTML or an
     * exception message (both can hold patient data), and gives the documented {@code null}.</p>
     *
     * @param eformString String the raw HTML content to convert
     * @param filename String the base filename for the output PDF
     * @param source HtmlPdfRenderSource which kind of document this is, for the render log line
     * @return Path the path to the saved temporary PDF, or null if conversion fails
     */
    private static Path execute(final String eformString, final String filename, final HtmlPdfRenderSource source) {
        if (eformString == null) {
            // No source HTML to render. Return null per this method's documented failure contract
            // instead of NPE-ing deep in the Jsoup/Flying Saucer pipeline; callers treat null as a
            // conversion failure and surface it.
            logger.error("Cannot render PDF: source HTML was null for {}", LogSafe.sanitize(filename)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        Path path = null;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            // Settle the webapp root first, so translating the paths and rendering use the same one.
            webappRoot();
            String document = tidyDocument(eformString);
            renderPDF(document, os, source);
            // Resolve against the current context; do not retain a bean across context reloads.
            path = SpringUtils.getBean(NioFileManager.class).saveTempFile(filename, os);
            if (logger.isDebugEnabled()) {
                // Filename embeds demographic_no (buildFilename), so it is sanitized before logging.
                logger.debug("Rendered temporary PDF ({} bytes) for {}", os.size(), LogSafe.sanitize(filename));
            }
        } catch (PDFGenerationException | IOException | RuntimeException e) {
            logger.error("HTML could not be converted to PDF for {}: {}", LogSafe.sanitize(filename), e.getClass().getSimpleName());
        }

        return path;
    }

    /**
     * Creates a well-formed, unique filename by sanitizing spaces/slashes and appending
     * the demographic number and a timestamp.
     *
     * @param filename String the base filename (may contain spaces or slashes)
     * @param demographicNo String the patient demographic number
     * @return String the sanitized filename with demographic number and timestamp suffix
     */
    private static String buildFilename(String filename, String demographicNo) {

        if (filename == null || filename.isEmpty()) {
            filename = DEFAULT_FILENAME;
        }

        filename = filename.trim();
        filename = filename.replaceAll(" ", "_");
        filename = filename.replaceAll("/", "_");
        filename = String.format("%1$s_%2$s", filename, demographicNo);
        filename = String.format("%1$s_%2$s", filename, new Date().getTime());
        return filename;
    }

    /**
     * Builds an EDoc instance with the specified metadata and file information.
     * Sets the content type to PDF, counts pages, and records the current date.
     *
     * @param filename String the PDF filename
     * @param subject String the document description/subject
     * @param sourceHtml String the original HTML source (may be null)
     * @param providerNo String the creating provider's number
     * @param demographicNo String the patient demographic number
     * @param documentType DocumentType the document origin type (eForm or form)
     * @param filePath String the directory path where the PDF is stored
     * @return EDoc the constructed document object
     */
    public static EDoc buildEDoc(final String filename, final String subject, final String sourceHtml,
                                 final String providerNo, final String demographicNo, final DocumentType documentType, final String filePath) {

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DEFAULT_DATE_FORMAT);
        final String todayDate = simpleDateFormat.format(new Date());

        EDoc eDoc = new EDoc(
                (subject == null) ? "" : subject,
                documentType.name(),
                filename,
                sourceHtml,
                SYSTEM_ID,
                providerNo,
                "",
                EDocFactory.Status.ACTIVE.getStatusCharacter(),
                todayDate,
                "",
                new Date().toString(),
                EDocFactory.Module.demographic.name(),
                demographicNo,
                Boolean.FALSE);

        eDoc.setContentType(DEFAULT_CONTENT_TYPE);
        eDoc.setContentDateTime(new Date());
        eDoc.setNumberOfPages(EDocUtil.getPDFPageCount(filePath + "/" + filename));
        eDoc.setFilePath(filePath);

        return eDoc;
    }

    /**
     * Renders the HTML document string to a PDF output stream through {@link HtmlPdfRenderService}, resolved at
     * call time (this class is static; the bean is not retained across context reloads). The service reads only
     * the directories of {@link #buildRenderRequest}, makes no network request and prints a simpler version of a
     * document it cannot lay out as written rather than none at all.
     *
     * @param document String the cleaned HTML document string
     * @param os ByteArrayOutputStream the output stream to write the PDF to
     * @param source HtmlPdfRenderSource which kind of document this is
     * @throws PDFGenerationException if the service cannot produce a PDF
     * @throws IOException if an I/O error occurs during rendering
     */
    private static void renderPDF(final String document, ByteArrayOutputStream os, HtmlPdfRenderSource source)
            throws PDFGenerationException, IOException {
        HtmlPdfRenderService service = SpringUtils.getBean(HtmlPdfRenderService.class);
        service.render(buildRenderRequest(document, source)).writeTo(os);
    }

    /**
     * The request for one document: relative references resolve against the webapp root, and the
     * document may read from it, from the eForm image directory, from {@code java.io.tmpdir} and from
     * Catalina's work directory, and from nowhere else.
     *
     * @param tidiedHtml String the HTML after {@link #tidyDocument(String)}
     * @param source HtmlPdfRenderSource which kind of document this is
     * @return HtmlPdfRenderRequest the request; its base directory is {@code null} when the webapp root cannot be resolved
     */
    static HtmlPdfRenderRequest buildRenderRequest(String tidiedHtml, HtmlPdfRenderSource source) {
        Path root = usablePath(webappRoot());
        Set<Path> allowed = new LinkedHashSet<>();
        if (root != null) {
            allowed.add(root);
        }
        try {
            addUsable(allowed, usablePath(getImageDirectory()));
        } catch (RuntimeException e) {
            // Not configured or not resolvable: documents then cannot read eForm images, which print as boxes.
            logger.warn("The eForm image directory could not be resolved ({})", e.getClass().getSimpleName());
        }
        addUsable(allowed, usablePath(System.getProperty("java.io.tmpdir")));
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null && !catalinaBase.isBlank()) {
            addUsable(allowed, usablePath(Path.of(catalinaBase, "work").toString()));
        }
        return new HtmlPdfRenderRequest(tidiedHtml, root, List.copyOf(allowed), source);
    }

    private static void addUsable(Set<Path> allowed, Path directory) {
        if (directory != null) {
            allowed.add(directory);
        }
    }

    /** An absolute, normalised path that is not a filesystem root, or {@code null} if the text is not one. */
    private static Path usablePath(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(text).toAbsolutePath().normalize();
            return path.getParent() == null ? null : path;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static Path usablePath(Path path) {
        return path == null ? null : usablePath(path.toString());
    }

    /**
     * The webapp root the HTML's relative references resolve against, so that translating the paths and
     * rendering use one root: {@link #realPath} if it was set, else the real path of the servlet
     * context, which works on any thread (a fax scheduler has no request); {@code null} if neither
     * resolves. A root found this way becomes {@link #realPath} too.
     */
    static synchronized String webappRoot() {
        if (realPath == null || realPath.isBlank()) {
            try {
                ServletContext context = SpringUtils.getBean(ServletContext.class);
                String contextRoot = context == null ? null : context.getRealPath("/");
                if (contextRoot != null && !contextRoot.isBlank()) {
                    realPath = contextRoot;
                }
            } catch (RuntimeException e) {
                logger.warn("The webapp root could not be resolved from the servlet context ({})", e.getClass().getSimpleName());
            }
        }
        return realPath == null || realPath.isBlank() ? null : realPath;
    }

    /**
     * Parses and cleans an HTML document string using the specified real path for resource resolution.
     *
     * @param documentString String the raw HTML content
     * @param realPath String the servlet context real path for resolving relative resource paths
     * @return Document the parsed and cleaned Jsoup DOM
     */
    public static synchronized Document getDocument(final String documentString, String realPath) {
		ConvertToEdoc.realPath = realPath;
		return getDocument(documentString);
	}

    /**
     * Clean and parse the HTML document string into a manageable DOM
     * with JSoup tools
     * Also validates all URL paths to resources.
     *
     * @param documentString raw HTML string
     * @return org.jsoup.nodes.Document JSoup DOM
     */
    public static synchronized Document getDocument(String documentString) {
        Document document = parseDocument(documentString);

        /*
         * Process and validate resource paths
         */
        validateResourcePaths(document);

        /*
         * Returns a Document object.
         * Document will contain a blank HTML page if the incoming HTML
         * string is NULL, empty, or if an error occurs.
         */
        return document;
    }

    /**
     * Parses runtime eForm HTML into a Jsoup DOM without stripping unresolved resource tags.
     * Runtime eForms intentionally reference application-served assets such as
     * /eform/displayImage?imagefile=... and should keep those authored elements intact.
     *
     * @param documentString raw HTML to parse
     * @return parsed Jsoup DOM configured for runtime HTML handling
     * @throws IllegalArgumentException if {@code documentString} is null, empty, or blank
     */
    public static Document parseDocument(String documentString) {
        if (StringUtils.isBlank(documentString)) {
            throw new IllegalArgumentException("HTML cannot be blank");
        }

        // DOCTYPE declarations are mandatory. HTML5 if none is declared.
        if (!documentString.trim().toLowerCase().startsWith("<!doctype")) {
            documentString = "<!DOCTYPE html>\n" + documentString;
        }

        Document document = Jsoup.parse(documentString);
        document.outputSettings()
            .syntax(Document.OutputSettings.Syntax.html)  // Use HTML syntax for better compatibility
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset("UTF-8")
            .prettyPrint(false);
        return document;
    }

    /**
     * Adds custom CSS templates to the Document.
     * Normally the stylesheets should be included with the HTML being converted. This method may be
     * required to alter the current style for better print to PDF. Or if the original stylesheet gets
     * stripped out of the HTML like with the Rich Text Letter Editor
     * A stylesheet reference can be set into the origin HTML document with a hidden input tag:
     * <input type="hidden" id="customStylesheet" name="customStylesheet" value="<stylesheet filename>" />
     * This tag would be inserted between the section tag of a Rich Text Letter Template.
     * The custom stylesheet will be retrieved from Oscar's images directory. Only the filename needs to be
     * given by the input tag. This method will build the filepath.
     * - Adds a head element to the document if one does not exist.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static void addCss(Document document) {
        Element styleSheetElement = document.getElementById(CUSTOM_STYLESHEET_ID);
        if (styleSheetElement != null && "hidden".equalsIgnoreCase(styleSheetElement.attributes().getIgnoreCase("type"))) {
            setParameterInjectedCss(document, styleSheetElement.attributes().getIgnoreCase("value"));
        }
    }

    /**
     * It is critical that a head element is present.
     */
    private static void setHeadElement(Document document) {
        if (!document.head().isBlock()) {
            document.appendElement("head");
        }
    }

    /**
     * Returns the head element directly from the given Document
     */
    private static Element getHeadElement(Document document) {
        setHeadElement(document);
        return document.head();
    }

    /**
     * Create a Link element from the CSS filename that was inserted into the
     * origin HTML as a hidden input element.
     */
    private static void setParameterInjectedCss(Document document, String filename) {
        filename = buildImageDirectoryPath(filename);
        filename = validateLink(filename);

        // add a stylesheet ie: link <link rel="stylesheet" href=".." />
        if (filename != null) {
            Element linkElement = document.createElement("link");
            linkElement.attr("rel", "stylesheet");
            linkElement.attr("href", toFileUri(filename));
            getHeadElement(document).appendChild(linkElement);
        }
    }

    /**
     * HTTP request paths routed through Struts need to be
     * translated into an absolute path to the global image directory.
     */
    private static void translateResourcePaths(Document document) {
        // Keyed by element, not by the candidate paths: every <img> of a repeated src is translated.
        Map<Element, List<String>> pathTranslationMap = new LinkedHashMap<>();
        translateLinkPaths(document, pathTranslationMap);
        translateImagePaths(document, pathTranslationMap);
        translateBackgroundAttributes(document);
        translateInlineStylePaths(document);
        translateEmbeddedStylesheetPaths(document);

        for (Map.Entry<Element, List<String>> pathSet : pathTranslationMap.entrySet()) {
            if (!pathSet.getValue().isEmpty()) {
                Element element = pathSet.getKey();
                // The first candidate that is a readable file, as an encoded file: URI.
                String path = toFileUri(validateLink(pathSet.getValue()));
                if (path == null) {
                    markOrRemoveUnresolved(element);
                    continue;
                }

                if (element.hasAttr(ElementAttribute.href.name())) {
                    element.attr(ElementAttribute.href.name(), path);
                }

                if (element.hasAttr(ElementAttribute.src.name())) {
                    element.attr(ElementAttribute.src.name(), path);
                }
            }
        }
    }

    /**
     * CSS (link) resource links
     */
    private static void translateLinkPaths(Document document, Map<Element, List<String>> pathTranslationMap) {
        Elements linkNodeList = document.getElementsByTag("link");
        translatePaths(linkNodeList, ElementAttribute.href, pathTranslationMap);
    }

    /**
     * Javascript resource links
     */
    private static void translateJavascriptPaths(Document document, Map<Element, List<String>> pathTranslationMap) {
        Elements linkNodeList = document.getElementsByTag("script");
        translatePaths(linkNodeList, ElementAttribute.src, pathTranslationMap);
    }

    /**
     * Image resource links
     */
    private static void translateImagePaths(Document document, Map<Element, List<String>> pathTranslationMap) {
        Elements imageNodeList = document.getElementsByTag("img");
        translatePaths(imageNodeList, ElementAttribute.src, pathTranslationMap);
    }

    private static void translateBackgroundAttributes(Document document) {
        for (Element element : document.select("[" + BACKGROUND_ATTRIBUTE + "]")) {
            String originalPath = element.attr(BACKGROUND_ATTRIBUTE);
            String translatedPath = translateSingleResourcePath(originalPath);
            if (translatedPath != null) {
                element.attr(BACKGROUND_ATTRIBUTE, toFileUri(translatedPath));
            } else if (!isEmbeddedDataResourcePath(originalPath)) {
                element.removeAttr(BACKGROUND_ATTRIBUTE);
            }
        }
    }

    private static void translateInlineStylePaths(Document document) {
        for (Element element : document.select("[" + STYLE_ATTRIBUTE + "]")) {
            element.attr(STYLE_ATTRIBUTE, rewriteCssResourceUrls(element.attr(STYLE_ATTRIBUTE)));
        }
    }

    private static void translateEmbeddedStylesheetPaths(Document document) {
        for (Element styleElement : document.getElementsByTag(STYLE_ATTRIBUTE)) {
            styleElement.text(rewriteCssResourceUrls(styleElement.data()));
        }
    }

    private static String rewriteCssResourceUrls(String cssText) {
        if (StringUtils.isBlank(cssText)) {
            return cssText;
        }

        StringBuilder rewrittenCss = new StringBuilder();
        int cursor = 0;
        CssUrlMatch match = findNextCssUrlMatch(cssText, cursor);
        while (match != null) {
            rewrittenCss.append(cssText, cursor, match.urlStart());
            if (match.complete()) {
                appendRewrittenCssUrl(rewrittenCss, cssText, match);
                cursor = match.urlEnd() + 1;
                match = findNextCssUrlMatch(cssText, cursor);
            } else {
                rewrittenCss.append(cssText, match.urlStart(), cssText.length());
                cursor = cssText.length();
                match = null;
            }
        }

        rewrittenCss.append(cssText, cursor, cssText.length());
        return rewrittenCss.toString();
    }

    private static void appendRewrittenCssUrl(StringBuilder rewrittenCss, String cssText, CssUrlMatch match) {
        String originalPath = extractCssUrlPath(cssText, match.contentStart(), match.urlEnd());
        String translatedPath = translateSingleResourcePath(originalPath);
        if (translatedPath != null) {
            // translateSingleResourcePath returns data: URIs unchanged, so this branch
            // also handles embedded data resources (they arrive here with translatedPath == originalPath).
            rewrittenCss.append("url('").append(SafeEncode.forCssString(toFileUri(translatedPath))).append("')");
        } else {
            rewrittenCss.append("url('')");
        }
    }

    private static CssUrlMatch findNextCssUrlMatch(String cssText, int cursor) {
        int urlStart = StringUtils.indexOfIgnoreCase(cssText, "url(", cursor);
        if (urlStart < 0) {
            return null;
        }

        int contentStart = urlStart + 4;
        while (contentStart < cssText.length() && Character.isWhitespace(cssText.charAt(contentStart))) {
            contentStart++;
        }

        int urlEnd = findCssUrlEnd(cssText, contentStart);
        if (urlEnd < 0) {
            return CssUrlMatch.incomplete(urlStart, contentStart);
        }

        return CssUrlMatch.complete(urlStart, contentStart, urlEnd);
    }

    private static int findCssUrlEnd(String cssText, int contentStart) {
        char quote = 0;
        boolean escaped = false;
        int nestedParens = 0;
        for (int i = contentStart; i < cssText.length(); i++) {
            char current = cssText.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\' && i + 1 < cssText.length()) {
                    escaped = true;
                } else {
                    quote = closeQuoteIfNeeded(quote, current);
                }
            } else if (isCssQuote(current)) {
                quote = current;
            } else if (current == '(') {
                nestedParens++;
            } else if (current == ')') {
                if (nestedParens == 0) {
                    return i;
                }
                nestedParens--;
            }
        }
        return -1;
    }

    private static char closeQuoteIfNeeded(char quote, char current) {
        return current == quote ? (char) 0 : quote;
    }

    private static boolean isCssQuote(char current) {
        return current == '\'' || current == '"';
    }

    private static String extractCssUrlPath(String cssText, int contentStart, int urlEnd) {
        String rawPath = cssText.substring(contentStart, urlEnd).trim();
        if (rawPath.length() >= 2) {
            char first = rawPath.charAt(0);
            char last = rawPath.charAt(rawPath.length() - 1);
            if (isMatchingQuote(first, last)) {
                rawPath = rawPath.substring(1, rawPath.length() - 1).trim();
            }
        }
        return rawPath;
    }

    private static boolean isMatchingQuote(char first, char last) {
        return isCssQuote(first) && first == last;
    }

    private record CssUrlMatch(int urlStart, int contentStart, int urlEnd, boolean complete) {
        private static CssUrlMatch complete(int urlStart, int contentStart, int urlEnd) {
            return new CssUrlMatch(urlStart, contentStart, urlEnd, true);
        }

        private static CssUrlMatch incomplete(int urlStart, int contentStart) {
            return new CssUrlMatch(urlStart, contentStart, -1, false);
        }
    }

    private static String translateSingleResourcePath(String path) {
        if (isEmbeddedDataResourcePath(path)) {
            return path;
        }
        return validateLink(collectPotentialFilePaths(path));
    }

    private static List<String> collectPotentialFilePaths(String path) {
        List<String> potentialFilePaths = new ArrayList<>();

        if (!isTranslatableResourcePath(path)) {
            return potentialFilePaths;
        }

        if (path.contains("?")) {
            String basePath = path.split("\\?", 2)[0];
            collectRealPathCandidates(basePath, potentialFilePaths);
            collectImageDirectoryCandidates(path, potentialFilePaths);
        } else {
            collectRealPathCandidates(path, potentialFilePaths);
            collectOscarImagePathCandidate(path, potentialFilePaths);
        }

        return potentialFilePaths;
    }

    private static void collectOscarImagePathCandidate(String path, List<String> potentialFilePaths) {
        String filename;
        if (path.startsWith(OSCAR_IMAGE_PATH_TOKEN)) {
            filename = path.substring(OSCAR_IMAGE_PATH_TOKEN.length());
        } else if (path.regionMatches(true, 0, OSCAR_IMAGE_PATH_TOKEN_ENCODED, 0, OSCAR_IMAGE_PATH_TOKEN_ENCODED.length())) {
            // A percent-encoded token implies a percent-encoded filename too; decode the remainder so
            // encoded eForm background images still resolve into the PDF instead of being dropped
            // Fall back to the raw remainder if it is not validly encoded.
            String encodedRemainder = path.substring(OSCAR_IMAGE_PATH_TOKEN_ENCODED.length());
            try {
                // Protect a literal '+' (percent-encode it) before decoding: URLDecoder applies form
                // semantics and would otherwise turn '+' into a space, dropping a valid filename that
                // legitimately contains '+'. '%20' still decodes to a real space.
                filename = URLDecoder.decode(encodedRemainder.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                filename = encodedRemainder;
            }
        } else {
            return;
        }

        String candidate = buildImageDirectoryPath(filename);
        if (!candidate.isEmpty()) {
            potentialFilePaths.add(candidate);
        }
    }

    private static boolean isTranslatableResourcePath(String path) {
        return StringUtils.isNotBlank(path) && !isDisallowedResourcePath(path);
    }

    private static void collectRealPathCandidates(String path, List<String> potentialFilePaths) {
        String resolvedPath = getRealPath(path);
        if (!resolvedPath.isEmpty()) {
            potentialFilePaths.add(resolvedPath);
        }
    }

    private static void collectImageDirectoryCandidates(String path, List<String> potentialFilePaths) {
        String parameters = path.split("\\?", 2)[1];
        for (String parameter : parameters.split("&")) {
            String candidate = buildImageDirectoryCandidate(parameter);
            if (!candidate.isEmpty()) {
                potentialFilePaths.add(candidate);
            }
        }
    }

    private static String buildImageDirectoryCandidate(String parameter) {
        if (!parameter.contains("=")) {
            return "";
        }

        return buildImageDirectoryPath(parameter.split("=", 2)[1]);
    }

    /**
     * Translate any given Link or Image element resource path from
     * a Struts HTTP request parameter or HTTP relative context path.
     * All resource links in the document must be absolute for the PDF
     * creator to work.
     *
     * <p>A link or script that cannot be translated (remote, outside the web root, not found) is
     * removed. An image that cannot be translated is kept as an unavailable marker, so that the printed
     * page shows the "[image unavailable]" box where the picture should be instead of silently
     * closing up the layout.</p>
     *
     * @param nodeList           jsoup Elements collection of potential resource links
     * @param pathAttribute      jsoup ElementAttribute to be translated to an absolute link
     * @param pathTranslationMap Map to collect translated URI's, in document order
     */
    private static void translatePaths(Elements nodeList, ElementAttribute pathAttribute, Map<Element, List<String>> pathTranslationMap) {
        for (Element element : nodeList) {
            if (element.hasAttr(pathAttribute.name())) {
                String path = element.attributes().get(pathAttribute.name());
                if (isExternalResourcePath(path)) {
                    markOrRemoveUnresolved(element);
                } else if (!isEmbeddedDataResourcePath(path)) {
                    List<String> potentialFilePaths = collectPotentialFilePaths(path);
                    if (!potentialFilePaths.isEmpty()) {
                        pathTranslationMap.put(element, potentialFilePaths);
                    } else {
                        markOrRemoveUnresolved(element);
                    }
                }
            }
        }
    }

    /**
     * Gives up on a resource that cannot be read: a picture keeps its place as an unavailable marker
     * (no {@code src}, so nothing can be fetched; the PDF renderer prints a box), anything else is removed.
     */
    private static void markOrRemoveUnresolved(Element element) {
        if (element.normalName().equals("img")) {
            element.removeAttr(ElementAttribute.src.name()).removeAttr("srcset").attr(UNAVAILABLE_IMAGE_ATTRIBUTE, "");
        } else {
            element.remove();
        }
    }

    /**
     * A translated path as an encoded {@code file:} URI, so a directory or file name with a space or an
     * accent loads; {@code data:} URIs and null pass through.
     */
    private static String toFileUri(String translatedPath) {
        if (translatedPath == null || isEmbeddedDataResourcePath(translatedPath)) {
            return translatedPath;
        }
        return Path.of(translatedPath).toUri().toString();
    }

    private static boolean isExternalResourcePath(String path) {
        String normalized = StringUtils.trimToEmpty(path).toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("//")
                || normalized.startsWith("ftp://")
                || normalized.startsWith("file:");
    }

    private static boolean isEmbeddedDataResourcePath(String path) {
        return StringUtils.trimToEmpty(path).toLowerCase(Locale.ROOT).startsWith("data:");
    }

    private static boolean isDisallowedResourcePath(String path) {
        if (isExternalResourcePath(path)) {
            return true;
        }

        if (isEmbeddedDataResourcePath(path)) {
            return false;
        }

        String normalized = StringUtils.trimToEmpty(path);
        if (normalized.isEmpty()) {
            return false;
        }

        try {
            return Path.of(normalized).isAbsolute();
        } catch (InvalidPathException e) {
            logger.debug("Skipping malformed resource path", e);
            return true;
        }
    }

    /**
     * Feed this method a filename, it will return a full path to the Oscar images directory.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private static String buildImageDirectoryPath(String filename) {
        try {
            File imageDirectory = PathValidationUtils.resolveConfiguredDirectory(getImageDirectory(), "image directory");
            return PathValidationUtils.validateExistingPath(new File(imageDirectory, filename), imageDirectory).getPath();
        } catch (SecurityException e) {
            logger.warn("Skipping invalid image resource path", e);
            return "";
        }
    }

    /**
     * Convert a given URI into a file system absolute path.
     *
     * @param uri URI input
     * @return String fully resolved absolute path
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private static String getRealPath(String uri) {
        String contextRealPath = "";

        // Try to resolve relative paths
        if (ConvertToEdoc.realPath != null) {
            try {
				Path basePath = PathValidationUtils.resolveConfiguredDirectory(ConvertToEdoc.realPath, "real path").toPath();
				String fileNameToFind = PathValidationUtils.validatePathComponent(Paths.get(uri).getFileName().toString(), "resource file name");

				try (Stream<Path> paths = Files.walk(basePath)) {
					Path found = paths
						.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().equals(fileNameToFind))
						.findFirst()
						.orElse(null);

					if (found != null) {
						contextRealPath = PathValidationUtils.validateExistingPath(found.toFile(), basePath.toFile()).getAbsolutePath();
					}
				}
			} catch (Exception e) {
				logger.error("Error while searching file in directory: " + ConvertToEdoc.realPath, e);
			}
        }

        return contextRealPath;
    }

    /**
     * remove paths to the filesystem or to external sources that are not valid.
     */
    private static void validateResourcePaths(Document document) {
        Map<Element, List<String>> pathTranslationMap = new LinkedHashMap<>();
        translateLinkPaths(document, pathTranslationMap);
        translateImagePaths(document, pathTranslationMap);
        translateJavascriptPaths(document, pathTranslationMap);
        for (Map.Entry<Element, List<String>> pathSet : pathTranslationMap.entrySet()) {
            Element element = pathSet.getKey();
            List<String> paths = pathSet.getValue();
            if (validateLink(paths) == null) {
                // vamoose (a picture stays as an unavailable marker)
                markOrRemoveUnresolved(element);
            }
        }
    }

    /**
     * Returns a List of valid file links from a list of potential valid links.
     */
    private static List<String> validateLinks(List<String> potentialLinks) {

        List<String> finalLinks = null;
        String validLink;

        for (String potentialLink : potentialLinks) {
            if (potentialLink.isEmpty()) {
                continue;
            }

            validLink = validateLink(potentialLink);

            if (finalLinks == null && validLink != null) {
                finalLinks = new ArrayList<>();
            }

            if (validLink != null) {
                finalLinks.add(validLink);
            }
        }

        return finalLinks;
    }

    /**
     * Returns the first valid file link from a list of potential valid links.
     * Used in conjunction with as an overload method.
     * See use in methods: setParameterInjectedCss() and validateLinks
     *
     * @param potentialLinks Collection of links to validate. A Collection is
     *                       as a wrapper for a single link ie: potentialLinks.get(0)
     * @return String a single valid link
     */
    private static String validateLink(List<String> potentialLinks) {

        logger.debug("Validating potential file paths " + potentialLinks);

        List<String> validLinks = validateLinks(potentialLinks);

        if (validLinks != null) {
            return validLinks.get(0);
        }

        return null;
    }

    /**
     * Main link validation method
     * See overload above
     *
     * @param potentialLink String link to validate
     * @return String valid link
     */
    // FindSecBugs PATH_TRAVERSAL_IN: link comes from src/href attributes of server-rendered (template-generated) XHTML, bounded upstream by buildImageDirectoryPath/getRealPath; not request input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "link comes from src/href attributes of server-rendered (template-generated) XHTML, bounded upstream; not request input")
    private static String validateLink(String potentialLink) {

        String absolutePath = null;
        Path path = null;

        potentialLink = filterFileType(potentialLink);

        if (potentialLink != null) {
            try {
                path = PathValidationUtils.resolveTrustedPath(new File(potentialLink)).toPath();
            } catch (SecurityException e) {
                // Best-effort link resolver: an unresolvable/malformed path is treated as "no valid
                // local file" (returns null below) rather than throwing an unchecked exception.
                logger.debug("Ignoring unresolvable link path", e);
            }
        }

        if (path != null && Files.exists(path)) {
            absolutePath = path.toAbsolutePath().toString();

            logger.debug("Validated path " + absolutePath);
        }

        return absolutePath;
    }

    /**
     * File type filtering.  Links with invalid filetypes
     * will be removed.
     * See Enum: ConvertToEdoc FileType for complete list.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static String filterFileType(String path) {
        String pathFileType = FilenameUtils.getExtension(path);
        for (FileType legalFileType : FileType.values()) {
            if (legalFileType.name().equalsIgnoreCase(pathFileType)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Clean up any artifacts or poorly formed XHTML
     * and fetch the HTML template resources.
     */
    static synchronized String tidyDocument(final String documentString) {
        Document document = getDocument(documentString);

        /*
         * Use the w3c Document output to interpret the external image
         * and css links into absolute links that can be
         * read by the HTML-to-PDF service.
         */
        translateResourcePaths(document);
        addCss(document);

        /*
         * Convert edited Document object back to String,
         * which is the input the HTML-to-PDF service takes.
         */
        return documentToString(document);
    }

    static synchronized String tidyDocument(final String documentString, String realPath) {
        ConvertToEdoc.realPath = realPath;
        return tidyDocument(documentString);
    }

    /**
     * Fetches the configured EForm image directory in the host file system.
     *
     * <p>Resolved live on each call (via {@link CarlosProperties#getEformImageDirectory()}) rather
     * than captured in a load-time {@code static final}, so a reconfigured image root is picked up
     * without a redeploy and a test can point it at an isolated temporary directory.</p>
     *
     * @return String directory path
     */
    private static String getImageDirectory() {
        return CarlosProperties.getInstance().getEformImageDirectory();
    }

    /**
     * Converts a Jsoup Document object back to its HTML string representation.
     *
     * @param document Document the Jsoup DOM to serialize
     * @return String the HTML output
     */
    public static String documentToString(Document document) {
        return document.outerHtml();
    }

}
