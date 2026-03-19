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
import java.util.Locale;
import java.util.regex.Pattern;

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
 * <p>Injection is skipped when:
 * <ul>
 *   <li>Content-Type is not {@code text/html}</li>
 *   <li>The request is AJAX ({@code X-Requested-With: XMLHttpRequest})</li>
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

    private static final String AJAX_HEADER_NAME = "X-Requested-With";
    private static final String AJAX_HEADER_VALUE = "XMLHttpRequest";

    /**
     * Matches a {@code <script src="...csrfguard...">} tag (case-insensitive).
     * Used for the idempotency check to avoid double-injection. A simple
     * {@code contains("/csrfguard")} would produce false positives on pages where
     * the literal string appears in JavaScript code, comments, or user-generated content.
     */
    private static final Pattern CSRFGUARD_SCRIPT_PATTERN =
            Pattern.compile("<script[^>]*src=[\"'][^\"']*\\/csrfguard[\"']", Pattern.CASE_INSENSITIVE);

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

        // Skip AJAX requests early (before wrapping)
        String requestedWith = httpRequest.getHeader(AJAX_HEADER_NAME);
        if (AJAX_HEADER_VALUE.equalsIgnoreCase(requestedWith)) {
            chain.doFilter(request, response);
            return;
        }

        // Skip Struts .do requests — Tomcat 11's RequestDispatcher.forward() closes
        // the output stream after the first buffer flush, truncating responses > 8KB.
        // The filter-mapping includes FORWARD dispatcher so this filter also runs when
        // Struts forwards to the JSP, wrapping it to prevent truncation. AJAX sidebar
        // calls use EctDisplayAction.include() which bypasses Struts results entirely.
        String servletPath = httpRequest.getServletPath();
        if (servletPath != null && servletPath.endsWith(".do")) {
            chain.doFilter(request, response);
            return;
        }

        // Skip if CsrfGuard is disabled
        CsrfGuard csrfGuard;
        try {
            csrfGuard = CsrfGuard.getInstance();
        } catch (Exception e) {
            LOGGER.error("CsrfGuard.getInstance() failed — page will be served without CSRF "
                    + "script tag (method={})", httpRequest.getMethod(), e);
            chain.doFilter(request, response);
            return;
        }

        if (!csrfGuard.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        CaptureResponseWrapper wrapper = new CaptureResponseWrapper(httpResponse);
        LOGGER.debug("CsrfGuard: wrapping request {}", httpRequest.getRequestURI());
        chain.doFilter(request, wrapper);
        LOGGER.debug("CsrfGuard: chain completed for {} committed={} writer={} stream={}",
                httpRequest.getRequestURI(), wrapper.isResponseCommitted(),
                wrapper.isUsingWriter(), wrapper.isUsingOutputStream());

        // If the response was committed by sendRedirect() or sendError(), the status and
        // headers have already been sent to the client — no post-processing is possible
        if (wrapper.isResponseCommitted()) {
            LOGGER.debug("CsrfGuard: response committed for {}", httpRequest.getRequestURI());
            return;
        }

        // If the downstream code used getOutputStream(), content was written directly to the
        // client via the real response's output stream — no capture occurred and no injection
        // is possible
        if (wrapper.isUsingOutputStream()) {
            LOGGER.debug("CsrfGuard: output stream used for {}", httpRequest.getRequestURI());
            return;
        }

        // If getWriter() was never called either, nothing to do
        if (!wrapper.isUsingWriter()) {
            LOGGER.debug("CsrfGuard script injection skipped — getWriter() was never called "
                    + "(usingOutputStream={}, committed={}, uri={})",
                    wrapper.isUsingOutputStream(), wrapper.isResponseCommitted(),
                    httpRequest.getRequestURI());
            return;
        }

        String contentType = wrapper.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
            // Not HTML — write captured content through without modification
            writeToResponse(httpResponse, wrapper.getCapturedContent());
            return;
        }

        String captured = wrapper.getCapturedContent();
        LOGGER.debug("CsrfGuard: captured {} bytes for {} contentType={}",
                captured != null ? captured.length() : 0, httpRequest.getRequestURI(), contentType);

        // Idempotency: skip if the page already contains a <script src="...csrfguard..."> tag.
        // Regex is used rather than contains() to avoid false positives when the literal
        // string "/csrfguard" appears in JavaScript code, comments, or user-generated content.
        if (CSRFGUARD_SCRIPT_PATTERN.matcher(captured).find()) {
            writeToResponse(httpResponse, captured);
            return;
        }

        String scriptTag = "<script src=\"" + httpRequest.getContextPath() + "/csrfguard\"></script>";
        String modified = injectScript(captured, scriptTag);

        writeToResponse(httpResponse, modified);
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
     * Writes the final content to the real response, updating Content-Length.
     *
     * <p>Uses {@code getWriter()} for text content because Tomcat 11's
     * {@code RequestDispatcher.forward()} may have already opened the writer
     * on the underlying response. Calling {@code getOutputStream()} after
     * {@code getWriter()} throws {@code IllegalStateException}.</p>
     */
    private void writeToResponse(HttpServletResponse response, String content) throws IOException {
        // Clear only the response body buffer, preserving status code, headers, and cookies
        // set by downstream components. resetBuffer() is safer than reset() which would
        // wipe Set-Cookie, security headers, CSP, and non-200 status codes.
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            LOGGER.debug("writeToResponse: cannot resetBuffer for {}-byte content", content.length());
        }
        String encoding = response.getCharacterEncoding();
        if (encoding == null || encoding.isEmpty()) {
            encoding = "UTF-8";
        }
        byte[] bytes = content.getBytes(encoding);
        response.setContentLength(bytes.length);
        try {
            response.getWriter().write(content);
            response.getWriter().flush();
        } catch (IllegalStateException e) {
            // getWriter() failed because getOutputStream() was already called — use stream instead
            response.getOutputStream().write(bytes);
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
        private boolean committed;
        private Integer deferredContentLength;
        private Long deferredContentLengthLong;

        public CaptureResponseWrapper(HttpServletResponse response) {
            super(response);
            // Increase underlying response buffer to 1 MB to prevent premature flushing
            // before our wrapper can capture the complete HTML content for script injection
            // Note: setBufferSize removed for Tomcat 11 compatibility — the default
            // buffer is sufficient since we capture via CharArrayWriter, not the response buffer
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (usingWriter) {
                throw new IllegalStateException("getWriter() has already been called on this response");
            }
            usingOutputStream = true;
            // Apply any Content-Length that was set before we knew the response mode.
            // For output-stream passthrough, the header must reach the underlying response.
            if (deferredContentLength != null) {
                super.setContentLength(deferredContentLength);
                deferredContentLength = null;
            }
            if (deferredContentLengthLong != null) {
                super.setContentLengthLong(deferredContentLengthLong);
                deferredContentLengthLong = null;
            }
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (usingOutputStream) {
                throw new IllegalStateException("getOutputStream() has already been called on this response");
            }
            usingWriter = true;
            if (writer == null) {
                captureWriter = new CharArrayWriter();
                writer = new PrintWriter(captureWriter);
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
            if (usingWriter) {
                // Writer-capture path: suppress — final length is recomputed in writeToResponse()
                return;
            }
            // Mode not yet determined — defer until getOutputStream() or getWriter() is called
            deferredContentLengthLong = len;
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

        public String getCapturedContent() {
            if (writer != null) {
                writer.flush();
            }
            return captureWriter != null ? captureWriter.toString() : "";
        }
    }
}
