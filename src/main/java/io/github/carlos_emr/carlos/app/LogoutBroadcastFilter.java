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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.RequestNegotiation;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Servlet filter that injects session heartbeat and logout broadcast JavaScript
 * into authenticated HTML responses.
 *
 * <p>This filter provides two security mechanisms for multi-window session management:
 * <ol>
 *   <li><b>Logout broadcast:</b> When any window detects a logout (manual or timeout),
 *       it broadcasts via BroadcastChannel and localStorage to all other open windows,
 *       causing popups to close and tabs to redirect to the login page.</li>
 *   <li><b>Session heartbeat:</b> Each window polls {@code /status/SessionHeartbeat}
 *       every 60 seconds to detect server-side session loss (restart, timeout, invalidation).
 *       On detection, the window broadcasts logout to all others.</li>
 * </ol>
 *
 * <p>Injection conditions (all must be true after downstream rendering completes):
 * <ul>
 *   <li>Response Content-Type starts with {@code text/html}</li>
 *   <li>Not an AJAX request (no {@code X-Requested-With: XMLHttpRequest} header)</li>
 *   <li>User has a valid session ({@code session.getAttribute("user") != null}); login
 *       submissions may be wrapped before that attribute exists, but the script is appended only
 *       after the Struts action creates the authenticated session</li>
 *   <li>URL is not in the exclusion list (configurable via {@code exclusions} init-param)</li>
 *   <li>URL is not a known static asset prefix such as {@code /library/} or {@code /share/}</li>
 * </ul>
 *
 * <p>The filter wraps only requests that already have an authenticated session or are known to
 * establish one during the Struts action chain ({@code POST /login} and
 * {@code POST /forcepasswordresetSubmit}). Path matching is case-insensitive after request-path
 * normalization; the camel-case route name above documents the Struts action, while comparisons
 * use the normalized lower-case form. Wrapped responses configure the 1 MB injection buffer
 * lazily after downstream code marks the response as HTML.
 *
 * <p>The injected script reads the {@code INACTIVITY_LIMIT_MINS} property (default 60)
 * to set a client-side safety net timeout for prolonged network outages. The property
 * is read per-request to stay consistent with {@link io.github.carlos_emr.carlos.sec.LoginFilter}.
 *
 * <p>Uses the same {@code DelegatingServletResponse} wrapper pattern as
 * {@link io.github.carlos_emr.carlos.commn.printing.PrivacyStatementAppendingFilter}.
 *
 * <p>Configuration (web.xml):
 * <pre>
 * &lt;filter&gt;
 *     &lt;filter-name&gt;LogoutBroadcastFilter&lt;/filter-name&gt;
 *     &lt;filter-class&gt;io.github.carlos_emr.carlos.app.LogoutBroadcastFilter&lt;/filter-class&gt;
 *     &lt;init-param&gt;
 *         &lt;param-name&gt;exclusions&lt;/param-name&gt;
 *         &lt;param-value&gt;/logoutPage,/status/SessionHeartbeat&lt;/param-value&gt;
 *     &lt;/init-param&gt;
 * &lt;/filter&gt;
 * </pre>
 *
 * @see io.github.carlos_emr.carlos.commn.printing.PrivacyStatementAppendingFilter
 * @since 2026-02-24
 */
public class LogoutBroadcastFilter implements Filter {

    private static final Logger logger = MiscUtils.getLogger();

    private static final int HTML_INJECTION_BUFFER_SIZE_BYTES = 1024 * 1024;

    /** Default inactivity limit in minutes when INACTIVITY_LIMIT_MINS is not configured. */
    private static final int DEFAULT_INACTIVITY_LIMIT_MINS = 60;

    /** Request marker used to avoid duplicate server-side injection across REQUEST/FORWARD dispatches. */
    private static final String SCRIPT_INJECTED_REQUEST_ATTRIBUTE =
            LogoutBroadcastFilter.class.getName() + ".scriptInjected";

    private Set<String> exclusions = Collections.synchronizedSet(new HashSet<String>());

