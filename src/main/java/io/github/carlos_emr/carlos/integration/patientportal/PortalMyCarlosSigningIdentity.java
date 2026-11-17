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

import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Base64;

/** P-256 requirement is specific to myCarlos; the general PDF signer still supports RSA. */
final class PortalMyCarlosSigningIdentity {
    private PortalMyCarlosSigningIdentity() {}

    static PatientPortalClinicKeysPublishRequest.Key currentPublicKey() {
        try {
            var certificate = PDFSigningUtil.currentSigningCertificate(PDFSigningConfig.fromCarlosProperties());
            return fromValidatedCertificate(certificate);
        } catch (Exception failure) {
            // Neither keystore/provider diagnostics nor private key material crosses this boundary.
            throw new IllegalStateException("myCarlos document signing identity is unavailable");
        }
    }

    /** Certificate/private-key pairing and validity are the existing PDF loader's responsibility. */
    static PatientPortalClinicKeysPublishRequest.Key fromValidatedCertificate(X509Certificate certificate) {
        try {
            byte[] spki = certificate.getPublicKey().getEncoded();
            var encoder = Base64.getUrlEncoder().withoutPadding();
            // The existing request type validates canonical named P-256 DER, the point and kid.
            return new PatientPortalClinicKeysPublishRequest.Key(
                    encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(spki)),
                    encoder.encodeToString(spki), "active", null, null);
        } catch (Exception failure) {
            throw new IllegalStateException("myCarlos document signing identity is unavailable");
        }
    }
}
