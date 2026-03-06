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


package io.github.carlos_emr.carlos.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.documentManager.LocalOnlyUserAgent;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.layout.SharedContext;

/**
 * HTML-to-PDF conversion utility using Flying Saucer's ITextRenderer.
 *
 * <p>Provides multiple entry points for converting HTML content (from strings, URIs, or JSP
 * output) into PDF documents. Uses Jsoup for HTML parsing and XHTML cleanup, then
 * Flying Saucer ({@link ITextRenderer}) for PDF rendering. Also includes legacy methods
 * for invoking the external {@code htmldoc} command-line tool.</p>
 *
 * <p>Note: Flying Saucer's {@code ITextRenderer} uses OpenPDF (the LGPL fork of iText)
 * as its PDF backend. The "iText" in the class name is a historical artifact from when
 * Flying Saucer used the original iText library.</p>
 *
 * @deprecated Unsafe with potential memory leaks. Consider using
 *     {@link io.github.carlos_emr.carlos.documentManager.ConvertToEdoc} for HTML-to-PDF conversion.
 * @since 2005-07-17
 */
@Deprecated
public class Doc2PDF {
    private static Logger logger = MiscUtils.getLogger();

    /**
     * Configure Jsoup document for XHTML output compatible with Flying Saucer ITextRenderer.
     * This ensures consistent HTML cleaning across all PDF conversion methods.
     *
     * @param doc The Jsoup document to configure
     * @since 2026-01-29
     */
    private static void configureJsoupForXhtml(org.jsoup.nodes.Document doc) {
        doc.outputSettings()
            .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .prettyPrint(false);  // Critical: prevents whitespace issues in Flying Saucer XML parser
    }

    /**
     * Render HTML to PDF using Flying Saucer's ITextRenderer.
     * Mirrors the proven pattern from {@code ConvertToEdoc.fallbackRender()}.
     *
     * <p>Uses {@link LocalOnlyUserAgent} to prevent SSRF — all external network
     * resource fetches ({@code http:}, {@code https:}, {@code ftp:}, protocol-relative
     * {@code //}) are blocked at the transport layer. Only {@code data:} URIs and local
     * file paths are permitted.</p>
     *
     * @param html String containing the HTML content to render
     * @param os OutputStream to write the PDF to
     * @param baseUrl String optional base URL for resolving relative resources (e.g. {@code file:///path/to/webapp/}), or null
     * @throws Exception if rendering fails
     * @since 2026-03-04
     */
    private static void renderWithFlyingSaucer(String html, OutputStream os, String baseUrl) throws Exception {
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        configureJsoupForXhtml(doc);
        doc.outputSettings().charset(StandardCharsets.UTF_8.name());

        // Flying Saucer attempts to execute script content during rendering, causing errors
        doc.select("script").remove();
        // XHTML requires alt attributes on all img elements; missing ones cause validation failures
        doc.select("img:not([alt])").attr("alt", "");
        // Flying Saucer's form renderer crashes on input elements without an explicit type attribute
        doc.select("input:not([type])").attr("type", "text");

        ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer();
        SharedContext sharedContext = renderer.getSharedContext();
        sharedContext.setPrint(true);
        sharedContext.setInteractive(false);
        sharedContext.getTextRenderer().setSmoothingThreshold(0);

        renderer.setDocumentFromString(doc.outerHtml(), baseUrl);
        renderer.layout();
        renderer.createPDF(os, true);
    }