    /**
     * Initializes the filter, reading exclusion list from init-param.
     *
     * @param filterConfig FilterConfig servlet filter configuration
     * @throws ServletException if filter initialization fails
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Starting Filter : " + getClass().getSimpleName());

        String exclusionsParam = filterConfig.getInitParameter("exclusions");
        if (exclusionsParam != null && !exclusionsParam.trim().isEmpty()) {
            for (String ex : exclusionsParam.split(",")) {
                exclusions.add(ex.toLowerCase().trim());
            }
        }
    }

    /**
     * Returns the current inactivity limit in minutes, reading from CarlosProperties
     * on each call to stay consistent with LoginFilter's per-request reading.
     *
     * @return int the inactivity limit in minutes
     */
    private int getInactivityLimitMins() {
        String limitProp = CarlosProperties.getInstance().getProperty("INACTIVITY_LIMIT_MINS");
        if (limitProp != null && !limitProp.trim().isEmpty()) {
            try {
                return Integer.parseInt(limitProp.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid INACTIVITY_LIMIT_MINS value '{}', using default {}",
                        limitProp, DEFAULT_INACTIVITY_LIMIT_MINS, e);
            }
        }
        return DEFAULT_INACTIVITY_LIMIT_MINS;
    }

    /**
     * Filters HTML responses to append the logout broadcast and session heartbeat script.
     *
     * <p>Excluded URLs, static assets, AJAX requests, and anonymous public pages are
     * short-circuited before response wrapping. The 1 MB injection buffer is configured lazily
     * only for eligible HTML responses, so anonymous login pages and static assets do not pay
     * the buffer cost.
     *
     * <p>The script is appended only to authenticated, non-AJAX, HTML responses
     * that are not in the exclusion list.
     *
     * @param request ServletRequest the HTTP request
     * @param response ServletResponse the HTTP response
     * @param chain FilterChain the filter chain
     * @throws IOException if I/O error occurs
     * @throws ServletException if servlet-level error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (!isResponseWrappingCandidate(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String safeRequestUri = sanitizedRequestUri(httpRequest);
        DelegatingServletResponse delegatingResponse = new DelegatingServletResponse(
                (HttpServletResponse) response, safeRequestUri);
        chain.doFilter(request, delegatingResponse);
        delegatingResponse.markChainComplete();

        if (Boolean.TRUE.equals(httpRequest.getAttribute(SCRIPT_INJECTED_REQUEST_ATTRIBUTE))) {
            delegatingResponse.discardDeferredContentLength();
            delegatingResponse.completeWithoutInjection();
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            delegatingResponse.completeWithoutInjection();
            return;
        }

        // Only inject for HTML responses
        String contentType = delegatingResponse.getContentType();
        if (!RequestNegotiation.isHtmlContentType(contentType)) {
            delegatingResponse.completeWithoutInjection();
            return;
        }
        if (delegatingResponse.isHtmlInjectionBufferUnavailable()) {
            logger.warn("Skipping logout broadcast script injection because the HTML response buffer "
                    + "could not be enlarged before body content was written: uri={}",
                    safeRequestUri);
            delegatingResponse.completeWithoutInjection();
            return;
        }

        if (delegatingResponse.isResponseOutputStreamObtained()) {
            logger.warn("Skipping logout broadcast script injection because response used "
                    + "ServletOutputStream: uri={}, contentType={}",
                    safeRequestUri, LogSafe.sanitize(contentType));
            delegatingResponse.completeWithoutInjection();
            return;
        }

        try {
            delegatingResponse.discardDeferredContentLength();
            appendScript(delegatingResponse, httpRequest.getContextPath(), httpRequest.getLocale());
            httpRequest.setAttribute(SCRIPT_INJECTED_REQUEST_ATTRIBUTE, Boolean.TRUE);
        } catch (IOException e) {
            logger.error("Skipping logout broadcast script injection because the script could not be written: uri={}",
                    safeRequestUri, e);
            delegatingResponse.discardDeferredContentLength();
            delegatingResponse.flushDelegatingWriter();
            return;
        } catch (IllegalStateException e) {
            logger.error("Skipping logout broadcast script injection because the response writer was unavailable and the output stream write failed: uri={}",
                    safeRequestUri, e);
            delegatingResponse.discardDeferredContentLength();
            return;
        }
    }

    /**
     * Returns a log-safe request URI for script-injection failure diagnostics.
     *
     * @param request current HTTP request
     * @return sanitized URI suitable for operator logs
     */
    private String sanitizedRequestUri(HttpServletRequest request) {
        return LogSafe.sanitizeUri(request.getRequestURI());
    }

