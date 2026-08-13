/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ValidatedHttpEndpoint")
@Tag("unit")
@Tag("fast")
@Tag("security")
@Isolated
class ValidatedHttpEndpointUnitTest {

    private static final String ALLOWLIST_PROPERTY = "carlos.test.endpoint.allowedHosts";
    private String originalAllowlist;

    @BeforeEach
    void rememberProperty() {
        originalAllowlist = System.getProperty(ALLOWLIST_PROPERTY);
        System.clearProperty(ALLOWLIST_PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        if (originalAllowlist == null) {
            System.clearProperty(ALLOWLIST_PROPERTY);
        } else {
            System.setProperty(ALLOWLIST_PROPERTY, originalAllowlist);
        }
    }

    @Test
    @DisplayName("should accept and pin a public HTTP endpoint")
    void shouldPinPublicEndpoint() throws Exception {
        ValidatedHttpEndpoint endpoint = ValidatedHttpEndpoint.resolve(
                "https://203.0.113.10/fax", ALLOWLIST_PROPERTY);

        assertThat(endpoint.uri().toString()).isEqualTo("https://203.0.113.10/fax");
        assertThat(endpoint.isHttps()).isTrue();
        assertThat(endpoint.pinnedDnsResolver().resolve("203.0.113.10"))
                .extracting(address -> address.getHostAddress())
                .containsExactly("203.0.113.10");
        assertThatThrownBy(() -> endpoint.pinnedDnsResolver().resolve("example.com"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    @DisplayName("should reject ambiguous or credential-bearing endpoint forms")
    void shouldRejectUnsafeUriForms() {
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(
                "file:///etc/passwd", ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class);
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(
                "https://user:pass@203.0.113.10/fax", ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class);
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(
                "https://203.0.113.10/fax?next=http://127.0.0.1", ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class);
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(
                "https://203.0.113.10/fax#fragment", ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class);
    }

    @Test
    @DisplayName("should reject local and private addresses by default")
    void shouldRejectLocalAddressesByDefault() {
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(
                "http://127.0.0.1/fax", ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class)
                .hasMessageContaining("local or private");
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(
                "http://169.254.169.254/latest/meta-data", ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class)
                .hasMessageContaining("local or private");
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(
                "http://192.168.1.50/fax", ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class)
                .hasMessageContaining("local or private");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "rejects {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "http://100.64.0.1/fax",        // CGNAT, RFC 6598
        "http://100.127.255.254/fax",   // CGNAT upper bound
        "http://198.18.0.1/fax",        // benchmarking, RFC 2544
        "http://198.19.255.1/fax",      // benchmarking upper half
        "http://192.0.0.1/fax",         // IETF protocol assignments
        "http://0.1.2.3/fax",           // 0.0.0.0/8 this-network
        "http://255.255.255.255/fax",   // limited broadcast
        "http://[fc00::1]/fax",         // IPv6 ULA
        "http://[fd12:3456::1]/fax",    // IPv6 ULA, fd half
        "http://[64:ff9b::c000:0201]/fax" // NAT64 mapping an IPv4 target
    })
    @DisplayName("should reject non-global ranges the JDK predicates do not report")
    void shouldRejectNonGlobalRanges_notCoveredByJdkPredicates(String endpoint) {
        // None of isAnyLocal/isLoopback/isLinkLocal/isSiteLocal/isMulticast returns true for these —
        // isSiteLocalAddress in particular is fec0::/10 for IPv6, the deprecated range, not the ULA
        // range that is actually used. Each is unroutable on the public internet, so an endpoint that
        // resolves to one is only meaningful as an internal target and must go through the allowlist.
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(endpoint, ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class)
                .hasMessageContaining("local or private");
    }

    @Test
    @DisplayName("should still accept a genuinely global address")
    void shouldAcceptGlobalAddress_afterNonGlobalRangesAdded() throws Exception {
        // Guards the new predicate against over-reach: 100.64/10 must not swallow 100.63 or 100.128,
        // and 198.18/15 must not swallow 198.17 or 198.20.
        assertThat(ValidatedHttpEndpoint.resolve("http://100.63.255.255/fax", ALLOWLIST_PROPERTY)).isNotNull();
        assertThat(ValidatedHttpEndpoint.resolve("http://100.128.0.1/fax", ALLOWLIST_PROPERTY)).isNotNull();
        assertThat(ValidatedHttpEndpoint.resolve("http://198.17.255.255/fax", ALLOWLIST_PROPERTY)).isNotNull();
        assertThat(ValidatedHttpEndpoint.resolve("http://198.20.0.1/fax", ALLOWLIST_PROPERTY)).isNotNull();
        assertThat(ValidatedHttpEndpoint.resolve("http://[2001:4860:4860::8888]/fax", ALLOWLIST_PROPERTY)).isNotNull();
    }

    @Test
    @DisplayName("should permit only the exact configured private host")
    void shouldPermitExactAllowlistedHost() throws Exception {
        System.setProperty(ALLOWLIST_PROPERTY, "192.168.1.50");

        ValidatedHttpEndpoint endpoint = ValidatedHttpEndpoint.resolve(
                "http://192.168.1.50/fax", ALLOWLIST_PROPERTY);

        assertThat(endpoint.uri().getHost()).isEqualTo("192.168.1.50");
        assertThat(endpoint.isHttps()).isFalse();
        assertThatThrownBy(() -> ValidatedHttpEndpoint.resolve(
                "http://192.168.1.51/fax", ALLOWLIST_PROPERTY))
                .isInstanceOf(ValidatedHttpEndpoint.ValidationException.class);
    }

    @Test
    @DisplayName("should normalize DNS case before allowlisting and pinning")
    void shouldNormalizeDnsCase() throws Exception {
        System.setProperty(ALLOWLIST_PROPERTY, "LOCALHOST");

        ValidatedHttpEndpoint endpoint = ValidatedHttpEndpoint.resolve(
                "http://localhost/fax", ALLOWLIST_PROPERTY);

        assertThat(endpoint.isHttps()).isFalse();
        assertThat(endpoint.pinnedDnsResolver().resolve("LoCaLhOsT")).isNotEmpty();
    }
}
