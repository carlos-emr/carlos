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

import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** No real clinic, persisted private key, signed rotation proof, socket or provider. */
final class PortalMyCarlosKeyTestFixtures {
    static final String CLINIC = "fake-clinic";
    static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    static final KeyPair FIRST_PAIR = pair();
    static final KeyPair SECOND_PAIR = pair();
    static final PatientPortalClinicKeysPublishRequest.Key FIRST = key(FIRST_PAIR);
    static final PatientPortalClinicKeysPublishRequest.Key SECOND = key(SECOND_PAIR);
    static final PortalMyCarlosPublicationState.Target TARGET_A = target("portal-a.example");
    static final PortalMyCarlosPublicationState.Target TARGET_B = target("portal-b.example");
    static final String TOKEN_A = "A".repeat(32);
    static final String TOKEN_B = "B".repeat(32);

    private PortalMyCarlosKeyTestFixtures() {}

    static KeyPair pair() {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new AssertionError("FAKE key pair failed", failure);
        }
    }

    static PatientPortalClinicKeysPublishRequest.Key key(KeyPair pair) {
        try {
            byte[] spki = pair.getPublic().getEncoded();
            var encoder = Base64.getUrlEncoder().withoutPadding();
            return new PatientPortalClinicKeysPublishRequest.Key(
                    encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(spki)),
                    encoder.encodeToString(spki), "active", null, null);
        } catch (Exception failure) {
            throw new AssertionError("FAKE public-key fixture failed", failure);
        }
    }

    static PortalMyCarlosPublicationState.Target target(String host) {
        return new PortalMyCarlosPublicationState.Target(CLINIC,
                URI.create("https://" + host + PortalMyCarlosPublicationState.ENDPOINT_PATH));
    }

    static PortalMyCarlosKeyInventory catalogue() {
        return PortalMyCarlosKeyInventory.create(CLINIC, List.of(FIRST));
    }

    static PatientPortalClinicKeysPublishRequest.Key revoked(PatientPortalClinicKeysPublishRequest.Key key) {
        return new PatientPortalClinicKeysPublishRequest.Key(
                key.kid(), key.spki(), "revoked", key.replacesKid(), key.replacementSignature());
    }

    static PatientPortalClinicKeysPublishResult acknowledgement(
            PortalMyCarlosPublicationState.Claim claim) {
        return new PatientPortalClinicKeysPublishResult(CLINIC, true,
                claim.inventory().publication().keys().stream().map(key ->
                        new PatientPortalClinicKeysPublishResult.Key(key.kid(), key.status(), NOW,
                                "revoked".equals(key.status()) ? NOW : null, key.replacesKid())).toList());
    }
}