    /**
     * Determines whether a request should receive the response wrapper at all.
     *
     * <p>This is the performance-sensitive gate. Anonymous public pages and static/AJAX requests
     * bypass wrapping completely, while already-authenticated requests and the two login submissions
     * that can establish a session are wrapped so the post-chain append decision has enough body
     * control.</p>
     *
     * @param request current HTTP request
     * @return true when the response should be wrapped for possible script injection
     */
    private boolean isResponseWrappingCandidate(HttpServletRequest request) {
        if (isExcluded(request) || isStaticAssetPath(request) || RequestNegotiation.isAjax(request)) {
            return false;
        }

        HttpSession session = request.getSession(false);
        return (session != null && session.getAttribute("user") != null)
                || isSessionEstablishingPath(request);
    }

    /**
     * Identifies known public static asset trees that must never pay the wrapper cost.
     *
     * @param request current HTTP request
     * @return true for static asset paths such as {@code /library/...} and {@code /share/...}
     */
    private boolean isStaticAssetPath(HttpServletRequest request) {
        String servletPath = getNormalizedRequestPath(request);
        if (servletPath == null) {
            return false;
        }
        return servletPath.startsWith("/library/") || servletPath.startsWith("/share/");
    }

    /**
     * Identifies login submissions that may create an authenticated session during the chain.
     *
     * <p>These paths start anonymous, but they need wrapping before Struts runs so the first
     * post-login HTML response can receive the logout heartbeat script. The post-chain session
     * check in {@link #doFilter(ServletRequest, ServletResponse, FilterChain)} still prevents
     * injection when authentication fails.</p>
     *
     * @param request current HTTP request
     * @return true for session-establishing POST routes
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private boolean isSessionEstablishingPath(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String servletPath = getNormalizedRequestPath(request);
        if (servletPath == null) {
            return false;
        }
        return "/login".equals(servletPath) || "/forcepasswordresetsubmit".equals(servletPath);
    }

    /**
     * Checks if the request URL is in the exclusion list.
     *
     * @param request HttpServletRequest the request to check
     * @return boolean true if the URL should be excluded from script injection
     */
    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = getNormalizedRequestPath(request);
        if (servletPath == null) {
            return false;
        }

