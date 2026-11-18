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
package io.github.carlos_emr.carlos.commn.printing;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.OptionalLong;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.DeferredContentLength;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.RequestNegotiation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Filter class for handling confidentiality note printing. This class works by appending a confidentiality note
 * retrieved from the global OSCAR properties and appending it to the end of an HTML page.
 *
 * <p></p>
 * <p>
 * When outputting the statement, the filter uses the following conventions:
 *
 * <ul>
 *
 * 	<li>The statement is outputted for HTML requests that result in generation of HTML content. This is done by checking the response type being created.</li>
 * 	<li>The statement is outputted only once per request, using a request attribute
 * to guard against repeated decoration. No session attribute is used.</li>
 * 	<li>The statement is not outputted for AJAX-based requests, detected with
 * {@link io.github.carlos_emr.carlos.utility.RequestNegotiation#isAjax}. That reads
 * {@code X-Requested-With} as a list, because CSRFGuard's client script appends its own marker
 * to whatever jQuery already set.</li>
 *
 * </ul>
 *
 * <p></p>
 * <p>
 * Please note that this filter results in not well-formed HTML content being outputted to the browser, which in turn
 * results in page rendered in Quirks mode.
 */
public class PrivacyStatementAppendingFilter implements Filter {

    private static final int APPEND_BUFFER_SIZE_BYTES = 1024 * 1024;

    /**
     * @deprecated the AJAX decision moved to
     *     {@link io.github.carlos_emr.carlos.utility.RequestNegotiation#isAjax}, which matches
     *     this value as one entry in a list rather than as the whole header. Kept because both
     *     constants are public API; comparing a raw header against them is what this filter
     *     stopped doing.
     */
    @Deprecated
    public static final String HTTP_HEADER_VALUE_AJAX_REQUESTED_WITH = "XMLHttpRequest";
    /** @deprecated see {@link #HTTP_HEADER_VALUE_AJAX_REQUESTED_WITH}. */
    @Deprecated
    public static final String HTTP_HEADER_NAME_AJAX_REQUESTED_WITH = "X-Requested-With";
    public static final String ATTRIBUTE_NAME_CONFIDENTIALITY_NOTE_PRINTED = "CONFIDENTIALITY_NOTE_PRINTED";

    private Set<String> exclusions = Collections.synchronizedSet(new HashSet<String>());

    private String getPrivacyStatement() {
        String statement = CarlosProperties.getConfidentialityStatement();
        if (statement == null || statement.trim().isEmpty()) {
            return "";
        }
        // carlos-print-decoration, alongside yesprint: this paragraph is PLATFORM boilerplate the
        // filter appends to every printable page, not content the form author wrote. The eForm
        // render browser measures the page under print-media emulation (where yesprint is
        // display:block), finds this element outside the authored page divs, and — since the
        // completeness gate treats UNMARKED off-page content as clinical and withholds the
        // document — a configured confidentiality statement blocked every eForm download/attach
        // on a packaged install ("Excluded visible elements: 1"). The marker is the gate's own
        // opt-in for exactly this kind of boilerplate: the renderer discloses it as decoration
        // (advisory) instead of withholding, and the class is inert everywhere else — no
        // stylesheet outside the renderer binds it.
        return "<style type=\"text/css\"><!--\n" +
                ".yesprint {\n" +
                "	display: none;        \n" +
                "}\n" +
                "@media print {\n" +
                "	.yesprint {\n" +
                "		display:block;\n" +
                "	}\n" +
                "}\n" +
                "--></style>" +
                "<p class=\"yesprint carlos-print-decoration\"><b>\n" +
                statement +
                "</b><br/>" +
                "<b>END OF PRINTED DOCUMENT</b>" +
                "</p>";
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String exclusionsParam = filterConfig.getInitParameter("exclusions");
        if (exclusionsParam == null || exclusionsParam.trim().isEmpty()) {
            return;
        }

        for (String ex : exclusionsParam.split(",")) {
            exclusions.add(ex.toLowerCase().trim());
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        boolean isConfidentialityNotePrinted = false;
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isExcluded(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // check if we are the first to print the request here
            isConfidentialityNotePrinted = isConfidentialtyNotePrinted(httpRequest);
            String statement = isConfidentialityNotePrinted ? "" : getPrivacyStatement();
            HttpServletRequest downstreamRequest = httpRequest;
            boolean staticHtmlVariant = !statement.isEmpty()
                    && ("GET".equals(httpRequest.getMethod()) || "HEAD".equals(httpRequest.getMethod()))
                    && hasHtmlResourceType(httpRequest);
            boolean decoratedStaticHtml = staticHtmlVariant && !RequestNegotiation.isAjax(httpRequest);
            if (decoratedStaticHtml) {
                // A static file's validators omit the configured print statement. Prevent
                // DefaultServlet from returning 304 for that different representation.
                downstreamRequest = new PrintStatementRequest(httpRequest);
            }

            DelegatingServletResponse delegatingServletResponse = new DelegatingServletResponse(staticHtmlVariant
                    ? new StaticHtmlMetadataResponse(httpResponse, decoratedStaticHtml) : httpResponse);
            chain.doFilter(downstreamRequest, delegatingServletResponse);

            if (decoratedStaticHtml && delegatingServletResponse.getStatus() == HttpServletResponse.SC_NOT_MODIFIED) {
                // A wildcard existence condition can still produce 304. Its metadata
                // must not advertise the undecorated file's validators or length.
                delegatingServletResponse.discardDeferredContentLength();
                discardBareFileMetadata(delegatingServletResponse);
                delegatingServletResponse.completeWithoutStatement();
                return;
            }

            // A partial response must retain the bytes and length described by Content-Range.
            if (delegatingServletResponse.getStatus() == HttpServletResponse.SC_PARTIAL_CONTENT) {
                delegatingServletResponse.completeWithoutStatement();
                return;
            }

            if (isConfidentialityNotePrinted) {
                delegatingServletResponse.completeWithoutStatement();
                return;
            }

            // ignore this stuff for non-html responses and AJAX queries
            String contentType = delegatingServletResponse.getContentType();
            if (contentType == null) {
                delegatingServletResponse.completeWithoutStatement();
                return;
            }
            if (!RequestNegotiation.isHtmlContentType(contentType)) {
                delegatingServletResponse.completeWithoutStatement();
                return;
            }

            // don't append for AJAX queries as well.
            // RequestNegotiation.isAjax, not an exact header match: CSRFGuard's client script
            // appends its own marker to X-Requested-With, so a jQuery $.ajax call reaches this
            // filter as "XMLHttpRequest, OWASP CSRFGuard Project". An equals check classified
            // that as a page request and appended the statement to the AJAX body, which callers
            // that render the reply verbatim then show as stray markup.
            if (RequestNegotiation.isAjax(httpRequest)) {
                delegatingServletResponse.completeWithoutStatement();
                return;
            }

            if (statement.isEmpty()) {
                delegatingServletResponse.completeWithoutStatement();
                return;
            }
            if (!delegatingServletResponse.isResponseWriterObtained()
                    && !delegatingServletResponse.isResponseOutputStreamObtained()) {
                if (decoratedStaticHtml && "HEAD".equals(httpRequest.getMethod())
                        && delegatingServletResponse.getStatus() == HttpServletResponse.SC_OK) {
                    // A static HEAD's bare length omits the statement appended on GET.
                    delegatingServletResponse.discardDeferredContentLength();
                    discardBareFileMetadata(delegatingServletResponse);
                    return;
                }
                delegatingServletResponse.completeWithoutStatement();
                return;
            }
            if (!delegatingServletResponse.prepareAppendBuffer()) {
                delegatingServletResponse.completeWithoutStatement();
                return;
            }
            delegatingServletResponse.discardDeferredContentLength();
            if (delegatingServletResponse.getStatus() == HttpServletResponse.SC_OK) {
                discardBareFileMetadata(delegatingServletResponse);
            }
            printConfidentialityStatement(response, delegatingServletResponse, statement);
        } finally {
            if (!isConfidentialityNotePrinted)
                resetConfidentialityNote((HttpServletRequest) request);
        }
    }

    private boolean hasHtmlResourceType(HttpServletRequest request) {
        String path = getContextRelativePath(request);
        return path != null && request.getServletContext() != null
                && RequestNegotiation.isHtmlContentType(request.getServletContext().getMimeType(path));
    }

    private static void discardBareFileMetadata(HttpServletResponse response) {
        response.setHeader("ETag", null);
        response.setHeader("Last-Modified", null);
        response.setHeader("Accept-Ranges", null);
    }

    /** Applies static representation metadata before any body write can commit the response. */
    private static final class StaticHtmlMetadataResponse extends HttpServletResponseWrapper {
        private static final String REQUEST_VARIANT = "X-Requested-With";
        private static final List<String> FILE_METADATA = List.of("ETag", "Last-Modified", "Accept-Ranges");
        private final Map<String, List<Runnable>> metadata = new LinkedHashMap<>();
        private final boolean decorated;
        private boolean suppressing;

        private StaticHtmlMetadataResponse(HttpServletResponse response, boolean decorated) {
            super(response);
            this.decorated = decorated;
            for (String name : FILE_METADATA) {
                for (String value : response.getHeaders(name)) {
                    metadata.computeIfAbsent(metadataKey(name), ignored -> new ArrayList<>())
                            .add(() -> super.addHeader(name, value));
                }
            }
            updatePolicy();
            ensureRequestVariant();
        }

        private static String metadataKey(String name) {
            return name == null ? "" : name.toLowerCase(Locale.ROOT);
        }

        private static boolean isFileMetadata(String name) {
            return switch (metadataKey(name)) {
                case "etag", "last-modified", "accept-ranges" -> true;
                default -> false;
            };
        }

        private void updatePolicy() {
            boolean suppress = decorated && (getStatus() == SC_OK || getStatus() == SC_NOT_MODIFIED)
                    && (getContentType() == null || RequestNegotiation.isHtmlContentType(getContentType()));
            if (suppress == suppressing) return;
            suppressing = suppress;
            for (String name : FILE_METADATA) super.setHeader(name, null);
            if (!suppressing) metadata.values().forEach(operations -> operations.forEach(Runnable::run));
        }

        private void record(String name, boolean replace, Runnable operation) {
            var operations = metadata.computeIfAbsent(metadataKey(name), ignored -> new ArrayList<>());
            if (replace) operations.clear();
            operations.add(operation);
            if (!suppressing) operation.run();
        }

        private void ensureRequestVariant() {
            for (String value : super.getHeaders("Vary")) {
                for (String token : value.split(",")) {
                    if ("*".equals(token.trim()) || REQUEST_VARIANT.equalsIgnoreCase(token.trim())) return;
                }
            }
            super.addHeader("Vary", REQUEST_VARIANT);
        }

        @Override public void setHeader(String name, String value) {
            if (isFileMetadata(name)) record(name, true, () -> super.setHeader(name, value));
            else { super.setHeader(name, value); updatePolicy(); if ("vary".equals(metadataKey(name))) ensureRequestVariant(); }
        }
        @Override public void addHeader(String name, String value) {
            if (isFileMetadata(name)) record(name, false, () -> super.addHeader(name, value));
            else { super.addHeader(name, value); updatePolicy(); if ("vary".equals(metadataKey(name))) ensureRequestVariant(); }
        }
        @Override public void setDateHeader(String name, long value) {
            if (isFileMetadata(name)) record(name, true, () -> super.setDateHeader(name, value));
            else super.setDateHeader(name, value);
        }
        @Override public void addDateHeader(String name, long value) {
            if (isFileMetadata(name)) record(name, false, () -> super.addDateHeader(name, value));
            else super.addDateHeader(name, value);
        }
        @Override public void setIntHeader(String name, int value) {
            if (isFileMetadata(name)) record(name, true, () -> super.setIntHeader(name, value));
            else super.setIntHeader(name, value);
        }
        @Override public void addIntHeader(String name, int value) {
            if (isFileMetadata(name)) record(name, false, () -> super.addIntHeader(name, value));
            else super.addIntHeader(name, value);
        }
        @Override public void setStatus(int status) { super.setStatus(status); updatePolicy(); }
        @Override public void setContentType(String type) { super.setContentType(type); updatePolicy(); }
        @Override public void reset() {
            super.reset();
            metadata.clear();
            suppressing = false;
            updatePolicy();
            ensureRequestVariant();
        }
    }

    /** Keeps every other request property, including Range/If-Range and authentication, unchanged. */
    private static final class PrintStatementRequest extends HttpServletRequestWrapper {
        private final boolean wildcard;

        // FindSecBugs SERVLET_HEADER: the untrusted header only preserves HTTP's wildcard existence condition;
        // it grants no privilege and every other file validator is hidden from the decorating request.
        @SuppressFBWarnings(value = "SERVLET_HEADER", justification = "Untrusted header selects only the HTTP wildcard existence condition, not authorization; all other file validators are hidden")
        private PrintStatementRequest(HttpServletRequest request) {
            super(request);
            String tag = request.getHeader("If-None-Match");
            wildcard = tag != null && "*".equals(tag.trim());
        }

        // FindSecBugs IMPROPER_UNICODE: compares fixed ASCII HTTP field names, which are case-insensitive;
        // this is a protocol comparison, not a security or authorization decision.
        @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Case-insensitive comparison of fixed ASCII HTTP field names; protocol comparison, not authorization")
        private boolean isFileCacheValidator(String name) {
            return "If-Modified-Since".equalsIgnoreCase(name)
                    || ("If-None-Match".equalsIgnoreCase(name) && !wildcard);
        }

        @Override
        public String getHeader(String name) {
            return isFileCacheValidator(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isFileCacheValidator(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public long getDateHeader(String name) {
            return isFileCacheValidator(name) ? -1 : super.getDateHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                    .filter(name -> !isFileCacheValidator(name)).toList());
        }
    }

    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = getContextRelativePath(request);
        if (servletPath == null) {
            return false;
        }

        servletPath = normalizeServletPath(servletPath);

        for (String ex : exclusions) {
            if (matchesExcludedPath(servletPath, ex)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeServletPath(String servletPath) {
        return servletPath.toLowerCase().trim();
    }

    private String getContextRelativePath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath == null || servletPath.trim().isEmpty()) {
            servletPath = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (servletPath != null && contextPath != null && !contextPath.isEmpty()
                    && servletPath.startsWith(contextPath)) {
                servletPath = servletPath.substring(contextPath.length());
            }
        }
        return servletPath;
    }

    private boolean matchesExcludedPath(String servletPath, String exclusion) {
        if (servletPath.equals(exclusion)) {
            return true;
        }
        if (exclusion.endsWith("/")) {
            return servletPath.startsWith(exclusion);
        }
        return servletPath.startsWith(exclusion + "/");
    }

    // FindSecBugs XSS_SERVLET: writes fixed print-only privacy HTML from trusted system configuration.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "writes fixed print-only privacy HTML from trusted system configuration")
    private void printConfidentialityStatement(ServletResponse response, DelegatingServletResponse delegatingServletResponse,
                                               String statement) throws IOException {
        if (delegatingServletResponse.isResponseOutputStreamObtained()) {
            response.getOutputStream().write(statement.getBytes(RequestNegotiation.responseCharset(response)));
        } else if (delegatingServletResponse.isResponseWriterObtained()) {
            response.getWriter().print(statement);
        }

        response.flushBuffer();
    }

    private void resetConfidentialityNote(HttpServletRequest request) {
        request.removeAttribute(ATTRIBUTE_NAME_CONFIDENTIALITY_NOTE_PRINTED);
    }


    /**
     * Marks this request as the one that appends the notice and reports whether an outer pass
     * already did. The flag only has to survive dispatches within one request, so it lives on the
     * request: a session attribute, cleared in {@code doFilter}'s {@code finally}, added nothing
     * but a race in which a second request on the same session, arriving while the first was
     * still rendering, shipped its printable page without the notice.
     */
    private boolean isConfidentialtyNotePrinted(HttpServletRequest request) {
        boolean isConfidentialtyNotePrinted = request.getAttribute(ATTRIBUTE_NAME_CONFIDENTIALITY_NOTE_PRINTED) != null;
        if (!isConfidentialtyNotePrinted)
            request.setAttribute(ATTRIBUTE_NAME_CONFIDENTIALITY_NOTE_PRINTED, Boolean.TRUE);
        return isConfidentialtyNotePrinted;
    }

    @Override
    public void destroy() {
    }


    /**
     * Writer that prevents flushing and closing. Please be sure to close the underlying writer.
     */
    private static class DelegatingWriter extends PrintWriter {

        public DelegatingWriter(Writer out) {
            super(out);
        }

        @Override
        public void flush() {
            // avoid
        }

        private void flushDelegate() {
            super.flush();
        }

        @Override
        public void close() {
            // avoid
        }
    }

    /**
     * Wrapper class for delegation of flushing and response closing. Please be careful with large responses,
     * as it may cause memory problems.
     */
    private static class DelegatingServletResponse extends HttpServletResponseWrapper {

        private static final Logger logger = LoggerFactory.getLogger(DelegatingServletResponse.class);

        private boolean responseWriterObtained;
        private boolean responseOutputStreamObtained;
        private boolean appendBufferUnavailable;

        private DelegatingWriter writer;
        private ServletOutputStream outputStream;
        /** The shared defer/publish/retract rules; its two delegate calls are its only access to the response. */
        private final DeferredContentLength contentLength = new DeferredContentLength(
                super::setContentLengthLong,
                () -> super.setHeader(DeferredContentLength.HEADER, null));

        public DelegatingServletResponse(HttpServletResponse response) {
            super(response);
        }

        /**
         * Reserve the append buffer only when a body channel can still produce HTML.
         * A channel selected before its content type keeps the existing large-page protection;
         * known non-HTML static responses and metadata-only responses retain the container buffer.
         */
        private void configureAppendBufferForBody() {
            if (appendBufferUnavailable || isKnownNonHtml()) return;
            try {
                if (super.getBufferSize() < APPEND_BUFFER_SIZE_BYTES) {
                    super.setBufferSize(APPEND_BUFFER_SIZE_BYTES);
                }
            } catch (IllegalStateException alreadyWritten) {
                // Written bytes can prevent growth before commit; the length can still be
                // retracted and the notice appended safely. A committed response is different.
                appendBufferUnavailable = isCommitted();
            }
        }

        /** Reserve the notice buffer, reporting a refusal without corrupting the existing response. */
        public boolean prepareAppendBuffer() {
            configureAppendBufferForBody();
            if (appendBufferUnavailable) {
                logger.warn("Skipping privacy statement injection because the HTML response buffer "
                        + "could not be enlarged before body content was written");
            }
            return !appendBufferUnavailable;
        }

        // Each flag is set only after the container hands the channel over. A caller may ask
        // for the other channel and swallow the IllegalStateException. CXF does that when it
        // closes a void JAX-RS response with getOutputStream().close() after a forwarded JSP
        // took the writer. Flagging the stream before that call failed made
        // printConfidentialityStatement() open it, which threw, and the OAuth consent page
        // answered 500 (issue #3446). AuthorizeResource now tells CXF the forward is the
        // response, so this guard covers any other forwarding JAX-RS method.
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            // Publish before acquiring the delegate: an outer wrapper may itself defer the
            // length and replay it only when its output stream is first acquired. A stream whose
            // type is still unknown publishes too, as the CSRF and logout wrappers do: a download
            // that never declares a type can never be decorated, and holding its length until the
            // chain unwinds loses it once an explicit flush commits the body. A type that turns
            // to HTML before commit retracts the length in updateContentLengthForType().
            if (!RequestNegotiation.isHtmlContentType(getContentType())) {
                contentLength.publishPending();
            }
            ServletOutputStream stream = super.getOutputStream();
            responseOutputStreamObtained = true;
            if (outputStream == null) {
                // DefaultServlet obtains the stream before declaring a static asset's type.
                // The first write sees that type, while an HTML body written before its type
                // still reserves the buffer before any bytes can commit.
                outputStream = new ServletOutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        configureAppendBufferForBody();
                        stream.write(value);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        if (length > 0) configureAppendBufferForBody();
                        stream.write(bytes, offset, length);
                    }

                    @Override
                    public void write(java.nio.ByteBuffer buffer) throws IOException {
                        if (buffer.hasRemaining()) configureAppendBufferForBody();
                        stream.write(buffer);
                    }

                    @Override
                    public boolean isReady() {
                        return stream.isReady();
                    }

                    @Override
                    public void setWriteListener(jakarta.servlet.WriteListener listener) {
                        stream.setWriteListener(listener);
                    }

                    @Override
                    public void flush() throws IOException {
                        stream.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        stream.close();
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            configureAppendBufferForBody();
            if (writer == null)
                writer = new DelegatingWriter(super.getWriter());
            responseWriterObtained = true;
            // Unlike a stream, a writer taken before its type is usually a page about to declare
            // text/html, so an unknown type keeps the length pending here.
            if (isKnownNonHtml()) {
                contentLength.publishPending();
            }
            return writer;
        }

        public boolean isResponseWriterObtained() {
            return responseWriterObtained;
        }

        public boolean isResponseOutputStreamObtained() {
            return responseOutputStreamObtained;
        }

        public void completeWithoutStatement() {
            contentLength.publishPending();
            if (writer != null) {
                writer.flushDelegate();
            }
        }

        @Override
        public void setContentLength(int length) {
            setContentLengthLong(length);
        }

        @Override
        public void setContentLengthLong(long length) {
            contentLength.defer(length);
            updateContentLengthForType();
        }

        @Override
        public void setIntHeader(String name, int value) {
            if (DeferredContentLength.isContentLengthHeader(name)) {
                setContentLength(value);
            } else {
                super.setIntHeader(name, value);
            }
        }

        @Override
        public void addIntHeader(String name, int value) {
            if (DeferredContentLength.isContentLengthHeader(name)) {
                setContentLength(value);
            } else {
                super.addIntHeader(name, value);
            }
        }

        @Override
        public void setHeader(String name, String value) {
            if (DeferredContentLength.isContentLengthHeader(name)) {
                deferContentLengthHeader(value);
                return;
            }
            super.setHeader(name, value);
            updateContentLengthForType();
        }

        @Override
        public void addHeader(String name, String value) {
            if (DeferredContentLength.isContentLengthHeader(name)) {
                // Servlet 6.1: adding a null value is a no-op; setting one removes the header.
                if (value != null) {
                    deferContentLengthHeader(value);
                }
                return;
            }
            super.addHeader(name, value);
            updateContentLengthForType();
        }

        /**
         * Routes a string Content-Length through the shared rules. A malformed value is ignored
         * with a warning, never handed to the container, so every CARLOS wrapper treats it alike.
         */
        private void deferContentLengthHeader(String value) {
            if (value == null) {
                contentLength.remove();
                return;
            }
            OptionalLong parsed = DeferredContentLength.parse(value);
            if (parsed.isPresent()) {
                setContentLengthLong(parsed.getAsLong());
            } else if (logger.isWarnEnabled()) {
                logger.warn("Ignoring malformed Content-Length header value before the privacy notice: {}",
                        LogSafe.sanitize(value));
            }
        }

        @Override
        public void setContentType(String type) {
            super.setContentType(type);
            updateContentLengthForType();
        }

        private void updateContentLengthForType() {
            if (responseWriterObtained || (responseOutputStreamObtained && getContentType() != null)) {
                configureAppendBufferForBody();
            }
            if (!isCommitted() && RequestNegotiation.isHtmlContentType(getContentType())) {
                // A type change before writing makes a previously published length unsafe.
                // Keep it pending in case the response ultimately needs no decoration.
                contentLength.retractToPending();
            }
            // Content-Type can arrive through any header API after channel selection, and so can
            // the length. The channel rules of getOutputStream() and getWriter() apply again here:
            // a stream publishes unless the type is known HTML, a writer only once it is known
            // non-HTML.
            if ((responseOutputStreamObtained && !RequestNegotiation.isHtmlContentType(getContentType()))
                    || (responseWriterObtained && isKnownNonHtml())) {
                contentLength.publishPending();
            }
        }

        /** The delegate resets first, so a rejected reset leaves the shared length state as it was. */
        @Override
        public void resetBuffer() {
            super.resetBuffer();
            contentLength.onBufferReset();
            appendBufferUnavailable = false;
            if (responseWriterObtained || responseOutputStreamObtained) {
                configureAppendBufferForBody();
            }
        }

        @Override
        public void reset() {
            super.reset();
            contentLength.onReset();
            writer = null;
            outputStream = null;
            responseWriterObtained = false;
            responseOutputStreamObtained = false;
            appendBufferUnavailable = false;
        }

        private boolean isKnownNonHtml() {
            String type = getContentType();
            return type != null && !RequestNegotiation.isHtmlContentType(type);
        }

        /** The appended notice changes the body, so a declared length no longer applies. */
        private void discardDeferredContentLength() {
            contentLength.discardPending();
        }

        @Override
        public void flushBuffer() throws IOException {
            // avoid flushing buffer
            // super.flushBuffer();
        }
    }

}
