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

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.sql.SQLException;
import java.util.Properties;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.X509KeyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.HostAddress;
import org.mariadb.jdbc.export.ExceptionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Keeps explicit JSSE store failures from becoming anonymous or unverified TLS connections. */
@Tag("unit")
@Isolated
class MariaDbSystemStoreUnitTest {
    private static final String KEY_STORE = "javax.net.ssl.keyStore";
    private static final String TRUST_STORE = "javax.net.ssl.trustStore";
    private static final String STORE_PASSWORD = "synthetic-store-only"; // ggignore - isolated temporary keystore fixture
    private static final String[] SYSTEM_PROPERTIES = {KEY_STORE, KEY_STORE + "Password", KEY_STORE + "Type",
            TRUST_STORE, TRUST_STORE + "Password", TRUST_STORE + "Type"};
    private final Properties originalProperties = new Properties();
    @TempDir Path temporary;

    @BeforeEach
    void isolateSystemStores() {
        for (String key : SYSTEM_PROPERTIES) {
            String value = System.getProperty(key);
            if (value != null) originalProperties.setProperty(key, value);
            System.clearProperty(key);
        }
    }

    @AfterEach
    void restoreSystemStores() {
        for (String key : SYSTEM_PROPERTIES) {
            String value = originalProperties.getProperty(key);
            if (value == null) System.clearProperty(key);
            else System.setProperty(key, value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "malformed", "password"})
    void shouldRefuseConfiguredClientIdentity_whenSystemStoreCannotLoad(String failure) throws Exception {
        Path store = temporary.resolve("client-store.p12");
        if (failure.equals("malformed")) Files.writeString(store, "not a certificate store");
        else if (failure.equals("password")) writeStore(store);
        setSystemStore(KEY_STORE, store, failure.equals("password") ? "incorrect-fixture-password" : STORE_PASSWORD); // ggignore - synthetic wrong-password fixture
        Configuration configuration = configuration(true);
        assertThatThrownBy(() -> new MariaDbPreferredTlsPlugin().getKeyManager(configuration, errors(configuration)))
                .isInstanceOf(SQLException.class).hasMessage("Could not initialize database client certificate authentication")
                .hasNoCause();
    }

    @Test
    void shouldIgnoreSystemClientStore_whenFallbackIsDisabled() throws Exception {
        setSystemStore(KEY_STORE, temporary.resolve("missing.p12"), STORE_PASSWORD);
        Configuration configuration = configuration(false);
        assertThat(new MariaDbPreferredTlsPlugin().getKeyManager(configuration, errors(configuration))).isNull();
    }

    @Test
    void shouldPermitOptionalClientAuthentication_withoutConfiguredSystemStore() throws Exception {
        Configuration configuration = configuration(true);
        assertThat(new MariaDbPreferredTlsPlugin().getKeyManager(configuration, errors(configuration))).isNull();
    }

    @Test
    void shouldLoadConfiguredSystemClientStore_whenStoreIsValid() throws Exception {
        Path store = temporary.resolve("valid-client.p12");
        writeStore(store);
        setSystemStore(KEY_STORE, store, STORE_PASSWORD);
        Configuration configuration = configuration(true);
        assertThat(new MariaDbPreferredTlsPlugin().getKeyManager(configuration, errors(configuration))).hasSize(1);
    }

    @Test
    void shouldRefuseSystemTrustStore_whenConfiguredFileIsMissing() throws Exception {
        setSystemStore(TRUST_STORE, temporary.resolve("missing-trust.p12"), STORE_PASSWORD);
        Configuration configuration = configuration(true);
        assertThatThrownBy(() -> new MariaDbPreferredTlsPlugin().getTrustManager(configuration, errors(configuration), host()))
                .isInstanceOf(SQLException.class).hasMessage("Could not initialize database certificate verification").hasNoCause();
    }

    @Test
    void shouldRefuseSystemTrustStore_whenStorePasswordIsWrong() throws Exception {
        Path store = temporary.resolve("trust.p12");
        writeStore(store);
        setSystemStore(TRUST_STORE, store, "incorrect-fixture-password"); // ggignore - synthetic wrong-password fixture
        Configuration configuration = configuration(true);
        assertThatThrownBy(() -> new MariaDbPreferredTlsPlugin().getTrustManager(configuration, errors(configuration), host()))
                .isInstanceOf(SQLException.class).hasMessage("Could not initialize database certificate verification").hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "encoded", "raw", "localhost", "query", "fragment"})
    void shouldLoadSystemStores_withoutChangingTheirConfiguredLocations(String spelling) throws Exception {
        Path store = temporary.resolve("system store+100%.p12");
        writeStore(store);
        String encoded = store.toUri().toString();
        String location = switch (spelling) {
            case "plain" -> store.toString();
            case "raw" -> encoded.replace("%20", " ");
            case "localhost" -> encoded.replace("file:///", "file://localhost/");
            case "query" -> encoded + "?ignored";
            case "fragment" -> encoded + "#ignored";
            default -> encoded;
        };
        setSystemStore(KEY_STORE, store, STORE_PASSWORD);
        setSystemStore(TRUST_STORE, store, STORE_PASSWORD);
        System.setProperty(KEY_STORE, location);
        System.setProperty(TRUST_STORE, location);
        Configuration configuration = configuration(true);
        var plugin = new MariaDbPreferredTlsPlugin();
        assertThat(plugin.getKeyManager(configuration, errors(configuration))).hasSize(1);
        assertNoTrustAnchors(plugin.getTrustManager(configuration, errors(configuration), host()));
        assertThat(configuration.keyStore()).isNull();
        assertThat(configuration.trustStore()).isNull();
        assertThat(configuration.fallbackToSystemKeyStore()).isTrue();
        assertThat(configuration.fallbackToSystemTrustStore()).isTrue();
        assertThat(System.getProperty(KEY_STORE)).isEqualTo(location);
        assertThat(System.getProperty(TRUST_STORE)).isEqualTo(location);
    }

