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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import org.owasp.encoder.Encode;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Servlet filter that injects session heartbeat and logout broadcast JavaScript
 * into authenticated HTML responses.
 *
 * <p>This filter provides two security mechanisms for multi-window session management:
 * <ol>
 *   <li><b>Logout broadcast:</b> When any window detects a logout (manual or timeout),
 *       it broadcasts via BroadcastChannel and localStorage to all other open windows,
 *       causing popups to close and tabs to redirect to the login page.</li>
 *   <li><b>Session heartbeat:</b> Each window polls {@code /status/sessionHeartbeat.jsp}
 *       every 60 seconds to detect server-side session loss (restart, timeout, invalidation).
 *       On detection, the window broadcasts logout to all others.</li>
 * </ol>
 *
 * <p>Injection conditions (all must be true):
 * <ul>
 *   <li>Response Content-Type starts with {@code text/html}</li>
 *   <li>Not an AJAX request (no {@code X-Requested-With: XMLHttpRequest} header)</li>
 *   <li>User has a valid session ({@code session.getAttribute("user") != null})</li>
 *   <li>URL is not in the exclusion list (configurable via {@code exclusions} init-param)</li>
 * </ul>
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
 *         &lt;param-value&gt;/logout.jsp,/status/sessionHeartbeat.jsp&lt;/param-value&gt;
 *     &lt;/init-param&gt;
 * &lt;/filter&gt;
 * </pre>
 *
 * @see io.github.carlos_emr.carlos.commn.printing.PrivacyStatementAppendingFilter
 * @since 2026-02-24
 */
public class LogoutBroadcastFilter implements Filter {

    private static final Logger logger = MiscUtils.getLogger();

    private static final String HTTP_HEADER_VALUE_AJAX_REQUESTED_WITH = "XMLHttpRequest";
    private static final String HTTP_HEADER_NAME_AJAX_REQUESTED_WITH = "X-Requested-With";

    /** Default inactivity limit in minutes when INACTIVITY_LIMIT_MINS is not configured. */
    private static final int DEFAULT_INACTIVITY_LIMIT_MINS = 60;

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
     * Returns the current inactivity limit in minutes, reading from OscarProperties
     * on each call to stay consistent with LoginFilter's per-request reading.
     *
     * @return int the inactivity limit in minutes
     */
    private int getInactivityLimitMins() {
        String limitProp = OscarProperties.getInstance().getProperty("INACTIVITY_LIMIT_MINS");
        if (limitProp != null && !limitProp.trim().isEmpty()) {
            try {
                return Integer.parseInt(limitProp.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid INACTIVITY_LIMIT_MINS value '{}', using default {}",
                        limitProp, DEFAULT_INACTIVITY_LIMIT_MINS);
            }
        }
        return DEFAULT_INACTIVITY_LIMIT_MINS;
    }

