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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.utility.LogSanitizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Servlet filter that resolves the real client IP from the {@code X-Forwarded-For} header
 * when the application is deployed behind a reverse proxy.
 *
 * <p><strong>Security:</strong> To prevent localhost-gate bypass attacks, this filter rejects
 * loopback ({@code 127.0.0.0/8}, {@code ::1}) and unspecified ({@code 0.0.0.0}, {@code ::})
 * addresses from the {@code X-Forwarded-For} header. An external client should never claim
 * to be a loopback address; if one does, the raw peer IP from the socket is used instead.
 * This prevents spoofed {@code X-Forwarded-For: 127.0.0.1} headers from bypassing
 * security gates that restrict access to localhost-only processes (e.g., wkhtmltopdf).</p>
 *
 * @since 2012 (OSCAR McMaster heritage; loopback rejection added 2026-04-13)
 */
public class XforwardHeaderFilter implements Filter {

    private static final Logger logger = LogManager.getLogger(XforwardHeaderFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // do default
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            chain.doFilter(new ModifyRemoteAddress((HttpServletRequest) request), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // do default
    }

    static class ModifyRemoteAddress extends HttpServletRequestWrapper {

        /**
         * Matches numeric IPv4 ({@code 192.168.1.1}) or IPv6 ({@code ::1}, {@code fe80::1})
         * address literals. Does NOT match hostnames, preventing unintended DNS lookups
         * when passed to {@link InetAddress#getByName(String)}.
         *
         * <p>This pattern is intentionally loose (e.g., allows {@code 999.999.999.999}
         * for IPv4 or malformed colon sequences for IPv6) because {@code InetAddress.getByName()}
         * provides authoritative validation — malformed literals throw
         * {@link UnknownHostException} without triggering DNS, which is the safe default.</p>
         *
         * <ul>
         *   <li>IPv4: four dotted-decimal octets ({@code \d{1,3}(\.\d{1,3}){3}})</li>
         *   <li>IPv6: hex digits and colons, with optional IPv4-mapped suffix
         *       (requires at least one colon, which distinguishes from hostnames)</li>
         * </ul>
         */
        private static final Pattern IP_LITERAL_PATTERN = Pattern.compile(
                "\\d{1,3}(\\.\\d{1,3}){3}"
                + "|"
                + "[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

        public ModifyRemoteAddress(HttpServletRequest request) {
            super(request);
        }

        /**
         * Override get remote address in case the remote IP address is stored
         * in the {@code X-Forwarded-For} header attribute.
         *
         * <p>If the header value is a loopback or unspecified address, the header
         * is ignored and the raw socket peer address is returned instead. This
         * prevents {@code X-Forwarded-For} spoofing from bypassing localhost gates.</p>
         *
         * @return Remote IP address
         */
        @Override
        public String getRemoteAddr() {
            String ip = super.getHeader("X-FORWARDED-FOR");

            if (ip == null || ip.isEmpty()) {
                return super.getRemoteAddr();
            }

            if (ip.contains(",")) {
                ip = ip.split(",")[0];
            }
            ip = ip.trim();

            if (isLoopbackOrUnspecified(ip)) {
                logger.warn("Rejected loopback/unspecified address '{}' from X-Forwarded-For header; "
                        + "using raw peer address instead", LogSanitizer.sanitize(ip));
                return super.getRemoteAddr();
            }

            return ip;
        }

        /**
         * Checks whether the given IP string represents a loopback address
         * ({@code 127.0.0.0/8}, {@code ::1}) or the unspecified address
         * ({@code 0.0.0.0}, {@code ::}).
         *
         * <p>These addresses should never appear as a legitimate client IP in
         * the {@code X-Forwarded-For} header. Non-numeric-literal inputs (e.g.,
         * hostnames) are rejected without DNS lookup to avoid blocking on
         * attacker-controlled name resolution. If parsing fails, the address is
         * treated as reserved (safe default).</p>
         *
         * @param ip the IP address string to check
         * @return {@code true} if the address is loopback, unspecified, non-IP-literal, or unparseable
         */
        static boolean isLoopbackOrUnspecified(String ip) {
            if (ip == null || ip.isBlank()) {
                return true;
            }
            String trimmed = ip.trim();
            if (!IP_LITERAL_PATTERN.matcher(trimmed).matches()) {
                return true;
            }
            try {
                InetAddress addr = InetAddress.getByName(trimmed);
                return addr.isLoopbackAddress() || addr.isAnyLocalAddress();
            } catch (UnknownHostException e) {
                return true;
            }
        }
    }
}
