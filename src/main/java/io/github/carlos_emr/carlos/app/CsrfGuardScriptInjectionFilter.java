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

import io.github.carlos_emr.carlos.web.eform.EformViewForPdfGenerationServlet;

import io.github.carlos_emr.carlos.utility.DeferredContentLength;
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
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.CharArrayWriter;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Objects;
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
 * The capture is bounded by {@link #MAX_CAPTURE_CHARS}: a larger page has the script injected
 * into its captured prefix and the rest streamed (#5937).
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
    /** Upper bound on response wrapper layers unwrapped by {@link #isInnermostResponseCommitted}. */
    private static final int MAX_WRAPPER_DEPTH = 32;

    /**
     * Most characters an HTML forward is held in memory before it is streamed (#5937), the same
     * bound {@code ResponseSanitizationFilter} applies to its own capture. A page past it gets the
     * script injected into the captured prefix, which holds {@code </head>} on any real page, and
     * the rest is written straight through, so memory stays bounded and the page keeps CSRF
     * protection. Package-private for tests.
     */
    static final int MAX_CAPTURE_CHARS = 512 * 1024;

    /** Servlet path of the loopback server-side eForm PDF renderer route (see web.xml). */
    private static final String RENDERER_ROUTE_SERVLET_PATH = "/EFormViewForPdfGenerationServlet";

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

        if (Boolean.TRUE.equals(httpRequest.getAttribute(EformViewForPdfGenerationServlet.SKIP_HTML_INJECTION_ATTRIBUTE))) {
            chain.doFilter(request, response);
            return;
        }

        // The server-side eForm PDF renderer route (loopback + render-token gated) must be captured
        // byte-for-byte: never inject the CSRFGuard client script into the rendered eForm DOM, or it
        // would appear in the faxed/stored PDF and add a subresource fetch during capture. This skip
        // check runs before doFilter, so the servlet's SKIP attribute (set during execution) is too
        // late — match the route here.
        if (RENDERER_ROUTE_SERVLET_PATH.equals(httpRequest.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

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

        String scriptTag = "<script src=\"" + httpRequest.getContextPath() + "/csrfguard\"></script>";
        CaptureResponseWrapper wrapper = new CaptureResponseWrapper(httpResponse, safeRequestUri, scriptTag);
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
            if (wrapper.isScriptStillPending()) {
                LOGGER.warn("CSRF script not injected into a large HTML response: it has no </head> or "
                        + "</body>; uri={}", safeRequestUri);
            }
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
    static String injectScript(String html, String scriptTag) {
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
    private static int indexOfIgnoreCase(String source, String target) {
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
     * <p>The body buffer is cleared first with {@code resetBuffer()}, which keeps the status,
     * headers and cookies set downstream. That reset is only valid while the response is
     * uncommitted, so it is skipped when either the response or the innermost response beneath
     * its wrappers is already committed; the content is then written without it and a warning
     * says why (#3424). A reset that is still rejected is logged the same way, without a stack
     * trace.</p>
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
        // set by downstream components. resetBuffer() is invalid after commit, so skip it there
        // rather than throw. A commit at this point is itself the anomaly: Tomcat suspends a
        // forwarded response unless the context sets suspendWrappedResponseAfterForward="false"
        // (#3434), and a suspended response discards what is written next, so the page may
        // arrive blank. Log what is needed to recognise that case.
        boolean committed = response.isCommitted();
        // A capture wrapper can report uncommitted while the response beneath it is committed:
        // this filter's own CaptureResponseWrapper and ResponseSanitizationFilter's
        // CapturingResponseWrapper both do in writer-capture mode, and on a nested forward
        // (#3434) the reset would reach the committed response and be rejected. Ask the
        // innermost response too.
        boolean innermostCommitted = committed || isInnermostResponseCommitted(response);
        if (committed) {
            LOGGER.warn("writeToResponse: response already committed before CSRF-adjusted replay; "
                    + "writing captured content without buffer reset, but the container may discard "
                    + "it (see #3434, suspendWrappedResponseAfterForward): uri={}, status={}, "
                    + "contentType={}, capturedChars={}",
                    safeRequestUri, response.getStatus(), LogSafe.sanitize(response.getContentType()),
                    content.length());
        } else if (innermostCommitted) {
            LOGGER.warn("writeToResponse: innermost response already committed beneath a wrapper "
                    + "that reports uncommitted (e.g. CaptureResponseWrapper or ResponseSanitizationFilter's "
                    + "CapturingResponseWrapper); writing captured content without buffer reset, but the "
                    + "replay may not reach the client (see #3434): uri={}, status={}, contentType={}, "
                    + "capturedChars={}, committed={}, innermostCommitted={}",
                    safeRequestUri, response.getStatus(), LogSafe.sanitize(response.getContentType()),
                    content.length(), committed, innermostCommitted);
        } else {
            try {
                response.resetBuffer();
            } catch (IllegalStateException e) {
                // The outer and innermost responses both reported uncommitted, so an intermediate
                // wrapper with committed state of its own, or the container, rejected the reset.
                // Keep the exception's message, not its stack.
                LOGGER.warn("writeToResponse: resetBuffer() rejected although the response and the "
                        + "innermost response reported uncommitted; writing captured content without "
                        + "buffer reset: uri={}, status={}, contentType={}, committed={}, "
                        + "innermostCommitted={}, reason={}",
                        safeRequestUri, response.getStatus(), LogSafe.sanitize(response.getContentType()),
                        response.isCommitted(), isInnermostResponseCommitted(response),
                        LogSafe.sanitize(e.getMessage()));
            }
        }
        try {
            // Use getWriter() for text responses. Content-Length is not set here because
            // the byte count from encoding content may differ from what the writer
            // sends if the response encoding changes. The container should own the final length.
            response.getWriter().write(content); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer, java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep -- trusted CSRF framework content
            response.getWriter().flush();
        } catch (IllegalStateException e) {
            // getWriter() failed because getOutputStream() was already called. This fallback
            // writes the exact byte array, so Content-Length is safe here.
            LOGGER.warn("CsrfGuard: response writer unavailable while writing adjusted content; "
                            + "falling back to output stream: uri={}, contentType={}, committed={}",
                    safeRequestUri, response.getContentType(), response.isCommitted(), e);
            var charset = RequestNegotiation.responseCharset(response);
            response.setCharacterEncoding(charset.name());
            byte[] bytes = content.getBytes(charset);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- trusted CSRF framework content
            response.getOutputStream().flush();
        }
    }

    /**
     * Reports whether the innermost response beneath any {@link ServletResponseWrapper} layers is
     * committed.
     *
     * <p>Every wrapper in the forward chain delegates {@code resetBuffer()} down to that response,
     * which is the one that rejects a reset after commit, so skipping the reset when it is
     * committed loses nothing. {@code FormTransportContainer}'s capture wrapper is the exception:
     * it keeps a buffer of its own and does not delegate. It could only sit beneath this filter on
     * a forward nested inside its include, and then that forward has already reset its buffer and
     * this filter captured the page, so the buffer is empty at replay; the skip fires only when the
     * caller response beneath it is committed, which {@code FormTransportContainer}'s contract
     * forbids while it renders.</p>
     *
     * @param response the response handed to this filter
     * @return {@code true} if the innermost response is committed; after {@value #MAX_WRAPPER_DEPTH}
     *         layers the deepest one reached is asked, and the caller's try/catch remains the fallback
     */
    static boolean isInnermostResponseCommitted(ServletResponse response) {
        ServletResponse current = response;
        // Bounded so that a wrapper which (wrongly) returns itself cannot loop forever.
        for (int depth = 0; depth < MAX_WRAPPER_DEPTH
                && current instanceof ServletResponseWrapper wrapper; depth++) {
            current = wrapper.getResponse();
        }
        return current.isCommitted();
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
        /** The capture passed MAX_CAPTURE_CHARS and is streaming; its injected prefix changed the length. */
        private boolean captureOverflowed;
        /** The capture writer of this response, once created; it owns the overflow state. */
        private BoundedCaptureWriter boundedWriter;
        private boolean committed;
        /** The shared defer/publish/retract rules; its two delegate calls are its only access to the response. */
        private final DeferredContentLength contentLength = new DeferredContentLength(
                super::setContentLengthLong,
                () -> super.setHeader(DeferredContentLength.HEADER, null));
        private final String requestUri;
        private final String scriptTag;

        public CaptureResponseWrapper(HttpServletResponse response, String requestUri, String scriptTag) {
            super(response);
            this.requestUri = requestUri;
            this.scriptTag = scriptTag;
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

        /**
         * Discards the uncommitted capture when a nested forward resets its response.
         *
         * <p>Every CARLOS wrapper applies one rule to an accepted {@code resetBuffer()}: a length that
         * predates the reset, pending or published, described the discarded body and is dropped. A
         * forward always replaces the body, so a pre-forward length is stale by definition. A length
         * still pending is cleared; one this wrapper already passed through on a stream or
         * non-HTML writer channel is removed from the delegate, so the wrappers beneath this
         * FORWARD-only one never see it again. A rejected reset (the delegate throws) leaves the
         * capture and both kinds of length as they were.</p>
         */
        @Override
        public void resetBuffer() {
            super.resetBuffer();
            if (captureWriter != null) {
                captureWriter.reset();
            }
            if (captureOverflowed) {
                // The container accepted the reset, so the streamed prefix was still in its buffer
                // and is gone: capture the replacement document again so it gets its script.
                boundedWriter.rearm();
                captureOverflowed = false;
                writerPassthrough = false;
            }
            contentLength.onBufferReset();
        }

        /** Reset channel/capture state only after the underlying response accepts the full reset. */
        @Override
        public void reset() {
            super.reset();
            captureWriter = null;
            writer = null;
            usingOutputStream = false;
            usingWriter = false;
            writerPassthrough = false;
            // A full reset discards a streamed page too, with its held characters and pending script.
            captureOverflowed = false;
            boundedWriter = null;
            committed = false;
            contentLength.onReset();
        }

        @Override
        public void setContentLength(int len) {
            setContentLengthLong(len);
        }

        @Override
        public void setContentLengthLong(long len) {
            if (captureOverflowed) {
                // The streamed body carries the injected script, so a downstream length is stale.
                return;
            }
            if (usingOutputStream || writerPassthrough) {
                // Passthrough: the real response owns the content, so pass the length through
                contentLength.publishNow(len);
                return;
            }
            if (usingWriter) {
                // Writer-capture path: suppress — final length is recomputed in writeToResponse()
                return;
            }
            // Mode not yet determined — defer until getOutputStream() or getWriter() is called
            contentLength.defer(len);
        }

        @Override
        public void setHeader(String name, String value) {
            if (DeferredContentLength.isContentLengthHeader(name)) {
                deferContentLengthHeader(value);
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (DeferredContentLength.isContentLengthHeader(name)) {
                if (value == null) {
                    return;
                }
                deferContentLengthHeader(value);
                return;
            }
            super.addHeader(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            if (DeferredContentLength.isContentLengthHeader(name)) {
                setContentLength(value);
                return;
            }
            super.setIntHeader(name, value);
        }

        @Override
        public void addIntHeader(String name, int value) {
            if (DeferredContentLength.isContentLengthHeader(name)) {
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
         * A streamed large page also writes the partial closing tag it held back across flushes.
         */
        void flushPassthroughWriter() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            if (captureOverflowed) {
                // The response is complete, so a partial closing tag held back can no longer finish.
                boundedWriter.finish();
            }
        }

        /** A streamed HTML page that ended without the closing tag its script waits for. */
        boolean isScriptStillPending() {
            return boundedWriter != null && boundedWriter.injectionPending;
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
            contentLength.publishPending();
        }

        private void deferContentLengthHeader(String value) {
            if (value == null) {
                contentLength.remove();
                return;
            }
            java.util.OptionalLong parsed = DeferredContentLength.parse(value);
            if (parsed.isPresent()) {
                setContentLengthLong(parsed.getAsLong());
            } else if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Ignoring malformed Content-Length header value during CSRF capture: uri={}, value={}",
                        requestUri, LogSafe.sanitize(value));
            }
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
                boundedWriter = new BoundedCaptureWriter(captureWriter);
                return boundedWriter;
            }
        }

        /**
         * Captures writer output up to {@link #MAX_CAPTURE_CHARS}, then streams (#5937).
         *
         * <p>When a write would cross the bound, only the remaining allowance is captured. The
         * captured prefix is then written to the response with the CSRFGuard script injected
         * before its {@code </head>} (or {@code </body>}) when it is HTML. The rest of that write
         * and every later write stream straight from the caller's own string or array, so memory
         * stays bounded whatever the size of a single write. The capture buffer is released at
         * the switch.</p>
         *
         * <p>When the prefix holds no closing tag, the streamed writes are searched for the first
         * {@code </head>} or {@code </body>} and the script is written just before it, as the
         * replay path would have. A streamed write that ends part-way into a closing tag (such as
         * {@code </bo}, at most {@link #HELD_CHARACTERS} characters) keeps those characters back,
         * across a flush too, so a tag split by a write or a flush is still found. Nothing else is
         * held. The held characters are written at close, or when the filter completes the
         * response. A page that ends with no closing tag (an HTML fragment) streams unchanged and
         * is logged once by the filter.</p>
         *
         * <p>The stream goes to the response's writer, or, when the response beneath already
         * handed out its output stream (a nested forward or an outer filter), to that stream
         * encoded in the response charset, as the replay path does. The destination is acquired
         * before the captured prefix is released. After the switch the wrapper is in writer
         * passthrough: {@code isCommitted()} and {@code flushBuffer()} reach the real response,
         * and any Content-Length is dropped because the injection changed the body.</p>
         */
        private class BoundedCaptureWriter extends Writer {
            /** One less than the length of {@code </head>} and {@code </body>}: the longest partial tag. */
            private static final int HELD_CHARACTERS = 6;
            private static final String[] CLOSING_TAGS = {"</head>", "</body>"};

            private CharArrayWriter buffer;
            private Writer overflow;
            /** Streamed HTML whose script still waits for a closing tag. */
            private boolean injectionPending;
            /** The partial closing tag that ended the streamed text so far, not yet written. */
            private final StringBuilder held = new StringBuilder();

            BoundedCaptureWriter(CharArrayWriter buffer) {
                this.buffer = buffer;
            }

            @Override
            public void write(int character) throws IOException {
                write(String.valueOf((char) character), 0, 1);
            }

            @Override
            public void write(char[] chars, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, chars.length);
                // The whole array is wrapped, so positions below are the array's own indexes.
                accept(CharBuffer.wrap(chars), offset, offset + length);
            }

            @Override
            public void write(String string, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, string.length());
                accept(string, offset, offset + length);
            }

            /** Captures within the allowance; past it, streams positions {@code start..end} of the write. */
            private void accept(CharSequence source, int start, int end) throws IOException {
                if (overflow == null) {
                    int room = MAX_CAPTURE_CHARS - buffer.size();
                    if (end - start <= room) {
                        copy(buffer, source, start, end);
                        return;
                    }
                    copy(buffer, source, start, start + room);
                    startStreaming();
                    start += room;
                }
                stream(source, start, end);
            }

            private void startStreaming() throws IOException {
                Writer destination = streamingChannel();
                String prefix = buffer.toString();
                // Release the capture: only the bounded prefix copy above outlives the switch.
                buffer = null;
                captureWriter = null;
                LOGGER.debug("CsrfGuard: capture limit reached for {}; streaming the rest", requestUri);
                // The capture's own buffer is all the body there is; clear any container buffer as
                // the replay path does, while the response can still accept it.
                if (!CaptureResponseWrapper.super.isCommitted()) {
                    try {
                        CaptureResponseWrapper.super.resetBuffer();
                    } catch (IllegalStateException alreadyCommitted) {
                        LOGGER.debug("CsrfGuard: buffer reset rejected before streaming {}", requestUri);
                    }
                }
                writerPassthrough = true;
                captureOverflowed = true;
                overflow = destination;
                // The prefix is injected exactly as a replayed page would be (</head> preferred over
                // </body>); only when it holds neither does the search continue into the stream.
                boolean needsScript = RequestNegotiation.isHtmlContentType(getContentType())
                        && !CSRFGUARD_SCRIPT_PATTERN.matcher(prefix).find();
                String content = needsScript ? injectScript(prefix, scriptTag) : prefix;
                injectionPending = needsScript && content.length() == prefix.length();
                stream(content, 0, content.length());
            }

            /** The response's writer, or its already-selected output stream encoded in its charset. */
            private Writer streamingChannel() throws IOException {
                try {
                    return CaptureResponseWrapper.super.getWriter();
                } catch (IllegalStateException streamSelected) {
                    Charset charset = RequestNegotiation.responseCharset(CaptureResponseWrapper.this);
                    LOGGER.warn("CsrfGuard: response writer unavailable while streaming a large page; "
                            + "encoding it onto the output stream as {}: uri={}", charset.name(), requestUri);
                    CaptureResponseWrapper.super.setCharacterEncoding(charset.name());
                    return new InPlaceEncodingWriter(
                            new OutputStreamWriter(CaptureResponseWrapper.super.getOutputStream(), charset));
                }
            }

            /**
             * Writes positions {@code start..end} of a streamed write, injecting the script before the
             * first closing tag while one is awaited.
             *
             * <p>While the script waits, the search runs over the held partial tag followed by this
             * write, read in place: the two are never joined into a new string.</p>
             */
            private void stream(CharSequence source, int start, int end) throws IOException {
                if (!injectionPending) {
                    copy(overflow, source, start, end);
                    return;
                }
                int total = held.length() + end - start;
                int tag = closingTag(source, start, total);
                if (tag >= 0) {
                    writeJoined(source, start, 0, tag);
                    overflow.write(scriptTag + "\n");
                    writeJoined(source, start, tag, total);
                    held.setLength(0);
                    injectionPending = false;
                    return;
                }
                int keep = partialTagLength(source, start, total);
                writeJoined(source, start, 0, total - keep);
                // Copy the partial tag now: a writer may reuse its array once write returns.
                StringBuilder partial = new StringBuilder(keep);
                for (int position = total - keep; position < total; position++) {
                    partial.append(joinedCharAt(source, start, position));
                }
                held.setLength(0);
                held.append(partial);
            }

            /** Character {@code position} of the held partial tag followed by the write from {@code start}. */
            private char joinedCharAt(CharSequence source, int start, int position) {
                int split = held.length();
                return position < split ? held.charAt(position) : source.charAt(start + position - split);
            }

            /** Whether the joined text at {@code position} begins with {@code length} characters of {@code tag}. */
            private boolean joinedMatches(CharSequence source, int start, int position, String tag, int length) {
                for (int k = 0; k < length; k++) {
                    if (Character.toLowerCase(joinedCharAt(source, start, position + k)) != tag.charAt(k)) {
                        return false;
                    }
                }
                return true;
            }

            /** First position of a closing tag in the {@code total} joined characters, or -1. */
            private int closingTag(CharSequence source, int start, int total) {
                for (int position = 0; position + HELD_CHARACTERS < total; position++) {
                    if (joinedCharAt(source, start, position) != '<') {
                        continue;
                    }
                    for (String tag : CLOSING_TAGS) {
                        if (joinedMatches(source, start, position, tag, tag.length())) {
                            return position;
                        }
                    }
                }
                return -1;
            }

            /** Length of the longest end of the joined characters that a closing tag starts with. */
            private int partialTagLength(CharSequence source, int start, int total) {
                for (int length = Math.min(HELD_CHARACTERS, total); length > 0; length--) {
                    for (String tag : CLOSING_TAGS) {
                        if (joinedMatches(source, start, total - length, tag, length)) {
                            return length;
                        }
                    }
                }
                return 0;
            }

            /** Writes joined positions {@code from..to}: held characters, then the write, each from its own storage. */
            private void writeJoined(CharSequence source, int start, int from, int to) throws IOException {
                int split = held.length();
                if (from < split) {
                    overflow.append(held, from, Math.min(to, split));
                }
                if (to > split) {
                    copy(overflow, source, start + Math.max(from, split) - split, start + to - split);
                }
            }

            /**
             * Writes positions {@code from..to} of a write's own storage: a wrapped caller array (the
             * JSP writer's case) or the caller's string. Neither is copied on the way.
             */
            private static void copy(Writer target, CharSequence source, int from, int to) throws IOException {
                if (source instanceof CharBuffer wrappedArray) {
                    target.write(wrappedArray.array(), from, to - from);
                } else {
                    target.write((String) source, from, to - from);
                }
            }

            /** After an accepted reset discarded the streamed prefix: capture the next document again. */
            void rearm() {
                buffer = new CharArrayWriter();
                captureWriter = buffer;
                overflow = null;
                injectionPending = false;
                held.setLength(0);
            }

            /** Writes a held partial tag, which can no longer complete. */
            private void releaseHeld() throws IOException {
                overflow.append(held);
                held.setLength(0);
            }

            /** Flushes the stream; a held partial tag stays back so a tag split by the flush is found. */
            @Override
            public void flush() throws IOException {
                if (overflow != null) {
                    overflow.flush();
                }
            }

            @Override
            public void close() throws IOException {
                if (overflow != null) {
                    try {
                        releaseHeld();
                        overflow.close();
                    } finally {
                        overflow = null;
                    }
                }
            }

            /** The filter is completing a streamed response: write any held partial tag and flush. */
            void finish() throws IOException {
                if (overflow != null) {
                    releaseHeld();
                    overflow.flush();
                }
            }
        }

        /**
         * Encodes string writes onto an {@link OutputStreamWriter} without first copying them.
         *
         * <p>On Java 25, {@code OutputStreamWriter.write(String, int, int)} copies the slice into a
         * new {@code char[]} of its full length before encoding, so a streamed large page would
         * allocate twice its size. {@code append(CharBuffer)} encodes the same characters in place,
         * in the encoder's own fixed-size chunks. Character-array writes, flush and close pass
         * straight through.</p>
         */
        private static final class InPlaceEncodingWriter extends FilterWriter {

            InPlaceEncodingWriter(OutputStreamWriter encoder) {
                super(encoder);
            }

            @Override
            public void write(String text, int offset, int length) throws IOException {
                out.append(CharBuffer.wrap(text, offset, offset + length));
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
