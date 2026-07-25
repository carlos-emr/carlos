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
                        || address.isMulticastAddress()) {
                    throw new ValidationException(
                            "The endpoint host resolves to a disallowed local or private address.");
                }
            }
        }
        return new ValidatedHttpEndpoint(
                uri, addresses, "https".equals(scheme), normalizedHost);
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
