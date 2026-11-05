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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Properties;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.HostAddress;
import org.mariadb.jdbc.export.ExceptionFactory;
import org.mariadb.jdbc.plugin.tls.TlsSocketPluginLoader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises certificate validation itself, not only the configured mode name. */
@Tag("unit")
class MariaDbTlsPolicyUnitTest {
    @TempDir Path temporary;

    @Test
    void shouldLoadCompatibilityTlsProvider_throughPackagedServiceDescriptor() throws Exception {
        assertThat(TlsSocketPluginLoader.get("carlos-preferred")).isInstanceOf(MariaDbPreferredTlsPlugin.class);
    }

    @Test
    void shouldPreserveEncryptionWithoutIdentityRequirements_withLegacyPreferredMode() throws Exception {
        Configuration configuration = configuration("PREFERRED");
        HostAddress host = HostAddress.from("localhost", 3306, null);
        MariaDbPreferredTlsPlugin plugin = new MariaDbPreferredTlsPlugin();
        X509TrustManager trust = trustManager(plugin, configuration, host);
        assertThatCode(() -> trust.checkServerTrusted(chain(), "RSA")).doesNotThrowAnyException();
        // PREFERRED intentionally never requests hostname verification, matching Connector/J.
        assertThatCode(() -> plugin.verify("other.example", null, 1)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERIFY_CA", "VERIFY_IDENTITY", "ALLOW"})
    void shouldRejectUntrustedCertificate_withoutDeferringTrustToPasswordProof(String mode) throws Exception {
        Configuration configuration = configuration(mode);
        X509TrustManager trust = trustManager(new MariaDbPreferredTlsPlugin(), configuration,
                HostAddress.from("localhost", 3306, null));
        assertThatThrownBy(() -> trust.checkServerTrusted(chain(), "RSA")).isInstanceOf(CertificateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"verify-full", "allow"})
    void shouldKeepPerHostVerificationStronger_thanGlobalPreferredPolicy(String mode) throws Exception {
        Configuration configuration = configuration("PREFERRED");
        X509TrustManager trust = trustManager(new MariaDbPreferredTlsPlugin(), configuration,
                HostAddress.from("localhost", 3306, mode));
        assertThatThrownBy(() -> trust.checkServerTrusted(chain(), "RSA")).isInstanceOf(CertificateException.class);
    }

    @Test
    void shouldValidateHostname_withExplicitTrustStore() throws Exception {
        KeyStore roots = KeyStore.getInstance("PKCS12");
        roots.load(null, null);
        roots.setCertificateEntry("synthetic-server", chain()[0]);
        Path store = temporary.resolve("trusted-server.p12");
        try (var output = Files.newOutputStream(store)) {
            roots.store(output, "synthetic-only".toCharArray()); // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
        }
        Properties supplied = new Properties();
        supplied.setProperty("trustCertificateKeyStoreUrl", store.toUri().toString());
        supplied.setProperty("trustCertificateKeyStorePassword", "synthetic-only"); // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
        supplied.setProperty("trustCertificateKeyStoreType", "PKCS12");
        var settings = MariaDbJdbcCompatibility.settings("jdbc:mysql://localhost/test?sslMode=VERIFY_IDENTITY", supplied);
        Configuration configuration = Configuration.parse(settings.url(), settings.properties());
        HostAddress host = HostAddress.from("localhost", 3306, null);
        MariaDbPreferredTlsPlugin plugin = new MariaDbPreferredTlsPlugin();
        X509TrustManager trust = trustManager(plugin, configuration, host);
        assertThatCode(() -> trust.checkServerTrusted(chain(), "RSA")).doesNotThrowAnyException();
        SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(chain());
        assertThatCode(() -> plugin.verify("localhost", session, 1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> plugin.verify("other.example", session, 1)).isInstanceOf(SSLException.class);
    }

    private static Configuration configuration(String mode) throws Exception {
        var settings = MariaDbJdbcCompatibility.settings("jdbc:mysql://localhost/test?sslMode=" + mode, new Properties());
        return Configuration.parse(settings.url(), settings.properties());
    }

    private static X509TrustManager trustManager(MariaDbPreferredTlsPlugin plugin, Configuration configuration,
                                                HostAddress host) throws Exception {
        TrustManager[] managers = plugin.getTrustManager(configuration, new ExceptionFactory(configuration, host), host);
        return Arrays.stream(managers).filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast).findFirst().orElseThrow();
    }

    private static X509Certificate[] chain() throws Exception {
        // Public synthetic fixture only; no private key belongs in source control.
        try (InputStream input = MariaDbTlsPolicyUnitTest.class.getResourceAsStream("/jdbc/compatibility-server-cert.pem")) {
            return new X509Certificate[]{(X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input)};
        }
    }
}