    /**
     * Parses JSP output to PDF and writes it to the HTTP response.
     * Uses Jsoup for HTML parsing with UTF-8 encoding and XHTML output.
     *
     * @param request HttpServletRequest containing request context (protocol, host, port)
     * @param response HttpServletResponse to write the PDF to (sets Content-Type: application/pdf)
     * @param uri String URI of the JSP page to parse
     * @param jsessionid String session ID for authentication
     * @throws RuntimeException if PDF conversion fails
     * @since 2026-01-29
     */
    public static void parseJSP2PDF(HttpServletRequest request, HttpServletResponse response, String uri, String jsessionid) {

        try {

            // Fetch the rendered JSP page via HTTP and parse it into a clean XHTML document
            BufferedInputStream in = GetInputFromURI(jsessionid, uri);

            // Parse directly from InputStream with UTF-8 encoding and base URI
            org.jsoup.nodes.Document doc = Jsoup.parse(in, StandardCharsets.UTF_8.name(), uri);
            configureJsoupForXhtml(doc);

            String cleanHtml = doc.html();
            MiscUtils.getLogger().debug("Parsed HTML content, length: {} chars", cleanHtml.length());
            String documentTxt = AddAbsoluteTag(request, cleanHtml, uri);

            PrintPDFFromHTMLString(response, documentTxt, getFileBaseUrl(request));

        } catch (Exception e) {
            logger.error("Failed to convert JSP to PDF for URI: {}", uri, e);
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "PDF generation failed");
                }
            } catch (IOException ioEx) {
                logger.error("Could not send error response", ioEx);
            }
        }

    }

    /**
     * Converts a file to PDF using the external {@code htmldoc} command-line tool.
     *
     * @param request HttpServletRequest the current request (unused, kept for API compatibility)
     * @param response HttpServletResponse to write the generated PDF to
     * @param filename String the file path to convert
     * @return int the exit status from the htmldoc process (0 = success)
     */
    public static int topdf(HttpServletRequest request, HttpServletResponse response, String filename)// I - Name of file to convert
    {
        String command; // Command string
        Process process; // Process for HTMLDOC
        Runtime runtime; // Local runtime object
        java.io.InputStream input; // Output from HTMLDOC
        int bytes; // Number of bytes

        // Construct the command string
        command = "htmldoc --quiet --webpage -t pdf " + filename;

        try {

            // Run the process and wait for it to complete...
            runtime = Runtime.getRuntime();

            // Create a new HTMLDOC process...
            process = runtime.exec(command);

            // Get stdout from the process and a buffer for the data...
            input = process.getInputStream();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            // Compress the data
            byte[] buf = new byte[1024];

            // Read output from HTMLDOC until we have it all...
            while ((bytes = input.read(buf)) > 0)
                bos.write(buf, 0, bytes);

            PrintPDFFromBytes(response, bos.toByteArray());

            // Return the exit status from HTMLDOC...
            return (process.waitFor());
        } catch (Exception e) {
            // An error occurred - send it to stderr for the www server...
            logger.error(e.toString() + " caught while running:\n\n");
            logger.error("    " + command + "\n");
            logger.error("", e);
            return (1);
        }
    }

    /**
     * Main entry point for the htmldoc external converter. Appends QUERY_STRING if available
     * and delegates to {@link #topdf(HttpServletRequest, HttpServletResponse, String)}.
     *
     * @param request HttpServletRequest the current request
     * @param response HttpServletResponse to write the generated PDF to
     * @param url String the URL of the document to convert
     */
    public static void HTMLDOC(HttpServletRequest request, HttpServletResponse response, String url)// I - Command-line args
    {
        //String server_name, // SERVER_NAME env var
        //server_port, // SERVER_PORT env var
        //path_info, // PATH_INFO env var
        String query_string, // QUERY_STRING env var
                filename; // File to convert

        filename = url;

        if ((query_string = System.getProperty("QUERY_STRING")) != null) {
            filename = filename + "?" + query_string;
        }

        // Convert the file to PDF and send to the www client...
        topdf(request, response, filename);

        return;
    }

    /**
     * Converts an HTML string to PDF and writes it to the HTTP response.
     * Uses Jsoup for HTML parsing with UTF-8 encoding and XHTML output.
     *
     * @param request HttpServletRequest containing request context (protocol, host, port)
     * @param response HttpServletResponse to write the PDF to (sets Content-Type: application/pdf)
     * @param docText String containing the HTML content to convert
     * @throws RuntimeException if PDF conversion fails
     * @since 2026-01-29
     */
    public static void parseString2PDF(HttpServletRequest request, HttpServletResponse response, String docText) {

        try {

            // Parse and clean HTML with Jsoup (handles UTF-8 internally)
            org.jsoup.nodes.Document doc = Jsoup.parse(docText);
            configureJsoupForXhtml(doc);

            String cleanHtml = doc.html();

            PrintPDFFromHTMLString(response, AddAbsoluteTag(request, cleanHtml, ""), getFileBaseUrl(request));

        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }

    }

    /**
     * Converts an HTML string to binary PDF format (Base64-encoded).
     * Uses Jsoup for HTML parsing with UTF-8 encoding and XHTML output.
     *
     * @param request HttpServletRequest containing request context (protocol, host, port)
     * @param response HttpServletResponse (not used but maintained for API compatibility)
     * @param docText String containing the HTML content to convert
     * @return String Base64-encoded PDF binary data, or null if conversion fails
     * @since 2026-01-29
     */
    public static String parseString2Bin(HttpServletRequest request, HttpServletResponse response, String docText) {

        try {

            // Parse and clean HTML with Jsoup (handles UTF-8 internally)
            org.jsoup.nodes.Document doc = Jsoup.parse(docText);
            configureJsoupForXhtml(doc);

            String cleanHtml = doc.html();

            String testFile = GetPDFBin(response, AddAbsoluteTag(request, cleanHtml, ""), getFileBaseUrl(request));

            return testFile;

        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return null;
        }

    }

    /**
     * Saves binary PDF data to a file on disk.
     *
     * @param fileName String the output file path
     * @param docBin String the binary PDF data to write
     */
    public static void SavePDF2File(String fileName, String docBin) {
        try (FileOutputStream ostream = new FileOutputStream(fileName);
             ObjectOutputStream p = new ObjectOutputStream(ostream)) {
            p.writeBytes(docBin);
            p.flush();
        } catch (IOException ioe) {
            logger.error("Failed to save PDF to file: {}", fileName, ioe);
        }
    }

    /**
     * Opens an HTTP connection to the given URI with session authentication and returns the input stream.
     *
     * @param jsessionid String the session ID appended to the URI for authentication
     * @param uri String the target URI to fetch
     * @return BufferedInputStream the response body, or null if the connection fails
     */
    public static BufferedInputStream GetInputFromURI(String jsessionid, String uri) {

        BufferedInputStream in = null;
        try {

                // Append jsessionid to the URL path because this is a server-side HTTP connection
            // that does not share the browser's session cookie; the session ID must be passed
            // via URL rewriting so the target JSP executes within the user's authenticated session.
            URL url = new URI(uri + ";jsessionid=" + jsessionid).toURL();

            MiscUtils.getLogger().debug(" " + uri + ";jsessionid=" + jsessionid);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            in = new BufferedInputStream(conn.getInputStream());

        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
        return in;
    }

    /**
     * Converts an HTML string to a Base64-encoded PDF using Flying Saucer.
     *
     * @param response HttpServletResponse unused but maintained for API compatibility
     * @param docText String the HTML content to convert
     * @param baseUrl String optional {@code file://} base URL for resolving relative resources, or null
     * @return String Base64-encoded PDF data, or null if conversion fails
     */
    public static String GetPDFBin(HttpServletResponse response, String docText, String baseUrl) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            renderWithFlyingSaucer(docText, baos, baseUrl);
            return (new String(Base64.encodeBase64(baos.toByteArray())));
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
        return null;
    }

    /**
     * Decodes a Base64-encoded PDF string and writes it to the HTTP response.
     *
     * @param response HttpServletResponse to write the PDF to
     * @param docBin String Base64-encoded PDF data
     */
    public static void PrintPDFFromBin(HttpServletResponse response, String docBin) {

        try {

            byte[] binDecodedArray = Base64.decodeBase64(docBin.getBytes(StandardCharsets.UTF_8));

            PrintPDFFromBytes(response, binDecodedArray);
            return;

        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }

    }

    /**
     * Writes raw PDF bytes to the HTTP response with appropriate headers.
     *
     * <p>Sets no-cache headers and {@code application/pdf} content type before
     * streaming the byte array to the response output.</p>
     *
     * @param response HttpServletResponse to write the PDF to
     * @param docBytes byte[] the raw PDF document bytes
     */
    public static void PrintPDFFromBytes(HttpServletResponse response, byte[] docBytes) {

        try {
            // setting some response headers
            response.setHeader("Expires", "0");
            response.setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
            response.setHeader("Pragma", "public");

            // setting the content type
            response.setContentType("application/pdf");

            OutputStream o = response.getOutputStream();
            response.setContentLength(docBytes.length);

            InputStream is = new BufferedInputStream(new ByteArrayInputStream(docBytes));

            byte[] buf = new byte[32 * 1024]; // 32k buffer

            int nRead = 0;
            while ((nRead = is.read(buf)) != -1) {
                o.write(buf, 0, nRead);
            }

            o.flush();
            o.close(); // *important* to ensure no more jsp output
            return;
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }

    }

    /**
     * Converts an HTML string to PDF using Flying Saucer and writes it to the HTTP response.
     *
     * @param response HttpServletResponse to write the PDF to
     * @param docText String the HTML content to convert and serve
     * @param baseUrl String optional {@code file://} base URL for resolving relative resources, or null
     */
    public static void PrintPDFFromHTMLString(HttpServletResponse response, String docText, String baseUrl) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            renderWithFlyingSaucer(docText, baos, baseUrl);
            PrintPDFFromBytes(response, baos.toByteArray());
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
    }

    /**
     * Converts relative {@code src} attribute paths in HTML to local file paths
     * so that Flying Saucer (with {@link LocalOnlyUserAgent}) can resolve images
     * during PDF rendering without making network requests.
     *
     * <p>Strips leading slashes from {@code src} attributes so they become relative
     * paths that Flying Saucer resolves against the {@code file://} base URL passed
     * to {@link #renderWithFlyingSaucer}. This avoids constructing {@code http://} URLs
     * that would be blocked by the SSRF-safe {@link LocalOnlyUserAgent}.</p>
     *
     * @param request HttpServletRequest providing servlet context for the webapp real path
     * @param docText String the HTML content with potentially relative src attributes
     * @param uri String the original URI (unused in current implementation)
     * @return String the HTML with src attributes cleaned for local file resolution
     */
    public static String AddAbsoluteTag(HttpServletRequest request, String docText, String uri) {

        // Strip leading slashes from src attributes so they become relative paths
        // that Flying Saucer resolves against the file:// base URL
        docText = docText.replaceAll("src='/", "src='");
        docText = docText.replaceAll("src=\"/", "src=\"");
        docText = docText.replaceAll("src=/", "src=");

        return docText;
    }

    /**
     * Builds a {@code file://} base URL from the servlet context's real path on disk.
     * Used as the base URL for Flying Saucer so it resolves relative resource paths
     * (images, CSS) from the deployed webapp directory.
     *
     * @param request HttpServletRequest providing access to the servlet context
     * @return String the {@code file://} base URL, or null if the real path cannot be determined
     */
    static String getFileBaseUrl(HttpServletRequest request) {
        String realPath = request.getServletContext().getRealPath("/");
        if (realPath == null) {
            return null;
        }
        if (!realPath.endsWith("/")) {
            realPath += "/";
        }
        return "file://" + realPath;
    }

    /**
     * Extracts all values between matching XML tags from a string.
     *
     * <p>Performs simple string-based XML parsing to find all occurrences of
     * {@code <section>value</section>} and returns the values. Does not handle
     * self-closing tags ({@code <section />}).</p>
     *
     * @param xml String the XML content to parse
     * @param section String the tag name to search for
     * @return Vector of String values found between matching tags
     * @throws Exception if a closing tag is missing or appears before the opening tag
     */
    public static Vector getXMLTagValue(String xml, String section) throws Exception {
        String xmlString = xml;

        Vector v = new Vector();
        String beginTagToSearch = "<" + section + ">";
        String endTagToSearch = "</" + section + ">";

        // Look for the first occurrence of begin tag
        int index = xmlString.indexOf(beginTagToSearch);

        while (index != -1) {
            // Look for end tag
            // DOES NOT HANDLE <section Blah />
            int lastIndex = xmlString.indexOf(endTagToSearch);

            // Make sure there is no error
            if ((lastIndex == -1) || (lastIndex < index)) throw new Exception("Parse Error");

            // extract the substring
            String subs = xmlString.substring((index + beginTagToSearch.length()), lastIndex);

            // Add it to our list of tag values
            v.addElement(subs);

            // Try it again. Narrow down to the part of string which is not 
            // processed yet.
            try {
                xmlString = xmlString.substring(lastIndex + endTagToSearch.length());
            } catch (Exception e) {
                xmlString = "";
            }

            // Start over again by searching the first occurrence of the begin tag 
            // to continue the loop.

            index = xmlString.indexOf(beginTagToSearch);
        }

        return v;
    }

}
