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

import java.sql.SQLException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.HostAddress;
import org.mariadb.jdbc.export.ExceptionFactory;
import org.mariadb.jdbc.export.SslMode;
import org.mariadb.jdbc.plugin.TlsSocketPlugin;
import org.mariadb.jdbc.plugin.tls.main.DefaultTlsSocketPlugin;

/** Preserves Connector/J's certificate policies, including PREFERRED, through MariaDB's TLS SPI. */
public final class MariaDbPreferredTlsPlugin implements TlsSocketPlugin {
    private final DefaultTlsSocketPlugin delegate = new DefaultTlsSocketPlugin();
    // MariaDB creates a fresh SPI provider for each connection handshake.
    private boolean verifyIdentity = true;

    /** SPI identifier selected by the connection-settings compatibility boundary. */
    @Override public String type() { return "carlos-preferred"; }

    /** Preserve the selected CA policy without MariaDB's password-proof substitution for trusted roots. */
    @Override public TrustManager[] getTrustManager(Configuration configuration, ExceptionFactory errors,
                                                   HostAddress host) throws SQLException {
        SslMode mode = host.sslMode == null ? configuration.sslMode() : host.sslMode;
        boolean preferred = mode == SslMode.ALLOW && host.sslMode == null
                && Boolean.parseBoolean(configuration.nonMappedOptions().getProperty("carlosLegacyPreferredTls"));
        verifyIdentity = !preferred;
        // Per-host verification requirements always override the global preferred policy.
        HostAddress effectiveHost = verifyIdentity ? host : HostAddress.from(host.host, host.port, "trust");
        if (verifyIdentity && mode != SslMode.TRUST && mode != SslMode.DISABLE
                && configuration.serverSslCert() == null && configuration.trustStore() == null) {
            Configuration selected = systemStoreConfiguration(configuration, true);
            if (selected != configuration) return systemTrustManagers(selected, errors, effectiveHost);
            // MariaDB may defer an untrusted certificate to password-proof validation,
            // which is different from Connector/J's explicit CA/hostname requirements.
            // Use JSSE's actual configured roots and retain hostname verification.
            try {
                TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                KeyStore roots = null;
                if (!configuration.fallbackToSystemTrustStore()) {
                    roots = KeyStore.getInstance(KeyStore.getDefaultType());
                    roots.load(null, null);
                }
                factory.init(roots);
                return factory.getTrustManagers();
            } catch (GeneralSecurityException | IOException failure) {
                throw new SQLException("Could not initialize database certificate verification", "08000", failure);
            }
        }
        return delegate.getTrustManager(configuration, errors, effectiveHost);
    }

    /** Load selected client certificates without silently omitting a failed system-store identity. */
    @Override public KeyManager[] getKeyManager(Configuration configuration, ExceptionFactory errors) throws SQLException {
        Configuration selected = systemStoreConfiguration(configuration, false);
        if (selected.keyStore() == null) return delegate.getKeyManager(selected, errors);
        // The native manager silently drops keys it cannot decrypt. JSSE's factory
        // retains Connector/J's refusal when a requested private key cannot be recovered.
        try (InputStream input = storeInput(selected.keyStore())) {
            KeyStore keys = KeyStore.getInstance(selected.keyStoreType() == null ? "JKS" : selected.keyStoreType());
            char[] storePassword = selected.keyStorePassword() == null ? new char[0] : selected.keyStorePassword().toCharArray();
            keys.load(input, storePassword);
            char[] keyPassword = selected.keyPassword() == null ? storePassword : selected.keyPassword().toCharArray();
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keys, keyPassword);
            return factory.getKeyManagers();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException failure) {
            throw storeFailure("Could not initialize database client certificate authentication", failure);
        }
    }

    /** Pin a selected system store so native fallback cannot discard it or substitute public roots. */
    private static Configuration systemStoreConfiguration(Configuration configuration, boolean trust) throws SQLException {
        String explicit = trust ? configuration.trustStore() : configuration.keyStore();
        boolean fallback = trust ? configuration.fallbackToSystemTrustStore() : configuration.fallbackToSystemKeyStore();
        if (explicit != null || !fallback) return configuration;
        String property = trust ? "javax.net.ssl.trustStore" : "javax.net.ssl.keyStore";
        String location = System.getProperty(property);
        if (location == null || location.isEmpty()) return configuration;
        String normalized = MariaDbJdbcCompatibility.normalizeStoreLocation(location);
        String configuredType = trust ? configuration.trustStoreType() : configuration.keyStoreType();
        String type = System.getProperty(property + "Type", configuredType == null ? "JKS" : configuredType);
        // Connector/J does not use a connection-store password for a system-selected store.
        String password = System.getProperty(property + "Password");
        var selected = configuration.toBuilder();
        if (trust) {
            selected.trustStore(normalized).trustStoreType(type).trustStorePassword(password).fallbackToSystemTrustStore(false);
        } else {
            selected.keyStore(normalized).keyStoreType(type).keyStorePassword(password)
                    .keyPassword(configuration.keyPassword() == null ? password : configuration.keyPassword())
                    .fallbackToSystemKeyStore(false);
        }
        return selected.build();
    }

    private TrustManager[] systemTrustManagers(Configuration selected, ExceptionFactory errors, HostAddress host) throws SQLException {
        try {
            return delegate.getTrustManager(selected, errors, host);
        } catch (SQLException failure) {
            throw storeFailure("Could not initialize database certificate verification", failure);
        }
    }

    /** Retain URL-backed stores and ordinary file paths, including relative paths. */
    private static InputStream storeInput(String configured) throws IOException, SQLException {
        String location = MariaDbJdbcCompatibility.normalizeStoreLocation(configured);
        java.net.URL url;
        try {
            url = new URI(location).toURL();
        } catch (java.net.MalformedURLException | URISyntaxException | IllegalArgumentException urlFailure) {
            try {
                return Files.newInputStream(Path.of(location));
            } catch (IOException pathFailure) {
                pathFailure.addSuppressed(urlFailure);
                throw pathFailure;
            }
        }
        return url.openStream();
    }

    private static SQLException storeFailure(String message, Exception failure) {
        SQLException sanitized = new SQLException(message, "08000");
        sanitized.setStackTrace(failure.getStackTrace());
        return sanitized;
    }

    /** Verify the hostname whenever the selected policy requires verified server identity. */
    @Override public void verify(String host, SSLSession session, long serverThreadId) throws SSLException {
        if (verifyIdentity) delegate.verify(host, session, serverThreadId);
    }
}
