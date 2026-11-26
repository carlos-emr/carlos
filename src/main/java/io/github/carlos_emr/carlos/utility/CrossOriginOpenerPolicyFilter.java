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
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Applies one opener policy to every application response, including static
 * pages, redirects, action forwards and errors. A popup loses its opener when
 * one navigation hop differs from its originating page's policy, so a Struts
 * result interceptor cannot own this header.
 *
 * <p>The policy is {@code same-origin} everywhere except one page: the Shared Outcomes
 * dashboard launcher ({@value #SHARED_OUTCOMES_LAUNCHER_PATH}, issue #5335). That page opens the
 * external shared dashboard on another origin and talks to it over {@code postMessage}
 * (CONNECT, refresh requests). Under {@code same-origin} the browser severs that popup from its
 * opener, so the channel can never open; {@code same-origin-allow-popups} keeps it while still
 * isolating the page from any cross-origin window that might open it. The exception is one exact
 * path, chosen from the request the user made (forwards and error dispatches keep it), and any
 * other spelling of the address falls back to {@code same-origin}.
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
    /** Opener policy of the Shared Outcomes launcher, the only page that must keep a cross-origin popup. */
    static final String POPUP_POLICY = "same-origin-allow-popups";
    /** Application-relative path of the Shared Outcomes launcher action (struts-form.xml). */
    static final String SHARED_OUTCOMES_LAUNCHER_PATH = "/web/dashboard/display/sharedOutcomesDashboard";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        PolicyResponse wrapper = new PolicyResponse(httpResponse, policyFor(request));
        wrapper.applyPolicy();
        try {
            chain.doFilter(request, wrapper);
        } finally {
            if (!wrapper.isCommitted()) {
                wrapper.applyPolicy();
            }
        }
    }

    /**
     * The policy for the page the user asked for. A forward (action to JSP) or an error dispatch
     * sees the internal target, so the original request URI is read from the dispatch attributes;
     * otherwise the launcher's JSP would be served {@code same-origin} and the exception lost.
     */
    static String policyFor(ServletRequest request) {
        if (!(request instanceof HttpServletRequest http)) {
            return POLICY;
        }
        Object forwarded = http.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        Object errored = http.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String uri = forwarded instanceof String f ? f : errored instanceof String e ? e : http.getRequestURI();
        String context = http.getContextPath();
        if (uri != null && context != null && uri.equals(context + SHARED_OUTCOMES_LAUNCHER_PATH)) {
            return POPUP_POLICY;
        }
        return POLICY;
    }

    /** Preserve the header when a download/action clears its response before writing. */
    private static final class PolicyResponse extends HttpServletResponseWrapper {
        private final String policy;

        private PolicyResponse(HttpServletResponse response, String policy) {
            super(response);
            this.policy = policy;
        }

        private void applyPolicy() {
            setHeader(HEADER, policy);
        }

        @Override
        public void reset() {
            super.reset();
            applyPolicy();
        }
    }
}
