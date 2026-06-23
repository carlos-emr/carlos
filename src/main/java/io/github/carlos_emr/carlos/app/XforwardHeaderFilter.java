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
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves the client IP from proxy headers only when the immediate peer is trusted.
 *
 * <p>The previous implementation trusted {@code X-Forwarded-For} from any client,
 * which made rate limiting and audit logging spoofable. This filter now honors
 * proxy headers only when the TCP peer address is explicitly trusted via
 * {@code WAF_TRUSTED_PROXY_IPS} or {@code WAF_TRUSTED_PROXY_CIDRS}.</p>
 *
 * @since 2026-04-15
 */
public class XforwardHeaderFilter implements Filter {

    static final String TRUSTED_PROXY_IPS_PROPERTY = "WAF_TRUSTED_PROXY_IPS";
    static final String TRUSTED_PROXY_CIDRS_PROPERTY = "WAF_TRUSTED_PROXY_CIDRS";

    private static final Logger LOGGER = MiscUtils.getLogger();

    private Set<String> trustedProxyIps = Collections.emptySet();
    private Set<CidrRange> trustedProxyCidrs = Collections.emptySet();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        CarlosProperties properties = CarlosProperties.getInstance();
        trustedProxyIps = parseCsv(properties.getProperty(TRUSTED_PROXY_IPS_PROPERTY, ""));
        trustedProxyCidrs = parseCidrs(properties.getProperty(TRUSTED_PROXY_CIDRS_PROPERTY, ""));
        LOGGER.info("XforwardHeaderFilter initialized: trustedProxyIps={}, trustedProxyCidrs={}",
                trustedProxyIps.size(), trustedProxyCidrs.size());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            chain.doFilter(new ModifyRemoteAddress(
                    (HttpServletRequest) request, trustedProxyIps, trustedProxyCidrs), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // no-op
    }

    static Set<String> parseCsv(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .forEach(result::add);
        return Collections.unmodifiableSet(result);
    }

    static Set<CidrRange> parseCidrs(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }

