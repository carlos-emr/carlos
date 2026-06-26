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

import org.openpdf.text.DocumentException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextRenderer;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
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
 *   <li>Translate relative resource paths to absolute file system paths</li>
 *   <li>Primary conversion via {@code InternalEDocConverter} (wkhtmltopdf)</li>
 *   <li>Fallback conversion via Flying Saucer {@code ITextRenderer} with OpenPDF backend</li>
 * </ol>
 *
 * <p>Thread safety: The {@code from()} and {@code saveAsTempPDF()} methods are synchronized
 * to prevent concurrent modification of the shared {@link #realPath} field.
 *
 * @see EDoc
 * @see EDocFactory
 * @see ReplacedElementFactoryImpl
 * @since 2018-10-15
 */
public final class ConvertToEdoc {

    private static final Logger logger = MiscUtils.getLogger();

    public enum DocumentType {eForm, form}

    public enum ElementAttribute {src, href, value, name, id, title, type, rel, media}

    private enum FileType {pdf, css, jpeg, png, gif, js, jpg}

    public static final String CUSTOM_STYLESHEET_ID = "pdfMediaStylesheet";
    private static final String DEFAULT_IMAGE_DIRECTORY = String.format("%1$s", CarlosProperties.getInstance().getEformImageDirectory());
    private static final String DEFAULT_FILENAME = "temporaryPDF";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_CONTENT_TYPE = "application/pdf";
    private static final String SYSTEM_ID = "-1";
    private static final String DEFAULT_WKHTMLTOPDF_COMMAND = "/usr/bin/wkhtmltopdf";
    private static final String DEFAULT_WKHTMLTOPDF_ARGS = "--enable-local-file-access --minimum-font-size 10 --print-media-type --encoding utf-8 -T 10mm -L 8mm -R 8mm --disable-javascript";
    private static final String BACKGROUND_ATTRIBUTE = "background";
    private static final String STYLE_ATTRIBUTE = "style";
    private static String realPath;
    private static final NioFileManager nioFileManager = SpringUtils.getBean(NioFileManager.class);

    /**
     * Converts an EForm into a PDF and returns an EDoc wrapping the result.
     * The PDF is saved to a temporary file path. The caller should move the file
     * to persistent storage before saving the EDoc to the database.
     *
     * @param eform EFormData the electronic form data to convert
     * @return EDoc the document object referencing the generated PDF, or null if conversion fails
     */
    public synchronized static EDoc from(EFormData eform) {

        String eformString = eform.getFormData();
        String demographicNo = eform.getDemographicId() + "";
        String filename = buildFilename(eform.getFormName(), demographicNo);
        String eDocDescription = eform.getSubject().trim().isEmpty() ? eform.getFormName() : eform.getSubject();
        EDoc edoc = null;
        Path path = execute(eformString, filename);

        if (path != null && Files.isReadable(path)) {
            edoc = buildEDoc(path.getFileName().toString(),
                    eDocDescription,
                    null,
                    eform.getProviderNo(),
                    demographicNo,
                    DocumentType.eForm,
                    path.getParent().toString());
        } else {
            logger.error("Could not read temporary PDF file " + filename);
        }

        return edoc;
    }

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
        String eDocDescription = eForm.getSubject().trim().isEmpty() ? eForm.getFormName() : eForm.getSubject();
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
     * Converts a Form (via its transport container) into a PDF and returns an EDoc.
     * The PDF is saved to a temporary file path. The caller should move the file
     * to persistent storage before saving the EDoc to the database.
     *
     * <p>Example usage:
     * <pre>{@code
     * FormTransportContainer ftc = new FormTransportContainer(response, request, forwardPath);
     * ftc.setDemographicNo(bpmh.getDemographicNo());
     * ftc.setProviderNo(bpmh.getProvider().getProviderNo());
     * ftc.setSubject("BPMH Form ID " + bpmh.getFormId());
     * ftc.setFormName("bpmh");
     * ftc.setRealPath(getServlet().getServletContext().getRealPath(File.separator));
     * FormsManager.saveFormDataAsEDoc(loggedInInfo, ftc);
     * }</pre>
     *
     * @param formTransportContainer FormTransportContainer containing form HTML, metadata, and real path
     * @return EDoc the document object referencing the generated PDF, or null if conversion fails
     */
    public synchronized static EDoc from(FormTransportContainer formTransportContainer) {

        String htmlString = formTransportContainer.getHTML();
        String demographicNo = formTransportContainer.getDemographicNo();
        String filename = buildFilename(formTransportContainer.getFormName(), demographicNo);
        String subject = formTransportContainer.getSubject();
        String providerNo = formTransportContainer.getProviderNo();
        if (providerNo == null) {
            providerNo = formTransportContainer.getLoggedInInfo().getLoggedInProviderNo();
        }
        // this should be the same for every thread.
        ConvertToEdoc.realPath = formTransportContainer.getRealPath();

        EDoc edoc = null;
        Path path = execute(htmlString, filename);

        if (path != null && Files.isReadable(path)) {
            edoc = buildEDoc(filename,
                    subject,
                    null,
                    providerNo,
                    demographicNo,
                    formTransportContainer.getDocumentType(),
                    path.toString());
        } else {
            logger.error("Could not read temporary PDF file " + filename);
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
        return execute(eformString, filename);
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
        return execute(htmlString, filename);
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
        return execute(htmlString, filename);
    }

    /**
     * Executes the HTML-to-PDF conversion pipeline and saves the result to the temp directory.
     *
     * @param eformString String the raw HTML content to convert
     * @param filename String the base filename for the output PDF
     * @return Path the path to the saved temporary PDF, or null if conversion fails
     */
    private static Path execute(final String eformString, final String filename) {
        Path path = null;
        String document = tidyDocument(eformString);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            renderPDF(document, os);
            path = nioFileManager.saveTempFile(filename, os);
        } catch (DocumentException e1) {
            logger.error("Exception parsing file to PDF. File not saved. ", e1);
        } catch (IOException e) {
            logger.error("Problem while writing PDF file to filesystem. " + filename, e);
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
     * Renders the HTML document string to a PDF output stream. Uses the primary converter
     * (wkhtmltopdf via InternalEDocConverter) first; on failure, falls back to Flying Saucer
     * with OpenPDF backend. Flying Saucer requires well-formed XHTML, so the fallback path
     * performs additional document preparation via Jsoup.
     *
     * @param document String the cleaned HTML document string
     * @param os ByteArrayOutputStream the output stream to write the PDF to
     * @throws DocumentException if both primary and fallback PDF conversion fail
     * @throws IOException if an I/O error occurs during rendering
     */
    private static void renderPDF(final String document, ByteArrayOutputStream os)
            throws DocumentException, IOException {
        EDocConverterInterface converter = new InternalEDocConverter();

        try {
            converter.convert(document, os);
        } catch (Exception e) {
            logger.warn("Primary PDF conversion failed, attempting fallback: " + e.getMessage());

            try {
                os.reset();
                fallbackRender(document, os);
            } catch (Exception fallbackError) {
                logger.error("Fallback PDF conversion also failed", fallbackError);
                String combinedMessage = "PDF conversion failed with all methods. "
                        + "Primary error: " + e.getMessage()
                        + "; Fallback error: " + fallbackError.getMessage();
                DocumentException docEx = new DocumentException(combinedMessage);
                docEx.initCause(fallbackError);
                throw docEx;
            }
        }
    }

    /**
     * Fallback PDF renderer using Flying Saucer ITextRenderer with OpenPDF backend.
     * Prepares the document for Flying Saucer's strict XHTML requirements and
     * uses a custom {@link ReplacedElementFactoryImpl} for image scaling.
     *
     * <p>Uses {@link LocalOnlyUserAgent} to prevent SSRF — all external network
     * resource fetches ({@code http:}, {@code https:}, {@code ftp:}, protocol-relative
     * {@code //}) are blocked at the transport layer. This closes the CSS {@code @import}
     * and inline {@code style} {@code url()} gaps that DOM-level stripping cannot cover.
     * The existing DOM-level stripping in {@link #translatePaths} remains as defense-in-depth.</p>
     *
     * @param document String the HTML document string
     * @param os OutputStream the output stream to write the PDF to
     * @throws DocumentException if PDF creation fails
     * @throws IOException if an I/O error occurs
     */
    private static void fallbackRender(String document, OutputStream os)
        throws DocumentException, IOException {
        // Prepare document for Flying Saucer's strict XHTML requirements
        Document doc = prepareDocumentForFlyingSaucer(document);
        
        ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer();
        SharedContext sharedContext = renderer.getSharedContext();
        sharedContext.setPrint(true);
        sharedContext.setInteractive(false);
        sharedContext.setReplacedElementFactory(new ReplacedElementFactoryImpl());
        sharedContext.getTextRenderer().setSmoothingThreshold(0);
        
        renderer.setDocumentFromString(doc.outerHtml(), null);
        renderer.layout();
        renderer.createPDF(os, true);
    }

    /**
     * Prepare document for Flying Saucer which requires strict XHTML
     */
    static Document prepareDocumentForFlyingSaucer(String document) {
        Document doc = Jsoup.parse(document);
        normalizeHtmlCommentsForXml(doc);
        
        // Flying Saucer requires XML/XHTML syntax
        doc.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)  // Self-closes tags automatically
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset("UTF-8")
            .prettyPrint(false);
        
        // Remove scripts (Flying Saucer can't execute them)
        doc.select("script").remove();
        
        // Ensure img tags have alt attributes (XHTML requirement)
        doc.select("img:not([alt])").attr("alt", "");
        
        // Ensure input tags have type attribute
        doc.select("input:not([type])").attr("type", "text");
        
        return doc;
    }

    private static void normalizeHtmlCommentsForXml(Node root) {
        if (root == null) {
            return;
        }

        for (Node child : root.childNodes()) {
            if (child instanceof Comment comment) {
                String normalized = comment.getData().replace("--", "- -");
                if (normalized.endsWith("-")) {
                    normalized += " ";
                }
                comment.setData(normalized);
            }
            normalizeHtmlCommentsForXml(child);
        }
    }

    /**
     * Parses and cleans an HTML document string using the specified real path for resource resolution.
     *
     * @param documentString String the raw HTML content
     * @param realPath String the servlet context real path for resolving relative resource paths
     * @return Document the parsed and cleaned Jsoup DOM
     */
    public static Document getDocument(final String documentString, String realPath) {
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
    public static Document getDocument(String documentString) {
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
            linkElement.attr("href", filename);
            getHeadElement(document).appendChild(linkElement);
        }
    }

    /**
     * HTTP request paths routed through Struts need to be
     * translated into an absolute path to the global image directory.
     */
    private static void translateResourcePaths(Document document) {
        Map<List<String>, Element> pathTranslationMap = new HashMap<>();
        translateLinkPaths(document, pathTranslationMap);
        translateImagePaths(document, pathTranslationMap);
        translateBackgroundAttributes(document);
        translateInlineStylePaths(document);
        translateEmbeddedStylesheetPaths(document);

        for (Map.Entry<List<String>, Element> pathSet : pathTranslationMap.entrySet()) {
            if (!pathSet.getKey().isEmpty()) {
                Element element = pathSet.getValue();
                List<String> path = pathSet.getKey();

                if (element.hasAttr(ElementAttribute.href.name())) {
                    element.attr(ElementAttribute.href.name(), path.get(0));
                }

                if (element.hasAttr(ElementAttribute.src.name())) {
                    element.attr(ElementAttribute.src.name(), path.get(0));
                }
            }
        }
    }

    /**
     * CSS (link) resource links
     */
    private static void translateLinkPaths(Document document, Map<List<String>, Element> pathTranslationMap) {
        Elements linkNodeList = document.getElementsByTag("link");
        translatePaths(linkNodeList, ElementAttribute.href, pathTranslationMap);
    }

    /**
     * Javascript resource links
     */
    private static void translateJavascriptPaths(Document document, Map<List<String>, Element> pathTranslationMap) {
        Elements linkNodeList = document.getElementsByTag("script");
        translatePaths(linkNodeList, ElementAttribute.src, pathTranslationMap);
    }

    /**
     * Image resource links
     */
    private static void translateImagePaths(Document document, Map<List<String>, Element> pathTranslationMap) {
        Elements imageNodeList = document.getElementsByTag("img");
        translatePaths(imageNodeList, ElementAttribute.src, pathTranslationMap);
    }

    private static void translateBackgroundAttributes(Document document) {
        for (Element element : document.select("[" + BACKGROUND_ATTRIBUTE + "]")) {
            String originalPath = element.attr(BACKGROUND_ATTRIBUTE);
            String translatedPath = translateSingleResourcePath(originalPath);
            if (translatedPath != null) {
                element.attr(BACKGROUND_ATTRIBUTE, translatedPath);
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
        while (cursor < cssText.length()) {
            int urlStart = StringUtils.indexOfIgnoreCase(cssText, "url(", cursor);
            if (urlStart < 0) {
                rewrittenCss.append(cssText, cursor, cssText.length());
                break;
            }

            rewrittenCss.append(cssText, cursor, urlStart);

            int contentStart = urlStart + 4;
            while (contentStart < cssText.length() && Character.isWhitespace(cssText.charAt(contentStart))) {
                contentStart++;
            }

            int urlEnd = findCssUrlEnd(cssText, contentStart);
            if (urlEnd < 0) {
                rewrittenCss.append(cssText, urlStart, cssText.length());
                break;
            }

            String originalPath = extractCssUrlPath(cssText, contentStart, urlEnd);
            String translatedPath = translateSingleResourcePath(originalPath);
            if (translatedPath != null) {
                rewrittenCss.append("url('").append(translatedPath).append("')");
            } else if (isEmbeddedDataResourcePath(originalPath)) {
                rewrittenCss.append(cssText, urlStart, urlEnd + 1);
            } else {
                rewrittenCss.append("url('')");
            }
            cursor = urlEnd + 1;
        }
        return rewrittenCss.toString();
    }

    private static int findCssUrlEnd(String cssText, int contentStart) {
        char quote = 0;
        int nestedParens = 0;
        for (int i = contentStart; i < cssText.length(); i++) {
            char current = cssText.charAt(i);
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }

            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }

            if (current == '(') {
                nestedParens++;
                continue;
            }

            if (current == ')') {
                if (nestedParens == 0) {
                    return i;
                }
                nestedParens--;
            }
        }
        return -1;
    }

    private static String extractCssUrlPath(String cssText, int contentStart, int urlEnd) {
        String rawPath = cssText.substring(contentStart, urlEnd).trim();
        if (rawPath.length() >= 2) {
            char first = rawPath.charAt(0);
            char last = rawPath.charAt(rawPath.length() - 1);
            if ((first == '\'' || first == '"') && first == last) {
                rawPath = rawPath.substring(1, rawPath.length() - 1).trim();
            }
        }
        return rawPath;
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
        }

        return potentialFilePaths;
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
     * TODO filter out relative URI's and external URL's without a proper place holder so they can be removed
     *
     * @param nodeList           jsoup Elements collection of potential resource links
     * @param pathAttribute      jsoup ElementAttribute to be translated to an absolute link
     * @param pathTranslationMap Map to collect translated URI's
     */
    private static void translatePaths(Elements nodeList, ElementAttribute pathAttribute, Map<List<String>, Element> pathTranslationMap) {
        for (Element element : nodeList) {
            if (element.hasAttr(pathAttribute.name())) {
                String path = element.attributes().get(pathAttribute.name());
                if (isExternalResourcePath(path)) {
                    element.remove();
                } else if (!isEmbeddedDataResourcePath(path)) {
                    List<String> potentialFilePaths = collectPotentialFilePaths(path);
                    if (!potentialFilePaths.isEmpty()) {
                        pathTranslationMap.put(potentialFilePaths, element);
                    } else {
                        element.remove();
                    }
                }
            }
        }
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
        Map<List<String>, Element> pathTranslationMap = new HashMap<>();
        translateLinkPaths(document, pathTranslationMap);
        translateImagePaths(document, pathTranslationMap);
        translateJavascriptPaths(document, pathTranslationMap);
        for (Map.Entry<List<String>, Element> pathSet : pathTranslationMap.entrySet()) {
            Element element = pathSet.getValue();
            List<String> paths = pathSet.getKey();
            if (validateLink(paths) == null) {
                // vamoose
                element.remove();
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
    static String tidyDocument(final String documentString) {
        Document document = getDocument(documentString);

        /*
         * Use the w3c Document output to interpret the external image
         * and css links into absolute links that can be
         * read by the HTMLtoPDF parser.
         */
        translateResourcePaths(document);
        addCss(document);

        /*
         * Convert edited Document object back to String
         * Mostly because the htmltopdf tools require String input
         * for some strange reason.
         */
        return documentToString(document);
    }

    static String tidyDocument(final String documentString, String realPath) {
        ConvertToEdoc.realPath = realPath;
        return tidyDocument(documentString);
    }

    /**
     * fetch the default EForm image directory in the host file system
     *
     * @return String directory path
     */
    private static String getImageDirectory() {
        return DEFAULT_IMAGE_DIRECTORY;
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
