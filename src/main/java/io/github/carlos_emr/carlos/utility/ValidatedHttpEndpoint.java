/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Validated outbound HTTP endpoint with a request-scoped, pinned DNS result.
 */
public final class ValidatedHttpEndpoint {

    private final URI uri;
    private final InetAddress[] addresses;
    private final boolean https;
    private final String normalizedHost;

    private ValidatedHttpEndpoint(
            URI uri, InetAddress[] addresses, boolean https, String normalizedHost) {
        this.uri = uri;
        this.addresses = addresses.clone();
        this.https = https;
        this.normalizedHost = normalizedHost;
    }

    /**
     * Validates an operator-configured endpoint and resolves every address exactly once.
     *
     * <p>Hosts explicitly listed in {@code allowlistProperty} may resolve to private addresses, but
     * are still pinned to the validated result for the lifetime of the HTTP client.</p>
     */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "URI schemes and DNS host labels are protocol identifiers; Locale.ROOT and exact case-insensitive DNS comparison do not fold user identity")
    public static ValidatedHttpEndpoint resolve(String endpoint, String allowlistProperty)
            throws ValidationException {
        URI uri;
        try {
            uri = new URI(endpoint == null ? "" : endpoint.trim());
        } catch (URISyntaxException e) {
            throw new ValidationException("The endpoint is not a valid URI.", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ValidationException("The endpoint must use HTTP or HTTPS.");
        }
        if (uri.getUserInfo() != null) {
            throw new ValidationException("The endpoint must not embed user-info credentials.");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new ValidationException("The endpoint must not contain a query or fragment.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("The endpoint must include a host.");
        }
        String normalizedHost = normalizeHost(host);

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalizedHost);
        } catch (UnknownHostException e) {
            throw new ValidationException("The endpoint host could not be resolved.", e);
        }
        if (addresses.length == 0) {
            throw new ValidationException("The endpoint host resolved to no addresses.");
        }

        if (!isAllowlisted(normalizedHost, allowlistProperty)) {
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()
                        || isNonGlobalRange(address)) {
                    throw new ValidationException(
                            "The endpoint host resolves to a disallowed local or private address.");
                }
            }
        }
        return new ValidatedHttpEndpoint(
                uri, addresses, "https".equals(scheme), normalizedHost);
    }

    /**
     * Non-global address ranges that {@link InetAddress}'s own predicates do not report.
     *
     * <p>The JDK's {@code isSiteLocalAddress}/{@code isLinkLocalAddress}/{@code isLoopbackAddress}/
     * {@code isAnyLocalAddress} between them cover RFC 1918, 169.254/16, 127/8 and 0.0.0.0 — but
     * nothing else that is unroutable on the public internet. In particular
     * {@code isSiteLocalAddress} is fec0::/10 for IPv6, which is deprecated and does <em>not</em>
     * include the ULA range that actually gets used.</p>
     *
     * <p>Ranges added here, each unreachable from the internet and therefore only meaningful as an
     * internal target:</p>
     * <ul>
     *   <li>{@code 100.64.0.0/10} — carrier-grade NAT (RFC 6598)</li>
     *   <li>{@code 198.18.0.0/15} — benchmarking (RFC 2544)</li>
     *   <li>{@code 192.0.0.0/24} — IETF protocol assignments (RFC 6890)</li>
     *   <li>{@code 0.0.0.0/8} beyond the exact any-address, which is all this-network</li>
     *   <li>{@code 255.255.255.255} — limited broadcast</li>
     *   <li>{@code fc00::/7} — IPv6 unique local addresses (RFC 4193)</li>
     *   <li>{@code 64:ff9b::/96} — NAT64 well-known prefix (RFC 6052), which maps onto IPv4 and can
     *       therefore reach an internal v4 target through a v6 literal</li>
     * </ul>
     *
     * <p>This is defence in depth rather than a fix for an open door: both callers take an
     * operator-configured endpoint (a fax URL behind {@code _admin.fax} write, and a mail
     * {@code end_point} no request path writes at all), so the practical effect is that an admin can
     * no longer point credentials at these ranges without the allowlist the design intends them to
     * use.</p>
     */
    private static boolean isNonGlobalRange(InetAddress address) {
        byte[] octets = address.getAddress();
        if (octets.length == 4) {
            int first = octets[0] & 0xff;
            int second = octets[1] & 0xff;
            // 0.0.0.0/8 (this network) — the exact any-address is already covered upstream.
            if (first == 0) {
                return true;
            }
            // 100.64.0.0/10 (CGNAT).
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            // 198.18.0.0/15 (benchmarking).
            if (first == 198 && (second == 18 || second == 19)) {
                return true;
            }
            // 192.0.0.0/24 (IETF protocol assignments).
            if (first == 192 && second == 0 && (octets[2] & 0xff) == 0) {
                return true;
            }
            // 255.255.255.255 (limited broadcast).
            return first == 255 && second == 255
                    && (octets[2] & 0xff) == 255 && (octets[3] & 0xff) == 255;
        }
        if (octets.length == 16) {
            // fc00::/7 (unique local). isSiteLocalAddress only covers the deprecated fec0::/10.
            if ((octets[0] & 0xfe) == 0xfc) {
                return true;
            }
            // 64:ff9b::/96 (NAT64 well-known prefix), which embeds an IPv4 destination.
            return (octets[0] & 0xff) == 0x00 && (octets[1] & 0xff) == 0x64
                    && (octets[2] & 0xff) == 0xff && (octets[3] & 0xff) == 0x9b
                    && allZero(octets, 4, 12);
        }
        return false;
    }

    private static boolean allZero(byte[] octets, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (octets[i] != 0) {
                return false;
            }
        }
        return true;
    }

    public URI uri() {
        return uri;
    }

    public boolean isHttps() {
        return https;
    }

    /**
     * Returns a resolver that serves only the already-validated host and address set.
     */
    public DnsResolver pinnedDnsResolver() {
        String approvedHost = normalizedHost;
        InetAddress[] approvedAddresses = addresses.clone();
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                if (!approvedHost.equals(normalizeResolverHost(host))) {
                    throw new UnknownHostException("Host was not part of the validated endpoint");
                }
                return approvedAddresses.clone();
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                if (!approvedHost.equals(normalizeResolverHost(host))) {
                    throw new UnknownHostException("Host was not part of the validated endpoint");
                }
                return approvedHost;
            }
        };
    }

    private static boolean isAllowlisted(String host, String propertyName) {
        String value = System.getProperty(propertyName, "");
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(ValidatedHttpEndpoint::normalizeAllowlistHost)
                .anyMatch(host::equals);
    }

    private static String normalizeAllowlistHost(String host) {
        try {
            return normalizeHost(host);
        } catch (ValidationException e) {
            return "";
        }
    }

    private static String normalizeResolverHost(String host) throws UnknownHostException {
        try {
            return normalizeHost(host);
        } catch (ValidationException e) {
            UnknownHostException failure = new UnknownHostException(e.getMessage());
            failure.initCause(e);
            throw failure;
        }
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "IDN.toASCII applies DNS normalization before Locale.ROOT folds the resulting ASCII protocol identifier")
    private static String normalizeHost(String host) throws ValidationException {
        if (host == null || host.isBlank()) {
            throw new ValidationException("The endpoint host is empty.");
        }
        String unwrapped = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        if (unwrapped.indexOf(':') >= 0) {
            return unwrapped.toLowerCase(Locale.ROOT);
        }
        try {
            return IDN.toASCII(unwrapped, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("The endpoint host is not a valid DNS name.", e);
        }
    }

    public static class ValidationException extends Exception {
        private static final long serialVersionUID = 1L;

        ValidationException(String message) {
            super(message);
        }

        ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
