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