    /**
     * Filters HTML responses to append the logout broadcast and session heartbeat script.
     *
     * <p>Excluded URLs are short-circuited before response wrapping to avoid unnecessary
     * buffer allocation (e.g., for the lightweight sessionHeartbeat.jsp endpoint).
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

        // Short-circuit excluded URLs before wrapping to avoid 1MB buffer allocation
        if (isExcluded(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        DelegatingServletResponse delegatingResponse = new DelegatingServletResponse(httpResponse);
        chain.doFilter(request, delegatingResponse);

        // Only inject for authenticated sessions
        HttpSession session = httpRequest.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return;
        }

        // Only inject for HTML responses
        String contentType = delegatingResponse.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("text/html")) {
            return;
        }

        // Don't inject for AJAX requests
        String requestedWith = httpRequest.getHeader(HTTP_HEADER_NAME_AJAX_REQUESTED_WITH);
        if (requestedWith != null && HTTP_HEADER_VALUE_AJAX_REQUESTED_WITH.equalsIgnoreCase(requestedWith)) {
            return;
        }

        appendScript(response, delegatingResponse, httpRequest.getContextPath());
    }

    /**
     * Checks if the request URL is in the exclusion list.
     *
     * @param request HttpServletRequest the request to check
     * @return boolean true if the URL should be excluded from script injection
     */
    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath == null) {
            return false;
        }

        servletPath = servletPath.toLowerCase().trim();
        for (String ex : exclusions) {
            if (servletPath.startsWith(ex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends the inline logout broadcast and session heartbeat script to the response.
     *
     * @param response ServletResponse the original response for output
     * @param delegatingResponse DelegatingServletResponse the wrapped response
     * @param contextPath String the servlet context path
     * @throws IOException if I/O error occurs writing the script
     */
    private void appendScript(ServletResponse response, DelegatingServletResponse delegatingResponse,
                              String contextPath) throws IOException {

        String script = buildScript(contextPath);

        if (delegatingResponse.isResponseOutputStreamObtained()) {
            response.getOutputStream().write(script.getBytes());
        } else if (delegatingResponse.isResponseWriterObtained()) {
            response.getWriter().print(script);
        }

        response.flushBuffer();
    }

    /**
     * Builds the inline JavaScript for logout broadcast and session heartbeat.
     *
     * <p>The script uses an IIFE to avoid polluting the global scope. It sets a
     * {@code window.__carlosLogoutActive} guard to prevent duplicate injection
     * (e.g., from nested frames or multiple filter passes).
     *
     * <p>The context path is encoded with {@link Encode#forJavaScript(String)} to
     * prevent XSS in the JavaScript string literal.
     *
     * @param contextPath String the servlet context path for URL construction
     * @return String the complete {@code <script>} block to inject
     */
    private String buildScript(String contextPath) {
        int inactivityLimitMins = getInactivityLimitMins();

        return "<script>" +
                "(function(){" +
                "if(window.__carlosLogoutActive)return;" +
                "window.__carlosLogoutActive=true;" +

                "var cp='" + Encode.forJavaScript(contextPath) + "';" +
                "var ilMs=" + inactivityLimitMins + "*60000;" +
                "var lastOk=Date.now();" +
                "var loginUrl=cp+'/index.jsp';" +
                "var done=false;" +

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
                "fetch(cp+'/status/sessionHeartbeat.jsp?autoRefresh=true')" +
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

                // doLogout — close popup or redirect tab to login
                "function dL(){" +
                "try{window.close()}catch(e){}" +
                "setTimeout(function(){window.location.href=loginUrl},200)" +
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
     * Writer that prevents flushing and closing of the underlying writer,
     * allowing content to be appended after the chain completes.
     */
    private static class DelegatingWriter extends PrintWriter {

        /**
         * Creates a DelegatingWriter wrapping the given writer.
         *
         * @param out Writer the underlying writer to delegate output to
         */
        public DelegatingWriter(Writer out) {
            super(out);
        }

        /**
         * Suppresses flush to prevent premature buffer flushing while the filter chain executes.
         *
         * <p>The actual flush is deferred until after the logout broadcast script has been
         * appended to the response by {@link LogoutBroadcastFilter#appendScript}.
         */
        @Override
        public void flush() {
            // prevent premature flush
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
            // prevent premature close
        }
    }

    /**
     * Response wrapper that defers flushing so the filter can append content
     * after the filter chain has written its output.
     *
     * <p>Tracks whether {@code getWriter()} or {@code getOutputStream()} was called
     * so the filter knows which output method to use for appending.
     */
    private static class DelegatingServletResponse extends HttpServletResponseWrapper {

        private boolean responseWriterObtained;
        private boolean responseOutputStreamObtained;
        private DelegatingWriter writer;

        /**
         * Creates a DelegatingServletResponse wrapping the given HTTP response.
         *
         * <p>Allocates a 1 MB server-side buffer to ensure the response body is held in
         * memory until the filter can append the logout broadcast script after the chain.
         *
         * @param response HttpServletResponse the HTTP response to wrap
         */
        public DelegatingServletResponse(HttpServletResponse response) {
            super(response);
            response.setBufferSize(1024 * 1024);
        }

        /**
         * Returns the underlying response and marks the writer path as obtained.
         *
         * <p>Called by Servlet/JSP infrastructure when downstream code needs the raw response
         * object. Marks {@code responseWriterObtained} so the script appender knows which
         * output channel was used.
         *
         * @return ServletResponse the underlying HTTP response
         */
        @Override
        public ServletResponse getResponse() {
            responseWriterObtained = true;
            return super.getResponse();
        }

        /**
         * Returns the raw servlet output stream and marks the stream path as obtained.
         *
         * <p>Unlike {@link #getWriter()}, the returned stream is <em>not</em> wrapped
         * with a delegating proxy. This is intentional: HTML JSP responses always use the
         * Writer path ({@code getWriter()} and {@code getOutputStream()} are mutually
         * exclusive per the Servlet spec). The OutputStream path is tracked for the rare
         * case of non-JSP HTML generators; Tomcat's response lifecycle prevents premature
         * stream closure during filter chain execution in practice.
         *
         * @return ServletOutputStream the raw servlet output stream
         * @throws IOException if the output stream cannot be obtained from the underlying response
         */
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            responseOutputStreamObtained = true;
            return super.getOutputStream();
        }

        /**
         * Returns a {@link DelegatingWriter} wrapping the underlying response writer.
         *
         * <p>The delegating writer suppresses {@code close()} and {@code flush()} calls
         * made by the JSP container during JSP execution, ensuring the filter can still
         * append the logout broadcast script after the chain completes.
         *
         * @return PrintWriter a delegating writer that suppresses premature close and flush
         * @throws IOException if the underlying writer cannot be obtained
         */
        @Override
        public PrintWriter getWriter() throws IOException {
            responseWriterObtained = true;
            if (writer == null) {
                writer = new DelegatingWriter(super.getWriter());
            }
            return writer;
        }

        /**
         * Returns whether downstream code obtained the response writer during chain execution.
         *
         * <p>Used by {@link LogoutBroadcastFilter#appendScript} to determine which output
         * channel to use when writing the injected script.
         *
         * @return boolean true if {@link #getWriter()} or {@link #getResponse()} was called
         */
        public boolean isResponseWriterObtained() {
            return responseWriterObtained;
        }

        /**
         * Returns whether downstream code obtained the servlet output stream during chain execution.
         *
         * <p>Used by {@link LogoutBroadcastFilter#appendScript} to determine which output
         * channel to use when writing the injected script.
         *
         * @return boolean true if {@link #getOutputStream()} was called
         */
        public boolean isResponseOutputStreamObtained() {
            return responseOutputStreamObtained;
        }

        /**
         * Suppresses buffer flushing until after the filter appends the logout broadcast script.
         *
         * <p>Without this guard, the JSP container may flush and commit the response to the
         * client before {@link LogoutBroadcastFilter#appendScript} can write the script block,
         * resulting in incomplete or missing injection.
         *
         * @throws IOException never thrown by this implementation
         */
        @Override
        public void flushBuffer() throws IOException {
            // defer flushing until after script is appended
        }
    }
}
