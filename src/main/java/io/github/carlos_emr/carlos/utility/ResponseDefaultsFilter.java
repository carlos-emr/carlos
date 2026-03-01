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
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
            temp = filterConfig.getInitParameter("noCacheEndings");
            if (temp != null) {
                this.noCacheEndings = temp.split(",");
            }
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

        setSecurityHeaders(response);

        if (this.forceStrongETag || this.warnCharsetCacheChange) {
            response = new ResponseDefaultsFilterResponseWrapper((HttpServletResponse) response, this.forceStrongETag, this.warnCharsetCacheChange);
        }

        chain.doFilter(request, (ServletResponse) response);

        // Re-apply security headers after the chain to override any downstream changes.
        // The committed check is required because headers cannot be sent after the
        // response body has already been flushed to the client.
        if (!response.isCommitted()) {
            setSecurityHeaders(response);
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
     * @param response HttpServletResponse the response to add headers to
     */
    private void setSecurityHeaders(HttpServletResponse response) {
        // Clickjack protection (replaces ESAPI ClickjackFilter)
        response.setHeader("X-Frame-Options", "SAMEORIGIN");

        // Prevent Adobe Flash/Acrobat cross-domain data loading
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");

        // Restrict browser features not used by this application
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        // Prevent MIME type sniffing (defense-in-depth for content-type handling)
        response.setHeader("X-Content-Type-Options", "nosniff");
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

