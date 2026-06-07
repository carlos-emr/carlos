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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.RequestNegotiation;
import org.owasp.csrfguard.CsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Servlet filter that auto-injects the CSRFGuard JavaScript tag into HTML responses.
 *
 * <p>CSRFGuard 4.5 requires a {@code <script src="contextPath/csrfguard"></script>} tag
 * on every HTML page for its client-side token injection features (form injection, XHR
 * interception, dynamic node handling) to work. Rather than manually adding the tag to
 * 1,200+ JSPs, this filter captures HTML responses and injects the script tag automatically.</p>
 *
 * <p>The filter uses a {@link CharArrayWriter}-based {@link HttpServletResponseWrapper}
 * to capture {@link PrintWriter} output, then inserts the script tag before {@code </head>}.
 * Responses using {@link ServletOutputStream} (binary content like PDFs, images) pass
 * through untouched.</p>
 *
 * <p>The deployed web.xml mapping is load-bearing and currently FORWARD-only so JSP rendering
 * can be captured after Struts forwards without double-wrapping the top-level request. The
 * REQUEST-dispatch branch remains defensive for tests or future remapping; see
 * {@code docs/csrf-protection-architecture.md} before changing dispatcher mappings.</p>
 *
 * <p>If {@link CsrfGuard#getInstance()} fails, the filter fails closed with HTTP 503 to avoid
 * serving HTML without the CSRF script tag.</p>
 *
 * <p>Injection is skipped when:
 * <ul>
 *   <li>Content-Type is not {@code text/html}</li>
 *   <li>The request is AJAX ({@code X-Requested-With: XMLHttpRequest}) on REQUEST dispatch
 *       if that dispatcher mapping is re-enabled</li>
 *   <li>CSRFGuard is disabled</li>
 *   <li>The response already contains a {@code /csrfguard} script reference (idempotency)</li>
 *   <li>The response used {@code getOutputStream()} instead of {@code getWriter()}</li>
 *   <li>The response was committed by {@code sendRedirect()} or {@code sendError()} during
 *       downstream processing</li>
 * </ul>
 * </p>
 *
 * @since 2026-02-23
 */
public class CsrfGuardScriptInjectionFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsrfGuardScriptInjectionFilter.class);

    /**
     * Matches a {@code <script src="...csrfguard...">} tag (case-insensitive).
     * Used for the idempotency check to avoid double-injection. A simple
     * {@code contains("/csrfguard")} would produce false positives on pages where
     * the literal string appears in JavaScript code, comments, or user-generated content.
     */
    private static final Pattern CSRFGUARD_SCRIPT_PATTERN =
            Pattern.compile("<script[^>]*src=[\"'][^\"']*\\/csrfguard[\"']", Pattern.CASE_INSENSITIVE);
    private static final AtomicBoolean HTML_LOOKING_PASSTHROUGH_WARNED = new AtomicBoolean(false);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization required
    }

    /**
     * Captures the HTML response, injects the CSRFGuard script tag before {@code </head>},
     * and writes the modified content to the client.
     *
     * <p>Non-HTML responses, AJAX requests, and binary content (via {@code getOutputStream()})
     * pass through without modification. See the class-level JavaDoc for all skip conditions.</p>
     *
     * @param request  the servlet request
     * @param response the servlet response
     * @param chain    the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String safeRequestUri = LogSafe.sanitizeUri(httpRequest.getRequestURI());

        // Skip AJAX requests on REQUEST dispatch only (not FORWARD).
        // On FORWARD dispatch, the CaptureResponseWrapper is needed to prevent
        // Tomcat 11 from truncating large JSP responses (> 8KB) during forward.
        if (httpRequest.getDispatcherType() == jakarta.servlet.DispatcherType.REQUEST) {
            if (RequestNegotiation.isAjax(httpRequest)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Skip if CsrfGuard is disabled
        CsrfGuard csrfGuard;
        try {
            csrfGuard = CsrfGuard.getInstance();
        } catch (Exception e) {
            LOGGER.error("CsrfGuard.getInstance() failed — failing closed so HTML is not "
                    + "served without CSRF script injection (method={} uri={})",
                    LogSafe.sanitize(httpRequest.getMethod()), safeRequestUri, e);
            httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        if (!csrfGuard.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        CaptureResponseWrapper wrapper = new CaptureResponseWrapper(httpResponse, safeRequestUri);
        LOGGER.debug("CsrfGuard: wrapping request {}", safeRequestUri);
        chain.doFilter(request, wrapper);
        LOGGER.debug("CsrfGuard: chain completed for {} committed={} writer={} stream={}",
                safeRequestUri, wrapper.isResponseCommitted(),
                wrapper.isUsingWriter(), wrapper.isUsingOutputStream());

        // If the response was committed by sendRedirect() or sendError(), the status and
        // headers have already been sent to the client — no post-processing is possible
        if (wrapper.isResponseCommitted()) {
            LOGGER.debug("CsrfGuard: response committed for {}", safeRequestUri);
            return;
        }

        // If the downstream code used getOutputStream(), content was written directly to the
        // client via the real response's output stream — no capture occurred and no injection
        // is possible
        if (wrapper.isUsingOutputStream()) {
            LOGGER.debug("CsrfGuard: output stream used for {}", safeRequestUri);
            return;
        }

        // If getWriter() was never called either, nothing to do
        if (!wrapper.isUsingWriter()) {
            LOGGER.debug("CsrfGuard script injection skipped — getWriter() was never called "
                    + "(usingOutputStream={}, committed={}, uri={})",
                    wrapper.isUsingOutputStream(), wrapper.isResponseCommitted(),
                    safeRequestUri);
            return;
        }

        if (wrapper.isWriterPassthrough()) {
            LOGGER.debug("CsrfGuard: writer passthrough for {} contentType={}",
                    safeRequestUri, wrapper.getContentType());
            wrapper.flushPassthroughWriter();
            return;
        }

        String contentType = wrapper.getContentType();
        if (!RequestNegotiation.isHtmlContentType(contentType)) {
            // Not HTML — write captured content through without modification
            writeToResponse(httpResponse, wrapper.getCapturedContent(), safeRequestUri);
            return;
        }

        String captured = wrapper.getCapturedContent();
        LOGGER.debug("CsrfGuard: captured {} bytes for {} contentType={}",
                captured.length(), safeRequestUri, contentType);

        // Idempotency: skip if the page already contains a <script src="...csrfguard..."> tag.
        // Regex is used rather than contains() to avoid false positives when the literal
        // string "/csrfguard" appears in JavaScript code, comments, or user-generated content.
        if (CSRFGUARD_SCRIPT_PATTERN.matcher(captured).find()) {
            writeToResponse(httpResponse, captured, safeRequestUri);
            return;
        }

        String scriptTag = "<script src=\"" + httpRequest.getContextPath() + "/csrfguard\"></script>";
        String modified = injectScript(captured, scriptTag);

        writeToResponse(httpResponse, modified, safeRequestUri);
    }

    /**
     * Injects the script tag into the HTML content before {@code </head>} or {@code </body>}.
     * If neither closing tag is present the response is treated as an HTML fragment
     * (e.g., a partial loaded via {@code fetch()} or an AJAX call that does not include
     * the full page structure). Fragments do not need the injection because the parent
     * page already has CSRFGuard loaded; returning them unchanged avoids inserting
     * semantically incorrect markup into table rows, list items, or similar partials.
     */
    private String injectScript(String html, String scriptTag) {
        // Try </head> (case-insensitive)
        int headIdx = indexOfIgnoreCase(html, "</head>");
        if (headIdx >= 0) {
            return html.substring(0, headIdx) + scriptTag + "\n" + html.substring(headIdx);
        }

        // Fallback: try </body>
        int bodyIdx = indexOfIgnoreCase(html, "</body>");
        if (bodyIdx >= 0) {
            return html.substring(0, bodyIdx) + scriptTag + "\n" + html.substring(bodyIdx);
        }

        // No </head> or </body> — treat as an HTML fragment, not a full page.
        // The parent page already has CSRFGuard loaded; do not inject into partials.
        return html;
    }

    /**
     * Case-insensitive indexOf using {@link String#regionMatches(boolean, int, String, int, int)}
     * to avoid allocating a lowercase copy of the entire HTML body.
     */
    private int indexOfIgnoreCase(String source, String target) {
        int targetLength = target.length();
        int searchLength = source.length() - targetLength;
        for (int i = 0; i <= searchLength; i++) {
            if (source.regionMatches(true, i, target, 0, targetLength)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Writes the final content to the real response.
     *
     * <p>{@code Content-Length} is updated only on the output-stream fallback path, where this
     * method writes the exact byte array. The normal writer path leaves length calculation to the
     * container so response character encoding cannot create a stale byte count.</p>
     *
     * <p>Uses {@code getWriter()} for text content because Tomcat 11's
     * {@code RequestDispatcher.forward()} may have already opened the writer
     * on the underlying response. Calling {@code getOutputStream()} after
     * {@code getWriter()} throws {@code IllegalStateException}.</p>
     */
    // FindSecBugs XSS_SERVLET: replays captured response content after trusted CSRF token injection; not raw request data.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "replays captured response content after trusted CSRF token injection; not raw request data")
    private void writeToResponse(HttpServletResponse response, String content, String safeRequestUri)
            throws IOException {
        // Clear only the response body buffer, preserving status code, headers, and cookies
        // set by downstream components. resetBuffer() is safer than reset() which would
        // wipe Set-Cookie, security headers, CSP, and non-200 status codes.
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            LOGGER.warn("writeToResponse: response buffer was already committed before "
                    + "CSRF-adjusted replay; writing captured content without reset: uri={}, "
                    + "contentType={}, committed={}",
                    safeRequestUri, response.getContentType(), response.isCommitted(), e);
        }
        String encoding = response.getCharacterEncoding();
        if (encoding == null || encoding.isEmpty()) {
            encoding = "UTF-8";
        }
        try {
            // Use getWriter() for text responses. Content-Length is not set here because
            // the byte count from content.getBytes(encoding) may differ from what the writer
            // sends if the response encoding changes. The container should own the final length.
            response.getWriter().write(content); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer, java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep -- trusted CSRF framework content
            response.getWriter().flush();
        } catch (IllegalStateException e) {
            // getWriter() failed because getOutputStream() was already called. This fallback
            // writes the exact byte array, so Content-Length is safe here.
            LOGGER.warn("CsrfGuard: response writer unavailable while writing adjusted content; "
                            + "falling back to output stream: uri={}, contentType={}, committed={}",
                    safeRequestUri, response.getContentType(), response.isCommitted(), e);
            byte[] bytes = content.getBytes(encoding);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- trusted CSRF framework content
            response.getOutputStream().flush();
        }
    }

    @Override
    public void destroy() {
        // No cleanup required
    }

    /**
     * Response wrapper that captures {@link PrintWriter} output into a {@link CharArrayWriter}
     * while passing {@link ServletOutputStream} calls directly through to the real response.
     *
     * <p>This dual-mode approach ensures binary responses (PDFs, images, downloads) are
     * never buffered or corrupted, while text/html responses can be captured and modified.</p>
     */
    private static class CaptureResponseWrapper extends HttpServletResponseWrapper {

        private CharArrayWriter captureWriter;
        private PrintWriter writer;
        private boolean usingOutputStream;
        private boolean usingWriter;
        private boolean writerPassthrough;
        private boolean committed;
        private Integer deferredContentLength;
        private Long deferredContentLengthLong;
        private final String requestUri;

        public CaptureResponseWrapper(HttpServletResponse response, String requestUri) {
            super(response);
            this.requestUri = requestUri;
            // Do not call setBufferSize() here. Tomcat 11 forwards can reject late buffer-size
            // changes after a JSP has obtained its writer, so the capture boundary is the
            // CharArrayWriter below rather than the servlet container's response buffer.
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (usingWriter) {
                throw new IllegalStateException("getWriter() has already been called on this response");
            }
            usingOutputStream = true;
            // Apply any Content-Length that was set before we knew the response mode.
            // For output-stream passthrough, the header must reach the underlying response.
            applyDeferredContentLength();
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (usingOutputStream) {
                throw new IllegalStateException("getOutputStream() has already been called on this response");
            }
            usingWriter = true;
            if (writer == null) {
                if (isKnownNonHtmlContentType()) {
                    writerPassthrough = true;
                    applyDeferredContentLength();
                    writer = new PrintWriter(new SniffingPassthroughWriter(super.getWriter()));
                } else {
                    writer = new PrintWriter(new LazyCaptureWriter());
                }
            }
            return writer;
        }

        @Override
        public void setContentLength(int len) {
            if (usingOutputStream) {
                // Output-stream passthrough: the real response owns the content, so pass through
                super.setContentLength(len);
                return;
            }
            if (writerPassthrough) {
                super.setContentLength(len);
                return;
            }
            if (usingWriter) {
                // Writer-capture path: suppress — final length is recomputed in writeToResponse()
                return;
            }
            // Mode not yet determined — defer until getOutputStream() or getWriter() is called
            deferredContentLength = len;
        }

        @Override
        public void setContentLengthLong(long len) {
            if (usingOutputStream) {
                // Output-stream passthrough: the real response owns the content, so pass through
                super.setContentLengthLong(len);
                return;
            }
            if (writerPassthrough) {
                super.setContentLengthLong(len);
                return;
            }
            if (usingWriter) {
                // Writer-capture path: suppress — final length is recomputed in writeToResponse()
                return;
            }
            // Mode not yet determined — defer until getOutputStream() or getWriter() is called
            deferredContentLengthLong = len;
        }

        @Override
        public void setHeader(String name, String value) {
            if (isContentLengthHeader(name)) {
                deferContentLengthHeader(value);
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (isContentLengthHeader(name)) {
                deferContentLengthHeader(value);
                return;
            }
            super.addHeader(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            if (isContentLengthHeader(name)) {
                setContentLength(value);
                return;
            }
            super.setIntHeader(name, value);
        }

        @Override
        public void addIntHeader(String name, int value) {
            if (isContentLengthHeader(name)) {
                setContentLength(value);
                return;
            }
            super.addIntHeader(name, value);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            committed = true;
            LOGGER.debug("CsrfGuard wrapper: sendRedirect called to {}", location);
            super.sendRedirect(location);
        }

        @Override
        public void sendError(int sc) throws IOException {
            committed = true;
            LOGGER.debug("CsrfGuard wrapper: sendError called with {}", sc);
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            committed = true;
            super.sendError(sc, msg);
        }

        @Override
        public void flushBuffer() throws IOException {
            // For output-stream passthrough responses, delegate flushing so streaming downloads
            // and progressive chunk delivery work correctly. The suppression is only needed for
            // the writer-capture path (text/html), where we must prevent the response from being
            // committed before script injection has run.
            if (usingOutputStream) {
                super.flushBuffer();
                return;
            }
            if (writerPassthrough) {
                super.flushBuffer();
                return;
            }
            // Suppress flushing to prevent captured content from being committed to the client
            // before script injection.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("flushBuffer() suppressed during CSRF script injection capture");
            }
        }

        public boolean isResponseCommitted() {
            return committed;
        }

        /**
         * Override isCommitted to report false during writer-capture mode.
         * Tomcat 11 commits the underlying response during RequestDispatcher.forward(),
         * but our CharArrayWriter still has the captured content. Reporting committed=true
         * to downstream filters (e.g. LogoutBroadcastFilter) would prevent them from
         * writing to the captured writer, and would cause writeToResponse to fail.
         */
        @Override
        public boolean isCommitted() {
            if (writerPassthrough) {
                return committed || super.isCommitted();
            }
            if (usingWriter && !committed) {
                return false;
            }
            return committed || super.isCommitted();
        }

        public boolean isUsingOutputStream() {
            return usingOutputStream;
        }

        public boolean isUsingWriter() {
            return usingWriter;
        }

        /**
         * Returns whether writer output has been delegated directly to the underlying response.
         */
        public boolean isWriterPassthrough() {
            return writerPassthrough;
        }

        public String getCapturedContent() {
            if (writer != null) {
                writer.flush();
            }
            return captureWriter != null ? captureWriter.toString() : "";
        }

        /**
         * Completes non-HTML writer passthrough responses before returning from the FORWARD
         * filter. JSP writers can hold up to the servlet buffer size; without this explicit
         * flush, JavaScript and JSON forwards may reach the browser with only the first buffer.
         */
        void flushPassthroughWriter() {
            if (writer != null) {
                writer.flush();
            }
        }

        /**
         * Returns true only when Content-Type is known and not HTML.
         */
        private boolean isKnownNonHtmlContentType() {
            String contentType = getContentType();
            return contentType != null && !RequestNegotiation.isHtmlContentType(contentType);
        }

        /**
         * Emits one operator-visible warning when a response opts out of CSRF injection using a
         * non-HTML content type but the first body chunk looks like a full HTML page. This catches
         * JSPs that accidentally set {@code text/json} or similar before writing HTML, a failure
         * mode that otherwise causes missing CSRF tokens with only DEBUG-level evidence.
         */
        private void warnIfHtmlLookingPassthrough(String value, int offset, int length) {
            if (value == null || length <= 0 || !isKnownNonHtmlContentType()) {
                return;
            }
            int safeOffset = Math.max(0, Math.min(offset, value.length()));
            int safeEnd = Math.max(safeOffset, Math.min(value.length(), safeOffset + length));
            if (startsWithHtmlDocument(value.subSequence(safeOffset, safeEnd))
                    && HTML_LOOKING_PASSTHROUGH_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("CSRF script injection passed through a response with non-HTML "
                        + "Content-Type [{}] even though the body appears to be HTML; uri={}",
                        getContentType(), requestUri);
            }
        }

        private void warnIfHtmlLookingPassthrough(char[] chars, int offset, int length) {
            if (chars == null || length <= 0 || !isKnownNonHtmlContentType()) {
                return;
            }
            int safeOffset = Math.max(0, Math.min(offset, chars.length));
            int safeLength = Math.max(0, Math.min(length, chars.length - safeOffset));
            if (safeLength == 0) {
                return;
            }
            warnIfHtmlLookingPassthrough(new String(chars, safeOffset, safeLength), 0, safeLength);
        }

        private static boolean startsWithHtmlDocument(CharSequence value) {
            int index = 0;
            int length = value.length();
            while (index < length && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            return startsWithIgnoreCase(value, index, "<html")
                    || startsWithIgnoreCase(value, index, "<!doctype");
        }

        private static boolean startsWithIgnoreCase(CharSequence value, int offset, String prefix) {
            if (value.length() - offset < prefix.length()) {
                return false;
            }
            for (int i = 0; i < prefix.length(); i++) {
                if (Character.toLowerCase(value.charAt(offset + i))
                        != Character.toLowerCase(prefix.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Applies Content-Length values deferred while the response mode was still unknown.
         */
        private void applyDeferredContentLength() {
            if (deferredContentLength != null) {
                super.setContentLength(deferredContentLength);
                deferredContentLength = null;
            }
            if (deferredContentLengthLong != null) {
                super.setContentLengthLong(deferredContentLengthLong);
                deferredContentLengthLong = null;
            }
        }

        private void deferContentLengthHeader(String value) {
            try {
                setContentLengthLong(Long.parseLong(value));
            } catch (NumberFormatException e) {
                LOGGER.warn("Ignoring malformed Content-Length header value during CSRF capture: uri={}, value={}",
                        requestUri, LogSafe.sanitize(value), e);
            }
        }

        // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
        @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
        private boolean isContentLengthHeader(String name) {
            return "Content-Length".equalsIgnoreCase(name);
        }

        /**
         * Defers choosing capture vs. passthrough until the first write, when Content-Type
         * may be known even if it was not set when getWriter() was called.
         */
        private class LazyCaptureWriter extends Writer {
            private final AtomicReference<Writer> target = new AtomicReference<>();
            private final Object targetLock = new Object();

            @Override
            public void write(int character) throws IOException {
                getTarget().write(character);
            }

            @Override
            public void write(char[] chars, int offset, int length) throws IOException {
                warnIfHtmlLookingPassthrough(chars, offset, length);
                getTarget().write(chars, offset, length);
            }

            @Override
            public void write(String string, int offset, int length) throws IOException {
                warnIfHtmlLookingPassthrough(string, offset, length);
                getTarget().write(string, offset, length);
            }

            @Override
            public void flush() throws IOException {
                Writer currentTarget = target.get();
                if (currentTarget != null) {
                    currentTarget.flush();
                }
            }

            @Override
            public void close() throws IOException {
                Writer currentTarget = target.get();
                if (currentTarget != null) {
                    currentTarget.close();
                }
            }

            private Writer getTarget() throws IOException {
                Writer currentTarget = target.get();
                if (currentTarget == null) {
                    synchronized (targetLock) {
                        currentTarget = target.get();
                        if (currentTarget == null) {
                            currentTarget = createTarget();
                            target.set(currentTarget);
                        }
                    }
                }
                return currentTarget;
            }

            private Writer createTarget() throws IOException {
                if (isKnownNonHtmlContentType()) {
                    writerPassthrough = true;
                    applyDeferredContentLength();
                    return CaptureResponseWrapper.super.getWriter();
                }
                captureWriter = new CharArrayWriter();
                return captureWriter;
            }
        }

        private class SniffingPassthroughWriter extends Writer {
            private final Writer delegate;

            SniffingPassthroughWriter(Writer delegate) {
                this.delegate = delegate;
            }

            @Override
            public void write(int character) throws IOException {
                delegate.write(character);
            }

            @Override
            public void write(char[] chars, int offset, int length) throws IOException {
                warnIfHtmlLookingPassthrough(chars, offset, length);
                delegate.write(chars, offset, length);
            }

            @Override
            public void write(String string, int offset, int length) throws IOException {
                warnIfHtmlLookingPassthrough(string, offset, length);
                delegate.write(string, offset, length);
            }

            @Override
            public void flush() throws IOException {
                delegate.flush();
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }
        }
    }
}
