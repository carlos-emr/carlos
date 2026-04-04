/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter that sets response defaults for encoding, caching, ETags, and security headers.
 *
 * <p>Applied to all requests ({@code /*}) via {@code web.xml}. Configurable via init-params:
 * <ul>
 *   <li>{@code setEncoding} / {@code encoding} — force UTF-8 on requests and responses (default: true)</li>
 *   <li>{@code setNoCache} / {@code noCacheEndings} — disable caching for dynamic resources (default: .jsp, .json, .jsf)</li>
 *   <li>{@code forceStrongETag} — strip weak ETag prefixes ({@code W/}) for proxy compatibility (default: true)</li>
 *   <li>{@code warnCharsetCacheChange} — log warnings when downstream code changes encoding or cache headers (default: false)</li>
 * </ul>
 *
 * <p>Also sets security headers on every response:
 * <ul>
 *   <li>{@code X-Frame-Options: SAMEORIGIN} — clickjack protection (replaces the removed ESAPI ClickjackFilter)</li>
 *   <li>{@code X-Permitted-Cross-Domain-Policies: none} — blocks Flash/Acrobat cross-domain data loading</li>
 *   <li>{@code Permissions-Policy: camera=(), microphone=(), geolocation=()} — restricts unused browser APIs</li>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME type sniffing attacks</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — prevents PHI leakage in referrer headers</li>
 *   <li>{@code Cross-Origin-Opener-Policy: same-origin} — isolates browsing context</li>
 *   <li>{@code Cross-Origin-Resource-Policy: same-origin} — prevents cross-origin resource reads</li>
 *   <li>{@code Content-Security-Policy-Report-Only} — XSS defense-in-depth (report-only until tuned)</li>
 *   <li>{@code Strict-Transport-Security} — HTTPS enforcement (only on secure connections)</li>
 * </ul>
 *
 * @since 2012 (OSCAR McMaster heritage; security headers added 2026-02-26)
 * @see ResponseDefaultsFilterResponseWrapper
 */
public final class ResponseDefaultsFilter implements Filter {
    private static Logger logger = MiscUtils.getLogger();
    private boolean setEncoding = true;
    private String encoding = "UTF-8";
    private boolean setNoCache = true;
    private String[] noCacheEndings = new String[]{".jsp", ".json", ".jsf"};
    private boolean forceStrongETag = true;
    private boolean warnCharsetCacheChange = false;

    public ResponseDefaultsFilter() {
    }

    /**
     * Reads init-params from {@code web.xml} filter configuration.
     *
     * @param filterConfig FilterConfig the servlet container filter configuration
     */
    public void init(FilterConfig filterConfig) {
        logger.info("Initialising " + ResponseDefaultsFilter.class.getSimpleName());
        String temp = filterConfig.getInitParameter("setEncoding");
        if (temp != null) {
            this.setEncoding = Boolean.parseBoolean(temp);
            this.encoding = filterConfig.getInitParameter("encoding");
        }

        logger.info("setEncoding=" + this.setEncoding + ", encoding=" + this.encoding);
        temp = filterConfig.getInitParameter("setNoCache");
        if (temp != null) {
            this.setNoCache = Boolean.parseBoolean(temp);
        }

        temp = filterConfig.getInitParameter("noCacheEndings");
        if (temp != null) {
            this.noCacheEndings = Arrays.stream(temp.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }

        logger.info("setNoCache=" + this.setNoCache + ", noCacheEndings=" + Arrays.toString(this.noCacheEndings));
        temp = filterConfig.getInitParameter("forceStrongETag");
        if (temp != null) {
            this.forceStrongETag = Boolean.parseBoolean(temp);
        }

        logger.info("forceStrongETag=" + this.forceStrongETag);
        temp = filterConfig.getInitParameter("warnCharsetCacheChange");
        if (temp != null) {
            this.warnCharsetCacheChange = Boolean.parseBoolean(temp);
        }

        logger.info("warnCharsetCacheChange=" + this.warnCharsetCacheChange);
    }

    /** {@inheritDoc} */
    public void destroy() {
        logger.info("shutdown " + ResponseDefaultsFilter.class.getSimpleName());
    }

    /**
     * Applies encoding, caching, and security header defaults, then delegates to the filter chain.
     *
     * <p>Security headers are set both before and after {@code chain.doFilter}. The pre-chain
     * call guarantees headers are present on all responses including those where the response
     * body is flushed by the downstream action (e.g. JSON API responses). The post-chain call
     * (guarded by {@code !isCommitted()}) overrides any downstream code that may have changed
     * the security headers, providing defense-in-depth.
     *
     * <p>When {@code forceStrongETag} or {@code warnCharsetCacheChange} is enabled, the response
     * is wrapped in a {@link ResponseDefaultsFilterResponseWrapper} to intercept downstream
     * header modifications.
     *
     * @param originalRequest  ServletRequest the incoming request
     * @param originalResponse ServletResponse the outgoing response
     * @param chain            FilterChain the remaining filter chain
     * @throws IOException      if an I/O error occurs during filtering
     * @throws ServletException if the filter chain throws
     */
    public void doFilter(ServletRequest originalRequest, ServletResponse originalResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) originalRequest;
        HttpServletResponse response = (HttpServletResponse) originalResponse;
        if (this.setEncoding) {
            this.setEncoding(request, (HttpServletResponse) response);
        }

        if (this.setNoCache) {
            this.setCaching(request, (HttpServletResponse) response);
        }

        setSecurityHeaders(request, response);

        if (this.forceStrongETag || this.warnCharsetCacheChange) {
            response = new ResponseDefaultsFilterResponseWrapper((HttpServletResponse) response, this.forceStrongETag, this.warnCharsetCacheChange);
        }

        chain.doFilter(request, (ServletResponse) response);

        // Re-apply security headers after the chain to override any downstream changes.
        // The committed check is required because headers cannot be sent after the
        // response body has already been flushed to the client.
        if (!response.isCommitted()) {
            setSecurityHeaders(request, response);
        }
    }

    /**
     * Disables caching for requests whose URI ends with a configured suffix
     * (e.g. {@code .jsp}, {@code .do}).
     *
     * @param request  HttpServletRequest the current request (used for URI matching)
     * @param response HttpServletResponse the response to set cache headers on
     */
    private void setCaching(HttpServletRequest request, HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        String[] arr$ = this.noCacheEndings;
        int len$ = arr$.length;

        for (int i$ = 0; i$ < len$; ++i$) {
            String noCacheEnding = arr$[i$];
            if (requestUri.endsWith(noCacheEnding)) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
                return;
            }
        }

    }

    /**
     * Sets security headers on every response. Replaces the ESAPI ClickjackFilter
     * that previously only set X-Frame-Options.
     *
     * <p>Headers are grouped by purpose:
     * <ol>
     *   <li><strong>Framing/embedding protection</strong> — X-Frame-Options, COOP, CORP, Cross-Domain</li>
     *   <li><strong>Content type protection</strong> — X-Content-Type-Options</li>
     *   <li><strong>Browser feature restrictions</strong> — Permissions-Policy</li>
     *   <li><strong>Referrer privacy</strong> — Referrer-Policy (prevents PHI leakage in referrer headers)</li>
     *   <li><strong>XSS defense-in-depth</strong> — Content-Security-Policy-Report-Only</li>
     *   <li><strong>Transport security</strong> — Strict-Transport-Security (HTTPS only)</li>
     * </ol>
     *
     * @param request  HttpServletRequest the incoming request (used for HSTS secure check)
     * @param response HttpServletResponse the response to add headers to
     */
    private void setSecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        // Clickjack protection (replaces ESAPI ClickjackFilter)
        response.setHeader("X-Frame-Options", "SAMEORIGIN");

        // Prevent Adobe Flash/Acrobat cross-domain data loading
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");

        // Restrict browser features not used by this application
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");

        // Prevent MIME type sniffing (defense-in-depth for content-type handling)
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent PHI leakage in referrer headers — full URL for same-origin, origin-only for cross-origin
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Isolate browsing context from cross-origin windows (prevents Spectre-class attacks)
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");

        // Prevent cross-origin reads of application resources
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");

        // Content-Security-Policy in Report-Only mode — logs violations without breaking pages.
        // The 'unsafe-inline' and 'unsafe-eval' directives are required for legacy JSP inline
        // scripts and jQuery; tighten these as inline scripts are migrated to external files.
        // Bootstrap 5.3 and fonts are loaded from cdn.jsdelivr.net.
        response.setHeader("Content-Security-Policy-Report-Only",
                "default-src 'self'; "
                + "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                + "img-src 'self' data:; "
                + "font-src 'self' https://cdn.jsdelivr.net; "
                + "frame-ancestors 'self'; "
                + "base-uri 'self'; "
                + "form-action 'self'");

        // HSTS — only set on secure (HTTPS) connections to avoid breaking HTTP dev environments.
        // 2-year max-age per OWASP recommendation; includeSubDomains for comprehensive coverage.
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
        }
    }

    /**
     * Sets UTF-8 encoding on both request (if not already set) and response.
     *
     * @param request  HttpServletRequest the incoming request
     * @param response HttpServletResponse the outgoing response
     * @throws UnsupportedEncodingException if the configured encoding is not supported
     */
    private void setEncoding(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding(this.encoding);
        }

        response.setCharacterEncoding(this.encoding);
    }
}

