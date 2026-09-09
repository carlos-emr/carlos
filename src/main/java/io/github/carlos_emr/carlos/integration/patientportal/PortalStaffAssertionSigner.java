/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * You can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Creates the short-lived provider assertion required by the portal's internal API.
 *
 * <p>The bearer token authenticates the CARLOS workload. This assertion separately binds one call
 * to the authenticated provider, configured clinic, and patient-scoped permissions derived inside
 * CARLOS. The portal accepts a compact {@code base64url(json).base64url(ed25519-signature)} value;
 * it is deliberately not a JWT and has no caller-controlled algorithm field.
 *
 * <p>A signer is immutable and creates a fresh {@link Signature} for every call, so the singleton
 * {@link PatientPortalService} remains safe when servlet requests execute concurrently.
 */
final class PortalStaffAssertionSigner {

    static final String HEADER = "X-CARLOS-Staff-Assertion";
    static final String ISSUER = "carlos";
    static final String AUDIENCE = "carlos-patient-portal-internal-api";
    static final Duration LIFETIME = Duration.ofSeconds(60);

    private static final String ALGORITHM = "Ed25519";
    private static final String INVALID_KEY =
            "%s must be an unpadded base64url PKCS#8 Ed25519 private key";
    private static final String SIGNING_FAILED = "could not sign the portal staff assertion";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final PrivateKey privateKey;
    private final Clock clock;
    private final Supplier<UUID> assertionIds;
    private final ObjectMapper objectMapper;

    static PortalStaffAssertionSigner from(PortalSecret encodedPrivateKey) {
        return new PortalStaffAssertionSigner(
                parsePrivateKey(encodedPrivateKey),
                Clock.systemUTC(),
                UUID::randomUUID,
                new ObjectMapper());
    }

    /** Validates the configured key while settings are constructed, before any portal call. */
    static void validate(PortalSecret encodedPrivateKey) {
        parsePrivateKey(encodedPrivateKey);
    }

    PortalStaffAssertionSigner(
            PrivateKey privateKey,
            Clock clock,
            Supplier<UUID> assertionIds,
            ObjectMapper objectMapper) {
        if (privateKey == null || clock == null || assertionIds == null || objectMapper == null) {
            throw new IllegalArgumentException("portal assertion signer dependencies are required");
        }
        this.privateKey = privateKey;
        this.clock = clock;
        this.assertionIds = assertionIds;
        this.objectMapper = objectMapper;
    }

    String sign(PatientPortalStaffContext staff, String clinicId) {
        if (staff == null) {
            throw new IllegalArgumentException("portal staff context is required");
        }
        long issuedAt = clock.instant().getEpochSecond();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("iss", ISSUER);
        payload.put("aud", AUDIENCE);
        payload.put("iat", issuedAt);
        payload.put("exp", issuedAt + LIFETIME.toSeconds());
        payload.put("jti", assertionIds.get().toString());
        payload.put("provider_id", staff.providerId());
        payload.put("provider_name", staff.providerName());
        payload.put("clinic_id", clinicId);
        ArrayNode permissions = payload.putArray("permissions");
        staff.sortedPermissions().forEach(permissions::add);

        try {
            byte[] encodedPayload = objectMapper.writeValueAsBytes(payload);
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(encodedPayload);
            return ENCODER.encodeToString(encodedPayload)
                    + "."
                    + ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException | JsonProcessingException exception) {
            throw new PatientPortalConfigurationException(SIGNING_FAILED, exception);
        }
    }

    private static PrivateKey parsePrivateKey(PortalSecret encodedPrivateKey) {
        if (encodedPrivateKey == null) {
            throw invalidKey(null);
        }
        String encoded = encodedPrivateKey.expose();
        byte[] decoded;
        try {
            decoded = DECODER.decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw invalidKey(exception);
        }
        if (!ENCODER.encodeToString(decoded).equals(encoded)) {
            throw invalidKey(null);
        }
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (GeneralSecurityException exception) {
            throw invalidKey(exception);
        }
    }

    private static PatientPortalConfigurationException invalidKey(Exception cause) {
        String message =
                String.format(Locale.ROOT, INVALID_KEY, PatientPortalSettings.STAFF_ASSERTION_KEY);
        return cause == null
                ? new PatientPortalConfigurationException(message)
                : new PatientPortalConfigurationException(message, cause);
    }

    @Override
    public String toString() {
        return "PortalStaffAssertionSigner[REDACTED]";
    }
}