    @Test
    void shouldKeepExplicitStores_whenSystemDefaultsAreUnusable() throws Exception {
        Path store = temporary.resolve("explicit.p12");
        writeStore(store);
        setSystemStore(KEY_STORE, temporary.resolve("missing-client.p12"), STORE_PASSWORD);
        setSystemStore(TRUST_STORE, temporary.resolve("missing-trust.p12"), STORE_PASSWORD);
        Configuration configuration = configuration(true).toBuilder()
                .keyStore(store.toString()).keyStoreType("PKCS12").keyStorePassword(STORE_PASSWORD)
                .trustStore(store.toString()).trustStoreType("PKCS12").trustStorePassword(STORE_PASSWORD).build();
        var plugin = new MariaDbPreferredTlsPlugin();
        assertThat(plugin.getKeyManager(configuration, errors(configuration))).hasSize(1);
        assertNoTrustAnchors(plugin.getTrustManager(configuration, errors(configuration), host()));
    }

    @Test
    void shouldIgnoreSystemTrustStore_whenFallbackIsDisabled() throws Exception {
        setSystemStore(TRUST_STORE, temporary.resolve("missing-trust.p12"), STORE_PASSWORD);
        Configuration configuration = configuration(true).toBuilder().fallbackToSystemTrustStore(false).build();
        assertNoTrustAnchors(new MariaDbPreferredTlsPlugin().getTrustManager(configuration, errors(configuration), host()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldHonorExplicitNativeKeyPassword_forSystemSelectedStore(boolean valid) throws Exception {
        Path store = temporary.resolve("protected-client.jks");
        String keyPassword = "synthetic-private-key-password"; // ggignore - generated key in an isolated temporary fixture
        var privateKey = writeProtectedKeyStore(store, keyPassword);
        setSystemStore(KEY_STORE, store, STORE_PASSWORD);
        System.setProperty(KEY_STORE + "Type", "JKS");
        Configuration configuration = configuration(true).toBuilder()
                .keyPassword(valid ? keyPassword : "incorrect-fixture-password").build(); // ggignore - synthetic wrong-password fixture
        if (valid) {
            assertRecoveredKey(configuration, privateKey);
        } else {
            assertClientIdentityRefused(configuration);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRecoverDefaultClientKey_onlyWithMatchingStorePassword(boolean matching) throws Exception {
        Path store = temporary.resolve("default-client.jks");
        var privateKey = writeProtectedKeyStore(store, matching ? STORE_PASSWORD : "different-fixture-key-password"); // ggignore - generated temporary key fixture
        setSystemStore(KEY_STORE, store, STORE_PASSWORD);
        System.setProperty(KEY_STORE + "Type", "JKS");
        Configuration configuration = configuration(true);
        if (matching) {
            assertRecoveredKey(configuration, privateKey);
        } else {
            assertClientIdentityRefused(configuration);
            Configuration disabled = configuration.toBuilder().fallbackToSystemKeyStore(false).build();
            assertThat(new MariaDbPreferredTlsPlugin().getKeyManager(disabled, errors(disabled))).isNull();
        }
    }

    @Test
    void shouldKeepSystemPasswordsIndependent_fromConnectionStorePasswords() throws Exception {
        Path store = temporary.resolve("system-password.p12");
        writeStore(store);
        setSystemStore(KEY_STORE, store, STORE_PASSWORD);
        setSystemStore(TRUST_STORE, store, STORE_PASSWORD);
        System.clearProperty(KEY_STORE + "Password");
        System.clearProperty(TRUST_STORE + "Password");
        Configuration configuration = configuration(true).toBuilder().keyStorePassword(STORE_PASSWORD)
                .trustStorePassword("incorrect-fixture-password").build(); // ggignore - synthetic wrong-password fixture
        assertClientIdentityRefused(configuration);
        assertNoTrustAnchors(new MariaDbPreferredTlsPlugin().getTrustManager(configuration, errors(configuration), host()));
    }

    private static void assertRecoveredKey(Configuration configuration, java.security.PrivateKey expected) throws SQLException {
        var managers = new MariaDbPreferredTlsPlugin().getKeyManager(configuration, errors(configuration));
        var manager = java.util.Arrays.stream(managers).filter(X509KeyManager.class::isInstance)
                .map(X509KeyManager.class::cast).findFirst().orElseThrow();
        assertThat(manager.getPrivateKey("synthetic-client")).isEqualTo(expected);
    }

    private static void assertClientIdentityRefused(Configuration configuration) {
        assertThatThrownBy(() -> new MariaDbPreferredTlsPlugin().getKeyManager(configuration, errors(configuration)))
                .isInstanceOf(SQLException.class).hasMessage("Could not initialize database client certificate authentication").hasNoCause();
    }

    private static void assertNoTrustAnchors(javax.net.ssl.TrustManager[] managers) {
        assertThat(managers).isNotEmpty();
        for (var manager : managers) {
            assertThat(manager).isInstanceOf(X509TrustManager.class);
            assertThat(((X509TrustManager) manager).getAcceptedIssuers()).isEmpty();
        }
    }

    private static Configuration configuration(boolean fallback) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("sslMode", "verify-full");
        properties.setProperty("fallbackToSystemKeyStore", Boolean.toString(fallback));
        return Configuration.parse("jdbc:mariadb://localhost/test", properties);
    }

    private static ExceptionFactory errors(Configuration configuration) {
        return new ExceptionFactory(configuration, host());
    }

    private static HostAddress host() { return HostAddress.from("localhost", 3306); }

    private static void setSystemStore(String property, Path store, String password) {
        System.setProperty(property, store.toString());
        System.setProperty(property + "Password", password);
        System.setProperty(property + "Type", "PKCS12");
    }

    private static void writeStore(Path store) throws java.security.GeneralSecurityException, java.io.IOException {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        keys.load(null, null);
        try (var output = Files.newOutputStream(store)) {
            keys.store(output, STORE_PASSWORD.toCharArray());
        }
    }

    private static java.security.PrivateKey writeProtectedKeyStore(Path store, String keyPassword)
            throws java.security.GeneralSecurityException, java.io.IOException {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var key = generator.generateKeyPair().getPrivate();
        KeyStore keys = KeyStore.getInstance("JKS");
        keys.load(null, null);
        // This fixture exercises private-key recovery, not a TLS identity handshake.
        // Generate the private key at runtime; only the existing public certificate is stored in Git.
        try (var input = MariaDbSystemStoreUnitTest.class.getResourceAsStream("/jdbc/compatibility-server-cert.pem")) {
            var certificate = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(input);
            keys.setKeyEntry("synthetic-client", key, keyPassword.toCharArray(), new java.security.cert.Certificate[]{certificate});
        }
        try (var output = Files.newOutputStream(store)) {
            keys.store(output, STORE_PASSWORD.toCharArray());
        }
        return key;
    }
}
