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
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.documentManager.LocalOnlyUserAgent;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.layout.SharedContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
    private static final int INTERNAL_FETCH_CONNECT_TIMEOUT_MS = 5000;
    private static final int INTERNAL_FETCH_READ_TIMEOUT_MS = 30000;
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /**
     * Sends an HTTP 500 error response if the response has not yet been committed.
     * Used by PDF generation methods to signal failure to the client instead of
     * returning a blank page.
     */
    private static void sendErrorIfPossible(HttpServletResponse response) {
        try {
            if (response != null && !response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "PDF generation failed");
            }
        } catch (IOException ioEx) {
            logger.error("Could not send error response", ioEx);
        }
    }

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
     * @throws org.openpdf.text.DocumentException if the PDF document structure cannot be created
     * @since 2026-03-04
     */
    private static void renderWithFlyingSaucer(String html, OutputStream os, String baseUrl) throws org.openpdf.text.DocumentException {
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
            BufferedInputStream in = openValidatedInternalFetch(request, jsessionid, uri);
            if (in == null) {
                throw new IOException("Failed to fetch JSP content from the given URI");
            }

            // Parse directly from InputStream with UTF-8 encoding and base URI
            org.jsoup.nodes.Document doc = Jsoup.parse(in, StandardCharsets.UTF_8.name(), uri);
            configureJsoupForXhtml(doc);

            String cleanHtml = doc.html();
            MiscUtils.getLogger().debug("Parsed HTML content, length: {} chars", cleanHtml.length());
            String documentTxt = AddAbsoluteTag(request, cleanHtml, uri);

            PrintPDFFromHTMLString(response, documentTxt, getFileBaseUrl(request));

        } catch (Exception e) {
            logger.error("Failed to convert JSP to PDF for URI: {}", uri, e);
            sendErrorIfPossible(response);
        }

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
            logger.error("Failed to convert HTML string to PDF", e);
            sendErrorIfPossible(response);
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
            logger.error("Failed to convert HTML string to Base64 PDF binary", e);
            return null;
        }

    }

    /**
     * Saves binary PDF data to a file on disk.
     *
     * @param fileName String the output file path
     * @param docBin String the binary PDF data to write
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public static void SavePDF2File(String fileName, String docBin) {
        try (FileOutputStream ostream = new FileOutputStream(PathValidationUtils.resolveTrustedPath(new File(fileName)));
             ObjectOutputStream p = new ObjectOutputStream(ostream)) {
            p.writeBytes(docBin);
            p.flush();
        } catch (IOException ioe) {
            logger.error("Failed to save PDF to file: {}", fileName, ioe);
        }
    }

    /**
     * Opens an internal same-application HTTP(S) connection with session authentication.
     *
     * <p>This legacy path is used only for server-side rendering of application JSPs
     * into PDF. The target URI must use {@code http} or {@code https}, target a
     * loopback/current local connector host and port, and stay under the current
     * servlet context path. External hosts, alternate schemes, fragments, user-info,
     * and cross-context paths are rejected before opening a connection.</p>
     *
     * @param jsessionid String the session ID appended to the URI for authentication
     * @param uri String the target URI to fetch
     * @return BufferedInputStream the response body, or null if the connection fails
     */
    public static BufferedInputStream GetInputFromURI(String jsessionid, String uri) {
        if (jsessionid == null && uri == null) {
            logger.warn("Blocked legacy Doc2PDF server-side fetch without request context and target data");
        } else {
            logger.warn("Blocked legacy Doc2PDF server-side fetch without request context");
        }
        return null;
    }

    /**
     * Opens an internal same-application HTTP(S) connection with session authentication.
     *
     * @param request HttpServletRequest providing the allowed server name, port, and context path
     * @param jsessionid String the session ID appended to the URI for authentication
     * @param uri String the target URI to fetch
     * @return BufferedInputStream the response body, or null if the connection fails
     */
    // FindSecBugs URLCONNECTION_SSRF_FD: validateInternalFetchUri rejects external hosts/schemes before openConnection.
    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "validateInternalFetchUri enforces same-application http(s) targets before opening a connection")
    static BufferedInputStream openValidatedInternalFetch(HttpServletRequest request, String jsessionid, String uri) {

        HttpURLConnection conn = null;
        try {
            // Append jsessionid to the URL path because this is a server-side HTTP connection
            // that does not share the browser's session cookie; the session ID must be passed
            // via URL rewriting so the target JSP executes within the user's authenticated session.
            URI validatedUri = validateInternalFetchUri(request, uri);
            URL url = appendSessionId(validatedUri, jsessionid).toURL();

            MiscUtils.getLogger().debug("Opening internal Doc2PDF fetch for validated same-application target");

            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(INTERNAL_FETCH_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(INTERNAL_FETCH_READ_TIMEOUT_MS);

            // Wrap the stream so that closing it also disconnects the HTTP connection,
            // preventing connection pool leaks when the caller closes the returned stream.
            final HttpURLConnection connection = conn;
            InputStream wrapped = new FilterInputStream(new BufferedInputStream(connection.getInputStream())) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        connection.disconnect();
                    }
                }
            };
            return new BufferedInputStream(wrapped);

        } catch (IllegalArgumentException e) {
            logger.warn("Rejected internal Doc2PDF fetch due to validation constraints");
            if (conn != null) {
                conn.disconnect();
            }
            return null;
        } catch (URISyntaxException e) {
            logger.warn("Rejected internal Doc2PDF fetch due to invalid URI syntax");
            return null;
        } catch (Exception e) {
            logger.error("Failed to open HTTP connection to fetch URI content", e);
            if (conn != null) {
                conn.disconnect();
            }
            return null;
        }
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of parsed ASCII URL schemes/hosts in fail-closed SSRF validation")
    private static URI validateInternalFetchUri(HttpServletRequest request, String uri)
            throws URISyntaxException {
        if (request == null) {
            throw new IllegalArgumentException("Request context is required for internal Doc2PDF fetches");
        }
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("Internal Doc2PDF fetch URI must not be empty");
        }

        URI target = new URI(uri).normalize();
        String scheme = target.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Internal Doc2PDF fetch URI must use http or https");
        }
        if (target.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Internal Doc2PDF fetch URI must not include user info");
        }
        if (target.getRawFragment() != null) {
            throw new IllegalArgumentException("Internal Doc2PDF fetch URI must not include a fragment");
        }

        if (!isAllowedInternalHost(request, target.getHost())) {
            throw new IllegalArgumentException("Internal Doc2PDF fetch URI must target the local application connector");
        }

        int targetPort = effectivePort(target.getScheme(), target.getPort());
        int requestPort = effectivePort(request.getScheme(), getRequestLocalPort(request));
        if (targetPort != requestPort) {
            throw new IllegalArgumentException("Internal Doc2PDF fetch URI must target the local application connector port");
        }

        String contextPath = request.getContextPath();
        if (contextPath == null) {
            contextPath = "";
        }
        String targetPath = target.getPath();
        if (targetPath == null || targetPath.isEmpty()) {
            targetPath = "/";
        }
        if (!contextPath.isEmpty()
                && !targetPath.equals(contextPath)
                && !targetPath.startsWith(contextPath + "/")) {
            throw new IllegalArgumentException("Internal Doc2PDF fetch URI must stay within the current context path");
        }

        return target;
    }

    private static boolean isAllowedInternalHost(HttpServletRequest request, String targetHost) {
        if (targetHost == null || targetHost.trim().isEmpty()) {
            return false;
        }

        String normalizedTarget = normalizeHost(targetHost);
        if (isLoopbackHost(normalizedTarget)) {
            return true;
        }

        return matchesHost(normalizedTarget, request.getLocalName())
                || matchesHost(normalizedTarget, request.getLocalAddr());
    }

    private static boolean matchesHost(String normalizedTarget, String candidate) {
        return candidate != null
                && !candidate.trim().isEmpty()
                && normalizedTarget.equals(normalizeHost(candidate));
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-folding parsed hostnames for same-connector comparison; not user identity or authorization")
    private static String normalizeHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isLoopbackHost(String normalizedHost) {
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "0:0:0:0:0:0:0:1".equals(normalizedHost);
    }

    private static int getRequestLocalPort(HttpServletRequest request) {
        int localPort = request.getLocalPort();
        if (localPort > 0) {
            return localPort;
        }
        return request.getServerPort();
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-folding parsed ASCII URL schemes for default port selection")
    private static int effectivePort(String scheme, int port) {
        if (port > 0) {
            return port;
        }
        String normalizedScheme = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        if ("https".equals(normalizedScheme)) {
            return 443;
        }
        if ("http".equals(normalizedScheme)) {
            return 80;
        }
        return port;
    }

    private static URI appendSessionId(URI uri, String jsessionid) throws URISyntaxException {
        if (jsessionid == null || jsessionid.trim().isEmpty()) {
            throw new URISyntaxException(String.valueOf(uri), "JSESSIONID must not be empty");
        }

        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            rawPath = "/";
        }
        String pathWithSession = rawPath + ";jsessionid=" + encodePathSegment(jsessionid);
        StringBuilder rewrittenUri = new StringBuilder()
                .append(uri.getScheme())
                .append("://")
                .append(uri.getRawAuthority())
                .append(pathWithSession);
        if (uri.getRawQuery() != null) {
            rewrittenUri.append('?').append(uri.getRawQuery());
        }
        return new URI(rewrittenUri.toString());
    }

    private static String encodePathSegment(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (isUnreservedPathByte(c)) {
                encoded.append((char) c);
            } else {
                encoded.append('%');
                encoded.append(HEX_DIGITS[(c >> 4) & 0x0f]);
                encoded.append(HEX_DIGITS[c & 0x0f]);
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreservedPathByte(int c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '-'
                || c == '.'
                || c == '_'
                || c == '~';
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
            logger.error("Failed to render HTML to Base64 PDF binary", e);
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
            logger.error("Failed to decode and print Base64 PDF", e);
            sendErrorIfPossible(response);
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
            logger.error("Failed to write PDF bytes to HTTP response", e);
            sendErrorIfPossible(response);
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
            logger.error("Failed to render HTML string to PDF", e);
            sendErrorIfPossible(response);
        }
    }

    /**
     * Strips leading slashes from {@code src} attributes so they become relative paths
     * that Flying Saucer resolves against the {@code file://} base URL passed to
     * {@link #renderWithFlyingSaucer}.
     *
     * <p>Note: The method name {@code AddAbsoluteTag} is a legacy artifact. The original
     * implementation constructed absolute HTTP URLs from src attributes; the current
     * implementation strips leading slashes so paths resolve relatively against a
     * {@code file://} base URL, which avoids HTTP requests blocked by
     * {@link LocalOnlyUserAgent}.</p>
     *
     * @param request HttpServletRequest unused, retained for API compatibility with existing callers
     * @param docText String the HTML content with potentially absolute src attributes
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
