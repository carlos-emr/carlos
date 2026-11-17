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
package io.github.carlos_emr.carlos.integration.patientportal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.Arrays;
import java.util.Base64;

/** Loads one explicitly selected retained predecessor; never discovers or advertises alias history. */
final class PortalMyCarlosRetainedSigningKeys {
    private PortalMyCarlosRetainedSigningKeys() {}

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "Explicit server-side retained keystore configuration is validated as an existing file")
    static PrivateKey load(
            PDFSigningConfig selected, PatientPortalClinicKeysPublishRequest.Key expectedPredecessor) {
        char[] storePassword = null;
        char[] keyPassword = null;
        try {
            if (selected == null || !selected.isEnabled() || expectedPredecessor == null) {
                throw invalid();
            }
            selected.validateEnabled();
            var file = PathValidationUtils.validateConfiguredFile(
                    selected.getKeystorePath(), "retained myCarlos predecessor keystore");
            KeyStore keyStore = KeyStore.getInstance(selected.getKeystoreType());
            storePassword = selected.getKeystorePassword();
            keyPassword = selected.getKeyPassword();
            try (InputStream input = Files.newInputStream(file.toPath())) {
                keyStore.load(input, storePassword);
            }
            String alias = selected.getKeyAlias();
            if (!keyStore.isKeyEntry(alias)
                    || !(keyStore.getCertificate(alias) instanceof X509Certificate certificate)
                    || !MessageDigest.isEqual(certificate.getPublicKey().getEncoded(),
                            Base64.getUrlDecoder().decode(expectedPredecessor.spki()))
                    || !(keyStore.getKey(alias, keyPassword) instanceof ECPrivateKey privateKey)) {
                throw invalid();
            }
            // Pin status, not old X.509 validity dates, authorizes this frozen rotation proof.
            // The caller checks the durable predecessor was active; mintOnce verifies the pair.
            return privateKey;
        } catch (Exception failure) {
            throw invalid(); // No path, password, alias diagnostics or key bytes in exceptions.
        } finally {
            if (storePassword != null) {
                Arrays.fill(storePassword, '\0');
            }
            if (keyPassword != null) {
                Arrays.fill(keyPassword, '\0');
            }
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("the selected retained predecessor key is unavailable");
    }
}
