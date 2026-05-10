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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.CarlosProperties;
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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Servlet filter that sanitizes error responses to prevent Java stack traces and internal
 * implementation details from reaching clients.
 *
 * <p>When an error response (HTTP 4xx or 5xx) is detected that contains stack trace markers,
 * the body is replaced with a generic error page that includes only the HTTP status code and
 * a correlation ID. A sanitized excerpt (up to 200 characters) is logged server-side with the
 * same correlation ID so operators can diagnose issues without exposing sensitive information
 * to clients.</p>
 *
 * <h3>Detection</h3>
 * <p>Stack trace detection uses a {@link Pattern} that matches common Java stack trace markers:
 * {@code at pkg.Class.method(File.java:nn)}, {@code Caused by:}, {@code java.lang.},
 * {@code jakarta.servlet.}, and class name prefixes for Tomcat, Spring, Hibernate, and
 * the CARLOS application itself.</p>
 *
 * <h3>Scope</h3>
 * <p>Captures responses written via {@link PrintWriter} (text content). Binary responses
 * written via {@link ServletOutputStream} pass through unmodified. Responses committed
 * by {@code sendError()} or {@code sendRedirect()} before the chain returns also pass through —
 * those trigger Tomcat's error-page mechanism which forwards to
 * {@code /WEB-INF/jsp/error/errorpage.jsp}.</p>
 *
 * <h3>Configuration</h3>
 * <p>The filter can be disabled in {@code carlos.properties} for development environments
 * where stack traces aid debugging:
 * <pre>
 * response.sanitization.enabled=false
 * </pre>
 * Default: {@code true} (enabled).</p>
 *
 * <h3>Placement</h3>
 * <p>Must be mapped as the outermost filter in {@code web.xml} (first {@code <filter-mapping>})
 * so it can intercept errors from all downstream layers including other WAF filters, the Struts
 * dispatcher, and JSP processing.</p>
 *
 * <h3>Compliance</h3>
 * <ul>
 *   <li>NIST SP 800-53 Rev. 5 SI-11: Error Handling</li>
 *   <li>OWASP Top 10 (2021) A05: Security Misconfiguration</li>
 *   <li>OWASP ASVS 7.4.1: Generic error messages with support IDs</li>
 *   <li>CWE-209: Generation of Error Message Containing Sensitive Information</li>
 * </ul>
 *
 * @since 2026-04-06
 */
