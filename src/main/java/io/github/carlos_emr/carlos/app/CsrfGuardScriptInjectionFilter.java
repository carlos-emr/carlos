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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

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
        chain.doFilter(request, wrapper);

        // If the response was committed by sendRedirect() or sendError(), the status and
        // headers have already been sent to the client — no post-processing is possible
        if (wrapper.isResponseCommitted()) {
            return;
        }

        // If the downstream code used getOutputStream(), content was written directly to the
        // client via the real response's output stream — no capture occurred and no injection
        // is possible
        if (wrapper.isUsingOutputStream()) {
            return;
        }

        // If getWriter() was never called either, nothing to do
        if (!wrapper.isUsingWriter()) {
            return;
        }

        String contentType = wrapper.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
            // Not HTML — write captured content through without modification
            writeToResponse(httpResponse, wrapper.getCapturedContent());
            return;
        }

        String captured = wrapper.getCapturedContent();

        // Idempotency: skip if the page already includes the csrfguard script
        if (captured.contains("/csrfguard\"") || captured.contains("/csrfguard'")) {
            writeToResponse(httpResponse, captured);
            return;
        }

        String scriptTag = "<script src=\"" + httpRequest.getContextPath() + "/csrfguard\"></script>";
        String modified = injectScript(captured, scriptTag);

        writeToResponse(httpResponse, modified);
    }

    /**
     * Injects the script tag into the HTML content.
     * Tries {@code </head>} first, then {@code </body>}, then appends at end.
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

        // Last resort: append at end
        return html + scriptTag + "\n";
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
     */
    private void writeToResponse(HttpServletResponse response, String content) throws IOException {
        String encoding = response.getCharacterEncoding();
        if (encoding == null) {
            encoding = "UTF-8";
        }
        byte[] bytes;
        try {
            bytes = content.getBytes(encoding);
        } catch (java.io.UnsupportedEncodingException e) {
            LOGGER.warn("Response has unsupported encoding '{}' — falling back to UTF-8", encoding, e);
            bytes = content.getBytes("UTF-8");
        }
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
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

        public CaptureResponseWrapper(HttpServletResponse response) {
            super(response);
            // Increase underlying response buffer to 1 MB to prevent premature flushing
            // before our wrapper can capture the complete HTML content for script injection
            try {
                response.setBufferSize(1024 * 1024);
            } catch (IllegalStateException e) {
                LOGGER.warn("Could not increase response buffer to 1 MB (response may already "
                        + "be committed). Script injection may fail for large pages.");
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (usingWriter) {
                throw new IllegalStateException("getWriter() has already been called on this response");
            }
            usingOutputStream = true;
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
            // Suppress — final Content-Length is set after script injection in writeToResponse()
        }

        @Override
        public void setContentLengthLong(long len) {
            // Suppress — final Content-Length is set after script injection in writeToResponse()
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            committed = true;
            super.sendRedirect(location);
        }

        @Override
        public void sendError(int sc) throws IOException {
            committed = true;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            committed = true;
            super.sendError(sc, msg);
        }

        @Override
        public void flushBuffer() throws IOException {
            // Suppress flushing to prevent captured content from being committed to the client
            // before script injection. For getOutputStream() responses (binary passthrough) this
            // is harmless since the output stream can be flushed independently via stream.flush().
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("flushBuffer() suppressed during CSRF script injection capture");
            }
        }

        public boolean isResponseCommitted() {
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
