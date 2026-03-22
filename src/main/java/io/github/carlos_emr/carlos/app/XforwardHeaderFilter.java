/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.app;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;

/**
 * Servlet filter that resolves the client's real IP address from the {@code X-FORWARDED-FOR}
 * header when the application is behind a reverse proxy or load balancer.
 *
 * <p>Wraps incoming {@link HttpServletRequest} objects with a {@link ModifyRemoteAddress}
 * wrapper that overrides {@link HttpServletRequest#getRemoteAddr()} to return the first
 * IP address in the {@code X-FORWARDED-FOR} header chain, if present.
 *
 * @since 2026-03-17
 */
public class XforwardHeaderFilter implements Filter {

    /**
     * Initializes the filter. No configuration is required.
     *
     * @param filterConfig FilterConfig the filter configuration
     * @throws ServletException if initialization fails
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // do default
    }

    /**
     * Wraps HTTP requests with a {@link ModifyRemoteAddress} wrapper that extracts
     * the client IP from the {@code X-FORWARDED-FOR} header. Non-HTTP requests
     * pass through unchanged.
     *
     * @param request  ServletRequest the incoming request
     * @param response ServletResponse the outgoing response
     * @param chain    FilterChain the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            chain.doFilter(new ModifyRemoteAddress((HttpServletRequest) request), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * Called when the filter is taken out of service. No cleanup is required.
     */
    @Override
    public void destroy() {
        // do default
    }

    /**
     * Request wrapper that overrides {@link #getRemoteAddr()} to return the client IP
     * from the {@code X-FORWARDED-FOR} header. When the header contains multiple
     * comma-separated addresses (proxy chain), the first address (original client) is used.
     */
    static class ModifyRemoteAddress extends HttpServletRequestWrapper {

        /**
         * Creates a new wrapper around the given request.
         *
         * @param request HttpServletRequest the original request to wrap
         */
        public ModifyRemoteAddress(HttpServletRequest request) {
            super(request);
        }

        /**
         * Override get remote address in case the remote
         * IP address is stored in the X-FORWARDED-FOR header attribute.
         *
         * @return Remote IP address
         */
        public String getRemoteAddr() {
            String ip = super.getHeader("X-FORWARDED-FOR");

            if (ip == null || ip.isEmpty()) {
                return super.getRemoteAddr();
            }

            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }

            return ip;
        }
    }
}