        Set<CidrRange> result = new HashSet<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(CidrRange.parse(trimmed));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Ignoring invalid trusted proxy CIDR '{}'", trimmed, e);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    static boolean isTrustedProxy(
            String remoteAddr,
            Set<String> trustedProxyIps,
            Set<CidrRange> trustedProxyCidrs) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }

        String normalized = remoteAddr.trim().toLowerCase(Locale.ROOT);
        if (trustedProxyIps.contains(normalized)) {
            return true;
        }

        for (CidrRange cidr : trustedProxyCidrs) {
            if (cidr.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    static String extractClientIp(
            String xForwardedFor,
            Set<String> trustedProxyIps,
            Set<CidrRange> trustedProxyCidrs) {
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return null;
        }

        String[] tokens = xForwardedFor.split(",");
        for (int i = tokens.length - 1; i >= 0; i--) {
            String candidate = tokens[i].trim();
            if (!isValidIpAddress(candidate)) {
                continue;
            }
            if (!isTrustedProxy(candidate, trustedProxyIps, trustedProxyCidrs)) {
                return candidate;
            }
        }
        return null;
    }

    static boolean isValidIpAddress(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        try {
            InetAddress.getByName(candidate);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static final class ModifyRemoteAddress extends HttpServletRequestWrapper {

        private final Set<String> trustedProxyIps;
        private final Set<CidrRange> trustedProxyCidrs;

        ModifyRemoteAddress(
                HttpServletRequest request,
                Set<String> trustedProxyIps,
                Set<CidrRange> trustedProxyCidrs) {
            super(request);
            this.trustedProxyIps = trustedProxyIps;
            this.trustedProxyCidrs = trustedProxyCidrs;
        }

        @Override
        public String getRemoteAddr() {
            String remoteAddr = super.getRemoteAddr();
            if (!isTrustedProxy(remoteAddr, trustedProxyIps, trustedProxyCidrs)) {
                return remoteAddr;
            }

            String forwardedFor = super.getHeader("X-Forwarded-For");
            String clientIp = extractClientIp(forwardedFor, trustedProxyIps, trustedProxyCidrs);
            return clientIp != null ? clientIp : remoteAddr;
        }

        @Override
        public String getScheme() {
            String proto = forwardedProto();
            return proto != null ? proto : super.getScheme();
        }

        @Override
        public boolean isSecure() {
            String proto = forwardedProto();
            return proto != null ? "https".equals(proto) : super.isSecure();
        }

        @Override
        public String getServerName() {
            String host = forwardedHostName();
            return host != null ? host : super.getServerName();
        }

        @Override
        public int getServerPort() {
            if (!peerIsTrusted()) {
                return super.getServerPort();
            }
            Integer explicitPort = parsePort(firstToken(super.getHeader("X-Forwarded-Port")));
            if (explicitPort != null) {
                return explicitPort;
            }
            Integer hostPort = parsePort(hostPortPart(firstToken(super.getHeader("X-Forwarded-Host"))));
            if (hostPort != null) {
                return hostPort;
            }
            String proto = forwardedProto();
            if (proto != null) {
                return "https".equals(proto) ? 443 : 80;
            }
            return super.getServerPort();
        }

        private boolean peerIsTrusted() {
            return isTrustedProxy(super.getRemoteAddr(), trustedProxyIps, trustedProxyCidrs);
        }

        /**
         * @return the lowercased {@code X-Forwarded-Proto} value ({@code http} or {@code https})
         *         when the peer is trusted and the header is present and valid, otherwise {@code null}.
         */
        private String forwardedProto() {
            if (!peerIsTrusted()) {
                return null;
            }
            String proto = firstToken(super.getHeader("X-Forwarded-Proto"));
            if ("https".equalsIgnoreCase(proto)) {
                return "https";
            }
            if ("http".equalsIgnoreCase(proto)) {
                return "http";
            }
            return null;
        }

        /**
         * @return the host portion (port stripped) of {@code X-Forwarded-Host} when the peer is
         *         trusted and the header is present, otherwise {@code null}. A bracketed IPv6
         *         literal keeps its brackets so it remains valid in a reconstructed URL.
         */
        private String forwardedHostName() {
            if (!peerIsTrusted()) {
                return null;
            }
            String host = firstToken(super.getHeader("X-Forwarded-Host"));
            if (host == null) {
                return null;
            }
            String name;
            if (host.startsWith("[")) {
                int close = host.indexOf(']');
                name = (close >= 0) ? host.substring(0, close + 1).trim() : host;
            } else {
                int colon = host.indexOf(':');
                // Only treat as host:port when there is a single colon (IPv4 / hostname); an
                // unbracketed value with multiple colons is a bare IPv6 literal, so keep it whole.
                name = (colon >= 0 && host.indexOf(':', colon + 1) < 0)
                        ? host.substring(0, colon).trim() : host;
            }
            return name.isEmpty() ? null : name;
        }

        private static String hostPortPart(String host) {
            if (host == null) {
                return null;
            }
            if (host.startsWith("[")) {
                int close = host.indexOf(']');
                if (close >= 0 && close + 1 < host.length() && host.charAt(close + 1) == ':') {
                    String port = host.substring(close + 2).trim();
                    return port.isEmpty() ? null : port;
                }
                return null;
            }
            int colon = host.indexOf(':');
            // A single colon delimits host:port; multiple colons mean a bare IPv6 literal (no port).
            if (colon >= 0 && colon < host.length() - 1 && host.indexOf(':', colon + 1) < 0) {
                return host.substring(colon + 1).trim();
            }
            return null;
        }

        private static String firstToken(String header) {
            if (header == null) {
                return null;
            }
            int comma = header.indexOf(',');
            String token = (comma >= 0 ? header.substring(0, comma) : header).trim();
            return token.isEmpty() ? null : token;
        }

        private static Integer parsePort(String value) {
            if (value == null) {
                return null;
            }
            try {
                int port = Integer.parseInt(value);
                return (port >= 1 && port <= 65535) ? port : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    static final class CidrRange {

        private final byte[] addressBytes;
        private final int prefixLength;

        private CidrRange(byte[] addressBytes, int prefixLength) {
            this.addressBytes = addressBytes;
            this.prefixLength = prefixLength;
        }

        static CidrRange parse(String cidr) {
            int slash = cidr.indexOf('/');
            if (slash <= 0 || slash == cidr.length() - 1) {
                throw new IllegalArgumentException("CIDR must be in address/prefix form");
            }

            String addressPart = cidr.substring(0, slash).trim();
            String prefixPart = cidr.substring(slash + 1).trim();

            try {
                InetAddress address = InetAddress.getByName(addressPart);
                int prefixLength = Integer.parseInt(prefixPart);
                int maxBits = address.getAddress().length * 8;
                if (prefixLength < 0 || prefixLength > maxBits) {
                    throw new IllegalArgumentException("CIDR prefix out of range");
                }
                return new CidrRange(address.getAddress(), prefixLength);
            } catch (UnknownHostException | NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR", e);
            }
        }

        boolean contains(String address) {
            try {
                byte[] candidate = InetAddress.getByName(address).getAddress();
                if (candidate.length != addressBytes.length) {
                    return false;
                }

                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;

                for (int i = 0; i < fullBytes; i++) {
                    if (candidate[i] != addressBytes[i]) {
                        return false;
                    }
                }

                if (remainingBits == 0) {
                    return true;
                }

                int mask = 0xFF << (8 - remainingBits);
                return (candidate[fullBytes] & mask) == (addressBytes[fullBytes] & mask);
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
