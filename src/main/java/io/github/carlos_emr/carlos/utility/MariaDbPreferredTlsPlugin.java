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
import javax.net.ssl.KeyManager;
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

    /** Retain MariaDB's client-certificate and configured key-store handling. */
    @Override public KeyManager[] getKeyManager(Configuration configuration, ExceptionFactory errors) throws SQLException {
        return delegate.getKeyManager(configuration, errors);
    }

    /** Verify the hostname whenever the selected policy requires verified server identity. */
    @Override public void verify(String host, SSLSession session, long serverThreadId) throws SSLException {
        if (verifyIdentity) delegate.verify(host, session, serverThreadId);
    }
}
