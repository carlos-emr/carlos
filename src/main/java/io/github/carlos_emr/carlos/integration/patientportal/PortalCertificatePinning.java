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

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Requires the portal to present a specific public key, on top of normal TLS validation.
 *
 * <p>Certificate validation alone trusts the entire JVM truststore. In a clinic that has installed
 * a TLS-inspecting proxy's CA — which is common — anyone holding that CA can present a certificate
 * for the portal's hostname that passes every standard check, and CARLOS would hand them the
 * service token along with the patient email, date of birth, and health card number that an
 * invitation carries. Pinning narrows "any CA the machine trusts" to "the portal's actual key".
 *
 * <p><b>Pinning is additive and must stay that way.</b> This delegates to the platform trust
 * manager first, so chain building, expiry, and revocation all still apply, and only then requires
 * a pin match. The common way pinning is implemented wrongly is to replace the trust manager with
 * one that accepts everything and then compare a fingerprint — which silently discards expiry and
 * chain validation in exchange for the pin. The delegate call below is what prevents that, and
 * {@code PortalCertificatePinningUnitTest} fails if it is removed.
 *
 * <p>Pins are over the <b>public key</b> (SubjectPublicKeyInfo), not the certificate body, so
 * renewing a certificate with the same key does not break the deployment while rotating the key
 * does. Configure more than one during a planned key rotation: any match is accepted.
 *
 * <p>Format is {@code sha256/} followed by the base64 SHA-256 of the encoded public key, matching
 * the convention used by HTTP Public Key Pinning and most tooling that prints these.
 *
 * @since 2026-08-20
 */
final class PortalCertificatePinning implements X509TrustManager {

    static final String PIN_PREFIX = "sha256/";

    private static final String NO_MATCH =
            "portal certificate did not match any configured pin in %s";
    private static final String BAD_PIN =
            "%s entries must look like sha256/<base64 sha-256 of the public key>";

    private final X509TrustManager delegate;
    private final Set<String> pins;

    private PortalCertificatePinning(X509TrustManager delegate, Set<String> pins) {
        this.delegate = delegate;
        this.pins = pins;
    }

    /**
     * Wraps the platform trust manager with a pin requirement.
     *
     * @param pins one or more {@code sha256/…} pins; must not be empty
     */
    static PortalCertificatePinning over(Set<String> pins) {
        if (pins == null || pins.isEmpty()) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, BAD_PIN, PatientPortalSettings.CERTIFICATE_PINS_KEY));
        }
        for (String pin : pins) {
            if (pin == null || !pin.startsWith(PIN_PREFIX) || pin.length() <= PIN_PREFIX.length()) {
                throw new PatientPortalConfigurationException(
                        String.format(
                                Locale.ROOT, BAD_PIN, PatientPortalSettings.CERTIFICATE_PINS_KEY));
            }
        }
        return new PortalCertificatePinning(platformTrustManager(), Set.copyOf(pins));
    }

    /** The JVM's own trust manager, so the standard checks are kept rather than replaced. */
    private static X509TrustManager platformTrustManager() {
        try {
            TrustManagerFactory factory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (TrustManager candidate : factory.getTrustManagers()) {
                if (candidate instanceof X509TrustManager manager) {
                    return manager;
                }
            }
            throw new PatientPortalConfigurationException(
                    "no platform X509 trust manager is available");
        } catch (java.security.GeneralSecurityException exception) {
            throw new PatientPortalConfigurationException(
                    "could not initialise the platform trust manager", exception);
        }
    }

    /**
     * Computes the pin for a certificate, for operators reading one off a live portal.
     */
    static String pinFor(X509Certificate certificate) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(certificate.getPublicKey().getEncoded());
            return PIN_PREFIX + Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        // Standard validation first. Never remove this in favour of the pin check alone.
        delegate.checkServerTrusted(chain, authType);

        Set<String> presented = new LinkedHashSet<>();
        for (X509Certificate certificate : chain) {
            String pin = pinFor(certificate);
            presented.add(pin);
            if (pins.contains(pin)) {
                return;
            }
        }
        throw new CertificateException(
                String.format(
                        Locale.ROOT, NO_MATCH, PatientPortalSettings.CERTIFICATE_PINS_KEY));
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }
}