public class ResponseSanitizationFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseSanitizationFilter.class);

    /**
     * Property name in {@code carlos.properties} that controls whether this filter is active.
     * Set to {@code false} to disable sanitization (e.g. in development environments).
     * Default: {@code true} (enabled).
     */
    static final String ENABLED_PROPERTY = "response.sanitization.enabled";

    /**
     * Property name that activates developer error display mode. When this property is
     * {@code true} / {@code yes} / {@code on} in {@code carlos.properties}, this filter
     * disables itself so that raw exception details reach the browser.
     *
     * <p><strong>SECURITY RISK:</strong> Stack traces expose internal class names,
     * library versions, code paths, and data structures that significantly aid attackers.
     * Only enable in isolated devcontainer environments with synthetic test data.
     * Never enable in any environment with real patient data or external network access.</p>
     *
     * @see <a href="https://owasp.org/www-project-top-ten/">OWASP A05 Security Misconfiguration</a>
     */
    static final String DISPLAY_ERROR_PROPERTY = "DISPLAY_ERROR";

    /**
     * Maximum number of characters to buffer per response before switching to pass-through mode.
     * Responses larger than this cannot contain a stack trace header; skipping inspection
     * avoids unbounded heap growth for large encounter notes, lab reports, etc.
     */
    static final int MAX_CAPTURE_CHARS = 512 * 1024;

    /**
     * Regex that matches Java stack trace markers commonly found in unhandled error responses.
     *
     * <p>Patterns matched:
     * <ul>
     *   <li>{@code \n   at pkg.Class.method(File.java:nn)} — standard stack frame line</li>
     *   <li>{@code Caused by:} — chained exception marker</li>
     *   <li>{@code java.lang.} — JDK core exception class names (e.g. NullPointerException)</li>
     *   <li>{@code io.github.carlos_emr.} — CARLOS application class names</li>
     *   <li>{@code jakarta.servlet.} — Jakarta Servlet API exception class names</li>
     *   <li>{@code org.apache.} — Apache Tomcat / Struts / Commons class names</li>
     *   <li>{@code org.springframework.} — Spring Framework class names</li>
     *   <li>{@code org.hibernate.} — Hibernate ORM class names</li>
     * </ul>
     *
     * <p>The {@code at} frame pattern uses a preceding newline anchor so it does not match
     * the word "at" in normal English text (e.g. "error at line"). Class name patterns use
     * a word boundary {@code \b} for the same reason.</p>
     */
    static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "(?:[\r\n]\\s*at\\s+[\\w.$]+\\.[\\w$<>]+\\([^)]*\\))" // stack frame: \n   at pkg.Class.method(File.java:nn)
            + "|(?:\\bCaused by:)"                                   // chained exception
            + "|(?:\\bjava\\.lang\\.)"                               // JDK exception class names
            + "|(?:\\bio\\.github\\.carlos_emr\\.)"                  // CARLOS class names
            + "|(?:\\bjakarta\\.servlet\\.)"                         // Servlet API exceptions
            + "|(?:\\borg\\.apache\\.)"                              // Tomcat / Struts / Commons
            + "|(?:\\borg\\.springframework\\.)"                     // Spring Framework
            + "|(?:\\borg\\.hibernate\\.)"                           // Hibernate ORM
    );

    private boolean enabled;

    /**
     * Reads the {@code response.sanitization.enabled} property from {@code carlos.properties}.
     * Defaults to {@code true} (enabled) when the property is absent or blank.
     *
     * @param filterConfig FilterConfig the servlet container filter configuration
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String propValue = CarlosProperties.getInstance().getProperty(ENABLED_PROPERTY, "").trim();
        enabled = propValue.isEmpty() || Boolean.parseBoolean(propValue);
        if (enabled && CarlosProperties.getInstance().isPropertyActive(DISPLAY_ERROR_PROPERTY)) {
            // DISPLAY_ERROR overrides the sanitization flag — it is a developer-only mode
            // that deliberately allows raw exception details to reach the browser. Disable
            // sanitization so the full error context is visible. This must NEVER be active
            // in production (see DISPLAY_ERROR_PROPERTY Javadoc).
            enabled = false;
            LOGGER.warn("DISPLAY_ERROR is active — ResponseSanitizationFilter disabled. "
                    + "Stack traces WILL be sent to clients. "
                    + "This is a SECURITY RISK — do not enable in production environments "
                    + "or any system with real patient data.");
        }
        LOGGER.info("ResponseSanitizationFilter initialized: enabled={}", enabled);
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        LOGGER.info("ResponseSanitizationFilter shutdown");
    }

    /**
     * Wraps the response to capture PrintWriter output, inspects it for stack trace markers
     * after the chain completes, and replaces any tainted error response with a generic page.
     *
     * <p>Binary responses (via {@code getOutputStream()}) and committed responses
     * (via {@code sendError()} or {@code sendRedirect()}) pass through unmodified.</p>
     *
     * @param request  ServletRequest the incoming request
     * @param response ServletResponse the outgoing response
     * @param chain    FilterChain the remaining filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if the filter chain throws
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled
                || !(request instanceof HttpServletRequest)
                || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        CapturingResponseWrapper wrapper = new CapturingResponseWrapper(httpResponse);

        try {
            chain.doFilter(request, wrapper);
        } catch (IOException | ServletException | RuntimeException e) {
            // An exception escaped the entire filter chain.
            // Always log for operational visibility and auditability, even when the response
            // is already committed, so failures are never silently swallowed.
            String correlationId = generateCorrelationId();
            String requestUri = ((HttpServletRequest) request).getRequestURI();
            boolean committed = httpResponse.isCommitted();
            LOGGER.error(
                    "Uncaught exception escaped filter chain [uri={} correlationId={} committed={}]",
                    requestUri, correlationId, committed, e);
            if (!committed) {
                sendSanitizedError(httpResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        correlationId);
                return;
            }
            // Response already committed — rethrow so the container can handle the broken
            // connection correctly rather than treating the request as successfully completed.
            if (e instanceof IOException) {
                throw (IOException) e;
            } else if (e instanceof ServletException) {
                throw (ServletException) e;
            } else {
                throw (RuntimeException) e;
            }
        }

        // Response was committed by sendError() or sendRedirect() inside the chain.
        // Tomcat's error-page mechanism will forward to /WEB-INF/jsp/error/errorpage.jsp,
        // which is already safe.
        if (wrapper.isResponseCommitted()) {
            return;
        }

        // Binary content (getOutputStream) — stack traces do not appear in binary responses.
        if (wrapper.isUsingOutputStream()) {
            return;
        }

        // getWriter() was never called — nothing to sanitize, nothing to flush.
        if (!wrapper.isUsingWriter()) {
            return;
        }

        // Response exceeded the capture limit — content already written directly to the real
        // response by CapturingSwitchingWriter. Large responses cannot contain stack traces;
        // skip inspection.
        if (wrapper.isCaptureLimitExceeded()) {
            return;
        }

        int status = wrapper.getStatus();
        String capturedBody = wrapper.getCapturedContent();

        if (status >= 400 && containsStackTrace(capturedBody)) {
            // Stack trace detected: log only a sanitized excerpt and send a sanitized replacement.
            String correlationId = generateCorrelationId();
            String sanitizedExcerpt = LogSanitizer.sanitize(capturedBody, 200);
            LOGGER.error("Stack trace detected in error response "
                    + "[status={} uri={} correlationId={} excerpt={}]",
                    status,
                    ((HttpServletRequest) request).getRequestURI(),
                    correlationId,
                    sanitizedExcerpt);
            sendSanitizedError(httpResponse, status, correlationId);
        } else {
            // Safe response — write captured content through to the real response.
            writeToResponse(httpResponse, capturedBody);
        }
    }

    /**
     * Tests whether the given response body string contains Java stack trace markers.
     * Package-private to allow direct unit testing without a servlet container.
     *
     * @param body String the response body to inspect; may be {@code null} or empty
     * @return {@code true} if the body matches one or more stack trace patterns
     */
    static boolean containsStackTrace(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return STACK_TRACE_PATTERN.matcher(body).find();
    }

    /**
     * Generates a random UUID correlation ID for log/response correlation.
     * Package-private to allow override in tests.
     *
     * @return String a newly generated UUID string (e.g. {@code "550e8400-e29b-41d4-a716-446655440000"})
     */
    static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Clears the response body buffer and writes a generic HTML error page containing only the
     * HTTP status code and the correlation ID.
     *
     * <p>Uses {@link HttpServletResponse#resetBuffer()} rather than
     * {@link HttpServletResponse#reset()} deliberately: {@code resetBuffer()} clears only
     * the body buffer, leaving previously-set response headers intact. This preserves security
     * headers ({@code X-Frame-Options}, {@code X-Content-Type-Options}, etc.) set by
     * downstream filters such as {@code ResponseDefaultsFilter}, as well as session cookies.
     * Calling {@code reset()} would wipe all headers, undermining the security posture of the
     * rest of the filter chain.</p>
     *
     * @param response      HttpServletResponse the real (unwrapped) response
     * @param status        int the HTTP error status code (4xx or 5xx)
     * @param correlationId String the correlation ID for log lookup
     * @throws IOException if an I/O error occurs writing the error page
     */
    private static void sendSanitizedError(HttpServletResponse response, int status,
            String correlationId) throws IOException {
        if (response.isCommitted()) {
            LOGGER.debug("Cannot send sanitized error — response already committed "
                    + "[status={} correlationId={}]", status, correlationId);
            return;
        }
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            LOGGER.debug("Cannot reset buffer for sanitized error [correlationId={}]: {}",
                    correlationId, e.getMessage());
            return;
        }
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        String body = buildSanitizedErrorPage(status, correlationId);
        // Content-Length is intentionally not set — the container handles chunked encoding.
        // Setting it risks byte-count mismatches if the response charset is not UTF-8,
        // consistent with CsrfGuardScriptInjectionFilter which avoids setContentLength
        // for writer-based responses for the same reason.
        try {
            PrintWriter writer = response.getWriter();
            writer.write(body);
            writer.flush();
        } catch (IllegalStateException ex) {
            // Response is in output-stream mode (e.g. a binary download threw mid-write).
            // Fall back to byte stream so the sanitized error page still reaches the client.
            LOGGER.debug("sendSanitizedError: getWriter() failed (stream mode active)"
                    + " [correlationId={}]: {}", correlationId, ex.getMessage());
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            response.getOutputStream().write(bodyBytes);
            response.getOutputStream().flush();
        }
    }

    /**
     * Builds a minimal HTML error page containing only the HTTP status code and correlation ID.
     *
     * @param status        int the HTTP error status code
     * @param correlationId String the correlation ID for support staff to look up the full error
     * @return String a complete HTML document (no stack traces or implementation details)
     */
    private static String buildSanitizedErrorPage(int status, String correlationId) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head><meta charset=\"UTF-8\"><title>Error " + status + "</title></head>\n"
                + "<body>\n"
                + "<h1>An error occurred</h1>\n"
                + "<p>HTTP Status: " + status + "</p>\n"
                + "<p>Reference ID: " + correlationId + "</p>\n"
                + "<p>If this problem persists, please contact support with the Reference ID above.</p>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * Writes the captured response content to the real response.
     * Uses {@link HttpServletResponse#resetBuffer()} to clear any partially-written buffer
     * before writing the captured content.
     *
     * @param response HttpServletResponse the real (unwrapped) response
     * @param content  String the captured response body to write; no-op if null or empty
     * @throws IOException if an I/O error occurs
     */
    private static void writeToResponse(HttpServletResponse response, String content)
            throws IOException {
        if (content == null || content.isEmpty()) {
            return;
        }
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            LOGGER.debug("writeToResponse: cannot resetBuffer — response already committed: {}",
                    e.getMessage());
        }
        try {
            PrintWriter out = response.getWriter();
            out.write(content);
            out.flush();
        } catch (IllegalStateException e) {
            // getWriter() failed because getOutputStream() was already called on the real response.
            // Fall back to byte-stream output.
            String encoding = response.getCharacterEncoding();
            if (encoding == null || encoding.isEmpty()) {
                encoding = StandardCharsets.UTF_8.name();
            }
            byte[] bytes = content.getBytes(encoding);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        }
    }

    /**
     * Response wrapper that captures {@link PrintWriter} output into a {@link CharArrayWriter}
     * while passing {@link ServletOutputStream} calls through to the real response unchanged.
     *
     * <p>This dual-mode design ensures:
     * <ul>
     *   <li>Binary responses (PDFs, images, downloads) are never buffered or corrupted.</li>
     *   <li>Text responses (HTML error pages) are fully captured for inspection.</li>
     *   <li>Buffer flushing is suppressed during capture to prevent the response being committed
     *       before the outer filter can replace the body.</li>
     * </ul>
     * </p>
     *
     * <p>{@code sendError()} and {@code sendRedirect()} are delegated immediately to the
     * real response and mark the wrapper as committed, signalling the outer filter that
     * Tomcat's error-page mechanism has taken over.</p>
     */
    private static class CapturingResponseWrapper extends HttpServletResponseWrapper {

        private CapturingSwitchingWriter switchingWriter;
        private PrintWriter writer;
        private boolean usingWriter;
        private boolean usingOutputStream;
        private boolean committed;

        /**
         * Wraps the given response for capture.
         *
         * @param response HttpServletResponse the real response to wrap
         */
        public CapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        /**
         * Returns a capturing {@link PrintWriter} that writes to an in-memory
         * {@link CharArrayWriter} instead of the real response.
         *
         * @return PrintWriter the capturing writer
         * @throws IllegalStateException if {@link #getOutputStream()} was already called
         * @throws IOException           if an I/O error occurs
         */
        @Override
        public PrintWriter getWriter() throws IOException {
            if (usingOutputStream) {
                throw new IllegalStateException(
                        "getOutputStream() has already been called on this response");
            }
            usingWriter = true;
            if (writer == null) {
                CharArrayWriter buffer = new CharArrayWriter();
                switchingWriter = new CapturingSwitchingWriter(buffer,
                        (HttpServletResponse) getResponse(), MAX_CAPTURE_CHARS);
                writer = new PrintWriter(switchingWriter);
            }
            return writer;
        }

        /**
         * Passes the output stream through to the real response (binary passthrough path).
         *
         * @return ServletOutputStream the real response output stream
         * @throws IllegalStateException if {@link #getWriter()} was already called
         * @throws IOException           if an I/O error occurs
         */
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (usingWriter) {
                throw new IllegalStateException(
                        "getWriter() has already been called on this response");
            }
            usingOutputStream = true;
            return super.getOutputStream();
        }

        /**
         * Delegates {@code sendError} to the real response and marks this wrapper as committed.
         * Tomcat's error-page mechanism will forward to
         * {@code /WEB-INF/jsp/error/errorpage.jsp}.
         *
         * @param sc int the HTTP error status code
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void sendError(int sc) throws IOException {
            committed = true;
            super.sendError(sc);
        }

        /**
         * Delegates {@code sendError} to the real response and marks this wrapper as committed.
         *
         * @param sc  int the HTTP error status code
         * @param msg String the error message (not sent to the client via this filter)
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void sendError(int sc, String msg) throws IOException {
            committed = true;
            super.sendError(sc, msg);
        }

        /**
         * Delegates {@code sendRedirect} to the real response and marks this wrapper as committed.
         *
         * @param location String the redirect URL
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void sendRedirect(String location) throws IOException {
            committed = true;
            super.sendRedirect(location);
        }

        /**
         * Suppresses buffer flushing during the capture phase to prevent the response from
         * being committed before the outer filter has had a chance to inspect the body.
         * Output-stream responses are flushed normally to support streaming content.
         *
         * @throws IOException if an I/O error occurs during an output-stream flush
         */
        @Override
        public void flushBuffer() throws IOException {
            if (usingOutputStream) {
                // Binary passthrough — flush as normal to support streaming downloads.
                super.flushBuffer();
                return;
            }
            // Writer capture path — suppress to prevent premature commitment.
        }

        /**
         * Returns {@code false} during writer-capture mode so that downstream filters
         * (e.g. ResponseDefaultsFilter) can continue writing headers after the chain returns.
         *
         * @return {@code false} if writer capture is active and the response has not been
         *         explicitly committed; otherwise delegates to the real response
         */
        @Override
        public boolean isCommitted() {
            if (usingWriter && !committed) {
                return false;
            }
            return committed || super.isCommitted();
        }

        /**
         * Returns {@code true} if {@code sendError()} or {@code sendRedirect()} was called
         * during chain processing, indicating that the response was committed and the body
         * cannot be replaced.
         *
         * @return boolean {@code true} if the response was committed via sendError/sendRedirect
         */
        public boolean isResponseCommitted() {
            return committed;
        }

        /**
         * Returns {@code true} if the response was written via {@link #getWriter()}.
         *
         * @return boolean {@code true} if writer capture mode is active
         */
        public boolean isUsingWriter() {
            return usingWriter;
        }

        /**
         * Returns {@code true} if the response was written via {@link #getOutputStream()}.
         *
         * @return boolean {@code true} if output-stream passthrough mode is active
         */
        public boolean isUsingOutputStream() {
            return usingOutputStream;
        }

        /**
         * Returns {@code true} if the captured response body exceeded
         * {@link ResponseSanitizationFilter#MAX_CAPTURE_CHARS} and content was written
         * directly to the real response by the {@link CapturingSwitchingWriter}.
         * In this case no further inspection or write-back is needed in the outer filter.
         *
         * @return boolean {@code true} if the capture limit was exceeded
         */
        public boolean isCaptureLimitExceeded() {
            return switchingWriter != null && switchingWriter.isLimitExceeded();
        }

        /**
         * Returns the content captured so far from the in-memory writer.
         * Flushes the writer before returning to ensure all buffered content is included.
         *
         * @return String the captured response body; never {@code null}, empty string if nothing
         *         was written
         */
        public String getCapturedContent() {
            if (writer != null) {
                writer.flush();
            }
            return switchingWriter != null ? switchingWriter.getCaptured() : "";
        }
    }

    /**
     * A {@link Writer} that buffers writes into a {@link CharArrayWriter} up to
     * {@code maxChars} characters, then switches to direct pass-through via the real
     * response {@link PrintWriter} to prevent unbounded heap growth for large responses.
     *
     * <p>When the limit is exceeded the buffered content is flushed to the real writer
     * before subsequent writes go directly there. Callers can test
     * {@link #isLimitExceeded()} to determine whether content was captured in the buffer
     * or written directly to the real response (in which case no write-back is needed).</p>
     */
    private static class CapturingSwitchingWriter extends Writer {

        private final CharArrayWriter buffer;
        private final HttpServletResponse realResponse;
        private final int maxChars;
        private PrintWriter passthroughWriter;
        private boolean limitExceeded;

        /**
         * Constructs a new writer that buffers into {@code buffer} up to {@code maxChars}
         * characters, then switches to writing directly to {@code realResponse}.
         *
         * @param buffer       CharArrayWriter the in-memory buffer for capture mode
         * @param realResponse HttpServletResponse the real servlet response for passthrough mode
         * @param maxChars     int the maximum number of characters to buffer before switching
         */
        CapturingSwitchingWriter(CharArrayWriter buffer, HttpServletResponse realResponse,
                int maxChars) {
            this.buffer = buffer;
            this.realResponse = realResponse;
            this.maxChars = maxChars;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (!limitExceeded && buffer.size() + len > maxChars) {
                switchToPassthrough();
            }
            if (limitExceeded) {
                passthroughWriter.write(cbuf, off, len);
            } else {
                buffer.write(cbuf, off, len);
            }
        }

        @Override
        public void write(int c) throws IOException {
            if (!limitExceeded && buffer.size() + 1 > maxChars) {
                switchToPassthrough();
            }
            if (limitExceeded) {
                passthroughWriter.write(c);
            } else {
                buffer.write(c);
            }
        }

        @Override
        public void write(String str, int off, int len) throws IOException {
            if (!limitExceeded && buffer.size() + len > maxChars) {
                switchToPassthrough();
            }
            if (limitExceeded) {
                passthroughWriter.write(str, off, len);
            } else {
                buffer.write(str, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            if (limitExceeded && passthroughWriter != null) {
                passthroughWriter.flush();
            }
        }

        @Override
        public void close() throws IOException {
            flush();
        }

        /**
         * Returns {@code true} if the capture limit was exceeded and subsequent content
         * was written directly to the real response.
         *
         * @return boolean {@code true} if limit exceeded
         */
        boolean isLimitExceeded() {
            return limitExceeded;
        }

        /**
         * Returns the buffered content if the capture limit was not exceeded, or an empty
         * string if the limit was exceeded (content was written directly to the real response).
         *
         * @return String the captured content, or {@code ""} if limit was exceeded
         */
        String getCaptured() {
            if (limitExceeded) {
                return "";
            }
            return buffer.toString();
        }

        /**
         * Flushes the in-memory buffer to the real response writer and switches to
         * direct pass-through mode for all subsequent writes.
         *
         * @throws IOException if the real response writer cannot be obtained or written to
         */
        private void switchToPassthrough() throws IOException {
            if (limitExceeded) {
                return;
            }
            limitExceeded = true;
            LOGGER.debug("ResponseSanitizationFilter: capture limit exceeded ({} chars)"
                    + " — switching to passthrough mode", maxChars);
            passthroughWriter = realResponse.getWriter();
            char[] captured = buffer.toCharArray();
            if (captured.length > 0) {
                passthroughWriter.write(captured);
            }
            buffer.reset();
        }
    }
}
