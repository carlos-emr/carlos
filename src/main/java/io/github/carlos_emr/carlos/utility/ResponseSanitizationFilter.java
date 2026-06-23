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
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Servlet filter that sanitizes error responses to prevent Java stack traces and internal
 * implementation details from reaching clients.
 *
 * <p>When an error response (HTTP 4xx or 5xx) is detected that contains stack trace markers,
 * the body is replaced with a generic error page that includes only the HTTP status code and
 * a correlation ID. Logs include status, route, and correlation ID, but never response-body
 * excerpts because error pages can contain PHI even after control-character sanitization.</p>
 *
 * <p>For web-service routes ({@code /ws/*}, the CXF servlet), the body of any {@code 5xx}
 * response is replaced regardless of stack-trace content. A mid-stream Jackson/JAXB
 * serialization failure leaves a clean, well-formed JSON/XML prefix (e.g. patient demographics)
 * already written; that prefix does not match the stack-trace heuristic but still leaks PHI.
 * A web-service {@code 5xx} carries no client-meaningful entity body, so suppressing it closes
 * the exposure without breaking an API contract. See issue #2953.</p>
 *
 * <h3>Detection</h3>
 * <p>Stack trace detection uses a {@link Pattern} that matches common Java stack trace markers:
 * {@code at pkg.Class.method(File.java:nn)}, {@code Caused by:}, {@code java.lang.},
 * {@code jakarta.servlet.}, and class name prefixes for Tomcat, Spring, Hibernate, and
 * the CARLOS application itself.</p>
 *
 * <h3>Scope</h3>
 * <p>Captures responses written via {@link PrintWriter} (text content). Output-stream error
 * responses are buffered up to the same cap because some legacy actions write text errors through
 * {@link ServletOutputStream}; clearly binary successful responses still pass through. Responses committed
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
     * Property name that activates developer error display mode. This property is intentionally
     * advisory for this filter: raw exception details reach the browser only when
     * {@code response.sanitization.enabled=false} is also set.
     *
     * <p><strong>SECURITY RISK:</strong> Stack traces expose internal class names,
     * library versions, code paths, and data structures that significantly aid attackers.
     * Only enable in isolated devcontainer environments with synthetic test data.
     * Never enable in any environment with real patient data or external network access.</p>
     *
     * @see <a href="https://owasp.org/www-project-top-ten/">OWASP A05 Security Misconfiguration</a>
     */
    static final String DISPLAY_ERROR_PROPERTY = "DISPLAY_ERROR";

    private static final Set<String> ENTITY_HEADERS_TO_DROP = Set.of(
            "Accept-Ranges",
            "Content-Disposition",
            "Content-Encoding",
            "Content-Language",
            "Content-Location",
            "Content-MD5",
            "Content-Range",
            "ETag",
            "Last-Modified",
            "Transfer-Encoding"
    );

    /**
     * Maximum number of characters/bytes to buffer per response before switching to pass-through mode.
     * The buffered prefix is still inspected before passthrough so oversized error pages cannot
     * leak a stack trace before normal page content.
     */
    static final int MAX_CAPTURE_CHARS = 512 * 1024;

    /**
     * Default response buffer size (bytes) applied to web-service ({@code /ws/*}) requests so a
     * partial entity body written before a late 5xx stays uncommitted — and therefore replaceable —
     * up to this size. Web-service responses serialize via {@code getOutputStream()} while the status
     * is still 200 (the filter's pass-through path); the container's small default buffer (~8 KB on
     * Tomcat) otherwise commits the partial body before a mid-serialization failure flips the status
     * to 5xx, leaking PHI. Aligned with {@link #MAX_CAPTURE_CHARS}. Override with
     * {@link #WEB_SERVICE_BUFFER_PROPERTY}. See issue #2994.
     */
    static final int DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES = 512 * 1024;

    /**
     * Property name in {@code carlos.properties} that overrides the web-service response buffer size
     * (in bytes). Lets administrators trade memory (a buffer is held per concurrent {@code /ws}
     * request) against the size of partial body protected from the #2994 leak. A larger value
     * protects bigger partial payloads; a smaller value reduces heap pressure under high concurrency.
     * Absent, blank, or non-positive values fall back to {@link #DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES}.
     */
    static final String WEB_SERVICE_BUFFER_PROPERTY = "response.sanitization.ws.buffer.bytes";

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
    private int webServiceResponseBufferBytes = DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES;

    /**
     * Reads the {@code response.sanitization.enabled} property from {@code carlos.properties}.
     * Defaults to {@code true} (enabled) when the property is absent or blank.
     *
     * <p>When {@code response.sanitization.enabled=false} AND {@code DISPLAY_ERROR=true} are
     * both set, this filter is disabled and a security WARN is emitted — that combination is
     * the intended full developer error-display mode. {@code DISPLAY_ERROR=true} alone does
     * not bypass sanitization; {@code response.sanitization.enabled} is the gating control.</p>
     *
     * @param filterConfig FilterConfig the servlet container filter configuration
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String propValue = CarlosProperties.getInstance().getProperty(ENABLED_PROPERTY, "");
        enabled = parseEnabledProperty(propValue);
        webServiceResponseBufferBytes = parseBufferBytes(
                CarlosProperties.getInstance().getProperty(WEB_SERVICE_BUFFER_PROPERTY, ""));
        if (!enabled && CarlosProperties.getInstance().isPropertyActive(DISPLAY_ERROR_PROPERTY)) {
            // Sanitization is already disabled via response.sanitization.enabled=false.
            // When DISPLAY_ERROR is also active the developer has opted into full error
            // display — emit a prominent security warning so this state is never missed in logs.
            // (enabled is already false; this block only adds the WARN.)
            LOGGER.warn("DISPLAY_ERROR is active with sanitization disabled — "
                    + "stack traces WILL be sent to clients. "
                    + "This is a SECURITY RISK — do not enable in production environments "
                    + "or any system with real patient data.");
        }
        LOGGER.info("ResponseSanitizationFilter initialized: enabled={} webServiceResponseBufferBytes={}",
                enabled, webServiceResponseBufferBytes);
    }

    /**
     * Parses the {@link #WEB_SERVICE_BUFFER_PROPERTY} value into a positive byte count, falling back
     * to {@link #DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES} when the value is absent, blank,
     * non-numeric, or non-positive. Package-private for direct unit testing.
     *
     * @param propValue String the raw property value; may be {@code null}/blank
     * @return int the configured buffer size in bytes, or the default on any invalid input
     */
    static int parseBufferBytes(String propValue) {
        if (propValue == null || propValue.isBlank()) {
            return DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES;
        }
        try {
            int parsed = Integer.parseInt(propValue.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // fall through to the warning + default below
        }
        LOGGER.warn("Unrecognized {} value '{}'; using default {} bytes",
                WEB_SERVICE_BUFFER_PROPERTY, LogSafe.sanitize(propValue),
                DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES);
        return DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    static boolean parseEnabledProperty(String propValue) {
        if (propValue == null || propValue.isBlank()) {
            return true;
        }
        String normalized = propValue.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)
                || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)
                || "0".equals(normalized)) {
            return false;
        }
        LOGGER.warn("Unrecognized {} value '{}'; keeping response sanitization enabled",
                ENABLED_PROPERTY, LogSafe.sanitize(propValue));
        return true;
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
        boolean webServiceRequest = isWebServiceRequest((HttpServletRequest) request);
        if (webServiceRequest) {
            enlargeWebServiceResponseBuffer(httpResponse);
        }
        CapturingResponseWrapper wrapper = new CapturingResponseWrapper(httpResponse);

        try {
            chain.doFilter(request, wrapper);
        } catch (IOException | ServletException | RuntimeException e) {
            // An exception escaped the entire filter chain.
            // Always log for operational visibility and auditability, even when the response
            // is already committed, so failures are never silently swallowed.
            String correlationId = generateCorrelationId();
            String requestUri = LogSafe.sanitizeUri(((HttpServletRequest) request).getRequestURI());
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

        if (wrapper.isUsingOutputStream()) {
            if (wrapper.isOutputStreamPassthrough()) {
                int status = wrapper.getStatus();
                if (status >= 400) {
                    String correlationId = generateCorrelationId();
                    LOGGER.error("Late output-stream error response bypassed capture; "
                                    + "replacing buffered body [status={} uri={} correlationId={} committed={}]",
                            status,
                            LogSafe.sanitizeUri(((HttpServletRequest) request).getRequestURI()),
                            correlationId,
                            httpResponse.isCommitted());
                    if (!httpResponse.isCommitted()) {
                        sendSanitizedError(httpResponse, status, correlationId);
                    }
                }
                return;
            }
            if (wrapper.isOutputCaptureLimitExceeded()) {
                return;
            }
            int status = wrapper.getStatus();
            byte[] capturedBytes = wrapper.getCapturedOutput();
            String capturedBody = new String(capturedBytes, StandardCharsets.UTF_8);
            String reason = sanitizationReason(status, capturedBody, webServiceRequest);
            if (reason != null) {
                String correlationId = generateCorrelationId();
                LOGGER.error("Sanitizing output-stream error response body "
                                + "[status={} uri={} correlationId={} reason={}]",
                        status,
                        LogSafe.sanitizeUri(((HttpServletRequest) request).getRequestURI()),
                        correlationId,
                        reason);
                sendSanitizedError(httpResponse, status, correlationId);
            } else {
                writeBytesToResponse(httpResponse, capturedBytes);
            }
            return;
        }

        // getWriter() was never called — nothing to sanitize, nothing to flush.
        if (!wrapper.isUsingWriter()) {
            return;
        }

        // Response exceeded the capture limit after CapturingSwitchingWriter made a terminal
        // decision about the buffered prefix. Safe large responses have already been flushed
        // through; tainted error prefixes are converted to sanitized error pages before any raw
        // stack trace reaches the client.
        if (wrapper.isCaptureLimitExceeded()) {
            return;
        }

        int status = wrapper.getStatus();
        String capturedBody = wrapper.getCapturedContent();

        String reason = sanitizationReason(status, capturedBody, webServiceRequest);
        if (reason != null) {
            // Tainted (stack trace) or web-service 5xx partial body: log correlation details
            // only and send a sanitized replacement.
            String correlationId = generateCorrelationId();
            LOGGER.error("Sanitizing error response body "
                    + "[status={} uri={} correlationId={} reason={}]",
                    status,
                    LogSafe.sanitizeUri(((HttpServletRequest) request).getRequestURI()),
                    correlationId,
                    reason);
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

    /** Reason code logged when an error body is replaced because it carries a stack trace. */
    static final String REASON_STACK_TRACE = "stack-trace-marker";

    /** Reason code logged when a web-service 5xx partial entity body is replaced. */
    static final String REASON_WEB_SERVICE_5XX = "web-service-5xx-partial-body";

    /**
     * Determines whether a captured error-response body must be replaced with a generic page and,
     * if so, why. Returns a short, log-safe reason code, or {@code null} when the body may pass
     * through unchanged. The reason never includes any portion of the response body itself (which
     * can carry PHI even after the decision).
     *
     * <p>Two independent triggers, both scoped to error status codes ({@code >= 400}):
     * <ul>
     *   <li>{@link #REASON_STACK_TRACE} — the body contains Java stack trace markers (any error
     *       route); the original CWE-209 protection.</li>
     *   <li>{@link #REASON_WEB_SERVICE_5XX} — the request targets a web-service route
     *       ({@code /ws/*}) and the status is a server error ({@code >= 500}). A mid-stream
     *       Jackson/JAXB serialization failure commonly leaves a clean, well-formed JSON/XML prefix
     *       already written — patient demographics, names, phone numbers — that the stack-trace
     *       heuristic does not match. Web-service 5xx responses carry no client-meaningful entity
     *       body, so suppressing it removes the PHI exposure without breaking an API contract. See
     *       issue #2953.</li>
     * </ul>
     *
     * <p>Client errors ({@code 4xx}) on web-service routes are intentionally left to the
     * stack-trace check alone, because REST endpoints legitimately return structured JSON error
     * payloads (validation messages, {@code 401}/{@code 403}/{@code 404} envelopes) that callers
     * depend on.</p>
     *
     * @param status             int the response status code
     * @param body               String the captured response body; may be {@code null}/empty
     * @param webServiceRequest  boolean {@code true} if the request targeted a {@code /ws/*} route
     * @return a log-safe reason code, or {@code null} if the body must not be sanitized
     */
    static String sanitizationReason(int status, String body, boolean webServiceRequest) {
        if (status < 400) {
            return null;
        }
        if (body != null && containsStackTrace(body)) {
            return REASON_STACK_TRACE;
        }
        if (webServiceRequest && status >= 500) {
            return REASON_WEB_SERVICE_5XX;
        }
        return null;
    }

    /**
     * Convenience predicate over {@link #sanitizationReason(int, String, boolean)}.
     *
     * @param status             int the response status code
     * @param body               String the captured response body; may be {@code null}/empty
     * @param webServiceRequest  boolean {@code true} if the request targeted a {@code /ws/*} route
     * @return {@code true} if the body must be replaced with a sanitized error page
     */
    static boolean shouldSanitizeErrorBody(int status, String body, boolean webServiceRequest) {
        return sanitizationReason(status, body, webServiceRequest) != null;
    }

    /**
     * Determines whether the request targets a CXF web-service route. The CXF servlet is mapped at
     * {@code /ws/*} (see {@code web.xml}); {@link HttpServletRequest#getServletPath()} therefore
     * returns {@code /ws} for these requests. As a fallback for forwarded/wrapped requests where the
     * servlet path may not be populated, the <em>context-relative</em> request URI is matched with
     * an exact/prefix check ({@code /ws} or {@code /ws/...}). A substring match is deliberately
     * avoided so unrelated paths that merely contain {@code /ws/} (e.g. {@code /proxy/ws/foo}) are
     * not misclassified as web-service requests.
     *
     * @param request HttpServletRequest the incoming request
     * @return boolean {@code true} if the request path is under {@code /ws/}
     */
    static boolean isWebServiceRequest(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && (servletPath.equals("/ws") || servletPath.startsWith("/ws/"))) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
        return path.equals("/ws") || path.startsWith("/ws/");
    }

    /**
     * Enlarges the response buffer for web-service ({@code /ws/*}) requests so a partial entity body
     * written before a late 5xx stays uncommitted — and therefore replaceable by
     * {@link #sendSanitizedError} — up to the configured buffer size
     * ({@link #WEB_SERVICE_BUFFER_PROPERTY}, default
     * {@link #DEFAULT_WEB_SERVICE_RESPONSE_BUFFER_BYTES}).
     *
     * <p>Why this is needed: CXF/JAX-RS serializes responses via {@code getOutputStream()} while the
     * status is still 200, so the wrapper leaves the stream in pass-through mode. With the container's
     * small default buffer (~8 KB on Tomcat), a partial body larger than that is flushed and committed
     * before a mid-serialization failure sets a 5xx — at which point the body can no longer be
     * suppressed and PHI leaks (issue #2994). Keeping the response uncommitted within the buffer lets
     * the existing pass-through sanitization run.</p>
     *
     * <p>Scope/limits: only applied to {@code /ws/*}; the buffer is bounded. Handlers that explicitly
     * {@code flush()} (e.g. genuine streaming) still commit as before, so streamed responses are
     * unaffected. Partial bodies larger than the buffer still commit and remain a known residual.</p>
     *
     * @param response HttpServletResponse the real (unwrapped) response
     */
    private void enlargeWebServiceResponseBuffer(HttpServletResponse response) {
        try {
            if (response.getBufferSize() < webServiceResponseBufferBytes) {
                response.setBufferSize(webServiceResponseBufferBytes);
            }
        } catch (IllegalStateException e) {
            // Content already written / response committed — the buffer can no longer be resized.
            // The existing capture and pass-through logic still applies; nothing more to do here.
            LOGGER.debug("Cannot enlarge web-service response buffer (already committed or written)", e);
        }
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
     * <p>Uses {@link HttpServletResponse#resetBuffer()} deliberately so security/session headers
     * survive, then removes entity headers that describe the original representation. Without that
     * cleanup, a sanitized HTML page could inherit {@code Content-Disposition} or
     * {@code Content-Encoding} from a failed PDF/download response.</p>
     *
     * @param response      HttpServletResponse the real (unwrapped) response
     * @param status        int the HTTP error status code (4xx or 5xx)
     * @param correlationId String the correlation ID for log lookup
     * @throws IOException if an I/O error occurs writing the error page
     */
    private static void sendSanitizedError(HttpServletResponse response, int status,
            String correlationId) throws IOException {
        if (response.isCommitted()) {
            LOGGER.warn("Cannot send sanitized error — response already committed "
                    + "[status={} correlationId={}]", status, correlationId);
            throw new IOException("Cannot send sanitized error after response commit");
        }
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            LOGGER.error("Cannot reset buffer for sanitized error [correlationId={}]",
                    correlationId, e);
            throw new IOException("Cannot reset buffer for sanitized error", e);
        }
        dropEntityHeaders(response);
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        String body = buildSanitizedErrorPage(status, correlationId);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bodyBytes.length);
        response.getOutputStream().write(bodyBytes);
        response.getOutputStream().flush();
    }

    private static void dropEntityHeaders(HttpServletResponse response) {
        for (String header : ENTITY_HEADERS_TO_DROP) {
            // Tomcat 11 removes a response header when setHeader(name, null) is used. Servlet
            // does not expose a portable removeHeader API, and Tomcat 11 is the supported runtime
            // for this deployment.
            response.setHeader(header, null);
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
    // FindSecBugs XSS_SERVLET: replays captured response content after sanitization; content originates from downstream response pipeline.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "replays captured response content after sanitization; content originates from downstream response pipeline")
    private static void writeToResponse(HttpServletResponse response, String content)
            throws IOException {
        if (content == null || content.isEmpty()) {
            return;
        }
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            LOGGER.warn("writeToResponse: cannot resetBuffer before replaying captured response",
                    e);
            throw new IOException("Cannot reset buffer before replaying captured response", e);
        }
        String encoding = response.getCharacterEncoding();
        if (encoding == null || encoding.isEmpty()) {
            encoding = StandardCharsets.UTF_8.name();
        }
        byte[] bytes = content.getBytes(encoding);
        response.setContentLength(bytes.length);
        try {
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (IllegalStateException e) {
            // A downstream wrapper may have opened the real writer; keep the byte-accurate length
            // and fall back to the matching character path.
            PrintWriter out = response.getWriter();
            out.write(content);
            out.flush();
        }
    }

    private static void writeBytesToResponse(HttpServletResponse response, byte[] content)
            throws IOException {
        if (content == null || content.length == 0) {
            return;
        }
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            LOGGER.warn("writeBytesToResponse: cannot resetBuffer before replaying captured response",
                    e);
            throw new IOException("Cannot reset buffer before replaying captured response", e);
        }
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    /**
     * Response wrapper that captures {@link PrintWriter} output into a {@link CharArrayWriter}
     * while keeping successful {@link ServletOutputStream} responses on the real response.
     *
     * <p>This dual-mode design ensures:
     * <ul>
     *   <li>Successful binary and HTML output-stream responses are never buffered or replayed.</li>
     *   <li>Error responses written through a writer, and error output streams opened after a
     *       4xx/5xx status, are captured for inspection.</li>
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
        private CapturingSwitchingServletOutputStream switchingOutputStream;
        private PrintWriter writer;
        private boolean usingWriter;
        private boolean usingOutputStream;
        private boolean outputStreamPassthrough;
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
         * Returns a capturing output stream only after the response has entered an error status.
         * Successful output-stream responses stay pass-through so JSPs and streamed content cannot
         * be replayed after Tomcat has already committed their headers.
         *
         * @return ServletOutputStream the capturing or real response output stream
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
            if (outputStreamPassthrough || getStatus() < 400) {
                outputStreamPassthrough = true;
                return super.getOutputStream();
            }
            if (switchingOutputStream == null) {
                switchingOutputStream = new CapturingSwitchingServletOutputStream(
                        (HttpServletResponse) getResponse(), MAX_CAPTURE_CHARS);
            }
            return switchingOutputStream;
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
                if (outputStreamPassthrough) {
                    super.flushBuffer();
                } else if (switchingOutputStream != null) {
                    switchingOutputStream.flush();
                }
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
            if ((usingWriter || (usingOutputStream && !outputStreamPassthrough)) && !committed) {
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
         * @return boolean {@code true} if output-stream mode is active
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

        public boolean isOutputCaptureLimitExceeded() {
            return switchingOutputStream != null && switchingOutputStream.isLimitExceeded();
        }

        public boolean isOutputStreamPassthrough() {
            return outputStreamPassthrough;
        }

        public byte[] getCapturedOutput() throws IOException {
            if (switchingOutputStream != null) {
                switchingOutputStream.flush();
                return switchingOutputStream.getCaptured();
            }
            return new byte[0];
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
     * Captures output-stream bodies until the response proves it needs passthrough or sanitization.
     */
    private static class CapturingSwitchingServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final HttpServletResponse realResponse;
        private final int maxBytes;
        private ServletOutputStream passthroughStream;
        private boolean limitExceeded;
        private boolean discardingTaintedOutput;
        private long discardedByteCount;
        private boolean discardLogged;

        CapturingSwitchingServletOutputStream(HttpServletResponse realResponse, int maxBytes) {
            this.realResponse = realResponse;
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int b) throws IOException {
            if (!limitExceeded && buffer.size() + 1 > maxBytes) {
                switchToPassthrough();
            }
            if (limitExceeded) {
                if (discardingTaintedOutput) {
                    discardedByteCount++;
                } else {
                    passthroughStream.write(b);
                }
            } else {
                buffer.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (!limitExceeded && buffer.size() + len > maxBytes) {
                switchToPassthrough();
            }
            if (limitExceeded) {
                if (discardingTaintedOutput) {
                    discardedByteCount += len;
                } else {
                    passthroughStream.write(b, off, len);
                }
            } else {
                buffer.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            if (limitExceeded && !discardingTaintedOutput && passthroughStream != null) {
                passthroughStream.flush();
            }
            logDiscardedBytesIfNeeded();
        }

        @Override
        public boolean isReady() {
            return passthroughStream == null || passthroughStream.isReady();
        }

        @Override
        public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
            if (passthroughStream != null) {
                passthroughStream.setWriteListener(writeListener);
            }
        }

        boolean isLimitExceeded() {
            return limitExceeded;
        }

        byte[] getCaptured() {
            return limitExceeded ? new byte[0] : buffer.toByteArray();
        }

        private void switchToPassthrough() throws IOException {
            if (limitExceeded) {
                return;
            }
            int status = realResponse.getStatus();
            if (status >= 400) {
                String correlationId = generateCorrelationId();
                LOGGER.error("Large output-stream error response exceeded sanitization capture limit; "
                                + "replacing body to avoid leaking stack traces [status={} correlationId={}]",
                        status, correlationId);
                sendSanitizedError(realResponse, status, correlationId);
                buffer.reset();
                limitExceeded = true;
                discardingTaintedOutput = true;
                return;
            }
            limitExceeded = true;
            passthroughStream = realResponse.getOutputStream();
            byte[] captured = buffer.toByteArray();
            if (captured.length > 0) {
                passthroughStream.write(captured);
            }
            buffer.reset();
        }

        private void logDiscardedBytesIfNeeded() {
            if (discardingTaintedOutput && discardedByteCount > 0 && !discardLogged) {
                LOGGER.debug("ResponseSanitizationFilter: discarded {} bytes after sanitized "
                        + "large output-stream error response", discardedByteCount);
                discardLogged = true;
            }
        }
    }

    /**
     * A {@link Writer} that buffers writes into a {@link CharArrayWriter} up to
     * {@code maxChars} characters, then switches to direct pass-through via the real
     * response {@link PrintWriter} to prevent unbounded heap growth for large responses.
     *
     * <p>When the limit is exceeded, the buffered prefix and triggering write are inspected if
     * the response is an error. Tainted error output is replaced with a sanitized page before any
     * raw stack trace is flushed; otherwise the buffered content is flushed to the real writer and
     * subsequent writes go directly there. Callers can test {@link #isLimitExceeded()} to
     * determine whether no write-back is needed.</p>
     */
    private static class CapturingSwitchingWriter extends Writer {

        private final CharArrayWriter buffer;
        private final HttpServletResponse realResponse;
        private final int maxChars;
        private PrintWriter passthroughWriter;
        private boolean limitExceeded;
        private boolean discardingTaintedOutput;
        private long discardedByteCount;
        private boolean discardLogged;

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

        // FindSecBugs XSS_SERVLET: buffers or passes through downstream response body for sanitization size-limit handling.
        @SuppressFBWarnings(value = "XSS_SERVLET", justification = "buffers or passes through downstream response body for sanitization size-limit handling")
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (!limitExceeded && buffer.size() + len > maxChars) {
                switchToPassthrough(new String(cbuf, off, len));
            }
            if (limitExceeded) {
                recordDiscardedBytes(cbuf, off, len);
                passthroughWriter.write(cbuf, off, len);
            } else {
                buffer.write(cbuf, off, len);
            }
        }

        @Override
        public void write(int c) throws IOException {
            if (!limitExceeded && buffer.size() + 1 > maxChars) {
                switchToPassthrough(String.valueOf((char) c));
            }
            if (limitExceeded) {
                recordDiscardedBytes(c);
                passthroughWriter.write(c);
            } else {
                buffer.write(c);
            }
        }

        // FindSecBugs XSS_SERVLET: buffers or passes through downstream response body for sanitization size-limit handling.
        @SuppressFBWarnings(value = "XSS_SERVLET", justification = "buffers or passes through downstream response body for sanitization size-limit handling")
        @Override
        public void write(String str, int off, int len) throws IOException {
            if (!limitExceeded && buffer.size() + len > maxChars) {
                switchToPassthrough(str.substring(off, off + len));
            }
            if (limitExceeded) {
                recordDiscardedBytes(str, off, len);
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
            logDiscardedBytesIfNeeded();
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
         * @param pendingWrite String the write that triggered the limit check, used to catch
         *                     stack traces when the first write itself exceeds the limit
         * @throws IOException if the real response writer cannot be obtained or written to
         */
        // FindSecBugs XSS_SERVLET: switches captured downstream response body to passthrough after sanitization size limit is exceeded.
        @SuppressFBWarnings(value = "XSS_SERVLET", justification = "switches captured downstream response body to passthrough after sanitization size limit is exceeded")
        private void switchToPassthrough(String pendingWrite) throws IOException {
            if (limitExceeded) {
                return;
            }
            int status = realResponse.getStatus();
            String capturedPrefix = buffer.toString();
            if (status >= 400) {
                String correlationId = generateCorrelationId();
                LOGGER.error("Large error response exceeded sanitization capture limit; replacing body "
                                + "to avoid leaking late stack traces [status={} correlationId={}]",
                        status, correlationId);
                sendSanitizedError(realResponse, status, correlationId);
                buffer.reset();
                limitExceeded = true;
                discardingTaintedOutput = true;
                passthroughWriter = new PrintWriter(Writer.nullWriter());
                return;
            }
            limitExceeded = true;
            LOGGER.debug("ResponseSanitizationFilter: capture limit exceeded ({} chars)"
                    + " — switching to passthrough mode [status={}]", maxChars, status);
            passthroughWriter = realResponse.getWriter();
            char[] captured = capturedPrefix.toCharArray();
            if (captured.length > 0) {
                passthroughWriter.write(captured);
            }
            buffer.reset();
        }

        private void recordDiscardedBytes(char[] discardedContent, int off, int len) {
            if (discardingTaintedOutput && len > 0) {
                discardedByteCount += new String(discardedContent, off, len)
                        .getBytes(StandardCharsets.UTF_8).length;
            }
        }

        private void recordDiscardedBytes(int discardedContent) {
            if (discardingTaintedOutput) {
                discardedByteCount += String.valueOf((char) discardedContent)
                        .getBytes(StandardCharsets.UTF_8).length;
            }
        }

        private void recordDiscardedBytes(String discardedContent, int off, int len) {
            if (discardingTaintedOutput && discardedContent != null && len > 0) {
                discardedByteCount += discardedContent.substring(off, off + len)
                        .getBytes(StandardCharsets.UTF_8).length;
            }
        }

        private void logDiscardedBytesIfNeeded() {
            if (discardingTaintedOutput && discardedByteCount > 0 && !discardLogged) {
                LOGGER.debug("ResponseSanitizationFilter: discarded {} bytes after sanitized "
                        + "large-error response", discardedByteCount);
                discardLogged = true;
            }
        }
    }
}
