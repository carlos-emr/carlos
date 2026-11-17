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

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Applies one opener policy to every application response, including static
 * pages, redirects, action forwards and errors. A popup loses its opener when
 * one navigation hop differs from its originating page's policy, so a Struts
 * result interceptor cannot own this header.
 *
 * <p>This filter changes headers only: it neither buffers nor rewrites bodies.
 * It runs immediately inside the response sanitizer and before filters that
 * may reject a request. Resetting a response restores the policy before any
 * downstream code can commit it.
 *
 * @since 2026-10-04
 */
public final class CrossOriginOpenerPolicyFilter implements Filter {
    static final String HEADER = "Cross-Origin-Opener-Policy";
    static final String POLICY = "same-origin";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        PolicyResponse wrapper = new PolicyResponse(httpResponse);
        wrapper.applyPolicy();
        try {
            chain.doFilter(request, wrapper);
        } finally {
            if (!wrapper.isCommitted()) {
                wrapper.applyPolicy();
            }
        }
    }

    /** Preserve the header when a download/action clears its response before writing. */
    private static final class PolicyResponse extends HttpServletResponseWrapper {
        private PolicyResponse(HttpServletResponse response) {
            super(response);
        }

        private void applyPolicy() {
            setHeader(HEADER, POLICY);
        }

        @Override
        public void reset() {
            super.reset();
            applyPolicy();
        }
    }
}