        for (String ex : exclusions) {
            if (matchesExcludedPath(servletPath, ex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes a servlet path for case-insensitive path matching.
     *
     * @param servletPath raw servlet path or context-relative URI
     * @return lower-case trimmed path string
     */
    private String normalizeServletPath(String servletPath) {
        return servletPath.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Returns a context-relative request path for filter matching.
     *
     * <p>During a {@code RequestDispatcher.forward()} — e.g. a Struts action forwarding its route to
     * an internal {@code /WEB-INF/jsp} view — {@code getServletPath()}/{@code getRequestURI()} reflect
     * the forward <em>target</em> (the JSP), not the route the client requested. The exclusion list is
     * configured with client-facing routes, and the heartbeat script is appended on the FORWARD
     * dispatch, so matching against the forward target would silently miss excluded routes on the very
     * dispatch where injection happens. When the original request URI is available via the standard
     * {@link RequestDispatcher#FORWARD_REQUEST_URI} attribute, match against it instead (issue #3099).</p>
     *
     * <p>Some servlet containers or tests leave {@code servletPath} empty for extensionless routes,
     * so this method falls back to {@code requestURI} and removes the context path before
     * normalizing.</p>
     *
     * @param request current HTTP request
     * @return normalized path beginning with {@code /}, or null when no path is available
     */
    private String getNormalizedRequestPath(HttpServletRequest request) {
        String path = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (path != null && !path.trim().isEmpty()) {
            path = stripContextPath(path, request.getContextPath());
        } else {
            path = request.getServletPath();
            if (path == null || path.trim().isEmpty()) {
                path = stripContextPath(request.getRequestURI(), request.getContextPath());
            }
        }

        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        path = normalizeServletPath(path);
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Removes the leading context path from an absolute request URI so it can be matched against the
     * context-relative routes configured in the exclusion list.
     *
     * @param uri absolute request URI (may be null)
     * @param contextPath servlet context path (may be null or empty for the root context)
     * @return the context-relative path, or the original URI when no context path prefix applies
     */
    private String stripContextPath(String uri, String contextPath) {
        if (uri != null && contextPath != null && !contextPath.isEmpty()
                && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * Matches either an exact excluded path or a child path under an excluded prefix.
     *
     * @param servletPath normalized request path
     * @param exclusion normalized configured exclusion
     * @return true when the request should bypass logout script injection
     */
    private boolean matchesExcludedPath(String servletPath, String exclusion) {
        if (servletPath.equals(exclusion)) {
            return true;
        }
        if (exclusion.endsWith("/")) {
            return servletPath.startsWith(exclusion);
        }
        return servletPath.startsWith(exclusion + "/");
    }

    /**
     * Appends the inline logout broadcast and session heartbeat script through the wrapped response.
     *
     * @param delegatingResponse DelegatingServletResponse the wrapped response
     * @param contextPath String the servlet context path
     * @param locale Locale the user's locale for i18n message lookup
     * @throws IOException if I/O error occurs writing the script
     */
    private void appendScript(DelegatingServletResponse delegatingResponse, String contextPath, Locale locale)
            throws IOException {

        String script = buildScript(contextPath, locale);

        if (delegatingResponse.isResponseWriterObtained()) {
            writeScriptToWriter(delegatingResponse, script);
        } else {
            writeScriptWithBestAvailableOutput(delegatingResponse, script);
        }
    }

    /**
     * Writes the injected script through the servlet output stream path.
     *
     * @param delegatingResponse DelegatingServletResponse the wrapped response
     * @param script String the script content to append
     * @throws IOException if the output stream write fails
     */
    private void writeScriptToOutputStream(DelegatingServletResponse delegatingResponse, String script)
            throws IOException {
        delegatingResponse.getOutputStream().write(script.getBytes(StandardCharsets.UTF_8));
        delegatingResponse.flushBuffer();
    }

    /**
     * Writes the injected script through the servlet writer path.
     *
     * @param delegatingResponse DelegatingServletResponse the wrapped response
     * @param script String the script content to append
     * @throws IOException if the writer flush fails
     */
    // FindSecBugs XSS_SERVLET: intentionally injects logout-broadcast script; buildScript encodes dynamic values for JavaScript.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "intentionally injects logout-broadcast script; buildScript encodes dynamic values for JavaScript")
    private void writeScriptToWriter(DelegatingServletResponse delegatingResponse, String script)
            throws IOException {
        PrintWriter writer = delegatingResponse.getWriter();
        writer.print(script);
        writer.flush();
        delegatingResponse.flushBuffer();
    }

    /**
     * Writes the injected script using the best available output mechanism.
     *
     * <p>This method prefers the writer path for standard HTML rendering and falls back to the
     * output stream only when the writer cannot be obtained during injection. Responses that used
     * the output stream during normal rendering are skipped before this method is called.
     *
     * @param delegatingResponse DelegatingServletResponse the wrapped response
     * @param script String the script content to append
     * @throws IOException if writing fails for the selected output path
     */
    private void writeScriptWithBestAvailableOutput(DelegatingServletResponse delegatingResponse, String script)
            throws IOException {
        try {
            writeScriptToWriter(delegatingResponse, script);
        } catch (IllegalStateException e) {
            logger.warn("Response writer unavailable during logout script injection; retrying with output stream.", e);
            writeScriptToOutputStream(delegatingResponse, script);
        }
    }

    /**
     * Returns the localized "logged out" message for the logout overlay, falling back
     * to English if the key is missing or the locale is unsupported.
     *
     * @param locale Locale the user's locale
     * @return String the localized message
     */
    private String getLoggedOutMessage(Locale locale) {
        try {
            return ResourceBundle.getBundle("oscarResources", locale).getString("logoutBroadcast.loggedOut");
        } catch (MissingResourceException e) {
            logger.warn("Missing localized logout broadcast message for locale={}; using built-in fallback",
                    locale, e);
            return "Logged out";
        }
    }

    /**
     * Builds the inline JavaScript for logout broadcast and session heartbeat.
     *
     * <p>The script uses an IIFE to avoid polluting the global scope. It sets a
     * {@code window.__carlosLogoutActive} guard to prevent duplicate injection
     * (e.g., from nested frames or multiple filter passes).
     *
     * <p>The context path and logout message are encoded with
     * {@link SafeEncode#forJavaScript(String)} to prevent XSS in JavaScript string literals.
     *
     * @param contextPath String the servlet context path for URL construction
     * @param locale Locale the user's locale for the logout overlay message
     * @return String the complete {@code <script>} block to inject
     */
    private String buildScript(String contextPath, Locale locale) {
        int inactivityLimitMins = getInactivityLimitMins();

        return "<script>" +
                "(function(){" +
                "if(window.__carlosLogoutActive)return;" +
                "window.__carlosLogoutActive=true;" +

                "var cp='" + SafeEncode.forJavaScript(contextPath) + "';" +
                "var ilMs=" + inactivityLimitMins + "*60000;" +
                "var lastOk=Date.now();" +
                "var loginUrl=cp+'/index';" +
                "var done=false;" +
                "var logoutMsg='" + SafeEncode.forJavaScript(getLoggedOutMessage(locale)) + "';" +
                // BroadcastChannel listener (feature detection — may not exist in all browsers)
                "var bc;" +
                "try{bc=new BroadcastChannel('carlos_logout')}catch(e){}" +
                "if(bc){bc.onmessage=function(e){if(e.data==='logout')hL()}}" +

                // localStorage fallback listener
                "window.addEventListener('storage',function(e){" +
                "if(e.key==='carlos_logout_signal')hL()" +
                "});" +

                // Session heartbeat — poll every 60s
                "setInterval(function(){" +
                "if(done)return;" +
                "if(Date.now()-lastOk>ilMs){bL();return}" +
                "fetch(cp+'/status/SessionHeartbeat?autoRefresh=true')" +
                ".then(function(r){" +
                "if(r.ok)return r.json();" +
                "if(r.status===401||r.status===403){bL();return null}" +
                "return null" +
                "})" +
                ".then(function(d){" +
                "if(!d)return;" +
                "if(d.valid===true)lastOk=Date.now();" +
                "if(d.valid===false)bL()" +
                "})" +
                ".catch(function(err){" +
                "if(typeof console!=='undefined'&&console.warn)" +
                "console.warn('CARLOS session heartbeat error:',err)" +
                "})" +
                "},60000);" +

                // broadcastLogout — this window detected session loss, notify others
                "function bL(){" +
                "if(done)return;done=true;" +
                "if(bc){try{bc.postMessage('logout')}catch(e){" +
                "if(typeof console!=='undefined')console.warn('CARLOS logout broadcast failed:',e)}}" +
                "try{localStorage.setItem('carlos_logout_signal',''+Date.now())}catch(e){" +
                "if(typeof console!=='undefined')console.warn('CARLOS logout localStorage failed:',e)}" +
                "try{localStorage.removeItem('carlos_logout_signal')}catch(e){}" +
                "dL()}" +

                // handleLogout — received broadcast from another window
                "function hL(){if(done)return;done=true;dL()}" +

                // doLogout — show logged-out overlay, close popup or redirect tab to login
                "function dL(){" +
                "try{var ov=document.createElement('div');" +
                "ov.style.cssText='position:fixed;top:0;left:0;right:0;bottom:0;background:#fff;" +
                "z-index:999999;display:flex;align-items:center;justify-content:center;" +
                "font-family:sans-serif;font-size:1.5em;color:#333;';" +
                "ov.textContent=logoutMsg;" +
                "document.body.appendChild(ov);}catch(e){}" +
                "try{window.close()}catch(e){}" +
                "setTimeout(function(){window.location.href=loginUrl},500)" +
                "}" +

                "})();" +
                "</script>";
    }

    /**
     * Called by the web container when the filter is taken out of service.
     *
     * <p>No cleanup is required: this filter holds no threads, connections,
     * or other external resources that need explicit release.
     *
     * @since 2026-02-24
     */
    @Override
    public void destroy() {
    }

    /**
     * Writer that keeps the response open for post-chain script injection.
     */
    private static class DelegatingWriter extends PrintWriter {

        private final Writer out;

        /**
         * Creates a DelegatingWriter wrapping the given underlying writer.
         *
         * @param out Writer the underlying response writer
         */
        public DelegatingWriter(Writer out) {
            super(out);
            this.out = out;
        }

        /**
         * Lets downstream writer flushes reach the outer response wrapper.
         *
         * <p>Struts/Tomcat forwards call {@code resetBuffer()} before dispatching to the result JSP.
         * This filter suppresses {@link DelegatingServletResponse#flushBuffer()} before chain
         * completion, but writer flushes themselves must pass through so Jasper can drain its final
         * JSP buffer into the outer sanitization wrapper instead of truncating the page tail.</p>
         */
        @Override
        public void flush() {
            super.flush();
        }

        /**
         * Flushes the underlying writer once the outer filter has made its append decision.
         */
        void flushToUnderlying() throws IOException {
            super.flush();
            out.flush();
        }

        /**
         * Suppresses close to prevent the underlying writer from being closed before
         * the filter can append the logout broadcast script.
         *
         * <p>The JSP container calls {@code close()} at the end of JSP execution.
         * Without this guard, the underlying writer would be closed before
         * {@link LogoutBroadcastFilter#appendScript} could write to it.
         */
        @Override
        public void close() {
            super.flush();
        }

    }

    /**
     * Response wrapper that keeps enough HTML response body buffered for the filter to append
     * content after the filter chain has written its output.
     *
     * <p>Tracks whether {@code getWriter()} or {@code getOutputStream()} was called
     * so the filter knows which output method to use for appending. Content-Length is deferred
     * while wrapped because an injected script changes the final byte count; if no script is
     * appended, {@link #applyDeferredContentLength()} restores the downstream value.
     */
    private static class DelegatingServletResponse extends HttpServletResponseWrapper {

        private boolean responseWriterObtained;
        private boolean responseOutputStreamObtained;
        private boolean contentLengthDeferred;
        private int deferredContentLength;
        private long deferredContentLengthLong;
        private boolean deferredContentLengthIsLong;
        private boolean htmlInjectionBufferConfigured;
        private boolean htmlInjectionBufferUnavailable;
        private boolean chainComplete;
        private DelegatingWriter writer;
        private final String requestUriForLog;

        /**
         * Creates a DelegatingServletResponse wrapping the given HTTP response.
         *
         * <p>The 1 MB server-side buffer is configured lazily once downstream code marks the
         * response as HTML. This avoids allocating the large buffer for redirects, JSON,
         * binary streams, and other wrapped-but-not-injected responses.
         *
         * @param response HttpServletResponse the HTTP response to wrap
         * @param requestUriForLog sanitized request URI captured at filter entry for buffer
         *                         diagnostics; do not call {@code request.getRequestURI()} from
         *                         later log sites because forwards can mutate request attributes
         */
        public DelegatingServletResponse(HttpServletResponse response, String requestUriForLog) {
            super(response);
            this.requestUriForLog = requestUriForLog;
        }

        /**
         * Returns the raw servlet output stream and marks the stream path as obtained.
         *
         * <p>Unlike {@link #getWriter()}, the returned stream is <em>not</em> wrapped
         * with a delegating proxy. Content-Length is replayed before the stream is handed out
         * because stream responses can commit before the filter chain returns. Script injection is
         * skipped for stream responses; only writer-backed HTML is safe to append after rendering.
         * This path intentionally does not configure the HTML append buffer because the outer
         * filter excludes stream-backed responses from injection.
         *
         * @return ServletOutputStream the raw servlet output stream
         * @throws IOException if the output stream cannot be obtained from the underlying response
         */
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            responseOutputStreamObtained = true;
            applyDeferredContentLength();
            return super.getOutputStream();
        }

        /**
         * Returns a {@link DelegatingWriter} wrapping the underlying response writer.
         *
         * <p>The delegating writer suppresses {@code close()} while allowing {@code flush()} calls
         * to reach the outer capture wrapper. The response buffer is reserved before returning the
         * writer because several legacy JSP paths obtain the writer before declaring
         * {@code text/html}; waiting for the content type lets large pages commit before this
         * filter can append safely.
         *
         * @return PrintWriter a delegating writer that suppresses premature close
         * @throws IOException if the underlying writer cannot be obtained
         */
        @Override
        public PrintWriter getWriter() throws IOException {
            responseWriterObtained = true;
            configureWriterInjectionBufferIfNeeded();
            if (writer == null) {
                writer = new DelegatingWriter(super.getWriter());
            }
            return writer;
        }

        /**
         * Records the response content type and configures the HTML append buffer when appropriate.
         *
         * <p>Header callers are routed here too, so {@code Content-Type: text/html} has the same
         * lazy buffer side effect whether downstream code uses {@link #setContentType(String)},
         * {@link #setHeader(String, String)}, or {@link #addHeader(String, String)}.</p>
         *
         * @param type response Content-Type value supplied by downstream code
         */
        @Override
        public void setContentType(String type) {
            super.setContentType(type);
            if (RequestNegotiation.isHtmlContentType(type)) {
                configureWriterInjectionBufferIfNeeded();
            }
        }

        /**
         * Returns whether downstream code obtained the response writer during chain execution.
         *
         * <p>Used by {@link LogoutBroadcastFilter#appendScript} to determine which output
         * channel to use when writing the injected script.
         *
         * @return boolean true if {@link #getWriter()} was called
         */
        public boolean isResponseWriterObtained() {
            return responseWriterObtained;
        }

        /**
         * Returns whether downstream code obtained the servlet output stream during chain execution.
         *
         * <p>The outer filter uses this as a passthrough signal. Stream-backed responses can commit
         * before chain return, so they keep their original body and never receive appended script.
         *
         * @return boolean true if {@link #getOutputStream()} was called
         */
        public boolean isResponseOutputStreamObtained() {
            return responseOutputStreamObtained;
        }

        /**
         * Defers writer-backed response commits until after the filter chain returns.
         *
         * <p>Tomcat 11 forwards need to reset the response buffer before dispatching to a JSP result.
         * A downstream {@code flushBuffer()} during action/interceptor rendering must therefore not
         * commit writer-backed responses before Struts reaches the result forward. Stream-backed
         * responses still pass through immediately because binary/direct responses are never
         * candidates for appended logout script injection.</p>
         *
         * @throws IOException if the wrapped response cannot flush
         */
        @Override
        public void flushBuffer() throws IOException {
            if (!chainComplete && !responseOutputStreamObtained) {
                flushDelegatingWriter();
                return;
            }
            flushDelegatingWriter();
            super.flushBuffer();
        }

        /**
         * Resets the response and clears any deferred headers owned by this wrapper.
         *
         * <p>Reset means downstream code has intentionally abandoned the previous body and headers,
         * commonly to send an error response. Keeping an earlier Content-Length after that point
         * would risk stale-length headers on the replacement response.</p>
         */
        @Override
        public void reset() {
            clearDeferredContentLength();
            htmlInjectionBufferConfigured = false;
            htmlInjectionBufferUnavailable = false;
            super.reset();
        }

        /**
         * Resets only the body buffer and clears deferred Content-Length state.
         *
         * <p>The servlet response keeps its headers across {@code resetBuffer()}, but the body length
         * no longer matches any prior downstream Content-Length. Clearing the deferred value avoids
         * replaying an obsolete length after an error path swaps the response body. The HTML buffer
         * configured flag is intentionally left intact because servlet containers keep the buffer
         * size across {@code resetBuffer()} just as they keep headers.</p>
         */
        @Override
        public void resetBuffer() {
            clearDeferredContentLength();
            htmlInjectionBufferUnavailable = false;
            super.resetBuffer();
        }

        @Override
        public void setContentLength(int len) {
            contentLengthDeferred = true;
            deferredContentLength = len;
            deferredContentLengthIsLong = false;
        }

        @Override
        public void setContentLengthLong(long len) {
            contentLengthDeferred = true;
            deferredContentLengthLong = len;
            deferredContentLengthIsLong = true;
        }

        @Override
        public void setHeader(String name, String value) {
            if (isContentTypeHeader(name)) {
                setContentType(value);
                return;
            }
            if (isContentLengthHeader(name)) {
                deferContentLengthHeader(value);
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (isContentTypeHeader(name)) {
                setContentType(value);
                return;
            }
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

        /**
         * Replays the deferred downstream Content-Length when the filter decides not to append.
         *
         * <p>If script injection occurs, the original length is intentionally discarded because the
         * body is longer. If downstream code calls {@link #reset()} or {@link #resetBuffer()}, the
         * deferred value is cleared so reset error responses cannot inherit stale lengths. A
         * successful replay also clears the deferred state so later stream access cannot apply the
         * same length twice.</p>
         */
        void applyDeferredContentLength() {
            if (!contentLengthDeferred || isCommitted()) {
                return;
            }
            if (deferredContentLengthIsLong) {
                logger.debug("Replaying deferred Content-Length for non-injected response: uri={}, length={}",
                        requestUriForLog, deferredContentLengthLong);
                super.setContentLengthLong(deferredContentLengthLong);
            } else {
                logger.debug("Replaying deferred Content-Length for non-injected response: uri={}, length={}",
                        requestUriForLog, deferredContentLength);
                super.setContentLength(deferredContentLength);
            }
            clearDeferredContentLength();
        }

        /**
         * Finishes a wrapped response when the filter decides not to append a script.
         *
         * <p>{@link DelegatingWriter#close()} is intentionally suppressed so JSP rendering can
         * return to this filter for possible script injection. Non-HTML writer responses still
         * need an explicit flush before the filter exits; otherwise JavaScript and JSON responses
         * can remain partially buffered and reach the browser truncated.</p>
         */
        void completeWithoutInjection() throws IOException {
            applyDeferredContentLength();
            flushDelegatingWriter();
        }

        /**
         * Drops any downstream Content-Length once script injection touches the response.
         *
         * <p>At that point the body length no longer matches the original downstream length, so
         * replaying it can truncate the client response on success or failure.</p>
         */
        void discardDeferredContentLength() {
            clearDeferredContentLength();
        }

        void flushDelegatingWriter() throws IOException {
            if (writer != null) {
                writer.flushToUnderlying();
            }
        }

        void markChainComplete() {
            chainComplete = true;
        }

        /**
         * Returns whether the wrapper could not enlarge the HTML response buffer in time.
         *
         * <p>This is a per-response safety signal. If downstream code obtains the writer and writes
         * body bytes before declaring {@code text/html}, servlet containers may reject
         * {@link #setBufferSize(int)}. The filter then skips injection instead of aborting the
         * request.</p>
         *
         * @return true when script injection should be skipped for this response
         */
        boolean isHtmlInjectionBufferUnavailable() {
            return htmlInjectionBufferUnavailable;
        }

        /**
         * Caches string Content-Length header values until the final append decision is known.
         *
         * @param value raw Content-Length header value from downstream code
         */
        private void deferContentLengthHeader(String value) {
            try {
                setContentLengthLong(Long.parseLong(value));
            } catch (NumberFormatException e) {
                logger.warn("Ignoring malformed Content-Length header value from downstream response: {}",
                        LogSafe.sanitize(value), e);
            }
        }

        /**
         * Clears all deferred Content-Length state after reset or replacement response paths.
         */
        private void clearDeferredContentLength() {
            contentLengthDeferred = false;
            deferredContentLength = 0;
            deferredContentLengthLong = 0L;
            deferredContentLengthIsLong = false;
        }

        // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
        @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
        private boolean isContentLengthHeader(String name) {
            return "Content-Length".equalsIgnoreCase(name);
        }

        // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
        @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
        private boolean isContentTypeHeader(String name) {
            return "Content-Type".equalsIgnoreCase(name);
        }

        /**
         * Enables the large servlet buffer before writer-backed body bytes can commit.
         *
         * <p>The wrapper itself is already limited to authenticated/session-establishing,
         * non-AJAX, non-static requests. Within that narrowed set, reserving the buffer at writer
         * acquisition is safer than waiting for {@code Content-Type}: Tomcat/JSP code can obtain the
         * writer and write a large HTML page before the content type reaches this wrapper.</p>
         */
        private void configureWriterInjectionBufferIfNeeded() {
            if (htmlInjectionBufferConfigured || htmlInjectionBufferUnavailable || isCommitted()) {
                return;
            }

            String contentType = getContentType();
            if (contentType != null && !RequestNegotiation.isHtmlContentType(contentType)) {
                return;
            }

            try {
                setBufferSize(HTML_INJECTION_BUFFER_SIZE_BYTES);
                htmlInjectionBufferConfigured = true;
            } catch (IllegalStateException e) {
                logger.debug("setBufferSize rejected for logout broadcast writer injection: uri={}, committed={}, contentType={}",
                        requestUriForLog, isCommitted(), LogSafe.sanitize(contentType), e);
                htmlInjectionBufferUnavailable = true;
            }
        }
    }
}
