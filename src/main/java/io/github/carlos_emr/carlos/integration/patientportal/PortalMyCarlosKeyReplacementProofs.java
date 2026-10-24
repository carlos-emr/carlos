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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Frozen additive v1 domain/framing and standard JCA P-256/P1363 signatures; no custom curve code. */
final class PortalMyCarlosKeyReplacementProofs {
    private static final byte[] DOMAIN =
            "mycarlos-clinic-key-replacement-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final String ALGORITHM = "SHA256withECDSAinP1363Format";

    private PortalMyCarlosKeyReplacementProofs() {}

    static byte[] statement(
            String clinicId, PatientPortalClinicKeysPublishRequest.Key predecessor,
            PatientPortalClinicKeysPublishRequest.Key replacement) {
        if (clinicId == null || clinicId.isBlank() || clinicId.length() > 128
                || predecessor == null || replacement == null || predecessor.kid().equals(replacement.kid())) {
            throw invalid();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(DOMAIN);
        for (byte[] component : List.of(
                clinicId.getBytes(StandardCharsets.UTF_8),
                predecessor.kid().getBytes(StandardCharsets.UTF_8),
                replacement.kid().getBytes(StandardCharsets.UTF_8),
                Base64.getUrlDecoder().decode(replacement.spki()))) {
            bytes.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(component.length).array());
            bytes.writeBytes(component);
        }
        return bytes.toByteArray();
    }

    /**
     * Only explicit provisioning calls this, once under its durable generation lock. The returned
     * exact signature bytes must be persisted and reused; the publisher never calls this method.
     */
    static PatientPortalClinicKeysPublishRequest.Key mintOnce(
            String clinicId, PatientPortalClinicKeysPublishRequest.Key predecessor,
            PatientPortalClinicKeysPublishRequest.Key replacement, PrivateKey selectedPredecessorKey) {
        if (predecessor == null || !"active".equals(predecessor.status())
                || replacement == null || replacement.replacesKid() != null
                || replacement.replacementSignature() != null || selectedPredecessorKey == null) {
            throw invalid();
        }
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(selectedPredecessorKey);
            signer.update(statement(clinicId, predecessor, replacement));
            byte[] signed = signer.sign();
            if (signed.length != 64) {
                throw invalid();
            }
            var result = new PatientPortalClinicKeysPublishRequest.Key(replacement.kid(), replacement.spki(),
                    replacement.status(), predecessor.kid(),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(signed));
            verify(clinicId, predecessor, result); // Proves the explicitly selected private/public pair.
            return result;
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    /** Bootstrap/import validates every proof and the whole graph, without requiring previous pins. */
    static void validateAll(String clinicId, PatientPortalClinicKeysPublishRequest inventory) {
        Map<String, PatientPortalClinicKeysPublishRequest.Key> keys = index(inventory);
        Map<String, String> successors = new HashMap<>();
        for (var key : inventory.keys()) {
            if (key.replacesKid() == null) {
                continue;
            }
            var predecessor = keys.get(key.replacesKid());
            if (predecessor == null || successors.put(predecessor.kid(), key.kid()) != null) {
                throw invalid();
            }
            verify(clinicId, predecessor, key);
        }
        if (successors.size() > 15) {
            throw invalid();
        }
        for (var key : inventory.keys()) {
            Set<String> visited = new HashSet<>();
            var cursor = key;
            while (cursor != null) {
                if (!visited.add(cursor.kid()) || visited.size() > 16) {
                    throw invalid();
                }
                cursor = cursor.replacesKid() == null ? null : keys.get(cursor.replacesKid());
            }
        }
    }

    /** Prior-state continuity applies only to newly introduced signed edges, not historical edges. */
    static void validateNewEdges(
            PortalMyCarlosKeyInventory previous, PatientPortalClinicKeysPublishRequest next) {
        Map<String, PatientPortalClinicKeysPublishRequest.Key> before = index(previous.publication());
        Map<String, PatientPortalClinicKeysPublishRequest.Key> after = index(next);
        for (var key : next.keys()) {
            if (before.containsKey(key.kid()) || key.replacesKid() == null) {
                continue;
            }
            var cursor = after.get(key.replacesKid());
            Set<String> visited = new HashSet<>();
            boolean anchored = false;
            while (cursor != null && visited.add(cursor.kid()) && visited.size() <= 16) {
                var old = before.get(cursor.kid());
                if (old != null) {
                    if ("revoked".equals(old.status())) {
                        throw invalid();
                    }
                    anchored = true; // Nearest previously active ancestor, even if incoming revoked.
                    break;
                }
                if (cursor.replacesKid() == null) {
                    // A new unsigned root is accepted with the client's atomic unsigned-change notice.
                    anchored = true;
                    break;
                }
                cursor = after.get(cursor.replacesKid());
            }
            if (!anchored) {
                throw invalid();
            }
        }
    }

    private static Map<String, PatientPortalClinicKeysPublishRequest.Key> index(
            PatientPortalClinicKeysPublishRequest inventory) {
        Map<String, PatientPortalClinicKeysPublishRequest.Key> keys = new HashMap<>();
        inventory.keys().forEach(key -> keys.put(key.kid(), key));
        return keys;
    }

    private static void verify(
            String clinicId, PatientPortalClinicKeysPublishRequest.Key predecessor,
            PatientPortalClinicKeysPublishRequest.Key replacement) {
        try {
            if (!predecessor.kid().equals(replacement.replacesKid())) {
                throw invalid();
            }
            byte[] proof = Base64.getUrlDecoder().decode(replacement.replacementSignature());
            if (proof.length != 64) {
                throw invalid();
            }
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(Base64.getUrlDecoder().decode(predecessor.spki()))));
            verifier.update(statement(clinicId, predecessor, replacement));
            if (!verifier.verify(proof)) {
                throw invalid();
            }
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("clinic public key replacement proof is invalid");
    }
}
